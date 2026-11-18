package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Armor, horse and wolf armor and llama carpets burn where lava touched them, on a copy of their own texture (see
 * Scorch), unless the item does not burn in lava (netherite). This copy is for 26.1.x.
 */
@Mixin(EquipmentLayerRenderer.class)
abstract class ArmorBurnMixin {
	// Both overloads by name (only one draws, and in 1.21.2 to 1.21.4 their parameters change): a bare name would pick the first.
	@ModifyArg(method = "renderLayers*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
	@SuppressWarnings("rawtypes")
	private Identifier ragdolldeaths$burntArmor(Identifier texture, @Local(argsOnly = true) Model model, @Local(argsOnly = true) ItemStack stack) {
		return PhysicsPoseApplier.layerTexture(texture, model, stack);
	}
}
