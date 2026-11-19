package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.HorseMarkingLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A horse's white markings burn where lava touched them, like the coat under them (see Scorch). */
@Mixin(HorseMarkingLayer.class)
abstract class HorseMarkingBurnMixin {
	@ModifyArg(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HorseRenderState;FF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;entityTranslucent(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
	@SuppressWarnings("rawtypes")
	private net.minecraft.resources.ResourceLocation ragdolldeaths$burnt(net.minecraft.resources.ResourceLocation texture) {
		return PhysicsPoseApplier.layerTexture(texture, ((RenderLayer) (Object) this).getParentModel(), null);
	}
}
