package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.JointPose.Side;
import com.frostfleee.ragdolldeaths.core.JointRole;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.physics.Body;
import com.frostfleee.ragdolldeaths.physics.Joint;
import java.util.List;
import java.util.Random;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * The little a physics body still does on its own: two fall reflexes while it goes over (arms thrown out towards the
 * ground in a forward fall, chin tucked and arms back in a backward one) and, once it has landed, a few small fading
 * twitches (Leg Kicks). Everything else is physics: the body is limp from the moment it dies.
 *
 * <p>A reflex pulls a joint like a soft spring whose strength has a ceiling tied to the limb's own weight, so it can
 * throw an arm out but can never hold the body up or push it along the ground: physics always wins.
 */
final class Reflexes {
	/** The pull as a spring, in hertz, and how damped it is. Soft: a reflex, not a servo. */
	private static final double HERTZ = 5.0;
	private static final double DAMPING = 1.0;
	private static final double GRAVITY = 32.0;
	/** A torso leaning further than this (radians from upright) is falling, and the reflexes start. */
	private static final double FALLING_LEAN = 0.45;
	private static final int TWITCH_TICKS = 30;
	/** A body that never comes down stops trying after this long. */
	private static final int GIVE_UP_TICKS = 120;

	/**
	 * One joint that can move on its own.
	 *
	 * @param hinge a knee or an elbow, which never moves on its own
	 * @param nested whether the part is inside the piece it hangs from in the model, rather than beside the torso
	 * @param rest the part's angles in the model's rest pose
	 * @param weightTorque the torque the limb's own weight puts on the joint when it is held out level
	 */
	record Fibre(Joint joint, JointRole role, Side side, boolean hinge, boolean nested, float[] rest, double weightTorque) {
	}

	private final List<Fibre> fibres;
	private final float[] torsoRest;
	private final Random random;
	private final int[] nextTwitch;
	private final int[] twitchUntil;
	private int landedAt = -1;
	private boolean done;

	private final Quaterniond torsoTarget = new Quaterniond();
	private final Quaterniond target = new Quaterniond();
	private final Quaterniond scratch = new Quaterniond();
	private final Vector3d axis = new Vector3d();

	Reflexes(List<Fibre> fibres, float[] torsoRest, long seed) {
		this.fibres = fibres;
		this.torsoRest = torsoRest;
		this.random = new Random(seed);
		this.nextTwitch = new int[fibres.size()];
		this.twitchUntil = new int[fibres.size()];
	}

	/** The torque a limb's weight puts on its joint when held level: mass × gravity × how far out its middle is. */
	static double weightTorque(double mass, double lever) {
		return mass * GRAVITY * lever;
	}

	/** Whether this body has stopped moving on its own for good. */
	boolean done() {
		return done;
	}

	/**
	 * Sets every joint for the coming tick.
	 *
	 * @param ageTicks ticks since the mob died
	 */
	void update(PhysicsRagdoll ragdoll, double ageTicks, RagdollConfig config) {
		if (done) {
			return;
		}
		Body torso = ragdoll.bodies[ragdoll.torso];
		int tick = (int) ageTicks;

		// Which way the torso is going over. In model space up is -Y and the front is -Z.
		scratch.set(torso.qx, torso.qy, torso.qz, torso.qw);
		double lean = Math.acos(Math.clamp(scratch.transform(axis.set(0.0, -1.0, 0.0)).y, -1.0, 1.0));
		double facing = scratch.transform(axis.set(0.0, 0.0, -1.0)).y;
		boolean forward = facing < -0.3, backward = facing > 0.3;

		if (landedAt < 0 && tick > 6 && torso.vx * torso.vx + torso.vy * torso.vy + torso.vz * torso.vz < 1.5) {
			landedAt = tick;
		}
		// Reflexes only while it goes over; once down it is limp.
		double reflex = config.reflexes && landedAt < 0 ? Math.clamp((lean - FALLING_LEAN) / 0.5, 0.0, 1.0) : 0.0;
		boolean twitching = config.legKicks && landedAt >= 0 && tick - landedAt < TWITCH_TICKS;

		torsoTarget.rotationZYX(torsoRest[2], torsoRest[1], torsoRest[0]).conjugate();
		boolean anyAlive = false;
		for (int i = 0; i < fibres.size(); i++) {
			Fibre f = fibres.get(i);
			if (f.hinge) {
				continue;
			}
			if (twitching && twitch(f, i, tick)) {
				anyAlive = true;
				continue;
			}
			if (reflex <= 0.01 || !reflexPose(f, forward, backward, target)) {
				f.joint.relax();
				continue;
			}
			anyAlive = true;
			// A part hung from the torso beside it wants its pose relative to the torso; one inside the piece it
			// hangs from (a wing in a bat's body) already has its pose relative to that piece.
			if (!f.nested) {
				torsoTarget.mul(target, target);
			}
			f.joint.muscle(target.x, target.y, target.z, target.w, HERTZ, DAMPING, reflex * f.weightTorque * reach(f.role));
		}
		// Down and the twitches over, or it never came down: limp for good.
		boolean over = landedAt >= 0 && !twitching || tick > GIVE_UP_TICKS;
		if (!anyAlive && over) {
			done = true;
			for (Fibre f : fibres) {
				f.joint.relax();
			}
		}
	}

	/** How much stronger than its own weight a reflex is: enough to throw the limb about, never to lift the body. */
	private static double reach(JointRole role) {
		return switch (role) {
			case HEAD -> 2.0;
			case ARM -> 2.2;
			default -> 1.3;
		};
	}

	/** The part's wanted orientation in model space during a reflex, or false when this part has none for the fall. */
	private static boolean reflexPose(Fibre f, boolean forward, boolean backward, Quaterniond out) {
		double x = f.rest[0], y = f.rest[1], z = f.rest[2];
		if (forward && f.role == JointRole.ARM) {
			// Falling on its face: the arms are thrown out ahead, towards the ground.
			x = -2.0;
		} else if (backward && f.role == JointRole.HEAD) {
			// Falling on its back: the chin tucks in.
			x = 0.55;
		} else if (backward && f.role == JointRole.ARM) {
			// And the arms go back and out to meet the ground.
			x += (0.6 - x) * 0.7;
			z += ((f.side == Side.LEFT ? -0.6 : 0.6) - z) * 0.7;
		} else {
			return false;
		}
		out.rotationZYX(z, y, x);
		return true;
	}

	/**
	 * After landing, a limb now and then jerks a little from wherever it lies, weaker each time. Returns true while
	 * this fibre is in the middle of one.
	 */
	private boolean twitch(Fibre f, int i, int tick) {
		if (nextTwitch[i] == 0) {
			nextTwitch[i] = landedAt + 2 + random.nextInt(8);
		}
		if (tick < nextTwitch[i]) {
			return false;
		}
		if (tick >= twitchUntil[i]) {
			if (twitchUntil[i] > nextTwitch[i]) {
				// That jerk is over; the next one comes a little later.
				nextTwitch[i] = tick + 4 + random.nextInt(9);
				twitchUntil[i] = 0;
				f.joint.relax();
				return false;
			}
			twitchUntil[i] = tick + 2;
			// A small turn about a random axis, from the pose the limb is lying in.
			Body a = f.joint.a, b = f.joint.b;
			double angle = 0.25 + 0.25 * random.nextDouble();
			axis.set(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize();
			target.set(-a.qx, -a.qy, -a.qz, a.qw).mul(scratch.set(b.qx, b.qy, b.qz, b.qw)).rotateAxis(angle, axis);
			double fade = 1.0 - (double) (tick - landedAt) / TWITCH_TICKS;
			f.joint.muscle(target.x, target.y, target.z, target.w, 7.0, 0.7, 0.9 * fade * f.weightTorque * reach(f.role));
		}
		return true;
	}
}
