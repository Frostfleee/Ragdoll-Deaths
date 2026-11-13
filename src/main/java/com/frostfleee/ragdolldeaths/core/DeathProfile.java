package com.frostfleee.ragdolldeaths.core;

public record DeathProfile(
		BodyKind kind,
		Special special,
		Rig rig,
		double legRatio,
		DeathCause cause,
		HitDirection hit,
		double fallDirX,
		double fallDirZ,
		double forwardX,
		double forwardZ,
		double width,
		double height,
		double bodyHalfWidth,
		double heaviness,
		boolean baby,
		boolean flipsOnBack,
		boolean alreadySideways,
		double velocityX,
		double velocityY,
		double velocityZ,
		double maxPush,
		double floorDrop,
		double waterRise,
		double waterSink,
		double attachX,
		double attachY,
		double attachZ,
		boolean ghostCapable,
		boolean freeFall,
		boolean blasted,
		boolean calmWings,
		Ground ground,
		boolean simulated,
		double landedAt,
		DeathCause landedIn,
		double fallLimit,
		long seed) {

	public static final double DEFAULT_LEG_RATIO = 0.4;

	private static final double HEAVY_MASS_START = 1.5;
	private static final double HEAVY_MASS_RANGE = 4.5;

	/**
	 * The same body, switched to falling from where it is now, e.g. after the killing blow knocked it off a ledge.
	 * It is simulated from here on, and the ground it was going to lie on no longer applies.
	 */
	public DeathProfile asFreeFall(double floorDrop, double velocityX, double velocityY, double velocityZ, double maxPush) {
		return new DeathProfile(kind, special, rig, legRatio, cause, hit, fallDirX, fallDirZ, forwardX, forwardZ, width, height,
				bodyHalfWidth, heaviness, baby, flipsOnBack, alreadySideways, velocityX, velocityY, velocityZ, Math.max(0.0, maxPush),
				Math.min(0.0, floorDrop), waterRise, waterSink, attachX, attachY, attachZ, ghostCapable, true, false, calmWings, Ground.FLAT, true, Double.NaN, DeathCause.GROUND, fallLimit, seed);
	}

	/** The same body with the shape of the ground it rests on, measured where it settled. */
	public DeathProfile withGround(Ground ground) {
		return new DeathProfile(kind, special, rig, legRatio, cause, hit, fallDirX, fallDirZ, forwardX, forwardZ, width, height,
				bodyHalfWidth, heaviness, baby, flipsOnBack, alreadySideways, velocityX, velocityY, velocityZ, maxPush,
				floorDrop, waterRise, waterSink, attachX, attachY, attachZ, ghostCapable, freeFall, blasted, calmWings,
				ground == null ? Ground.FLAT : ground, simulated, landedAt, landedIn, fallLimit, seed);
	}

	/** The same body after it first touched the ground or a fluid, at choreography time {@code landedAt}. */
	public DeathProfile withLanding(double landedAt, DeathCause landedIn) {
		return new DeathProfile(kind, special, rig, legRatio, cause, hit, fallDirX, fallDirZ, forwardX, forwardZ, width, height,
				bodyHalfWidth, heaviness, baby, flipsOnBack, alreadySideways, velocityX, velocityY, velocityZ, maxPush,
				floorDrop, waterRise, waterSink, attachX, attachY, attachZ, ghostCapable, freeFall, blasted, calmWings, ground,
				simulated, landedAt, landedIn, fallLimit, seed);
	}

	/** The same body allowed to keep falling for up to {@code fallLimit} ticks before it vanishes in the air. */
	public DeathProfile withFallLimit(double fallLimit) {
		return new DeathProfile(kind, special, rig, legRatio, cause, hit, fallDirX, fallDirZ, forwardX, forwardZ, width, height,
				bodyHalfWidth, heaviness, baby, flipsOnBack, alreadySideways, velocityX, velocityY, velocityZ, maxPush,
				floorDrop, waterRise, waterSink, attachX, attachY, attachZ, ghostCapable, freeFall, blasted, calmWings, ground,
				simulated, landedAt, landedIn, fallLimit, seed);
	}

	/** The body follows its own path from the death position instead of the synced entity position. */
	public boolean followsTrajectory() {
		return kind.airborne() || freeFall || blasted || simulated;
	}

	/** A simulated body that has touched the ground or a fluid. */
	public boolean hasLanded() {
		return !Double.isNaN(landedAt);
	}

	public double legLength() {
		return legRatio * height;
	}

	/** 0 for ordinary mobs, rising to 1 for golem- or ravager-sized bodies. */
	public static double heavinessOf(double width, double height) {
		double mass = width * width * height;
		return Math.clamp((mass - HEAVY_MASS_START) / HEAVY_MASS_RANGE, 0.0, 1.0);
	}

	public static Builder builder(BodyKind kind) {
		return new Builder(kind);
	}

	public static final class Builder {
		private final BodyKind kind;
		private Special special = Special.NONE;
		private Rig rig = Rig.OTHER;
		private double legRatio = DEFAULT_LEG_RATIO;
		private DeathCause cause = DeathCause.GROUND;
		private HitDirection hit = HitDirection.NONE;
		private float bodyYawDegrees;
		private double width = 0.6;
		private double height = 1.8;
		private double bodyHalfWidth = Double.NaN;
		private boolean baby;
		private boolean flipsOnBack;
		private boolean alreadySideways;
		private double velocityX;
		private double velocityY;
		private double velocityZ;
		private double maxPush = Double.MAX_VALUE;
		private double floorDrop;
		private double waterRise;
		private double waterSink;
		private double attachX;
		private double attachY = -1.0;
		private double attachZ;
		private boolean ghostCapable;
		private boolean freeFall;
		private boolean blasted;
		private boolean calmWings;
		private Ground ground = Ground.FLAT;
		private boolean simulated;
		private double fallLimit = RagdollConfig.DEFAULT_GHOST_TICKS;
		private long seed;

		private Builder(BodyKind kind) {
			this.kind = kind;
		}

		public Builder special(Special special) {
			this.special = special;
			return this;
		}

		public Builder rig(Rig rig, double legRatio) {
			this.rig = rig;
			this.legRatio = Math.clamp(legRatio, 0.1, 0.65);
			return this;
		}

		public Builder cause(DeathCause cause) {
			this.cause = cause;
			return this;
		}

		public Builder hit(HitDirection hit, float bodyYawDegrees) {
			this.hit = hit;
			this.bodyYawDegrees = bodyYawDegrees;
			return this;
		}

		public Builder size(double width, double height) {
			this.width = width;
			this.height = height;
			return this;
		}

		/** Half the torso's real side-to-side width in blocks; NaN when unknown. */
		public Builder bodyHalfWidth(double bodyHalfWidth) {
			this.bodyHalfWidth = bodyHalfWidth;
			return this;
		}

		public Builder baby(boolean baby) {
			this.baby = baby;
			return this;
		}

		public Builder flipsOnBack(boolean flipsOnBack) {
			this.flipsOnBack = flipsOnBack;
			return this;
		}

		public Builder alreadySideways(boolean alreadySideways) {
			this.alreadySideways = alreadySideways;
			return this;
		}

		public Builder velocity(double x, double y, double z) {
			this.velocityX = x;
			this.velocityY = y;
			this.velocityZ = z;
			return this;
		}

		public Builder maxPush(double maxPush) {
			this.maxPush = maxPush;
			return this;
		}

		public Builder floorDrop(double floorDrop) {
			this.floorDrop = Math.min(0.0, floorDrop);
			return this;
		}

		public Builder water(double rise, double sink) {
			this.waterRise = Math.max(0.0, rise);
			this.waterSink = Math.max(0.0, sink);
			return this;
		}

		public Builder attach(double x, double y, double z) {
			this.attachX = x;
			this.attachY = y;
			this.attachZ = z;
			return this;
		}

		public Builder ghostCapable(boolean ghostCapable) {
			this.ghostCapable = ghostCapable;
			return this;
		}

		/** The mob died in mid-air above {@link #floorDrop}; its body falls and only plays its death on landing. */
		public Builder freeFall(boolean freeFall) {
			this.freeFall = freeFall;
			return this;
		}

		/**
		 * The body was launched by an explosion with {@link #velocity}: it flies in an arc above {@link #floorDrop},
		 * playing its death on the way, and lands with a bounce and a short slide.
		 */
		public Builder blasted(boolean blasted) {
			this.blasted = blasted;
			return this;
		}

		/**
		 * The body's position comes from a {@link BodyMotion} stepped through the world, not from the precomputed
		 * paths; its landing is reported with {@link DeathProfile#withLanding}.
		 */
		public Builder simulated(boolean simulated) {
			this.simulated = simulated;
			return this;
		}

		public Builder fallLimit(double fallLimit) {
			this.fallLimit = fallLimit;
			return this;
		}

		public Builder ground(Ground ground) {
			this.ground = ground == null ? Ground.FLAT : ground;
			return this;
		}

		/** Wings settle to rest and go still instead of fluttering. */
		public Builder calmWings(boolean calmWings) {
			this.calmWings = calmWings;
			return this;
		}

		public Builder seed(long seed) {
			this.seed = seed;
			return this;
		}

		public DeathProfile build() {
			double[] fall = hit.fallDirection(bodyYawDegrees, seed);
			double yaw = Math.toRadians(bodyYawDegrees);
			return new DeathProfile(kind, special, rig, legRatio, cause, hit, fall[0], fall[1], -Math.sin(yaw), Math.cos(yaw), width, height,
					bodyHalfWidth > 0.0 ? bodyHalfWidth : Double.NaN,
					heavinessOf(width, height), baby, flipsOnBack, alreadySideways, velocityX, velocityY, velocityZ,
					Math.max(0.0, maxPush), floorDrop, waterRise, waterSink, attachX, attachY, attachZ, ghostCapable, freeFall, blasted, calmWings, ground, simulated, Double.NaN, DeathCause.GROUND, fallLimit, seed);
		}
	}
}
