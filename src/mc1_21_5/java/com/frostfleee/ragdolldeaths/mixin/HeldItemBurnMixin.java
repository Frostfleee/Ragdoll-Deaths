package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A held item burns black with the hand that held it in lava, unless it is an item that does not burn (netherite).
 * This copy is for 1.21.5 to 1.21.8, which draw the item as the layer is drawn.
 */
@Mixin(ItemInHandLayer.class)
abstract class HeldItemBurnMixin {
	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int ragdolldeaths$burntItem(int light, @Local(argsOnly = true) HumanoidArm arm) {
		return PhysicsPoseApplier.heldItemLight(arm, light);
	}
}
