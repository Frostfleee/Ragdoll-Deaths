package com.frostfleee.ragdolldeaths.core;

/**
 * How a knee or an elbow may bend. Both are hinges on the outer edge of the limb: the front of the knee and the back of
 * the elbow. The outer side of the bend therefore never opens, so the inside of the limb cannot be seen.
 *
 * <p>Angles are turns about the limb's own X axis. A positive turn carries a hanging part backward, so a knee bends
 * from 0 to {@link #MAX_RADIANS} and an elbow from 0 to minus that. Neither bends at all the other way.
 */
public final class LimbBend {
	public static final double MAX_DEGREES = 89.0;
	public static final double MAX_RADIANS = Math.toRadians(MAX_DEGREES);
	private static final double EASE_TICKS = 1.0, MAX_TURN_PER_TICK = Math.toRadians(22.0);
	/** For the visual test: draws every knee and elbow at this bend, in degrees, to look at it from all sides. */
	public static double testDegrees = Double.NaN;

	/**
	 * How far a drawn limb follows its physics this frame. The physics can flick a limb back and forth from tick to tick
	 * while the body lands, which on screen is a blur; what is drawn eases after it, and never turns faster than this.
	 *
	 * @param gap how far the drawn limb is from the physics, in radians
	 * @param ticks the time since the last frame
	 * @return the share of the gap to close, 0 to 1
	 */
	public static double follow(double gap, double ticks) {
		if (gap <= 1.0E-9 || ticks <= 0.0) {
			return ticks <= 0.0 ? 0.0 : 1.0;
		}
		double eased = 1.0 - Math.exp(-ticks / EASE_TICKS);
		return Math.min(eased, MAX_TURN_PER_TICK * ticks / gap);
	}

	private LimbBend() {
	}

	public static double clampKnee(double angle) {
		return Math.clamp(angle, 0.0, MAX_RADIANS);
	}

	public static double clampElbow(double angle) {
		return Math.clamp(angle, -MAX_RADIANS, 0.0);
	}

	/**
	 * How far to move the lower half along its own length, away from the joint. Past a right angle the far corner of
	 * its cut end would otherwise come out through the other side of the upper half.
	 *
	 * @param thickness the limb's size front to back
	 * @param angle the bend, either sign
	 * @return the distance, in the same unit as {@code thickness}; zero up to a right angle
	 */
	public static double slide(double thickness, double angle) {
		double cos = Math.cos(angle);
		return cos >= 0.0 ? 0.0 : thickness * -cos / Math.max(Math.abs(Math.sin(angle)), 0.1);
	}
}
