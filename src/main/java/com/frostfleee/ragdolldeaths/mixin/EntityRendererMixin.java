package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Puts the ragdoll on each entity's render state as it is made (1.21.2 and later; 1.21 has no render states). */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	@Inject(
			method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
			at = @At("RETURN"))
	private void ragdolldeaths$applyRagdoll(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
		RagdollTracker.INSTANCE.onStateCreated((EntityRenderer) (Object) this, entity, cir.getReturnValue(), partialTicks);
	}
}
