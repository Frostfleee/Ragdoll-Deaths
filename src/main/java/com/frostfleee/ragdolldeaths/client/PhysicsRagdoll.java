package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.mixin.ModelPartAccessor;
import com.frostfleee.ragdolldeaths.physics.Body;
import com.frostfleee.ragdolldeaths.physics.Island;
import com.frostfleee.ragdolldeaths.physics.Joint;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import com.frostfleee.ragdolldeaths.core.JointRole;
import com.frostfleee.ragdolldeaths.core.LimbBend;
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

	/**
	 * A link found in one particular model.
	 *
	 * @param hinge for the lower half of a limb, the knee or elbow it hangs from, else null
	 * @param limb for the same, the limb's two halves
	 */
	private record Binding(ModelPart part, Link link, Matrix4f correction, int parent, ModelPart[] between, LimbSplitter.Split split,
			Joint hinge, LimbSplitter.Split limb, Rest rest) {
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

	/** Which bodies have been in lava, in body order; null until one has. A held item burns with the hand that held it. */
	boolean[] burnt;
	/** The living things near the body this tick, which push it (see BodyPushing); null when none were looked for. */
	List<LivingEntity> walkers;
	/** Which burnt texture's turn it is: one is tested a tick, to spread the cost. */
	private int scorchTurn;
	/** Which bodies are in lava this tick: only their pixels are tested for burning (see Scorch). */
	boolean[] lavaNow;

	/** An armor piece or other layer drawn on this body: its texture, its model, and its burnt copy once it has one. */
	private static final class Layer {
		final Object base;
		final Model model;
		/** The model's parts this texture is drawn on, noted as they are drawn (a helmet: the head and hat). */
		final java.util.Set<ModelPart> parts = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		Scorch scorch;
		boolean failed;

		Layer(Object base, Model model) {
			this.base = base;
			this.model = model;
		}
	}

	/** At most this many layers burn: a body never wears more, and a strange mod cannot make it grow. */
	private static final int MAX_LAYERS = 12;
	private final List<Layer> layers = new ArrayList<>();

	/** Burns the pixels that are in lava this tick, on the body and on every layer it wears. */
	void scorchTick() {
		if (scorch == null && !scorchFailed) {
			scorch = Scorch.of(baseTexture);
			// A texture that cannot be read (a player's skin) leaves the whole body darkened instead.
			scorchFailed = scorch == null && baseTexture != null;
		}
		for (Layer layer : layers) {
			if (layer.scorch == null && !layer.failed) {
				layer.scorch = Scorch.of(layer.base, layer.model, layer.parts);
				layer.failed = layer.scorch == null;
			}
		}
		// One texture a tick, the skin and each layer in turn: testing them all every tick cost enough time for the
		// physics to let bodies go early. A burn shows a few ticks later at most.
		List<Scorch> all = new ArrayList<>();
		if (scorch != null) {
			all.add(scorch);
		}
		for (Layer layer : layers) {
			if (layer.scorch != null) {
				all.add(layer.scorch);
			}
		}
		if (!all.isEmpty()) {
			all.get(scorchTurn++ % all.size()).update(this);
		}
	}

	/** Lets go of every burnt texture copy; the body is gone. */
	void closeScorches() {
		if (scorch != null) {
			scorch.close();
		}
		for (Layer layer : layers) {
			if (layer.scorch != null) {
				layer.scorch.close();
			}
		}
	}

	/**
	 * The texture to draw a layer with (armor, horse armor, a carpet, or a second skin such as a horse's markings or a
	 * villager's clothes): its own burnt copy once lava has reached it, else the texture it has. An item that does not
	 * burn in lava (netherite) keeps its own. Notes, as it is drawn, which parts of the layer's model the texture covers,
	 * so only those burn. A second skin is often drawn on the mob's own model, with a texture of its own.
	 */
	@SuppressWarnings("unchecked")
	<T> T layerTexture(T base, Model model, net.minecraft.world.item.ItemStack stack) {
		if (base == null || model == null || !built() || fireproof(stack) || model == mainModel && base.equals(baseTexture)) {
			return base;
		}
		Layer layer = null;
		for (Layer candidate : layers) {
			if (candidate.scorch != null && candidate.scorch.id.equals(base)) {
				// Already this layer's burnt copy (a game version that hands the texture on to a second method).
				return base;
			}
		}
		for (Layer candidate : layers) {
			if (candidate.model == model && candidate.base.equals(base)) {
				layer = candidate;
				break;
			}
		}
		if (layer == null) {
			if (layers.size() >= MAX_LAYERS) {
				return base;
			}
			layer = new Layer(base, model);
			layers.add(layer);
		}
		ModelPart root = Compat.root(model);
		for (Link link : links) {
			ModelPart part = Scorch.find(root, link.path());
			if (part != null && part.visible) {
				layer.parts.add(part);
			}
		}
		return layer.scorch != null ? (T) layer.scorch.id : base;
	}

	/** For the self-test: each layer noted on this body, with whether it has burnt, e.g. "netherite.png kept". */
	List<String> layerReport() {
		List<String> lines = new ArrayList<>();
		for (Layer layer : layers) {
			String name = layer.base.toString();
			lines.add(name.substring(name.lastIndexOf('/') + 1) + (layer.scorch != null ? " burnt" : " kept"));
		}
		return lines;
	}

	/** For the self-test: whether a layer whose texture has this in its name has pixels burnt by lava. */
	boolean layerBurnt(String name) {
		for (Layer layer : layers) {
			if (layer.base.toString().contains(name) && layer.scorch != null && layer.scorch.anyBurnt()) {
				return true;
			}
		}
		return false;
	}

	/** Whether the item shrugs off lava, as netherite does when it is dropped in it. */
	boolean fireproof(net.minecraft.world.item.ItemStack stack) {
		return stack != null && !stack.isEmpty() && !stack.canBeHurtBy(entity.level().damageSources().lava());
	}

	/**
	 * Whether the item held in that hand is burnt: the arm holding it went into lava, and the item burns. A mob with no
	 * arm of that name (an item held in a fox's mouth) burns its item once any of it went into lava.
	 */
	boolean heldItemBurnt(net.minecraft.world.entity.HumanoidArm arm) {
		if (!charred || burnt == null) {
			return false;
		}
		net.minecraft.world.item.ItemStack stack = arm == entity.getMainArm() ? entity.getMainHandItem() : entity.getOffhandItem();
		if (fireproof(stack)) {
			return false;
		}
		String name = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? "right_arm" : "left_arm";
		boolean found = false;
		for (Link link : links) {
			String[] path = link.path();
			if (path.length > 0 && path[path.length - 1].equals(name)) {
				found = true;
				if (burnt[link.body()]) {
					return true;
				}
			}
		}
		return !found;
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
	private final Matrix4f above = new Matrix4f();
	private final PoseWatch watch = new PoseWatch();
	// What is drawn eases after the physics (see LimbBend.follow): each part's turn relative to the torso, each knee's bend.
	private double[] easedTurn, easedBend, easedAt;
	private final Quaternionf torsoTurn = new Quaternionf(), bodyTurn = new Quaternionf(), relative = new Quaternionf(), eased = new Quaternionf(),
			drawnTurn = new Quaternionf();
	private final Vector3f pivot = new Vector3f(), torsoAt = new Vector3f();
	private final Quaternionf torsoFix = new Quaternionf();
	/** How many physics ticks this body has had. */
	private long ticksRun;
	private final Quaternionf rotation = new Quaternionf();
	private final Vector3f angles = new Vector3f();
	private final double[] at = new double[7];

	/**
	 * For a rider that died on its mount: the speed it leaves its seat with, in blocks per second (the mount's momentum,
	 * a push away from the blow and a little hop). Null for everything else, which starts with the speed it really had.
	 */
	Vec3 seatThrow;
	/** Whether this mob has blood (see Blood), the body the killing blow struck, and how long it has dripped. */
	boolean bleeds;
	int bleedingBody = -1;
	int bleedTicks;

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
		easedTurn = new double[bodies.length * 4];
		easedBend = new double[bodies.length];
		easedAt = new double[bodies.length];
		java.util.Arrays.fill(easedAt, -1.0);
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
		ticksRun++;
		double[] last = frames[STEPS_PER_TICK];
		for (int i = 0; i < STEPS_PER_TICK; i++) {
			System.arraycopy(last, 0, frames[i], 0, last.length);
		}
	}

	/** For the self-test only: moves the whole body, as if it had been carried there, with the poses it is drawn from. */
	void moveForTest(double dx, double dy, double dz) {
		watch.reset();
		for (Body b : bodies) {
			b.x += dx;
			b.y += dy;
			b.z += dz;
		}
		for (double[] f : frames) {
			for (int k = 0; k + 2 < f.length; k += 7) {
				f[k] += dx;
				f[k + 1] += dy;
				f[k + 2] += dz;
			}
		}
		island.asleep = false;
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
		double now = ticksRun + alpha;
		interpolate(torso);
		torsoTurn.set((float) at[3], (float) at[4], (float) at[5], (float) at[6]);
		torsoAt.set((float) (at[0] - camX), (float) (at[1] - lowered[torso] - camY), (float) (at[2] - camZ));
		steadyTorso(now);
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
			if (!brokenFree(body)) {
				if (body != torso && b.hinge == null) {
					steady(body, now);
				}
				// The whole body turns together about its middle, so every part stays joined.
				target.translateLocal(-torsoAt.x, -torsoAt.y, -torsoAt.z).rotateLocal(torsoFix).translateLocal(torsoAt);
			}
			// What the part hangs from: the model root, or another driven part, then any plain parts in between.
			inverse.set(b.parent < 0 ? rootMatrix : drawn[b.parent]);
			for (ModelPart step : b.between) {
				ModelMath.mul(inverse, step);
			}
			above.set(inverse);
			inverse.invertAffine().mul(target, local);
			if (b.hinge != null && !b.hinge.broken) {
				bendLimb(b);
				target.set(above).mul(local);
			}
			drawn[i].set(target);
			ModelMath.store(local, b.part, rotation, angles);
			if (PoseWatch.ON && !brokenFree(b.link.body())) {
				watch.check(this, b, b.part, b.link.path(), above, target, ticksRun + alpha, camX, camY, camZ);
			}
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

	/** Eases the torso after its physics, which can jolt on landing; {@link #torsoFix} is the difference to draw. */
	private void steadyTorso(double now) {
		torsoFix.identity();
		if (brokenFree(torso)) {
			return;
		}
		int k = torso * 4;
		if (easedAt[torso] < 0.0) {
			store(k, torsoTurn);
			easedAt[torso] = now;
			return;
		}
		eased.set((float) easedTurn[k], (float) easedTurn[k + 1], (float) easedTurn[k + 2], (float) easedTurn[k + 3]);
		double ticks = now - easedAt[torso];
		if (ticks > 0.0) {
			double dot = Math.min(1.0, Math.abs(eased.dot(torsoTurn)));
			eased.slerp(torsoTurn, (float) LimbBend.follow(2.0 * Math.acos(dot), ticks)).normalize();
			store(k, eased);
			easedAt[torso] = now;
		}
		eased.mul(torsoTurn.conjugate(relative), torsoFix);
	}

	/** Turns the part being placed from where its physics body is to where it is drawn, about the part's own pivot. */
	private void steady(int body, double now) {
		bodyTurn.set((float) at[3], (float) at[4], (float) at[5], (float) at[6]);
		torsoTurn.conjugate(relative).mul(bodyTurn);
		int k = body * 4;
		if (easedAt[body] < 0.0) {
			store(k, relative);
			easedAt[body] = now;
			return;
		}
		eased.set((float) easedTurn[k], (float) easedTurn[k + 1], (float) easedTurn[k + 2], (float) easedTurn[k + 3]);
		double ticks = now - easedAt[body];
		if (ticks > 0.0) {
			double dot = Math.min(1.0, Math.abs(eased.dot(relative)));
			eased.slerp(relative, (float) LimbBend.follow(2.0 * Math.acos(dot), ticks)).normalize();
			store(k, eased);
			easedAt[body] = now;
		}
		// From the physics pose to the drawn one, with the pivot left on its joint.
		torsoTurn.mul(eased, drawnTurn).mul(bodyTurn.conjugate());
		target.getTranslation(pivot);
		target.translateLocal(-pivot.x, -pivot.y, -pivot.z).rotateLocal(drawnTurn).translateLocal(pivot);
	}

	private void store(int k, Quaternionf q) {
		easedTurn[k] = q.x;
		easedTurn[k + 1] = q.y;
		easedTurn[k + 2] = q.z;
		easedTurn[k + 3] = q.w;
	}

	/**
	 * Draws the lower half of a limb exactly on its hinge. The physics only comes close (its joints give a little under
	 * load), and on screen even a small slip would open the limb or bend it the wrong way.
	 */
	private void bendLimb(Binding b) {
		double angle = Math.atan2(local.m12(), local.m11());
		boolean knee = roles[b.link.body()] == JointRole.LEG;
		if (!Double.isNaN(LimbBend.testDegrees)) {
			angle = Math.toRadians(knee ? LimbBend.testDegrees : -LimbBend.testDegrees);
		}
		angle = knee ? LimbBend.clampKnee(angle) : LimbBend.clampElbow(angle);
		if (Double.isNaN(LimbBend.testDegrees)) {
			int body = b.link.body();
			double now = ticksRun + alpha;
			if (easedAt[body] < 0.0) {
				easedBend[body] = angle;
				easedAt[body] = now;
			} else if (now > easedAt[body]) {
				easedBend[body] += (angle - easedBend[body]) * LimbBend.follow(Math.abs(angle - easedBend[body]), now - easedAt[body]);
				easedAt[body] = now;
			}
			angle = easedBend[body];
		}
		lastBend = angle;
		float slide = (float) LimbBend.slide(b.limb.thickness(), angle);
		local.translation(b.rest.x() / 16.0F, b.rest.y() / 16.0F, b.rest.z() / 16.0F)
				.rotateX((float) angle)
				.translate(0.0F, slide / 16.0F, 0.0F);
	}

	/** For the pose watch: whether this part has come off its joint (a skeleton's bones fly apart, spinning, on purpose). */
	private boolean brokenFree(int body) {
		for (Joint joint : island.joints) {
			if (joint.broken && (joint.b == bodies[body] || joint.a == bodies[body])) {
				return true;
			}
		}
		return false;
	}

	/** For the self-test: whether a player's left sleeve is cut in two with the arm under it. */
	boolean sleeveBends() {
		ModelPart root = Compat.root(mainModel);
		ModelPart beside = Scorch.find(root, new String[] {"left_sleeve"});
		if (beside != null) {
			return Scorch.find(beside, new String[] {LimbSplitter.LOWER}) != null;
		}
		return Scorch.find(root, new String[] {"left_arm", LimbSplitter.LOWER, "ragdolldeaths_left_sleeve"}) != null;
	}

	/** For the self-test: the bend of the knee or elbow drawn last, in radians. */
	double lastBend;

	/** For the self-test: how many knees and elbows the body has, and whether the physics keeps each near its limits. */
	int hinges(double[] worstDegreesOutside) {
		int count = 0;
		for (Joint joint : island.joints) {
			if (joint.type != Joint.Type.HINGE || joint.broken) {
				continue;
			}
			count++;
			double angle = joint.hingeAngle();
			double outside = Math.max(joint.lo - angle, angle - joint.hi);
			worstDegreesOutside[0] = Math.max(worstDegreesOutside[0], Math.toDegrees(outside));
		}
		return count;
	}

	/** Finds this ragdoll's driven parts in a model: the mob's own, or a layer's with the same part names. */
	private Binding[] bind(ModelPart root) {
		List<Binding> found = new ArrayList<>();
		List<String[]> paths = new ArrayList<>();
		Map<ModelPart, LimbSplitter.Split> splits = new IdentityHashMap<>();
		for (Link link : links) {
			ModelPart part = root;
			for (String name : link.path()) {
				part = part == null ? null : ((ModelPartAccessor) (Object) part).ragdolldeaths$children().get(name);
			}
			if (part == null) {
				continue;
			}
			// A limb with a knee or an elbow gets its two halves in this model too (armor has legs of its own).
			LimbSplitter.Split split = Float.isNaN(link.splitY()) ? null : LimbSplitter.split(root, part, link.splitY(), roles[link.body()] == JointRole.LEG);
			if (split != null) {
				splits.put(part, split);
			}
			// The lower half of a limb, and the hinge it turns on.
			Joint hinge = null;
			LimbSplitter.Split limb = null;
			if (link.path()[link.path().length - 1].equals(LimbSplitter.LOWER)) {
				ModelPart whole = Scorch.find(root, java.util.Arrays.copyOf(link.path(), link.path().length - 1));
				limb = splits.get(whole);
				for (Joint joint : island.joints) {
					if (limb != null && joint.type == Joint.Type.HINGE && joint.b == bodies[link.body()]) {
						hinge = joint;
					}
				}
			}
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
			found.add(new Binding(part, link, correction, parent, between.toArray(ModelPart[]::new), split, hinge, limb, Rest.of(part)));
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
