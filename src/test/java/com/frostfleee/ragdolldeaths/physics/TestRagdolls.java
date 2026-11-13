package com.frostfleee.ragdolldeaths.physics;

/** Hand-built ragdolls for the engine tests: a zombie-sized biped with knees and elbows, ten parts. */
final class TestRagdolls {
	private TestRagdolls() {
	}

	/** Standing upright with its feet at the given height. Body 0 is the torso, 1 the head. */
	static Island humanoid(double x, double feetY, double z) {
		Body torso = part(0.25, 0.375, 0.125, 30, x, feetY + 1.125, z);
		Body head = part(0.25, 0.25, 0.25, 8, x, feetY + 1.75, z);
		Body[] bodies = new Body[10];
		Joint[] joints = new Joint[9];
		bodies[0] = torso;
		bodies[1] = head;
		joints[0] = Joint.ball(torso, head, x, feetY + 1.5, z, 0, 1, 0, 1, 0, 0)
				.swing(rad(-50), rad(50), rad(-30), rad(30)).twist(rad(-70), rad(70)).damping(4);
		for (int side = 0; side < 2; side++) {
			double s = side == 0 ? -1 : 1;
			double ax = x + s * 0.375, lx = x + s * 0.125;
			Body upperArm = part(0.125, 0.1875, 0.125, 5, ax, feetY + 1.3125, z);
			Body foreArm = part(0.125, 0.1875, 0.125, 4, ax, feetY + 0.9375, z);
			Body thigh = part(0.125, 0.1875, 0.125, 9, lx, feetY + 0.5625, z);
			Body shin = part(0.125, 0.1875, 0.125, 7, lx, feetY + 0.1875, z);
			int at = 2 + side * 4;
			bodies[at] = upperArm;
			bodies[at + 1] = foreArm;
			bodies[at + 2] = thigh;
			bodies[at + 3] = shin;
			int jt = 1 + side * 4;
			joints[jt] = Joint.ball(torso, upperArm, x + s * 0.25, feetY + 1.5, z, 0, -1, 0, 1, 0, 0)
					.swing(rad(-150), rad(150), rad(-150), rad(150)).twist(rad(-60), rad(60)).damping(3);
			joints[jt + 1] = Joint.hinge(upperArm, foreArm, ax, feetY + 1.125, z, 1, 0, 0).limits(rad(-140), 0).damping(3);
			joints[jt + 2] = Joint.ball(torso, thigh, lx, feetY + 0.75, z, 0, -1, 0, 1, 0, 0)
					.swing(rad(-120), rad(20), rad(-45), rad(45)).twist(rad(-30), rad(30)).damping(3);
			joints[jt + 3] = Joint.hinge(thigh, shin, lx, feetY + 0.375, z, 1, 0, 0).limits(0, rad(140)).damping(3);
		}
		return new Island(bodies, joints);
	}

	/** The same biped, already lying flat on its back with its centre line at the given height. */
	static Island lying(double x, double y, double z) {
		Island doll = humanoid(0, 0, 0);
		// Tip the whole standing pose back about the X axis through the feet: up becomes +Z.
		double s = Math.sqrt(0.5);
		for (Body b : doll.bodies) {
			b.position(x + b.x, y, z + b.y).rotation(s, 0, 0, s);
		}
		return doll;
	}

	private static Body part(double hx, double hy, double hz, double mass, double x, double y, double z) {
		return new Body().box(hx, hy, hz, mass).position(x, y, z);
	}

	private static double rad(double degrees) {
		return Math.toRadians(degrees);
	}
}
