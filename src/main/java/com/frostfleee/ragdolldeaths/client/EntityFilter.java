package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

final class EntityFilter {
	private EntityFilter() {
	}

	static String idOf(EntityType<?> type) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
	}

	static boolean isEligible(LivingEntity entity, RagdollConfig config) {
		if (!config.enabled || keepsVanillaDeath(entity)) {
			return false;
		}
		if (entity instanceof Player && !config.playerRagdolls) {
			return false;
		}
		if (entity.isPassenger() || entity.isSleeping() || config.isExcluded(idOf(entity.getType()))) {
			return false;
		}
		return withinRenderDistance(entity.position(), config);
	}

	static boolean withinRenderDistance(Vec3 position, RagdollConfig config) {
		Vec3 camera = Compat.cameraPosition();
		return camera.distanceToSqr(position) <= config.maxRenderDistance * config.maxRenderDistance;
	}

	private static boolean keepsVanillaDeath(LivingEntity entity) {
		return entity instanceof EnderDragon
				|| entity instanceof WitherBoss
				|| entity instanceof ArmorStand
				|| entity instanceof Creaking
				|| entity instanceof Avatar && !(entity instanceof Player);
	}
}
