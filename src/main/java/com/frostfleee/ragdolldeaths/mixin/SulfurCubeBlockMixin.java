package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The block a sulfur cube has swallowed is drawn by the game at the mob's standing pose, not on its model: on a body
 * it is moved to where the cube's physics body is, so it turns and falls with it.
 *
 * <p>Sulfur cubes exist from 26.2. The target is named as text and the mixin is {@link Pseudo}, so on older versions,
 * which have no such class, it is simply skipped.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.entity.layers.SulfurCubeInnerLayer")
abstract class SulfurCubeBlockMixin {
	@Inject(
			method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/SulfurCubeRenderState;FF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockModelRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void ragdolldeaths$carryBlock(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
		PhysicsPoseApplier.carryBlock(poseStack);
	}
}
