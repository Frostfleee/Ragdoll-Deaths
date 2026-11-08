package com.frostfleee.ragdolldeaths.core;

import static com.frostfleee.ragdolldeaths.core.Motion.decay;
import static com.frostfleee.ragdolldeaths.core.Motion.lerp;
import static com.frostfleee.ragdolldeaths.core.Motion.ring;
import static com.frostfleee.ragdolldeaths.core.Motion.smooth;

import com.frostfleee.ragdolldeaths.core.JointPose.Side;

/** Joint motion for bodies without a dedicated choreography, driven by whichever parts the model has. */
final class CreatureJoints {
	private CreatureJoints() {
	}

	static void animate(DeathProfile p, DeathCause cause, double t, double landTick, JointPose j) {
		boolean inWater = cause == DeathCause.WATER;
		wings(p, t, j);
		tentacles(p, inWater, t, landTick, j);
		fins(p, inWater, t, j);
		limp(p, inWater, t, j);
	}

	/**
	 * Most winged mobs flap in their own model code every frame, so their wings are first pulled back to rest and
	 * then only quiver faintly before going still. Others (bees) get a short burst of weakening flaps.
	 */
	private static void wings(DeathProfile p, double t, JointPose j) {
		if (p.calmWings()) {
			j.relax(JointRole.WING, smooth(t, 0.0, 2.0));
			j.twitch(JointRole.WING, 0.0, 0.0, 0.08 * decay(t, 0.0, 0.4), 0.7);
			double rest = 0.2 * smooth(t, 2.0, 10.0);
			j.offset(JointRole.WING, Side.RIGHT, 0.0, 0.0, -rest);
			j.offset(JointRole.WING, Side.LEFT, 0.0, 0.0, -rest);
			if (p.rig() == Rig.WINGED) {
				j.offsetAll(JointRole.LEG, -0.6 * smooth(t, 0.0, 6.0), 0.0, 0.0);
			}
			return;
		}
		double flutter = 0.9 * decay(t, 0.0, 0.16) * (1.0 - smooth(t, 10.0, 16.0));
		j.twitch(JointRole.WING, 0.0, 0.0, flutter, 1.9);
		double droop = 0.4 * smooth(t, 4.0, 14.0);
		j.offset(JointRole.WING, Side.RIGHT, 0.0, 0.0, -droop);
		j.offset(JointRole.WING, Side.LEFT, 0.0, 0.0, -droop);
		if (p.rig() == Rig.WINGED) {
			j.offsetAll(JointRole.LEG, -0.6 * smooth(t, 0.0, 6.0), 0.0, 0.0);
		}
	}

	/** Tentacles stream outward while falling, splay flat on landing, or curl in when dying in water. */
	private static void tentacles(DeathProfile p, boolean inWater, double t, double landTick, JointPose j) {
		double lift;
		if (inWater) {
			lift = -0.35 * smooth(t, 0.0, 10.0);
			j.twitch(JointRole.TENTACLE, 0.15, 0.0, 0.0, 0.4);
		} else if (p.kind().airborne()) {
			double landed = smooth(t, landTick, landTick + 4.0);
			lift = lerp(1.0 * smooth(t, 0.0, 8.0), 1.45, landed);
			j.twitch(JointRole.TENTACLE, 0.25 * decay(t, 0.0, 0.08) * (1.0 - landed), 0.0, 0.0, 1.2);
		} else {
			lift = 1.2 * smooth(t, 0.0, 6.0);
			j.twitch(JointRole.TENTACLE, 0.2 * decay(t, 0.0, 0.2), 0.0, 0.0, 1.4);
		}
		j.lift(JointRole.TENTACLE, lift);
	}

	/** Fins and tails flop hard out of water and slow to a stop in it. */
	private static void fins(DeathProfile p, boolean inWater, double t, JointPose j) {
		double amplitude = inWater ? 0.3 * decay(t, 0.0, 0.1) : 0.55 * decay(t, 0.0, 0.12);
		double frequency = inWater ? 0.5 : 1.15;
		j.twitch(JointRole.FIN, 0.0, amplitude, 0.0, frequency);
		if (p.rig() == Rig.FINNED || p.kind() == BodyKind.AQUATIC) {
			j.twitch(JointRole.TAIL, 0.0, amplitude * 0.8, 0.0, frequency);
		}
	}

	private static void limp(DeathProfile p, boolean inWater, double t, JointPose j) {
		double sag = smooth(t, 0.0, 8.0);
		double turn = RagdollAnimator.asymmetry(p);
		j.set(JointRole.HEAD, Side.CENTER, 0.6 * sag, Double.NaN, turn * 0.3 * sag, sag);
		j.offset(JointRole.HEAD, Side.CENTER, ring(t, 6.0, 0.08, 0.9, 0.35), 0.0, 0.0);
		j.offset(JointRole.NECK, Side.CENTER, 0.3 * sag, 0.0, 0.0);

		j.set(JointRole.ARM, Side.RIGHT, 0.25 * sag, Double.NaN, 0.35 * sag, sag);
		j.set(JointRole.ARM, Side.LEFT, 0.1 * sag, Double.NaN, 0.5 * sag, sag);

		if (inWater) {
			double paddle = 0.5 * decay(t, 0.0, 0.1);
			for (JointRole leg : new JointRole[] {JointRole.LEG, JointRole.FRONT_LEG, JointRole.HIND_LEG, JointRole.MID_LEG}) {
				j.twitch(leg, paddle, 0.0, 0.0, 0.5);
			}
		} else {
			j.offsetAll(JointRole.FRONT_LEG, -0.3 * sag, 0.0, 0.0);
			j.offsetAll(JointRole.HIND_LEG, 0.3 * sag, 0.0, 0.0);
		}

		j.offset(JointRole.TAIL, Side.CENTER, 0.45 * sag, 0.0, 0.0);
		j.offset(JointRole.EAR, Side.RIGHT, 0.2 * sag, 0.0, 0.3 * sag);
		j.offset(JointRole.EAR, Side.LEFT, 0.2 * sag, 0.0, 0.3 * sag);
	}
}
