package com.frostfleee.ragdolldeaths.physics;

/** A reusable list of fixed world boxes (block collision shapes) with their surface material. */
public final class BoxBuffer {
	static final int STRIDE = 8;

	double[] data = new double[STRIDE * 32];
	int count;

	public void clear() {
		count = 0;
	}

	public int size() {
		return count;
	}

	/**
	 * @param friction Coulomb friction of the surface (stone about 0.8, ice about 0.05)
	 * @param restitution bounciness, 0 for nearly everything, high for slime
	 */
	public void add(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
			double friction, double restitution) {
		// A box that carries straight on from the last one along X, with the same surface, just makes that one
		// longer: a floor of blocks becomes a few long strips. Fewer boxes to test, and no seams to catch on.
		if (count > 0) {
			int last = (count - 1) * STRIDE;
			if (data[last + 3] == minX && data[last + 1] == minY && data[last + 2] == minZ && data[last + 4] == maxY && data[last + 5] == maxZ
					&& data[last + 6] == friction && data[last + 7] == restitution) {
				data[last + 3] = maxX;
				return;
			}
		}
		int at = count * STRIDE;
		if (at == data.length) {
			data = java.util.Arrays.copyOf(data, data.length * 2);
		}
		data[at] = minX;
		data[at + 1] = minY;
		data[at + 2] = minZ;
		data[at + 3] = maxX;
		data[at + 4] = maxY;
		data[at + 5] = maxZ;
		data[at + 6] = friction;
		data[at + 7] = restitution;
		count++;
	}
}
