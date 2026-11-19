package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.StuckArrows;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The arrows and bee stingers the game itself draws on players follow a limb bent at a knee or an elbow. */
@Mixin(StuckInBodyLayer.class)
abstract class StuckInLimbMixin {
	@WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
	private void ragdolldeaths$intoBentLimb(PoseStack pose, float x, float y, float z, Operation<Void> original, @Local ModelPart part) {
		if (!StuckArrows.intoLowerHalf(pose, part, x, y, z)) {
			original.call(pose, x, y, z);
		}
	}
}
