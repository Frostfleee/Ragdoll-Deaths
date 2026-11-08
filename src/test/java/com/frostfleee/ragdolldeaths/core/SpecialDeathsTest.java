package com.frostfleee.ragdolldeaths.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostfleee.ragdolldeaths.core.JointPose.Side;
import org.junit.jupiter.api.Test;

class SpecialDeathsTest {
	private final RagdollConfig config = RagdollConfig.defaults();
	private final RagdollPose pose = new RagdollPose();
	private final JointPose joints = new JointPose();

	@Test
	void snowGolemMeltsFlatAndItsPumpkinFallsOff() {
		DeathProfile golem = DeathProfile.builder(BodyKind.STANDARD).special(Special.SNOW_GOLEM).size(0.7, 1.9).build();
		RagdollAnimator.compute(golem, config, SpecialDeaths.SNOW_MELT_END, pose, joints);
		assertTrue(joints.meltHeight() < 0.25);
		assertTrue(joints.meltSpread() > 1.3);
		assertEquals(1.0, pose.squashY, "the whole model is never squashed, so the pumpkin keeps its shape");
		assertEquals(1.0, joints.scale(JointRole.HEAD, JointPose.Y));
		assertTrue(joints.translation(JointRole.HEAD, Side.CENTER, JointPose.Z) > 2.0, "the pumpkin lands behind the golem");
		assertTrue(joints.rotation(JointRole.HEAD, Side.CENTER, JointPose.X, 0, 0.0) < -1.0, "the pumpkin tips backward");
	}

	@Test
	void wardenKneelsThenLiesFaceDownBeforeVanishing() {
		DeathProfile warden = DeathProfile.builder(BodyKind.STANDARD).special(Special.WARDEN).rig(Rig.BIPED, 0.3).size(0.9, 2.9).build();
		double end = RagdollAnimator.endTick(warden, config);
		assertEquals(config.lifetimeTicks + config.wardenExtraTicks, end);

		RagdollAnimator.compute(warden, config, 18.0, pose, joints);
		assertTrue(joints.rotation(JointRole.LEG, Side.RIGHT, JointPose.X, 0, 0.0) > 1.2, "still kneeling");
		assertTrue(pose.tipAngle < 0.4);

		RagdollAnimator.compute(warden, config, end - 6.0, pose, joints);
		assertEquals(Math.PI / 2.0, pose.tipAngle, Math.toRadians(4.0));
		assertEquals(0.0, joints.rotation(JointRole.HEAD, Side.CENTER, JointPose.Y, 0, 0.0), 1.0E-9, "face stays toward the ground");
		assertTrue(Math.abs(joints.rotation(JointRole.LEG, Side.RIGHT, JointPose.X, 0, 0.0)) < 0.1, "legs straighten out behind");
		assertEquals(warden.forwardX(), -pose.tipAxisZ, 1.0E-9);
		assertEquals(warden.forwardZ(), pose.tipAxisX, 1.0E-9);
	}

	@Test
	void blazeRodsDropAndLieFlatWhileItsHeadFallsAndRollsOver() {
		DeathProfile blaze = DeathProfile.builder(BodyKind.STANDARD).special(Special.BLAZE).size(0.6, 1.8).hit(HitDirection.LEFT, 0.0F).seed(7L).build();
		RagdollAnimator.compute(blaze, config, 1.0, pose, joints);
		assertTrue(joints.scatters(JointRole.ROD));
		assertEquals(0.0, joints.scatterDrop(JointRole.ROD, 0), "the rods hang on a moment before dropping");

		RagdollAnimator.compute(blaze, config, config.lifetimeTicks - 1.0, pose, joints);
		for (int rod = 0; rod < 12; rod++) {
			assertEquals(1.0, joints.scatterDrop(JointRole.ROD, rod), "rod " + rod + " reached the ground");
			assertEquals(1.0, joints.scatterTip(JointRole.ROD, rod), "rod " + rod + " lies flat");
			assertEquals(0.0, joints.scatterHop(JointRole.ROD, rod), 0.05, "rod " + rod + " has stopped bouncing");
		}
		assertNotEquals(joints.scatterHeading(JointRole.ROD, 0), joints.scatterHeading(JointRole.ROD, 1), "each rod lies its own way");
		assertEquals(20.0, joints.translation(JointRole.HEAD, Side.CENTER, JointPose.Y), 1.0E-3, "the head rests on the ground");
		assertEquals(-8.0, joints.translation(JointRole.HEAD, Side.CENTER, JointPose.X), 1.0E-6, "rolled one face over to its right, away from the blow");
		assertEquals(-Math.PI / 2.0, joints.rotation(JointRole.HEAD, Side.CENTER, JointPose.Z, 0, 0.0), 1.0E-6);
		assertEquals(0.0, pose.tipAngle, "the body itself never tips over");
	}

	@Test
	void blazeAndBreezeSpinDownToAStop() {
		DeathProfile blaze = DeathProfile.builder(BodyKind.STANDARD).special(Special.BLAZE).build();
		assertTrue(SpecialDeaths.idleAge(blaze, 1.0) < 1.0, "already slowing");
		assertEquals(SpecialDeaths.idleAge(blaze, 40.0), SpecialDeaths.idleAge(blaze, 60.0), 1.0E-3, "stopped");
		assertTrue(Double.isNaN(SpecialDeaths.idleAge(DeathProfile.builder(BodyKind.STANDARD).build(), 5.0)));
	}

	@Test
	void breezeWindShrinksIntoTheGroundAndItsHeadLandsUpright() {
		DeathProfile breeze = DeathProfile.builder(BodyKind.STANDARD).special(Special.BREEZE).size(0.6, 1.77).hit(HitDirection.FRONT, 0.0F).build();
		RagdollAnimator.compute(breeze, config, 2.0, pose, joints);
		assertTrue(joints.scale(JointRole.WIND, JointPose.X) > 1.0, "the whirlwind flares first");

		RagdollAnimator.compute(breeze, config, config.lifetimeTicks - 1.0, pose, joints);
		double height = joints.scale(JointRole.WIND, JointPose.Y);
		assertTrue(joints.scale(JointRole.WIND, JointPose.X) < 0.02, "gone");
		assertEquals(24.0 * (1.0 - height), joints.translation(JointRole.WIND, Side.CENTER, JointPose.Y), 1.0E-6, "shrinks down onto the ground");
		assertEquals(20.0, joints.translation(JointRole.HEAD, Side.CENTER, JointPose.Y), 1.0E-3, "the head rests on the ground");
		assertEquals(0.0, joints.translation(JointRole.HEAD, Side.CENTER, JointPose.Z), 1.0E-9, "it doesn't roll away");
		assertEquals(0.0, joints.rotation(JointRole.HEAD, Side.CENTER, JointPose.X, 0, 0.0), 1.0E-9, "still facing ahead");
		assertEquals(0.0, joints.rotation(JointRole.HEAD, Side.CENTER, JointPose.Z, 0, 0.0), 1.0E-9, "not tipped onto its side");
		assertEquals(1.0, joints.scatterTip(JointRole.ROD, 2));
	}

	@Test
	void blazeLandingFromAFallBreaksApartAtOnce() {
		DeathProfile blaze = DeathProfile.builder(BodyKind.STANDARD).special(Special.BLAZE).cause(DeathCause.FALL).build();
		RagdollAnimator.compute(blaze, config, 3.0, pose, joints);
		assertEquals(20.0, joints.translation(JointRole.HEAD, Side.CENTER, JointPose.Y), 0.5, "the head is already down");
	}

	@Test
	void guardianInWaterEndsBellyUpWithSpikesIn() {
		DeathProfile guardian = DeathProfile.builder(BodyKind.AQUATIC).special(Special.GUARDIAN).cause(DeathCause.WATER).size(0.85, 0.85).build();
		assertEquals(1.0, SpecialDeaths.guardianSpikes(guardian, 2.0), "spikes shoot out");
		assertEquals(0.0, SpecialDeaths.guardianSpikes(guardian, 20.0), 1.0E-9, "and sink back in");

		RagdollAnimator.compute(guardian, config, config.lifetimeTicks - 1.0, pose, joints);
		assertEquals(Math.PI, Math.abs(pose.rollAngle), 0.05, "belly-up");
		for (int segment = 0; segment < 3; segment++) {
			for (int axis : new int[] {JointPose.X, JointPose.Y, JointPose.Z}) {
				assertEquals(0.0, joints.rotation(JointRole.TAIL_SEGMENT, Side.CENTER, axis, segment, 0.0), "the tail joints are left to vanilla, so they stay joined");
			}
		}
	}

	@Test
	void guardianRollsAwayFromASideHitLikeOtherAnimals() {
		for (HitDirection hit : new HitDirection[] {HitDirection.LEFT, HitDirection.RIGHT}) {
			for (DeathCause cause : new DeathCause[] {DeathCause.GROUND, DeathCause.WATER}) {
				DeathProfile guardian = DeathProfile.builder(BodyKind.AQUATIC).special(Special.GUARDIAN).cause(cause).hit(hit, 30.0F).size(0.85, 0.85).build();
				DeathProfile cow = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.QUADRUPED, 0.4).hit(hit, 30.0F).size(0.9, 1.4).build();
				RagdollAnimator.compute(guardian, config, config.lifetimeTicks - 1.0, pose, joints);
				double guardianRoll = pose.rollAngle;
				RagdollAnimator.compute(cow, config, config.lifetimeTicks - 1.0, pose, joints);
				assertEquals(Math.signum(pose.rollAngle), Math.signum(guardianRoll), hit + " " + cause);
			}
		}
	}

	@Test
	void guardianTailLashesWithinVanillasSwingThenGoesStraight() {
		DeathProfile guardian = DeathProfile.builder(BodyKind.AQUATIC).special(Special.GUARDIAN).size(0.85, 0.85).build();
		double strongest = 0.0;
		for (double t = 0.0; t < 6.0; t += 0.25) {
			strongest = Math.max(strongest, Math.abs(SpecialDeaths.guardianTail(guardian, t)));
		}
		assertTrue(strongest > 0.8, "lashes hard");
		assertTrue(strongest <= Math.PI / 2.0, "never past vanilla's widest swing");
		assertEquals(0.0, SpecialDeaths.guardianTail(guardian, 20.0), 1.0E-9, "still and straight");
		assertTrue(Double.isNaN(SpecialDeaths.guardianTail(DeathProfile.builder(BodyKind.AQUATIC).build(), 5.0)));
	}

	@Test
	void elderGuardianStillFinishesRollingOverInTime() {
		DeathProfile elder = DeathProfile.builder(BodyKind.AQUATIC).special(Special.GUARDIAN).cause(DeathCause.WATER).size(2.0, 2.0).build();
		assertTrue(elder.heaviness() > 0.9);
		RagdollAnimator.compute(elder, config, config.lifetimeTicks - 1.0, pose, joints);
		assertEquals(Math.PI, Math.abs(pose.rollAngle), 0.05);
	}

	@Test
	void guardianOnLandFlopsThenLiesOnItsSide() {
		DeathProfile guardian = DeathProfile.builder(BodyKind.AQUATIC).special(Special.GUARDIAN).size(0.85, 0.85).build();
		RagdollAnimator.compute(guardian, config, 2.5, pose, joints);
		assertTrue(pose.offsetY > 0.1, "flops up off the ground");
		RagdollAnimator.compute(guardian, config, config.lifetimeTicks - 1.0, pose, joints);
		assertEquals(Math.PI / 2.0, Math.abs(pose.rollAngle), 0.02, "on its side");
	}

	@Test
	void nautilusWithdrawsWhileAZombieNautilusSlumpsOut() {
		DeathProfile nautilus = DeathProfile.builder(BodyKind.AQUATIC).special(Special.NAUTILUS).size(0.875, 0.95).build();
		RagdollAnimator.compute(nautilus, config, 20.0, pose, joints);
		assertEquals(-5.0, joints.translation(JointRole.BODY, Side.CENTER, JointPose.Z), 1.0E-9, "the body pulls back into the shell");
		assertEquals(Math.PI / 2.0, Math.abs(pose.rollAngle), 0.05, "the shell tips onto its side on land");

		DeathProfile zombie = DeathProfile.builder(BodyKind.AQUATIC).special(Special.ZOMBIE_NAUTILUS).size(0.875, 0.95).build();
		RagdollAnimator.compute(zombie, config, 20.0, pose, joints);
		assertEquals(3.0, joints.translation(JointRole.BODY, Side.CENTER, JointPose.Z), 1.0E-9, "the rotten body hangs out");
		assertTrue(joints.rotation(JointRole.BODY, Side.CENTER, JointPose.X, 0, 0.0) > 0.3, "and droops");
	}

	@Test
	void nautilusShellTurnsItsOpeningUpInWater() {
		DeathProfile nautilus = DeathProfile.builder(BodyKind.AQUATIC).special(Special.NAUTILUS).cause(DeathCause.WATER).size(0.875, 0.95).build();
		RagdollAnimator.compute(nautilus, config, 26.0, pose, joints);
		assertEquals(1.1, pose.tipAngle, 1.0E-6);
		assertEquals(nautilus.forwardX(), -pose.tipAxisZ, 1.0E-9, "the top tips forward, so the opening at the back faces up");
	}

	@Test
	void armadilloEndsOnItsBack() {
		DeathProfile armadillo = DeathProfile.builder(BodyKind.STANDARD).special(Special.ROLL_ONTO_BACK).size(0.7, 0.65).build();
		RagdollAnimator.compute(armadillo, config, 20.0, pose, joints);
		assertEquals(Math.PI, Math.abs(pose.rollAngle), Math.toRadians(4.0));
	}

	@Test
	void skeletonBonesEndUpOnTheGround() {
		DeathProfile skeleton = DeathProfile.builder(BodyKind.STANDARD).special(Special.SKELETON).rig(Rig.BIPED, 0.375).size(0.6, 1.99)
				.hit(HitDirection.FRONT, 0.0F).build();
		RagdollAnimator.compute(skeleton, config, 28.0, pose, joints);
		assertEquals(0.0, pose.tipAngle, 1.0E-9, "the pile forms in place instead of the body toppling");
		assertEquals(20.0, joints.translation(JointRole.HEAD, Side.CENTER, JointPose.Y), 0.3);
		assertEquals(22.0, joints.translation(JointRole.BODY, Side.CENTER, JointPose.Y), 0.3);
		assertEquals(21.0, joints.translation(JointRole.ARM, Side.RIGHT, JointPose.Y), 0.3);
		assertTrue(joints.translation(JointRole.HEAD, Side.CENTER, JointPose.Z) > 10.0, "a front hit sends the skull backward");
	}

	@Test
	void specialsFallBackToTheirBodyPlanInLava() {
		DeathProfile skeleton = DeathProfile.builder(BodyKind.STANDARD).special(Special.SKELETON).rig(Rig.BIPED, 0.375)
				.cause(DeathCause.LAVA).size(0.6, 1.99).build();
		RagdollAnimator.compute(skeleton, config, 10.0, pose, joints);
		assertTrue(pose.offsetY < -0.5);
		assertEquals(0.0, joints.translation(JointRole.HEAD, Side.CENTER, JointPose.Y));
	}

	@Test
	void frogCurlsItsArmsAndLegsOnItsBack() {
		DeathProfile frog = DeathProfile.builder(BodyKind.STANDARD).special(Special.ROLL_ONTO_BACK).rig(Rig.BIPED, 0.3).size(0.5, 0.5).build();
		RagdollAnimator.compute(frog, config, 20.0, pose, joints);
		assertEquals(Math.PI, Math.abs(pose.rollAngle), Math.toRadians(4.0));
		assertTrue(joints.rotation(JointRole.ARM, Side.RIGHT, JointPose.X, 0, 0.0) > 0.8);
		assertTrue(joints.rotation(JointRole.LEG, Side.RIGHT, JointPose.X, 0, 0.0) < -0.6);
		assertEquals(1.0, joints.relax(JointRole.LEG), 1.0E-9);
	}

	@Test
	void wardenEndsWithArmsStretchedPastItsHead() {
		DeathProfile warden = DeathProfile.builder(BodyKind.STANDARD).special(Special.WARDEN).rig(Rig.BIPED, 0.3).size(0.9, 2.9).build();
		RagdollAnimator.compute(warden, config, RagdollAnimator.endTick(warden, config) - 2.0, pose, joints);
		assertTrue(joints.rotation(JointRole.ARM, Side.RIGHT, JointPose.X, 0, 0.0) < -2.5);
		assertTrue(joints.rotation(JointRole.ARM, Side.LEFT, JointPose.X, 0, 0.0) < -2.5);
	}

	@Test
	void creeperSplaysItsLegsThenTipsAwayFromTheHit() {
		DeathProfile creeper = DeathProfile.builder(BodyKind.STANDARD).special(Special.CREEPER).rig(Rig.QUADRUPED, 0.23).size(0.6, 1.7)
				.hit(HitDirection.FRONT, 0.0F).build();
		for (double age = 0.0; age <= 30.0; age += 0.5) {
			RagdollAnimator.compute(creeper, config, age, pose, joints);
			assertTrue(Math.abs(joints.rotation(JointRole.FRONT_LEG, Side.RIGHT, JointPose.X, 0, 0.0)) <= 0.45, "legs only buckle a little");
			assertTrue(Math.abs(joints.rotation(JointRole.FRONT_LEG, Side.RIGHT, JointPose.Z, 0, 0.0)) <= 0.15);
		}
		RagdollAnimator.compute(creeper, config, 7.0, pose, joints);
		assertTrue(joints.rotation(JointRole.FRONT_LEG, Side.RIGHT, JointPose.X, 0, 0.0) < -0.3);
		assertTrue(joints.rotation(JointRole.HIND_LEG, Side.RIGHT, JointPose.X, 0, 0.0) > 0.3);

		RagdollAnimator.compute(creeper, config, 25.0, pose, joints);
		assertEquals(Math.PI / 2.0, pose.tipAngle, Math.toRadians(4.0));
		assertEquals(creeper.fallDirX(), -pose.tipAxisZ, 1.0E-9);
		assertEquals(creeper.fallDirZ(), pose.tipAxisX, 1.0E-9);
	}

	@Test
	void ravagerSinksOntoItsBellyAndNeverRollsOver() {
		DeathProfile ravager = DeathProfile.builder(BodyKind.STANDARD).special(Special.RAVAGER).rig(Rig.QUADRUPED, 0.4).size(1.95, 2.2)
				.hit(HitDirection.LEFT, 0.0F).build();
		for (double age = 0.0; age <= 30.0; age += 0.5) {
			RagdollAnimator.compute(ravager, config, age, pose, joints);
			assertTrue(Math.abs(pose.rollAngle) < 0.15, "rolled at tick " + age);
			assertEquals(0.0, pose.tipAngle, 1.0E-9);
		}

		RagdollAnimator.compute(ravager, config, 29.0, pose, joints);
		assertTrue(pose.offsetY < -0.3, "sunk onto its belly");
		assertTrue(joints.rotation(JointRole.FRONT_LEG, Side.RIGHT, JointPose.Z, 0, 0.0) > 0.9, "legs splayed out");
		assertTrue(joints.rotation(JointRole.JAW, Side.CENTER, JointPose.X, 0, 0.0) > 0.5, "jaw hangs open");
		assertEquals(0.0, joints.rotation(JointRole.NECK, Side.CENTER, JointPose.X, 0, 0.0), 1.0E-9, "the head keeps its height instead of dipping into the ground");
		assertEquals(0.0, joints.rotation(JointRole.HEAD, Side.CENTER, JointPose.X, 0, 0.0), 1.0E-9);
	}

	@Test
	void ravagerFallStaysOnItsBelly() {
		DeathProfile ravager = DeathProfile.builder(BodyKind.STANDARD).special(Special.RAVAGER).rig(Rig.QUADRUPED, 0.4).size(1.95, 2.2)
				.cause(DeathCause.FALL).build();
		RagdollAnimator.compute(ravager, config, 25.0, pose, joints);
		assertEquals(0.0, pose.rollAngle, 1.0E-9);
		assertTrue(pose.offsetY < -0.4);
	}
}
