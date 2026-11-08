package com.frostfleee.ragdolldeaths.core;

public enum HitDirection {
	FRONT,
	BACK,
	LEFT,
	RIGHT,
	NONE;

	private static final double MIN_DISTANCE_SQR = 1.0E-6;

	/**
	 * Classifies where a hit came from relative to the victim's body. Uses Minecraft's yaw
	 * convention: yaw 0 faces +Z, so forward is (-sin, cos) and the body's left is (cos, sin).
	 */
	public static HitDirection classify(float bodyYawDegrees, double toSourceX, double toSourceZ) {
		if (toSourceX * toSourceX + toSourceZ * toSourceZ < MIN_DISTANCE_SQR) {
			return NONE;
		}

		double yaw = Math.toRadians(bodyYawDegrees);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		double forward = -sin * toSourceX + cos * toSourceZ;
		double left = cos * toSourceX + sin * toSourceZ;

		if (Math.abs(forward) >= Math.abs(left)) {
			return forward > 0 ? FRONT : BACK;
		}
		return left > 0 ? LEFT : RIGHT;
	}

	/**
	 * The horizontal direction the body topples toward: away from the hit. Without a known
	 * source the body falls to one side, picked by {@code seed} so it stays stable across frames.
	 */
	public double[] fallDirection(float bodyYawDegrees, long seed) {
		double yaw = Math.toRadians(bodyYawDegrees);
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);
		double leftX = Math.cos(yaw);
		double leftZ = Math.sin(yaw);

		return switch (this) {
			case FRONT -> new double[] {-forwardX, -forwardZ};
			case BACK -> new double[] {forwardX, forwardZ};
			case LEFT -> new double[] {-leftX, -leftZ};
			case RIGHT -> new double[] {leftX, leftZ};
			case NONE -> (seed & 1L) == 0L ? new double[] {leftX, leftZ} : new double[] {-leftX, -leftZ};
		};
	}
}
