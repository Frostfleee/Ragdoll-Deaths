package com.frostfleee.ragdolldeaths.core;

public enum JointRole {
	HEAD,
	NECK,
	BODY,
	ARM,
	LEG,
	FRONT_LEG,
	HIND_LEG,
	MID_LEG,
	WING,
	TAIL,
	FIN,
	TENTACLE,
	EAR,
	JAW,
	EYE,
	/** Loose rods, such as the ones circling a blaze or a breeze. */
	ROD,
	/** A breeze's whirlwind. */
	WIND,
	/** One link of a tail built as a chain ({@code tail0}, {@code tail1}, ...), such as a guardian's. */
	TAIL_SEGMENT,
	/** A guardian's spikes. */
	SPIKE;

	public boolean isLeg() {
		return this == LEG || this == FRONT_LEG || this == HIND_LEG || this == MID_LEG;
	}
}
