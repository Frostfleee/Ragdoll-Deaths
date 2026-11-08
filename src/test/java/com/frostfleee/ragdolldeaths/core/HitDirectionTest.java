package com.frostfleee.ragdolldeaths.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HitDirectionTest {
	@ParameterizedTest
	@CsvSource({
			"0, 0, 1, FRONT",
			"0, 0, -1, BACK",
			"0, 1, 0, LEFT",
			"0, -1, 0, RIGHT",
			"90, -1, 0, FRONT",
			"90, 1, 0, BACK",
			"90, 0, 1, LEFT",
			"180, 0, -1, FRONT",
			"-90, 1, 0, FRONT",
			"270, 1, 0, FRONT",
			"45, -1, 1, FRONT",
			"0, 0.4, 1, FRONT",
			"0, 1, 0.4, LEFT"
	})
	void classifiesRelativeToBodyYaw(float yaw, double toX, double toZ, HitDirection expected) {
		assertEquals(expected, HitDirection.classify(yaw, toX, toZ));
	}

	@Test
	void sourceOnTopOfVictimHasNoDirection() {
		assertEquals(HitDirection.NONE, HitDirection.classify(30.0F, 0.0, 0.0));
		assertEquals(HitDirection.NONE, HitDirection.classify(30.0F, 1.0E-4, -1.0E-4));
	}

	@Test
	void bodyFallsAwayFromTheHit() {
		assertDirection(new double[] {0.0, -1.0}, HitDirection.FRONT.fallDirection(0.0F, 0L));
		assertDirection(new double[] {0.0, 1.0}, HitDirection.BACK.fallDirection(0.0F, 0L));
		assertDirection(new double[] {-1.0, 0.0}, HitDirection.LEFT.fallDirection(0.0F, 0L));
		assertDirection(new double[] {1.0, 0.0}, HitDirection.RIGHT.fallDirection(0.0F, 0L));
	}

	@Test
	void unknownHitFallsSidewaysDeterministically() {
		double[] even = HitDirection.NONE.fallDirection(0.0F, 2L);
		double[] odd = HitDirection.NONE.fallDirection(0.0F, 3L);
		assertDirection(new double[] {1.0, 0.0}, even);
		assertDirection(new double[] {-1.0, 0.0}, odd);
		assertDirection(even, HitDirection.NONE.fallDirection(0.0F, 2L));
	}

	@Test
	void fallDirectionIsOppositeOfTheSourceForAnyYaw() {
		for (float yaw = -180.0F; yaw <= 180.0F; yaw += 15.0F) {
			double rad = Math.toRadians(yaw);
			double frontX = -Math.sin(rad);
			double frontZ = Math.cos(rad);
			assertEquals(HitDirection.FRONT, HitDirection.classify(yaw, frontX, frontZ));
			double[] fall = HitDirection.FRONT.fallDirection(yaw, 0L);
			assertEquals(-1.0, fall[0] * frontX + fall[1] * frontZ, 1.0E-9);
		}
	}

	private static void assertDirection(double[] expected, double[] actual) {
		assertArrayEquals(expected, actual, 1.0E-9);
	}
}
