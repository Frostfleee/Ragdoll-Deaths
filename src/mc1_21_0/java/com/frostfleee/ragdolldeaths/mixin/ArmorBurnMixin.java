package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Armor burns where lava touched it, on a copy of its own texture (see Scorch), unless the item does not burn in lava
 * (netherite). This copy is for 1.21 and 1.21.1, which draw each piece with one of two shared models, its texture
 * handed to renderModel; the piece being drawn is noted just before.
 *
 * <p>NeoForge and Forge add a second renderArmorPiece and a second renderModel (taking any Model, for armor with a
 * model of its own) and draw through those, so both of each are hooked. A texture passed from one to the other is
 * only burnt once: an already burnt copy is left as it is.
 */
@Mixin(HumanoidArmorLayer.class)
abstract class ArmorBurnMixin {
	@Unique
	private static ItemStack ragdolldeaths$piece = ItemStack.EMPTY;

	@Inject(method = "renderArmorPiece*", at = @At("HEAD"))
	private void ragdolldeaths$notePiece(CallbackInfo ci, @Local(argsOnly = true) LivingEntity entity, @Local(argsOnly = true) EquipmentSlot slot) {
		ragdolldeaths$piece = entity.getItemBySlot(slot);
	}

	@ModifyVariable(method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/HumanoidModel;ILnet/minecraft/resources/ResourceLocation;)V",
			at = @At("HEAD"), argsOnly = true)
	private ResourceLocation ragdolldeaths$burntArmor(ResourceLocation texture, @Local(argsOnly = true) HumanoidModel<?> model) {
		return PhysicsPoseApplier.layerTexture(texture, model, ragdolldeaths$piece);
	}

	// NeoForge and Forge only.
	@ModifyVariable(method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;ILnet/minecraft/resources/ResourceLocation;)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private ResourceLocation ragdolldeaths$burntModelArmor(ResourceLocation texture, @Local(argsOnly = true) Model model) {
		return PhysicsPoseApplier.layerTexture(texture, model, ragdolldeaths$piece);
	}
}
