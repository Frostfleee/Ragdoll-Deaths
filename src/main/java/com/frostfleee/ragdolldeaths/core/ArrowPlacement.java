package com.frostfleee.ragdolldeaths.core;

import java.util.Random;

/**
 * Where the arrows stuck in a mob sit. Each arrow lands on one face of one of the mob's boxes, big boxes being hit more
 * often. The places come from the mob's id alone, in order, so an arrow stays put while the mob moves and a new arrow
 * leaves the earlier ones where they were.
 */
public final class ArrowPlacement {
	/** The most arrows drawn on one mob. */
	public static final int MAX_ARROWS = 32;

	/** How far inside the face an arrow's middle sits, as a share of the box. */
	private static final double DEPTH = 0.06;
	/** Arrows stay this far from the edges of a face, as a share of it. */
	private static final double MARGIN = 0.12;

	/**
	 * One arrow.
	 *
	 * @param box which box it is in
	 * @param x where in the box, 0 to 1 from its low side to its high side; likewise {@code y} and {@code z}
	 * @param yRot the arrow's turn, in degrees, the way the game's stuck arrows take it
	 * @param xRot its tilt, in degrees
	 */
	public record Place(int box, double x, double y, double z, float yRot, float xRot) {
	}

	private ArrowPlacement() {
	}

	/**
	 * @param seed the mob's id
	 * @param sizes each box's width, height and depth
	 * @param count how many arrows
	 * @return one place per arrow, or none when no box has a surface
	 */
	public static Place[] pick(long seed, double[][] sizes, int count) {
		double total = 0.0;
		double[] areas = new double[sizes.length];
		for (int i = 0; i < sizes.length; i++) {
			double[] s = sizes[i];
			areas[i] = s[1] * s[2] + s[0] * s[2] + s[0] * s[1];
			total += areas[i];
		}
		if (total <= 0.0) {
			return new Place[0];
		}
		Random random = new Random(seed * 0x9E3779B97F4A7C15L + 0x51L);
		Place[] places = new Place[Math.min(count, MAX_ARROWS)];
		for (int n = 0; n < places.length; n++) {
			// Always the same five numbers per arrow, so that the arrows before this one do not depend on it.
			double whichBox = random.nextDouble() * total;
			double whichAxis = random.nextDouble();
			boolean high = random.nextBoolean();
			double u = MARGIN + (1.0 - 2.0 * MARGIN) * random.nextDouble();
			double v = MARGIN + (1.0 - 2.0 * MARGIN) * random.nextDouble();

			int box = 0;
			while (box < areas.length - 1 && whichBox >= areas[box]) {
				whichBox -= areas[box++];
			}
			double[] s = sizes[box];
			double faceX = s[1] * s[2], faceY = s[0] * s[2];
			double pick = whichAxis * areas[box];
			int axis = pick < faceX ? 0 : pick < faceX + faceY ? 1 : 2;

			double side = high ? 1.0 - DEPTH : DEPTH;
			double x = axis == 0 ? side : u;
			double y = axis == 1 ? side : axis == 0 ? u : v;
			double z = axis == 2 ? side : v;
			// As the game aims a stuck arrow: along the line through the middle of the box.
			double dx = 1.0 - 2.0 * x, dy = 1.0 - 2.0 * y, dz = 1.0 - 2.0 * z;
			float yRot = (float) Math.toDegrees(Math.atan2(dx, dz));
			float xRot = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			places[n] = new Place(box, x, y, z, yRot, xRot);
		}
		return places;
	}
}
