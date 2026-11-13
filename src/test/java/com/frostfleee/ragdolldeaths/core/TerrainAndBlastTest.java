package com.frostfleee.ragdolldeaths.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerrainAndBlastTest {
	private static final int TOWARD_MINUS_Z = 6;

	private final RagdollConfig config = RagdollConfig.defaults();
	private final RagdollPose pose = new RagdollPose();
	private final JointPose joints = new JointPose();

	private static DeathProfile.Builder zombie() {
		// Yaw 0 faces +Z, so a hit from the front topples the body toward -Z.
		return DeathProfile.builder(BodyKind.STANDARD).rig(Rig.BIPED, 0.45).size(0.6, 1.95).hit(HitDirection.FRONT, 0.0F).seed(3L);
	}

	private static Ground riseToward(int direction, double rise) {
		double[] samples = new double[Ground.DIRECTIONS];
		samples[direction] = rise;
		samples[(direction + 1) % Ground.DIRECTIONS] = rise;
		samples[(direction + Ground.DIRECTIONS - 1) % Ground.DIRECTIONS] = rise;
		return new Ground(1.0, samples);
	}

	private double headHeight(DeathProfile profile, double age) {
		RagdollAnimator.compute(profile, config, age, pose, joints);
		return pose.toWorld(0.0, profile.height(), 0.0)[1];
	}

	@Test
	void groundBlendsBetweenNeighbouringSamples() {
		double[] samples = new double[Ground.DIRECTIONS];
		samples[0] = 1.0;
		Ground ground = new Ground(1.0, samples);
		assertEquals(1.0, ground.riseToward(1.0, 0.0), 1.0E-9);
		assertEquals(0.5, ground.riseToward(Math.cos(Math.PI / 8.0), Math.sin(Math.PI / 8.0)), 1.0E-9);
		assertEquals(0.0, ground.riseToward(0.0, 1.0), 1.0E-9);
		assertTrue(Ground.FLAT.flat());
		assertFalse(ground.flat());
	}

	@Test
	void bodyLyingOnRisingGroundTiltsUpOntoIt() {
		double flat = headHeight(zombie().build(), 25.0);
		double rising = headHeight(zombie().ground(riseToward(TOWARD_MINUS_Z, 0.6)).build(), 25.0);
		assertTrue(rising > flat + 0.4, "the head should rest on the higher ground, not inside it: " + flat + " vs " + rising);
	}

	@Test
	void bodyLyingOverAStepDownTiltsDownButNotPastTheLimit() {
		double flat = headHeight(zombie().build(), 25.0);
		double dropping = headHeight(zombie().ground(riseToward(TOWARD_MINUS_Z, -2.5)).build(), 25.0);
		assertTrue(dropping < flat - 0.3);
		assertTrue(Math.abs(pose.slopeAngle) <= Math.toRadians(30.0) + 1.0E-9);
	}

	@Test
	void upstandingBodyIgnoresTheSlope() {
		RagdollAnimator.compute(zombie().ground(riseToward(TOWARD_MINUS_Z, 0.6)).build(), config, 0.0, pose, joints);
		assertEquals(0.0, pose.slopeAngle, 1.0E-9);
	}

	@Test
	void groundBesideTheBodyDoesNotTiltIt() {
		RagdollAnimator.compute(zombie().ground(riseToward(0, 0.6)).build(), config, 25.0, pose, joints);
		assertEquals(0.0, pose.slopeAngle, Math.toRadians(2.0));
	}

	@Test
	void slopeTiltRotatesAboutTheFeet() {
		RagdollPose tilted = new RagdollPose();
		tilted.tipToward(1.0, 0.0, Math.PI / 2.0);
		tilted.slopeAxisX = 0.0;
		tilted.slopeAxisZ = 1.0;
		tilted.slopeAngle = Math.PI / 2.0;
		tilted.offsetX = 2.0;
		assertArrayEquals(new double[] {2.0, 1.8, 0.0}, tilted.toWorld(0.0, 1.8, 0.0), 1.0E-9);
		assertArrayEquals(new double[] {2.0, 0.0, 0.0}, tilted.toWorld(0.0, 0.0, 0.0), 1.0E-9);
	}

	@Test
	void blastedBodyFliesThenLandsAndRests() {
		DeathProfile blasted = zombie().blasted(true).velocity(0.0, 0.4, -0.35).maxPush(10.0).build();
		double land = RagdollAnimator.landTick(blasted, config);
		assertTrue(land > 5.0, "an upward launch must stay in the air for a while: " + land);

		RagdollAnimator.compute(blasted, config, land / 2.0, pose, joints);
		assertTrue(pose.offsetY > 0.5, "the body should be well off the ground mid-flight");

		double lastZ = 0.0;
		for (double age = 0.0; age <= 40.0; age += 0.5) {
			RagdollAnimator.compute(blasted, config, age, pose, joints);
			assertTrue(pose.offsetY >= -1.0E-9, "never below the floor it was thrown from");
			assertTrue(pose.offsetZ <= lastZ + 1.0E-6, "keeps moving away from the blast, never back");
			lastZ = pose.offsetZ;
		}
		RagdollAnimator.compute(blasted, config, land + 20.0, pose, joints);
		assertTrue(pose.offsetY < 0.3, "settled on the ground after landing");
		assertTrue(RagdollAnimator.endTick(blasted, config) > RagdollConfig.VANILLA_DEATH_TICKS);
		assertTrue(RagdollAnimator.endTick(blasted, config) <= config.ghostMaxTicks);
	}

	@Test
	void blastedBodyStopsAtAWall() {
		DeathProfile pinned = zombie().blasted(true).velocity(0.0, 0.3, -0.6).maxPush(0.5).build();
		for (double age = 0.0; age <= 40.0; age += 0.5) {
			RagdollAnimator.compute(pinned, config, age, pose, joints);
			assertTrue(Math.hypot(pose.offsetX, pose.offsetZ) <= 0.5 + 1.0E-9);
		}
	}

	@Test
	void blastOffALedgeLandsOnTheLowerFloor() {
		DeathProfile ledge = zombie().blasted(true).velocity(0.0, 0.2, -0.4).maxPush(10.0).floorDrop(-3.0).build();
		double land = RagdollAnimator.landTick(ledge, config);
		RagdollAnimator.compute(ledge, config, land + 15.0, pose, joints);
		assertTrue(pose.offsetY < -2.4 && pose.offsetY > -3.0 - 1.0E-9, "rests on the floor 3 blocks down: " + pose.offsetY);
	}

	@Test
	void sidewaysBlastLandsAtOnceAndSlides() {
		DeathProfile shoved = zombie().blasted(true).velocity(0.0, 0.0, -0.5).maxPush(10.0).build();
		assertEquals(1.0, RagdollAnimator.landTick(shoved, config));
		double[] early = RagdollAnimator.trajectory(shoved, config, 2.0);
		double[] late = RagdollAnimator.trajectory(shoved, config, 30.0);
		assertTrue(late[2] < early[2], "slides on after landing");
		assertTrue(late[2] > -2.5, "but friction stops it within a couple of blocks: " + late[2]);
	}

	@Test
	void thrownRiderHopsOffTheSeatThenLandsBelowIt() {
		DeathProfile rider = zombie().freeFall(true).velocity(0.16, 0.14, 0.0).maxPush(10.0).floorDrop(-1.4).build();
		RagdollAnimator.compute(rider, config, 1.0, pose, joints);
		assertTrue(pose.offsetY > 0.0, "leaves the seat with a small hop");
		double land = RagdollAnimator.landTick(rider, config);
		RagdollAnimator.compute(rider, config, land + 20.0, pose, joints);
		assertTrue(pose.offsetY < -1.0, "ends up on the ground beside the mount");
		assertTrue(pose.offsetX > 0.3, "thrown away from the blow");
	}

	@Test
	void groundBodiesHaveNoTrajectory() {
		assertArrayEquals(new double[3], RagdollAnimator.trajectory(zombie().build(), config, 10.0), 0.0);
	}
}
