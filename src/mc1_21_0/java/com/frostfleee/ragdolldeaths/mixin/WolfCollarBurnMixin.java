package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A wolf's collar burns where lava touched it (see Scorch). */
@Mixin(WolfCollarLayer.class)
abstract class WolfCollarBurnMixin {
	@ModifyArg(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/animal/Wolf;FFFFFF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;entityCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
	@SuppressWarnings("rawtypes")
	private net.minecraft.resources.ResourceLocation ragdolldeaths$burnt(net.minecraft.resources.ResourceLocation texture) {
		return PhysicsPoseApplier.layerTexture(texture, ((RenderLayer) (Object) this).getParentModel(), null);
	}
}
