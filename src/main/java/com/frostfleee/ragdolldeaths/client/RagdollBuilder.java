package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.JointNames;
import com.frostfleee.ragdolldeaths.core.JointRole;
import com.frostfleee.ragdolldeaths.mixin.ModelPartAccessor;
import com.frostfleee.ragdolldeaths.physics.Body;
import com.frostfleee.ragdolldeaths.physics.Island;
import com.frostfleee.ragdolldeaths.physics.Joint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Turns a mob's model into a ragdoll at the moment it dies.
 *
 * <p>It works from the model as posed for the frame being drawn and the matrix that frame draws it with, so every
 * body starts exactly where its part is on screen, whatever the mob was doing. Each part with a role (head, body,
 * arm, leg) becomes a box fitted to its cubes, hung from the torso by a joint at the part's own pivot. With the
 * knees-and-elbows beta on, arms and legs are cut in two (see {@link LimbSplitter}) and get an elbow or a knee.
 * Anything else rides on the torso.
 */
final class RagdollBuilder {
	/** A zombie's weight in kilograms; other sizes scale by the volume of their hitbox. */
	private static final double ZOMBIE_MASS = 70.0;
	private static final double ZOMBIE_VOLUME = 0.6 * 0.6 * 1.95;
	/** A teleport or a lag spike can make a mob seem to have moved absurdly fast in one tick. */
	private static final double MAX_CARRIED_SPEED = 60.0;
	private static final double TICKS_PER_SECOND = 20.0;
	private static final float MIN_PIECE_PIXELS = 3.0F;
	/** How far ahead, in ticks, the walk animation is sampled to measure how fast the limbs are swinging. */
	private static final float SWING_SAMPLE_TICKS = 0.5F;

	private RagdollBuilder() {
	}

	/** One model part measured in the world: its matrix, its cubes' box and the body made for it. */
	private static final class Piece {
		String[] path;
		ModelPart part;
		JointRole role;
		Matrix4f matrix;
		Rest rest;
		/** Where this part's limb is cut, when this is the upper half of one. */
		float splitY = Float.NaN;
		/** The upper half this lower half hangs from, or null for a part hung from the torso. */
		Piece upper;
		/** The piece this one is inside in the model and hangs from, or null to hang from the torso. */
		Piece parent;
		/** The matrix of the part this one is attached to in the model, as posed. */
		Matrix4f parentSpace;
		/** The part's angles on the frame the mob died, before anything else poses the model. */
		float[] died;
		final Vector3f centre = new Vector3f();
		final Vector3f half = new Vector3f();
		double weight;
		Body body;
	}

	/**
	 * @param pose model space to camera-relative world space, as the model is about to be drawn
	 * @throws IllegalStateException when the model has no torso to build around; the caller falls back
	 */
	static void build(PhysicsRagdoll ragdoll, Model model, Object state, Matrix4f pose) {
		ModelPart root = Compat.root(model);
		Matrix4f rootMatrix = ModelMath.mul(new Matrix4f(pose), root);

		List<Piece> pieces = new ArrayList<>();
		// Top-level parts that are not bodies themselves ride on the torso; anything inside them follows them.
		List<String[]> riders = new ArrayList<>();
		shellModel = ragdoll.entity instanceof net.minecraft.world.entity.monster.Shulker;
		vanillaRoot = EmfCompat.vanillaRoot(root);
		collect(root, root, new String[0], rootMatrix, null, true, pieces, riders);
		Piece torso = null;
		for (Piece piece : pieces) {
			if (piece.role == JointRole.BODY && piece.upper == null && (torso == null || piece.weight > torso.weight)) {
				torso = piece;
			}
		}
		if (torso == null) {
			// No part is called a body (a slime-like blob, an odd modded model): the heaviest part carries the rest.
			for (Piece piece : pieces) {
				if (piece.upper == null && (torso == null || piece.weight > torso.weight)) {
					torso = piece;
				}
			}
		}
		if (torso == null) {
			// Nothing in the model has a role at all (a slime is a cube with eyes): the whole model is one lump.
			torso = lump(root, rootMatrix);
			if (torso == null) {
				throw new IllegalStateException("the model has no part to build a ragdoll around");
			}
			pieces.add(torso);
		}
		// A second body part (a spider's abdomen, a wolf's chest) is just another piece hung from the torso.
		pieces.remove(torso);
		pieces.addFirst(torso);
		for (Piece piece : pieces) {
			if (piece.parent == torso.parent && piece != torso && piece.upper == null && piece.parent != null) {
				// Hung beside the torso inside the same body piece: it hangs from the torso instead.
				piece.parent = null;
			}
		}
		torso.parent = null;

		LivingEntity entity = ragdoll.entity;
		double total = 0.0;
		for (Piece piece : pieces) {
			total += piece.weight;
		}
		double mass = ZOMBIE_MASS * entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight() / ZOMBIE_VOLUME;
		Vec3 velocity = velocityOf(entity);

		Body[] bodies = new Body[pieces.size()];
		List<PhysicsRagdoll.Link> links = new ArrayList<>();
		for (int i = 0; i < bodies.length; i++) {
			Piece piece = pieces.get(i);
			piece.body = body(piece, mass * piece.weight / total, ragdoll, velocity);
			bodies[i] = piece.body;
			if (piece.path.length > 0) {
				links.add(new PhysicsRagdoll.Link(piece.path, i, offset(piece.body, piece.matrix, ragdoll), piece.rest, piece.splitY, false));
			}
		}
		for (String[] path : riders) {
			ModelPart part = find(root, path);
			Matrix4f matrix = ModelMath.mul(new Matrix4f(rootMatrix), part);
			// A breeze's whirlwind dies with it.
			boolean hidden = JointNames.roleOf(JointNames.tokens(path[0])) == JointRole.WIND;
			links.add(new PhysicsRagdoll.Link(path, 0, offset(torso.body, matrix, ragdoll), Rest.of(part), Float.NaN, hidden));
		}
		// Each part's place is worked out from the part it hangs from, so that one has to be placed first.
		links.sort(java.util.Comparator.comparingInt(link -> link.path().length));

		List<Joint> joints = new ArrayList<>();
		for (int i = 1; i < pieces.size(); i++) {
			Piece piece = pieces.get(i);
			joints.add(piece.upper != null ? hinge(piece.upper, piece, ragdoll) : socket(piece.parent != null ? piece.parent : nearestBody(pieces, piece), piece, ragdoll));
		}
		Joint[] jointArray = joints.toArray(Joint[]::new);
		carrySwing(model, state, pieces, rootMatrix, ragdoll);
		JointRole[] roles = new JointRole[bodies.length];
		for (int i = 0; i < roles.length; i++) {
			roles[i] = pieces.get(i).role;
		}
		ragdoll.roles = roles;
		ragdoll.blow = DeathBlow.apply(entity, bodies, jointArray, roles, ConfigStore.get());
		ragdoll.muscles = muscles(root, pieces, jointArray, entity);
		Island island = new Island(bodies, jointArray);
		// Parts that already overlap where the model puts them (a spider's eight legs meet at its body) must not be
		// pushed apart: that would fling them. Only the joints hold those pairs together.
		island.ignoreOverlaps();
		ragdoll.finishBuild(island, bodies, links.toArray(PhysicsRagdoll.Link[]::new), 0);
		ragdoll.special = SpecialBodies.create(ragdoll, entity, DeathBlow.lastStrength, ConfigStore.get());
	}

	/** Gives every joint its dying muscle: which animation joint it follows and how much weight hangs from it. */
	private static Muscles muscles(ModelPart root, List<Piece> pieces, Joint[] joints, LivingEntity entity) {
		List<JointRig.Joint> rig = JointRig.of(root).joints;
		Piece torso = pieces.getFirst();
		int torsoIndex = 0;
		for (JointRig.Joint named : rig) {
			if (named.part() == torso.part) {
				torsoIndex = named.index();
			}
		}
		List<Muscles.Fibre> fibres = new ArrayList<>();
		for (int i = 1; i < pieces.size(); i++) {
			Piece piece = pieces.get(i);
			Joint joint = joints[i - 1];
			// A lower half follows its limb's animation joint; the limb's own muscle carries both halves.
			ModelPart animated = piece.upper == null ? piece.part : piece.upper.part;
			JointRig.Joint named = null;
			for (JointRig.Joint candidate : rig) {
				if (candidate.part() == animated) {
					named = candidate;
				}
			}
			if (named == null) {
				continue;
			}
			Body hung = piece.body;
			Body from = piece.upper != null ? piece.upper.body : piece.parent != null ? piece.parent.body : torso.body;
			// The joint sits where the two bodies meet; the limb's weight acts at its middle.
			double torque = Muscles.weightTorque(hung.mass, distance(hung, from) * 0.5);
			for (Piece other : pieces) {
				if (other.upper == piece) {
					torque += Muscles.weightTorque(other.body.mass, distance(other.body, from) * 0.75);
				}
			}
			var rest = Rest.of(animated);
			fibres.add(new Muscles.Fibre(joint, piece.role, named.side(), named.index(), piece.upper != null, piece.parent != null,
					new float[] {rest.xRot(), rest.yRot(), rest.zRot()}, piece.upper == null ? piece.died : piece.upper.died, torque));
		}
		var torsoRest = Rest.of(torso.part);
		return new Muscles(fibres, torsoIndex, new float[] {torsoRest.xRot(), torsoRest.yRot(), torsoRest.zRot()}, torso.died,
				entity.getUUID().getMostSignificantBits() ^ entity.level().getGameTime());
	}

	private static double distance(Body a, Body b) {
		return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z));
	}

	/**
	 * A mob killed mid-stride keeps its limbs swinging: each part starts with the spin its walk animation had. The
	 * model is posed once more a moment further into its walk cycle, and how far each part turned in that moment is
	 * its spin. That works for any model, without knowing how it animates.
	 */
	@SuppressWarnings("unchecked")
	private static void carrySwing(Model model, Object state, List<Piece> pieces, Matrix4f rootMatrix, PhysicsRagdoll ragdoll) {
		if (!(state instanceof LivingEntityRenderState living) || living.walkAnimationSpeed < 0.05F) {
			return;
		}
		float[] before = new float[pieces.size() * 3];
		for (int i = 0; i < pieces.size(); i++) {
			ModelPart part = pieces.get(i).part;
			before[i * 3] = part.xRot;
			before[i * 3 + 1] = part.yRot;
			before[i * 3 + 2] = part.zRot;
		}
		float position = living.walkAnimationPos, age = living.ageInTicks;
		try {
			living.walkAnimationPos = position + living.walkAnimationSpeed * SWING_SAMPLE_TICKS;
			living.ageInTicks = age + SWING_SAMPLE_TICKS;
			Compat.setupAnim(model, state);
			// The matrix scales the model; a direction through it has to be brought back to unit length.
			float scale = rootMatrix.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)).length();
			for (int i = 0; i < pieces.size(); i++) {
				Piece piece = pieces.get(i);
				// Both halves of a limb turn with the limb itself.
				ModelPart part = piece.upper == null ? piece.part : piece.upper.part;
				int k = (piece.upper == null ? i : pieces.indexOf(piece.upper)) * 3;
				Vector3f spin = new Vector3f(part.xRot - before[k], part.yRot - before[k + 1], part.zRot - before[k + 2])
						.mul((float) (TICKS_PER_SECOND / SWING_SAMPLE_TICKS));
				if (spin.lengthSquared() < 1.0E-4F || spin.length() > 40.0F) {
					continue;
				}
				rootMatrix.transformDirection(spin).div(scale);
				Matrix4f limb = piece.upper == null ? piece.matrix : piece.upper.matrix;
				Vector3f pivot = limb.transformPosition(new Vector3f());
				Body body = piece.body;
				double rx = body.x - (ragdoll.camX + pivot.x), ry = body.y - (ragdoll.camY + pivot.y), rz = body.z - (ragdoll.camZ + pivot.z);
				body.wx += spin.x;
				body.wy += spin.y;
				body.wz += spin.z;
				body.vx += spin.y * rz - spin.z * ry;
				body.vy += spin.z * rx - spin.x * rz;
				body.vz += spin.x * ry - spin.y * rx;
			}
		} catch (RuntimeException e) {
			// The pose could not be sampled; the body simply starts without its swing.
		} finally {
			living.walkAnimationPos = position;
			living.ageInTicks = age;
			Compat.setupAnim(model, state);
		}
	}

	/**
	 * Walks the model: every part with a role that can hang from where it is becomes a piece. A piece inside another
	 * piece (a bat's wing inside its body) hangs from that one; anything else hangs from the torso. Parts without a
	 * role are carried by the piece they are in, or at the top level by the torso.
	 *
	 * @param parentSpace the matrix of {@code parent}, as posed
	 * @param container the piece the parts are inside, or null at the top level
	 * @param topLevel whether the parts are not inside any piece, so a part that is not a piece must ride the torso
	 */
	private static void collect(ModelPart root, ModelPart parent, String[] path, Matrix4f parentSpace, Piece container, boolean topLevel,
			List<Piece> pieces, List<String[]> riders) {
		for (Map.Entry<String, ModelPart> child : List.copyOf(((ModelPartAccessor) (Object) parent).ragdolldeaths$children().entrySet())) {
			String name = child.getKey();
			ModelPart part = child.getValue();
			if (name.startsWith("ragdolldeaths_")) {
				continue;
			}
			String[] childPath = java.util.Arrays.copyOf(path, path.length + 1);
			childPath[path.length] = name;
			JointRole role = roleOf(name);
			Matrix4f matrix = ModelMath.mul(new Matrix4f(parentSpace), part);
			Piece piece = null;
			// A resource pack's model (Entity Model Features): the limb is drawn from the pack's parts, but shaped and
			// shown as the vanilla part at the same place (the pack's own parts can be anywhere, or the vanilla part
			// hidden while they are drawn).
			ModelPart vanilla = vanillaRoot == null ? null : vanillaPart(vanillaRoot, childPath);
			boolean shown = vanilla != null ? vanilla.visible : part.visible;
			// Both arms as one folded piece across the chest (a villager's, a witch's): that is part of the torso. On a
			// joint of its own it swings into the body.
			boolean foldedArms = role == JointRole.ARM && JointNames.sideOf(JointNames.tokens(name)) == com.frostfleee.ragdolldeaths.core.JointPose.Side.CENTER
					&& Math.abs(Rest.of(part).x()) < 0.01F;
			if (role != null && simulated(role) && !foldedArms && shown && allowedIn(container, role)) {
				boolean bends = ConfigStore.get().kneesAndElbows && container == null && (role == JointRole.ARM || role == JointRole.LEG) && vanillaRoot == null;
				float plane = bends ? LimbSplitter.planeOf(part) : Float.NaN;
				LimbSplitter.Split split = Float.isNaN(plane) ? null : LimbSplitter.split(root, part, plane);
				piece = measure(childPath, part, vanilla != null ? vanilla : split == null ? part : split.upper(), role, matrix, Rest.of(part));
				if (piece != null) {
					piece.parent = container;
					piece.parentSpace = new Matrix4f(parentSpace);
					pieces.add(piece);
					if (split != null) {
						Matrix4f lowerMatrix = ModelMath.mul(new Matrix4f(matrix), Rest.of(split.lower()));
						String[] lowerPath = java.util.Arrays.copyOf(childPath, childPath.length + 1);
						lowerPath[childPath.length] = LimbSplitter.LOWER;
						Piece lower = measure(lowerPath, split.lower(), split.lower(), role, lowerMatrix, Rest.of(split.lower()));
						if (lower != null) {
							piece.splitY = plane;
							lower.upper = piece;
							pieces.add(lower);
						}
					}
				}
			}
			if (piece == null && topLevel) {
				riders.add(childPath);
			}
			// Inside a piece the parts follow it; inside a part that rides the torso, they follow that part.
			collect(root, part, childPath, matrix, piece != null ? piece : container, false, pieces, riders);
		}
	}

	/** The vanilla parts behind a resource pack's model being built (see EmfCompat), or null. Building runs on one thread. */
	private static ModelPart vanillaRoot;

	private static ModelPart vanillaPart(ModelPart root, String[] path) {
		ModelPart part = root;
		for (String name : path) {
			part = ((ModelPartAccessor) (Object) part).ragdolldeaths$children().get(name);
			if (part == null) {
				return null;
			}
		}
		return part;
	}

	/** Whether the model being built is a shulker's. Building runs on one thread. */
	private static boolean shellModel;

	/**
	 * The part's role. Parts named after the body with a number ({@code body0}, a spider's) count as bodies too, and
	 * a shulker is a single box (see shellModel).
	 */
	private static JointRole roleOf(String name) {
		if (shellModel) {
			// A shulker is one rigid box around its whole shell, lid and head: the shell collides as the full cube it
			// is, on every side, and the head stays tucked inside it as it tumbles.
			return null;
		}
		JointRole role = JointNames.roleOf(JointNames.tokens(name));
		if (role == null && name.matches("(body|torso)\\d+")) {
			return JointRole.BODY;
		}
		return role;
	}

	private static boolean simulated(JointRole role) {
		return switch (role) {
			case BODY, HEAD, NECK, ARM, LEG, FRONT_LEG, HIND_LEG, MID_LEG, WING, TAIL, TAIL_SEGMENT, TENTACLE, FIN, ROD -> true;
			default -> false;
		};
	}

	/**
	 * Which pieces can hang inside which: anything inside a body, a head on a neck, and chains of the same kind
	 * (a bat's wing tip on its wing, a tail's segments). Anything else inside a piece just rides on it.
	 */
	private static boolean allowedIn(Piece container, JointRole role) {
		if (container == null) {
			return true;
		}
		return switch (container.role) {
			case BODY -> role != JointRole.BODY;
			case NECK -> role == JointRole.HEAD;
			case WING -> role == JointRole.WING;
			case TAIL, TAIL_SEGMENT -> role == JointRole.TAIL || role == JointRole.TAIL_SEGMENT || role == JointRole.FIN;
			case TENTACLE -> role == JointRole.TENTACLE;
			case FIN -> role == JointRole.FIN;
			default -> false;
		};
	}

	/**
	 * The body part a piece beside the torso hangs from: the torso, or another body part when the piece is attached
	 * nearer to that one (a snow golem's head sits on its upper body, not on the big lower one).
	 */
	private static Piece nearestBody(List<Piece> pieces, Piece piece) {
		Piece torso = pieces.getFirst();
		if (piece.role == JointRole.BODY) {
			return torso;
		}
		Vector3f pivot = piece.matrix.transformPosition(new Vector3f());
		Piece nearest = torso;
		float best = Float.MAX_VALUE;
		for (Piece other : pieces) {
			if (other.role != JointRole.BODY || other.upper != null || other.parent != null && other != torso) {
				continue;
			}
			float distance = other.matrix.transformPosition(other.centre, new Vector3f()).distance(pivot);
			if (distance < best) {
				best = distance;
				nearest = other;
			}
		}
		return nearest;
	}

	/** One piece around every cube in the model, for a model whose parts have no roles. */
	private static Piece lump(ModelPart root, Matrix4f rootMatrix) {
		float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (ModelPart child : ((ModelPartAccessor) (Object) root).ragdolldeaths$children().values()) {
			extend(child, ModelMath.mul(new Matrix4f(), child), bounds);
		}
		if (bounds[0] > bounds[3]) {
			return null;
		}
		Piece piece = new Piece();
		piece.path = new String[0];
		piece.part = root;
		piece.role = JointRole.BODY;
		piece.matrix = new Matrix4f(rootMatrix);
		piece.parentSpace = new Matrix4f(rootMatrix);
		piece.rest = Rest.ZERO;
		piece.died = new float[3];
		piece.centre.set((bounds[0] + bounds[3]) / 2.0F, (bounds[1] + bounds[4]) / 2.0F, (bounds[2] + bounds[5]) / 2.0F);
		piece.half.set((bounds[3] - bounds[0]) / 2.0F, (bounds[4] - bounds[1]) / 2.0F, (bounds[5] - bounds[2]) / 2.0F);
		piece.weight = piece.half.x * piece.half.y * piece.half.z;
		return piece.weight > 0.0 ? piece : null;
	}

	/** Grows the bounds (in the model root's space, in sixteenths) around a part's cubes and its children's. */
	private static void extend(ModelPart part, Matrix4f matrix, float[] bounds) {
		if (!part.visible) {
			return;
		}
		Vector3f corner = new Vector3f();
		for (ModelPart.Cube cube : ((ModelPartAccessor) (Object) part).ragdolldeaths$cubes()) {
			for (int i = 0; i < 8; i++) {
				matrix.transformPosition(corner.set(((i & 1) == 0 ? cube.minX : cube.maxX) / 16.0F, ((i & 2) == 0 ? cube.minY : cube.maxY) / 16.0F,
						((i & 4) == 0 ? cube.minZ : cube.maxZ) / 16.0F));
				bounds[0] = Math.min(bounds[0], corner.x);
				bounds[1] = Math.min(bounds[1], corner.y);
				bounds[2] = Math.min(bounds[2], corner.z);
				bounds[3] = Math.max(bounds[3], corner.x);
				bounds[4] = Math.max(bounds[4], corner.y);
				bounds[5] = Math.max(bounds[5], corner.z);
			}
		}
		for (ModelPart child : ((ModelPartAccessor) (Object) part).ragdolldeaths$children().values()) {
			extend(child, ModelMath.mul(new Matrix4f(matrix), child), bounds);
		}
	}

	private static ModelPart find(ModelPart root, String[] path) {
		ModelPart part = root;
		for (String name : path) {
			part = ((ModelPartAccessor) (Object) part).ragdolldeaths$children().get(name);
		}
		return part;
	}

	/** Measures the cubes of {@code shape} (the part itself, or the half of it that stands in for it). */
	private static Piece measure(String[] path, ModelPart part, ModelPart shape, JointRole role, Matrix4f matrix, Rest rest) {
		float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (ModelPart.Cube cube : ((ModelPartAccessor) (Object) shape).ragdolldeaths$cubes()) {
			grow(box, cube.minX, cube.minY, cube.minZ);
			grow(box, cube.maxX, cube.maxY, cube.maxZ);
		}
		if (box[0] > box[3]) {
			// A resource pack's model (Entity Model Features) keeps the vanilla part empty and hangs its own parts,
			// named EMF_..., under it: the limb's shape is theirs, where they sit under it.
			packParts(shape, new Matrix4f(), box);
		}
		if (box[0] > box[3]) {
			return null;
		}
		float minX = box[0], minY = box[1], minZ = box[2], maxX = box[3], maxY = box[4], maxZ = box[5];
		Piece piece = new Piece();
		piece.path = path;
		piece.part = part;
		piece.role = role;
		piece.matrix = matrix;
		piece.rest = rest;
		piece.died = new float[] {part.xRot, part.yRot, part.zRot};
		// Parts smaller than this many pixels every way (a parrot's feet) just ride on what they are attached to.
		if (Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) < MIN_PIECE_PIXELS) {
			return null;
		}
		// Cube corners are in pixels; the matrix works in sixteenths. A flat part (a fin, a wing) is given a little
		// thickness, so it has a box to collide with and some weight.
		piece.centre.set((minX + maxX) / 32.0F, (minY + maxY) / 32.0F, (minZ + maxZ) / 32.0F);
		piece.half.set(Math.max(maxX - minX, 1.0F) / 32.0F, Math.max(maxY - minY, 1.0F) / 32.0F, Math.max(maxZ - minZ, 1.0F) / 32.0F);
		// Torsos are denser than limbs, so the body turns about its middle rather than about its feet.
		double density = role == JointRole.BODY ? 1.4 : 1.0;
		piece.weight = density * piece.half.x * piece.half.y * piece.half.z;
		return piece.weight > 0.0 ? piece : null;
	}

	private static void grow(float[] box, float x, float y, float z) {
		box[0] = Math.min(box[0], x);
		box[1] = Math.min(box[1], y);
		box[2] = Math.min(box[2], z);
		box[3] = Math.max(box[3], x);
		box[4] = Math.max(box[4], y);
		box[5] = Math.max(box[5], z);
	}

	/** Grows the box (in the part's pixels) by the visible cubes of the resource pack parts under the part. */
	private static void packParts(ModelPart part, Matrix4f toPart, float[] box) {
		for (var child : ((ModelPartAccessor) (Object) part).ragdolldeaths$children().entrySet()) {
			ModelPart sub = child.getValue();
			if (!child.getKey().startsWith("EMF_") || !sub.visible || sub.skipDraw) {
				continue;
			}
			Matrix4f m = ModelMath.mul(new Matrix4f(toPart), sub);
			Vector3f corner = new Vector3f();
			for (ModelPart.Cube cube : ((ModelPartAccessor) (Object) sub).ragdolldeaths$cubes()) {
				for (int i = 0; i < 8; i++) {
					corner.set((i & 1) == 0 ? cube.minX : cube.maxX, (i & 2) == 0 ? cube.minY : cube.maxY, (i & 4) == 0 ? cube.minZ : cube.maxZ).div(16.0F);
					m.transformPosition(corner).mul(16.0F);
					grow(box, corner.x, corner.y, corner.z);
				}
			}
			packParts(sub, m, box);
		}
	}

	private static Body body(Piece piece, double mass, PhysicsRagdoll ragdoll, Vec3 velocity) {
		Matrix4f m = piece.matrix;
		Vector3f centre = m.transformPosition(piece.centre, new Vector3f());
		Quaternionf rotation = m.getNormalizedRotation(new Quaternionf());
		// The matrix scales the model (babies, giants); each axis of the box is scaled the same way.
		double sx = Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
		double sy = Math.sqrt(m.m10() * m.m10() + m.m11() * m.m11() + m.m12() * m.m12());
		double sz = Math.sqrt(m.m20() * m.m20() + m.m21() * m.m21() + m.m22() * m.m22());
		Body body = new Body()
				.box(Math.max(0.01, piece.half.x * sx), Math.max(0.01, piece.half.y * sy), Math.max(0.01, piece.half.z * sz), Math.max(0.05, mass))
				.position(ragdoll.camX + centre.x, ragdoll.camY + centre.y, ragdoll.camZ + centre.z)
				.rotation(rotation.x, rotation.y, rotation.z, rotation.w)
				.velocity(velocity.x, velocity.y, velocity.z);
		// Stiff arms and legs must not grip the ground, or a body ends up propped on them like a table; with little
		// grip they slide out from under it and it settles flat.
		body.friction = piece.role == JointRole.ARM || piece.role.isLeg() || piece.role == JointRole.TENTACLE ? 0.25 : 0.7;
		// Limbs and heads bounce a little when they slap the ground; the heavy torso just thuds.
		body.restitution = piece.role == JointRole.BODY ? 0.03 : 0.12;
		// The game slows everything in the air by 2% a tick; the same here keeps falls looking like the game's.
		body.linearDrag = 0.4;
		body.angularDrag = 0.4;
		if (piece.role == JointRole.WING || piece.role == JointRole.FIN) {
			// Wings and fins are light and flat: the air holds them back, so they trail and flutter as the body falls.
			body.linearDrag = 4.0;
			body.angularDrag = 3.0;
		}
		return body;
	}

	/** The part's space as seen from its body, so that body pose × offset reproduces the part's matrix. */
	private static Matrix4f offset(Body body, Matrix4f partMatrix, PhysicsRagdoll ragdoll) {
		return new Matrix4f()
				.rotation(new Quaternionf((float) -body.qx, (float) -body.qy, (float) -body.qz, (float) body.qw))
				.translate((float) (ragdoll.camX - body.x), (float) (ragdoll.camY - body.y), (float) (ragdoll.camZ - body.z))
				.mul(partMatrix);
	}

	/**
	 * The ball joint that hangs a piece from the piece it is attached to, at its own pivot: a head, an arm or a leg
	 * from the torso, a wing from a bat's body, a tentacle from a squid's.
	 */
	private static Joint socket(Piece from, Piece piece, PhysicsRagdoll ragdoll) {
		Matrix4f now = piece.matrix;
		// Where this part would be if it hung at rest from the piece it hangs from, as that piece is now.
		Matrix4f rest;
		if (piece.parent == from) {
			rest = new Matrix4f(piece.parentSpace);
		} else {
			rest = new Matrix4f(from.matrix).mul(ModelMath.mul(new Matrix4f(), from.rest).invertAffine())
					.mul(new Matrix4f(from.parentSpace).invertAffine()).mul(piece.parentSpace);
		}
		ModelMath.mul(rest, piece.rest);

		Vector3f anchor = now.transformPosition(new Vector3f());
		// The limb's own direction: from its pivot to the middle of its cubes.
		Vector3f along = new Vector3f(piece.centre);
		if (along.lengthSquared() < 1.0E-4F) {
			along.set(0.0F, 1.0F, 0.0F);
		}
		Vector3f restTwist = rest.transformDirection(along, new Vector3f()).normalize();
		Vector3f restRef = rest.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
		Vector3f twist = now.transformDirection(along, new Vector3f()).normalize();
		Vector3f ref = now.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
		Joint joint = Joint.ball(from.body, piece.body, ragdoll.camX + anchor.x, ragdoll.camY + anchor.y, ragdoll.camZ + anchor.z,
				restTwist.x, restTwist.y, restTwist.z, restRef.x, restRef.y, restRef.z, twist.x, twist.y, twist.z, ref.x, ref.y, ref.z);
		limit(joint, piece, along);
		return joint;
	}

	/**
	 * How far each kind of part can turn. Swing 1 is about the part's own X axis, where negative is forward for a
	 * hanging limb; swing 2 is sideways, positive towards the model's +X (inward for parts on the -X side). Limbs
	 * that stick out sideways (a spider's legs, a bee's) get the same reach every way, since for them those axes mean
	 * something else.
	 */
	private static void limit(Joint joint, Piece piece, Vector3f along) {
		boolean hanging = Math.abs(along.y) > 0.7F * along.length();
		float side = piece.rest.x();
		boolean minusSide = side < -0.01F, plusSide = side > 0.01F;
		switch (piece.role) {
			case HEAD -> joint.swing(rad(-50), rad(50), rad(-30), rad(30)).twist(rad(-70), rad(70)).damping(5.0);
			case NECK -> joint.swing(rad(-40), rad(40), rad(-30), rad(30)).twist(rad(-40), rad(40)).damping(5.0);
			case ARM -> {
				if (!hanging) {
					joint.swing(rad(-90), rad(90), rad(-90), rad(90)).twist(rad(-60), rad(60)).damping(3.0);
				} else if (!minusSide && !plusSide) {
					// One part for both arms (a villager's, folded): it can only swing as a whole.
					joint.swing(rad(-90), rad(40), rad(-20), rad(20)).twist(rad(-20), rad(20)).damping(4.0);
				} else {
					joint.swing(rad(-150), rad(60), minusSide ? rad(-150) : rad(-25), minusSide ? rad(25) : rad(150)).twist(rad(-80), rad(80)).damping(3.0);
				}
			}
			case LEG, FRONT_LEG, HIND_LEG, MID_LEG -> {
				if (!hanging) {
					joint.swing(rad(-55), rad(55), rad(-55), rad(55)).twist(rad(-25), rad(25)).damping(2.5);
				} else if (piece.role == JointRole.LEG) {
					joint.swing(rad(-120), rad(20), minusSide ? rad(-45) : rad(-15), minusSide ? rad(15) : rad(45)).twist(rad(-30), rad(30)).damping(3.0);
				} else {
					// Four-legged: the legs swing far forward and back, little to the side.
					joint.swing(rad(-80), rad(80), minusSide ? rad(-30) : rad(-10), minusSide ? rad(10) : rad(30)).twist(rad(-20), rad(20)).damping(3.0);
				}
			}
			case WING -> joint.swing(rad(-150), rad(150), rad(-150), rad(150)).twist(rad(-70), rad(70)).damping(1.0);
			case TAIL, TAIL_SEGMENT, TENTACLE -> joint.swing(rad(-70), rad(70), rad(-70), rad(70)).twist(rad(-30), rad(30)).damping(2.0);
			case FIN -> joint.swing(rad(-50), rad(50), rad(-50), rad(50)).twist(rad(-20), rad(20)).damping(1.5);
			default -> joint.swing(rad(-30), rad(30), rad(-30), rad(30)).twist(rad(-20), rad(20)).damping(5.0);
		}
	}

	/** The knee or elbow between the two halves of a limb: it turns about the limb's own X axis, one way only. */
	private static Joint hinge(Piece upper, Piece lower, PhysicsRagdoll ragdoll) {
		Vector3f anchor = lower.matrix.transformPosition(new Vector3f());
		Vector3f axis = upper.matrix.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
		Joint joint = Joint.hinge(upper.body, lower.body, ragdoll.camX + anchor.x, ragdoll.camY + anchor.y, ragdoll.camZ + anchor.z, axis.x, axis.y, axis.z);
		// About the model's X axis a positive turn carries a hanging part backward: a knee folds the shin back, an
		// elbow lifts the forearm forward.
		if (lower.role == JointRole.LEG) {
			joint.limits(0.0, rad(140));
		} else {
			joint.limits(rad(-140), 0.0);
		}
		return joint.damping(3.0);
	}

	/**
	 * The speed the mob was really moving at, in blocks per second: how far it went in the last tick. The knockback
	 * of the killing blow is not in here; {@link DeathBlow} adds that, on the part that was hit.
	 */
	private static Vec3 velocityOf(LivingEntity entity) {
		Vec3 moved = entity.position().subtract(entity.xo, entity.yo, entity.zo).scale(TICKS_PER_SECOND);
		// Your own player is moved by your client the moment it is hit, not later by the server as mobs are, so its
		// last tick already holds the killing blow's knockback, which DeathBlow adds as well. Only a fall is kept.
		if (entity == net.minecraft.client.Minecraft.getInstance().player && entity.getLastDamageSource() != null
				&& entity.getLastDamageSource().getSourcePosition() != null) {
			moved = new Vec3(0.0, Math.min(0.0, moved.y), 0.0);
		}
		double speed = moved.length();
		return speed > MAX_CARRIED_SPEED ? moved.scale(MAX_CARRIED_SPEED / speed) : moved;
	}

	private static double rad(double degrees) {
		return Math.toRadians(degrees);
	}
}
