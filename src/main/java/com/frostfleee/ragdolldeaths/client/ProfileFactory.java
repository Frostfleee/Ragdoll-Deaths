package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.BodyKind;
import com.frostfleee.ragdolldeaths.core.DeathCause;
import com.frostfleee.ragdolldeaths.core.DeathProfile;
import com.frostfleee.ragdolldeaths.core.HitDirection;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.core.Special;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class ProfileFactory {
	private static final double WATER_FLOOR_SCAN = 16.0;
	private static final int WATER_SURFACE_SCAN = 12;
	private static final double MAX_SPEED = 0.8;
	private static final double WALL_MARGIN = 0.05;
	private static final float BACK_FLIP_DEGREES = 135.0F;
	static final double FREE_FALL_MIN_DROP = 1.5;
	static final double FLOOR_SCAN = 48.0;
	/** RAGDOLL_DEBUG=1 logs how each death was read, for tracking down a death that looks wrong. */
	static final boolean DEBUG = System.getenv("RAGDOLL_DEBUG") != null;

	/**
	 * An explosion's throw, in blocks per tick: sideways from a base speed up to base plus extra right at the blast,
	 * and up the same way. A close blast throws a body around 35 blocks, a distant one around 10.
	 */
	private static final double BLAST_BASE_SPEED = 1.0;
	private static final double BLAST_EXTRA_SPEED = 2.6;
	private static final double BLAST_BASE_RISE = 0.55;
	private static final double BLAST_EXTRA_RISE = 0.9;
	/** Keeps a body right above the blast from rocketing out of sight: about 15 blocks up at most. */
	private static final double BLAST_MAX_RISE = 1.6;
	/** Bodies this far from the blast's center, in blocks, get the weakest throw. */
	private static final double BLAST_REACH = 8.0;
	private static final double BLAST_MIN_CLOSENESS = 0.2;
	/** The server's push at point-blank range, used to judge closeness when the blast's position is unknown. */
	private static final double BLAST_FULL_PUSH = 0.8;
	/** A falling body with a floor somewhere below it stays until it lands, for up to this many ticks. */
	private static final double FALL_LIMIT_WITH_FLOOR = 200.0;
	private static final double FALL_LIMIT_SCAN = 96.0;
	/** A thrown rider leaves its seat with this push away from the blow, and this hop up. */
	private static final double RIDER_THROW = 0.16;
	private static final double RIDER_HOP = 0.14;

	private ProfileFactory() {
	}

	static DeathProfile create(LivingEntityRenderer renderer, LivingEntity entity, RagdollConfig config) {
		BodyKind kind = classify(entity);
		JointRig rig = JointRig.of(Compat.root(renderer.getModel()));
		DeathCause cause = causeOf(entity);
		float bodyYaw = entity.yBodyRot;
		Vec3 launch = blastLaunch(entity, kind, cause, config);
		HitDirection hit = hitDirection(entity, bodyYaw, launch);
		double width = entity.getBbWidth();
		double height = entity.getBbHeight();
		Vec3 position = entity.position();
		Vec3 velocity = clampLength(position.subtract(entity.xo, entity.yo, entity.zo), MAX_SPEED);

		DeathProfile.Builder builder = DeathProfile.builder(kind)
				.seed(entity.getUUID().getLeastSignificantBits() ^ entity.getId())
				.special(specialOf(entity))
				.cause(cause)
				.hit(hit, bodyYaw)
				.size(width, height)
				.baby(entity.isBaby())
				.rig(rig.rig, rig.legRatio)
				.bodyHalfWidth(rig.bodyHalfWidthRatio * height)
				.flipsOnBack(Compat.flipDegrees(renderer, entity) >= BACK_FLIP_DEGREES)
				.alreadySideways(Mobs.isFish(entity) && !entity.isInWater())
				.ghostCapable(kind == BodyKind.GHAST)
				.calmWings(!Mobs.isBee(entity));

		Level level = entity.level();
		Vec3 center = position.add(0.0, height / 2.0, 0.0);
		double fallLimit = fallLimit(level, entity, position, config);
		if (launch != null) {
			builder.blasted(true)
					.simulated(true)
					.fallLimit(fallLimit)
					.velocity(launch.x, launch.y, launch.z)
					.maxPush(clearance(level, entity, center, launch.x, launch.z, horizontalReach(launch), width))
					.floorDrop(floorDistance(level, entity, position, FLOOR_SCAN, ClipContext.Fluid.ANY));
		} else if (EntityFilter.isMountedRider(entity) || EntityFilter.isVehicleRider(entity)) {
			Vec3 thrown = EntityFilter.isVehicleRider(entity) ? clampLength(carried(entity), MAX_SPEED) : riderThrow(entity, hit, bodyYaw);
			builder.freeFall(true)
					.simulated(true)
					.fallLimit(fallLimit)
					.velocity(thrown.x, thrown.y, thrown.z)
					.maxPush(clearance(level, entity, center, thrown.x, thrown.z, horizontalReach(thrown), width))
					.floorDrop(floorDistance(level, entity, position, FLOOR_SCAN, ClipContext.Fluid.ANY));
		} else if (kind.airborne()) {
			builder.simulated(true)
					.fallLimit(fallLimit)
					.velocity(velocity.x, velocity.y, velocity.z)
					.maxPush(clearance(level, entity, center, velocity.x, velocity.z, horizontalReach(velocity), width))
					.floorDrop(floorDistance(level, entity, position, FLOOR_SCAN, ClipContext.Fluid.ANY));
		} else {
			double drop = canFreeFall(entity) ? floorDistance(level, entity, position, FLOOR_SCAN, ClipContext.Fluid.ANY) : 0.0;
			if (drop < -FREE_FALL_MIN_DROP) {
				builder.freeFall(true)
						.simulated(true)
						.fallLimit(fallLimit)
						.velocity(velocity.x, velocity.y, velocity.z)
						.maxPush(clearance(level, entity, center, velocity.x, velocity.z, horizontalReach(velocity), width))
						.floorDrop(drop);
			} else {
				double[] fall = hit.fallDirection(bodyYaw, 0L);
				builder.maxPush(clearance(level, entity, center, fall[0], fall[1], 1.0 + width, width));
				if (cause == DeathCause.WATER) {
					// The body drifts with the current as it floats up or sinks.
					builder.simulated(true).velocity(velocity.x, 0.0, velocity.z);
				}
				if (cause != DeathCause.WATER && cause != DeathCause.LAVA && kind != BodyKind.SHULKER) {
					// Measured once where the body will lie; falling and blasted bodies are measured where they land.
					// From the floor itself, since a mob that died mid-hop is still a little above it.
					double floor = GroundProbe.floorBelow(level, entity, position, FREE_FALL_MIN_DROP);
					builder.ground(GroundProbe.around(level, entity, position.x, floor, position.z, height));
				}
			}
		}

		if (entity.isInWater()) {
			builder.water(waterRise(level, entity, width), -floorDistance(level, entity, position, WATER_FLOOR_SCAN, ClipContext.Fluid.NONE));
		}

		if (entity instanceof Shulker shulker) {
			Direction face = shulker.getAttachFace();
			builder.attach(face.getStepX(), face.getStepY(), face.getStepZ());
		}

		DeathProfile profile = builder.build();
		if (DEBUG) {
			DamageSource last = entity.getLastDamageSource();
			RagdollDeathsClient.LOGGER.info("[ragdoll-debug] {} id={} deathTime={} cause={} kind={} source={} sourcePos={} delta={} moved={} onGround={} launch={} freeFall={} blasted={}",
					EntityFilter.idOf(entity.getType()), entity.getId(), entity.deathTime, cause, kind,
					last == null ? "none" : last.typeHolder().getRegisteredName(), last == null ? null : last.getSourcePosition(),
					entity.getDeltaMovement(), position.subtract(entity.xo, entity.yo, entity.zo), entity.onGround(), launch,
					profile.freeFall(), profile.blasted());
		}
		return profile;
	}

	static Special specialOf(LivingEntity entity) {
		if (Mobs.isSnowGolem(entity)) {
			return Special.SNOW_GOLEM;
		}
		if (entity instanceof Warden) {
			return Special.WARDEN;
		}
		if (entity instanceof Armadillo || entity instanceof Frog) {
			return Special.ROLL_ONTO_BACK;
		}
		if (Mobs.isSkeleton(entity)) {
			return Special.SKELETON;
		}
		if (entity instanceof Creeper) {
			return Special.CREEPER;
		}
		if (entity instanceof Ravager) {
			return Special.RAVAGER;
		}
		if (Compat.isMagmaCube(entity)) {
			return Special.MAGMA_CUBE;
		}
		if (entity instanceof Blaze) {
			return Special.BLAZE;
		}
		if (entity instanceof Breeze) {
			return Special.BREEZE;
		}
		if (entity instanceof Guardian) {
			return Special.GUARDIAN;
		}
		if (Mobs.isZombieNautilus(entity)) {
			return Special.ZOMBIE_NAUTILUS;
		}
		if (Mobs.isNautilus(entity)) {
			return Special.NAUTILUS;
		}
		return Special.NONE;
	}

	static BodyKind classify(LivingEntity entity) {
		if (entity instanceof Ghast || Mobs.isHappyGhast(entity)) {
			return BodyKind.GHAST;
		}
		if (entity instanceof Bat || entity instanceof Phantom || Mobs.isParrot(entity) || Mobs.isBee(entity)
				|| entity instanceof Allay || entity instanceof Vex) {
			return BodyKind.WINGED;
		}
		if (entity instanceof Blaze || entity instanceof Breeze) {
			return BodyKind.STANDARD;
		}
		if (Compat.isCubeMob(entity)) {
			return BodyKind.CUBE;
		}
		if (entity instanceof Shulker) {
			return BodyKind.SHULKER;
		}
		if (Mobs.isWaterAnimal(entity) || Mobs.isAgeableWaterCreature(entity) || entity instanceof Guardian
				|| entity instanceof Axolotl || Mobs.isTurtle(entity) || Mobs.isNautilus(entity)) {
			return BodyKind.AQUATIC;
		}
		return classifyUnknown(entity);
	}

	private static BodyKind classifyUnknown(LivingEntity entity) {
		if (entity instanceof Mob mob) {
			if (mob.getNavigation() instanceof WaterBoundPathNavigation) {
				return BodyKind.AQUATIC;
			}
			if (mob.getNavigation() instanceof FlyingPathNavigation && !entity.onGround()) {
				return BodyKind.WINGED;
			}
		}
		if (entity.isNoGravity() && !entity.onGround()) {
			return BodyKind.SPIRIT;
		}
		return BodyKind.STANDARD;
	}

	private static DeathCause causeOf(LivingEntity entity) {
		if (entity.isInLava()) {
			return DeathCause.LAVA;
		}
		if (entity.isInWater()) {
			return DeathCause.WATER;
		}
		DamageSource source = entity.getLastDamageSource();
		if (source != null && source.is(DamageTypeTags.IS_FALL)) {
			return DeathCause.FALL;
		}
		return DeathCause.GROUND;
	}

	/**
	 * The hit comes from the damage source's position. A blast whose source is already gone (primed TNT is
	 * removed before it explodes) is placed opposite the way the explosion threw the body.
	 */
	private static HitDirection hitDirection(LivingEntity entity, float bodyYaw, Vec3 launch) {
		DamageSource source = entity.getLastDamageSource();
		Vec3 sourcePosition = source == null ? null : source.getSourcePosition();
		if (sourcePosition == null) {
			return launch == null ? HitDirection.NONE : HitDirection.classify(bodyYaw, -launch.x, -launch.z);
		}
		return HitDirection.classify(bodyYaw, sourcePosition.x - entity.getX(), sourcePosition.z - entity.getZ());
	}

	/**
	 * How an explosion throws the body, or null when it wasn't killed by one. Every explosion death throws the body:
	 * away from the blast, harder the closer it was. How close it was comes from the blast's position when the client
	 * knows it (a creeper), or else from the push the server gave the body (primed TNT is gone by the time it
	 * explodes). Heavy bodies (an iron golem, a ravager) are thrown up to half as far.
	 */
	private static Vec3 blastLaunch(LivingEntity entity, BodyKind kind, DeathCause cause, RagdollConfig config) {
		DamageSource source = entity.getLastDamageSource();
		if (source == null || !source.is(DamageTypeTags.IS_EXPLOSION)) {
			return null;
		}
		if (cause == DeathCause.WATER || cause == DeathCause.LAVA || kind.airborne() || kind == BodyKind.SHULKER || entity.isPassenger()) {
			return null;
		}
		Vec3 pushed = entity.getDeltaMovement();
		Vec3 moved = entity.position().subtract(entity.xo, entity.yo, entity.zo);
		Vec3 vanilla = moved.lengthSqr() > pushed.lengthSqr() ? moved : pushed;
		Vec3 center = entity.position().add(0.0, entity.getBbHeight() / 2.0, 0.0);
		Vec3 blast = source.getSourcePosition();

		double closeness;
		double awayX;
		double awayZ;
		if (blast != null) {
			Vec3 away = center.subtract(blast);
			closeness = Math.clamp(1.0 - away.length() / BLAST_REACH, BLAST_MIN_CLOSENESS, 1.0);
			awayX = away.x;
			awayZ = away.z;
		} else {
			closeness = Math.clamp(vanilla.length() / BLAST_FULL_PUSH, BLAST_MIN_CLOSENESS, 1.0);
			awayX = vanilla.x;
			awayZ = vanilla.z;
		}
		double flat = Math.hypot(awayX, awayZ);
		if (flat < 1.0E-3) {
			// Right on top of the blast: thrown up and off to a side of its own.
			double angle = (entity.getUUID().getLeastSignificantBits() & 0xFFFF) / 65536.0 * 2.0 * Math.PI;
			awayX = Math.cos(angle);
			awayZ = Math.sin(angle);
			flat = 1.0;
		}
		double power = config.hitReactionStrength * (1.0 - 0.5 * DeathProfile.heavinessOf(entity.getBbWidth(), entity.getBbHeight()));
		double sideways = (BLAST_BASE_SPEED + BLAST_EXTRA_SPEED * closeness) * power;
		double up = Math.min(BLAST_MAX_RISE, (BLAST_BASE_RISE + BLAST_EXTRA_RISE * closeness) * power);
		return new Vec3(awayX / flat * sideways, up, awayZ / flat * sideways);
	}

	/** A rider leaves its seat with its mount's momentum, pushed away from the blow and hopping up a little. */
	private static Vec3 riderThrow(LivingEntity entity, HitDirection hit, float bodyYaw) {
		double[] away = hit.fallDirection(bodyYaw, entity.getUUID().getLeastSignificantBits() ^ entity.getId());
		Vec3 carried = carried(entity);
		return clampLength(new Vec3(carried.x + away[0] * RIDER_THROW, Math.max(0.0, carried.y) + RIDER_HOP, carried.z + away[1] * RIDER_THROW), MAX_SPEED);
	}

	/** How far the vehicle carried its rider in the last tick: what a body leaving the seat starts with. */
	private static Vec3 carried(LivingEntity entity) {
		Entity vehicle = entity.getVehicle();
		return vehicle == null ? Vec3.ZERO : vehicle.position().subtract(vehicle.xo, vehicle.yo, vehicle.zo);
	}

	private static double horizontalReach(Vec3 velocity) {
		return Math.hypot(velocity.x, velocity.z) * 12.5;
	}

	/**
	 * How long a body may keep falling before it vanishes in the air: until it lands when there is a floor below
	 * it, or the configured limit over the void.
	 */
	static double fallLimit(Level level, LivingEntity entity, Vec3 from, RagdollConfig config) {
		Vec3 start = from.add(0.0, 0.05, 0.0);
		HitResult hit = level.clip(new ClipContext(start, start.add(0.0, -FALL_LIMIT_SCAN, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, entity));
		return hit.getType() == HitResult.Type.MISS ? config.ghostMaxTicks : Math.max(FALL_LIMIT_WITH_FLOOR, config.ghostMaxTicks);
	}

	static boolean canFreeFall(LivingEntity entity) {
		return !(entity instanceof Shulker) && !entity.onGround() && !entity.isInWater() && !entity.isInLava() && !entity.onClimbable() && !entity.isPassenger();
	}

	private static double clearance(Level level, LivingEntity entity, Vec3 from, double dirX, double dirZ, double reach, double width) {
		double length = Math.hypot(dirX, dirZ);
		if (length < 1.0E-6 || reach <= 0.0) {
			return 0.0;
		}
		double travel = reach + width / 2.0;
		Vec3 to = from.add(dirX / length * travel, 0.0, dirZ / length * travel);
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
		double free = hit.getType() == HitResult.Type.MISS ? travel : hit.getLocation().distanceTo(from);
		return Math.max(0.0, free - width / 2.0 - WALL_MARGIN);
	}

	private static double floorDistance(Level level, LivingEntity entity, Vec3 from, double scan, ClipContext.Fluid fluid) {
		Vec3 start = from.add(0.0, 0.05, 0.0);
		Vec3 to = start.add(0.0, -scan, 0.0);
		HitResult hit = level.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, fluid, entity));
		if (hit.getType() == HitResult.Type.MISS) {
			return -scan;
		}
		return Math.min(0.0, hit.getLocation().y - from.y);
	}

	private static double waterRise(Level level, LivingEntity entity, double width) {
		BlockPos base = entity.blockPosition();
		for (int up = 0; up <= WATER_SURFACE_SCAN; up++) {
			BlockPos pos = base.above(up);
			if (!level.getFluidState(pos).is(FluidTags.WATER)) {
				return Math.max(0.0, pos.getY() - entity.getY() - width * 0.5);
			}
		}
		return WATER_SURFACE_SCAN;
	}

	private static Vec3 clampLength(Vec3 vector, double max) {
		double length = vector.length();
		return length > max ? vector.scale(max / length) : vector;
	}
}
