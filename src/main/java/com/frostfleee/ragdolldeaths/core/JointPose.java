package com.frostfleee.ragdolldeaths.core;

import java.util.Arrays;

/**
 * Per-joint rotation offsets for one frame of a death animation. Values are written in an
 * outward-positive convention for side parts: a positive Z on either arm swings it away from the
 * body. Left-side Y and Z are mirrored internally to match vanilla model space.
 */
public final class JointPose {
	public enum Side {
		RIGHT,
		LEFT,
		CENTER
	}

	public static final int X = 0;
	public static final int Y = 1;
	public static final int Z = 2;

	private static final int ROLES = JointRole.values().length;
	private static final int SIDES = Side.values().length;
	private static final double INDEX_PHASE = 1.7;
	private static final double LEFT_PHASE = 0.9;
	private static final double SCATTER_TIP_TICKS = 4.0;

	private final double[] target = new double[ROLES * SIDES * 3];
	private final double[] blend = new double[ROLES * SIDES];
	private final double[] add = new double[ROLES * SIDES * 3];
	private final double[] move = new double[ROLES * SIDES * 3];
	private final boolean[] centerSet = new boolean[ROLES];
	private final double[] twitchAmplitude = new double[ROLES * 3];
	private final double[] twitchFrequency = new double[ROLES];
	private final double[] lift = new double[ROLES];
	private final double[] scale = new double[ROLES * 3];
	private final double[] relax = new double[ROLES];
	private final double[] scatter = new double[ROLES * 5];
	private final boolean[] scatters = new boolean[ROLES];
	private long scatterSeed;
	private double meltHeight;
	private double meltSpread;
	private double time;

	public JointPose() {
		reset(0.0);
	}

	public void reset(double time) {
		this.time = time;
		Arrays.fill(target, Double.NaN);
		Arrays.fill(blend, 0.0);
		Arrays.fill(add, 0.0);
		Arrays.fill(move, 0.0);
		Arrays.fill(centerSet, false);
		Arrays.fill(twitchAmplitude, 0.0);
		Arrays.fill(twitchFrequency, 0.0);
		Arrays.fill(lift, 0.0);
		Arrays.fill(scale, 1.0);
		Arrays.fill(relax, 0.0);
		Arrays.fill(scatters, false);
		meltHeight = 1.0;
		meltSpread = 1.0;
	}

	/** Blends the joint toward an absolute rotation. NaN keeps the model's own value on that axis. */
	public void set(JointRole role, Side side, double x, double y, double z, double weight) {
		int slot = slot(role, side);
		double mirror = side == Side.LEFT ? -1.0 : 1.0;
		target[slot * 3 + X] = x;
		target[slot * 3 + Y] = y * mirror;
		target[slot * 3 + Z] = z * mirror;
		blend[slot] = Motion.clamp01(weight);
		markCenter(role, side);
	}

	public void both(JointRole role, double x, double y, double z, double weight) {
		set(role, Side.RIGHT, x, y, z, weight);
		set(role, Side.LEFT, x, y, z, weight);
	}

	public void offset(JointRole role, Side side, double x, double y, double z) {
		int slot = slot(role, side);
		double mirror = side == Side.LEFT ? -1.0 : 1.0;
		add[slot * 3 + X] += x;
		add[slot * 3 + Y] += y * mirror;
		add[slot * 3 + Z] += z * mirror;
		markCenter(role, side);
	}

	public void offsetAll(JointRole role, double x, double y, double z) {
		offset(role, Side.RIGHT, x, y, z);
		offset(role, Side.LEFT, x, y, z);
		offset(role, Side.CENTER, x, y, z);
	}

	/** Shifts the joint's pivot, in model pixels and raw model space (+Y is down, -Z is the model's front). */
	public void move(JointRole role, Side side, double x, double y, double z) {
		int slot = slot(role, side);
		move[slot * 3 + X] += x;
		move[slot * 3 + Y] += y;
		move[slot * 3 + Z] += z;
	}

	public double translation(JointRole role, Side side, int axis) {
		return move[slot(role, side) * 3 + axis];
	}

	/** Independent per-part oscillation; parts of the same role are phase-shifted by their index. */
	public void twitch(JointRole role, double x, double y, double z, double frequency) {
		int base = role.ordinal() * 3;
		twitchAmplitude[base + X] = x;
		twitchAmplitude[base + Y] = y;
		twitchAmplitude[base + Z] = z;
		twitchFrequency[role.ordinal()] = frequency;
	}

	/** Swings radially arranged parts, such as tentacles, outward (positive) or inward (negative). */
	public void lift(JointRole role, double amount) {
		lift[role.ordinal()] = amount;
	}

	/**
	 * Pulls the joint back toward its rest pose before any other motion is added, cancelling whatever pose the
	 * model was in when it died (a rearing horse, a sitting wolf).
	 */
	public void relax(JointRole role, double weight) {
		relax[role.ordinal()] = Motion.clamp01(weight);
	}

	public void relaxAll(double weight, JointRole... roles) {
		for (JointRole role : roles) {
			relax(role, weight);
		}
	}

	public double relax(JointRole role) {
		return relax[role.ordinal()];
	}

	/**
	 * Collapses the model toward the ground: every pivot sinks by {@code height} and spreads by {@code spread};
	 * body parts are also squashed. Other parts keep their own shape.
	 */
	public void melt(double height, double spread) {
		meltHeight = height;
		meltSpread = spread;
	}

	public double meltHeight() {
		return meltHeight;
	}

	public double meltSpread() {
		return meltSpread;
	}

	/**
	 * Loose parts of this role, such as a blaze's rods, drop straight down one after another in a shuffled order
	 * spread over {@code spreadTicks}, each taking {@code fallTicks}. Then each tips over flat facing its own random
	 * way, slides up to {@code slide} pixels outward and bounces up to {@code hop} pixels.
	 */
	public void scatter(JointRole role, double start, double fallTicks, double spreadTicks, double slide, double hop, long seed) {
		int base = role.ordinal() * 5;
		scatter[base] = start;
		scatter[base + 1] = Math.max(1.0, fallTicks);
		scatter[base + 2] = Math.max(0.0, spreadTicks);
		scatter[base + 3] = slide;
		scatter[base + 4] = hop;
		scatters[role.ordinal()] = true;
		scatterSeed = seed;
	}

	public boolean scatters(JointRole role) {
		return scatters[role.ordinal()];
	}

	/** 0 to 1: how far the part has dropped toward standing upright on the ground. */
	public double scatterDrop(JointRole role, int index) {
		double start = scatterStart(role, index);
		return Motion.fall(time, start, start + scatter[role.ordinal() * 5 + 1]);
	}

	/** 0 to 1: how far the part has tipped over from upright to lying flat. */
	public double scatterTip(JointRole role, int index) {
		double landed = scatterStart(role, index) + scatter[role.ordinal() * 5 + 1];
		return Motion.fall(time, landed, landed + SCATTER_TIP_TICKS);
	}

	/** The direction the part ends up pointing, as a rotation about the vertical axis. */
	public double scatterHeading(JointRole role, int index) {
		return Math.PI * (2.0 * random(role, index, 1) - 1.0);
	}

	/** Pixels the part has slid outward from the body's center. */
	public double scatterSlide(JointRole role, int index) {
		double landed = scatterStart(role, index) + scatter[role.ordinal() * 5 + 1];
		double distance = scatter[role.ordinal() * 5 + 3] * (0.5 + 0.5 * random(role, index, 2));
		return distance * Motion.easeOut(Motion.progress(time, landed, landed + SCATTER_TIP_TICKS + 2.0));
	}

	/** Pixels the part is bouncing up off the ground after it has tipped flat. */
	public double scatterHop(JointRole role, int index) {
		double flat = scatterStart(role, index) + scatter[role.ordinal() * 5 + 1] + SCATTER_TIP_TICKS;
		double height = scatter[role.ordinal() * 5 + 4] * (0.6 + 0.4 * random(role, index, 3));
		return Motion.bounce(time, flat, height, 1.4, 0.5);
	}

	private double scatterStart(JointRole role, int index) {
		int base = role.ordinal() * 5;
		return scatter[base] + scatter[base + 2] * random(role, index, 0);
	}

	/** A stable value in [0, 1) for one part, so every part of a role gets its own timing and direction. */
	private double random(JointRole role, int index, int salt) {
		long h = scatterSeed ^ (role.ordinal() * 0x9E3779B97F4A7C15L) ^ ((index * 31L + salt) * 0xBF58476D1CE4E5B9L);
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** Multiplies the size of every part with this role. */
	public void scale(JointRole role, double x, double y, double z) {
		int base = role.ordinal() * 3;
		scale[base + X] = x;
		scale[base + Y] = y;
		scale[base + Z] = z;
	}

	public double scale(JointRole role, int axis) {
		return scale[role.ordinal() * 3 + axis];
	}

	public double lift(JointRole role) {
		return lift[role.ordinal()];
	}

	public double rotation(JointRole role, Side side, int axis, int index, double original) {
		Side source = side == Side.CENTER && !centerSet[role.ordinal()] ? Side.RIGHT : side;
		if (source != side && axis != X) {
			return original;
		}

		int slot = slot(role, source);
		double value = original;
		double targetValue = target[slot * 3 + axis];
		if (blend[slot] > 0.0 && !Double.isNaN(targetValue)) {
			value = Motion.lerp(original, targetValue, blend[slot]);
		}
		value += add[slot * 3 + axis];

		double amplitude = twitchAmplitude[role.ordinal() * 3 + axis];
		if (amplitude != 0.0) {
			double phase = index * INDEX_PHASE + (side == Side.LEFT ? LEFT_PHASE : 0.0);
			double mirror = side == Side.LEFT && axis != X ? -1.0 : 1.0;
			value += mirror * amplitude * Math.sin(twitchFrequency[role.ordinal()] * time + phase);
		}
		return value;
	}

	private void markCenter(JointRole role, Side side) {
		if (side == Side.CENTER) {
			centerSet[role.ordinal()] = true;
		}
	}

	private static int slot(JointRole role, Side side) {
		return role.ordinal() * SIDES + side.ordinal();
	}
}
