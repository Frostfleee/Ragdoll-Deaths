package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.RagdollRenderData;
import com.frostfleee.ragdolldeaths.client.RagdollStates;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21 and 1.21.1 only: a slime's renderer stretches it by the squish it reads from the entity; for a physics body the
 * squish the mod set on the stand-in state (zero, see ActiveRagdoll.applyPhysics) is used instead.
 */
@Mixin(SlimeRenderer.class)
abstract class SlimeSquishMixin {
	@ModifyExpressionValue(method = "scale", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"))
	private float ragdolldeaths$noSquish(float squish) {
		return RagdollStates.current instanceof SlimeRenderState state && RagdollRenderData.physics(state) != null ? state.squish : squish;
	}
}
