package com.frostfleee.ragdolldeaths.physics;

/**
 * One rigid box: a body part of a ragdoll, or a fixed anchor. Positions are in blocks, time in seconds, mass in
 * kilograms. The box is centred on the body's position and its half extents lie along the body's own axes.
 */
public final class Body {
	public double x, y, z;
	public double qx, qy, qz, qw = 1.0;
	public double vx, vy, vz;
	/** Angular velocity in world space, radians per second. */
	public double wx, wy, wz;
	public double hx = 0.25, hy = 0.25, hz = 0.25;
	public double mass = 1.0;
	public double invMass = 1.0;
	/** Inverse inertia about the body's own axes. */
	public double invIx = 1.0, invIy = 1.0, invIz = 1.0;
	/** Coulomb friction coefficient; a contact uses the geometric mean of the two sides. */
	public double friction = 0.8;
	public double restitution;
	/** Air drag, per second. */
	public double linearDrag;
	public double angularDrag;
	public double gravityScale = 1.0;
	/**
	 * Fluid acting on the body, set by the owner each tick: an upward push (blocks/s², buoyancy), how strongly the
	 * fluid holds the body's motion and spin back (per second), and the velocity the fluid moves at (a current, a
	 * bubble column), which the drag pulls the body towards.
	 */
	public double lift, fluidDrag, fluidSpinDrag;
	public double flowX, flowY, flowZ;
	/** The pose one full step ago, for drawing between steps. */
	public double lastX, lastY, lastZ;
	public double lastQx, lastQy, lastQz, lastQw = 1.0;
	/** Whatever the owner wants to hang on the body (the model part, later). */
	public Object user;

	// Inverse mass and inertia the solver uses: zero while the body is fixed or asleep.
	double im, iix, iiy, iiz;
	// The rotation as a matrix, and the inverse inertia in world space (symmetric), both set by refresh().
	double r00 = 1.0, r01, r02, r10, r11 = 1.0, r12, r20, r21, r22 = 1.0;
	double j00, j01, j02, j11, j12, j22;
	double minX, minY, minZ, maxX, maxY, maxZ;
	double margin;
	// The pose the sleep test measures movement from.
	double sx = Double.MAX_VALUE, sy, sz, sqx, sqy, sqz, sqw = 1.0;
	Island island;
	int index;
	// Last step's contacts in which this was the first body: where each touched (in the body's own space), what it
	// touched, and the three impulses it ended with. The next step's contacts start from these.
	double[] held = new double[6 * 8];
	Body[] heldWith = new Body[8];
	int heldCount;
	/** Indices, within the island, of the bodies jointed to this one; those pairs do not collide. */
	int[] linked = new int[0];

	private final V3 tmp = new V3();

	public Body box(double halfX, double halfY, double halfZ, double mass) {
		hx = halfX;
		hy = halfY;
		hz = halfZ;
		this.mass = mass;
		invMass = 1.0 / mass;
		double k = mass / 3.0;
		invIx = 1.0 / (k * (hy * hy + hz * hz));
		invIy = 1.0 / (k * (hx * hx + hz * hz));
		invIz = 1.0 / (k * (hx * hx + hy * hy));
		return this;
	}

	/** Makes the body immovable: an anchor for joints, or a prop that never moves. */
	public Body fixed() {
		mass = 0.0;
		invMass = invIx = invIy = invIz = 0.0;
		return this;
	}

	/** How many things this body pressed against in the last step (only counted while its island is awake). */
	public int heldCount() {
		return heldCount;
	}

	/** The body (another part, another ragdoll or a block) that contact {@code m} of the last step was with. */
	public Body heldWith(int m) {
		return heldWith[m];
	}

	public boolean isFixed() {
		return invMass == 0.0;
	}

	public Body position(double x, double y, double z) {
		this.x = lastX = x;
		this.y = lastY = y;
		this.z = lastZ = z;
		return this;
	}

	public Body rotation(double qx, double qy, double qz, double qw) {
		double n = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
		this.qx = lastQx = qx / n;
		this.qy = lastQy = qy / n;
		this.qz = lastQz = qz / n;
		this.qw = lastQw = qw / n;
		return this;
	}

	public Body velocity(double vx, double vy, double vz) {
		this.vx = vx;
		this.vy = vy;
		this.vz = vz;
		return this;
	}

	public Body spin(double wx, double wy, double wz) {
		this.wx = wx;
		this.wy = wy;
		this.wz = wz;
		return this;
	}

	/** An impulse (kg·blocks/s) applied at a world point: the killing blow. Wakes the body's ragdoll. */
	public void applyImpulse(double ix, double iy, double iz, double atX, double atY, double atZ) {
		if (invMass == 0.0) {
			return;
		}
		vx += ix * invMass;
		vy += iy * invMass;
		vz += iz * invMass;
		double rx = atX - x, ry = atY - y, rz = atZ - z;
		tmp.set(ry * iz - rz * iy, rz * ix - rx * iz, rx * iy - ry * ix);
		tmp.rotate(-qx, -qy, -qz, qw);
		tmp.set(tmp.x * invIx, tmp.y * invIy, tmp.z * invIz);
		tmp.rotate(qx, qy, qz, qw);
		wx += tmp.x;
		wy += tmp.y;
		wz += tmp.z;
		if (island != null) {
			island.wake();
		}
	}

	public double kineticEnergy() {
		if (invMass == 0.0) {
			return 0.0;
		}
		tmp.set(wx, wy, wz).rotate(-qx, -qy, -qz, qw);
		return 0.5 * mass * (vx * vx + vy * vy + vz * vz)
				+ 0.5 * (tmp.x * tmp.x / invIx + tmp.y * tmp.y / invIy + tmp.z * tmp.z / invIz);
	}

	/** Recomputes the rotation matrix and the world-space inverse inertia from the current pose. */
	void refresh() {
		double xx = qx * qx, yy = qy * qy, zz = qz * qz;
		double xy = qx * qy, xz = qx * qz, yz = qy * qz;
		double wxq = qw * qx, wyq = qw * qy, wzq = qw * qz;
		r00 = 1 - 2 * (yy + zz);
		r01 = 2 * (xy - wzq);
		r02 = 2 * (xz + wyq);
		r10 = 2 * (xy + wzq);
		r11 = 1 - 2 * (xx + zz);
		r12 = 2 * (yz - wxq);
		r20 = 2 * (xz - wyq);
		r21 = 2 * (yz + wxq);
		r22 = 1 - 2 * (xx + yy);
		if (im == 0.0) {
			j00 = j01 = j02 = j11 = j12 = j22 = 0.0;
			return;
		}
		double a0 = r00 * iix, a1 = r01 * iiy, a2 = r02 * iiz;
		double b0 = r10 * iix, b1 = r11 * iiy, b2 = r12 * iiz;
		double c0 = r20 * iix, c1 = r21 * iiy, c2 = r22 * iiz;
		j00 = a0 * r00 + a1 * r01 + a2 * r02;
		j01 = a0 * r10 + a1 * r11 + a2 * r12;
		j02 = a0 * r20 + a1 * r21 + a2 * r22;
		j11 = b0 * r10 + b1 * r11 + b2 * r12;
		j12 = b0 * r20 + b1 * r21 + b2 * r22;
		j22 = c0 * r20 + c1 * r21 + c2 * r22;
	}

	/** Turns the body by a small rotation vector (axis times angle, world space). */
	void applyRotation(double rx, double ry, double rz) {
		double len2 = rx * rx + ry * ry + rz * rz;
		if (len2 > 1.0) {
			// Never more than a radian in one substep, whatever went wrong upstream.
			double s = 1.0 / Math.sqrt(len2);
			rx *= s;
			ry *= s;
			rz *= s;
		}
		double nx = qx + 0.5 * (rx * qw + ry * qz - rz * qy);
		double ny = qy + 0.5 * (ry * qw + rz * qx - rx * qz);
		double nz = qz + 0.5 * (rz * qw + rx * qy - ry * qx);
		double nw = qw - 0.5 * (rx * qx + ry * qy + rz * qz);
		double n = 1.0 / Math.sqrt(nx * nx + ny * ny + nz * nz + nw * nw);
		qx = nx * n;
		qy = ny * n;
		qz = nz * n;
		qw = nw * n;
	}

	/** World bounds of the box, grown by how far it can travel in one step so fast bodies find their contacts early. */
	void updateBounds(double step) {
		double xx = qx * qx, yy = qy * qy, zz = qz * qz;
		double xy = qx * qy, xz = qx * qz, yz = qy * qz;
		double wxq = qw * qx, wyq = qw * qy, wzq = qw * qz;
		double ex = Math.abs(1 - 2 * (yy + zz)) * hx + Math.abs(2 * (xy - wzq)) * hy + Math.abs(2 * (xz + wyq)) * hz;
		double ey = Math.abs(2 * (xy + wzq)) * hx + Math.abs(1 - 2 * (xx + zz)) * hy + Math.abs(2 * (yz - wxq)) * hz;
		double ez = Math.abs(2 * (xz - wyq)) * hx + Math.abs(2 * (yz + wxq)) * hy + Math.abs(1 - 2 * (xx + yy)) * hz;
		margin = PhysicsWorld.CONTACT_MARGIN + step * speed();
		minX = x - ex - margin;
		minY = y - ey - margin;
		minZ = z - ez - margin;
		maxX = x + ex + margin;
		maxY = y + ey + margin;
		maxZ = z + ez + margin;
	}

	/** Half the box's height as it is turned now: how far it reaches above and below its centre. */
	public double halfHeight() {
		double xx = qx * qx, yy = qy * qy, zz = qz * qz;
		double xy = qx * qy, yz = qy * qz, wx = qw * qx, wz = qw * qz;
		return Math.abs(2 * (xy + wz)) * hx + Math.abs(1 - 2 * (xx + zz)) * hy + Math.abs(2 * (yz - wx)) * hz;
	}

	/** The fastest any point of the box is moving. */
	double speed() {
		return Math.sqrt(vx * vx + vy * vy + vz * vz) + spinSpeed();
	}

	/** The fastest the box's corners move because of its spin alone. */
	double spinSpeed() {
		return Math.sqrt(wx * wx + wy * wy + wz * wz) * Math.sqrt(hx * hx + hy * hy + hz * hz);
	}

	boolean overlaps(Body o) {
		return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY && minZ <= o.maxZ && maxZ >= o.minZ;
	}
}
