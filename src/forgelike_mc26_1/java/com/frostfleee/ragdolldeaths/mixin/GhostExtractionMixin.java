package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds ghost bodies once the visible entities are extracted (Fabric uses Fabric API's extraction event instead).
 * This copy is for 26.1.x, where entity extraction lives in LevelRenderer.
 */
@Mixin(LevelRenderer.class)
abstract class GhostExtractionMixin {
	@Inject(method = "extractVisibleEntities", at = @At("TAIL"))
	private void ragdolldeaths$extractGhosts(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState output, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			RagdollTracker.INSTANCE.extractGhosts(minecraft.level, output, deltaTracker.getGameTimeDeltaPartialTick(true));
		}
	}
}
