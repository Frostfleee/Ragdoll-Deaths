package com.frostfleee.ragdolldeaths.physics;

import java.util.ArrayList;

/**
 * Steps every ragdoll: rigid boxes joined by limited joints, colliding with the fixed world and with each other.
 * Plain Java with no Minecraft types, so it runs in unit tests.
 *
 * <p>The solver is sequential impulses with soft constraints and substeps (the "soft step" scheme of Box2D 3). Each
 * substep applies gravity, then trades impulses along every joint and contact until the bodies' velocities agree
 * with all of them, moves the bodies, and trades once more without any position correction so that fixing an
 * overlap never leaves a body with speed it did not earn.
 *
 * <p>Why this and not XPBD (position-based dynamics), which was built and measured first: XPBD pushes positions
 * around every substep and reads velocity back from the result, starting from nothing each time. At a substep
 * count the game can afford (three, with two passes each) a ragdoll lying on the ground never settled: the joint
 * limits and the floor contacts kept shoving its limbs back and forth, the leftover showed up as speed, and the
 * body shivered, crept along the ground and never fell asleep; a stack of three boxes slid apart. An impulse solver
 * works on velocity and remembers: every joint and contact keeps the impulse it ended the last step with and starts
 * the next one from it, so a resting body is simply one whose remembered impulses already cancel gravity, and its
 * speed is truly zero. Soft constraints (a stiff, heavily damped spring instead of a hard push) keep it from
 * blowing up at high mass ratios, and the same soft angular constraint is what muscles will be made of.
 *
 * <p>The step is fixed at 1/60 s. {@link #advance} runs as many steps as the elapsed time needs and {@link #alpha}
 * says how far to draw between the last two, so motion is smooth and equally fast at any frame rate.
 */
public final class PhysicsWorld {
	public static final double STEP = 1.0 / 60.0;
	static final double CONTACT_MARGIN = 0.02;
	/** A contact this close below a block's top counts as on the top: lower down it is on the side, a real wall. */
	private static final double TOP_EDGE = 0.12;
	/** A new contact within this distance of one from the last step is the same contact, and inherits its impulses. */
	private static final double SAME_CONTACT = 0.04;
	/** A limit further away than this many radians cannot be reached within a substep, so it is left alone. */
	private static final double LIMIT_RANGE = 0.6;

	/** Blocks per second squared. Minecraft mobs fall at 0.08 blocks per tick squared, which is 32. */
	public double gravity = -32.0;
	public int substeps = 3;
	public int maxStepsPerAdvance = 4;
	public double maxLinearSpeed = 120.0;
	public double maxAngularSpeed = 50.0;
	/** Stiffness of contacts and joints as springs, in hertz; capped at a quarter of the substep rate. */
	public double contactHertz = 30.0;
	public double jointHertz = 60.0;
	/** The fastest an overlap is pushed apart, blocks per second. Faster would pop a buried body into the air. */
	public double maxPushOut = 3.0;
	/**
	 * The fastest a joint that has been pulled apart closes again, blocks per second. A violent blow can drive a
	 * torso down past its own legs for a moment; closing that gap at full speed would fling the body into the air.
	 */
	public double maxJointPull = 8.0;
	/** The fastest a part past a joint limit is turned back, radians per second. */
	public double maxLimitSpeed = 8.0;
	/** Overlap that is left alone, so that touching things keep touching instead of flickering apart. */
	public double slop = 0.003;
	/**
	 * A ragdoll goes to sleep when, for {@link #sleepDelay} seconds, no part of it has moved further than this many
	 * blocks or turned more than {@link #sleepAngle} radians. Measuring where parts end up, rather than their speed
	 * at one instant, ignores a shiver around a rest pose and still notices any real, steady motion.
	 */
	public double sleepDistance = 0.006;
	public double sleepAngle = 0.03;
	public double sleepDelay = 0.5;
	/** An awake ragdoll moving faster than this wakes a sleeping one it reaches. */
	public double wakeSpeed = 0.6;
	/** Whether different ragdolls collide with each other. */
	public boolean bodyCollisions = true;
	public ColliderProvider colliders;
	/**
	 * Bump this whenever the fixed world may have changed (the game does it once a tick). A ragdoll's world boxes are
	 * fetched once per stamp for a region a little bigger than the ragdoll, and reused while it stays inside.
	 */
	public long worldStamp;
	private static final double BOX_PAD = 0.5;
	private final BoxBuffer probe = new BoxBuffer();

	private final ArrayList<Island> islands = new ArrayList<>();
	private Contact[] contacts = new Contact[256];
	private int contactCount;
	private double accumulator;
	private final BoxCollision col = new BoxCollision();
	private final V3 t = new V3();
	/** Stands in for the fixed world as the second body of a contact: it has no inverse mass and never moves. */
	private final Body ground = new Body().fixed();

	// Soft-constraint coefficients for the current substep length.
	private double contactBias, contactMass, contactImpulse;
	private double jointBias, jointMass, jointImpulse;
	// Scratch for building a pin's 3×3 matrix.
	private double k00, k01, k02, k11, k12, k22;
	/** The substep length of the step being solved. */
	private double substep;

	/** One direction a contact acts along (its normal, or a friction direction) with everything needed to solve it. */
	private static final class Row {
		double dx, dy, dz;
		// (anchor × direction) for each body, and the same times that body's inverse inertia.
		double cax, cay, caz, cbx, cby, cbz;
		double uax, uay, uaz, ubx, uby, ubz;
		double mass;
		double acc;
	}

	private static final class Contact {
		Body a, b;
		/** The touching point in each body's own space (world space for the ground). */
		double rax, ray, raz, rbx, rby, rbz;
		/** The same two points as offsets from the bodies' centres, in world space, for the current substep. */
		double wax, way, waz, wbx, wby, wbz;
		double friction, restitution;
		/** Closing speed along the normal when the step began, for the bounce. */
		double approach;
		double maxNormal;
		final Row n = new Row(), t1 = new Row(), t2 = new Row();
	}

	public PhysicsWorld() {
		ground.refresh();
	}

	public Island add(Island island) {
		islands.add(island);
		return island;
	}

	public void remove(Island island) {
		islands.remove(island);
	}

	public void clear() {
		islands.clear();
		accumulator = 0.0;
	}

	public int size() {
		return islands.size();
	}

	public Island get(int i) {
		return islands.get(i);
	}

	/** Contacts found in the last step, for tests and the debug log. */
	public int contactCount() {
		return contactCount;
	}

	/** Runs the fixed steps the elapsed time calls for. Returns how many ran. */
	public int advance(double seconds) {
		accumulator += seconds;
		int steps = 0;
		while (accumulator >= STEP && steps < maxStepsPerAdvance) {
			step();
			accumulator -= STEP;
			steps++;
		}
		if (accumulator > STEP) {
			// After a long freeze, drop the backlog rather than race to catch up.
			accumulator = STEP;
		}
		return steps;
	}

	/** How far between the previous step's pose (0) and the current one (1) to draw. */
	public double alpha() {
		return accumulator / STEP;
	}

	/** One fixed step of 1/60 s. */
	public void step() {
		int n = islands.size();
		for (int i = 0; i < n; i++) {
			Island is = islands.get(i);
			if (!is.failed && !is.asleep) {
				prepare(is);
			}
		}
		if (bodyCollisions) {
			for (int i = 0; i < n; i++) {
				Island sleeper = islands.get(i);
				if (!sleeper.asleep || sleeper.failed) {
					continue;
				}
				for (int j = 0; j < n; j++) {
					Island mover = islands.get(j);
					if (!mover.asleep && !mover.failed && mover.maxSpeed > wakeSpeed && mover.overlaps(sleeper)) {
						sleeper.wake();
						prepare(sleeper);
						break;
					}
				}
			}
		}

		contactCount = 0;
		for (int i = 0; i < n; i++) {
			Island is = islands.get(i);
			if (is.failed) {
				continue;
			}
			if (!is.asleep && is.selfCollide) {
				selfContacts(is);
			}
			if (bodyCollisions) {
				for (int j = i + 1; j < n; j++) {
					Island other = islands.get(j);
					if (!other.failed && !(is.asleep && other.asleep) && is.overlaps(other)) {
						pairContacts(is, other);
					}
				}
			}
		}
		// Contacts with the fixed world are listed, and so solved, after those between bodies: whatever the bodies do
		// to each other, the ground has the last word.
		for (int i = 0; i < n; i++) {
			Island is = islands.get(i);
			if (!is.failed && !is.asleep) {
				worldContacts(is);
			}
		}

		double h = STEP / substeps;
		double invH = 1.0 / h;
		substep = h;
		soften(Math.min(contactHertz, 0.25 * invH), 10.0, h, true);
		soften(Math.min(jointHertz, 0.25 * invH), 2.0, h, false);

		for (int s = 0; s < substeps; s++) {
			for (int i = 0; i < n; i++) {
				Island is = islands.get(i);
				if (is.failed || is.asleep) {
					continue;
				}
				for (Body b : is.bodies) {
					b.refresh();
				}
				for (Joint j : is.joints) {
					if (!j.broken) {
						prepareJoint(j);
					}
				}
			}
			for (int i = 0; i < contactCount; i++) {
				prepareContact(contacts[i], s == 0);
			}

			for (int i = 0; i < n; i++) {
				Island is = islands.get(i);
				if (is.failed || is.asleep) {
					continue;
				}
				for (Body b : is.bodies) {
					integrateVelocity(b, h);
				}
				for (Joint j : is.joints) {
					if (!j.broken) {
						warmStart(j);
					}
				}
			}
			for (int i = 0; i < contactCount; i++) {
				warmStart(contacts[i]);
			}

			solve(true, invH);
			for (int i = 0; i < n; i++) {
				Island is = islands.get(i);
				if (is.failed || is.asleep) {
					continue;
				}
				for (Body b : is.bodies) {
					if (b.im > 0.0) {
						b.x += b.vx * h;
						b.y += b.vy * h;
						b.z += b.vz * h;
						b.applyRotation(b.wx * h, b.wy * h, b.wz * h);
					}
				}
			}
			solve(false, invH);

			for (int i = 0; i < n; i++) {
				Island is = islands.get(i);
				if (is.failed || is.asleep) {
					continue;
				}
				for (Joint j : is.joints) {
					if (j.broken) {
						continue;
					}
					if (j.damping > 0.0) {
						dampJoint(j, h);
					}
					if (j.breakForce > 0.0 && Math.sqrt(j.px * j.px + j.py * j.py + j.pz * j.pz) * invH > j.breakForce) {
						j.broken = true;
					}
				}
			}
		}

		for (int i = 0; i < contactCount; i++) {
			bounce(contacts[i]);
		}
		for (int i = 0; i < n; i++) {
			Island is = islands.get(i);
			if (!is.failed && !is.asleep) {
				for (Body b : is.bodies) {
					b.heldCount = 0;
				}
			}
		}
		for (int i = 0; i < contactCount; i++) {
			remember(contacts[i]);
		}
		for (int i = 0; i < n; i++) {
			Island is = islands.get(i);
			if (!is.failed && !is.asleep) {
				finish(is);
			}
		}
	}

	/** Coefficients that turn a rigid constraint into a stiff spring of the given frequency and damping ratio. */
	private void soften(double hertz, double dampingRatio, double h, boolean contact) {
		double omega = 2.0 * Math.PI * hertz;
		double a1 = 2.0 * dampingRatio + h * omega;
		double a2 = h * omega * a1;
		double a3 = 1.0 / (1.0 + a2);
		if (contact) {
			contactBias = omega / a1;
			contactMass = a2 * a3;
			contactImpulse = a3;
		} else {
			jointBias = omega / a1;
			jointMass = a2 * a3;
			jointImpulse = a3;
		}
	}

	private void solve(boolean useBias, double invH) {
		int n = islands.size();
		for (int i = 0; i < n; i++) {
			Island is = islands.get(i);
			if (is.failed || is.asleep) {
				continue;
			}
			for (Joint j : is.joints) {
				if (!j.broken) {
					solveJoint(j, useBias, invH);
				}
			}
		}
		// Contacts go last so that when a joint and a floor disagree, the floor wins.
		for (int i = 0; i < contactCount; i++) {
			solveContact(contacts[i], useBias, invH);
		}
	}

	private void prepare(Island is) {
		is.minX = is.minY = is.minZ = Double.MAX_VALUE;
		is.maxX = is.maxY = is.maxZ = -Double.MAX_VALUE;
		is.maxSpeed = 0.0;
		for (Body b : is.bodies) {
			b.lastX = b.x;
			b.lastY = b.y;
			b.lastZ = b.z;
			b.lastQx = b.qx;
			b.lastQy = b.qy;
			b.lastQz = b.qz;
			b.lastQw = b.qw;
			b.im = b.invMass;
			b.iix = b.invIx;
			b.iiy = b.invIy;
			b.iiz = b.invIz;
			b.refresh();
			b.updateBounds(STEP);
			is.minX = Math.min(is.minX, b.minX);
			is.minY = Math.min(is.minY, b.minY);
			is.minZ = Math.min(is.minZ, b.minZ);
			is.maxX = Math.max(is.maxX, b.maxX);
			is.maxY = Math.max(is.maxY, b.maxY);
			is.maxZ = Math.max(is.maxZ, b.maxZ);
			is.maxSpeed = Math.max(is.maxSpeed, b.speed());
		}
	}

	// ---- Finding contacts ----

	private void worldContacts(Island is) {
		BoxBuffer boxes = is.statics;
		if (colliders == null) {
			boxes.clear();
			return;
		}
		if (is.boxStamp != worldStamp || is.minX < is.boxMinX || is.minY < is.boxMinY || is.minZ < is.boxMinZ
				|| is.maxX > is.boxMaxX || is.maxY > is.boxMaxY || is.maxZ > is.boxMaxZ) {
			is.boxStamp = worldStamp;
			is.boxMinX = is.minX - BOX_PAD;
			is.boxMinY = is.minY - BOX_PAD;
			is.boxMinZ = is.minZ - BOX_PAD;
			is.boxMaxX = is.maxX + BOX_PAD;
			is.boxMaxY = is.maxY + BOX_PAD;
			is.boxMaxZ = is.maxZ + BOX_PAD;
			boxes.clear();
			colliders.collect(is.boxMinX, is.boxMinY, is.boxMinZ, is.boxMaxX, is.boxMaxY, is.boxMaxZ, boxes);
		}
		double[] d = boxes.data;
		for (Body b : is.bodies) {
			if (b.im == 0.0) {
				continue;
			}
			col.setA(b);
			for (int k = 0, at = 0; k < boxes.count; k++, at += BoxBuffer.STRIDE) {
				if (b.minX > d[at + 3] || b.maxX < d[at] || b.minY > d[at + 4] || b.maxY < d[at + 1]
						|| b.minZ > d[at + 5] || b.maxZ < d[at + 2]) {
					continue;
				}
				col.setB(d[at], d[at + 1], d[at + 2], d[at + 3], d[at + 4], d[at + 5]);
				boolean buried = b.x > d[at] && b.x < d[at + 3] && b.y > d[at + 1] && b.y < d[at + 4] && b.z > d[at + 2] && b.z < d[at + 5]
						&& !covered(boxes, b.x, d[at + 4] + 0.01, b.z);
				boolean touching = buried ? col.collideUpward(b.margin) : col.collide(b.margin);
				if (touching && !buried && col.ny < 0.99 && onTopEdge(d[at + 4]) && !(thin(b) && outerEdge(boxes, d[at + 4]))) {
					// Caught on the top edge of a block it is lying on: that is resting on the block, so it is pushed
					// straight up. A tilted push there acts like a bump at every seam between blocks. Not for a thin part
					// at an outer edge (open air past it): there the side of the block is real, and an enderman's arm
					// dragged over the edge with only an upward push sank into the block's side. Thicker parts keep the
					// upward push, which lets a body come to rest on stairs.
					touching = covered(boxes, b.x, d[at + 4] + 0.01, b.z) ? touching : col.collideUpward(b.margin);
				} else if (touching && internalFace(boxes)) {
					// The face it found is pressed against the next block, inside the ground: a body sliding across the
					// seam between two blocks would catch on it and stop as if it hit a wall. Only the open top counts.
					touching = !covered(boxes, b.x, d[at + 4] + 0.01, b.z) && col.collideUpward(b.margin);
				}
				if (touching) {
					addContacts(b, ground, Math.sqrt(b.friction * d[at + 6]), Math.max(b.restitution, d[at + 7]));
				}
			}
		}
	}

	/** Whether the contacts just found are all within a little of the top of a box. */
	private boolean onTopEdge(double top) {
		for (int k = 0; k < col.count; k++) {
			if (col.pb[k * 3 + 1] < top - TOP_EDGE) {
				return false;
			}
		}
		return true;
	}

	/** A part thinner than this (half its smallest size, in blocks) is thin enough to slip into the side of a block. */
	private static final double THIN = 0.1;

	private static boolean thin(Body b) {
		return Math.min(b.hx, Math.min(b.hy, b.hz)) < THIN;
	}

	/**
	 * Whether the contacts just found are at an outer edge of the top of a fixed box: level with its top, just past
	 * them the way the contact pushes (sideways only), there is open air, not the next block.
	 */
	private boolean outerEdge(BoxBuffer boxes, double top) {
		double length = Math.sqrt(col.nx * col.nx + col.nz * col.nz);
		if (length < 1.0e-6) {
			return false;
		}
		double x = 0, z = 0;
		for (int k = 0; k < col.count; k++) {
			x += col.pb[k * 3];
			z += col.pb[k * 3 + 2];
		}
		x /= col.count;
		z /= col.count;
		return !covered(boxes, x + col.nx / length * 0.05, top - 0.05, z + col.nz / length * 0.05);
	}

	/**
	 * Whether the contacts just found are against a face of the fixed box that another fixed box covers: just past
	 * the face, along the normal, there is more solid block.
	 */
	private boolean internalFace(BoxBuffer boxes) {
		if (col.ny > 0.7) {
			return false;
		}
		double x = 0, y = 0, z = 0;
		for (int k = 0; k < col.count; k++) {
			x += col.pb[k * 3];
			y += col.pb[k * 3 + 1];
			z += col.pb[k * 3 + 2];
		}
		x /= col.count;
		y /= col.count;
		z /= col.count;
		return covered(boxes, x + col.nx * 0.02, y + col.ny * 0.02, z + col.nz * 0.02);
	}

	/** Whether any of the fixed boxes contains the point: the space above a block is not open air. */
	private static boolean covered(BoxBuffer boxes, double x, double y, double z) {
		double[] d = boxes.data;
		for (int k = 0, at = 0; k < boxes.count; k++, at += BoxBuffer.STRIDE) {
			if (x > d[at] && x < d[at + 3] && y > d[at + 1] && y < d[at + 4] && z > d[at + 2] && z < d[at + 5]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the fixed world around a ragdoll is no longer what it last collided with: a block under a sleeping body
	 * was broken, or one was placed into it. The owner calls this now and then for sleeping ragdolls and wakes them.
	 */
	public boolean worldChanged(Island is) {
		if (colliders == null || is.boxStamp == Long.MIN_VALUE) {
			return false;
		}
		probe.clear();
		colliders.collect(is.boxMinX, is.boxMinY, is.boxMinZ, is.boxMaxX, is.boxMaxY, is.boxMaxZ, probe);
		if (probe.count != is.statics.count) {
			return true;
		}
		for (int i = 0, n = probe.count * BoxBuffer.STRIDE; i < n; i++) {
			if (probe.data[i] != is.statics.data[i]) {
				return true;
			}
		}
		return false;
	}

	private void selfContacts(Island is) {
		Body[] bodies = is.bodies;
		for (int i = 0; i < bodies.length; i++) {
			Body a = bodies[i];
			next:
			for (int j = i + 1; j < bodies.length; j++) {
				Body b = bodies[j];
				if ((a.im == 0.0 && b.im == 0.0) || !a.overlaps(b)) {
					continue;
				}
				for (int linked : a.linked) {
					if (linked == j) {
						continue next;
					}
				}
				bodyContact(a, b);
			}
		}
	}

	private void pairContacts(Island x, Island y) {
		for (Body a : x.bodies) {
			if (a.minX > y.maxX || a.maxX < y.minX || a.minY > y.maxY || a.maxY < y.minY || a.minZ > y.maxZ || a.maxZ < y.minZ) {
				continue;
			}
			for (Body b : y.bodies) {
				if ((a.im != 0.0 || b.im != 0.0) && a.overlaps(b)) {
					bodyContact(a, b);
				}
			}
		}
	}

	private void bodyContact(Body a, Body b) {
		// Two parts flying along together are not closing on each other: look ahead by their relative speed only.
		double dvx = a.vx - b.vx, dvy = a.vy - b.vy, dvz = a.vz - b.vz;
		double closing = Math.sqrt(dvx * dvx + dvy * dvy + dvz * dvz) + a.spinSpeed() + b.spinSpeed();
		col.setA(a);
		col.setB(b);
		if (col.collide(CONTACT_MARGIN + STEP * closing)) {
			addContacts(a, b, Math.sqrt(a.friction * b.friction), Math.max(a.restitution, b.restitution));
		}
	}

	private void addContacts(Body a, Body b, double friction, double restitution) {
		double nx = col.nx, ny = col.ny, nz = col.nz;
		// Two friction directions square to the normal.
		double ux, uy, uz;
		if (Math.abs(ny) < 0.9) {
			ux = -nz;
			uy = 0.0;
			uz = nx;
		} else {
			ux = 0.0;
			uy = nz;
			uz = -ny;
		}
		double ul = 1.0 / Math.sqrt(ux * ux + uy * uy + uz * uz);
		ux *= ul;
		uy *= ul;
		uz *= ul;
		double vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
		for (int k = 0; k < col.count; k++) {
			if (contactCount == contacts.length) {
				contacts = java.util.Arrays.copyOf(contacts, contacts.length * 2);
			}
			Contact c = contacts[contactCount];
			if (c == null) {
				c = contacts[contactCount] = new Contact();
			}
			contactCount++;
			c.a = a;
			c.b = b;
			c.friction = friction;
			c.restitution = restitution;
			c.maxNormal = 0.0;
			c.n.dx = nx;
			c.n.dy = ny;
			c.n.dz = nz;
			c.t1.dx = ux;
			c.t1.dy = uy;
			c.t1.dz = uz;
			c.t2.dx = vx;
			c.t2.dy = vy;
			c.t2.dz = vz;
			c.n.acc = c.t1.acc = c.t2.acc = 0.0;
			t.set(col.pa[k * 3] - a.x, col.pa[k * 3 + 1] - a.y, col.pa[k * 3 + 2] - a.z).rotate(-a.qx, -a.qy, -a.qz, a.qw);
			c.rax = t.x;
			c.ray = t.y;
			c.raz = t.z;
			// A resting body starts each step already carrying the impulses that held it up in the last one. Starting
			// from nothing instead means re-solving its weight from scratch every step, always corner by corner in the
			// same order, and that small repeated lopsidedness makes a resting body creep.
			double[] held = a.held;
			double nearest = SAME_CONTACT * SAME_CONTACT;
			for (int m = 0, at = 0; m < a.heldCount; m++, at += 6) {
				if (a.heldWith[m] != b) {
					continue;
				}
				double ex = held[at] - t.x, ey = held[at + 1] - t.y, ez = held[at + 2] - t.z;
				double d2 = ex * ex + ey * ey + ez * ez;
				if (d2 < nearest) {
					nearest = d2;
					c.n.acc = held[at + 3];
					c.t1.acc = held[at + 4];
					c.t2.acc = held[at + 5];
				}
			}
			t.set(col.pb[k * 3] - b.x, col.pb[k * 3 + 1] - b.y, col.pb[k * 3 + 2] - b.z).rotate(-b.qx, -b.qy, -b.qz, b.qw);
			c.rbx = t.x;
			c.rby = t.y;
			c.rbz = t.z;
		}
	}

	// ---- Contacts ----

	private static void setRow(Row r, Body a, Body b, double rax, double ray, double raz, double rbx, double rby, double rbz) {
		double dx = r.dx, dy = r.dy, dz = r.dz;
		double cax = ray * dz - raz * dy, cay = raz * dx - rax * dz, caz = rax * dy - ray * dx;
		double cbx = rby * dz - rbz * dy, cby = rbz * dx - rbx * dz, cbz = rbx * dy - rby * dx;
		r.cax = cax;
		r.cay = cay;
		r.caz = caz;
		r.cbx = cbx;
		r.cby = cby;
		r.cbz = cbz;
		r.uax = a.j00 * cax + a.j01 * cay + a.j02 * caz;
		r.uay = a.j01 * cax + a.j11 * cay + a.j12 * caz;
		r.uaz = a.j02 * cax + a.j12 * cay + a.j22 * caz;
		r.ubx = b.j00 * cbx + b.j01 * cby + b.j02 * cbz;
		r.uby = b.j01 * cbx + b.j11 * cby + b.j12 * cbz;
		r.ubz = b.j02 * cbx + b.j12 * cby + b.j22 * cbz;
		double k = a.im + b.im + cax * r.uax + cay * r.uay + caz * r.uaz + cbx * r.ubx + cby * r.uby + cbz * r.ubz;
		r.mass = k > 0.0 ? 1.0 / k : 0.0;
	}

	/** How fast A's point is moving away from B's along the row's direction. */
	private static double speed(Row r, Body a, Body b) {
		return r.dx * (a.vx - b.vx) + r.dy * (a.vy - b.vy) + r.dz * (a.vz - b.vz)
				+ r.cax * a.wx + r.cay * a.wy + r.caz * a.wz - r.cbx * b.wx - r.cby * b.wy - r.cbz * b.wz;
	}

	private static void push(Row r, Body a, Body b, double p) {
		a.vx += r.dx * p * a.im;
		a.vy += r.dy * p * a.im;
		a.vz += r.dz * p * a.im;
		a.wx += r.uax * p;
		a.wy += r.uay * p;
		a.wz += r.uaz * p;
		b.vx -= r.dx * p * b.im;
		b.vy -= r.dy * p * b.im;
		b.vz -= r.dz * p * b.im;
		b.wx -= r.ubx * p;
		b.wy -= r.uby * p;
		b.wz -= r.ubz * p;
	}

	private void prepareContact(Contact c, boolean first) {
		Body a = c.a, b = c.b;
		c.wax = a.r00 * c.rax + a.r01 * c.ray + a.r02 * c.raz;
		c.way = a.r10 * c.rax + a.r11 * c.ray + a.r12 * c.raz;
		c.waz = a.r20 * c.rax + a.r21 * c.ray + a.r22 * c.raz;
		c.wbx = b.r00 * c.rbx + b.r01 * c.rby + b.r02 * c.rbz;
		c.wby = b.r10 * c.rbx + b.r11 * c.rby + b.r12 * c.rbz;
		c.wbz = b.r20 * c.rbx + b.r21 * c.rby + b.r22 * c.rbz;
		setRow(c.n, a, b, c.wax, c.way, c.waz, c.wbx, c.wby, c.wbz);
		setRow(c.t1, a, b, c.wax, c.way, c.waz, c.wbx, c.wby, c.wbz);
		setRow(c.t2, a, b, c.wax, c.way, c.waz, c.wbx, c.wby, c.wbz);
		if (first) {
			c.approach = speed(c.n, a, b);
		}
	}

	private static void warmStart(Contact c) {
		Body a = c.a, b = c.b;
		push(c.n, a, b, c.n.acc);
		push(c.t1, a, b, c.t1.acc);
		push(c.t2, a, b, c.t2.acc);
	}

	private void solveContact(Contact c, boolean useBias, double invH) {
		Body a = c.a, b = c.b;
		// Friction first, with the grip the normal has built so far; the normal goes last because not sinking in
		// matters more than not sliding.
		double grip = c.friction * c.n.acc;
		Row r;
		double p, next;
		// A contact that is not pressing (most of the ones found ahead of a moving body) has no grip to solve.
		if (grip > 0.0 || c.t1.acc != 0.0 || c.t2.acc != 0.0) {
			r = c.t1;
			p = -r.mass * speed(r, a, b);
			next = Math.max(-grip, Math.min(grip, r.acc + p));
			push(r, a, b, next - r.acc);
			r.acc = next;
			r = c.t2;
			p = -r.mass * speed(r, a, b);
			next = Math.max(-grip, Math.min(grip, r.acc + p));
			push(r, a, b, next - r.acc);
			r.acc = next;
		}

		r = c.n;
		double gap = (a.x + c.wax - b.x - c.wbx) * r.dx + (a.y + c.way - b.y - c.wby) * r.dy + (a.z + c.waz - b.z - c.wbz) * r.dz;
		double bias = 0.0, massScale = 1.0, impulseScale = 0.0;
		if (gap > 0.0) {
			// Not touching yet: allow exactly the approach that closes the gap this substep, and no more.
			bias = gap * invH;
		} else if (useBias) {
			bias = Math.max(contactBias * Math.min(gap + slop, 0.0), -maxPushOut);
			massScale = contactMass;
			impulseScale = contactImpulse;
		}
		p = -r.mass * massScale * (speed(r, a, b) + bias) - impulseScale * r.acc;
		next = Math.max(r.acc + p, 0.0);
		push(r, a, b, next - r.acc);
		r.acc = next;
		if (next > c.maxNormal) {
			c.maxNormal = next;
		}
	}

	/** Bounce is added once, after the solve, from the speed the bodies met at. */
	private void bounce(Contact c) {
		if (c.restitution <= 0.0 || c.maxNormal <= 0.0 || c.approach > -1.0) {
			return;
		}
		Row r = c.n;
		double p = -r.mass * (speed(r, c.a, c.b) + c.restitution * c.approach);
		double next = Math.max(r.acc + p, 0.0);
		push(r, c.a, c.b, next - r.acc);
		r.acc = next;
	}

	private static void remember(Contact c) {
		Body a = c.a;
		if (c.n.acc <= 0.0 || a.island == null || a.island.asleep) {
			return;
		}
		if (a.heldCount == a.heldWith.length) {
			a.heldWith = java.util.Arrays.copyOf(a.heldWith, a.heldCount * 2);
			a.held = java.util.Arrays.copyOf(a.held, a.heldCount * 12);
		}
		int at = a.heldCount * 6;
		a.heldWith[a.heldCount++] = c.b;
		a.held[at] = c.rax;
		a.held[at + 1] = c.ray;
		a.held[at + 2] = c.raz;
		a.held[at + 3] = c.n.acc;
		a.held[at + 4] = c.t1.acc;
		a.held[at + 5] = c.t2.acc;
	}

	// ---- Joints ----

	private void prepareJoint(Joint j) {
		Body a = j.a, b = j.b;
		double rax = a.r00 * j.ax + a.r01 * j.ay + a.r02 * j.az;
		double ray = a.r10 * j.ax + a.r11 * j.ay + a.r12 * j.az;
		double raz = a.r20 * j.ax + a.r21 * j.ay + a.r22 * j.az;
		double rbx = b.r00 * j.bx + b.r01 * j.by + b.r02 * j.bz;
		double rby = b.r10 * j.bx + b.r11 * j.by + b.r12 * j.bz;
		double rbz = b.r20 * j.bx + b.r21 * j.by + b.r22 * j.bz;
		j.wax = rax;
		j.way = ray;
		j.waz = raz;
		j.wbx = rbx;
		j.wby = rby;
		j.wbz = rbz;
		// The pin: how the two anchor points respond, together, to an impulse in any direction.
		k00 = k11 = k22 = a.im + b.im;
		k01 = k02 = k12 = 0.0;
		addPinMatrix(a, rax, ray, raz);
		addPinMatrix(b, rbx, rby, rbz);
		double det = k00 * (k11 * k22 - k12 * k12) - k01 * (k01 * k22 - k12 * k02) + k02 * (k01 * k12 - k11 * k02);
		double inv = det != 0.0 ? 1.0 / det : 0.0;
		j.m00 = (k11 * k22 - k12 * k12) * inv;
		j.m01 = (k02 * k12 - k01 * k22) * inv;
		j.m02 = (k01 * k12 - k02 * k11) * inv;
		j.m11 = (k00 * k22 - k02 * k02) * inv;
		j.m12 = (k01 * k02 - k00 * k12) * inv;
		j.m22 = (k00 * k11 - k01 * k01) * inv;
		if (j.motorTorque > 0.0) {
			prepareMuscle(j, a, b);
		}

		double tax = a.r00 * j.tax + a.r01 * j.tay + a.r02 * j.taz;
		double tay = a.r10 * j.tax + a.r11 * j.tay + a.r12 * j.taz;
		double taz = a.r20 * j.tax + a.r21 * j.tay + a.r22 * j.taz;
		double tbx = b.r00 * j.tbx + b.r01 * j.tby + b.r02 * j.tbz;
		double tby = b.r10 * j.tbx + b.r11 * j.tby + b.r12 * j.tbz;
		double tbz = b.r20 * j.tbx + b.r21 * j.tby + b.r22 * j.tbz;
		double sax = a.r00 * j.sax + a.r01 * j.say + a.r02 * j.saz;
		double say = a.r10 * j.sax + a.r11 * j.say + a.r12 * j.saz;
		double saz = a.r20 * j.sax + a.r21 * j.say + a.r22 * j.saz;
		double sbx = b.r00 * j.sbx + b.r01 * j.sby + b.r02 * j.sbz;
		double sby = b.r10 * j.sbx + b.r11 * j.sby + b.r12 * j.sbz;
		double sbz = b.r20 * j.sbx + b.r21 * j.sby + b.r22 * j.sbz;
		// The turn that carries A's axis onto B's: its direction is the swing axis, its size (the sine of) the angle.
		double cx = tay * tbz - taz * tby, cy = taz * tbx - tax * tbz, cz = tax * tby - tay * tbx;

		if (j.type == Joint.Type.HINGE) {
			// Two rows hold the halves of the hinge on one axis.
			double ox = tay * saz - taz * say, oy = taz * sax - tax * saz, oz = tax * say - tay * sax;
			setAngle(j.axis1, a, b, sax, say, saz);
			j.axis1.c = cx * sax + cy * say + cz * saz;
			setAngle(j.axis2, a, b, ox, oy, oz);
			j.axis2.c = cx * ox + cy * oy + cz * oz;
			if (j.hi < Math.PI || j.lo > -Math.PI) {
				double angle = angleBetween(sax, say, saz, sbx, sby, sbz, tax, tay, taz);
				setLimit(j.limit1, a, b, tax, tay, taz, angle, j.lo, j.hi);
			} else {
				j.limit1.active = false;
			}
			j.limit2.active = false;
			return;
		}

		j.limit1.active = false;
		if (j.swing1Hi < Math.PI || j.swing1Lo > -Math.PI || j.swing2Hi < Math.PI || j.swing2Lo > -Math.PI) {
			double sin = Math.sqrt(cx * cx + cy * cy + cz * cz);
			if (sin > 1.0e-9) {
				double swing = Math.atan2(sin, tax * tbx + tay * tby + taz * tbz);
				double nx = cx / sin, ny = cy / sin, nz = cz / sin;
				double c1 = nx * sax + ny * say + nz * saz;
				// The third axis: twist axis × reference axis.
				double c2 = nx * (tay * saz - taz * say) + ny * (taz * sax - tax * saz) + nz * (tax * say - tay * sax);
				double l1 = Math.max(1.0e-3, c1 >= 0.0 ? j.swing1Hi : -j.swing1Lo);
				double l2 = Math.max(1.0e-3, c2 >= 0.0 ? j.swing2Hi : -j.swing2Lo);
				double limit = 1.0 / Math.sqrt(c1 * c1 / (l1 * l1) + c2 * c2 / (l2 * l2));
				setLimit(j.limit1, a, b, nx, ny, nz, swing, -Math.PI * 4.0, limit);
			}
		}
		if (!j.limit1.active) {
			j.limit1.acc = 0.0;
		}
		j.limit2.active = false;
		if (j.twistHi < Math.PI || j.twistLo > -Math.PI) {
			// Measure twist about the direction halfway between the two axes, where swing does not show up.
			double mx = tax + tbx, my = tay + tby, mz = taz + tbz;
			double len = Math.sqrt(mx * mx + my * my + mz * mz);
			if (len > 1.0e-6) {
				mx /= len;
				my /= len;
				mz /= len;
				double d = sax * mx + say * my + saz * mz;
				sax -= d * mx;
				say -= d * my;
				saz -= d * mz;
				d = sbx * mx + sby * my + sbz * mz;
				sbx -= d * mx;
				sby -= d * my;
				sbz -= d * mz;
				double angle = angleBetween(sax, say, saz, sbx, sby, sbz, mx, my, mz);
				setLimit(j.limit2, a, b, mx, my, mz, angle, j.twistLo, j.twistHi);
			}
		}
		if (!j.limit2.active) {
			j.limit2.acc = 0.0;
		}
	}

	/** Works out how far, and about which way, B has to turn to reach the muscle's wanted orientation. */
	private void prepareMuscle(Joint j, Body a, Body b) {
		// The wanted orientation of B in the world, then the turn from where B is to there.
		double tx = a.qw * j.motorX + a.qx * j.motorW + a.qy * j.motorZ - a.qz * j.motorY;
		double ty = a.qw * j.motorY + a.qy * j.motorW + a.qz * j.motorX - a.qx * j.motorZ;
		double tz = a.qw * j.motorZ + a.qz * j.motorW + a.qx * j.motorY - a.qy * j.motorX;
		double tw = a.qw * j.motorW - a.qx * j.motorX - a.qy * j.motorY - a.qz * j.motorZ;
		double ex = -tw * b.qx + tx * b.qw - ty * b.qz + tz * b.qy;
		double ey = -tw * b.qy + ty * b.qw - tz * b.qx + tx * b.qz;
		double ez = -tw * b.qz + tz * b.qw - tx * b.qy + ty * b.qx;
		double ew = tw * b.qw + tx * b.qx + ty * b.qy + tz * b.qz;
		if (ew < 0.0) {
			ex = -ex;
			ey = -ey;
			ez = -ez;
			ew = -ew;
		}
		double sin = Math.sqrt(ex * ex + ey * ey + ez * ez);
		// As a turn vector: its direction is the axis, its length the angle. No more than a radian at a time.
		double k = sin > 1.0e-9 ? Math.min(1.0, 2.0 * Math.atan2(sin, ew)) / sin : 0.0;
		setAngle(j.motor1, a, b, 1.0, 0.0, 0.0);
		setAngle(j.motor2, a, b, 0.0, 1.0, 0.0);
		setAngle(j.motor3, a, b, 0.0, 0.0, 1.0);
		j.motor1.c = -ex * k;
		j.motor2.c = -ey * k;
		j.motor3.c = -ez * k;
	}

	private void solveMuscle(Joint j, Body a, Body b) {
		double h = substep;
		double omega = 2.0 * Math.PI * Math.min(j.motorHertz, 0.25 / h);
		double a1 = 2.0 * j.motorDamping + h * omega;
		double a2 = h * omega * a1;
		double a3 = 1.0 / (1.0 + a2);
		double biasRate = omega / a1, massScale = a2 * a3, impulseScale = a3;
		springRow(j.motor1, a, b, biasRate, massScale, impulseScale);
		springRow(j.motor2, a, b, biasRate, massScale, impulseScale);
		springRow(j.motor3, a, b, biasRate, massScale, impulseScale);
		// The ceiling on the muscle's strength: the three rows together may not exceed its torque.
		double max = j.motorTorque * h;
		double total = Math.sqrt(j.motor1.acc * j.motor1.acc + j.motor2.acc * j.motor2.acc + j.motor3.acc * j.motor3.acc);
		if (total > max) {
			double keep = max / total;
			turn(j.motor1, a, b, j.motor1.acc * (keep - 1.0));
			turn(j.motor2, a, b, j.motor2.acc * (keep - 1.0));
			turn(j.motor3, a, b, j.motor3.acc * (keep - 1.0));
			j.motor1.acc *= keep;
			j.motor2.acc *= keep;
			j.motor3.acc *= keep;
		}
	}

	private static void springRow(Joint.AngleRow r, Body a, Body b, double biasRate, double massScale, double impulseScale) {
		double rel = (b.wx - a.wx) * r.nx + (b.wy - a.wy) * r.ny + (b.wz - a.wz) * r.nz;
		double p = -r.mass * massScale * (rel + r.c * biasRate) - impulseScale * r.acc;
		r.acc += p;
		turn(r, a, b, p);
	}

	/** Adds one body's share of the pin matrix: how an impulse at offset r moves that point, linear part aside. */
	private void addPinMatrix(Body b, double rx, double ry, double rz) {
		if (b.im == 0.0) {
			return;
		}
		// For each unit direction e: (J (r × e)) × r.
		double vx = 0.0, vy = rz, vz = -ry;
		double wx = b.j00 * vx + b.j01 * vy + b.j02 * vz, wy = b.j01 * vx + b.j11 * vy + b.j12 * vz, wz = b.j02 * vx + b.j12 * vy + b.j22 * vz;
		k00 += wy * rz - wz * ry;
		k01 += wz * rx - wx * rz;
		k02 += wx * ry - wy * rx;
		vx = -rz;
		vy = 0.0;
		vz = rx;
		wx = b.j00 * vx + b.j01 * vy + b.j02 * vz;
		wy = b.j01 * vx + b.j11 * vy + b.j12 * vz;
		wz = b.j02 * vx + b.j12 * vy + b.j22 * vz;
		k11 += wz * rx - wx * rz;
		k12 += wx * ry - wy * rx;
		vx = ry;
		vy = -rx;
		vz = 0.0;
		wx = b.j00 * vx + b.j01 * vy + b.j02 * vz;
		wy = b.j01 * vx + b.j11 * vy + b.j12 * vz;
		wz = b.j02 * vx + b.j12 * vy + b.j22 * vz;
		k22 += wx * ry - wy * rx;
	}

	/** The angle from u to v about n. */
	private static double angleBetween(double ux, double uy, double uz, double vx, double vy, double vz, double nx, double ny, double nz) {
		double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
		return Math.atan2(cx * nx + cy * ny + cz * nz, ux * vx + uy * vy + uz * vz);
	}

	private static void setAngle(Joint.AngleRow r, Body a, Body b, double nx, double ny, double nz) {
		r.nx = nx;
		r.ny = ny;
		r.nz = nz;
		r.uax = a.j00 * nx + a.j01 * ny + a.j02 * nz;
		r.uay = a.j01 * nx + a.j11 * ny + a.j12 * nz;
		r.uaz = a.j02 * nx + a.j12 * ny + a.j22 * nz;
		r.ubx = b.j00 * nx + b.j01 * ny + b.j02 * nz;
		r.uby = b.j01 * nx + b.j11 * ny + b.j12 * nz;
		r.ubz = b.j02 * nx + b.j12 * ny + b.j22 * nz;
		double k = nx * (r.uax + r.ubx) + ny * (r.uay + r.uby) + nz * (r.uaz + r.ubz);
		r.mass = k > 0.0 ? 1.0 / k : 0.0;
	}

	/** Readies a limit row for whichever end of the range the angle is nearer to. */
	private static void setLimit(Joint.AngleRow r, Body a, Body b, double nx, double ny, double nz, double angle, double lo, double hi) {
		double room;
		int dir;
		if (hi - angle < angle - lo) {
			room = hi - angle;
			dir = 1;
		} else {
			room = angle - lo;
			dir = -1;
		}
		if (room > LIMIT_RANGE) {
			r.active = false;
			r.acc = 0.0;
			return;
		}
		if (dir != r.dir) {
			r.acc = 0.0;
		}
		r.active = true;
		r.dir = dir;
		r.c = room;
		setAngle(r, a, b, nx, ny, nz);
	}

	private static void warmStart(Joint j) {
		Body a = j.a, b = j.b;
		applyPin(j, a, b, j.px, j.py, j.pz);
		if (j.motorTorque > 0.0) {
			turn(j.motor1, a, b, j.motor1.acc);
			turn(j.motor2, a, b, j.motor2.acc);
			turn(j.motor3, a, b, j.motor3.acc);
		}
		if (j.type == Joint.Type.HINGE) {
			turn(j.axis1, a, b, j.axis1.acc);
			turn(j.axis2, a, b, j.axis2.acc);
		}
		if (j.limit1.active) {
			turn(j.limit1, a, b, -j.limit1.dir * j.limit1.acc);
		}
		if (j.limit2.active) {
			turn(j.limit2, a, b, -j.limit2.dir * j.limit2.acc);
		}
	}

	/** An impulse on A's anchor point, and the opposite one on B's. */
	private static void applyPin(Joint j, Body a, Body b, double px, double py, double pz) {
		a.vx += px * a.im;
		a.vy += py * a.im;
		a.vz += pz * a.im;
		double cx = j.way * pz - j.waz * py, cy = j.waz * px - j.wax * pz, cz = j.wax * py - j.way * px;
		a.wx += a.j00 * cx + a.j01 * cy + a.j02 * cz;
		a.wy += a.j01 * cx + a.j11 * cy + a.j12 * cz;
		a.wz += a.j02 * cx + a.j12 * cy + a.j22 * cz;
		b.vx -= px * b.im;
		b.vy -= py * b.im;
		b.vz -= pz * b.im;
		cx = j.wby * pz - j.wbz * py;
		cy = j.wbz * px - j.wbx * pz;
		cz = j.wbx * py - j.wby * px;
		b.wx -= b.j00 * cx + b.j01 * cy + b.j02 * cz;
		b.wy -= b.j01 * cx + b.j11 * cy + b.j12 * cz;
		b.wz -= b.j02 * cx + b.j12 * cy + b.j22 * cz;
	}

	/** A twisting impulse about the row's axis: B turns one way, A the other. */
	private static void turn(Joint.AngleRow r, Body a, Body b, double impulse) {
		a.wx -= r.uax * impulse;
		a.wy -= r.uay * impulse;
		a.wz -= r.uaz * impulse;
		b.wx += r.ubx * impulse;
		b.wy += r.uby * impulse;
		b.wz += r.ubz * impulse;
	}

	private void solveJoint(Joint j, boolean useBias, double invH) {
		Body a = j.a, b = j.b;
		if (a.im == 0.0 && b.im == 0.0) {
			return;
		}
		double massScale = useBias ? jointMass : 1.0, impulseScale = useBias ? jointImpulse : 0.0;
		// Muscles first: limits and the pin are solved after them, so a muscle can never win against either.
		if (j.motorTorque > 0.0) {
			solveMuscle(j, a, b);
		}
		if (j.type == Joint.Type.HINGE) {
			solveAxis(j.axis1, a, b, useBias, massScale, impulseScale);
			solveAxis(j.axis2, a, b, useBias, massScale, impulseScale);
		}
		if (j.limit1.active) {
			solveLimit(j.limit1, a, b, useBias, invH);
		}
		if (j.limit2.active) {
			solveLimit(j.limit2, a, b, useBias, invH);
		}

		// The pin goes last: holding the body together matters more than its angles.
		double vx = a.vx + a.wy * j.waz - a.wz * j.way - b.vx - b.wy * j.wbz + b.wz * j.wby;
		double vy = a.vy + a.wz * j.wax - a.wx * j.waz - b.vy - b.wz * j.wbx + b.wx * j.wbz;
		double vz = a.vz + a.wx * j.way - a.wy * j.wax - b.vz - b.wx * j.wby + b.wy * j.wbx;
		if (useBias) {
			double bx = (a.x + j.wax - b.x - j.wbx) * jointBias;
			double by = (a.y + j.way - b.y - j.wby) * jointBias;
			double bz = (a.z + j.waz - b.z - j.wbz) * jointBias;
			double pull = bx * bx + by * by + bz * bz;
			if (pull > maxJointPull * maxJointPull) {
				double k = maxJointPull / Math.sqrt(pull);
				bx *= k;
				by *= k;
				bz *= k;
			}
			vx += bx;
			vy += by;
			vz += bz;
		}
		double px = -(j.m00 * vx + j.m01 * vy + j.m02 * vz) * massScale - impulseScale * j.px;
		double py = -(j.m01 * vx + j.m11 * vy + j.m12 * vz) * massScale - impulseScale * j.py;
		double pz = -(j.m02 * vx + j.m12 * vy + j.m22 * vz) * massScale - impulseScale * j.pz;
		j.px += px;
		j.py += py;
		j.pz += pz;
		applyPin(j, a, b, px, py, pz);
	}

	private void solveAxis(Joint.AngleRow r, Body a, Body b, boolean useBias, double massScale, double impulseScale) {
		double rel = (b.wx - a.wx) * r.nx + (b.wy - a.wy) * r.ny + (b.wz - a.wz) * r.nz;
		double bias = useBias ? r.c * jointBias : 0.0;
		double p = -r.mass * massScale * (rel + bias) - impulseScale * r.acc;
		r.acc += p;
		turn(r, a, b, p);
	}

	/**
	 * A limit works like a contact: it only ever pushes back into the range, lets a part swing up to the stop but
	 * not through it, and eases back a part that is already past.
	 */
	private void solveLimit(Joint.AngleRow r, Body a, Body b, boolean useBias, double invH) {
		double closing = -r.dir * ((b.wx - a.wx) * r.nx + (b.wy - a.wy) * r.ny + (b.wz - a.wz) * r.nz);
		double bias = 0.0, massScale = 1.0, impulseScale = 0.0;
		if (r.c > 0.0) {
			bias = r.c * invH;
		} else if (useBias) {
			bias = Math.max(r.c * jointBias, -maxLimitSpeed);
			massScale = jointMass;
			impulseScale = jointImpulse;
		}
		double p = -r.mass * massScale * (closing + bias) - impulseScale * r.acc;
		double next = Math.max(r.acc + p, 0.0);
		turn(r, a, b, -r.dir * (next - r.acc));
		r.acc = next;
	}

	/** Takes away a share of the two parts' relative spin: the stiffness of dead flesh. */
	private void dampJoint(Joint j, double h) {
		Body a = j.a, b = j.b;
		double k = Math.min(1.0, j.damping * h);
		double dx = (b.wx - a.wx) * k, dy = (b.wy - a.wy) * k, dz = (b.wz - a.wz) * k;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0e-10) {
			return;
		}
		double nx = dx / len, ny = dy / len, nz = dz / len;
		double uax = a.j00 * nx + a.j01 * ny + a.j02 * nz, uay = a.j01 * nx + a.j11 * ny + a.j12 * nz, uaz = a.j02 * nx + a.j12 * ny + a.j22 * nz;
		double ubx = b.j00 * nx + b.j01 * ny + b.j02 * nz, uby = b.j01 * nx + b.j11 * ny + b.j12 * nz, ubz = b.j02 * nx + b.j12 * ny + b.j22 * nz;
		double w = nx * (uax + ubx) + ny * (uay + uby) + nz * (uaz + ubz);
		if (w <= 0.0) {
			return;
		}
		double p = len / w;
		a.wx += uax * p;
		a.wy += uay * p;
		a.wz += uaz * p;
		b.wx -= ubx * p;
		b.wy -= uby * p;
		b.wz -= ubz * p;
	}

	// ---- Bodies ----

	private void integrateVelocity(Body b, double h) {
		if (b.im == 0.0) {
			return;
		}
		b.vy += (gravity * b.gravityScale + b.lift) * h;
		if (b.fluidDrag > 0.0) {
			// The fluid pulls the body towards its own motion, not towards stillness: that is what a current is.
			double k = 1.0 / (1.0 + h * b.fluidDrag);
			b.vx = b.flowX + (b.vx - b.flowX) * k;
			b.vy = b.flowY + (b.vy - b.flowY) * k;
			b.vz = b.flowZ + (b.vz - b.flowZ) * k;
		}
		if (b.fluidSpinDrag > 0.0) {
			double k = 1.0 / (1.0 + h * b.fluidSpinDrag);
			b.wx *= k;
			b.wy *= k;
			b.wz *= k;
		}
		if (b.linearDrag > 0.0) {
			double k = 1.0 / (1.0 + h * b.linearDrag);
			b.vx *= k;
			b.vy *= k;
			b.vz *= k;
		}
		if (b.angularDrag > 0.0) {
			double k = 1.0 / (1.0 + h * b.angularDrag);
			b.wx *= k;
			b.wy *= k;
			b.wz *= k;
		}
	}

	private void finish(Island is) {
		boolean quiet = true;
		double maxLin2 = maxLinearSpeed * maxLinearSpeed, maxAng2 = maxAngularSpeed * maxAngularSpeed;
		for (Body b : is.bodies) {
			double sum = b.x + b.y + b.z + b.qx + b.qy + b.qz + b.qw + b.vx + b.vy + b.vz + b.wx + b.wy + b.wz;
			if (Double.isNaN(sum) || Double.isInfinite(sum)) {
				is.failed = true;
				return;
			}
			if (b.im == 0.0) {
				continue;
			}
			double lin2 = b.vx * b.vx + b.vy * b.vy + b.vz * b.vz;
			if (lin2 > maxLin2) {
				double k = maxLinearSpeed / Math.sqrt(lin2);
				b.vx *= k;
				b.vy *= k;
				b.vz *= k;
			}
			double ang2 = b.wx * b.wx + b.wy * b.wy + b.wz * b.wz;
			if (ang2 > maxAng2) {
				double k = maxAngularSpeed / Math.sqrt(ang2);
				b.wx *= k;
				b.wy *= k;
				b.wz *= k;
			}
			double mx = b.x - b.sx, my = b.y - b.sy, mz = b.z - b.sz;
			// Twice the sine of half the angle turned since the reference pose: near enough the angle itself.
			double dot = b.qx * b.sqx + b.qy * b.sqy + b.qz * b.sqz + b.qw * b.sqw;
			double turned = 2.0 * Math.sqrt(Math.max(0.0, 1.0 - dot * dot));
			if (mx * mx + my * my + mz * mz > sleepDistance * sleepDistance || turned > sleepAngle) {
				quiet = false;
			}
		}
		if (!quiet) {
			is.quiet = 0.0;
			for (Body b : is.bodies) {
				b.sx = b.x;
				b.sy = b.y;
				b.sz = b.z;
				b.sqx = b.qx;
				b.sqy = b.qy;
				b.sqz = b.qz;
				b.sqw = b.qw;
			}
			return;
		}
		is.quiet += STEP;
		if (is.quiet >= sleepDelay) {
			is.asleep = true;
			for (Body b : is.bodies) {
				b.vx = b.vy = b.vz = b.wx = b.wy = b.wz = 0.0;
				b.im = b.iix = b.iiy = b.iiz = 0.0;
				b.lastX = b.x;
				b.lastY = b.y;
				b.lastZ = b.z;
				b.lastQx = b.qx;
				b.lastQy = b.qy;
				b.lastQz = b.qz;
				b.lastQw = b.qw;
				b.refresh();
				b.updateBounds(0.0);
			}
		}
	}
}
