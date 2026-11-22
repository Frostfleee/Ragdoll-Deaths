package com.frostfleee.ragdolldeaths.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The engine on its own, with no Minecraft: the checks of the build brief's section 14.1 and a few more. */
class PhysicsEngineTest {
	private static final double STONE = 0.8;
	private static final double ICE = 0.05;

	/** A fixed world of boxes. */
	private static final class Boxes implements ColliderProvider {
		private final List<double[]> boxes = new ArrayList<>();

		Boxes box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double friction, double restitution) {
			boxes.add(new double[] {minX, minY, minZ, maxX, maxY, maxZ, friction, restitution});
			return this;
		}

		Boxes floor(double friction) {
			return box(-200, -1, -200, 200, 0, 200, friction, 0);
		}

		@Override
		public void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, BoxBuffer out) {
			for (double[] b : boxes) {
				if (b[0] <= maxX && b[3] >= minX && b[1] <= maxY && b[4] >= minY && b[2] <= maxZ && b[5] >= minZ) {
					out.add(b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7]);
				}
			}
		}
	}

	private static PhysicsWorld world(ColliderProvider colliders) {
		PhysicsWorld w = new PhysicsWorld();
		w.colliders = colliders;
		return w;
	}

	private static Island single(PhysicsWorld w, Body b) {
		return w.add(new Island(new Body[] {b}, new Joint[0]));
	}

	private static void run(PhysicsWorld w, double seconds) {
		for (int i = 0; i < Math.round(seconds * 60); i++) {
			w.step();
		}
	}

	/** The lowest point of a body's box. */
	static double bottom(Body b) {
		double low = Double.MAX_VALUE;
		V3 v = new V3();
		for (int i = 0; i < 8; i++) {
			v.set((i & 1) == 0 ? -b.hx : b.hx, (i & 2) == 0 ? -b.hy : b.hy, (i & 4) == 0 ? -b.hz : b.hz).rotate(b.qx, b.qy, b.qz, b.qw);
			low = Math.min(low, b.y + v.y);
		}
		return low;
	}

	private static double energy(PhysicsWorld w, Island is) {
		double e = is.kineticEnergy();
		for (Body b : is.bodies) {
			e += b.mass * -w.gravity * b.y;
		}
		return e;
	}

	@Test
	void aBoxRestingOnTheGroundStaysStillForTenSeconds() {
		PhysicsWorld w = world(new Boxes().floor(STONE));
		Body flat = new Body().box(0.25, 0.25, 0.25, 10).position(0, 0.25, 0);
		// A second one dropped tilted has to find its own rest first.
		Body tilted = new Body().box(0.3, 0.1, 0.2, 10).position(3, 1.0, 0).rotation(0.3, 0.1, 0.2, 0.9);
		Island a = single(w, flat), b = single(w, tilted);
		run(w, 3);
		double fy = flat.y, tx = tilted.x, ty = tilted.y, tz = tilted.z, tq = tilted.qw;
		run(w, 10);
		// Resting things sit a few millimetres into the ground: the overlap the solver leaves alone.
		assertEquals(0.25, flat.y, 0.006, "flat box height");
		assertEquals(fy, flat.y, 1.0e-3);
		assertEquals(0.0, flat.x, 1.0e-3);
		assertEquals(tx, tilted.x, 1.0e-3);
		assertEquals(ty, tilted.y, 1.0e-3);
		assertEquals(tz, tilted.z, 1.0e-3);
		assertEquals(tq, tilted.qw, 1.0e-3);
		assertTrue(bottom(tilted) > -0.01, "tilted box sank: " + bottom(tilted));
		assertTrue(a.asleep && b.asleep, "both boxes should be asleep");
	}

	@Test
	void aStackOfBoxesStands() {
		PhysicsWorld w = world(new Boxes().floor(STONE));
		Body[] stack = new Body[3];
		for (int i = 0; i < 3; i++) {
			stack[i] = new Body().box(0.25, 0.25, 0.25, 10).position(0, 0.25 + 0.5 * i, 0);
			single(w, stack[i]);
		}
		run(w, 8);
		for (int i = 0; i < 3; i++) {
			assertEquals(0.25 + 0.5 * i, stack[i].y, 0.02, "box " + i + " height");
			assertEquals(0.0, stack[i].x, 0.02);
			assertEquals(0.0, stack[i].z, 0.02);
			assertTrue(stack[i].island.asleep, "box " + i + " asleep");
		}
	}

	@Test
	void aDroppedBodyLandsAtTheRightTime() {
		PhysicsWorld w = world(new Boxes().floor(STONE));
		Body b = new Body().box(0.25, 0.25, 0.25, 10).position(0, 40.25, 0);
		single(w, b);
		double expected = Math.sqrt(2 * 40 / -w.gravity);
		double landed = -1;
		for (int i = 0; i < 300; i++) {
			w.step();
			assertTrue(bottom(b) > -0.02, "went through the floor at step " + i + ": " + bottom(b));
			if (landed < 0 && bottom(b) < 0.02) {
				landed = (i + 1) * PhysicsWorld.STEP;
			}
		}
		assertEquals(expected, landed, 0.04, "landing time");
		assertEquals(0.25, b.y, 0.01);
	}

	@Test
	void nothingTunnelsAtFiveBlocksPerTick() {
		// 5 blocks per tick is 100 blocks per second; the floor is a carpet-thin plate with nothing under it.
		Boxes plate = new Boxes().box(-50, 0, -50, 50, 0.0625, 50, STONE, 0).box(10, 0, -50, 10.0625, 50, 50, STONE, 0);
		PhysicsWorld w = world(plate);
		Body down = new Body().box(0.25, 0.25, 0.25, 10).position(0, 3.1, 0).velocity(0, -100, 0);
		Body spinning = new Body().box(0.3, 0.1, 0.15, 10).position(-5, 2.7, 3).velocity(0, -100, 0).spin(20, 5, 30);
		Body sideways = new Body().box(0.25, 0.25, 0.25, 10).position(3.3, 5, 0).velocity(100, 0, 0);
		single(w, down);
		single(w, spinning);
		single(w, sideways);
		for (int i = 0; i < 120; i++) {
			w.step();
			assertTrue(bottom(down) > 0.0625 - 0.03, "fell through at step " + i);
			assertTrue(bottom(spinning) > 0.0625 - 0.03, "spinning box fell through at step " + i);
			assertTrue(sideways.x < 10.0, "went through the wall at step " + i);
		}
	}

	@Test
	void aBoxStopsSoonerOnStoneThanOnIce() {
		double[] travelled = new double[2];
		double[] friction = {STONE, ICE};
		for (int k = 0; k < 2; k++) {
			PhysicsWorld w = world(new Boxes().floor(friction[k]));
			Body b = new Body().box(0.25, 0.25, 0.25, 10).position(0, 0.25, 0).velocity(6, 0, 0);
			single(w, b);
			run(w, 12);
			travelled[k] = b.x;
			assertTrue(Math.abs(b.vx) < 0.01, "still sliding on friction " + friction[k]);
		}
		assertTrue(travelled[0] > 0.2 && travelled[0] < 2.0, "stone slide " + travelled[0]);
		assertTrue(travelled[1] > 4 * travelled[0], "ice " + travelled[1] + " vs stone " + travelled[0]);
	}

	/** A floor of one-block boxes with the given grip, the way the game's ground is made. */
	private static ColliderProvider blockFloor(double grip) {
		return (x0, y0, z0, x1, y1, z1, out) -> {
			for (int bx = (int) Math.floor(x0); bx <= (int) Math.floor(x1); bx++) {
				for (int bz = (int) Math.floor(z0); bz <= (int) Math.floor(z1); bz++) {
					if (y0 < 0) {
						out.add(bx, -1, bz, bx + 1, 0, bz + 1, grip, 0);
					}
				}
			}
		};
	}

	@Test
	void aBodySlidingAcrossBlockSeamsDoesNotCatchOnThem() {
		// The seams between blocks must not act as bumps: on ice a body slides as far as on one smooth floor.
		double[] travelled = new double[2];
		double[] grip = {STONE, 0.022};
		for (int k = 0; k < 2; k++) {
			PhysicsWorld w = world(blockFloor(grip[k]));
			Island doll = w.add(TestRagdolls.lying(0.5, 0.13, 0.5));
			for (Body b : doll.bodies) {
				b.velocity(0, 0, 6);
			}
			run(w, 10);
			travelled[k] = doll.bodies[0].z;
			for (Body b : doll.bodies) {
				assertTrue(bottom(b) > -0.05, "a part sank into the blocks");
			}
		}
		assertTrue(travelled[1] > 2.5 * travelled[0], "ice " + travelled[1] + " vs stone " + travelled[0]);
	}

	@Test
	void aSlimeBlockBouncesAndStoneDoesNot() {
		PhysicsWorld w = world(new Boxes().box(-5, -1, -5, 5, 0, 5, STONE, 0).box(15, -1, -5, 25, 0, 5, STONE, 0.8));
		Body onStone = new Body().box(0.25, 0.25, 0.25, 10).position(0, 3, 0);
		Body onSlime = new Body().box(0.25, 0.25, 0.25, 10).position(20, 3, 0);
		single(w, onStone);
		single(w, onSlime);
		double stonePeak = 0, slimePeak = 0;
		for (int i = 0; i < 90; i++) {
			w.step();
			if (i > 30) {
				stonePeak = Math.max(stonePeak, onStone.y);
				slimePeak = Math.max(slimePeak, onSlime.y);
			}
		}
		assertTrue(stonePeak < 0.3, "stone bounce " + stonePeak);
		assertTrue(slimePeak > 1.0 && slimePeak < 3.0, "slime bounce " + slimePeak);
	}

	/** A fixed anchor with a row of links hinged end to end, laid out sideways so gravity swings each hinge hard. */
	private static Joint[] hingedChain(PhysicsWorld w, int links, double limit) {
		Body[] bodies = new Body[links + 1];
		Joint[] joints = new Joint[links];
		bodies[0] = new Body().fixed().position(0, 10, 0);
		for (int i = 1; i <= links; i++) {
			bodies[i] = new Body().box(0.25, 0.06, 0.06, 4).position(0.5 * i - 0.25, 10, 0);
			joints[i - 1] = Joint.hinge(bodies[i - 1], bodies[i], 0.5 * (i - 1), 10, 0, 0, 0, 1).limits(-limit, limit);
		}
		w.add(new Island(bodies, joints)).selfCollide = false;
		return joints;
	}

	@Test
	void aHingedChainStaysPinnedAndInsideItsLimits() {
		double limit = Math.toRadians(30);
		// A limb is two links at most (thigh and shin): the stop gives a few degrees under the swing and holds.
		// A five-link whip is far more than any ragdoll has; its stops give more while the whole chain's weight
		// snaps onto them, and still come back to the limit.
		int[] links = {2, 5};
		double[] swingGive = {Math.toRadians(7), Math.toRadians(30)};
		for (int k = 0; k < 2; k++) {
			PhysicsWorld w = world(null);
			Joint[] joints = hingedChain(w, links[k], limit);
			double worstAngle = 0, worstGap = 0;
			for (int i = 0; i < 600; i++) {
				w.step();
				for (Joint j : joints) {
					worstAngle = Math.max(worstAngle, Math.abs(j.hingeAngle()));
					worstGap = Math.max(worstGap, j.separation());
					assertEquals(0.0, j.b.z, 0.01, "a hinge let a link leave its plane");
				}
			}
			assertTrue(worstAngle < limit + swingGive[k], links[k] + " links, worst angle " + Math.toDegrees(worstAngle));
			assertTrue(worstGap < 0.03, links[k] + " links, worst gap " + worstGap);
			for (Joint j : joints) {
				assertTrue(Math.abs(j.hingeAngle()) < limit + Math.toRadians(3.5),
						links[k] + " links, resting angle " + Math.toDegrees(j.hingeAngle()));
			}
			assertFalse(w.get(0).failed);
		}
	}

	@Test
	void aBallJointKeepsItsSwingAndTwistLimits() {
		PhysicsWorld w = world(null);
		Body anchor = new Body().fixed().position(0, 10, 0);
		Body limb = new Body().box(0.1, 0.4, 0.1, 5).position(0, 9.6, 0).velocity(9, 0, 4).spin(0, 25, 0);
		double swing = Math.toRadians(40), twist = Math.toRadians(20);
		Joint j = Joint.ball(anchor, limb, 0, 10, 0, 0, -1, 0, 1, 0, 0).swing(-swing, swing, -swing, swing).twist(-twist, twist);
		w.add(new Island(new Body[] {anchor, limb}, new Joint[] {j}));
		double worst = 0;
		for (int i = 0; i < 600; i++) {
			w.step();
			worst = Math.max(worst, j.swingAngle());
			assertTrue(j.separation() < 0.02);
		}
		assertTrue(worst > Math.toRadians(30), "the limb never reached its limit: " + Math.toDegrees(worst));
		assertTrue(worst < swing + Math.toRadians(4), "worst swing " + Math.toDegrees(worst));
		// With swing held near the axis and twist capped, the spin about the axis cannot carry on.
		assertTrue(Math.abs(limb.wy) < 3.0, "still twisting at " + limb.wy);
	}

	@Test
	void asymmetricSwingLimitsDifferByDirection() {
		// A hip: far forward, barely back. The reference axis is X, so swinging about +X and about -X are the two ways.
		double[] reached = new double[2];
		for (int k = 0; k < 2; k++) {
			PhysicsWorld w = world(null);
			w.gravity = 0;
			Body pelvis = new Body().fixed().position(0, 10, 0);
			Body leg = new Body().box(0.1, 0.4, 0.1, 5).position(0, 9.6, 0).spin(k == 0 ? 6 : -6, 0, 0);
			Joint j = Joint.ball(pelvis, leg, 0, 10, 0, 0, -1, 0, 1, 0, 0)
					.swing(Math.toRadians(-20), Math.toRadians(120), Math.toRadians(-45), Math.toRadians(45));
			w.add(new Island(new Body[] {pelvis, leg}, new Joint[] {j}));
			// Long enough to reach the stop, not long enough to bounce back and swing out the other way.
			for (int i = 0; i < (k == 0 ? 100 : 20); i++) {
				w.step();
				reached[k] = Math.max(reached[k], j.swingAngle());
			}
		}
		assertEquals(120, Math.toDegrees(reached[0]), 5);
		assertEquals(20, Math.toDegrees(reached[1]), 5);
	}

	@Test
	void aMuscleHoldsALimbUpWhenStrongEnoughAndLosesWhenTooWeak() {
		// A 5 kg limb hanging from a fixed anchor. Held out level, gravity's torque on it is 5 * 32 * 0.4 = 64.
		double[] torque = {200.0, 20.0};
		double[] reached = new double[2];
		for (int k = 0; k < 2; k++) {
			PhysicsWorld w = world(null);
			Body anchor = new Body().fixed().position(0, 10, 0);
			Body limb = new Body().box(0.1, 0.4, 0.1, 5).position(0, 9.6, 0);
			Joint j = Joint.ball(anchor, limb, 0, 10, 0, 0, -1, 0, 1, 0, 0).damping(8);
			// Wanted: the limb turned 80 degrees about X from hanging.
			double half = Math.toRadians(80) / 2;
			j.muscle(Math.sin(half), 0, 0, Math.cos(half), 5.0, 1.0, torque[k]);
			w.add(new Island(new Body[] {anchor, limb}, new Joint[] {j}));
			// A muscle at its ceiling is a steady torque, so the limb swings about where that torque balances its
			// weight (about 18 degrees here) and the joint's own stiffness brings it to rest there.
			run(w, 10);
			reached[k] = Math.toDegrees(j.swingAngle());
			assertTrue(j.separation() < 0.02);
			assertTrue(Math.abs(limb.wx) + Math.abs(limb.wy) + Math.abs(limb.wz) < 0.2, "the limb should have come to rest, torque " + torque[k]);
		}
		// A muscle is a spring, so the limb's weight makes it sag a little short of where it is wanted.
		assertEquals(80, reached[0], 18, "a strong muscle holds the limb near where it wants it");
		assertTrue(reached[1] < 25, "a weak muscle cannot lift the limb: " + reached[1]);
		assertTrue(reached[1] > 3, "but it still pulls: " + reached[1]);
	}

	@Test
	void aRelaxedMuscleLetsTheLimbFall() {
		PhysicsWorld w = world(null);
		Body anchor = new Body().fixed().position(0, 10, 0);
		Body limb = new Body().box(0.1, 0.4, 0.1, 5).position(0, 9.6, 0);
		Joint j = Joint.ball(anchor, limb, 0, 10, 0, 0, -1, 0, 1, 0, 0).damping(3);
		double half = Math.toRadians(80) / 2;
		j.muscle(Math.sin(half), 0, 0, Math.cos(half), 5.0, 1.0, 200.0);
		w.add(new Island(new Body[] {anchor, limb}, new Joint[] {j}));
		run(w, 2);
		assertTrue(j.swingAngle() > Math.toRadians(60));
		j.relax();
		run(w, 6);
		assertTrue(j.swingAngle() < Math.toRadians(8), "still raised after letting go: " + Math.toDegrees(j.swingAngle()));
	}

	@Test
	void aBodyInWaterFloatsUpAndACurrentCarriesIt() {
		// No floor: the water is everywhere below y = 5. The lift is worked out each step from how deep the box is.
		PhysicsWorld w = world(null);
		Body b = new Body().box(0.25, 0.25, 0.25, 10).position(0, 2, 0);
		single(w, b);
		for (int i = 0; i < 600; i++) {
			double wet = Math.clamp((5.0 - (b.y - 0.25)) / 0.5, 0.0, 1.0);
			b.lift = 32.0 * 1.3 * wet;
			b.fluidDrag = 2.2 * wet;
			b.fluidSpinDrag = 2.5 * wet;
			b.flowX = 1.0;
			w.step();
		}
		// Floating: 1/1.3 of the box under water, so its centre sits a little below the surface.
		assertEquals(5.0 - 0.5 / 1.3 + 0.25, b.y, 0.05, "floating height");
		assertEquals(1.0, b.vx, 0.05, "carried along at the current's speed");
		assertEquals(0.0, b.vy, 0.05);
	}

	@Test
	void aRisingCurrentLiftsFasterAndASinkingOneSlower() {
		double[] rise = new double[3];
		double[] flowY = {0.0, 4.0, -0.9};
		for (int k = 0; k < 3; k++) {
			PhysicsWorld w = world(null);
			Body b = new Body().box(0.25, 0.25, 0.25, 10).position(0, 0, 0);
			single(w, b);
			b.lift = 32.0 * 1.1;
			b.fluidDrag = 2.2;
			b.flowY = flowY[k];
			run(w, 3);
			rise[k] = b.vy;
		}
		assertTrue(rise[1] > rise[0] + 3.0, "soul sand column " + rise[1] + " vs still water " + rise[0]);
		assertTrue(rise[2] < rise[0] && rise[2] > 0.0, "magma column still rises, slower: " + rise[2] + " vs " + rise[0]);
	}

	@Test
	void energyNeverIncreases() {
		// A free-swinging chain of ball joints: no drag, no damping, so any gain would show.
		PhysicsWorld w = world(null);
		Body[] bodies = new Body[5];
		Joint[] joints = new Joint[4];
		bodies[0] = new Body().fixed().position(0, 10, 0);
		for (int i = 1; i < 5; i++) {
			bodies[i] = new Body().box(0.25, 0.06, 0.06, i == 4 ? 12 : 3).position(0.5 * i - 0.25, 10, 0);
			joints[i - 1] = Joint.ball(bodies[i - 1], bodies[i], 0.5 * (i - 1), 10, 0, 1, 0, 0, 0, 1, 0);
		}
		Island chain = w.add(new Island(bodies, joints));
		chain.selfCollide = false;
		double start = energy(w, chain), span = 19 * -w.gravity * 1.0;
		for (int i = 0; i < 900; i++) {
			w.step();
			double e = energy(w, chain);
			assertTrue(e <= start + 0.01 * span, "chain gained energy at step " + i + ": " + (e - start));
		}

		// A box bouncing and tumbling across the floor only ever loses energy.
		PhysicsWorld w2 = world(new Boxes().box(-200, -1, -200, 200, 0, 200, STONE, 0.5));
		Body box = new Body().box(0.3, 0.2, 0.1, 10).position(0, 4, 0).velocity(5, 0, 2).spin(3, 7, 1);
		Island tumbling = single(w2, box);
		double last = energy(w2, tumbling);
		for (int i = 0; i < 600; i++) {
			w2.step();
			double e = energy(w2, tumbling);
			assertTrue(e <= last + 0.02 * 10 * 32, "box gained energy at step " + i + ": " + (e - last));
			last = Math.min(last, e);
		}
	}

	@Test
	void aRagdollFallsSettlesAndSleepsAboveTheGround() {
		PhysicsWorld w = world(new Boxes().floor(STONE));
		Island doll = w.add(TestRagdolls.humanoid(0, 3, 0));
		for (Body b : doll.bodies) {
			b.velocity(4, 2, 1);
		}
		doll.bodies[0].spin(3, 1, 4);
		double start = energy(w, doll);
		for (int i = 0; i < 60 * 12; i++) {
			w.step();
			assertFalse(doll.failed, "solver failed at step " + i);
			for (Body b : doll.bodies) {
				assertTrue(bottom(b) > -0.05, "a part is under the floor at step " + i + ": " + bottom(b));
			}
			for (Joint j : doll.joints) {
				assertTrue(j.separation() < 0.08, "a joint came apart at step " + i + ": " + j.separation());
			}
			assertTrue(energy(w, doll) < start * 1.02 + 1, "gained energy at step " + i);
		}
		assertTrue(doll.asleep, "the ragdoll never came to rest; energy " + doll.kineticEnergy());
		for (Body b : doll.bodies) {
			assertTrue(b.y < 0.6, "a part is resting too high to be lying down: " + b.y);
		}
	}

	@Test
	void aRagdollTumblesDownStairsWithoutSinkingIn() {
		Boxes stairs = new Boxes().floor(STONE);
		for (int i = 1; i <= 6; i++) {
			stairs.box(-2, 0, -i - 1, 2, i * 0.5, -i, STONE, 0);
		}
		PhysicsWorld w = world(stairs);
		Island doll = w.add(TestRagdolls.humanoid(0, 3.05, -5.5));
		for (Body b : doll.bodies) {
			b.velocity(0, 0, 3);
		}
		for (int i = 0; i < 60 * 15; i++) {
			w.step();
			assertFalse(doll.failed);
			for (Body b : doll.bodies) {
				// The centre of a part must never be inside a step.
				double stepTop = b.z < -1 && b.z > -7 && Math.abs(b.x) < 2 ? Math.floor(-b.z) * 0.5 : 0;
				assertTrue(b.y > stepTop - 0.02, "a part's centre is inside a step at step " + i);
			}
		}
		assertTrue(doll.asleep, "never came to rest on the stairs; energy " + doll.kineticEnergy());
		assertTrue(doll.bodies[0].z > -5.0, "the body did not go down the stairs: " + doll.bodies[0].z);
	}

	@Test
	void twoRagdollsPileUpInsteadOfOverlapping() {
		PhysicsWorld w = world(new Boxes().floor(STONE));
		Island under = w.add(TestRagdolls.lying(0, 0.13, 0));
		run(w, 3);
		assertTrue(under.asleep);
		Island over = w.add(TestRagdolls.lying(0, 2.5, 0));
		run(w, 10);
		assertFalse(under.failed || over.failed);
		Body torsoUnder = under.bodies[0], torsoOver = over.bodies[0];
		assertTrue(torsoOver.y > torsoUnder.y + 0.15 || Math.hypot(torsoOver.x - torsoUnder.x, torsoOver.z - torsoUnder.z) > 0.4,
				"the torsos share the same space: " + torsoUnder.y + " and " + torsoOver.y);
		assertTrue(over.asleep, "the top body never settled");
	}

	@Test
	void aBrokenJointLetsGoAndABadBodyOnlyFailsItself() {
		PhysicsWorld w = world(new Boxes().floor(STONE));
		Body anchor = new Body().fixed().position(0, 5, 0);
		Body weight = new Body().box(0.2, 0.2, 0.2, 50).position(0, 4.5, 0).velocity(0, -30, 0);
		Joint weak = Joint.ball(anchor, weight, 0, 5, 0, 0, -1, 0, 1, 0, 0);
		weak.breakForce = 4000;
		w.add(new Island(new Body[] {anchor, weight}, new Joint[] {weak}));
		Body bad = new Body().box(0.2, 0.2, 0.2, 5).position(5, 1, 0);
		Island badIsland = single(w, bad);
		Body good = new Body().box(0.2, 0.2, 0.2, 5).position(9, 1, 0);
		Island goodIsland = single(w, good);
		w.step();
		bad.x = Double.NaN;
		run(w, 3);
		assertTrue(weak.broken, "the joint should have snapped");
		assertEquals(0.2, weight.y, 0.02, "the freed weight should be on the floor");
		assertTrue(badIsland.failed);
		assertFalse(goodIsland.failed);
		assertEquals(0.2, good.y, 0.01);
	}

	@Test
	void sixtyFourRagdollsFitTheBudget() {
		PhysicsWorld w = world(new Boxes().floor(STONE));
		for (int i = 0; i < 64; i++) {
			Island doll = w.add(TestRagdolls.humanoid((i % 8) * 1.2, 2.5 + (i % 3) * 0.4, (i / 8) * 1.2));
			for (Body b : doll.bodies) {
				b.velocity(Math.sin(i) * 3, 1, Math.cos(i * 1.7) * 3);
			}
			doll.bodies[0].spin(Math.sin(i * 2.1) * 4, 0, Math.cos(i) * 4);
		}
		// Warm up the JIT on a copy of the same work first.
		for (int i = 0; i < 120; i++) {
			w.step();
		}
		w.clear();
		for (int i = 0; i < 64; i++) {
			Island doll = w.add(TestRagdolls.humanoid((i % 8) * 1.2, 2.5 + (i % 3) * 0.4, (i / 8) * 1.2));
			for (Body b : doll.bodies) {
				b.velocity(Math.sin(i) * 3, 1, Math.cos(i * 1.7) * 3);
			}
		}
		double worst = 0, total = 0;
		int ticks = 200, awakeTicks = 0;
		double awakeTotal = 0;
		for (int tick = 0; tick < ticks; tick++) {
			long t0 = System.nanoTime();
			w.step();
			w.step();
			w.step();
			double ms = (System.nanoTime() - t0) / 1.0e6;
			worst = Math.max(worst, ms);
			total += ms;
			int awake = 0;
			for (int i = 0; i < w.size(); i++) {
				awake += w.get(i).asleep ? 0 : 1;
				assertFalse(w.get(i).failed);
			}
			if (awake > 48) {
				awakeTicks++;
				awakeTotal += ms;
			}
		}
		int asleep = 0;
		for (int i = 0; i < w.size(); i++) {
			asleep += w.get(i).asleep ? 1 : 0;
		}
		System.out.printf("[physics benchmark] 64 ragdolls x 10 parts: average %.3f ms/tick, while most are moving %.3f ms/tick"
				+ " (%d ticks), worst %.3f ms; %d of 64 asleep after 10 s%n",
				total / ticks, awakeTicks == 0 ? 0 : awakeTotal / awakeTicks, awakeTicks, worst, asleep);
		// A loose ceiling so a slow CI machine does not fail the build; the printed numbers are what to read.
		assertTrue(total / ticks < 8.0, "average " + total / ticks + " ms per tick");
	}

	/**
	 * An enderman's arm is an eighth of a block thick. Its body sliding off the edge of a platform drags the arm over the
	 * edge: the arm must slide over the corner, never sink into the edge block (it once did, the self-test caught it with
	 * its middle 0.2 blocks deep in the stone). Many starting points, as it depends on how the arm meets the corner.
	 */
	@Test
	void aThinLimbDraggedOverAnEdgeDoesNotSinkIntoIt() {
		List<String> sunk = new ArrayList<>();
		double s = Math.sqrt(0.5);
		for (int angle = -60; angle <= 60; angle += 15) {
			for (double push = 0.5; push <= 3.0; push += 0.5) {
				for (double drop = -0.8; drop <= 0.81; drop += 0.4) {
					// A platform of whole blocks, x below 0, its top at y 1; nothing past the edge.
					Boxes stage = new Boxes();
					for (int x = -4; x < 0; x++) {
						for (int z = -3; z < 3; z++) {
							stage.box(x, 0, z, x + 1, 1, z + 1, STONE, 0);
						}
					}
					PhysicsWorld w = world(stage);
					double a = Math.toRadians(angle);
					// The arm lies flat on the platform, its shoulder end at the edge, pointing back at an angle.
					double half = 0.9375;
					double cx = -Math.cos(a) * half, cz = Math.sin(a) * half;
					// Lying along its length: its long (Y) axis turned onto -X (90 degrees about Z), then round Y to the angle.
					double qy = Math.sin(a / 2), qw = Math.cos(a / 2);
					Body arm = new Body().box(0.0625, half, 0.0625, 3).position(cx, 1.0625, cz).rotation(qy * s, qy * s, qw * s, qw * s);
					// The body just past the edge, the shoulder on its inner side (higher or lower on it), sliding off.
					Body torso = new Body().box(0.25, 0.375, 0.125, 30).position(0.26, 1.0625 + drop * 0.375, 0).velocity(push, 0, 0);
					Joint shoulder = Joint.ball(torso, arm, 0.0, 1.0625, 0.0, 0, -1, 0, 1, 0, 0)
							.swing(Math.toRadians(-150), Math.toRadians(150), Math.toRadians(-150), Math.toRadians(150)).damping(3);
					w.add(new Island(new Body[] {torso, arm}, new Joint[] {shoulder}));
					double deepest = 0.0;
					for (int i = 0; i < 180 && arm.y > -2; i++) {
						w.step();
						// The self-test's check: the middle inside a block, more than 0.03 from all of that block's faces.
						if (arm.x > -4 && arm.x < 0 && arm.y > 0 && arm.y < 1 && arm.z > -3 && arm.z < 3) {
							double depth = Math.min(Math.min(arm.x - Math.floor(arm.x), Math.ceil(arm.x) - arm.x),
									Math.min(Math.min(arm.y, 1.0 - arm.y), Math.min(arm.z - Math.floor(arm.z), Math.ceil(arm.z) - arm.z)));
							deepest = Math.max(deepest, depth);
						}
					}
					if (deepest > 0.03) {
						sunk.add(String.format(java.util.Locale.ROOT, "angle %d push %.1f shoulder %.1f: %.2f deep", angle, push, drop, deepest));
					}
				}
			}
		}
		assertTrue(sunk.isEmpty(), sunk.size() + " sank into the edge: " + sunk);
	}
}
