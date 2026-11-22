package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.BodyKind;
import com.frostfleee.ragdolldeaths.core.DeathProfile;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.core.Special;
import com.frostfleee.ragdolldeaths.physics.Body;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Blood (off by default), drawn only on this client:
 *
 * <ul>
 * <li><b>Particles:</b> a spray of drops and a fine red mist from the part the killing blow struck, drips from the wound
 * while the body is fresh, and a splatter on a hard landing. Drops fall, and leave a small stain where they land.</li>
 * <li><b>On the ground:</b> a pool spreads under the wound once the body comes to rest, and a hard landing leaves a
 * stain where it hit. Stains lie flat on the block, and fade away after about half a minute.</li>
 * <li><b>On the mobs themselves:</b> a dead body has blood on its skin around the wound and here and there (see
 * {@link Scorch#bleed}), and a living mob that is hurt shows more blood the more it is hurt (see {@link Wounds}).</li>
 * </ul>
 *
 * <p>Only things with blood bleed: bones, golems, slimes, blazes and breezes, shulkers, spirits (allays, vexes) and
 * mannequins never do, nothing bleeds in lava, and drops and stains do not form in water.
 */
final class Blood {
	/** Mobs of these kinds have no blood in them. */
	private static final Set<String> BLOODLESS = Set.of(
			"minecraft:iron_golem", "minecraft:snow_golem", "minecraft:copper_golem", "minecraft:skeleton_horse",
			"minecraft:slime", "minecraft:magma_cube", "minecraft:sulfur_cube", "minecraft:shulker", "minecraft:blaze",
			"minecraft:breeze", "minecraft:mannequin", "minecraft:armor_stand", "minecraft:creaking", "minecraft:allay",
			"minecraft:vex");
	/** How long a fresh body keeps dripping, in ticks. */
	private static final int DRIP_TICKS = 60;
	/** A part whose speed changes by this much in a tick (blocks per second) splatters blood where it hits. */
	static final double SPLATTER = 9.0;
	/** How long a stain lies on the ground, in ticks; it fades over the last part of that. */
	static final int STAIN_TICKS = 600;

	/** Fresh blood, darker blood, and the colour of a stain on the ground. */
	private static final int FRESH = 0x8E0B0B, DARK = 0x5C0606, STAINED = 0x630808;

	/** For the self-test: how many blood particles and stains were made. */
	static long particles, stains;

	private Blood() {
	}

	/** Whether this mob has blood to lose. */
	static boolean bleeds(LivingEntity entity, DeathProfile profile) {
		if (profile.kind() == BodyKind.CUBE || profile.kind() == BodyKind.SHULKER || profile.kind() == BodyKind.SPIRIT) {
			return false;
		}
		return bleedsAs(entity, profile.special());
	}

	/** The same for a living mob, which has no death profile. */
	static boolean bleeds(LivingEntity entity) {
		BodyKind kind = ProfileFactory.classify(entity);
		if (kind == BodyKind.CUBE || kind == BodyKind.SHULKER || kind == BodyKind.SPIRIT || Compat.isCubeMob(entity)) {
			return false;
		}
		return bleedsAs(entity, ProfileFactory.specialOf(entity));
	}

	private static boolean bleedsAs(LivingEntity entity, Special special) {
		return switch (special) {
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
		double hard = Math.min(strength, 3.0);
		int count = 8 + (int) (8.0 * hard);
		double speed = thrown.length();
		// Out with the blow, slower than the body, in a cone around that way.
		Vec3 out = speed > 0.5 ? thrown.scale(0.3 / speed) : Vec3.ZERO;
		double spread = 0.12 + 0.05 * hard;
		for (int i = 0; i < count; i++) {
			double kick = 0.5 + random.nextDouble();
			drop(at.x + (random.nextDouble() - 0.5) * 0.25, at.y + (random.nextDouble() - 0.5) * 0.25, at.z + (random.nextDouble() - 0.5) * 0.25,
					out.x * kick + (random.nextDouble() - 0.5) * spread * 2.0, out.y * kick + 0.08 + random.nextDouble() * 0.14,
					out.z * kick + (random.nextDouble() - 0.5) * spread * 2.0, random);
		}
		// A puff of fine mist where it struck.
		int mist = 2 + (int) (2.0 * hard);
		for (int i = 0; i < mist; i++) {
			mist(at.x + (random.nextDouble() - 0.5) * 0.2, at.y + (random.nextDouble() - 0.5) * 0.2, at.z + (random.nextDouble() - 0.5) * 0.2,
					out.x * 0.3 + (random.nextDouble() - 0.5) * 0.06, 0.01 + random.nextDouble() * 0.02, out.z * 0.3 + (random.nextDouble() - 0.5) * 0.06, random);
		}
	}

	private static void drop(double x, double y, double z, double vx, double vy, double vz, RandomSource random) {
		float size = 0.03F + random.nextFloat() * 0.035F;
		int colour = random.nextFloat() < 0.7F ? FRESH : DARK;
		if (BloodParticle.spawn(BloodParticle.DROP, "blood_drop", x, y, z, vx, vy, vz, size, colour, 1.0F, 50 + random.nextInt(30))) {
			particles++;
		}
	}

	private static void mist(double x, double y, double z, double vx, double vy, double vz, RandomSource random) {
		if (BloodParticle.spawn(BloodParticle.MIST, "blood_mist", x, y, z, vx, vy, vz, 0.12F + random.nextFloat() * 0.1F, FRESH, 0.55F, 10 + random.nextInt(8))) {
			particles++;
		}
	}

	/** A falling drop has reached the ground: most leave a small stain there. */
	static void dropLanded(Level level, double x, double y, double z, float size) {
		RandomSource random = level.getRandom();
		if (random.nextFloat() < 0.6F) {
			stain(level, x, y + 0.2, z, size * (2.0F + random.nextFloat() * 1.5F), random);
		}
	}

	/**
	 * A stain lying flat on the top of the block below the point, if there is solid ground within reach and no water
	 * or lava on it.
	 */
	static boolean stain(Level level, double x, double y, double z, float size, RandomSource random) {
		Vec3 from = new Vec3(x, y, z);
		BlockHitResult hit = level.clip(new ClipContext(from, from.add(0.0, -2.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
		if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) {
			return false;
		}
		BlockPos above = BlockPos.containing(hit.getLocation().x, hit.getLocation().y + 0.01, hit.getLocation().z);
		if (!level.getFluidState(above).isEmpty() || !level.getFluidState(hit.getBlockPos()).isEmpty()) {
			return false;
		}
		// Each stain sits a hair above the last, so overlapping ones do not flicker.
		double lift = 0.008 + (stains % 16) * 0.0006;
		String sprite = "blood_stain_" + random.nextInt(4);
		if (BloodParticle.spawn(BloodParticle.STAIN, sprite, hit.getLocation().x, hit.getLocation().y + lift, hit.getLocation().z, 0.0, 0.0, 0.0, size, STAINED, 0.92F,
				STAIN_TICKS - 80 + random.nextInt(160))) {
			stains++;
			return true;
		}
		return false;
	}

	/**
	 * After each physics tick: a fresh body drips from the wound for a few seconds unless it is under water, its skin
	 * is painted once its texture is known, and once it comes to rest a pool spreads under the wound.
	 */
	static void tick(PhysicsRagdoll ragdoll) {
		if (ragdoll.bleedingBody < 0) {
			return;
		}
		ragdoll.paintBlood();
		Level level = ragdoll.entity.level();
		RandomSource random = level.getRandom();
		int i = ragdoll.bleedingBody;
		Body b = ragdoll.bodies[i];
		boolean wet = ragdoll.wet != null && ragdoll.wet[i];
		ragdoll.bleedTicks++;
		if (ragdoll.bleedTicks < DRIP_TICKS && !wet && random.nextFloat() < 0.4F * (1.0F - ragdoll.bleedTicks / (float) DRIP_TICKS)) {
			drop(b.x + (random.nextDouble() - 0.5) * 0.2, b.y - b.halfHeight() * 0.5, b.z + (random.nextDouble() - 0.5) * 0.2,
					0.0, -0.02, 0.0, random);
		}
		// The pool: a few stains under the wound, spreading one after another once the body lies still.
		boolean still = b.vx * b.vx + b.vy * b.vy + b.vz * b.vz < 0.25;
		if (!wet && still && ragdoll.bleedTicks > 10 && ragdoll.pooled < 3 && ragdoll.bleedTicks % 12 == 0) {
			float size = 0.3F + 0.15F * ragdoll.pooled + random.nextFloat() * 0.15F;
			if (stain(level, b.x + (random.nextDouble() - 0.5) * 0.3, b.y + 0.3, b.z + (random.nextDouble() - 0.5) * 0.3, size, random)) {
				ragdoll.pooled++;
			} else {
				// Nothing to pool on (in the air, in water): it stops trying.
				ragdoll.pooled = 3;
			}
		}
	}

	/** A hard landing splatters blood out around the part that hit, low on the ground, and stains where it hit. */
	static void splatter(PhysicsRagdoll ragdoll, Level level, Body b, double hardness, RandomSource random) {
		int count = Math.min(10, (int) (hardness / 3.0));
		double low = b.y - b.halfHeight() + 0.1;
		for (int i = 0; i < count; i++) {
			drop(b.x + (random.nextDouble() - 0.5) * 0.4, low, b.z + (random.nextDouble() - 0.5) * 0.4,
					(random.nextDouble() - 0.5) * 0.3, 0.06 + random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.3, random);
		}
		stain(level, b.x, b.y + 0.2, b.z, 0.35F + (float) Math.min(hardness / 40.0, 0.35), random);
		ragdoll.bloodOn(java.util.Arrays.asList(ragdoll.bodies).indexOf(b));
	}

	/** Whether this body bleeds now: Blood is on, it is a mob with blood, and it has not been in lava. */
	static boolean active(PhysicsRagdoll ragdoll, RagdollConfig config) {
		return config.blood && ragdoll.bleeds && !ragdoll.charred;
	}
}
