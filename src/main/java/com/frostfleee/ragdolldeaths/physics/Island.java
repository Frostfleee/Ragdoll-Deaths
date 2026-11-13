package com.frostfleee.ragdolldeaths.physics;

/**
 * The bodies and joints of one ragdoll. It sleeps and wakes as a whole, and when its state goes bad (NaN, an
 * exploding solve) only this island is marked {@link #failed}, so the caller can fall back for that one body.
 */
public final class Island {
	public final Body[] bodies;
	public final Joint[] joints;
	public boolean asleep;
	public boolean failed;
	/** Whether parts of this ragdoll that are not jointed to each other collide (arm against torso). */
	public boolean selfCollide = true;

	double quiet;
	double maxSpeed;
	double minX, minY, minZ, maxX, maxY, maxZ;
	final BoxBuffer statics = new BoxBuffer();
	// The region the cached world boxes cover and the world stamp they were fetched at.
	long boxStamp = Long.MIN_VALUE;
	double boxMinX, boxMinY, boxMinZ, boxMaxX, boxMaxY, boxMaxZ;

	public Island(Body[] bodies, Joint[] joints) {
		this.bodies = bodies;
		this.joints = joints;
		int[] counts = new int[bodies.length];
		for (int i = 0; i < bodies.length; i++) {
			bodies[i].island = this;
			bodies[i].index = i;
		}
		for (Joint j : joints) {
			counts[j.a.index]++;
			counts[j.b.index]++;
		}
		for (int i = 0; i < bodies.length; i++) {
			bodies[i].linked = new int[counts[i]];
			counts[i] = 0;
		}
		for (Joint j : joints) {
			j.a.linked[counts[j.a.index]++] = j.b.index;
			j.b.linked[counts[j.b.index]++] = j.a.index;
		}
	}

	/**
	 * Stops collisions between parts whose boxes overlap right now. Called once, when the ragdoll is made: parts
	 * the model already draws inside each other (legs meeting at a body, a tail root inside a hip) would otherwise be
	 * shoved apart at once.
	 */
	public void ignoreOverlaps() {
		BoxCollision test = new BoxCollision();
		for (int i = 0; i < bodies.length; i++) {
			test.setA(bodies[i]);
			for (int j = i + 1; j < bodies.length; j++) {
				test.setB(bodies[j]);
				if (test.collide(-0.01)) {
					bodies[i].linked = append(bodies[i].linked, j);
					bodies[j].linked = append(bodies[j].linked, i);
				}
			}
		}
	}

	private static int[] append(int[] list, int value) {
		for (int v : list) {
			if (v == value) {
				return list;
			}
		}
		int[] longer = java.util.Arrays.copyOf(list, list.length + 1);
		longer[list.length] = value;
		return longer;
	}

	/**
	 * Breaks a joint for good: the two parts come apart, and from now on they collide with each other like any
	 * other two parts.
	 */
	public void release(Joint joint) {
		joint.broken = true;
		joint.a.linked = without(joint.a.linked, joint.b.index);
		joint.b.linked = without(joint.b.linked, joint.a.index);
		wake();
	}

	private static int[] without(int[] list, int value) {
		int at = -1;
		for (int i = 0; i < list.length; i++) {
			if (list[i] == value) {
				at = i;
			}
		}
		if (at < 0) {
			return list;
		}
		int[] shorter = new int[list.length - 1];
		System.arraycopy(list, 0, shorter, 0, at);
		System.arraycopy(list, at + 1, shorter, at, shorter.length - at);
		return shorter;
	}

	public void wake() {
		asleep = false;
		quiet = 0.0;
	}

	public double kineticEnergy() {
		double e = 0.0;
		for (Body b : bodies) {
			e += b.kineticEnergy();
		}
		return e;
	}

	boolean overlaps(Island o) {
		return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY && minZ <= o.maxZ && maxZ >= o.minZ;
	}
}
