package com.frostfleee.ragdolldeaths.core;

final class Motion {
	static final double HALF_PI = Math.PI / 2.0;

	private Motion() {
	}

	static double clamp01(double x) {
		return Math.clamp(x, 0.0, 1.0);
	}

	static double progress(double t, double from, double to) {
		return to <= from ? (t >= to ? 1.0 : 0.0) : clamp01((t - from) / (to - from));
	}

	static double smooth(double t, double from, double to) {
		double x = progress(t, from, to);
		return x * x * (3.0 - 2.0 * x);
	}

	/** Accelerating like a falling body. */
	static double fall(double t, double from, double to) {
		double x = progress(t, from, to);
		return x * x;
	}

	static double easeIn(double x) {
		return x * x;
	}

	static double easeOut(double x) {
		return 1.0 - (1.0 - x) * (1.0 - x);
	}

	static double easeInOut(double x) {
		return x < 0.5 ? 2.0 * x * x : 1.0 - Math.pow(-2.0 * x + 2.0, 2.0) / 2.0;
	}

	/** Ease out with a small overshoot past the end value before settling. */
	static double backOut(double x, double overshoot) {
		double p = x - 1.0;
		return 1.0 + p * p * ((overshoot + 1.0) * p + overshoot);
	}

	/** Rises and falls once between {@code from} and {@code to}. */
	static double bell(double t, double from, double to) {
		return Math.sin(Math.PI * progress(t, from, to));
	}

	/** A decaying oscillation that starts at {@code start}. */
	static double ring(double t, double start, double amplitude, double frequency, double decay) {
		if (t <= start) {
			return 0.0;
		}
		double since = t - start;
		return amplitude * Math.exp(-decay * since) * Math.sin(frequency * since);
	}

	/** Absolute value of {@link #ring}, for bounces that must stay on one side of rest. */
	static double bounce(double t, double start, double amplitude, double frequency, double decay) {
		return Math.abs(ring(t, start, amplitude, frequency, decay));
	}

	static double decay(double t, double start, double rate) {
		return t <= start ? 1.0 : Math.exp(-rate * (t - start));
	}

	static double lerp(double from, double to, double x) {
		return from + (to - from) * x;
	}
}
