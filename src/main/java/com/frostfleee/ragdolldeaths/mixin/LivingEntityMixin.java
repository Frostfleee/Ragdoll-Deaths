package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	// The death poof is skipped only for bodies this mod is animating; every other use of the poof is untouched.
	@Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true)
	private void ragdolldeaths$skipDeathPoof(CallbackInfo ci) {
		if (RagdollTracker.INSTANCE.suppressPoof((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
