package com.frostfleee.ragdolldeaths.core;

import static com.frostfleee.ragdolldeaths.core.Motion.HALF_PI;
import static com.frostfleee.ragdolldeaths.core.Motion.bell;
import static com.frostfleee.ragdolldeaths.core.Motion.decay;
import static com.frostfleee.ragdolldeaths.core.Motion.easeIn;
import static com.frostfleee.ragdolldeaths.core.Motion.easeInOut;
import static com.frostfleee.ragdolldeaths.core.Motion.fall;
import static com.frostfleee.ragdolldeaths.core.Motion.lerp;
import static com.frostfleee.ragdolldeaths.core.Motion.progress;
import static com.frostfleee.ragdolldeaths.core.Motion.ring;
import static com.frostfleee.ragdolldeaths.core.Motion.smooth;

import com.frostfleee.ragdolldeaths.core.JointPose.Side;

/**
 * Four-legged bodies: livestock, horses, llamas, wolves, cats and similar. Their legs are single rigid segments
 * without knees, so they stay nearly straight; the body does the falling and the legs only buckle a little and
 * stiffen. Vanilla's sitting, rearing and grazing poses are cleared on the render state before this runs.
 */
final class QuadrupedDeath {
	private QuadrupedDeath() {
	}

	static void animate(DeathProfile p, RagdollConfig config, DeathCause cause, double t, RagdollPose pose, JointPose j) {
		switch (cause) {
			case LAVA -> lava(p, config, t, pose, j);
			case WATER -> water(p, config, t, pose, j);
			case FALL -> bellyFlop(p, config, t, pose, j);
			case GROUND -> topple(p, config, t, pose, j);
		}
		limpExtras(t, j);
	}

	/**
	 * The legs give a little and the body rolls over onto its side; once down the legs stretch out stiffly with a
	 * couple of fading kicks, the legs underneath tucked off the ground rather than splayed into it. The body itself never pitches forward or back, so no end of it can sink into the
	 * ground. A hit from the front makes it brace its front legs and toss its head as it staggers back a step; a hit
	 * from behind kicks its hind legs out and dips its head as it stumbles forward. Both then roll to a side that
	 * varies per mob.
	 */
	private static void topple(DeathProfile p, RagdollConfig config, double t, RagdollPose pose, JointPose j) {
		double pace = RagdollAnimator.pace(p);
		boolean front = p.hit() == HitDirection.FRONT;
		boolean back = p.hit() == HitDirection.BACK;
		boolean lengthwise = front || back;
		double start = lengthwise ? 5.0 : 2.0;
		double impact = start + 10.0 * pace;
		double side = lengthwise ? RagdollAnimator.asymmetry(p) : RagdollAnimator.fallSide(p);

		double rolling = fall(t, start, impact);
		double sway = 0.1 * bell(t, 0.0, start + 1.5);
		pose.rollAngle = -side * (HALF_PI * rolling - RagdollAnimator.settle(p, config, t - impact)) + side * sway;
		if (lengthwise) {
			RagdollAnimator.applyPush(p, config, t, start + 1.0, 0.3, pose);
		} else {
			RagdollAnimator.applyPush(p, config, t, impact, 0.6, pose);
		}
		double buckle = bell(t, start - 1.0, impact);
		pose.offsetY += RagdollAnimator.groundLift(p, pose) - 0.12 * p.legLength() * buckle;

		double react = bell(t, 0.0, start + 1.0);
		double frontReact = front ? -0.4 * react : back ? 0.2 * react : 0.0;
		double hindReact = back ? 0.45 * react : front ? -0.1 * react : 0.0;
		double headReact = front ? -0.5 * bell(t, 0.0, start + 2.0) : back ? 0.35 * bell(t, 0.0, start + 2.0) : -0.3 * bell(t, 0.0, start + 2.0);

		double down = smooth(t, impact - 1.0, impact + 4.0);
		double frontLeg = 0.3 * buckle - 0.3 * down + frontReact;
		double hindLeg = -0.25 * buckle + 0.35 * down + hindReact;
		Side groundSide = side > 0.0 ? Side.LEFT : Side.RIGHT;
		for (Side legSide : new Side[] {Side.RIGHT, Side.LEFT}) {
			double spread = (legSide == groundSide ? -0.1 : 0.18) * rolling;
			j.offset(JointRole.FRONT_LEG, legSide, frontLeg, 0.0, spread);
			j.offset(JointRole.HIND_LEG, legSide, hindLeg, 0.0, spread);
			j.offset(JointRole.MID_LEG, legSide, lerp(frontLeg, hindLeg, 0.5), 0.0, spread);
		}
		double kicks = 0.15 * smooth(t, impact, impact + 1.0) * decay(t, impact, 0.25);
		j.twitch(JointRole.FRONT_LEG, kicks, 0.0, 0.0, 1.4);
		j.twitch(JointRole.HIND_LEG, kicks, 0.0, 0.0, 1.4);

		double headX = headReact + 0.15 * down + ring(t, impact + 1.0, 0.06 * config.bounceStrength, 1.0, 0.35);
		j.offset(JointRole.HEAD, Side.CENTER, headX, 0.0, 0.0);
		j.offset(JointRole.NECK, Side.CENTER, headX * 0.5, 0.0, 0.0);
	}


	/** Fall damage: belly flop with the legs splayed out sideways and the head slamming down. */
	private static void bellyFlop(DeathProfile p, RagdollConfig config, double t, RagdollPose pose, JointPose j) {
		double impact = 3.0;
		double flatten = smooth(t, 0.0, impact);
		pose.offsetY = -0.85 * p.legLength() * easeIn(progress(t, 0.0, impact)) + RagdollAnimator.impactBounceHeight(p, config, t, impact) * 0.5;

		for (Side legSide : new Side[] {Side.RIGHT, Side.LEFT}) {
			j.offset(JointRole.FRONT_LEG, legSide, -0.3 * flatten, 0.0, 1.3 * flatten);
			j.offset(JointRole.HIND_LEG, legSide, 0.3 * flatten, 0.0, 1.3 * flatten);
			j.offset(JointRole.MID_LEG, legSide, 0.0, 0.0, 1.3 * flatten);
		}
		j.offset(JointRole.HEAD, Side.CENTER, 0.5 * flatten + ring(t, impact, 0.08, 1.1, 0.35), 0.0, 0.0);
	}

	/** Water: rolls slowly onto its side while the legs paddle weaker and weaker. */
	private static void water(DeathProfile p, RagdollConfig config, double t, RagdollPose pose, JointPose j) {
		double side = RagdollAnimator.fallSide(p);
		pose.rollAngle = -side * HALF_PI * easeInOut(progress(t, 0.0, 16.0));
		RagdollAnimator.applyPush(p, config, t, 16.0, 0.4, pose);
		pose.offsetY += RagdollAnimator.groundLift(p, pose) + RagdollAnimator.waterDrift(p, config.waterBehavior, t);

		double paddle = 0.45 * decay(t, 0.0, 0.1);
		j.twitch(JointRole.FRONT_LEG, paddle, 0.0, 0.0, 0.5);
		j.twitch(JointRole.HIND_LEG, paddle, 0.0, 0.0, 0.5);
		j.offset(JointRole.HEAD, Side.CENTER, -0.3 * smooth(t, 0.0, 6.0), 0.0, 0.0);
	}

	/** Lava: legs thrash and the head strains upward as it sinks. */
	private static void lava(DeathProfile p, RagdollConfig config, double t, RagdollPose pose, JointPose j) {
		RagdollAnimator.lava(p, config, t, pose, 0.6);
		double energy = 1.0 - smooth(t, 8.0, 15.0);
		j.twitch(JointRole.FRONT_LEG, 0.5 * energy, 0.0, 0.0, 1.6);
		j.twitch(JointRole.HIND_LEG, 0.5 * energy, 0.0, 0.0, 1.6);
		j.offset(JointRole.HEAD, Side.CENTER, -0.5 * smooth(t, 0.0, 3.0), 0.0, 0.0);
	}

	private static void limpExtras(double t, JointPose j) {
		j.offset(JointRole.TAIL, Side.CENTER, 0.5 * smooth(t, 0.0, 6.0), ring(t, 2.0, 0.35, 0.7, 0.2), 0.0);
		j.offset(JointRole.EAR, Side.RIGHT, 0.25 * smooth(t, 0.0, 6.0), 0.0, 0.35 * smooth(t, 0.0, 6.0));
		j.offset(JointRole.EAR, Side.LEFT, 0.25 * smooth(t, 0.0, 6.0), 0.0, 0.35 * smooth(t, 0.0, 6.0));
		j.offset(JointRole.JAW, Side.CENTER, 0.22 * smooth(t, 4.0, 10.0), 0.0, 0.0);
	}
}
