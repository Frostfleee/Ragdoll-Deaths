package com.frostfleee.ragdolldeaths.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArrowPlacementTest {
	private static final double[][] ZOMBIE = {{8, 8, 8}, {8, 12, 4}, {4, 12, 4}, {4, 12, 4}, {4, 12, 4}, {4, 12, 4}};

	@Test
	void aNewArrowLeavesTheEarlierOnesWhereTheyWere() {
		ArrowPlacement.Place[] three = ArrowPlacement.pick(42, ZOMBIE, 3);
		ArrowPlacement.Place[] nine = ArrowPlacement.pick(42, ZOMBIE, 9);
		assertEquals(3, three.length);
		for (int i = 0; i < three.length; i++) {
			assertEquals(three[i], nine[i]);
		}
	}

	@Test
	void differentMobsHaveTheirArrowsInDifferentPlaces() {
		assertNotEquals(ArrowPlacement.pick(1, ZOMBIE, 1)[0], ArrowPlacement.pick(2, ZOMBIE, 1)[0]);
	}

	@Test
	void everyArrowSitsJustInsideAFaceOfItsBox() {
		for (long seed = 0; seed < 200; seed++) {
			for (ArrowPlacement.Place p : ArrowPlacement.pick(seed, ZOMBIE, 8)) {
				int onFace = 0;
				for (double v : new double[] {p.x(), p.y(), p.z()}) {
					assertTrue(v > 0.0 && v < 1.0, "outside its box: " + p);
					onFace += v < 0.1 || v > 0.9 ? 1 : 0;
				}
				assertEquals(1, onFace, "not on one face: " + p);
			}
		}
	}

	@Test
	void bigBoxesAreHitMoreOften() {
		double[][] sizes = {{16, 16, 16}, {1, 1, 1}};
		int big = 0, total = 0;
		for (long seed = 0; seed < 500; seed++) {
			for (ArrowPlacement.Place p : ArrowPlacement.pick(seed, sizes, 4)) {
				big += p.box() == 0 ? 1 : 0;
				total++;
			}
		}
		assertTrue(big > total * 0.95, big + " of " + total);
	}

	@Test
	void aFlatBoxIsHitOnItsFlatSides() {
		for (ArrowPlacement.Place p : ArrowPlacement.pick(5, new double[][] {{8, 8, 0}}, 20)) {
			assertTrue(p.z() < 0.1 || p.z() > 0.9, "on the edge of a flat box: " + p);
		}
	}

	@Test
	void theNumberOfArrowsHasACeilingAndAModelWithNoSurfaceGetsNone() {
		assertEquals(ArrowPlacement.MAX_ARROWS, ArrowPlacement.pick(1, ZOMBIE, 1000).length);
		assertEquals(0, ArrowPlacement.pick(1, new double[0][], 3).length);
		assertEquals(0, ArrowPlacement.pick(1, new double[][] {{0, 0, 0}}, 3).length);
	}
}
