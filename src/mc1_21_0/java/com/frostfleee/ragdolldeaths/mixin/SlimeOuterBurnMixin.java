package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A slime's outer jelly burns where lava touched it (see Scorch). */
@Mixin(SlimeOuterLayer.class)
abstract class SlimeOuterBurnMixin {
	@Shadow
	@Final
	@SuppressWarnings("rawtypes")
	private net.minecraft.client.model.EntityModel model;

	@ModifyArg(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;entityTranslucent(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
	private net.minecraft.resources.ResourceLocation ragdolldeaths$burnt(net.minecraft.resources.ResourceLocation texture) {
		return PhysicsPoseApplier.layerTexture(texture, model, null);
	}
}
