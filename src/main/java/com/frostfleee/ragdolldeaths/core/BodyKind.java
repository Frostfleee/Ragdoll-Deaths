package com.frostfleee.ragdolldeaths.core;

public enum BodyKind {
	STANDARD(false),
	WINGED(true),
	SPIRIT(true),
	GHAST(true),
	AQUATIC(false),
	CUBE(false),
	SHULKER(false);

	private final boolean airborne;

	BodyKind(boolean airborne) {
		this.airborne = airborne;
	}

	/** Airborne kinds follow their own trajectory from the death position instead of the synced entity position. */
	public boolean airborne() {
		return airborne;
	}
}
