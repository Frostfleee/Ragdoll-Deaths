package com.frostfleee.ragdolldeaths.core;

import static com.frostfleee.ragdolldeaths.core.Motion.bell;
import static com.frostfleee.ragdolldeaths.core.Motion.decay;
import static com.frostfleee.ragdolldeaths.core.Motion.easeInOut;
import static com.frostfleee.ragdolldeaths.core.Motion.progress;
import static com.frostfleee.ragdolldeaths.core.Motion.smooth;

import com.frostfleee.ragdolldeaths.core.JointPose.Side;

/** Many-legged bodies such as spiders: legs flare, the body flips onto its back and the legs curl partway in, twitching faintly. */
final class ArthropodDeath {
	/** Legs curl most of the way toward each other but stay visibly apart. */
	private static final double CURL_AMOUNT = 0.7;

	private static final JointRole[] LEGS = {JointRole.LEG, JointRole.FRONT_LEG, JointRole.MID_LEG, JointRole.HIND_LEG};

	private ArthropodDeath() {
	}

	static void animate(DeathProfile p, RagdollConfig config, DeathCause cause, double t, RagdollPose pose, JointPose j) {
		double pace = RagdollAnimator.pace(p);
		double flare = 3.0;
		double flipped = flare + 9.0 * pace;

		if (cause == DeathCause.LAVA) {
			RagdollAnimator.lava(p, config, t, pose, 0.0);
		} else {
			double side = RagdollAnimator.fallSide(p);
			pose.pivotY = p.height() / 2.0;
			pose.rollAngle = -side * (Math.PI * easeInOut(progress(t, flare, flipped)) - 0.5 * RagdollAnimator.settle(p, config, t - flipped));
			RagdollAnimator.applyPush(p, config, t, flipped, 0.7, pose);
			pose.offsetY += RagdollAnimator.groundLift(p, pose);
			if (cause == DeathCause.WATER) {
				pose.offsetY += RagdollAnimator.waterDrift(p, config.waterBehavior, t);
			}
		}

		double curl = CURL_AMOUNT * smooth(t, flare + 1.0, flipped + 3.0);
		double twitch = 0.12 * smooth(t, flipped, flipped + 1.0) * decay(t, flipped, 0.3);
		for (JointRole leg : LEGS) {
			j.set(leg, Side.RIGHT, Double.NaN, 0.0, -2.05, curl);
			j.set(leg, Side.LEFT, Double.NaN, 0.0, -2.05, curl);
			j.offset(leg, Side.RIGHT, 0.0, 0.0, 0.4 * bell(t, 0.0, flare + 2.0));
			j.offset(leg, Side.LEFT, 0.0, 0.0, 0.4 * bell(t, 0.0, flare + 2.0));
			j.twitch(leg, 0.0, 0.0, twitch, 1.7);
		}
		j.set(JointRole.HEAD, Side.CENTER, 0.35 * smooth(t, 0.0, 6.0), Double.NaN, Double.NaN, 1.0);
	}
}
