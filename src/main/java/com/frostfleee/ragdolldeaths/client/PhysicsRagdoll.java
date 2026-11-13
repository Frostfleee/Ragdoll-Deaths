package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.mixin.ModelPartAccessor;
import com.frostfleee.ragdolldeaths.physics.Body;
import com.frostfleee.ragdolldeaths.physics.Island;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import com.frostfleee.ragdolldeaths.core.JointRole;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One dead mob as a physics body: the simulated parts, and which model part each one moves.
 *
 * <p>It is built from the model exactly as it was drawn on the frame the mob died (see {@link RagdollBuilder}), so
 * the body starts in the pose the player last saw. After that the game's own animation no longer matters: every
 * frame each driven model part is given the transform that puts it where its body is.
 */
final class PhysicsRagdoll {
	/** Physics steps per game tick; the engine's step is 1/60 s and a tick is 1/20 s. */
	static final int STEPS_PER_TICK = 3;

	/**
	 * A model part moved by a body.
	 *
	 * @param path the part's names from the model root down, so the same part can be found in armor and other
	 *             layer models
	 * @param offset the part's space as seen from the body: body pose × offset is where the part must be drawn
	 * @param rest the rest pose of the part in the model the ragdoll was built from
	 * @param hidden a part that is not drawn once the mob is dead (a breeze's whirlwind)
	 * @param splitY where the part is cut into two halves (its own Y, in pixels) when it is a limb with a knee or
	 *               an elbow, else NaN
	 */
	record Link(String[] path, int body, Matrix4f offset, Rest rest, float splitY, boolean hidden) {
	}

	/** A link found in one particular model. */
	private record Binding(ModelPart part, Link link, Matrix4f correction, int parent, ModelPart[] between, LimbSplitter.Split split) {
	}

	final LivingEntity entity;
	/** The mob's own model (not an armor or other layer model), noted each frame before anything is drawn. */
	Model mainModel;
	/** The camera the current frame's matrices are relative to. */
	double camX, camY, camZ;
	/** How far into the current tick the frame being drawn is. */
	float alpha;
	boolean failed;
	/** Skeletons, slimes, the snow golem, blazes and breezes do more than fall; null for everything else. */
	SpecialBodies special;
	// How each body is drawn reshaped: scaled along its most upright axis and across it, and lowered so that its
	// underside stays where it is.
	private double[] stretch, across, lowered;
	private int[] upAxis;
	/** What each body is (head, body, arm, leg), in body order. */
	JointRole[] roles;
	/** How many ticks in a row some part of the body has been in lava. */
	int lavaTicks;
	/**
	 * Do not draw the head's own cube: a snow golem's pumpkin is drawn around its snow head, and once the head has
	 * rolled off the body the snow shows through the pumpkin's underside. A sheared golem keeps its head.
	 */
	boolean hideHead;
	/** Parts whose cubes were hidden for a ragdoll, by model, so the model is whole again for living mobs. */
	private static final Map<ModelPart, List<ModelPart>> HIDDEN = new java.util.WeakHashMap<>();

	/** Shows every part hidden for a ragdoll again; called whenever a model is about to be drawn for anything. */
	static void showHidden(ModelPart root) {
		if (HIDDEN.isEmpty()) {
			return;
		}
		List<ModelPart> parts = HIDDEN.remove(root);
		if (parts != null) {
			for (ModelPart part : parts) {
				part.skipDraw = false;
			}
		}
	}

	// For impact sounds: each part's speed after the last tick, whether it was in water, and the wait before the next sound.
	double[] heard;
	boolean[] wasWet;
	boolean[] wet;
	int quiet;
	/** Physics was running over budget and this was the oldest body: it vanishes now. */
	boolean retired;
	/** The body has touched lava. */
	boolean charred;
	/** The texture the mob is drawn with, noted each frame, and this body's own burnt copy of it once it has one. */
	Object baseTexture;
	Scorch scorch;
	private boolean scorchFailed;

	/** Burns the pixels that are in lava this tick. */
	void scorchTick() {
		if (scorch == null && !scorchFailed) {
			scorch = Scorch.of(baseTexture);
			// A texture that cannot be read (a player's skin) leaves the whole body darkened instead.
			scorchFailed = scorch == null && baseTexture != null;
		}
		if (scorch != null) {
			scorch.update(this);
		}
	}

	/** Whether the body is burnt but has no texture of its own to show it on. */
	boolean charredWhole() {
		return charred && scorch == null && scorchFailed;
	}
	/** The dying muscles, while there are any. */
	Muscles muscles;
	/** What the killing blow was read as, for the debug log. */
	String blow = "";
	/** Where the torso was when the body was built, to tell how far it was thrown. */
	double startX, startY, startZ;

	Island island;
	Body[] bodies;
	Link[] links;
	int torso;

	/** Each body's pose at the start of the current tick and after each of its physics steps: x, y, z, then the quaternion. */
	private double[][] frames;
	private final Map<ModelPart, Binding[]> bindings = new IdentityHashMap<>();
	private Matrix4f[] drawn = new Matrix4f[0];

	// Scratch for the render thread.
	private final Matrix4f rootMatrix = new Matrix4f();
	private final Matrix4f inverse = new Matrix4f();
	private final Matrix4f target = new Matrix4f();
	private final Matrix4f local = new Matrix4f();
	private final Quaternionf rotation = new Quaternionf();
	private final Vector3f angles = new Vector3f();
	private final double[] at = new double[7];

	PhysicsRagdoll(LivingEntity entity) {
		this.entity = entity;
	}

	boolean built() {
		return island != null;
	}

	void finishBuild(Island island, Body[] bodies, Link[] links, int torso) {
		this.island = island;
		this.bodies = bodies;
		this.links = links;
		this.torso = torso;
		startX = bodies[torso].x;
		startY = bodies[torso].y;
		startZ = bodies[torso].z;
		stretch = new double[bodies.length];
		across = new double[bodies.length];
		lowered = new double[bodies.length];
		upAxis = new int[bodies.length];
		java.util.Arrays.fill(stretch, 1.0);
		java.util.Arrays.fill(across, 1.0);
		java.util.Arrays.fill(upAxis, -1);
		frames = new double[STEPS_PER_TICK + 1][bodies.length * 7];
		for (int i = 0; i <= STEPS_PER_TICK; i++) {
			capture(i);
		}
	}

	/**
	 * Draws a body squashed or stretched: scaled by {@code height} along whichever of its own axes points most
	 * nearly up, and by {@code spread} across it.
	 *
	 * @param keepUnderside lower it so that its underside stays where it is (a blob squashing onto the ground). When
	 *                      false it is scaled about its middle and the axis, once chosen, is kept (melting snow,
	 *                      whose box shrinks with it).
	 * @return the axis it is scaled along: 0, 1 or 2
	 */
	int reshape(int body, double height, double spread, boolean keepUnderside) {
		Body b = bodies[body];
		if (keepUnderside || upAxis[body] < 0) {
			// The middle row of the body's rotation says how much each of its axes points up.
			double ux = Math.abs(2 * (b.qx * b.qy + b.qw * b.qz));
			double uy = Math.abs(1 - 2 * (b.qx * b.qx + b.qz * b.qz));
			double uz = Math.abs(2 * (b.qy * b.qz - b.qw * b.qx));
			upAxis[body] = ux > uy && ux > uz ? 0 : uy >= uz ? 1 : 2;
		}
		stretch[body] = height;
		across[body] = spread;
		lowered[body] = keepUnderside ? b.halfHeight() * (1.0 - height) : 0.0;
		return upAxis[body];
	}

	/** The tick is over: its last pose becomes the next tick's first. */
	void startTick() {
		double[] last = frames[STEPS_PER_TICK];
		for (int i = 0; i < STEPS_PER_TICK; i++) {
			System.arraycopy(last, 0, frames[i], 0, last.length);
		}
	}

	/** Records every body's pose after the given physics step of this tick (1 to {@link #STEPS_PER_TICK}). */
	void capture(int step) {
		double[] f = frames[step];
		for (int i = 0, k = 0; i < bodies.length; i++, k += 7) {
			Body b = bodies[i];
			f[k] = b.x;
			f[k + 1] = b.y;
			f[k + 2] = b.z;
			f[k + 3] = b.qx;
			f[k + 4] = b.qy;
			f[k + 5] = b.qz;
			f[k + 6] = b.qw;
		}
	}

	/** A body's pose for the frame being drawn, between two recorded steps, into {@link #at}. */
	private void interpolate(int body) {
		double s = Math.clamp(alpha, 0.0F, 1.0F) * STEPS_PER_TICK;
		int i = Math.min(STEPS_PER_TICK - 1, (int) s);
		double t = s - i;
		double[] a = frames[i], b = frames[i + 1];
		int k = body * 7;
		at[0] = a[k] + (b[k] - a[k]) * t;
		at[1] = a[k + 1] + (b[k + 1] - a[k + 1]) * t;
		at[2] = a[k + 2] + (b[k + 2] - a[k + 2]) * t;
		// The two quaternions may describe the same turn with opposite signs; blend along the short way.
		double sign = a[k + 3] * b[k + 3] + a[k + 4] * b[k + 4] + a[k + 5] * b[k + 5] + a[k + 6] * b[k + 6] < 0.0 ? -1.0 : 1.0;
		double qx = a[k + 3] + (sign * b[k + 3] - a[k + 3]) * t;
		double qy = a[k + 4] + (sign * b[k + 4] - a[k + 4]) * t;
		double qz = a[k + 5] + (sign * b[k + 5] - a[k + 5]) * t;
		double qw = a[k + 6] + (sign * b[k + 6] - a[k + 6]) * t;
		double n = 1.0 / Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
		at[3] = qx * n;
		at[4] = qy * n;
		at[5] = qz * n;
		at[6] = qw * n;
	}

	/** Where the torso is on the frame being drawn. */
	Vec3 torsoPosition(float partialTicks) {
		float keep = alpha;
		alpha = partialTicks;
		interpolate(torso);
		alpha = keep;
		return new Vec3(at[0], at[1], at[2]);
	}

	/** Where the head is on the frame being drawn, or the torso for a body without one. */
	Vec3 headPosition(float partialTicks) {
		int head = torso;
		for (int i = 0; i < roles.length; i++) {
			if (roles[i] == JointRole.HEAD) {
				head = i;
				break;
			}
		}
		float keep = alpha;
		alpha = partialTicks;
		interpolate(head);
		alpha = keep;
		return new Vec3(at[0], at[1], at[2]);
	}

	/** A box around every part, for culling. */
	AABB bounds() {
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (Body b : bodies) {
			double reach = Math.sqrt(b.hx * b.hx + b.hy * b.hy + b.hz * b.hz) + 0.25;
			minX = Math.min(minX, b.x - reach);
			minY = Math.min(minY, b.y - reach);
			minZ = Math.min(minZ, b.z - reach);
			maxX = Math.max(maxX, b.x + reach);
			maxY = Math.max(maxY, b.y + reach);
			maxZ = Math.max(maxZ, b.z + reach);
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	/**
	 * Gives every driven part of a model the transform that draws it on its body.
	 *
	 * @param pose the matrix the model is about to be drawn with: model space to camera-relative world space
	 */
	void write(Model model, Matrix4f pose) {
		ModelPart root = Compat.root(model);
		Binding[] bound = bindings.get(root);
		if (bound == null) {
			bound = bind(root);
			bindings.put(root, bound);
			if (bound.length > drawn.length) {
				drawn = new Matrix4f[bound.length];
				for (int i = 0; i < drawn.length; i++) {
					drawn[i] = new Matrix4f();
				}
			}
		}
		rootMatrix.set(pose);
		ModelMath.mul(rootMatrix, root);
		for (int i = 0; i < bound.length; i++) {
			Binding b = bound[i];
			int body = b.link.body();
			interpolate(body);
			target.translation((float) (at[0] - camX), (float) (at[1] - lowered[body] - camY), (float) (at[2] - camZ))
					.rotate(rotation.set((float) at[3], (float) at[4], (float) at[5], (float) at[6]));
			if (stretch[body] != 1.0 || across[body] != 1.0) {
				float along = (float) stretch[body], side = (float) across[body];
				target.scale(upAxis[body] == 0 ? along : side, upAxis[body] == 1 ? along : side, upAxis[body] == 2 ? along : side);
			}
			target.mul(b.link.offset());
			if (b.correction != null) {
				target.mul(b.correction);
			}
			// What the part hangs from: the model root, or another driven part, then any plain parts in between.
			inverse.set(b.parent < 0 ? rootMatrix : drawn[b.parent]);
			for (ModelPart step : b.between) {
				ModelMath.mul(inverse, step);
			}
			inverse.invertAffine().mul(target, local);
			drawn[i].set(target);
			ModelMath.store(local, b.part, rotation, angles);
			if (hideHead && roles[body] == JointRole.HEAD && !b.part.skipDraw) {
				b.part.skipDraw = true;
				HIDDEN.computeIfAbsent(root, r -> new ArrayList<>()).add(b.part);
			}
			if (b.link.hidden()) {
				b.part.xScale = b.part.yScale = b.part.zScale = 0.0F;
			}
			if (b.split != null) {
				LimbSplitter.show(b.split);
			}
		}
	}

	/** Finds this ragdoll's driven parts in a model: the mob's own, or a layer's with the same part names. */
	private Binding[] bind(ModelPart root) {
		List<Binding> found = new ArrayList<>();
		List<String[]> paths = new ArrayList<>();
		for (Link link : links) {
			ModelPart part = root;
			for (String name : link.path()) {
				part = part == null ? null : ((ModelPartAccessor) (Object) part).ragdolldeaths$children().get(name);
			}
			if (part == null) {
				continue;
			}
			// A limb with a knee or an elbow gets its two halves in this model too (armor has legs of its own).
			LimbSplitter.Split split = Float.isNaN(link.splitY()) ? null : LimbSplitter.split(root, part, link.splitY());
			// The nearest driven part above this one, and the undriven parts between the two.
			int parent = -1;
			int parentDepth = 0;
			for (int i = 0; i < paths.size(); i++) {
				String[] other = paths.get(i);
				if (other.length < link.path().length && other.length > parentDepth && startsWith(link.path(), other)) {
					parent = i;
					parentDepth = other.length;
				}
			}
			List<ModelPart> between = new ArrayList<>();
			ModelPart walk = root;
			for (int depth = 0; depth < link.path().length - 1; depth++) {
				walk = ((ModelPartAccessor) (Object) walk).ragdolldeaths$children().get(link.path()[depth]);
				if (depth >= parentDepth) {
					between.add(walk);
				}
			}
			Matrix4f correction = null;
			if (!sameRest(Rest.of(part), link.rest())) {
				correction = ModelMath.mul(new Matrix4f(), link.rest()).invertAffine();
				ModelMath.mul(correction, Rest.of(part));
			}
			found.add(new Binding(part, link, correction, parent, between.toArray(ModelPart[]::new), split));
			paths.add(link.path());
		}
		return found.toArray(Binding[]::new);
	}

	private static boolean startsWith(String[] path, String[] prefix) {
		for (int i = 0; i < prefix.length; i++) {
			if (!path[i].equals(prefix[i])) {
				return false;
			}
		}
		return true;
	}

	private static boolean sameRest(Rest a, Rest b) {
		return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z()) < 1.0E-3F
				&& Math.abs(a.xRot() - b.xRot()) + Math.abs(a.yRot() - b.yRot()) + Math.abs(a.zRot() - b.zRot()) < 1.0E-3F
				&& Math.abs(a.xScale() - b.xScale()) + Math.abs(a.yScale() - b.yScale()) + Math.abs(a.zScale() - b.zScale()) < 1.0E-3F;
	}

	/** For the visual test: the fastest any part is moving, in blocks per second. */
	double maxSpeed() {
		double max = 0.0;
		for (Body b : bodies) {
			max = Math.max(max, Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz));
		}
		return max;
	}

	/** For the visual test: how the torso is drawn reshaped, and its real box. */
	String shapeReport() {
		Body b = bodies[torso];
		return String.format(java.util.Locale.ROOT, " drawn height x%.2f spread x%.2f axis %d lowered %.2f, box half (%.2f %.2f %.2f) q (%.2f %.2f %.2f %.2f)",
				stretch[torso], across[torso], upAxis[torso], lowered[torso], b.hx, b.hy, b.hz, b.qx, b.qy, b.qz, b.qw);
	}

	/** For the visual test: where each part whose centre is inside a block is, and in what. */
	String insideReport() {
		Level level = entity.level();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < bodies.length; i++) {
			Body b = bodies[i];
			pos.set(b.x, b.y, b.z);
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			for (AABB box : shape.toAabbs()) {
				if (box.deflate(0.03).contains(b.x - pos.getX(), b.y - pos.getY(), b.z - pos.getZ())) {
					out.append(String.format(java.util.Locale.ROOT, " part %d at (%.2f, %.2f, %.2f) half (%.2f, %.2f, %.2f) in %s;", i, b.x, b.y, b.z, b.hx, b.hy, b.hz,
							level.getBlockState(pos).getBlock().getDescriptionId()));
					break;
				}
			}
		}
		return out.toString();
	}

	/** For the visual test: how many parts have their centre inside a block's collision shape. */
	int partsInsideBlocks() {
		Level level = entity.level();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int inside = 0;
		for (Body b : bodies) {
			pos.set(b.x, b.y, b.z);
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (shape.isEmpty()) {
				continue;
			}
			double lx = b.x - pos.getX(), ly = b.y - pos.getY(), lz = b.z - pos.getZ();
			for (AABB box : shape.toAabbs()) {
				if (box.deflate(0.03).contains(lx, ly, lz)) {
					inside++;
					break;
				}
			}
		}
		return inside;
	}
}
