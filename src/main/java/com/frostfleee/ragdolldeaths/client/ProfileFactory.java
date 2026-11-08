package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.BodyKind;
import com.frostfleee.ragdolldeaths.core.DeathCause;
import com.frostfleee.ragdolldeaths.core.DeathProfile;
import com.frostfleee.ragdolldeaths.core.HitDirection;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.core.Special;
import com.frostfleee.ragdolldeaths.mixin.LivingEntityRendererAccessor;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilus;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.turtle.Turtle;
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

	private ProfileFactory() {
	}

	static DeathProfile create(LivingEntityRenderer<?, ?, ?> renderer, LivingEntity entity, RagdollConfig config) {
		BodyKind kind = classify(entity);
		JointRig rig = JointRig.of(renderer.getModel().root());
		DeathCause cause = causeOf(entity);
		float bodyYaw = entity.yBodyRot;
		HitDirection hit = hitDirection(entity, bodyYaw);
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
				.flipsOnBack(((LivingEntityRendererAccessor) renderer).ragdolldeaths$getFlipDegrees() >= BACK_FLIP_DEGREES)
				.alreadySideways(entity instanceof AbstractFish && !entity.isInWater())
				.ghostCapable(kind.airborne() && config.usesGhost(EntityFilter.idOf(entity.getType())))
				.calmWings(!(entity instanceof Bee));

		Level level = entity.level();
		Vec3 center = position.add(0.0, height / 2.0, 0.0);
		if (kind.airborne()) {
			builder.velocity(velocity.x, velocity.y, velocity.z)
					.maxPush(clearance(level, entity, center, velocity.x, velocity.z, horizontalReach(velocity), width))
					.floorDrop(floorDistance(level, entity, position, FLOOR_SCAN, ClipContext.Fluid.ANY));
		} else if (canFreeFall(entity)) {
			double drop = floorDistance(level, entity, position, FLOOR_SCAN, ClipContext.Fluid.ANY);
			if (drop < -FREE_FALL_MIN_DROP) {
				builder.freeFall(true)
						.velocity(velocity.x, velocity.y, velocity.z)
						.maxPush(clearance(level, entity, center, velocity.x, velocity.z, horizontalReach(velocity), width))
						.floorDrop(drop);
			} else {
				double[] fall = hit.fallDirection(bodyYaw, 0L);
				builder.maxPush(clearance(level, entity, center, fall[0], fall[1], 1.0 + width, width));
			}
		} else {
			double[] fall = hit.fallDirection(bodyYaw, 0L);
			builder.maxPush(clearance(level, entity, center, fall[0], fall[1], 1.0 + width, width));
		}

		if (entity.isInWater()) {
			builder.water(waterRise(level, entity, width), -floorDistance(level, entity, position, WATER_FLOOR_SCAN, ClipContext.Fluid.NONE));
		}

		if (entity instanceof Shulker shulker) {
			Direction face = shulker.getAttachFace();
			builder.attach(face.getStepX(), face.getStepY(), face.getStepZ());
		}

		return builder.build();
	}

	static Special specialOf(LivingEntity entity) {
		if (entity instanceof SnowGolem) {
			return Special.SNOW_GOLEM;
		}
		if (entity instanceof Warden) {
			return Special.WARDEN;
		}
		if (entity instanceof Armadillo || entity instanceof Frog) {
			return Special.ROLL_ONTO_BACK;
		}
		if (entity instanceof AbstractSkeleton) {
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
		if (entity instanceof ZombieNautilus) {
			return Special.ZOMBIE_NAUTILUS;
		}
		if (entity instanceof AbstractNautilus) {
			return Special.NAUTILUS;
		}
		return Special.NONE;
	}

	static BodyKind classify(LivingEntity entity) {
		if (entity instanceof Ghast || entity instanceof HappyGhast) {
			return BodyKind.GHAST;
		}
		if (entity instanceof Bat || entity instanceof Phantom || entity instanceof Parrot || entity instanceof Bee
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
		if (entity instanceof WaterAnimal || entity instanceof AgeableWaterCreature || entity instanceof Guardian
				|| entity instanceof Axolotl || entity instanceof Turtle || entity instanceof AbstractNautilus) {
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

	private static HitDirection hitDirection(LivingEntity entity, float bodyYaw) {
		DamageSource source = entity.getLastDamageSource();
		Vec3 sourcePosition = source == null ? null : source.getSourcePosition();
		if (sourcePosition == null) {
			return HitDirection.NONE;
		}
		return HitDirection.classify(bodyYaw, sourcePosition.x - entity.getX(), sourcePosition.z - entity.getZ());
	}

	private static double horizontalReach(Vec3 velocity) {
		return Math.hypot(velocity.x, velocity.z) * 12.5;
	}

	static double travelClearance(Level level, LivingEntity entity, Vec3 velocity) {
		Vec3 center = entity.position().add(0.0, entity.getBbHeight() / 2.0, 0.0);
		return clearance(level, entity, center, velocity.x, velocity.z, horizontalReach(velocity), entity.getBbWidth());
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
