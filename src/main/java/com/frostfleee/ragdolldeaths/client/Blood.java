package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.BodyKind;
import com.frostfleee.ragdolldeaths.core.DeathProfile;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.physics.Body;
import java.util.Set;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Blood (beta, off by default): a spray from the part the killing blow struck, a few drops while the body is fresh,
 * and a splatter on a hard landing. Drawn only on this client with the game's own block particles, in the deep red of
 * a nether wart block.
 *
 * <p>Only things with blood bleed: bones, golems, slimes, blazes and breezes, shulkers, spirits (allays, vexes) and
 * mannequins never do, and nothing bleeds in lava.
 */
final class Blood {
	/** Mobs of these kinds have no blood in them. */
	private static final Set<String> BLOODLESS = Set.of(
			"minecraft:iron_golem", "minecraft:snow_golem", "minecraft:copper_golem", "minecraft:skeleton_horse",
			"minecraft:slime", "minecraft:magma_cube", "minecraft:sulfur_cube", "minecraft:shulker", "minecraft:blaze",
			"minecraft:breeze", "minecraft:mannequin", "minecraft:armor_stand", "minecraft:creaking");
	/** How long a fresh body keeps dripping, in ticks. */
	private static final int DRIP_TICKS = 40;
	/** A part whose speed changes by this much in a tick (blocks per second) splatters blood where it hits. */
	static final double SPLATTER = 9.0;

	private Blood() {
	}

	private static ParticleOptions drop() {
		return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState());
	}

	/** Whether this mob has blood to lose. */
	static boolean bleeds(LivingEntity entity, DeathProfile profile) {
		if (profile.kind() == BodyKind.CUBE || profile.kind() == BodyKind.SHULKER || profile.kind() == BodyKind.SPIRIT) {
			return false;
		}
		return switch (profile.special()) {
			case SKELETON, SNOW_GOLEM, BLAZE, BREEZE, MAGMA_CUBE -> false;
			default -> !Mobs.isSkeleton(entity) && !Mobs.isMannequin(entity) && !BLOODLESS.contains(EntityFilter.idOf(entity.getType()));
		};
	}

	/**
	 * The spray of the killing blow, from the part it struck (the one it set moving fastest), out along the way it was
	 * thrown. A harder blow sprays more; a death with no blow (poison, a /kill) only a little.
	 */
	static void blow(PhysicsRagdoll ragdoll, double strength) {
		Body struck = null;
		double fastest = -1.0;
		for (Body body : ragdoll.bodies) {
			double speed = body.vx * body.vx + body.vy * body.vy + body.vz * body.vz;
			if (speed > fastest) {
				fastest = speed;
				struck = body;
			}
		}
		if (struck == null) {
			return;
		}
		ragdoll.bleedingBody = java.util.Arrays.asList(ragdoll.bodies).indexOf(struck);
		spray(ragdoll.entity.level(), new Vec3(struck.x, struck.y, struck.z), new Vec3(struck.vx, struck.vy, struck.vz), strength);
	}

	/** The same spray for a body drawn by the classic animation, from the middle of the mob. */
	static void blow(LivingEntity entity, double strength) {
		Vec3 middle = entity.position().add(0.0, entity.getBbHeight() * 0.6, 0.0);
		spray(entity.level(), middle, entity.getDeltaMovement().scale(20.0), strength);
	}

	private static void spray(Level level, Vec3 at, Vec3 thrown, double strength) {
		RandomSource random = level.getRandom();
		int count = 6 + (int) (6.0 * Math.min(strength, 3.0));
		double speed = thrown.length();
		// Out with the blow, a little slower than the body, and spread.
		Vec3 out = speed > 0.5 ? thrown.scale(0.35 / speed) : Vec3.ZERO;
		ParticleOptions drop = drop();
		for (int i = 0; i < count; i++) {
			level.addParticle(drop, at.x + (random.nextDouble() - 0.5) * 0.3, at.y + (random.nextDouble() - 0.5) * 0.3, at.z + (random.nextDouble() - 0.5) * 0.3,
					out.x + (random.nextDouble() - 0.5) * 0.3, out.y + 0.1 + random.nextDouble() * 0.2, out.z + (random.nextDouble() - 0.5) * 0.3);
		}
	}

	/** After each physics tick: a fresh body drips from the wound for a couple of seconds, unless it is under water. */
	static void tick(PhysicsRagdoll ragdoll) {
		if (ragdoll.bleedTicks >= DRIP_TICKS || ragdoll.bleedingBody < 0) {
			return;
		}
		ragdoll.bleedTicks++;
		int i = ragdoll.bleedingBody;
		boolean wet = ragdoll.wet != null && ragdoll.wet[i];
		Level level = ragdoll.entity.level();
		RandomSource random = level.getRandom();
		if (wet || random.nextFloat() > 0.35F * (1.0F - ragdoll.bleedTicks / (float) DRIP_TICKS)) {
			return;
		}
		Body b = ragdoll.bodies[i];
		level.addParticle(drop(), b.x + (random.nextDouble() - 0.5) * 0.25, b.y, b.z + (random.nextDouble() - 0.5) * 0.25, 0.0, 0.0, 0.0);
	}

	/** A hard landing splatters a little blood out around the part that hit, low on the ground. */
	static void splatter(Level level, Body b, double hardness, RandomSource random) {
		int count = Math.min(8, (int) (hardness / 4.0));
		double low = b.y - b.halfHeight() + 0.1;
		ParticleOptions drop = drop();
		for (int i = 0; i < count; i++) {
			level.addParticle(drop, b.x + (random.nextDouble() - 0.5) * 0.5, low, b.z + (random.nextDouble() - 0.5) * 0.5,
					(random.nextDouble() - 0.5) * 0.35, 0.08 + random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.35);
		}
	}

	/** Whether this body bleeds now: Blood is on, it is a mob with blood, and it has not been in lava. */
	static boolean active(PhysicsRagdoll ragdoll, RagdollConfig config) {
		return config.blood && ragdoll.bleeds && !ragdoll.charred;
	}
}
