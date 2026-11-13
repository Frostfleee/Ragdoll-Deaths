package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.JointApplier;
import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Poses every model drawn while a ragdoll mob is being rendered: its own model and its layers (armor, clothes).
 * 1.21.9 and later do this on the model feature renderer; this copy, for 1.21.2 to 1.21.8, does it as a model draws.
 */
@Mixin(Model.class)
abstract class ModelFeatureRendererMixin {
	@Inject(method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"))
	private void ragdolldeaths$poseJoints(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
		Object state = PhysicsPoseApplier.drawing;
		if (state == null) {
			return;
		}
		Model model = (Model) (Object) this;
		if (!PhysicsPoseApplier.apply(model, state, poseStack.last().pose())) {
			JointApplier.apply(model, state);
		}
	}
}
