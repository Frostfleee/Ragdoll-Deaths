package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.physics.PhysicsWorld;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.Level;

/**
 * Runs the physics for every ragdoll in the level the player is in. Physics advances only in the client tick,
 * three fixed steps of 1/60 s each; frames in between are drawn by blending the recorded steps, so drawing a frame
 * never steps physics or looks at the world.
 */
final class PhysicsHost {
	static final PhysicsHost INSTANCE = new PhysicsHost();
	static final boolean DEBUG = System.getenv("RAGDOLL_DEBUG") != null;

	/** How often, in ticks, sleeping bodies check whether the blocks around them changed. */
	private static final int WAKE_CHECK_TICKS = 4;
	private static final int REPORT_TICKS = 100;

	private final PhysicsWorld world = new PhysicsWorld();
	private final FluidForces fluids = new FluidForces();
	private final Impacts impacts = new Impacts();
	/** Physics time per tick, in milliseconds, above which the solve is lightened, and above which bodies are let go. */
	private static final double SOFT_BUDGET_MS = 2.0, HARD_BUDGET_MS = 6.0;
	private static final int STRAIN_TICKS = 20, OVERRUN_TICKS = 5;
	private int strained, overrun;
	/** How many bodies have been let go early because the physics ran over its time; the self-test reports it. */
	static int retiredForTime;
	private final List<PhysicsRagdoll> ragdolls = new ArrayList<>();
	private Level level;
	private long ticks;
	private double spentMs;
	private double worstMs;
	private int measured;

	private PhysicsHost() {
	}

	void add(PhysicsRagdoll ragdoll) {
		Level in = ragdoll.entity.level();
		if (in != level) {
			clear();
			level = in;
			world.colliders = new BlockColliders(in);
		}
		world.add(ragdoll.island);
		ragdolls.add(ragdoll);
		if (DEBUG) {
			var torso = ragdoll.bodies[ragdoll.torso];
			RagdollDeathsClient.LOGGER.info("[physics] built {} parts for {} at torso ({}, {}, {}), entity at {}; {}; torso speed ({}, {}, {})", ragdoll.bodies.length,
					EntityFilter.idOf(ragdoll.entity.getType()), fmt(torso.x), fmt(torso.y), fmt(torso.z), ragdoll.entity.position(), ragdoll.blow,
					fmt(torso.vx), fmt(torso.vy), fmt(torso.vz));
		}
	}

	void remove(PhysicsRagdoll ragdoll) {
		if (ragdolls.remove(ragdoll)) {
			world.remove(ragdoll.island);
			ragdoll.closeScorches();
			if (DEBUG) {
				StackTraceElement[] trace = Thread.currentThread().getStackTrace();
				RagdollDeathsClient.LOGGER.info("[physics] removed {} (lava ticks {}), via {} <- {}", EntityFilter.idOf(ragdoll.entity.getType()), ragdoll.lavaTicks,
						trace.length > 3 ? trace[3].getMethodName() : "?", trace.length > 4 ? trace[4].getMethodName() : "?");
			}
		}
	}

	void clear() {
		for (PhysicsRagdoll ragdoll : ragdolls) {
			ragdoll.closeScorches();
		}
		ragdolls.clear();
		world.clear();
		level = null;
	}

	int count() {
		return ragdolls.size();
	}

	List<PhysicsRagdoll> ragdolls() {
		return ragdolls;
	}

	void tick() {
		if (ragdolls.isEmpty()) {
			return;
		}
		long start = System.nanoTime();
		ticks++;
		world.worldStamp++;
		RagdollConfig config = ConfigStore.get();
		world.substeps = switch (config.physicsQuality) {
			case LOW -> 2;
			case MEDIUM -> 3;
			case HIGH -> 4;
		};
		// On Low, bodies fall through each other instead of piling up: checking every body against every other is
		// the costliest part of a crowd.
		world.bodyCollisions = config.physicsQuality != RagdollConfig.Quality.LOW;
		if (strained > 0) {
			// The last ticks ran over budget: the lightest solve until the crowd has settled.
			world.substeps = 2;
			world.bodyCollisions = false;
		}
		for (int i = 0; i < ragdolls.size(); i++) {
			PhysicsRagdoll ragdoll = ragdolls.get(i);
			ragdoll.startTick();
			// A block broken under a resting body, or placed into it, sets it moving again; so does water flowing onto it.
			if (ragdoll.island.asleep && (ticks + i) % WAKE_CHECK_TICKS == 0 && (world.worldChanged(ragdoll.island) || fluids.moving(ragdoll))) {
				ragdoll.island.wake();
			}
			// Living players and mobs walking into the body push it along (and wake it); nothing pushes back. Who is near
			// is found once a tick; the pushes are given before each step, below.
			ragdoll.walkers = null;
			if (config.pushBodies && !ragdoll.failed) {
				BodyPushing.find(ragdoll);
				BodyPushing.push(ragdoll, 0.0);
			}
			if (!ragdoll.island.asleep) {
				fluids.apply(ragdoll, ConfigStore.get());
			}
		}
		for (int step = 1; step <= PhysicsRagdoll.STEPS_PER_TICK; step++) {
			if (step > 1) {
				for (int i = 0; i < ragdolls.size(); i++) {
					PhysicsRagdoll ragdoll = ragdolls.get(i);
					if (ragdoll.walkers != null && !ragdoll.failed) {
						BodyPushing.push(ragdoll, (step - 1) / (double) PhysicsRagdoll.STEPS_PER_TICK);
					}
				}
			}
			world.step();
			for (int i = 0; i < ragdolls.size(); i++) {
				PhysicsRagdoll ragdoll = ragdolls.get(i);
				if (ragdoll.island.failed) {
					if (!ragdoll.failed) {
						var b = ragdoll.bodies[ragdoll.torso];
						RagdollDeathsClient.LOGGER.warn("The physics of a {} body broke down (its state became invalid); it is removed. Torso at ({}, {}, {}), lift {}, drag {}",
								EntityFilter.idOf(ragdoll.entity.getType()), b.x, b.y, b.z, b.lift, b.fluidDrag);
					}
					ragdoll.failed = true;
				} else {
					ragdoll.capture(step);
				}
			}
		}
		for (int i = 0; i < ragdolls.size(); i++) {
			PhysicsRagdoll ragdoll = ragdolls.get(i);
			if (ragdoll.special != null && !ragdoll.failed) {
				ragdoll.special.afterTick(ragdoll);
			}
			if (!ragdoll.failed && Blood.active(ragdoll, config)) {
				Blood.tick(ragdoll);
			}
			if (!ragdoll.failed && !ragdoll.island.asleep && (config.impactSounds || config.impactParticles || config.blood)) {
				impacts.afterTick(ragdoll, config);
			}
		}
		budget((System.nanoTime() - start) / 1.0e6);
		if (DEBUG) {
			double ms = (System.nanoTime() - start) / 1.0e6;
			spentMs += ms;
			worstMs = Math.max(worstMs, ms);
			if (++measured == REPORT_TICKS) {
				int awake = 0;
				for (PhysicsRagdoll ragdoll : ragdolls) {
					awake += ragdoll.island.asleep ? 0 : 1;
				}
				RagdollDeathsClient.LOGGER.info("[physics] {} bodies ({} awake): average {} ms per tick, worst {} ms over the last {} ticks",
						ragdolls.size(), awake, fmt(spentMs / measured), fmt(worstMs), measured);
				spentMs = worstMs = 0.0;
				measured = 0;
			}
		}
	}

	/**
	 * Keeps physics from eating the frame. A tick over the soft budget switches the next ticks to the lightest
	 * solve. If ticks stay far over even so, the oldest body is let go, one a tick, until they fit: a body vanishing
	 * a little early is better than the game stuttering.
	 */
	private void budget(double ms) {
		strained = ms > SOFT_BUDGET_MS ? STRAIN_TICKS : Math.max(0, strained - 1);
		overrun = ms > HARD_BUDGET_MS ? overrun + 1 : 0;
		if (overrun >= OVERRUN_TICKS) {
			boolean keepPlayers = ConfigStore.get().playerBodyUntilRespawn;
			for (PhysicsRagdoll ragdoll : ragdolls) {
				// a player's body kept until they respawn is not let go (see Player Body Until Respawn)
				if (!ragdoll.retired && !(keepPlayers && ragdoll.entity instanceof net.minecraft.world.entity.player.Player)) {
					ragdoll.retired = true;
					retiredForTime++;
					break;
				}
			}
		}
	}

	private static String fmt(double value) {
		return String.format(java.util.Locale.ROOT, "%.3f", value);
	}
}
