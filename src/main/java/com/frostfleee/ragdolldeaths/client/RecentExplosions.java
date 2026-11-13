package com.frostfleee.ragdolldeaths.client;

import net.minecraft.world.phys.Vec3;

/**
 * The explosions the client was told about in the last moment, with their exact centre and size.
 *
 * <p>A mob killed by a creeper knows where the creeper was, but one killed by TNT does not: the primed TNT is gone
 * before the damage reaches the client, and the damage arrives with no position. Every explosion is also sent to the
 * client as its own packet, with its centre, so the blast that killed a mob is the one that went off near it just now.
 */
public final class RecentExplosions {
	/** An explosion is kept this long, in milliseconds: the packet and the death can arrive a few frames apart. */
	private static final long KEEP_MILLIS = 1500L;
	private static final int SIZE = 32;

	private static final double[] X = new double[SIZE], Y = new double[SIZE], Z = new double[SIZE], RADIUS = new double[SIZE];
	private static final long[] WHEN = new long[SIZE];
	private static int next;

	private RecentExplosions() {
	}

	/** Called on the client thread for every explosion packet. */
	public static void record(Vec3 centre, float radius) {
		int i = next;
		next = (next + 1) % SIZE;
		X[i] = centre.x;
		Y[i] = centre.y;
		Z[i] = centre.z;
		RADIUS[i] = radius;
		WHEN[i] = System.currentTimeMillis();
	}

	/**
	 * The centre and reach of the latest explosion that could have hit something at this point, or null when none
	 * went off near it just now. The reach is how far the blast hurts: twice its radius, as the game counts it.
	 */
	static double[] near(Vec3 point) {
		long now = System.currentTimeMillis();
		double[] best = null;
		long bestWhen = Long.MIN_VALUE;
		for (int i = 0; i < SIZE; i++) {
			if (WHEN[i] == 0L || now - WHEN[i] > KEEP_MILLIS || WHEN[i] < bestWhen) {
				continue;
			}
			double reach = RADIUS[i] * 2.0;
			double dx = point.x - X[i], dy = point.y - Y[i], dz = point.z - Z[i];
			if (dx * dx + dy * dy + dz * dz <= (reach + 2.0) * (reach + 2.0)) {
				best = new double[] {X[i], Y[i], Z[i], reach};
				bestWhen = WHEN[i];
			}
		}
		return best;
	}

	static void clear() {
		java.util.Arrays.fill(WHEN, 0L);
	}
}
