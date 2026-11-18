package com.frostfleee.ragdolldeaths.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostfleee.ragdolldeaths.core.JointPose.Side;
import org.junit.jupiter.api.Test;

class PhysicsTest {
	private final RagdollConfig config = RagdollConfig.defaults();

	/**
	 * A test world of solid boxes and water, enough for the sweep: blocks are axis-aligned boxes, and a moving box is
	 * clipped per axis (Y first, then X, then Z), the way vanilla resolves entity movement.
	 */
	private static final class TestWorld implements BodyMotion.World {
		private final java.util.List<double[]> solids = new java.util.ArrayList<>();
		private double[] water;
		private double flowX;
		private double flowZ;

		TestWorld solid(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
			solids.add(new double[] {minX, minY, minZ, maxX, maxY, maxZ});
			return this;
		}

		TestWorld floor(double y) {
			return solid(-1000, y - 10, -1000, 1000, y, 1000);
		}

		TestWorld water(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double flowX, double flowZ) {
			this.water = new double[] {minX, minY, minZ, maxX, maxY, maxZ};
			this.flowX = flowX;
			this.flowZ = flowZ;
			return this;
		}

		@Override
		public double[] collide(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double dx, double dy, double dz) {
			double[] box = {minX, minY, minZ, maxX, maxY, maxZ};
			dy = clip(box, 1, dy);
			box[1] += dy;
			box[4] += dy;
			dx = clip(box, 0, dx);
			box[0] += dx;
			box[3] += dx;
			dz = clip(box, 2, dz);
			return new double[] {dx, dy, dz};
		}

		private double clip(double[] box, int axis, double move) {
			for (double[] s : solids) {
				boolean overlaps = true;
				for (int a = 0; a < 3; a++) {
					if (a != axis && (box[a + 3] <= s[a] || box[a] >= s[a + 3])) {
						overlaps = false;
					}
				}
				if (!overlaps) {
					continue;
				}
				if (move > 0 && box[axis + 3] <= s[axis]) {
					move = Math.min(move, s[axis] - box[axis + 3]);
				} else if (move < 0 && box[axis] >= s[axis + 3]) {
					move = Math.max(move, s[axis + 3] - box[axis]);
				}
			}
			return move;
		}

		@Override
		public BodyMotion.Fluid fluidAt(double x, double y, double z) {
			if (water != null && x >= water[0] && x < water[3] && y >= water[1] && y < water[4] && z >= water[2] && z < water[5]) {
				return new BodyMotion.Fluid(true, false, flowX, flowZ);
			}
			return BodyMotion.Fluid.NONE;
		}
	}

	private static BodyMotion falling(double vx, double vy, double vz) {
		return new BodyMotion(BodyMotion.Air.HEAVY, 0.6, 1.95, WaterBehavior.FLOAT, 1.0, false, vx, vy, vz);
	}

	private static void run(BodyMotion motion, BodyMotion.World world, double ax, double ay, double az, int ticks) {
		for (int i = 0; i < ticks; i++) {
			motion.step(world, ax, ay, az, 0.0);
		}
	}

	@Test
	void fallingBodyLandsOnTheRealFloorAndStaysThere() {
		TestWorld world = new TestWorld().floor(0.0);
		BodyMotion motion = falling(0.0, 0.0, 0.0);
		run(motion, world, 0.5, 20.0, 0.5, 120);
		assertTrue(motion.landed());
		assertEquals(DeathCause.GROUND, motion.landedIn());
		assertEquals(-20.0, motion.y(), 1.0E-6, "rests exactly on the floor 20 blocks down");
		assertEquals(BodyMotion.Phase.GROUND, motion.phase());
	}

	@Test
	void bodyThrownIntoACeilingDropsInsteadOfStickingThere() {
		// The ceiling is 2 blocks above the feet: a strong upward throw hits it, then falls back to the floor.
		TestWorld world = new TestWorld().floor(0.0).solid(-1000, 2.0, -1000, 1000, 3.0, 1000);
		BodyMotion motion = falling(0.0, 1.5, 0.0);
		run(motion, world, 0.5, 0.0, 0.5, 4);
		assertTrue(motion.y() <= 2.0 - 0.2 + 1.0E-6, "never above the ceiling");
		run(motion, world, 0.5, 0.0, 0.5, 60);
		assertEquals(0.0, motion.y(), 1.0E-6, "fell back down to the floor");
	}

	@Test
	void bodyDriftingIntoACliffFallsPastItToTheFloorBelow() {
		// Knocked off a ledge at y=0 that ends at x=1, a block above it; the ground below the cliff is at y=-10. The
		// floor straight below where it died is the ledge, but the body carries past its edge and keeps falling.
		TestWorld world = new TestWorld().floor(-10.0).solid(-1000, -10.0, -1000, 1.0, 0.0, 1000);
		BodyMotion motion = falling(0.3, 0.0, 0.0);
		run(motion, world, 0.5, 1.0, 0.5, 120);
		assertEquals(-11.0, motion.y(), 1.0E-6, "landed on the ground below the cliff, not on the ledge's height");
		assertTrue(motion.x() > 0.5);
	}

	@Test
	void wallStopsAThrownBody() {
		TestWorld world = new TestWorld().floor(0.0).solid(3.0, 0.0, -1000, 4.0, 10.0, 1000);
		BodyMotion motion = falling(2.0, 0.5, 0.0);
		run(motion, world, 0.5, 0.0, 0.5, 80);
		assertTrue(0.5 + motion.x() + 0.3 <= 3.0 + 1.0E-6, "the box stays out of the wall");
	}

	@Test
	void hardLandingBouncesThenSettles() {
		TestWorld world = new TestWorld().floor(0.0);
		BodyMotion motion = falling(0.0, 0.0, 0.0);
		boolean bounced = false;
		double last = 0.0;
		for (int i = 0; i < 80; i++) {
			motion.step(world, 0.5, 6.0, 0.5, 0.0);
			if (motion.landed() && motion.y() > last + 1.0E-6) {
				bounced = true;
			}
			last = motion.y();
		}
		assertTrue(bounced, "a 6-block drop bounces once");
		assertEquals(-6.0, motion.y(), 1.0E-6);
	}

	@Test
	void flowingWaterCarriesTheBody() {
		TestWorld world = new TestWorld().floor(0.0).water(-1000, 0.0, -1000, 1000, 1.0, 1000, 1.0, 0.0);
		BodyMotion motion = falling(0.0, 0.0, 0.0);
		run(motion, world, 0.5, 4.0, 0.5, 100);
		assertTrue(motion.landed());
		assertEquals(DeathCause.WATER, motion.landedIn());
		assertTrue(motion.x() > 3.0, "carried downstream: " + motion.x());
		assertTrue(motion.y() > -4.0 + 0.2, "floats instead of lying on the bottom");
	}

	@Test
	void bodyDyingInFlowingWaterDriftsSidewaysOnly() {
		TestWorld world = new TestWorld().floor(-5.0).water(-1000, -5.0, -1000, 1000, 2.0, 1000, 0.0, 1.0);
		BodyMotion motion = new BodyMotion(BodyMotion.Air.HEAVY, 0.6, 1.95, WaterBehavior.FLOAT, 1.0, true, 0.0, 0.0, 0.0);
		for (int i = 0; i < 40; i++) {
			motion.step(world, 0.5, 0.0, 0.5, 0.05 * i);
		}
		assertTrue(motion.z() > 1.5, "carried along the current");
		assertEquals(0.05 * 39, motion.y(), 1.0E-9, "its rise is the animation's");
		assertTrue(motion.isDriftOnly());
	}

	@Test
	void stillWaterLetsTheBodyFloatInPlace() {
		TestWorld world = new TestWorld().floor(-5.0).water(-1000, -5.0, -1000, 1000, 0.0, 1000, 0.0, 0.0);
		BodyMotion motion = falling(0.0, 0.0, 0.0);
		run(motion, world, 0.5, 3.0, 0.5, 200);
		assertEquals(0.0, motion.x(), 1.0E-9);
		assertTrue(motion.y() > -4.2 && motion.y() < -2.8, "bobs near the surface 3 blocks below: " + motion.y());
	}

	@Test
	void simulatedFallStaysUntilItLandsInsteadOfVanishingInTheAir() {
		DeathProfile falling = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.BIPED, 0.45).size(0.6, 1.95).freeFall(true).simulated(true).fallLimit(200.0).build();
		assertTrue(RagdollAnimator.needsGhost(falling, config, 19.0));
		assertEquals(200.0, RagdollAnimator.endTick(falling, config), "still falling: kept up to the fall limit");
		DeathProfile landed = falling.withLanding(70.0, DeathCause.GROUND);
		assertEquals(70.0 + config.bodyLifetimeTicks, RagdollAnimator.endTick(landed, config), 1.0E-9, "lands at 3.5 s, then rests");
		RagdollPose pose = new RagdollPose();
		RagdollAnimator.compute(landed, config, 75.0, pose, new JointPose());
		assertFalse(pose.hidden);
	}

	@Test
	void flyingMobsAlsoStayUntilTheyLand() {
		DeathProfile bat = DeathProfile.builder(BodyKind.WINGED).size(0.5, 0.9).simulated(true).fallLimit(200.0).build();
		assertEquals(200.0, RagdollAnimator.endTick(bat, config));
		assertTrue(RagdollAnimator.endTick(bat.withLanding(45.0, DeathCause.GROUND), config) > 45.0);
	}

	@Test
	void bodyLandingInWaterPlaysTheWaterDeath() {
		DeathProfile falling = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.BIPED, 0.45).size(0.6, 1.95).freeFall(true).simulated(true).build();
		RagdollPose pose = new RagdollPose();
		RagdollAnimator.compute(falling.withLanding(10.0, DeathCause.WATER), config, 14.0, pose, new JointPose());
		RagdollPose slam = new RagdollPose();
		RagdollAnimator.compute(falling.withLanding(10.0, DeathCause.GROUND), config, 14.0, slam, new JointPose());
		assertTrue(Math.abs(pose.tipAngle - slam.tipAngle) > 1.0E-3 || Math.abs(pose.rollAngle - slam.rollAngle) > 1.0E-3,
				"water and ground landings look different");
	}

	@Test
	void simulatedBodiesTakeTheirPositionFromTheSimulation() {
		DeathProfile blasted = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.BIPED, 0.45).size(0.6, 1.95).blasted(true).simulated(true)
				.velocity(0.0, 2.0, -3.0).build();
		RagdollPose pose = new RagdollPose();
		RagdollAnimator.compute(blasted, config, 10.0, pose, new JointPose());
		assertTrue(Math.abs(pose.offsetZ) < 0.5 && pose.offsetY < 1.0, "no precomputed flight is added on top");
	}

	// ---------------------------------------------------------------------------------------------- limbs

	private static DeathProfile zombie(HitDirection hit) {
		return DeathProfile.builder(BodyKind.STANDARD).rig(Rig.BIPED, 0.45).size(0.6, 1.95).hit(hit, 0.0F).seed(3L).build();
	}

	/** Steps the limbs through a death, returning the largest swing seen and the swing left at the end. */
	private double[] runLimbs(DeathProfile profile, int ticks) {
		LimbDynamics limbs = new LimbDynamics();
		RagdollPose pose = new RagdollPose();
		JointPose joints = new JointPose();
		double largest = 0.0;
		for (int tick = 0; tick <= ticks; tick++) {
			RagdollAnimator.compute(profile, config, tick, pose, joints);
			double[] middle = pose.toWorld(0.0, profile.height() / 2.0, 0.0);
			limbs.step(profile, config, pose, joints, middle[0], middle[1], middle[2], tick);
			largest = Math.max(largest, limbs.largestSwing());
		}
		return new double[] {largest, limbs.largestSwing()};
	}

	@Test
	void limbsSwingWithTheFallThenComeToRest() {
		double[] swing = runLimbs(zombie(HitDirection.FRONT), 120);
		assertTrue(swing[0] > 0.15, "the fall and the impact visibly swing the limbs: " + swing[0]);
		assertTrue(swing[0] <= 1.1 + 1.0E-9, "never past the swing limit");
		assertTrue(swing[1] < 0.35, "they settle (gravity keeps only a small sag): " + swing[1]);
	}

	@Test
	void everyDirectionStaysBoundedAndFinite() {
		for (HitDirection hit : HitDirection.values()) {
			for (DeathCause cause : DeathCause.values()) {
				DeathProfile profile = DeathProfile.builder(BodyKind.STANDARD).rig(Rig.QUADRUPED, 0.5).size(0.9, 1.4).hit(hit, 40.0F).cause(cause).seed(9L).build();
				double[] swing = runLimbs(profile, 60);
				assertTrue(Double.isFinite(swing[0]) && swing[0] <= 1.1 + 1.0E-9, hit + " " + cause + ": " + swing[0]);
			}
		}
	}

	@Test
	void killingBlowFlingsTheArmsTowardIt() {
		LimbDynamics limbs = new LimbDynamics();
		DeathProfile profile = zombie(HitDirection.FRONT);
		RagdollPose pose = new RagdollPose();
		JointPose joints = new JointPose();
		for (int tick = 0; tick <= 2; tick++) {
			RagdollAnimator.compute(profile, config, tick, pose, joints);
			limbs.step(profile, config, pose, joints, 0.0, 1.0, 0.0, tick);
		}
		// Hit from the front (+Z), the body is shoved back (-Z) and the arms are left swinging forward: negative X.
		assertTrue(limbs.swing(JointRole.ARM, Side.RIGHT, JointPose.X) < -0.02, "arm swings toward the hit: " + limbs.swing(JointRole.ARM, Side.RIGHT, JointPose.X));
	}

	@Test
	void fallingLimbsFloatThenAHardLandingSlamsThemDown() {
		LimbDynamics limbs = new LimbDynamics();
		DeathProfile profile = zombie(HitDirection.NONE);
		RagdollPose pose = new RagdollPose();
		JointPose joints = new JointPose();
		// Arms held straight out in front, like a falling zombie's.
		joints.both(JointRole.ARM, -Math.PI / 2.0, 0.0, 0.0, 1.0);
		double y = 10.0;
		double vy = 0.0;
		for (int tick = 0; tick < 8; tick++) {
			vy -= 0.08;
			y += vy;
			limbs.step(profile, config, pose, joints, 0.0, y, 0.0, 0.0);
		}
		double falling = limbs.swing(JointRole.ARM, Side.RIGHT, JointPose.X);
		assertEquals(0.0, falling, 0.05, "weightless while falling");
		limbs.step(profile, config, pose, joints, 0.0, y, 0.0, 0.0);
		limbs.step(profile, config, pose, joints, 0.0, y, 0.0, 0.0);
		assertTrue(limbs.swing(JointRole.ARM, Side.RIGHT, JointPose.X) > falling + 0.1,
				"stopping short slams the outstretched arms down: " + limbs.swing(JointRole.ARM, Side.RIGHT, JointPose.X));
	}

	@Test
	void appliedSwingsBlendBetweenTicks() {
		LimbDynamics limbs = new LimbDynamics();
		DeathProfile profile = zombie(HitDirection.FRONT);
		RagdollPose pose = new RagdollPose();
		JointPose joints = new JointPose();
		for (int tick = 0; tick <= 3; tick++) {
			RagdollAnimator.compute(profile, config, tick, pose, joints);
			limbs.step(profile, config, pose, joints, 0.0, 1.0, 0.0, tick);
		}
		JointPose half = new JointPose();
		limbs.applyTo(half, 0.5);
		JointPose whole = new JointPose();
		limbs.applyTo(whole, 1.0);
		double end = whole.rotation(JointRole.ARM, Side.RIGHT, JointPose.X, 0, 0.0);
		assertEquals(limbs.swing(JointRole.ARM, Side.RIGHT, JointPose.X), end, 1.0E-9);
		assertTrue(Math.abs(half.rotation(JointRole.ARM, Side.RIGHT, JointPose.X, 0, 0.0)) <= Math.abs(end) + 0.5);
	}
}
