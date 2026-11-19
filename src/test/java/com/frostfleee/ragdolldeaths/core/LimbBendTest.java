package com.frostfleee.ragdolldeaths.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A leg seen from the side, with the knee at the origin on the limb's front edge: +z is back, +y is down the limb. */
class LimbBendTest {
	private static final double THICKNESS = 4.0, HALF_LENGTH = 6.0;

	/** A point of the lower half, in its own space, seen from the upper half. */
	private static double[] onUpper(double z, double y, double angle) {
		double slide = LimbBend.slide(THICKNESS, angle);
		double along = y + slide;
		return new double[] {z * Math.cos(angle) + along * Math.sin(angle), -z * Math.sin(angle) + along * Math.cos(angle)};
	}

	@Test
	void kneesAndElbowsBendOnlyOneWayAndNoFurtherThanTheLimit() {
		assertEquals(0.0, LimbBend.clampKnee(-0.5));
		assertEquals(0.0, LimbBend.clampElbow(0.5));
		assertEquals(Math.toRadians(89.0), LimbBend.clampKnee(3.0), 1.0e-9);
		assertEquals(-Math.toRadians(89.0), LimbBend.clampElbow(-3.0), 1.0e-9);
		assertEquals(1.0, LimbBend.clampKnee(1.0));
		assertEquals(-1.0, LimbBend.clampElbow(-1.0));
	}

	@Test
	void aDrawnLimbEasesAfterItsPhysicsAndNeverWhips() {
		assertEquals(0.0, LimbBend.follow(1.0, 0.0), "no time, no movement");
		double small = LimbBend.follow(Math.toRadians(5.0), 1.0);
		assertTrue(small > 0.5 && small < 0.7, "a small gap closes smoothly: " + small);
		double turned = Math.toDegrees(Math.PI * LimbBend.follow(Math.PI, 1.0));
		assertTrue(turned <= 22.0 + 1.0e-9, "a big gap is crossed no faster than the limit: " + turned);
	}

	@Test
	void theLowerHalfOnlySlidesPastARightAngle() {
		assertEquals(0.0, LimbBend.slide(THICKNESS, 0.0));
		assertEquals(0.0, LimbBend.slide(THICKNESS, LimbBend.MAX_RADIANS));
		assertTrue(LimbBend.slide(THICKNESS, Math.toRadians(110.0)) > 1.0);
		assertEquals(LimbBend.slide(THICKNESS, Math.toRadians(110.0)), LimbBend.slide(THICKNESS, -Math.toRadians(110.0)), 1.0e-9);
	}

	@Test
	void theCutEndNeverComesOutThroughTheFrontOfTheUpperHalf() {
		for (double degrees = 0.0; degrees <= LimbBend.MAX_DEGREES; degrees += 1.0) {
			double angle = Math.toRadians(degrees);
			// The back corner of the cut end: everything of the lower half stays behind the upper half's front face.
			double[] corner = onUpper(THICKNESS, 0.0, angle);
			assertTrue(corner[0] >= -1.0e-9, degrees + " degrees: corner at " + corner[0]);
			double[] front = onUpper(0.0, 0.0, angle);
			assertTrue(front[0] >= -1.0e-9, degrees + " degrees: front edge at " + front[0]);
		}
	}

	@Test
	void noGapOpensOnTheOuterSideOfTheBend() {
		for (double degrees = 0.0; degrees <= LimbBend.MAX_DEGREES; degrees += 1.0) {
			double angle = Math.toRadians(degrees);
			// The front edge of the cut end stays on the joint, or inside the upper half once it has slid.
			double[] front = onUpper(0.0, 0.0, angle);
			assertTrue(front[1] <= 1.0e-9 + (degrees > 90.0 ? THICKNESS : 0.0), degrees + " degrees: " + front[1]);
			assertTrue(front[0] <= THICKNESS, degrees + " degrees: " + front[0]);
			// And the far end of the lower half really swings backward.
			double[] foot = onUpper(0.0, HALF_LENGTH, angle);
			assertTrue(degrees == 0.0 || foot[0] > 0.0, degrees + " degrees: " + foot[0]);
		}
	}
}
