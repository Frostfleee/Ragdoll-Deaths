package com.frostfleee.ragdolldeaths.core;

/**
 * The body's own motion through the world, stepped once per tick: gravity, air drag, collision with blocks
 * (floors, walls and ceilings), a bounce and a slide on landing, and buoyancy and current in water. A thrown,
 * falling or drifting body follows this instead of a precomputed path, so it lands where it really lands.
 *
 * <p>Positions are the body's feet relative to where it died, in blocks. Velocities are in blocks per tick.
 */
public final class BodyMotion {
	/** The blocks and fluids around the body. */
	public interface World {
		/**
		 * How far a box can move by (dx, dy, dz) before it hits a block, per axis, the way vanilla moves entities.
		 * Coordinates are absolute.
		 */
		double[] collide(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double dx, double dy, double dz);

		/** The fluid at a point, or {@link Fluid#NONE}. */
		Fluid fluidAt(double x, double y, double z);
	}

	/** A fluid sample: whether it is water or lava, and which way and how hard a current pushes. */
	public record Fluid(boolean water, boolean lava, double flowX, double flowZ) {
		public static final Fluid NONE = new Fluid(false, false, 0.0, 0.0);

		public boolean any() {
			return water || lava;
		}
	}

	/** How a kind of body moves through the air. */
	public record Air(double gravity, double horizontalDrag, double verticalDrag, double terminal) {
		/** Ordinary bodies: vanilla's living-entity gravity and drag. */
		public static final Air HEAVY = new Air(0.08, 0.91, 0.98, 3.0);
		public static final Air WINGED = new Air(0.05, 0.92, 0.92, 1.0);
		public static final Air SPIRIT = new Air(0.008, 0.78, 0.78, 0.08);
		public static final Air GHAST = new Air(0.06, 0.9, 0.98, 1.5);

		public static Air of(BodyKind kind) {
			return switch (kind) {
				case WINGED -> WINGED;
				case SPIRIT -> SPIRIT;
				case GHAST -> GHAST;
				default -> HEAVY;
			};
		}
	}

	public enum Phase {
		/** In the air, falling or flying. */
		AIR,
		/** Resting or sliding on the ground. */
		GROUND,
		/** In water or lava. */
		FLUID
	}

	/** Below this landing speed the body just stops; above it, it bounces. */
	private static final double BOUNCE_MIN_SPEED = 0.3;
	private static final double BOUNCE_KEEP = 0.28;
	private static final int MAX_BOUNCES = 2;
	/** Sliding on the ground keeps this much of the horizontal speed each tick, about vanilla's friction. */
	private static final double GROUND_FRICTION = 0.6;
	private static final double WALL_KEEP = 0.2;

	private static final double WATER_DRAG = 0.8;
	/** A current speeds the body up by this much per tick, to a top speed of about 0.1 blocks a tick. */
	private static final double CURRENT_PUSH = 0.025;
	private static final double FLOAT_LIFT = 0.012;
	private static final double SINK_PULL = 0.006;
	/** Out of the water while floating, the body drops back with this gravity, so it bobs at the surface. */
	private static final double SURFACE_GRAVITY = 0.03;
	private static final double LAVA_DRAG = 0.5;
	private static final double LAVA_SINK = 0.015;
	/** Hitting water slows a falling body down this much. */
	private static final double SPLASH_KEEP = 0.35;

	private final Air air;
	private final double halfWidth;
	private final double boxHeight;
	private final WaterBehavior water;
	private final double bounce;
	private final boolean driftOnly;

	private double x;
	private double y;
	private double z;
	private double prevX;
	private double prevY;
	private double prevZ;
	private double vx;
	private double vy;
	private double vz;
	private Phase phase = Phase.AIR;
	private int bounces;
	private int ticks;
	private int landedTick = -1;
	private DeathCause landedIn = DeathCause.GROUND;
	private boolean justLanded;

	/**
	 * @param air how it moves through the air
	 * @param width the body's width; the collision box is kept small enough to fit through a one-block gap
	 * @param height the body's height
	 * @param bounce the configured bounce strength, 0 to 2
	 * @param driftOnly a body that died in water: it only drifts sideways with the current, its rise or sink is
	 *                  animated
	 */
	public BodyMotion(Air air, double width, double height, WaterBehavior water, double bounce, boolean driftOnly,
			double vx, double vy, double vz) {
		this.air = air;
		this.halfWidth = Math.clamp(width * 0.5, 0.15, 0.45);
		this.boxHeight = Math.clamp(Math.min(width, height), 0.2, 0.9);
		this.water = water;
		this.bounce = bounce;
		this.driftOnly = driftOnly;
		this.vx = vx;
		this.vy = driftOnly ? 0.0 : vy;
		this.vz = vz;
		if (driftOnly) {
			phase = Phase.FLUID;
		}
	}

	/**
	 * Advances one tick. {@code ax, ay, az} is where the body died; {@code driftY} is the animated rise or sink of
	 * a body that died in water, ignored otherwise.
	 */
	public void step(World world, double ax, double ay, double az, double driftY) {
		prevX = x;
		prevY = y;
		prevZ = z;
		justLanded = false;
		ticks++;
		if (driftOnly) {
			y = driftY;
		}

		// Sampled just above the feet, so a body floating at the surface still feels the current.
		Fluid fluid = world.fluidAt(ax + x, ay + y + 0.05, az + z);
		if (fluid.any() && phase != Phase.FLUID) {
			enterFluid(fluid);
		} else if (!fluid.any() && phase == Phase.FLUID && !driftOnly) {
			phase = Phase.AIR;
		}

		switch (phase) {
			case AIR -> {
				vy = Math.max(-air.terminal(), (vy - air.gravity()) * air.verticalDrag());
				vx *= air.horizontalDrag();
				vz *= air.horizontalDrag();
			}
			case GROUND -> {
				vx *= GROUND_FRICTION;
				vz *= GROUND_FRICTION;
				vy = Math.max(-air.terminal(), vy - air.gravity());
			}
			case FLUID -> {
				double drag = fluid.lava() ? LAVA_DRAG : WATER_DRAG;
				vx = vx * drag + fluid.flowX() * CURRENT_PUSH;
				vz = vz * drag + fluid.flowZ() * CURRENT_PUSH;
				if (fluid.lava()) {
					vy = -LAVA_SINK;
				} else {
					vy = vy * drag + buoyancy(world, ax, ay, az);
				}
			}
		}
		move(world, ax, ay, az);
	}

	private double buoyancy(World world, double ax, double ay, double az) {
		return switch (water) {
			case FLOAT -> world.fluidAt(ax + x, ay + y + boxHeight, az + z).water() ? FLOAT_LIFT : -SURFACE_GRAVITY;
			case SINK -> -SINK_PULL;
			case NEUTRAL -> 0.0;
		};
	}

	/**
	 * Moves the body to where the real, server-simulated entity is, instead of simulating it. While the entity
	 * still exists its fall is exact (vanilla's own physics and collisions), so the body lands exactly where the
	 * mob does; {@link #step} takes over from the same place once the entity is gone. {@code x, y, z} and the
	 * velocity are relative to where it died, like the rest of the motion.
	 */
	public void follow(World world, double ax, double ay, double az, double x, double y, double z, double vx, double vy, double vz, boolean onGround) {
		prevX = this.x;
		prevY = this.y;
		prevZ = this.z;
		justLanded = false;
		ticks++;
		this.x = x;
		this.y = y;
		this.z = z;
		this.vx = vx;
		this.vy = vy;
		this.vz = vz;
		Fluid fluid = world.fluidAt(ax + x, ay + y + 0.05, az + z);
		if (fluid.any() && phase != Phase.FLUID) {
			phase = Phase.FLUID;
			land(fluid.lava() ? DeathCause.LAVA : DeathCause.WATER);
		} else if (onGround && phase == Phase.AIR) {
			phase = Phase.GROUND;
			land(DeathCause.GROUND);
		}
	}

	/**
	 * Takes back a landing: the body stopped on something (the edge of a ledge) but has started falling again.
	 * The next touchdown counts as its landing.
	 */
	public void unland() {
		landedTick = -1;
		landedIn = DeathCause.GROUND;
		justLanded = false;
		phase = Phase.AIR;
	}

	private void enterFluid(Fluid fluid) {
		phase = Phase.FLUID;
		vy *= SPLASH_KEEP;
		land(fluid.lava() ? DeathCause.LAVA : DeathCause.WATER);
	}

	private void move(World world, double ax, double ay, double az) {
		double dy = driftOnly ? 0.0 : vy;
		double[] free = world.collide(ax + x - halfWidth, ay + y, az + z - halfWidth, ax + x + halfWidth, ay + y + boxHeight, az + z + halfWidth, vx, dy, vz);
		x += free[0];
		y += free[1];
		z += free[2];
		if (Math.abs(free[0] - vx) > 1.0E-7) {
			vx *= -WALL_KEEP;
		}
		if (Math.abs(free[2] - vz) > 1.0E-7) {
			vz *= -WALL_KEEP;
		}
		if (driftOnly || Math.abs(free[1] - dy) < 1.0E-7) {
			if (phase == Phase.GROUND && !driftOnly && free[1] < -1.0E-4) {
				phase = Phase.AIR;
			}
			return;
		}
		if (dy > 0.0) {
			vy = 0.0;
			return;
		}
		// Stopped while moving down: the body reached the floor.
		if (phase == Phase.AIR && -vy > BOUNCE_MIN_SPEED && bounces < MAX_BOUNCES && bounce > 0.0) {
			bounces++;
			vy = -vy * BOUNCE_KEEP * Math.min(bounce, 1.5);
			land(DeathCause.GROUND);
			return;
		}
		vy = 0.0;
		if (phase != Phase.FLUID) {
			phase = Phase.GROUND;
			land(DeathCause.GROUND);
		}
	}

	private void land(DeathCause in) {
		if (landedTick < 0) {
			landedTick = ticks;
			landedIn = in;
			justLanded = true;
		}
	}

	public double x() {
		return x;
	}

	public double y() {
		return y;
	}

	public double z() {
		return z;
	}

	/** Where the body is between the last tick and this one. */
	public double x(double partial) {
		return prevX + (x - prevX) * partial;
	}

	public double y(double partial) {
		return prevY + (y - prevY) * partial;
	}

	public double z(double partial) {
		return prevZ + (z - prevZ) * partial;
	}

	/** A body that died in water: it only drifts sideways, its rise or sink is animated. */
	public boolean isDriftOnly() {
		return driftOnly;
	}

	public double vy() {
		return vy;
	}

	public Phase phase() {
		return phase;
	}

	public boolean landed() {
		return landedTick >= 0;
	}

	/** True only on the tick the body first touched the ground or a fluid. */
	public boolean justLanded() {
		return justLanded;
	}

	/** The tick it first touched the ground or a fluid, counted from the start of the motion; -1 if still falling. */
	public int landedTick() {
		return landedTick;
	}

	public DeathCause landedIn() {
		return landedIn;
	}

	public int ticks() {
		return ticks;
	}
}
