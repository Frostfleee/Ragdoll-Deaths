package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.JointApplier;
import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
abstract class ModelFeatureRendererMixin {
	@Inject(
			method = "prepareModel",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
	// The submit is taken as a local: the method has more parameters in some versions than in others.
	private void ragdolldeaths$poseJoints(CallbackInfo ci, @Local(argsOnly = true) ModelFeatureRenderer.Submit<?> submit) {
		if (!PhysicsPoseApplier.apply(submit.model(), submit.state(), submit.pose().pose())) {
			JointApplier.apply(submit.model(), submit.state());
		}
	}
}
