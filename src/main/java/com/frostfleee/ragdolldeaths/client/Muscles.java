package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.JointPose;
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
 * The last of a dying body's muscles. A body that goes limp the instant it dies looks like a dropped puppet; a real
 * one braces, reaches and curls for a moment first.
 *
 * <p>Each joint pulls its limb towards a wanted pose for a short while, weaker every tick, and then lets go. The
 * wanted pose is the mod's old hand-made death animation, sampled at the same moment: it is no longer what is drawn,
 * only what the dying muscles are trying to do. The pull always has a ceiling tied to the limb's own weight, so a
 * muscle can throw an arm up but can never hold the body up or push it along the ground: physics always wins.
 *
 * <p>On top of that come two reflexes (arms thrown out towards the ground in a forward fall, chin tucked and arms
 * back in a backward one) and, once the body has landed, a few small fading twitches.
 */
final class Muscles {
	/** The pull of a muscle as a spring, in hertz, and how damped it is. Soft: a dying muscle, not a servo. */
	private static final double HERTZ = 5.0;
	private static final double DAMPING = 1.0;
	/** Full strength at death, this much left after the first part of the fade, nothing at the end of it. */
	private static final double FADE_KNEE = 0.3;
	private static final double FADE_KNEE_AT = 0.4;
	private static final double GRAVITY = 32.0;
	private static final double TICKS_PER_SECOND = 20.0;
	/** A torso leaning further than this (radians from upright) is falling, and the reflexes start. */
	private static final double FALLING_LEAN = 0.45;
	private static final int TWITCH_TICKS = 30;

	/**
	 * One joint with a muscle.
	 *
	 * @param hinge a knee or an elbow, which only tries to stay straight
	 * @param nested whether the part is inside the piece it hangs from in the model, rather than beside the torso
	 * @param rest the part's angles in the model's rest pose
	 * @param died the part's angles on the frame the mob died
	 * @param weightTorque the torque the limb's own weight puts on the joint when it is held out level
	 */
	record Fibre(Joint joint, JointRole role, Side side, int index, boolean hinge, boolean nested, float[] rest, float[] died, double weightTorque) {
	}

	private final List<Fibre> fibres;
	private final int torsoIndex;
	private final float[] torsoRest;
	private final float[] torsoDied;
	private final Random random;
	private final int[] nextTwitch;
	private final int[] twitchUntil;
	private int landedAt = -1;
	private boolean done;

	private final Quaterniond torsoTarget = new Quaterniond();
	private final Quaterniond target = new Quaterniond();
	private final Quaterniond scratch = new Quaterniond();
	private final Vector3d axis = new Vector3d();

	Muscles(List<Fibre> fibres, int torsoIndex, float[] torsoRest, float[] torsoDied, long seed) {
		this.fibres = fibres;
		this.torsoIndex = torsoIndex;
		this.torsoRest = torsoRest;
		this.torsoDied = torsoDied;
		this.random = new Random(seed);
		this.nextTwitch = new int[fibres.size()];
		this.twitchUntil = new int[fibres.size()];
	}

	/** The torque a limb's weight puts on its joint when held level: mass × gravity × how far out its middle is. */
	static double weightTorque(double mass, double lever) {
		return mass * GRAVITY * lever;
	}

	/**
	 * Sets every muscle for the coming tick.
	 *
	 * @param intent the old death animation's joint angles for this moment
	 * @param ageTicks ticks since the mob died
	 */
	void update(JointPose intent, PhysicsRagdoll ragdoll, double ageTicks, RagdollConfig config) {
		if (done) {
			return;
		}
		Body torso = ragdoll.bodies[ragdoll.torso];
		double seconds = ageTicks / TICKS_PER_SECOND;
		int tick = (int) ageTicks;

		// Which way the torso is going over. In model space up is -Y and the front is -Z.
		scratch.set(torso.qx, torso.qy, torso.qz, torso.qw);
		double lean = Math.acos(Math.clamp(scratch.transform(axis.set(0.0, -1.0, 0.0)).y, -1.0, 1.0));
		double facing = scratch.transform(axis.set(0.0, 0.0, -1.0)).y;
		double reflex = config.reflexes ? Math.clamp((lean - FALLING_LEAN) / 0.5, 0.0, 1.0) : 0.0;
		boolean forward = facing < -0.3, backward = facing > 0.3;

		if (landedAt < 0 && tick > 6 && torso.vx * torso.vx + torso.vy * torso.vy + torso.vz * torso.vz < 1.5) {
			landedAt = tick;
		}
		boolean twitching = config.legKicks && landedAt >= 0 && tick - landedAt < TWITCH_TICKS;

		angles(intent, JointRole.BODY, Side.CENTER, torsoIndex, torsoRest, torsoDied, 0.0, false, false, torsoTarget);
		torsoTarget.conjugate();
		boolean anyAlive = false;
		for (int i = 0; i < fibres.size(); i++) {
			Fibre f = fibres.get(i);
			double strength = strength(seconds, f, config);
			if (twitching && !f.hinge && twitch(f, i, tick, ragdoll)) {
				anyAlive = true;
				continue;
			}
			if (strength <= 0.01) {
				f.joint.relax();
				continue;
			}
			anyAlive = true;
			if (f.hinge) {
				// Knees and elbows only try to stay straight, and give out early: that is the buckle.
				f.joint.muscle(0.0, 0.0, 0.0, 1.0, HERTZ, DAMPING, strength * f.weightTorque * 1.2);
				continue;
			}
			angles(intent, f.role, f.side, f.index, f.rest, f.died, reflex, forward, backward, target);
			// A part hung from the torso beside it wants its pose relative to the torso; one inside the piece it
			// hangs from (a wing in a bat's body) already has its pose relative to that piece.
			if (!f.nested) {
				torsoTarget.mul(target, target);
			}
			f.joint.muscle(target.x, target.y, target.z, target.w, HERTZ, DAMPING, strength * f.weightTorque * reach(f.role));
		}
		// Nothing left to pull and the twitches are over (or the body never came down): it is limp for good.
		boolean twitchesOver = !config.legKicks || landedAt >= 0 && !twitching || seconds > config.muscleSeconds + 6.0;
		if (!anyAlive && seconds > config.muscleSeconds && twitchesOver) {
			done = true;
			for (Fibre f : fibres) {
				f.joint.relax();
			}
		}
	}

	/**
	 * How much of its strength a muscle has left. Necks go first (the head lolls), legs give out early (the body
	 * drops), arms last longest.
	 */
	private static double strength(double seconds, Fibre f, RagdollConfig config) {
		double whole = config.muscleSeconds * (f.hinge ? 0.4 : switch (f.role) {
			case HEAD -> 0.6;
			case LEG -> 0.75;
			default -> 1.0;
		});
		if (whole <= 0.0) {
			return 0.0;
		}
		double t = seconds / whole;
		if (t >= 1.0) {
			return 0.0;
		}
		return t < FADE_KNEE_AT ? 1.0 - (1.0 - FADE_KNEE) * t / FADE_KNEE_AT : FADE_KNEE * (1.0 - (t - FADE_KNEE_AT) / (1.0 - FADE_KNEE_AT));
	}

	/** How much stronger than its own weight a muscle is: enough to throw the limb about, never to lift the body. */
	private static double reach(JointRole role) {
		return switch (role) {
			case HEAD -> 2.0;
			case ARM -> 2.2;
			default -> 1.3;
		};
	}

	/** The part's wanted orientation in model space, from the animation's angles and the reflexes. */
	private void angles(JointPose intent, JointRole role, Side side, int index, float[] rest, float[] died, double reflex, boolean forward, boolean backward,
			Quaterniond out) {
		double relax = intent.relax(role);
		double x = intent.rotation(role, side, JointPose.X, index, died[0] + (rest[0] - died[0]) * relax);
		double y = intent.rotation(role, side, JointPose.Y, index, died[1] + (rest[1] - died[1]) * relax);
		double z = intent.rotation(role, side, JointPose.Z, index, died[2] + (rest[2] - died[2]) * relax);
		if (reflex > 0.0) {
			if (forward && role == JointRole.ARM) {
				// Falling on its face: the arms are thrown out ahead, towards the ground.
				x += (-2.0 - x) * reflex;
			} else if (backward && role == JointRole.HEAD) {
				// Falling on its back: the chin tucks in.
				x += (0.55 - x) * reflex;
			} else if (backward && role == JointRole.ARM) {
				// And the arms go back and out to meet the ground.
				x += (0.6 - x) * reflex * 0.7;
				z += ((side == Side.LEFT ? -0.6 : 0.6) - z) * reflex * 0.7;
			}
		}
		out.rotationZYX(z, y, x);
	}

	/**
	 * After landing, a limb now and then jerks a little from wherever it lies, weaker each time. Returns true while
	 * this fibre is in the middle of one.
	 */
	private boolean twitch(Fibre f, int i, int tick, PhysicsRagdoll ragdoll) {
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
