package com.frostfleee.ragdolldeaths.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RagdollAnimatorTest {
	private final RagdollConfig config = RagdollConfig.defaults();
	private final RagdollPose pose = new RagdollPose();
	private final JointPose joints = new JointPose();

	@Test
	void groundDeathEndsLyingOnItsSide() {
		DeathProfile zombie = DeathProfile.builder(BodyKind.STANDARD).size(0.6, 1.95).hit(HitDirection.FRONT, 0.0F).build();
		RagdollAnimator.compute(zombie, config, 19.0, pose, joints);
		assertEquals(Math.PI / 2.0, pose.tipAngle, Math.toRadians(3.0));
		assertFalse(pose.hidden);
		assertTrue(pose.offsetY >= 0.0, "a toppled body must not sink below the feet plane");
	}

	@Test
	void frontHitPushesBackward() {
		DeathProfile zombie = DeathProfile.builder(BodyKind.STANDARD).size(0.6, 1.95).hit(HitDirection.FRONT, 0.0F).build();
		RagdollAnimator.compute(zombie, config, 15.0, pose, joints);
		assertTrue(pose.offsetZ < -0.3, "pronounced push should move the body roughly half a block back");
		assertEquals(0.0, pose.offsetX, 1.0E-9);
	}

	@Test
	void pushNeverPassesAWall() {
		DeathProfile pinned = DeathProfile.builder(BodyKind.STANDARD).size(0.6, 1.95).hit(HitDirection.LEFT, 0.0F).maxPush(0.1).build();
		for (double age = 0.0; age <= 20.0; age += 0.5) {
			RagdollAnimator.compute(pinned, config, age, pose, joints);
			assertTrue(Math.hypot(pose.offsetX, pose.offsetZ) <= 0.1 + 1.0E-9);
		}
	}

	@Test
	void unknownSourceDoesNotPush() {
		DeathProfile poisoned = DeathProfile.builder(BodyKind.STANDARD).size(0.9, 1.4).build();
		RagdollAnimator.compute(poisoned, config, 18.0, pose, joints);
		assertEquals(0.0, Math.hypot(pose.offsetX, pose.offsetZ), 1.0E-9);
		assertTrue(pose.tipAngle > 1.0);
	}

	@Test
	void heavyBodiesCollapseSlowerThanLightOnes() {
		DeathProfile villager = DeathProfile.builder(BodyKind.STANDARD).size(0.6, 1.95).build();
		DeathProfile golem = DeathProfile.builder(BodyKind.STANDARD).size(1.4, 2.7).build();
		assertTrue(golem.heaviness() > 0.5);
		assertEquals(0.0, villager.heaviness());

		RagdollAnimator.compute(villager, config, 5.0, pose, joints);
		double light = pose.tipAngle;
		RagdollAnimator.compute(golem, config, 5.0, pose, joints);
		assertTrue(pose.tipAngle < light);
	}

	@Test
	void fallDeathStaysOnTheGround() {
		DeathProfile faller = DeathProfile.builder(BodyKind.STANDARD).cause(DeathCause.FALL).size(0.6, 1.8).build();
		for (double age = 0.0; age <= 20.0; age += 0.25) {
			RagdollAnimator.compute(faller, config, age, pose, joints);
			assertTrue(pose.offsetY >= 0.0);
			assertTrue(pose.offsetY < 0.6, "bounce must stay short instead of floating");
		}
	}

	@Test
	void lavaSinksAndVanishesEarly() {
		DeathProfile burning = DeathProfile.builder(BodyKind.STANDARD).cause(DeathCause.LAVA).size(0.6, 1.95).build();
		RagdollAnimator.compute(burning, config, RagdollAnimator.LAVA_SUBMERGE_TICKS, pose, joints);
		assertTrue(pose.offsetY <= -1.95);
		assertTrue(RagdollAnimator.endTick(burning, config) < RagdollConfig.VANILLA_DEATH_TICKS);
		RagdollAnimator.compute(burning, config, 18.0, pose, joints);
		assertTrue(pose.hidden);
	}

	@Test
	void waterRuleIsRespectedAndBounded() {
		DeathProfile swimmer = DeathProfile.builder(BodyKind.STANDARD).cause(DeathCause.WATER).size(0.6, 1.95).water(0.4, 0.3).build();
		double liftAllowance = swimmer.width() / 2.0;

		RagdollAnimator.compute(swimmer, config, 19.0, pose, joints);
		assertTrue(pose.offsetY > liftAllowance && pose.offsetY <= 0.4 + liftAllowance);

		RagdollConfig sinking = RagdollConfig.parse("{\"waterBehavior\": \"SINK\"}");
		RagdollAnimator.compute(swimmer, sinking, 19.0, pose, joints);
		assertTrue(pose.offsetY < liftAllowance);
		assertTrue(pose.offsetY >= -0.3);
	}

	@Test
	void aquaticMobsRollBellyUpInWater() {
		DeathProfile cod = DeathProfile.builder(BodyKind.AQUATIC).cause(DeathCause.WATER).size(0.5, 0.3).water(2.0, 0.0).build();
		RagdollAnimator.compute(cod, config, 19.0, pose, joints);
		assertEquals(Math.PI, pose.rollAngle, 1.0E-6);
		assertTrue(pose.offsetY > 0.0);
	}

	@Test
	void beachedFishFlopsWithoutDoubleRotating() {
		DeathProfile cod = DeathProfile.builder(BodyKind.AQUATIC).size(0.5, 0.3).alreadySideways(true).build();
		RagdollAnimator.compute(cod, config, 2.0, pose, joints);
		assertNotEquals(0.0, pose.rollAngle);
		assertTrue(Math.abs(pose.rollAngle) < Math.toRadians(20.0));
	}

	@Test
	void slimeStretchesThenSlumpsIntoAWobblingPuddle() {
		DeathProfile slime = DeathProfile.builder(BodyKind.CUBE).size(2.04, 2.04).hit(HitDirection.FRONT, 0.0F).build();
		RagdollAnimator.compute(slime, config, 2.0, pose, joints);
		assertTrue(pose.squashY > 1.05, "the hit stretches it tall first");
		assertTrue(pose.tipAngle > 0.1, "and it leans away from the blow");

		RagdollAnimator.compute(slime, config, 20.0, pose, joints);
		assertEquals(0.0, pose.tipAngle, 1.0E-6);
		assertTrue(pose.squashY < 0.4);
		assertTrue(pose.squashXZ > 1.4);

		RagdollAnimator.compute(slime, config, 10.0, pose, joints);
		double first = pose.squashY;
		RagdollAnimator.compute(slime, config, 12.0, pose, joints);
		assertNotEquals(first, pose.squashY, "it wobbles like jelly after slumping");
	}

	@Test
	void magmaCubeFoldsDownInSteps() {
		DeathProfile magma = DeathProfile.builder(BodyKind.CUBE).special(Special.MAGMA_CUBE).size(1.0, 1.0).build();
		double previous = 2.0;
		for (double age = 0.0; age <= 14.0; age += 0.5) {
			RagdollAnimator.compute(magma, config, age, pose, joints);
			assertTrue(pose.squashY <= previous + 0.05, "never springs back up at tick " + age);
			previous = pose.squashY;
		}
		RagdollAnimator.compute(magma, config, 5.0, pose, joints);
		double partway = pose.squashY;
		assertTrue(partway < 0.95 && partway > 0.5, "partway down after the first fold, at " + partway);
		RagdollAnimator.compute(magma, config, 20.0, pose, joints);
		assertEquals(0.28, pose.squashY, 0.02);
	}

	@Test
	void ghastFallsToTheFloorAndOutlivesTheVanillaWindow() {
		DeathProfile ghast = DeathProfile.builder(BodyKind.GHAST).size(4.0, 4.0).floorDrop(-20.0).ghostCapable(true).build();
		double land = RagdollAnimator.landTick(ghast, config);
		assertTrue(land > RagdollConfig.VANILLA_DEATH_TICKS && land < config.ghostMaxTicks);
		assertTrue(RagdollAnimator.needsGhost(ghast, config, RagdollConfig.VANILLA_DEATH_TICKS));

		double end = RagdollAnimator.endTick(ghast, config);
		for (double age = 0.0; age < end; age += 1.0) {
			RagdollAnimator.compute(ghast, config, age, pose, joints);
			assertTrue(pose.offsetY >= -20.0, "body must never pass through the floor");
			assertFalse(pose.hidden);
		}
		RagdollAnimator.compute(ghast, config, end, pose, joints);
		assertTrue(pose.hidden);
	}

	@Test
	void ghastOverTheVoidIsCappedByGhostLimit() {
		DeathProfile ghast = DeathProfile.builder(BodyKind.GHAST).size(4.0, 4.0).floorDrop(-200.0).ghostCapable(true).build();
		assertEquals(config.ghostMaxTicks, RagdollAnimator.endTick(ghast, config));
	}

	@Test
	void groundedGhastUsesTheNormalLifetime() {
		DeathProfile grounded = DeathProfile.builder(BodyKind.GHAST).size(4.0, 4.0).floorDrop(0.0).ghostCapable(true).build();
		assertEquals(config.lifetimeTicks, RagdollAnimator.endTick(grounded, config));
	}

	@Test
	void everyBodyOutlivesTheVanillaWindowByDefault() {
		DeathProfile phantom = DeathProfile.builder(BodyKind.WINGED).size(0.9, 0.5).floorDrop(-30.0).build();
		assertTrue(RagdollAnimator.needsGhost(phantom, config, RagdollConfig.VANILLA_DEATH_TICKS));
		assertEquals(RagdollConfig.DEFAULT_LIFETIME_TICKS, RagdollAnimator.endTick(phantom, config));
	}

	@Test
	void shortLifetimeNeedsNoGhost() {
		RagdollConfig vanilla = RagdollConfig.parse("{\"lifetimeTicks\": 20}");
		DeathProfile pig = DeathProfile.builder(BodyKind.STANDARD).size(0.9, 0.9).build();
		assertFalse(RagdollAnimator.needsGhost(pig, vanilla, RagdollConfig.VANILLA_DEATH_TICKS - 1));
	}

	@Test
	void wingedMobsCarryMomentumThenFall() {
		DeathProfile bat = DeathProfile.builder(BodyKind.WINGED).size(0.5, 0.9).velocity(0.3, 0.0, 0.0).floorDrop(-5.0).build();
		RagdollAnimator.compute(bat, config, 10.0, pose, joints);
		assertTrue(pose.offsetX > 0.5);
		assertTrue(pose.offsetY < 0.0);
		assertNotEquals(0.0, pose.spinAngle);
	}

	@Test
	void spiritsDriftDownSlowerThanWingedMobs() {
		DeathProfile vex = DeathProfile.builder(BodyKind.SPIRIT).size(0.4, 0.8).floorDrop(-10.0).build();
		DeathProfile bat = DeathProfile.builder(BodyKind.WINGED).size(0.5, 0.9).floorDrop(-10.0).build();
		assertTrue(RagdollAnimator.verticalTravel(vex, 20.0) > RagdollAnimator.verticalTravel(bat, 20.0));
	}

	@Test
	void shortLifetimeHidesTheBodyEarly() {
		RagdollConfig quick = RagdollConfig.parse("{\"lifetimeTicks\": 10}");
		DeathProfile pig = DeathProfile.builder(BodyKind.STANDARD).size(0.9, 0.9).build();
		RagdollAnimator.compute(pig, quick, 9.0, pose, joints);
		assertFalse(pose.hidden);
		RagdollAnimator.compute(pig, quick, 10.0, pose, joints);
		assertTrue(pose.hidden);
	}

	@Test
	void backFlippersRollOntoTheirBack() {
		DeathProfile spider = DeathProfile.builder(BodyKind.STANDARD).size(1.4, 0.9).flipsOnBack(true).build();
		RagdollAnimator.compute(spider, config, 19.0, pose, joints);
		assertEquals(Math.PI, Math.abs(pose.rollAngle), Math.toRadians(6.0));
		assertEquals(0.0, pose.tipAngle);
	}

	@Test
	void shulkerVanishesAtOnceWithoutMovingOrNeedingAGhost() {
		DeathProfile shulker = DeathProfile.builder(BodyKind.SHULKER).size(1.0, 1.0).attach(0.0, 1.0, 0.0).floorDrop(-10.0).build();
		for (double age : new double[] {0.0, 5.0, 19.0}) {
			RagdollAnimator.compute(shulker, config, age, pose, joints);
			assertTrue(pose.hidden, "hidden at tick " + age);
			assertEquals(0.0, pose.offsetY, "no gravity");
		}
		assertFalse(RagdollAnimator.needsGhost(shulker, config, 0.0));
	}

	@Test
	void bodyInDeepWaterStaysUntilItReachesTheSurface() {
		DeathProfile shallow = DeathProfile.builder(BodyKind.AQUATIC).cause(DeathCause.WATER).water(0.5, 0.0).build();
		assertEquals(config.lifetimeTicks, RagdollAnimator.endTick(shallow, config), "a short rise ends at the normal time");

		DeathProfile deep = DeathProfile.builder(BodyKind.AQUATIC).cause(DeathCause.WATER).water(3.0, 0.0).build();
		double arrival = RagdollAnimator.waterArrivalTick(deep, config);
		assertTrue(arrival > config.lifetimeTicks - 10.0);
		assertEquals(arrival + 10.0, RagdollAnimator.endTick(deep, config), 1.0E-9);
		assertTrue(RagdollAnimator.needsGhost(deep, config, 15.0));
		assertEquals(3.0, RagdollAnimator.waterDrift(deep, config.waterBehavior, arrival), 1.0E-6, "at the surface when it ends");

		DeathProfile abyss = DeathProfile.builder(BodyKind.AQUATIC).cause(DeathCause.WATER).water(12.0, 0.0).build();
		assertEquals(config.ghostMaxTicks, RagdollAnimator.endTick(abyss, config), "never more than 3 seconds");
	}

	@Test
	void sinkingBodyStaysUntilItReachesTheBottom() {
		RagdollConfig sink = RagdollConfig.defaults();
		sink.waterBehavior = WaterBehavior.SINK;
		DeathProfile zombie = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.BIPED, 0.4).cause(DeathCause.WATER).water(4.0, 2.5).build();
		double arrival = RagdollAnimator.waterArrivalTick(zombie, sink);
		assertEquals(Math.min(sink.ghostMaxTicks, arrival + 10.0), RagdollAnimator.endTick(zombie, sink), 1.0E-9);
		assertEquals(-2.5, RagdollAnimator.waterDrift(zombie, sink.waterBehavior, arrival), 1.0E-6);
	}

	@Test
	void knockedOffBodyKeepsItsIdentityButFalls() {
		DeathProfile cow = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.QUADRUPED, 0.5).size(0.9, 1.4).hit(HitDirection.LEFT, 30.0F).seed(7L).build();
		DeathProfile falling = cow.asFreeFall(-9.0, 0.2, -0.1, 0.0, 3.0);
		assertTrue(falling.freeFall());
		assertEquals(-9.0, falling.floorDrop());
		assertEquals(cow.hit(), falling.hit());
		assertEquals(cow.seed(), falling.seed());
		assertEquals(cow.fallDirX(), falling.fallDirX(), 1.0E-12);
		assertTrue(RagdollAnimator.landTick(falling, config) > 0.0);
	}

	@Test
	void groundLiftKeepsToppledBoxAboveTheFeetPlane() {
		DeathProfile cow = DeathProfile.builder(BodyKind.STANDARD).size(0.9, 1.4).build();
		pose.reset();
		pose.tipToward(1.0, 0.0, Math.PI / 2.0);
		assertEquals(0.45 * 0.85, RagdollAnimator.groundLift(cow, pose), 1.0E-9);
	}

	@Test
	void animationSpeedScalesTheChoreographyButNotTheLifetime() {
		RagdollConfig fast = RagdollConfig.parse("{\"animationSpeed\": 2.0}");
		DeathProfile zombie = DeathProfile.builder(BodyKind.STANDARD).size(0.6, 1.95).build();
		RagdollAnimator.compute(zombie, fast, 5.0, pose, joints);
		double fastAngle = pose.tipAngle;
		RagdollAnimator.compute(zombie, config, 10.0, pose, joints);
		assertEquals(pose.tipAngle, fastAngle, 1.0E-9);
		assertEquals(RagdollAnimator.endTick(zombie, config), RagdollAnimator.endTick(zombie, fast));
	}

	@Test
	void zeroBounceRemovesTheSettle() {
		RagdollConfig still = RagdollConfig.parse("{\"bounceStrength\": 0}");
		DeathProfile faller = DeathProfile.builder(BodyKind.STANDARD).cause(DeathCause.FALL).size(0.6, 1.8).build();
		RagdollAnimator.compute(faller, still, 7.0, pose, joints);
		assertEquals(Math.PI / 2.0, pose.tipAngle, 1.0E-9);
	}

	@Test
	void legKicksCanBeTurnedOff() {
		RagdollConfig calm = RagdollConfig.parse("{\"legKicks\": false}");
		DeathProfile cow = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.QUADRUPED, 0.5).size(0.9, 1.4).build();
		RagdollAnimator.compute(cow, calm, 21.0, pose, joints);
		double still = joints.rotation(JointRole.FRONT_LEG, JointPose.Side.RIGHT, JointPose.X, 0, 0.0);
		assertEquals(still, joints.rotation(JointRole.FRONT_LEG, JointPose.Side.RIGHT, JointPose.X, 3, 0.0), 1.0E-9);
	}
}
