package com.frostfleee.ragdolldeaths.core;

/**
 * The shape of the ground around where a body comes to rest: how far the floor rises (or drops, when negative)
 * at {@code radius} blocks away, in {@link #DIRECTIONS} evenly spaced directions. Direction {@code k} points at
 * {@code k * 45} degrees from +X toward +Z. Measured once, when the body settles.
 */
public final class Ground {
	public static final int DIRECTIONS = 8;
	public static final Ground FLAT = new Ground(1.0, new double[DIRECTIONS]);

	private static final double STEP = 2.0 * Math.PI / DIRECTIONS;

	private final double radius;
	private final double[] rise;
	private final boolean flat;

	public Ground(double radius, double[] rise) {
		if (rise.length != DIRECTIONS) {
			throw new IllegalArgumentException("expected " + DIRECTIONS + " samples, got " + rise.length);
		}
		this.radius = Math.max(0.1, radius);
		this.rise = rise.clone();
		boolean allFlat = true;
		for (double r : this.rise) {
			allFlat &= Math.abs(r) < 1.0E-3;
		}
		this.flat = allFlat;
	}

	public static double directionX(int k) {
		return Math.cos(k * STEP);
	}

	public static double directionZ(int k) {
		return Math.sin(k * STEP);
	}

	public double radius() {
		return radius;
	}

	public boolean flat() {
		return flat;
	}

	/** The rise toward a horizontal direction, blended between the two nearest samples. */
	public double riseToward(double dirX, double dirZ) {
		double angle = Math.atan2(dirZ, dirX);
		if (angle < 0.0) {
			angle += 2.0 * Math.PI;
		}
		double position = angle / STEP;
		int below = (int) Math.floor(position) % DIRECTIONS;
		int above = (below + 1) % DIRECTIONS;
		double blend = position - Math.floor(position);
		return rise[below] * (1.0 - blend) + rise[above] * blend;
	}
}
