package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.ModelRoots;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.21 and 1.21.1 only: notes each freshly baked model layer, the root of the model made next (see ModelRoots). */
@Mixin(EntityModelSet.class)
abstract class ModelBakeMixin {
	@Inject(method = "bakeLayer", at = @At("RETURN"))
	private void ragdolldeaths$noteRoot(ModelLayerLocation layer, CallbackInfoReturnable<ModelPart> cir) {
		ModelRoots.baked(cir.getReturnValue());
	}
}
