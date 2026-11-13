package com.frostfleee.ragdolldeaths.physics;

/**
 * Pins a point of body {@code b} (the child: a limb) to a point of body {@code a} (the parent) and limits how far the
 * child can turn from its rest direction.
 *
 * <p>A hinge turns about one axis only, between {@link #lo} and {@link #hi} (a knee, an elbow, a quadruped's leg).
 * A ball swings away from the rest direction and twists about it (a neck, a shoulder, a hip). Its swing limit is an
 * ellipse that can differ on each side: {@code swing1} is the swing about the reference axis given at creation,
 * {@code swing2} the swing about the third axis (twist axis × reference axis). Angles are radians.
 */
public final class Joint {
	public enum Type {
		BALL, HINGE
	}

	public final Type type;
	public final Body a;
	public final Body b;

	// All in each body's own space: the anchor, the twist (or hinge) axis, and a reference axis square to it.
	double ax, ay, az, bx, by, bz;
	double tax, tay, taz, tbx, tby, tbz;
	double sax, say, saz, sbx, sby, sbz;

	public double lo = -Math.PI, hi = Math.PI;
	public double swing1Lo = -Math.PI, swing1Hi = Math.PI;
	public double swing2Lo = -Math.PI, swing2Hi = Math.PI;
	public double twistLo = -Math.PI, twistHi = Math.PI;
	/** How fast the joint loses relative spin, per second: the stiffness of dead flesh. */
	public double damping;
	/** The joint snaps when the pull on it passes this force (kg·blocks/s²); zero never breaks. */
	public double breakForce;
	public boolean broken;

	/** One angle the solver holds or limits: its axis, how the two bodies respond, and the impulse built up. */
	static final class AngleRow {
		double nx, ny, nz;
		double uax, uay, uaz, ubx, uby, ubz;
		double mass;
		double acc;
		/** For the hinge's axis rows the misalignment; for a limit the room left before the stop (negative past it). */
		double c;
		int dir;
		boolean active;
	}

	// Solver state: the anchors as world offsets, the pin's inverse matrix and its built-up impulse, and the rows.
	double wax, way, waz, wbx, wby, wbz;
	double m00, m01, m02, m11, m12, m22;
	double px, py, pz;
	final AngleRow axis1 = new AngleRow(), axis2 = new AngleRow(), limit1 = new AngleRow(), limit2 = new AngleRow();

	// The muscle: a spring that turns B towards a wanted orientation relative to A, with a ceiling on its torque.
	double motorX, motorY, motorZ, motorW = 1.0;
	double motorHertz, motorDamping, motorTorque;
	final AngleRow motor1 = new AngleRow(), motor2 = new AngleRow(), motor3 = new AngleRow();

	private Joint(Type type, Body a, Body b) {
		this.type = type;
		this.a = a;
		this.b = b;
	}

	/** A ball joint at a world anchor, with the limb's rest direction and a reference axis, both in world space. */
	public static Joint ball(Body a, Body b, double anchorX, double anchorY, double anchorZ,
			double twistX, double twistY, double twistZ, double refX, double refY, double refZ) {
		return new Joint(Type.BALL, a, b).frame(anchorX, anchorY, anchorZ, twistX, twistY, twistZ, refX, refY, refZ);
	}

	/**
	 * A ball joint made while the child is already swung away from its rest direction (a mob dies mid-stride, or
	 * with its arms raised). {@code restTwist} and {@code restRef} are where the limb's axes would be at rest;
	 * {@code twist} and {@code ref} are where they are now. All are world directions.
	 */
	public static Joint ball(Body a, Body b, double anchorX, double anchorY, double anchorZ,
			double restTwistX, double restTwistY, double restTwistZ, double restRefX, double restRefY, double restRefZ,
			double twistX, double twistY, double twistZ, double refX, double refY, double refZ) {
		return new Joint(Type.BALL, a, b).frame(anchorX, anchorY, anchorZ, restTwistX, restTwistY, restTwistZ, restRefX, restRefY, restRefZ,
				twistX, twistY, twistZ, refX, refY, refZ);
	}

	/**
	 * A hinge made while it is already bent: {@code restRef} is where the child's reference direction would be with
	 * the hinge straight, {@code ref} where it is now. The axis and both references are world directions.
	 */
	public static Joint hinge(Body a, Body b, double anchorX, double anchorY, double anchorZ, double axisX, double axisY, double axisZ,
			double restRefX, double restRefY, double restRefZ, double refX, double refY, double refZ) {
		return new Joint(Type.HINGE, a, b).frame(anchorX, anchorY, anchorZ, axisX, axisY, axisZ, restRefX, restRefY, restRefZ,
				axisX, axisY, axisZ, refX, refY, refZ);
	}

	/** A hinge at a world anchor turning about a world axis; its angle is zero in the pose it is created in. */
	public static Joint hinge(Body a, Body b, double anchorX, double anchorY, double anchorZ,
			double axisX, double axisY, double axisZ) {
		return new Joint(Type.HINGE, a, b).frame(anchorX, anchorY, anchorZ, axisX, axisY, axisZ, 0, 0, 0);
	}

	public Joint limits(double lo, double hi) {
		this.lo = lo;
		this.hi = hi;
		return this;
	}

	public Joint swing(double lo1, double hi1, double lo2, double hi2) {
		swing1Lo = lo1;
		swing1Hi = hi1;
		swing2Lo = lo2;
		swing2Hi = hi2;
		return this;
	}

	public Joint twist(double lo, double hi) {
		twistLo = lo;
		twistHi = hi;
		return this;
	}

	/**
	 * Makes the joint pull like a muscle: B is turned towards the given orientation relative to A, as a spring of the
	 * given frequency and damping ratio, never with more than {@code maxTorque} (kg·blocks²/s²). A muscle too weak for
	 * the weight on it simply loses, which is how a dying limb sags. Call again to move the target or change the
	 * strength; {@link #relax()} lets go.
	 *
	 * @param qx the wanted orientation of B as seen from A, a unit quaternion
	 */
	public void muscle(double qx, double qy, double qz, double qw, double hertz, double dampingRatio, double maxTorque) {
		motorX = qx;
		motorY = qy;
		motorZ = qz;
		motorW = qw;
		motorHertz = hertz;
		motorDamping = dampingRatio;
		motorTorque = maxTorque;
		wake();
	}

	/** A change to a muscle has to reach a body that has gone to sleep. */
	private void wake() {
		if (b.island != null && b.island.asleep) {
			b.island.wake();
		}
	}

	/** Lets the muscle go: the joint is limp again. */
	public void relax() {
		if (motorTorque > 0.0) {
			wake();
		}
		motorTorque = 0.0;
		motor1.acc = motor2.acc = motor3.acc = 0.0;
	}

	public boolean hasMuscle() {
		return motorTorque > 0.0;
	}

	public Joint damping(double perSecond) {
		damping = perSecond;
		return this;
	}

	private Joint frame(double px, double py, double pz, double tx, double ty, double tz, double rx, double ry, double rz) {
		return frame(px, py, pz, tx, ty, tz, rx, ry, rz, tx, ty, tz, rx, ry, rz);
	}

	/** Stores the anchor and the two axes in each body's own space; body A and body B may be given different ones. */
	private Joint frame(double px, double py, double pz, double tax, double tay, double taz, double rax, double ray, double raz,
			double tbx, double tby, double tbz, double rbx, double rby, double rbz) {
		V3 v = new V3();
		v.set(px - a.x, py - a.y, pz - a.z).rotate(-a.qx, -a.qy, -a.qz, a.qw);
		ax = v.x;
		ay = v.y;
		az = v.z;
		v.set(px - b.x, py - b.y, pz - b.z).rotate(-b.qx, -b.qy, -b.qz, b.qw);
		bx = v.x;
		by = v.y;
		bz = v.z;
		double[] f = square(tax, tay, taz, rax, ray, raz);
		v.set(f[0], f[1], f[2]).rotate(-a.qx, -a.qy, -a.qz, a.qw);
		this.tax = v.x;
		this.tay = v.y;
		this.taz = v.z;
		v.set(f[3], f[4], f[5]).rotate(-a.qx, -a.qy, -a.qz, a.qw);
		sax = v.x;
		say = v.y;
		saz = v.z;
		f = square(tbx, tby, tbz, rbx, rby, rbz);
		v.set(f[0], f[1], f[2]).rotate(-b.qx, -b.qy, -b.qz, b.qw);
		this.tbx = v.x;
		this.tby = v.y;
		this.tbz = v.z;
		v.set(f[3], f[4], f[5]).rotate(-b.qx, -b.qy, -b.qz, b.qw);
		sbx = v.x;
		sby = v.y;
		sbz = v.z;
		return this;
	}

	/** A unit twist axis and a unit reference axis square to it; picks a reference when none (or a parallel one) is given. */
	private static double[] square(double tx, double ty, double tz, double rx, double ry, double rz) {
		double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
		tx /= tl;
		ty /= tl;
		tz /= tl;
		double d = rx * tx + ry * ty + rz * tz;
		rx -= d * tx;
		ry -= d * ty;
		rz -= d * tz;
		if (rx * rx + ry * ry + rz * rz < 1.0e-8) {
			if (Math.abs(ty) < 0.9) {
				rx = -tz;
				ry = 0;
				rz = tx;
			} else {
				rx = 0;
				ry = tz;
				rz = -ty;
			}
		}
		double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
		return new double[] {tx, ty, tz, rx / rl, ry / rl, rz / rl};
	}

	/** The hinge's current angle. For tests and debugging: it allocates. */
	public double hingeAngle() {
		V3 axis = new V3().set(tax, tay, taz).rotate(a.qx, a.qy, a.qz, a.qw);
		V3 ra = new V3().set(sax, say, saz).rotate(a.qx, a.qy, a.qz, a.qw);
		V3 rb = new V3().set(sbx, sby, sbz).rotate(b.qx, b.qy, b.qz, b.qw);
		double cx = ra.y * rb.z - ra.z * rb.y, cy = ra.z * rb.x - ra.x * rb.z, cz = ra.x * rb.y - ra.y * rb.x;
		return Math.atan2(cx * axis.x + cy * axis.y + cz * axis.z, ra.x * rb.x + ra.y * rb.y + ra.z * rb.z);
	}

	/** How far the child has swung from its rest direction. For tests and debugging: it allocates. */
	public double swingAngle() {
		V3 ta = new V3().set(tax, tay, taz).rotate(a.qx, a.qy, a.qz, a.qw);
		V3 tb = new V3().set(tbx, tby, tbz).rotate(b.qx, b.qy, b.qz, b.qw);
		return Math.acos(Math.clamp(ta.x * tb.x + ta.y * tb.y + ta.z * tb.z, -1.0, 1.0));
	}

	/** The gap between the two pinned points. For tests and debugging: it allocates. */
	public double separation() {
		V3 pa = new V3().set(ax, ay, az).rotate(a.qx, a.qy, a.qz, a.qw);
		V3 pb = new V3().set(bx, by, bz).rotate(b.qx, b.qy, b.qz, b.qw);
		double dx = a.x + pa.x - b.x - pb.x, dy = a.y + pa.y - b.y - pb.y, dz = a.z + pa.z - b.z - pb.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
