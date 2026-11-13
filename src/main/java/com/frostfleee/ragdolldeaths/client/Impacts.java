package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.JointRole;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.physics.Body;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a body sounds and looks like when it hits things, played only on this client with the game's own sounds and
 * particles: the thud of the block it lands on (a heavy one for the torso, a light tap for a limb), a puff of that
 * block's dust on a hard landing, a splash when it goes into water, and the clatter of loose bones for a skeleton
 * that has come apart.
 */
final class Impacts {
	/** A part whose speed changes by this much in one tick (blocks per second) has hit something worth hearing. */
	private static final double THUD = 5.0;
	private static final double DUST = 9.0;
	private static final double SPLASH = 3.0;
	/** A body makes at most one sound every this many ticks, so a tumble is a few thuds and not a drum roll. */
	private static final int QUIET_TICKS = 3;

	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	/** Called for every awake body after each tick of physics. */
	void afterTick(PhysicsRagdoll ragdoll, RagdollConfig config) {
		Body[] bodies = ragdoll.bodies;
		if (ragdoll.heard == null) {
			ragdoll.heard = new double[bodies.length * 3];
			ragdoll.wasWet = new boolean[bodies.length];
			remember(ragdoll);
			return;
		}
		Level level = ragdoll.entity.level();
		RandomSource random = level.getRandom();
		ragdoll.quiet--;
		// The hardest hit of the tick is the one that is heard.
		int loudest = -1;
		double hardest = THUD;
		for (int i = 0; i < bodies.length; i++) {
			Body b = bodies[i];
			boolean wet = ragdoll.wet != null && ragdoll.wet[i];
			if (wet && !ragdoll.wasWet[i]) {
				double speed = Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz);
				if (speed > SPLASH) {
					splash(level, b, speed, i == ragdoll.torso, config, random);
				}
			}
			ragdoll.wasWet[i] = wet;
			if (wet) {
				continue;
			}
			double dx = b.vx - ragdoll.heard[i * 3], dy = b.vy - ragdoll.heard[i * 3 + 1], dz = b.vz - ragdoll.heard[i * 3 + 2];
			double jolt = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (jolt > hardest) {
				hardest = jolt;
				loudest = i;
			}
		}
		if (loudest >= 0 && ragdoll.quiet <= 0) {
			Body b = bodies[loudest];
			BlockState ground = touched(level, b);
			if (ground != null) {
				ragdoll.quiet = QUIET_TICKS;
				if (PhysicsHost.DEBUG) {
					RagdollDeathsClient.LOGGER.info("[physics] impact {} on {}{}", String.format(java.util.Locale.ROOT, "%.1f", hardest), ground.getBlock().getDescriptionId(),
							ragdoll.special != null && ragdoll.special.isLooseBones() ? " (bones)" : "");
				}
				boolean heavy = loudest == ragdoll.torso || ragdoll.roles[loudest] == JointRole.BODY;
				if (config.impactSounds) {
					SoundType type = ground.getSoundType();
					// Loose bones clatter whatever they land on.
					SoundEvent sound = ragdoll.special != null && ragdoll.special.isLooseBones() ? SoundEvents.SKELETON_STEP : type.getFallSound();
					float volume = (float) Math.clamp(hardest / 22.0 * (heavy ? 1.0 : 0.55), 0.12, 1.0) * type.getVolume();
					float pitch = (heavy ? 0.75F : 1.15F) * type.getPitch() * (0.9F + 0.2F * random.nextFloat());
					level.playLocalSound(b.x, b.y, b.z, sound, SoundSource.NEUTRAL, volume, pitch, false);
				}
				if (config.impactParticles && hardest > DUST) {
					int puffs = Math.min(10, (int) (hardest / 3.0));
					BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
					double low = b.y - b.halfHeight();
					for (int k = 0; k < puffs; k++) {
						level.addParticle(dust, b.x + (random.nextDouble() - 0.5) * 0.6, low + 0.05, b.z + (random.nextDouble() - 0.5) * 0.6,
								(random.nextDouble() - 0.5) * 0.4, 0.15, (random.nextDouble() - 0.5) * 0.4);
					}
				}
			}
		}
		remember(ragdoll);
	}

	private static void remember(PhysicsRagdoll ragdoll) {
		for (int i = 0; i < ragdoll.bodies.length; i++) {
			Body b = ragdoll.bodies[i];
			ragdoll.heard[i * 3] = b.vx;
			ragdoll.heard[i * 3 + 1] = b.vy;
			ragdoll.heard[i * 3 + 2] = b.vz;
		}
	}

	/** The block the part is against: the one under it, or failing that one beside it. Null when it is in the open. */
	private BlockState touched(Level level, Body b) {
		double reach = b.halfHeight() + 0.15;
		double[][] around = {{0, -reach, 0}, {reach, 0, 0}, {-reach, 0, 0}, {0, 0, reach}, {0, 0, -reach}, {0, reach, 0}};
		for (double[] to : around) {
			BlockState state = level.getBlockState(cursor.set(b.x + to[0], b.y + to[1], b.z + to[2]));
			if (!state.isAir() && !state.getCollisionShape(level, cursor).isEmpty()) {
				return state;
			}
		}
		return null;
	}

	private static void splash(Level level, Body b, double speed, boolean heavy, RagdollConfig config, RandomSource random) {
		if (PhysicsHost.DEBUG) {
			RagdollDeathsClient.LOGGER.info("[physics] splash at speed {}", String.format(java.util.Locale.ROOT, "%.1f", speed));
		}
		if (config.impactSounds) {
			float volume = (float) Math.clamp(speed / 18.0 * (heavy ? 1.0 : 0.5), 0.1, 1.0);
			level.playLocalSound(b.x, b.y, b.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, volume, 0.9F + 0.3F * random.nextFloat(), false);
		}
		if (config.impactParticles) {
			int drops = Math.min(14, 3 + (int) speed);
			for (int k = 0; k < drops; k++) {
				level.addParticle(ParticleTypes.SPLASH, b.x + (random.nextDouble() - 0.5) * 0.7, b.y + 0.2, b.z + (random.nextDouble() - 0.5) * 0.7,
						(random.nextDouble() - 0.5) * 0.3, 0.2 + random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.3);
			}
		}
	}
}
