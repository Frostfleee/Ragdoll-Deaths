package com.frostfleee.ragdolldeaths.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * The mobs whose classes moved between game versions, asked by kind so the shared code needs no version of its
 * own. A mob this game version does not have is never any of these. This copy is for 1.21.9 and 1.21.10, before the nautilus.
 */
public final class Mobs {
	private Mobs() {
	}

	public static boolean isFish(Entity entity) {
		return entity instanceof net.minecraft.world.entity.animal.AbstractFish;
	}

	public static boolean isWaterAnimal(Entity entity) {
		return entity instanceof net.minecraft.world.entity.animal.WaterAnimal;
	}

	public static boolean isBee(Entity entity) {
		return entity instanceof net.minecraft.world.entity.animal.Bee;
	}

	public static boolean isParrot(Entity entity) {
		return entity instanceof net.minecraft.world.entity.animal.Parrot;
	}

	public static boolean isTurtle(Entity entity) {
		return entity instanceof net.minecraft.world.entity.animal.Turtle;
	}

	public static boolean isSnowGolem(Entity entity) {
		return entity instanceof net.minecraft.world.entity.animal.SnowGolem;
	}

	public static boolean snowGolemHasPumpkin(Entity entity) {
		return entity instanceof net.minecraft.world.entity.animal.SnowGolem golem && golem.hasPumpkin();
	}

	public static boolean isSkeleton(Entity entity) {
		return entity instanceof net.minecraft.world.entity.monster.AbstractSkeleton;
	}

	public static boolean isHappyGhast(Entity entity) {
		return entity instanceof net.minecraft.world.entity.animal.HappyGhast;
	}

	public static boolean isNautilus(Entity entity) {
		return false;
	}

	public static boolean isZombieNautilus(Entity entity) {
		return false;
	}

	/** A mannequin (a player-shaped mob), which keeps the vanilla death. */
	public static boolean isMannequin(Entity entity) {
		return entity instanceof net.minecraft.world.entity.Avatar && !(entity instanceof net.minecraft.world.entity.player.Player);
	}

	public static boolean isCreaking(Entity entity) {
		return entity instanceof net.minecraft.world.entity.monster.creaking.Creaking;
	}

	public static boolean isAgeableWaterCreature(Entity entity) {
		return entity instanceof net.minecraft.world.entity.animal.AgeableWaterCreature;
	}

	/** Stops the idle motion of render states only some game versions have. */
	public static void stillVersionSpecific(EntityRenderState state) {
	}
}
