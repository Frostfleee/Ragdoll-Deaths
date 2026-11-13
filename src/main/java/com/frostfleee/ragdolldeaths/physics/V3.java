package com.frostfleee.ragdolldeaths.physics;

/** A mutable vector for scratch maths. The solver keeps a few of these so its loops never allocate. */
final class V3 {
	double x;
	double y;
	double z;

	V3 set(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}

	/** Rotates the vector by a unit quaternion, in place. */
	V3 rotate(double qx, double qy, double qz, double qw) {
		double tx = 2.0 * (qy * z - qz * y);
		double ty = 2.0 * (qz * x - qx * z);
		double tz = 2.0 * (qx * y - qy * x);
		x += qw * tx + (qy * tz - qz * ty);
		y += qw * ty + (qz * tx - qx * tz);
		z += qw * tz + (qx * ty - qy * tx);
		return this;
	}
}
