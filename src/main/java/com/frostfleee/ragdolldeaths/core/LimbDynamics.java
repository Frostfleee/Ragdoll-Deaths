package com.frostfleee.ragdolldeaths.core;

import com.frostfleee.ragdolldeaths.core.JointPose.Side;

/**
 * Physics for the limbs, layered on top of the death animation. Each joint is a damped spring around the pose the
 * animation gives it, and is pushed by real forces:
 * <ul>
 * <li><b>inertia</b>: when the body starts to tip, limbs lag behind; when it stops short on the ground, they keep
 * swinging and whip;</li>
 * <li><b>impacts</b>: the body's own acceleration, so a hard landing slams the limbs down and the killing blow
 * flings them toward it;</li>
 * <li><b>gravity</b>: limbs sag toward the ground, and more as the muscles go limp over the death.</li>
 * </ul>
 * The ground holds up a limb of a body that is lying down. Stepped once per tick with substeps; drawing blends
 * between the last two ticks.
 */
public final class LimbDynamics {
	private static final JointRole[] ROLES = {
			JointRole.HEAD, JointRole.ARM, JointRole.LEG, JointRole.FRONT_LEG, JointRole.HIND_LEG, JointRole.TAIL, JointRole.EAR, JointRole.WING
	};
	private static final Side[] SIDES = Side.values();
	private static final int SUBSTEPS = 4;
	private static final double DT = 1.0 / SUBSTEPS;

	/** Gravity in blocks per tick squared, the same as vanilla's for living entities. */
	private static final double GRAVITY = 0.08;
	/**
	 * How much of gravity's pull the limbs feel, and of the body's jolts. Below 1 because the animation's pose
	 * already holds them up the way muscles would; at full strength a limp arm would hang straight down.
	 */
	private static final double FORCE_SHARE = 0.3;
	/** How long the limbs take to go limp, in ticks: their spring fades to {@link #LIMP_FLOOR} of its strength. */
	private static final double LIMP_TICKS = 14.0;
	private static final double LIMP_FLOOR = 0.3;
	private static final double DAMPING_RATIO = 0.28;
	/** The killing blow's jolt, in blocks per tick, at full hit reaction strength. */
	private static final double HIT_JOLT = 0.3;
	/**
	 * A limb of a body lying down rests on the ground: the animation already lays it there, so gravity may only
	 * push it this much lower than the animated pose.
	 */
	private static final double GROUND_GIVE = 0.08;
	private static final double LYING = 0.55;

	private final int count = ROLES.length * SIDES.length;
	private final double[] devX = new double[count];
	private final double[] devZ = new double[count];
	private final double[] velX = new double[count];
	private final double[] velZ = new double[count];
	private final double[] prevX = new double[count];
	private final double[] prevZ = new double[count];

	private final double[] up = new double[3];
	private final double[] forward = new double[3];
	private final double[] left = new double[3];
	private final double[] lastUp = new double[3];
	private final double[] lastForward = new double[3];
	private final double[] lastLeft = new double[3];
	private final double[] spin = new double[3];
	private final double[] lastSpin = new double[3];
	private final double[] center = new double[3];
	private final double[] lastCenter = new double[3];
	private final double[] lastVelocity = new double[3];
	private int steps;

	/**
	 * Advances one tick. {@code pose} and {@code joints} are the animation at this tick; {@code cx, cy, cz} is the
	 * middle of the body in the world, in blocks; {@code t} is the choreography time.
	 */
	public void step(DeathProfile profile, RagdollConfig config, RagdollPose pose, JointPose joints, double cx, double cy, double cz, double t) {
		System.arraycopy(devX, 0, prevX, 0, count);
		System.arraycopy(devZ, 0, prevZ, 0, count);
		basis(profile, pose);

		double[] accel = new double[3];
		if (steps > 0) {
			// The rotation since the last tick, as a rotation vector: half the sum of each axis's turn.
			for (int i = 0; i < 3; i++) {
				spin[i] = 0.5 * (cross(lastUp, up, i) + cross(lastForward, forward, i) + cross(lastLeft, left, i));
			}
			double[] velocity = {cx - lastCenter[0], cy - lastCenter[1], cz - lastCenter[2]};
			if (steps > 1) {
				for (int i = 0; i < 3; i++) {
					accel[i] = velocity[i] - lastVelocity[i];
				}
			}
			System.arraycopy(velocity, 0, lastVelocity, 0, 3);
		}
		if (steps == 0 && profile.hit() != HitDirection.NONE) {
			// The killing blow shoves the body away from it; the limbs are left behind, swinging toward the hit.
			double jolt = HIT_JOLT * config.hitReactionStrength;
			accel[0] += profile.fallDirX() * jolt;
			accel[2] += profile.fallDirZ() * jolt;
		}

		double tone = LIMP_FLOOR + (1.0 - LIMP_FLOOR) * Math.exp(-Math.max(0.0, t) / LIMP_TICKS);
		boolean lying = Math.abs(up[1]) < LYING;
		for (int r = 0; r < ROLES.length; r++) {
			JointRole role = ROLES[r];
			for (Side side : SIDES) {
				int slot = r * SIDES.length + side.ordinal();
				simulate(role, side, slot, joints, accel, tone, lying);
			}
		}

		System.arraycopy(up, 0, lastUp, 0, 3);
		System.arraycopy(forward, 0, lastForward, 0, 3);
		System.arraycopy(left, 0, lastLeft, 0, 3);
		System.arraycopy(spin, 0, lastSpin, 0, 3);
		center[0] = cx;
		center[1] = cy;
		center[2] = cz;
		System.arraycopy(center, 0, lastCenter, 0, 3);
		steps++;
	}

	private void simulate(JointRole role, Side side, int slot, JointPose joints, double[] accel, double tone, boolean lying) {
		double stiffness = stiffness(role) * tone;
		double damping = 2.0 * DAMPING_RATIO * Math.sqrt(stiffness);
		double inertia = inertia(role);
		double length = role == JointRole.HEAD ? 0.35 : 0.45;

		// Joint axes in the world: X swings a limb forward and back about the body's left axis, Z swings it out
		// to its side about the body's forward axis.
		double[] axisX = left;
		double sideSign = side == Side.RIGHT ? -1.0 : 1.0;
		double[] axisZ = {forward[0] * sideSign, forward[1] * sideSign, forward[2] * sideSign};

		// The body's angular acceleration since the last tick drags each limb the opposite way.
		double alpha = 0.0;
		double alphaZ = 0.0;
		if (steps > 1) {
			for (int i = 0; i < 3; i++) {
				alpha += (spin[i] - lastSpin[i]) * axisX[i];
				alphaZ += (spin[i] - lastSpin[i]) * axisZ[i];
			}
		}
		velX[slot] -= alpha * inertia;
		velZ[slot] -= alphaZ * inertia;

		boolean hasGravity = role != JointRole.TAIL && role != JointRole.WING;
		double baseX = joints.approximate(role, side, JointPose.X);
		double baseZ = side == Side.CENTER ? 0.0 : joints.approximate(role, side, JointPose.Z);
		double restY = direction(role, baseX, baseZ, axisX, axisZ)[1];
		// Gravity, less the body's own acceleration: a falling body's limbs float, a landing slams them down. Parts
		// that hold themselves up (a tail, a wing) only feel the slam. Per tick squared; spread over the substeps,
		// these add up to one tick's worth.
		double gx = -accel[0] * FORCE_SHARE;
		double gy = hasGravity ? (-GRAVITY - accel[1]) * FORCE_SHARE : -Math.max(0.0, accel[1]) * FORCE_SHARE;
		double gz = -accel[2] * FORCE_SHARE;
		for (int s = 0; s < SUBSTEPS; s++) {
			double[] dir = direction(role, baseX + devX[slot], baseZ + devZ[slot], axisX, axisZ);
			double forceX = (gx * crossDot(axisX, dir, 0) + gy * crossDot(axisX, dir, 1) + gz * crossDot(axisX, dir, 2)) / length;
			double forceZ = (gx * crossDot(axisZ, dir, 0) + gy * crossDot(axisZ, dir, 1) + gz * crossDot(axisZ, dir, 2)) / length;

			// The ground holds up a limb of a body that is lying down: gravity can't push it further in.
			if (lying && dir[1] < restY - GROUND_GIVE) {
				forceX = groundSupport(forceX, gy * crossDot(axisX, dir, 1) / length);
				forceZ = groundSupport(forceZ, gy * crossDot(axisZ, dir, 1) / length);
			}

			velX[slot] += (forceX - stiffness * devX[slot] - damping * velX[slot]) * DT;
			velZ[slot] += (forceZ - stiffness * devZ[slot] - damping * velZ[slot]) * DT;
			devX[slot] += velX[slot] * DT;
			devZ[slot] += velZ[slot] * DT;

			double maxX = maxSwing(role);
			double maxZ = maxSwing(role) * 0.75;
			if (Math.abs(devX[slot]) > maxX) {
				devX[slot] = Math.copySign(maxX, devX[slot]);
				velX[slot] *= -0.2;
			}
			if (Math.abs(devZ[slot]) > maxZ) {
				devZ[slot] = Math.copySign(maxZ, devZ[slot]);
				velZ[slot] *= -0.2;
			}
		}
	}

	/** Drops the part of a force that would push a limb into the ground, keeping any part that lifts it. */
	private static double groundSupport(double force, double gravityPart) {
		return gravityPart * force > 0.0 ? force - gravityPart : force;
	}

	/** Adds this tick's limb swings to the animation's joints, blended between the last two ticks. */
	public void applyTo(JointPose joints, double partial) {
		for (int r = 0; r < ROLES.length; r++) {
			for (Side side : SIDES) {
				int slot = r * SIDES.length + side.ordinal();
				double x = prevX[slot] + (devX[slot] - prevX[slot]) * partial;
				double z = prevZ[slot] + (devZ[slot] - prevZ[slot]) * partial;
				if (x != 0.0 || z != 0.0) {
					joints.offset(ROLES[r], side, x, 0.0, side == Side.CENTER ? 0.0 : z);
				}
			}
		}
	}

	/** The largest swing any joint has right now, in radians. */
	public double largestSwing() {
		double largest = 0.0;
		for (int i = 0; i < count; i++) {
			largest = Math.max(largest, Math.max(Math.abs(devX[i]), Math.abs(devZ[i])));
		}
		return largest;
	}

	public double swing(JointRole role, Side side, int axis) {
		for (int r = 0; r < ROLES.length; r++) {
			if (ROLES[r] == role) {
				int slot = r * SIDES.length + side.ordinal();
				return axis == JointPose.X ? devX[slot] : devZ[slot];
			}
		}
		return 0.0;
	}

	/**
	 * Scales a joint's swing back toward the animated pose, for when the swing would push the limb into a block,
	 * and stops it there: the limb comes to rest against what it hit.
	 */
	public void constrain(JointRole role, Side side, double keep) {
		for (int r = 0; r < ROLES.length; r++) {
			if (ROLES[r] == role) {
				int slot = r * SIDES.length + side.ordinal();
				devX[slot] *= keep;
				devZ[slot] *= keep;
				velX[slot] = 0.0;
				velZ[slot] = 0.0;
				return;
			}
		}
	}

	/** Sets a joint's swing outright and stops it, to fold a limb out of a block it was animated into. */
	public void setSwing(JointRole role, Side side, double x, double z) {
		for (int r = 0; r < ROLES.length; r++) {
			if (ROLES[r] == role) {
				int slot = r * SIDES.length + side.ordinal();
				devX[slot] = x;
				devZ[slot] = z;
				velX[slot] = 0.0;
				velZ[slot] = 0.0;
				return;
			}
		}
	}

	public static boolean simulates(JointRole role) {
		for (JointRole simulated : ROLES) {
			if (simulated == role) {
				return true;
			}
		}
		return false;
	}

	/** Where the limb points in the world: down from its joint (up for a head, back for a tail), then swung. */
	private double[] direction(JointRole role, double angleX, double angleZ, double[] axisX, double[] axisZ) {
		double[] dir = switch (role) {
			case HEAD, EAR -> new double[] {up[0], up[1], up[2]};
			case TAIL -> new double[] {-forward[0], -forward[1], -forward[2]};
			default -> new double[] {-up[0], -up[1], -up[2]};
		};
		rotate(dir, axisX, angleX);
		rotate(dir, axisZ, angleZ);
		return dir;
	}

	/** The body's up, forward and left directions in the world, as the animation has turned it this tick. */
	private void basis(DeathProfile profile, RagdollPose pose) {
		double[] origin = pose.toWorld(0.0, 0.0, 0.0);
		direction(pose.toWorld(0.0, 1.0, 0.0), origin, up);
		direction(pose.toWorld(profile.forwardX(), 0.0, profile.forwardZ()), origin, forward);
		direction(pose.toWorld(profile.forwardZ(), 0.0, -profile.forwardX()), origin, left);
	}

	private static void direction(double[] to, double[] from, double[] out) {
		double x = to[0] - from[0];
		double y = to[1] - from[1];
		double z = to[2] - from[2];
		double length = Math.sqrt(x * x + y * y + z * z);
		if (length < 1.0E-9) {
			return;
		}
		out[0] = x / length;
		out[1] = y / length;
		out[2] = z / length;
	}

	private static double cross(double[] a, double[] b, int i) {
		return switch (i) {
			case 0 -> a[1] * b[2] - a[2] * b[1];
			case 1 -> a[2] * b[0] - a[0] * b[2];
			default -> a[0] * b[1] - a[1] * b[0];
		};
	}

	/** Component {@code i} of {@code axis × dir}: how the limb's direction moves as it swings about the axis. */
	private static double crossDot(double[] axis, double[] dir, int i) {
		return cross(axis, dir, i);
	}

	/** Rotates {@code v} in place about a unit axis, right-handed. */
	private static void rotate(double[] v, double[] axis, double angle) {
		if (angle == 0.0) {
			return;
		}
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		double dot = axis[0] * v[0] + axis[1] * v[1] + axis[2] * v[2];
		double cx = axis[1] * v[2] - axis[2] * v[1];
		double cy = axis[2] * v[0] - axis[0] * v[2];
		double cz = axis[0] * v[1] - axis[1] * v[0];
		v[0] = v[0] * cos + cx * sin + axis[0] * dot * (1.0 - cos);
		v[1] = v[1] * cos + cy * sin + axis[1] * dot * (1.0 - cos);
		v[2] = v[2] * cos + cz * sin + axis[2] * dot * (1.0 - cos);
	}

	/** Spring strength, in 1/ticks squared: how hard the joint pulls back to the animated pose. */
	private static double stiffness(JointRole role) {
		return switch (role) {
			case HEAD -> 0.55;
			case LEG, FRONT_LEG, HIND_LEG -> 0.45;
			case ARM -> 0.26;
			case WING -> 0.3;
			case TAIL -> 0.22;
			case EAR -> 0.35;
			default -> 0.4;
		};
	}

	/** How much of the body's turning the limb resists. */
	private static double inertia(JointRole role) {
		return switch (role) {
			case HEAD -> 0.55;
			case LEG, FRONT_LEG, HIND_LEG -> 0.6;
			default -> 0.9;
		};
	}

	private static double maxSwing(JointRole role) {
		return switch (role) {
			case HEAD -> 0.6;
			case LEG, FRONT_LEG, HIND_LEG -> 0.8;
			default -> 1.1;
		};
	}
}
