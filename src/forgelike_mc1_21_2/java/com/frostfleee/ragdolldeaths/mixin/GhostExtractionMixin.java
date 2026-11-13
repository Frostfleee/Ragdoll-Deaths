package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the ghost bodies (mobs the server has already removed) along with the entities in view. This copy is for
 * 1.21.2 to 1.21.8, which draw entities from a list instead of extracting them.
 */
@Mixin(LevelRenderer.class)
abstract class GhostExtractionMixin {
	@Inject(method = "renderEntities", at = @At("HEAD"))
	private void ragdolldeaths$drawGhosts(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Camera camera, DeltaTracker deltaTracker, List<Entity> entities,
			CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			entities.addAll(RagdollTracker.INSTANCE.ghostEntities(minecraft.level));
		}
	}
}
