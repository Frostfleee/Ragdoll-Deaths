package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A mob's second skin burns where lava touched it, on a copy of its own texture (see Scorch): a villager's clothes, a
 * drowned's or a stray's outer layer, a sheep's wool, a cat's collar, a tropical fish's pattern.
 */
@Mixin(RenderLayer.class)
abstract class SkinLayerBurnMixin {
	@ModifyVariable(method = "renderColoredCutoutModel", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	@SuppressWarnings("rawtypes")
	private static net.minecraft.resources.ResourceLocation ragdolldeaths$burntSkinLayer(net.minecraft.resources.ResourceLocation texture, @Local(argsOnly = true) net.minecraft.client.model.EntityModel model) {
		return PhysicsPoseApplier.layerTexture(texture, model, null);
	}
}
