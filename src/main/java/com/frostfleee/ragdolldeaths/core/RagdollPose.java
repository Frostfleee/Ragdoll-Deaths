package com.frostfleee.ragdolldeaths.core;

/**
 * World-aligned transform applied around the entity's feet before vanilla's own body rotation.
 * Order: translate by offset, tilt about the horizontal slope axis through the feet (to rest on uneven
 * ground), then around (0, pivotY, 0) spin about up, tip about the horizontal tip axis, roll about the
 * body's forward axis, then squash from the feet.
 */
public final class RagdollPose {
	public double offsetX;
	public double offsetY;
	public double offsetZ;
	public double pivotY;
	public double spinAngle;
	public double tipAxisX;
	public double tipAxisZ;
	public double tipAngle;
	public double forwardX;
	public double forwardZ;
	public double rollAngle;
	public double squashY;
	public double squashXZ;
	public double slopeAxisX;
	public double slopeAxisZ;
	public double slopeAngle;
	public boolean hidden;

	public RagdollPose() {
		reset();
	}

	public void reset() {
		offsetX = 0.0;
		offsetY = 0.0;
		offsetZ = 0.0;
		pivotY = 0.0;
		spinAngle = 0.0;
		tipAxisX = 1.0;
		tipAxisZ = 0.0;
		tipAngle = 0.0;
		forwardX = 0.0;
		forwardZ = 1.0;
		rollAngle = 0.0;
		squashY = 1.0;
		squashXZ = 1.0;
		slopeAxisX = 1.0;
		slopeAxisZ = 0.0;
		slopeAngle = 0.0;
		hidden = false;
	}

	public void copyFrom(RagdollPose other) {
		offsetX = other.offsetX;
		offsetY = other.offsetY;
		offsetZ = other.offsetZ;
		pivotY = other.pivotY;
		spinAngle = other.spinAngle;
		tipAxisX = other.tipAxisX;
		tipAxisZ = other.tipAxisZ;
		tipAngle = other.tipAngle;
		forwardX = other.forwardX;
		forwardZ = other.forwardZ;
		rollAngle = other.rollAngle;
		squashY = other.squashY;
		squashXZ = other.squashXZ;
		slopeAxisX = other.slopeAxisX;
		slopeAxisZ = other.slopeAxisZ;
		slopeAngle = other.slopeAngle;
		hidden = other.hidden;
	}

	/**
	 * Where a point of the upright body ends up once this pose is applied, both relative to the entity's feet. The
	 * steps match the renderer's: squash, then roll, tip and spin about the pivot, then the slope tilt about the
	 * feet, then the offset.
	 */
	public double[] toWorld(double x, double y, double z) {
		double[] v = {x * squashXZ, y * squashY - pivotY, z * squashXZ};
		rotate(v, forwardX, 0.0, forwardZ, rollAngle);
		rotate(v, tipAxisX, 0.0, tipAxisZ, tipAngle);
		rotate(v, 0.0, 1.0, 0.0, spinAngle);
		v[1] += pivotY;
		rotate(v, slopeAxisX, 0.0, slopeAxisZ, slopeAngle);
		return new double[] {v[0] + offsetX, v[1] + offsetY, v[2] + offsetZ};
	}

	/**
	 * Tilts the whole body back up by {@code angle} about its feet, toward the side it lies on: it comes to rest
	 * leaning against whatever it would otherwise go into. Adds to any tilt from the ground's slope.
	 */
	public void liftTop(double angle, double height) {
		if (angle == 0.0) {
			return;
		}
		double[] top = toWorld(0.0, height, 0.0);
		double dirX = top[0] - offsetX;
		double dirZ = top[2] - offsetZ;
		double lean = Math.hypot(dirX, dirZ);
		if (lean < 1.0E-6) {
			return;
		}
		double axisX = -dirZ / lean;
		double axisZ = dirX / lean;
		if (slopeAngle != 0.0 && slopeAxisX * axisX + slopeAxisZ * axisZ < 0.0) {
			slopeAngle = -slopeAngle;
		}
		slopeAxisX = axisX;
		slopeAxisZ = axisZ;
		slopeAngle += angle;
	}

	/** How far the body leans from upright, in radians: 0 standing, about pi/2 lying down. */
	public double tilt() {
		double[] up = toWorld(0.0, 1.0, 0.0);
		double[] base = toWorld(0.0, 0.0, 0.0);
		double x = up[0] - base[0];
		double y = up[1] - base[1];
		double z = up[2] - base[2];
		double length = Math.sqrt(x * x + y * y + z * z);
		return length < 1.0E-9 ? 0.0 : Math.acos(Math.clamp(y / length, -1.0, 1.0));
	}

	/** Rotates {@code v} in place about the given axis (normalized here) by {@code angle}, right-handed. */
	private static void rotate(double[] v, double ax, double ay, double az, double angle) {
		double length = Math.sqrt(ax * ax + ay * ay + az * az);
		if (angle == 0.0 || length < 1.0E-9) {
			return;
		}
		ax /= length;
		ay /= length;
		az /= length;
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		double dot = ax * v[0] + ay * v[1] + az * v[2];
		double cx = ay * v[2] - az * v[1];
		double cy = az * v[0] - ax * v[2];
		double cz = ax * v[1] - ay * v[0];
		v[0] = v[0] * cos + cx * sin + ax * dot * (1.0 - cos);
		v[1] = v[1] * cos + cy * sin + ay * dot * (1.0 - cos);
		v[2] = v[2] * cos + cz * sin + az * dot * (1.0 - cos);
	}

	/** Sets the tip so the body's top swings toward the given horizontal direction. */
	public void tipToward(double dirX, double dirZ, double angle) {
		double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
		if (length < 1.0E-6) {
			dirX = 1.0;
			dirZ = 0.0;
			length = 1.0;
		}
		tipAxisX = dirZ / length;
		tipAxisZ = -dirX / length;
		tipAngle = angle;
	}
}
