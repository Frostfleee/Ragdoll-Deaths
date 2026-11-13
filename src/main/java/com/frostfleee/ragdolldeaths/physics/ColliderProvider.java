package com.frostfleee.ragdolldeaths.physics;

/** The fixed world the bodies collide with. In game this is the block collision shapes around a ragdoll. */
public interface ColliderProvider {
	/** Adds every fixed box touching the given bounds to {@code out}. Called once per awake ragdoll per step. */
	void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, BoxBuffer out);
}
