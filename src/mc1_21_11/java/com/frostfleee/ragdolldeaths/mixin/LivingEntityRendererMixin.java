package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.JointApplier;
import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import com.frostfleee.ragdolldeaths.client.RagdollRenderData;
import com.frostfleee.ragdolldeaths.client.RagdollTransforms;
import com.frostfleee.ragdolldeaths.core.RagdollPose;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	@Inject(
			method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
			at = @At("HEAD"),
			cancellable = true)
	private void ragdolldeaths$skipVanishedBody(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		RagdollPose pose = RagdollRenderData.pose(state);
		if (pose != null && pose.hidden) {
			ci.cancel();
		}
	}

	@Inject(
			method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
	private void ragdolldeaths$applyPose(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		RagdollPose pose = RagdollRenderData.pose(state);
		if (pose != null) {
			RagdollTransforms.apply(pose, poseStack);
		}
		PhysicsPoseApplier.begin((LivingEntityRenderer<?, ?, ?>) (Object) this, state, camera.pos);
	}

	@Inject(
			method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
	private void ragdolldeaths$poseLayerJoints(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		// Layers that hang things on the model (held items, heads) read its parts right after this.
		var model = ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
		if (!PhysicsPoseApplier.apply(model, state, poseStack.last().pose())) {
			JointApplier.apply(model, state);
		}
	}

	// A body burnt by lava is drawn with its own copy of the mob's texture, with the burnt pixels on it.
	@ModifyExpressionValue(
			method = "getRenderType",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;"))
	private net.minecraft.resources.Identifier ragdolldeaths$scorched(net.minecraft.resources.Identifier texture, @Local(argsOnly = true) LivingEntityRenderState state) {
		return RagdollRenderData.texture(state, texture);
	}

	// A body that has been in lava is drawn burnt: its own colours are multiplied down to almost black.
	@ModifyExpressionValue(
			method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I"))
	private int ragdolldeaths$charred(int tint, @Local(argsOnly = true) LivingEntityRenderState state) {
		return RagdollRenderData.charred(state) ? 0xFF1A1716 : tint;
	}

	@ModifyExpressionValue(
			method = "setupRotations",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getFlipDegrees()F", ordinal = 0))
	private float ragdolldeaths$replaceVanillaFlip(float flipDegrees, @Local(argsOnly = true) LivingEntityRenderState state) {
		return RagdollRenderData.pose(state) != null ? 0.0F : flipDegrees;
	}
}
