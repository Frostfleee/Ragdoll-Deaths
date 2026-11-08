package com.frostfleee.ragdolldeaths.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class RagdollPoseTest {
	private static final double EPSILON = 1.0E-9;

	@Test
	void restingPoseLeavesPointsWhereTheyAre() {
		assertArrayEquals(new double[] {0.3, 1.2, -0.4}, new RagdollPose().toWorld(0.3, 1.2, -0.4), EPSILON);
	}

	@Test
	void bodyTippedOverPutsItsHeadOnTheGroundToThatSide() {
		RagdollPose pose = new RagdollPose();
		pose.tipToward(1.0, 0.0, Math.PI / 2.0);
		assertArrayEquals(new double[] {1.8, 0.0, 0.0}, pose.toWorld(0.0, 1.8, 0.0), EPSILON);
	}

	@Test
	void rollAboutTheMiddleThenOffsetMovesTheWholeBody() {
		RagdollPose pose = new RagdollPose();
		pose.forwardX = 0.0;
		pose.forwardZ = 1.0;
		pose.pivotY = 0.5;
		pose.rollAngle = Math.PI;
		pose.offsetX = 2.0;
		pose.offsetY = 0.25;
		assertArrayEquals(new double[] {2.0, 1.25, 0.0}, pose.toWorld(0.0, 0.0, 0.0), EPSILON, "the feet end up on top");
	}

	@Test
	void squashFlattensFromTheFeet() {
		RagdollPose pose = new RagdollPose();
		pose.squashY = 0.25;
		pose.squashXZ = 2.0;
		assertArrayEquals(new double[] {1.0, 0.25, 0.0}, pose.toWorld(0.5, 1.0, 0.0), EPSILON);
	}
}
