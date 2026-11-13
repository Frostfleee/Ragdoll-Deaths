package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.ModelRoots;
import net.minecraft.client.model.Model;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21 and 1.21.1 only: gives each new model the layer baked just before it as its probable root (see ModelRoots). */
@Mixin(Model.class)
abstract class ModelRootMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void ragdolldeaths$takeRoot(CallbackInfo ci) {
		ModelRoots.made((Model) (Object) this);
	}
}
