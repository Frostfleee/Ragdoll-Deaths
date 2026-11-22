package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
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
		if (entity.isSleeping() || config.isExcluded(idOf(entity.getType()))) {
			return false;
		}
		if (entity.isPassenger() && !isVehicleRider(entity) && !(config.throwRiders && isMountedRider(entity))) {
			return false;
		}
		return withinRenderDistance(entity.position(), config);
	}

	/**
	 * Riding a mob (a horse, a spider, a chicken, a ravager) and above any water or lava, so the body can be thrown
	 * off. Boats and minecarts are {@link #isVehicleRider}.
	 */
	static boolean isMountedRider(LivingEntity entity) {
		return entity.getVehicle() instanceof LivingEntity && !entity.isInWater() && !entity.isInLava();
	}

	/**
	 * Seated in a boat, a raft or a minecart (of any kind): the body slumps out of the seat with the vehicle's
	 * momentum, whatever Thrown Riders is set to. Known by the vehicle's id, which has had different classes across
	 * game versions; modded boats and carts named that way count too.
	 */
	static boolean isVehicleRider(LivingEntity entity) {
		Entity vehicle = entity.getVehicle();
		if (vehicle == null || vehicle instanceof LivingEntity) {
			return false;
		}
		String path = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).getPath();
		return path.endsWith("boat") || path.endsWith("raft") || path.contains("minecart");
	}

	static boolean withinRenderDistance(Vec3 position, RagdollConfig config) {
		Vec3 camera = Compat.cameraPosition();
		return camera.distanceToSqr(position) <= config.maxRenderDistance * config.maxRenderDistance;
	}

	static boolean keepsVanillaDeath(LivingEntity entity) {
		return entity instanceof EnderDragon
				|| entity instanceof WitherBoss
				|| entity instanceof ArmorStand
				|| Mobs.isCreaking(entity);
	}
}
