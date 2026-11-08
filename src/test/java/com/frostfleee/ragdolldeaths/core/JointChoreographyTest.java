package com.frostfleee.ragdolldeaths.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostfleee.ragdolldeaths.core.JointPose.Side;
import org.junit.jupiter.api.Test;

class JointChoreographyTest {
	private static final int X = JointPose.X;
	private static final int Z = JointPose.Z;

	private final RagdollConfig config = RagdollConfig.defaults();
	private final RagdollPose pose = new RagdollPose();
	private final JointPose joints = new JointPose();

	@Test
	void mirroredSidesSwingOutwardInModelSpace() {
		joints.reset(0.0);
		joints.set(JointRole.ARM, Side.RIGHT, -1.0, 0.0, 0.8, 1.0);
		joints.set(JointRole.ARM, Side.LEFT, -1.0, 0.0, 0.8, 1.0);
		assertEquals(0.8, joints.rotation(JointRole.ARM, Side.RIGHT, Z, 0, 0.0), 1.0E-9);
		assertEquals(-0.8, joints.rotation(JointRole.ARM, Side.LEFT, Z, 0, 0.0), 1.0E-9);
		assertEquals(-1.0, joints.rotation(JointRole.ARM, Side.LEFT, X, 0, 0.0), 1.0E-9);
	}

	@Test
	void unsetJointsKeepTheModelsOwnPose() {
		joints.reset(5.0);
		assertEquals(0.42, joints.rotation(JointRole.TAIL, Side.CENTER, X, 0, 0.42), 1.0E-9);
		joints.set(JointRole.HEAD, Side.CENTER, Double.NaN, 0.5, Double.NaN, 1.0);
		assertEquals(0.3, joints.rotation(JointRole.HEAD, Side.CENTER, X, 0, 0.3), 1.0E-9);
		assertEquals(0.5, joints.rotation(JointRole.HEAD, Side.CENTER, JointPose.Y, 0, 0.0), 1.0E-9);
	}

	@Test
	void twitchesArePhaseShiftedPerLimb() {
		joints.reset(3.0);
		joints.twitch(JointRole.FRONT_LEG, 0.5, 0.0, 0.0, 1.3);
		assertNotEquals(joints.rotation(JointRole.FRONT_LEG, Side.RIGHT, X, 0, 0.0), joints.rotation(JointRole.FRONT_LEG, Side.RIGHT, X, 1, 0.0));
	}

	@Test
	void frontHitBipedReachesUpThenSplaysArmsOnTheGround() {
		DeathProfile zombie = biped(HitDirection.FRONT);
		RagdollAnimator.compute(zombie, config, 5.0, pose, joints);
		assertTrue(joints.rotation(JointRole.ARM, Side.RIGHT, X, 0, 0.0) < -1.0, "arms reach forward while falling");
		assertTrue(joints.rotation(JointRole.HEAD, Side.CENTER, X, 0, 0.0) < -0.3, "head snaps back");

		RagdollAnimator.compute(zombie, config, 19.0, pose, joints);
		assertTrue(joints.rotation(JointRole.ARM, Side.RIGHT, Z, 0, 0.0) > 0.5);
		assertTrue(joints.rotation(JointRole.ARM, Side.LEFT, Z, 0, 0.0) < -0.5);
		assertEquals(Math.PI / 2.0, pose.tipAngle, Math.toRadians(4.0));
	}

	@Test
	void frontHitArmsWaitForTheBodyToLandBeforeDropping() {
		DeathProfile zombie = biped(HitDirection.FRONT);
		double impact = 11.0;
		RagdollAnimator.compute(zombie, config, impact + 1.5, pose, joints);
		assertEquals(Math.PI / 2.0, pose.tipAngle, Math.toRadians(6.0), "the back is already on the ground");
		assertTrue(joints.rotation(JointRole.ARM, Side.RIGHT, X, 0, 0.0) < -1.5, "arms still raised just after contact");
		assertEquals(0.0, joints.rotation(JointRole.HEAD, Side.CENTER, JointPose.Y, 0, 0.0), 1.0E-9, "head has not turned yet");
	}

	@Test
	void everyBipedVariantEndsLyingDown() {
		for (HitDirection hit : HitDirection.values()) {
			RagdollAnimator.compute(biped(hit), config, 19.5, pose, joints);
			assertTrue(Math.abs(pose.tipAngle) > Math.toRadians(70.0), hit + " should end on the ground");
		}
	}

	@Test
	void crumpleKneelsBeforeToppling() {
		DeathProfile villager = biped(HitDirection.NONE);
		RagdollAnimator.compute(villager, config, 6.0, pose, joints);
		assertTrue(joints.rotation(JointRole.LEG, Side.RIGHT, X, 0, 0.0) > 1.0, "legs fold back to kneel");
		assertTrue(pose.offsetY < 0.0, "body lowers onto its knees");
		assertTrue(Math.abs(pose.tipAngle) < 0.5);
	}

	@Test
	void quadrupedTipsOntoItsSideWithNearlyStraightLegs() {
		DeathProfile horse = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.QUADRUPED, 0.45).size(1.4, 1.6).hit(HitDirection.FRONT, 0.0F).build();
		for (double age = 0.0; age <= 30.0; age += 0.5) {
			RagdollAnimator.compute(horse, config, age, pose, joints);
			for (JointRole leg : new JointRole[] {JointRole.FRONT_LEG, JointRole.HIND_LEG}) {
				double bend = joints.rotation(leg, Side.RIGHT, X, 0, 0.0);
				assertTrue(Math.abs(bend) < 0.8, leg + " bends " + bend + " at tick " + age);
			}
		}
		RagdollAnimator.compute(horse, config, 25.0, pose, joints);
		assertEquals(Math.PI / 2.0, Math.abs(pose.rollAngle), Math.toRadians(4.0));
	}

	@Test
	void quadrupedFrontAndBackHitsNeverPitchTheBody() {
		for (HitDirection hit : new HitDirection[] {HitDirection.FRONT, HitDirection.BACK}) {
			DeathProfile cow = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.QUADRUPED, 0.5).size(0.9, 1.4).hit(hit, 0.0F).build();
			for (double age = 0.0; age <= 30.0; age += 0.5) {
				RagdollAnimator.compute(cow, config, age, pose, joints);
				assertEquals(0.0, pose.tipAngle, 1.0E-9, hit + " pitches the body at tick " + age);
				assertTrue(pose.offsetY > -0.2, hit + " sinks into the ground at tick " + age);
			}
			RagdollAnimator.compute(cow, config, 25.0, pose, joints);
			assertEquals(Math.PI / 2.0, Math.abs(pose.rollAngle), Math.toRadians(4.0));
		}
	}

	@Test
	void quadrupedFrontAndBackHitsRollToEitherSide() {
		DeathProfile left = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.QUADRUPED, 0.5).size(0.9, 1.4).hit(HitDirection.FRONT, 0.0F).seed(0L).build();
		DeathProfile right = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.QUADRUPED, 0.5).size(0.9, 1.4).hit(HitDirection.FRONT, 0.0F).seed(8L).build();
		RagdollAnimator.compute(left, config, 25.0, pose, joints);
		double first = Math.signum(pose.rollAngle);
		RagdollAnimator.compute(right, config, 25.0, pose, joints);
		assertEquals(-first, Math.signum(pose.rollAngle));
	}

	@Test
	void quadrupedFrontHitBracesFrontLegsAndTossesHead() {
		DeathProfile horse = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.QUADRUPED, 0.45).size(1.4, 1.6).hit(HitDirection.FRONT, 0.0F).build();
		RagdollAnimator.compute(horse, config, 3.0, pose, joints);
		assertTrue(joints.rotation(JointRole.FRONT_LEG, Side.RIGHT, X, 0, 0.0) < -0.2);
		assertTrue(joints.rotation(JointRole.HEAD, Side.CENTER, X, 0, 0.0) < -0.3);
	}

	@Test
	void wideBoxedAnimalsRestOnTheirRealBodyWidth() {
		DeathProfile horse = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.QUADRUPED, 0.45).size(1.4, 1.6).bodyHalfWidth(0.34).build();
		RagdollAnimator.compute(horse, config, 25.0, pose, joints);
		double lift = RagdollAnimator.groundLift(horse, pose);
		assertTrue(lift < 0.34 * 1.2, "a horse must not float on its wide collision box (lift " + lift + ")");
		assertTrue(lift > 0.3);
	}

	@Test
	void legsUnderneathTuckUpInsteadOfSplayingIntoTheGround() {
		DeathProfile cow = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.QUADRUPED, 0.5).size(0.9, 1.4).hit(HitDirection.RIGHT, 0.0F).build();
		RagdollAnimator.compute(cow, config, 26.0, pose, joints);
		Side ground = pose.rollAngle < 0.0 ? Side.LEFT : Side.RIGHT;
		Side up = ground == Side.LEFT ? Side.RIGHT : Side.LEFT;
		double groundMirror = ground == Side.LEFT ? -1.0 : 1.0;
		assertTrue(groundMirror * joints.rotation(JointRole.FRONT_LEG, ground, Z, 0, 0.0) < 0.0, "ground-side legs tuck inward");
		assertTrue(-groundMirror * joints.rotation(JointRole.FRONT_LEG, up, Z, 0, 0.0) > 0.0, "upper legs splay outward");
	}

	@Test
	void quadrupedHeadNeverTiltsSidewaysIntoTheGround() {
		for (HitDirection hit : HitDirection.values()) {
			DeathProfile horse = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.QUADRUPED, 0.45).size(1.4, 1.6).hit(hit, 0.0F).build();
			for (double age = 0.0; age <= 30.0; age += 1.0) {
				RagdollAnimator.compute(horse, config, age, pose, joints);
				assertEquals(0.0, joints.rotation(JointRole.HEAD, Side.CENTER, Z, 0, 0.0), 1.0E-9);
			}
		}
	}

	@Test
	void spiderFlipsAndCurlsItsLegs() {
		DeathProfile spider = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.ARTHROPOD, 0.3).size(1.4, 0.9).build();
		RagdollAnimator.compute(spider, config, 19.0, pose, joints);
		assertEquals(Math.PI, Math.abs(pose.rollAngle), Math.toRadians(4.0));
		double curled = joints.rotation(JointRole.MID_LEG, Side.RIGHT, Z, 0, -0.58);
		assertEquals(-0.58 + (-2.05 + 0.58) * 0.7, curled, 0.05, "legs curl about 70% of the way");
		assertTrue(joints.rotation(JointRole.MID_LEG, Side.LEFT, Z, 0, 0.58) > 1.5);
		assertEquals(0.39 * 0.3, joints.rotation(JointRole.MID_LEG, Side.RIGHT, JointPose.Y, 0, 0.39), 0.02, "legs stay partly spread instead of bunching");
	}

	@Test
	void wingsFlutterThenDroop() {
		DeathProfile bat = DeathProfile.builder(BodyKind.WINGED).rig(Rig.WINGED, 0.2).size(0.5, 0.9).floorDrop(-4.0).build();
		RagdollAnimator.compute(bat, config, 1.0, pose, joints);
		double early = joints.rotation(JointRole.WING, Side.RIGHT, Z, 0, 0.0);
		RagdollAnimator.compute(bat, config, 18.0, pose, joints);
		assertTrue(Math.abs(early) > 0.1);
		assertEquals(-0.4, joints.rotation(JointRole.WING, Side.RIGHT, Z, 0, 0.0), 0.02);
	}

	@Test
	void ghastTentaclesStreamWhileFallingAndSplayOnLanding() {
		DeathProfile ghast = DeathProfile.builder(BodyKind.GHAST).rig(Rig.TENTACLED, 0.3).size(4.0, 4.0).floorDrop(-20.0).ghostCapable(true).build();
		RagdollAnimator.compute(ghast, config, 10.0, pose, joints);
		double falling = joints.lift(JointRole.TENTACLE);
		RagdollAnimator.compute(ghast, config, RagdollAnimator.landTick(ghast, config) + 6.0, pose, joints);
		assertTrue(falling > 0.8);
		assertTrue(joints.lift(JointRole.TENTACLE) > falling);
	}

	@Test
	void pushIsThirtyPercentBelowTheOriginalHalfBlock() {
		DeathProfile zombie = biped(HitDirection.FRONT);
		RagdollAnimator.compute(zombie, config, 19.0, pose, joints);
		assertEquals(0.35, Math.hypot(pose.offsetX, pose.offsetZ), 1.0E-6);
	}

	private static DeathProfile biped(HitDirection hit) {
		return DeathProfile.builder(BodyKind.STANDARD).rig(Rig.BIPED, 0.375).size(0.6, 1.95).hit(hit, 0.0F).build();
	}

	@Test
	void calmWingsReturnToRestAndBarelyMove() {
		DeathProfile bat = DeathProfile.builder(BodyKind.WINGED).rig(Rig.WINGED, 0.2).size(0.5, 0.9).floorDrop(-4.0).calmWings(true).build();
		double largest = 0.0;
		for (double age = 3.0; age <= 30.0; age += 0.25) {
			RagdollAnimator.compute(bat, config, age, pose, joints);
			assertEquals(1.0, joints.relax(JointRole.WING), 1.0E-9, "vanilla flapping is cancelled");
			largest = Math.max(largest, Math.abs(joints.rotation(JointRole.WING, Side.RIGHT, Z, 0, 0.0) + 0.2 * Math.min(1.0, (age - 2.0) / 8.0)));
		}
		assertTrue(largest < 0.1, "wings only quiver faintly, moved " + largest);
	}

	@Test
	void midAirDeathFallsFirstAndOnlyPlaysTheDeathOnLanding() {
		DeathProfile zombie = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.BIPED, 0.375).size(0.6, 1.95).hit(HitDirection.FRONT, 0.0F)
				.freeFall(true).floorDrop(-12.0).build();
		double land = RagdollAnimator.landTick(zombie, config);
		assertTrue(land > 5.0 && land < config.ghostMaxTicks);

		RagdollAnimator.compute(zombie, config, land - 1.0, pose, joints);
		assertEquals(0.0, pose.tipAngle, 1.0E-9, "no death pose while still falling");
		assertTrue(pose.offsetY < -5.0 && pose.offsetY > -12.0);
		assertTrue(joints.rotation(JointRole.ARM, Side.RIGHT, X, 0, 0.0) < -0.5, "arms trail upward in the fall");

		RagdollAnimator.compute(zombie, config, land + 8.0, pose, joints);
		assertEquals(Math.PI / 2.0, Math.abs(pose.tipAngle), Math.toRadians(8.0), "lies flat after the impact");
		assertTrue(pose.offsetY >= -12.0, "never passes through the floor");
		assertEquals(land + config.lifetimeTicks, RagdollAnimator.endTick(zombie, config), 1.0E-9);
	}

	@Test
	void midAirDeathOverTheVoidVanishesAfterThreeSeconds() {
		DeathProfile pig = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.QUADRUPED, 0.4).size(0.9, 0.9).freeFall(true).floorDrop(-500.0).build();
		assertEquals(config.ghostMaxTicks, RagdollAnimator.endTick(pig, config));
		assertTrue(RagdollAnimator.needsGhost(pig, config, RagdollConfig.VANILLA_DEATH_TICKS));
	}
}
