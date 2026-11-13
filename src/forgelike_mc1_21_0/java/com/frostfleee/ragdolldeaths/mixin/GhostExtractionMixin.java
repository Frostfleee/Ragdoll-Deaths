package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import com.google.common.collect.Iterables;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws the ghost bodies (mobs the server has already removed) along with the entities in view. This copy is for 1.21
 * and 1.21.1, which draw every entity the level lists for rendering.
 */
@Mixin(LevelRenderer.class)
abstract class GhostExtractionMixin {
	@ModifyExpressionValue(method = "renderLevel",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;entitiesForRendering()Ljava/lang/Iterable;"))
	private Iterable<Entity> ragdolldeaths$drawGhosts(Iterable<Entity> entities) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return entities;
		}
		var ghosts = RagdollTracker.INSTANCE.ghostEntities(minecraft.level);
		return ghosts.isEmpty() ? entities : Iterables.concat(entities, ghosts);
	}
}
