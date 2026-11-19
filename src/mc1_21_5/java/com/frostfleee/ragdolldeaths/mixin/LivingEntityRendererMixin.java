package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.Compat;
import com.frostfleee.ragdolldeaths.client.JointApplier;
import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import com.frostfleee.ragdolldeaths.client.RagdollRenderData;
import com.frostfleee.ragdolldeaths.client.RagdollTransforms;
import com.frostfleee.ragdolldeaths.core.RagdollPose;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws dead mobs as their ragdolls. This copy is for 1.21.2 to 1.21.8, which draw a mob straight away in render()
 * instead of submitting it for later. Every model drawn while a ragdoll mob is being rendered, its own and those of
 * its layers, is posed by ModelFeatureRendererMixin as it draws.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	private static final String RENDER = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

	@Inject(method = RENDER, at = @At("HEAD"), cancellable = true)
	private void ragdolldeaths$skipVanishedBody(LivingEntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
		RagdollPose pose = RagdollRenderData.pose(state);
		if (pose != null && pose.hidden) {
			ci.cancel();
			return;
		}
		PhysicsPoseApplier.drawing = pose != null || RagdollRenderData.physics(state) != null ? state : null;
	}

	// This version keeps no light on the state: a body lying away from the entity is lit where it lies.
	@ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int ragdolldeaths$bodyLight(int light, LivingEntityRenderState state) {
		return RagdollRenderData.light(state, light);
	}

	@Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
	private void ragdolldeaths$applyPose(LivingEntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
		RagdollPose pose = RagdollRenderData.pose(state);
		if (pose != null) {
			RagdollTransforms.apply(pose, poseStack);
		}
		PhysicsPoseApplier.begin((LivingEntityRenderer<?, ?, ?>) (Object) this, state, Compat.cameraPosition());
	}

	@Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)V", shift = At.Shift.AFTER))
	private void ragdolldeaths$poseLayerJoints(LivingEntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
		// Layers that hang things on the model (held items, heads) read its parts right after this.
		var model = ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
		if (!PhysicsPoseApplier.apply(model, state, poseStack.last().pose())) {
			JointApplier.apply(model, state);
		}
	}

	@Inject(method = RENDER, at = @At("TAIL"))
	private void ragdolldeaths$doneDrawing(LivingEntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
		PhysicsPoseApplier.drawing = null;
	}

	// A body burnt by lava is drawn with its own copy of the mob texture, with the burnt pixels on it.
	@ModifyExpressionValue(
			method = "getRenderType",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/ResourceLocation;"))
	private net.minecraft.resources.ResourceLocation ragdolldeaths$scorched(net.minecraft.resources.ResourceLocation texture, @Local(argsOnly = true) LivingEntityRenderState state) {
		return RagdollRenderData.texture(state, texture);
	}

	// A body that has been in lava is drawn burnt: its colours are multiplied down to almost black.
	@ModifyExpressionValue(
			method = RENDER,
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

	// The arrows stuck in the mob, placed on the model as it is posed for this frame.
	@Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 0))
	private void ragdolldeaths$stuckArrows(LivingEntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
		com.frostfleee.ragdolldeaths.client.Compat.stuckArrows(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state, poseStack, buffers, light);
	}
}
