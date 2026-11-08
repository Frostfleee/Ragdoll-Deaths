package com.frostfleee.ragdolldeaths.core;

import static com.frostfleee.ragdolldeaths.core.Motion.HALF_PI;
import static com.frostfleee.ragdolldeaths.core.Motion.backOut;
import static com.frostfleee.ragdolldeaths.core.Motion.bell;
import static com.frostfleee.ragdolldeaths.core.Motion.easeInOut;
import static com.frostfleee.ragdolldeaths.core.Motion.easeOut;
import static com.frostfleee.ragdolldeaths.core.Motion.fall;
import static com.frostfleee.ragdolldeaths.core.Motion.lerp;
import static com.frostfleee.ragdolldeaths.core.Motion.progress;
import static com.frostfleee.ragdolldeaths.core.Motion.ring;
import static com.frostfleee.ragdolldeaths.core.Motion.smooth;

import com.frostfleee.ragdolldeaths.core.JointPose.Side;

/** Two-legged bodies: zombies, skeletons, villagers, illagers, golems, players and similar. */
final class BipedDeath {
	/** The limbs react to the landing slightly after the back reaches the ground, so the impact reads as contact. */
	private static final double RECOIL_LANDING_DELAY = 2.0;

	private BipedDeath() {
	}

	static void animate(DeathProfile p, RagdollConfig config, DeathCause cause, double t, RagdollPose pose, JointPose j) {
		switch (cause) {
			case LAVA -> lava(p, config, t, pose, j);
			case WATER -> water(p, config, t, pose, j);
			case FALL -> splat(p, config, t, pose, j);
			case GROUND -> {
				switch (p.hit()) {
					case FRONT -> recoil(p, config, t, pose, j);
					case BACK -> pitchForward(p, config, t, pose, j);
					case LEFT, RIGHT -> spin(p, config, t, pose, j);
					case NONE -> crumple(p, config, t, pose, j);
				}
			}
		}
	}

	/** Struck from the front: head snaps back, arms reach up as the body falls backward, then splay on the ground. */
	private static void recoil(DeathProfile p, RagdollConfig config, double t, RagdollPose pose, JointPose j) {
		double impact = 11.0 * RagdollAnimator.pace(p);
		pose.tipToward(p.fallDirX(), p.fallDirZ(), HALF_PI * fall(t, 1.0, impact) - RagdollAnimator.settle(p, config, t - impact));
		RagdollAnimator.applyPush(p, config, t, impact, 1.0, pose);
		pose.offsetY += RagdollAnimator.groundLift(p, pose);
		double landing = impact + RECOIL_LANDING_DELAY;

		double turn = RagdollAnimator.asymmetry(p);
		j.set(JointRole.HEAD, Side.CENTER, lerp(-0.7, -0.25, smooth(t, 3.0, impact)), turn * 0.6 * smooth(t, landing, landing + 5.0), Double.NaN, smooth(t, 0.0, 2.0));
		j.offset(JointRole.HEAD, Side.CENTER, ring(t, landing, 0.12, 0.9, 0.35), 0.0, 0.0);

		Side lead = leadSide(p);
		for (Side side : new Side[] {Side.RIGHT, Side.LEFT}) {
			boolean leading = side == lead;
			double reach = leading ? -2.1 : -1.7;
			double splay = leading ? 1.05 : 0.7;
			double x = t < landing
					? reach * easeOut(progress(t, 0.0, impact * 0.7))
					: lerp(reach, 0.15, backOut(progress(t, landing, landing + 4.0), 0.6));
			double z = lerp(0.25 * smooth(t, 0.0, impact), splay, smooth(t, landing, landing + 4.0));
			j.set(JointRole.ARM, side, x, 0.0, z, smooth(t, 0.0, 2.0));

			double kick = leading ? -0.85 * bell(t, 1.0, impact + 1.0) : -0.25 * smooth(t, 0.0, impact);
			j.set(JointRole.LEG, side, kick * (1.0 - smooth(t, landing, landing + 4.0)), 0.0, (leading ? 0.22 : 0.12) * smooth(t, landing, landing + 4.0), smooth(t, 0.0, 2.0));
		}
	}

	/** Struck from behind: arches, throws the arms forward to break the fall and lands face down, head turned aside. */
	private static void pitchForward(DeathProfile p, RagdollConfig config, double t, RagdollPose pose, JointPose j) {
		double impact = 12.0 * RagdollAnimator.pace(p);
		double arch = 0.18 * bell(t, 0.0, 4.0);
		pose.tipToward(p.fallDirX(), p.fallDirZ(), HALF_PI * fall(t, 2.0, impact) - arch - RagdollAnimator.settle(p, config, t - impact));
		RagdollAnimator.applyPush(p, config, t, impact, 1.0, pose);
		pose.offsetY += RagdollAnimator.groundLift(p, pose);

		double turn = RagdollAnimator.asymmetry(p);
		double headX = -0.6 * bell(t, 0.0, 5.0) - 0.35 * smooth(t, impact, impact + 4.0);
		j.set(JointRole.HEAD, Side.CENTER, headX, turn * 1.0 * smooth(t, impact, impact + 5.0), Double.NaN, smooth(t, 0.0, 1.5));

		Side lead = leadSide(p);
		for (Side side : new Side[] {Side.RIGHT, Side.LEFT}) {
			boolean leading = side == lead;
			double x = t < 3.0
					? 0.9 * smooth(t, 0.0, 2.5)
					: lerp(0.9, leading ? -2.7 : -2.4, easeOut(progress(t, 3.0, impact)));
			double z = lerp(0.12, leading ? 0.55 : 0.35, smooth(t, impact, impact + 5.0)) + ring(t, impact, 0.08, 1.0, 0.4);
			j.set(JointRole.ARM, side, x, 0.0, z, smooth(t, 0.0, 1.5));

			double trail = leading ? 0.45 * bell(t, 0.0, impact) : 0.15 * smooth(t, 0.0, impact);
			j.set(JointRole.LEG, side, trail, 0.0, 0.12 * smooth(t, impact, impact + 4.0), smooth(t, 0.0, 2.0));
		}
	}

	/** Struck from the side: the body twists away, one arm flung across, the other swinging wide, legs scissoring. */
	private static void spin(DeathProfile p, RagdollConfig config, double t, RagdollPose pose, JointPose j) {
		double impact = 11.0 * RagdollAnimator.pace(p);
		double side = RagdollAnimator.fallSide(p);
		pose.spinAngle = side * 1.05 * easeOut(progress(t, 0.0, impact * 0.9));
		pose.tipToward(p.fallDirX(), p.fallDirZ(), HALF_PI * fall(t, 1.0, impact) - RagdollAnimator.settle(p, config, t - impact));
		RagdollAnimator.applyPush(p, config, t, impact, 1.0, pose);
		pose.offsetY += RagdollAnimator.groundLift(p, pose);

		j.set(JointRole.HEAD, Side.CENTER, 0.35 * smooth(t, 4.0, impact), -side * 0.8 * bell(t, 0.0, 6.0) + side * 0.5 * smooth(t, impact, impact + 5.0), Double.NaN, smooth(t, 0.0, 1.5));

		Side near = p.hit() == HitDirection.LEFT ? Side.LEFT : Side.RIGHT;
		Side far = near == Side.LEFT ? Side.RIGHT : Side.LEFT;
		double settled = smooth(t, impact, impact + 4.0);
		j.set(JointRole.ARM, near, lerp(-1.3 * smooth(t, 0.0, 4.0), -0.5, settled), 0.0, lerp(-0.7 * smooth(t, 0.0, 4.0), 0.1, settled), smooth(t, 0.0, 1.5));
		j.set(JointRole.ARM, far, -0.4 * smooth(t, 0.0, 3.0), 0.0, lerp(1.7 * easeOut(progress(t, 0.0, impact * 0.8)), 1.25, settled) + ring(t, impact, 0.1, 1.0, 0.4), smooth(t, 0.0, 1.5));

		j.set(JointRole.LEG, near, lerp(-0.6 * smooth(t, 0.0, impact), 0.25, settled), 0.0, 0.15 * settled, smooth(t, 0.0, 2.0));
		j.set(JointRole.LEG, far, lerp(0.4 * smooth(t, 0.0, impact), -0.15, settled), 0.0, 0.1 * settled, smooth(t, 0.0, 2.0));
	}

	/** No direction to react to: the knees give out, the body slumps, then folds forward onto its face. */
	private static void crumple(DeathProfile p, RagdollConfig config, double t, RagdollPose pose, JointPose j) {
		double pace = RagdollAnimator.pace(p);
		double kneel = 9.0 * pace;
		double impact = kneel + 9.0 * pace;
		double fold = 1.45 * easeInOut(progress(t, 0.0, kneel)) * (1.0 - 0.85 * smooth(t, kneel + 2.0, impact + 2.0));
		double lowered = p.legLength() * (1.0 - Math.cos(fold));

		double angle = 0.25 * smooth(t, 2.0, kneel) + (HALF_PI - 0.25) * fall(t, kneel, impact) - RagdollAnimator.settle(p, config, t - impact);
		pose.tipToward(p.forwardX(), p.forwardZ(), angle);
		pose.offsetY += RagdollAnimator.groundLift(p, pose) - lowered;

		double turn = RagdollAnimator.asymmetry(p);
		j.set(JointRole.HEAD, Side.CENTER, lerp(0.75 * smooth(t, 0.0, kneel), 0.3, smooth(t, impact, impact + 4.0)), turn * 1.0 * smooth(t, impact, impact + 5.0), Double.NaN, smooth(t, 0.0, 3.0));

		for (Side side : new Side[] {Side.RIGHT, Side.LEFT}) {
			double sway = 0.08 * Math.sin(0.3 * t + (side == Side.LEFT ? 1.3 : 0.0)) * (1.0 - smooth(t, kneel, impact));
			double trail = lerp(0.12 * smooth(t, 0.0, kneel), 0.7, smooth(t, kneel, impact));
			double x = lerp(trail, 0.25, smooth(t, impact, impact + 4.0)) + sway;
			j.set(JointRole.ARM, side, x, 0.0, (side == Side.RIGHT ? 0.18 : 0.1) * smooth(t, impact, impact + 4.0), smooth(t, 0.0, 3.0));
			double legFold = fold + (side == Side.RIGHT ? 0.08 : -0.08) * smooth(t, 0.0, kneel);
			j.set(JointRole.LEG, side, legFold, 0.0, 0.08, smooth(t, 0.0, 2.0));
		}
	}

	/** Fall damage: the knees buckle on impact and the body slams flat, limbs thrown out spread-eagled. */
	private static void splat(DeathProfile p, RagdollConfig config, double t, RagdollPose pose, JointPose j) {
		double impact = 4.0;
		double buckle = 1.0 * bell(t, 0.0, impact);
		double lowered = 0.7 * p.legLength() * (1.0 - Math.cos(buckle));
		boolean back = RagdollAnimator.ontoBack(p);
		double sign = back ? -1.0 : 1.0;
		pose.tipToward(sign * p.forwardX(), sign * p.forwardZ(), HALF_PI * fall(t, 0.5, impact) - RagdollAnimator.impactBounceAngle(config, t, impact));
		pose.offsetY = RagdollAnimator.groundLift(p, pose) - lowered + RagdollAnimator.impactBounceHeight(p, config, t, impact);

		double turn = RagdollAnimator.asymmetry(p);
		j.set(JointRole.HEAD, Side.CENTER, (back ? -0.2 : 0.0) * smooth(t, impact, impact + 3.0), turn * 0.5 * smooth(t, impact, impact + 6.0), Double.NaN, smooth(t, 0.0, 1.0));
		j.offset(JointRole.HEAD, Side.CENTER, ring(t, impact, 0.35, 1.1, 0.3), 0.0, 0.0);

		for (Side side : new Side[] {Side.RIGHT, Side.LEFT}) {
			boolean right = side == Side.RIGHT;
			double spread = (right ? 1.45 : 1.2) * backOut(progress(t, impact, impact + 4.0), 0.5);
			j.set(JointRole.ARM, side, lerp(-1.1 * bell(t, 0.0, impact), -0.15, smooth(t, impact, impact + 2.0)), 0.0, spread, smooth(t, 0.0, 1.0));
			j.set(JointRole.LEG, side, -0.6 * bell(t, 0.0, impact), 0.0, (right ? 0.38 : 0.3) * smooth(t, impact, impact + 3.0), smooth(t, 0.0, 1.0));
		}
	}

	/** Drowned or killed while swimming: rolls slowly onto its back with arms drifting up and out. */
	private static void water(DeathProfile p, RagdollConfig config, double t, RagdollPose pose, JointPose j) {
		pose.tipToward(-p.forwardX(), -p.forwardZ(), HALF_PI * easeInOut(progress(t, 0.0, 18.0)));
		RagdollAnimator.applyPush(p, config, t, 18.0, 0.5, pose);
		pose.offsetY += RagdollAnimator.groundLift(p, pose) + RagdollAnimator.waterDrift(p, config.waterBehavior, t);

		j.set(JointRole.HEAD, Side.CENTER, -0.45 * smooth(t, 0.0, 10.0), 0.2 * Math.sin(0.15 * t), Double.NaN, smooth(t, 0.0, 4.0));
		for (Side side : new Side[] {Side.RIGHT, Side.LEFT}) {
			double phase = side == Side.LEFT ? 1.6 : 0.0;
			j.set(JointRole.ARM, side, -0.5 * smooth(t, 0.0, 12.0) + 0.12 * Math.sin(0.2 * t + phase), 0.0, 1.25 * smooth(t, 0.0, 14.0) + 0.12 * Math.sin(0.17 * t + phase), smooth(t, 0.0, 4.0));
			j.set(JointRole.LEG, side, 0.2 * Math.sin(0.13 * t + phase * 1.5), 0.0, 0.3 * smooth(t, 0.0, 14.0), smooth(t, 0.0, 4.0));
		}
	}

	/** Lava: arms flail overhead while the body tips back and sinks. */
	private static void lava(DeathProfile p, RagdollConfig config, double t, RagdollPose pose, JointPose j) {
		RagdollAnimator.lava(p, config, t, pose, 0.45);
		double energy = 1.0 - smooth(t, 8.0, 15.0);

		j.set(JointRole.HEAD, Side.CENTER, -0.6 * smooth(t, 0.0, 3.0), Double.NaN, Double.NaN, smooth(t, 0.0, 2.0));
		for (Side side : new Side[] {Side.RIGHT, Side.LEFT}) {
			double phase = side == Side.LEFT ? 2.1 : 0.0;
			double x = -2.6 * smooth(t, 0.0, 3.0) + 0.5 * Math.sin(1.4 * t + phase) * energy;
			double z = 0.35 + 0.3 * Math.sin(1.1 * t + phase + 1.0) * energy;
			j.set(JointRole.ARM, side, x, 0.0, z, smooth(t, 0.0, 2.0));
			j.set(JointRole.LEG, side, 0.5 * Math.sin(1.2 * t + (side == Side.LEFT ? Math.PI : 0.0)) * energy, 0.0, 0.1, smooth(t, 0.0, 2.0));
		}
	}

	private static Side leadSide(DeathProfile p) {
		return RagdollAnimator.asymmetry(p) > 0.0 ? Side.RIGHT : Side.LEFT;
	}
}
