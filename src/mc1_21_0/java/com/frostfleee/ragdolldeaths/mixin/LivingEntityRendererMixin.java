package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.Compat;
import com.frostfleee.ragdolldeaths.client.JointApplier;
import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import com.frostfleee.ragdolldeaths.client.PoseKeeper;
import com.frostfleee.ragdolldeaths.client.RagdollRenderData;
import com.frostfleee.ragdolldeaths.client.RagdollStates;
import com.frostfleee.ragdolldeaths.client.RagdollTransforms;
import com.frostfleee.ragdolldeaths.core.RagdollPose;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws dead mobs as their ragdolls. This copy is for 1.21 and 1.21.1, which draw a mob straight from the entity: the
 * mob's stand-in render state was made just before (RagdollStates, from EntityRenderDispatcherMixin), and every value the
 * mod changed on it is handed back here as the renderer reads it from the entity. Every model drawn while a ragdoll mob
 * is being rendered, its own and those of its layers, is posed by ModelFeatureRendererMixin as it draws.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	private static final String RENDER = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

	private static LivingEntityRenderState ragdolldeaths$state(LivingEntity entity) {
		LivingEntityRenderState state = RagdollStates.current;
		return state != null && state.entity == entity ? state : null;
	}

	@Inject(method = RENDER, at = @At("HEAD"), cancellable = true)
	private void ragdolldeaths$skipVanishedBody(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
		LivingEntityRenderState state = ragdolldeaths$state(entity);
		RagdollPose pose = state == null ? null : RagdollRenderData.pose(state);
		if (pose != null && pose.hidden) {
			ci.cancel();
			return;
		}
		PhysicsPoseApplier.drawing = pose != null || state != null && RagdollRenderData.physics(state) != null ? state : null;
	}

	// This version keeps no light on the entity: a body lying away from the entity is lit where it lies.
	@ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int ragdolldeaths$bodyLight(int light, LivingEntity entity) {
		LivingEntityRenderState state = ragdolldeaths$state(entity);
		return state == null ? light : RagdollRenderData.light(state, light);
	}

	@Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
	private void ragdolldeaths$applyPose(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
		LivingEntityRenderState state = ragdolldeaths$state(entity);
		if (state == null) {
			return;
		}
		RagdollPose pose = RagdollRenderData.pose(state);
		if (pose != null) {
			RagdollTransforms.apply(pose, poseStack);
		}
		PhysicsPoseApplier.begin((LivingEntityRenderer) (Object) this, state, Compat.cameraPosition());
	}

	// The body's facing, held by an animated body (the first rotLerp is the body's turn).
	@ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F", ordinal = 0))
	private float ragdolldeaths$bodyFacing(float bodyRot, @Local(argsOnly = true) LivingEntity entity) {
		LivingEntityRenderState state = ragdolldeaths$state(entity);
		return state != null && RagdollRenderData.pose(state) != null ? state.bodyRot : bodyRot;
	}

	// The model's age, slowed to a stop for an animated body.
	@ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getBob(Lnet/minecraft/world/entity/LivingEntity;F)F"))
	private float ragdolldeaths$age(float age, @Local(argsOnly = true) LivingEntity entity) {
		LivingEntityRenderState state = ragdolldeaths$state(entity);
		return state != null && RagdollRenderData.pose(state) != null ? state.ageInTicks : age;
	}

	@Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
	private void ragdolldeaths$poseLayerJoints(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
		LivingEntityRenderState state = ragdolldeaths$state(entity);
		if (state == null) {
			return;
		}
		// Layers that hang things on the model (held items, heads) read its parts right after this.
		var model = ((LivingEntityRenderer) (Object) this).getModel();
		PoseKeeper.remember(model);
		if (!PhysicsPoseApplier.apply(model, state, poseStack.last().pose())) {
			JointApplier.apply(model, state);
		}
	}

	@Inject(method = RENDER, at = @At("TAIL"))
	private void ragdolldeaths$doneDrawing(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
		PhysicsPoseApplier.drawing = null;
		// The model is shared with the living mobs of this kind: they must not be drawn with the body's pose.
		PoseKeeper.restore();
	}

	// The red death tint, unless turned off.
	@ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getOverlayCoords(Lnet/minecraft/world/entity/LivingEntity;F)I"))
	private int ragdolldeaths$deathTint(int overlay, @Local(argsOnly = true) LivingEntity entity) {
		LivingEntityRenderState state = ragdolldeaths$state(entity);
		return state != null && !state.hasRedOverlay ? OverlayTexture.NO_OVERLAY : overlay;
	}

	// A body that has been in lava is drawn burnt: its colours are multiplied down to almost black.
	@ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"), index = 4)
	private int ragdolldeaths$charred(int tint) {
		Object state = PhysicsPoseApplier.drawing;
		return state instanceof LivingEntityRenderState living && RagdollRenderData.charred(living) ? 0xFF1A1716 : tint;
	}

	// A body burnt by lava is drawn with its own copy of the mob texture, with the burnt pixels on it.
	@ModifyExpressionValue(
			method = "getRenderType",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/resources/ResourceLocation;"))
	private net.minecraft.resources.ResourceLocation ragdolldeaths$scorched(net.minecraft.resources.ResourceLocation texture, @Local(argsOnly = true) LivingEntity entity) {
		LivingEntityRenderState state = ragdolldeaths$state(entity);
		return state == null ? com.frostfleee.ragdolldeaths.client.Wounds.livingTexture(texture, entity) : RagdollRenderData.texture(state, texture);
	}

	@ModifyExpressionValue(
			method = "setupRotations",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getFlipDegrees(Lnet/minecraft/world/entity/LivingEntity;)F", ordinal = 0))
	private float ragdolldeaths$replaceVanillaFlip(float flipDegrees, @Local(argsOnly = true) LivingEntity entity) {
		LivingEntityRenderState state = ragdolldeaths$state(entity);
		return state != null && RagdollRenderData.pose(state) != null ? 0.0F : flipDegrees;
	}

	// The arrows stuck in the mob, placed on the model as it is posed for this frame.
	@Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 0))
	private void ragdolldeaths$stuckArrows(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
		Compat.stuckArrows(((LivingEntityRenderer) (Object) this).getModel(), entity, poseStack, buffers, light, partialTicks);
	}
}
