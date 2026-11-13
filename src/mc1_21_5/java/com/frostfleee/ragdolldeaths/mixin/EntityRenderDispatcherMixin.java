package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.RagdollRenderData;
import com.frostfleee.ragdolldeaths.core.RagdollPose;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.LevelReader;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Flames and shadows of ragdoll bodies. This copy is for 1.21.5 to 1.21.8, which draw both straight away. */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
	// A burning mob has its flames drawn where the entity is. Its body can be far from there, so they go where the
	// body is drawn instead of hanging in the air.
	@WrapOperation(
			method = "render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderFlame(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lorg/joml/Quaternionf;)V"))
	private void ragdolldeaths$flamesOnBody(EntityRenderDispatcher dispatcher, PoseStack poseStack, MultiBufferSource buffers, EntityRenderState state, Quaternionf rotation,
			Operation<Void> original) {
		RagdollPose pose = RagdollRenderData.pose(state);
		if (pose == null) {
			original.call(dispatcher, poseStack, buffers, state, rotation);
			return;
		}
		if (pose.hidden) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(pose.offsetX, pose.offsetY, pose.offsetZ);
		original.call(dispatcher, poseStack, buffers, state, rotation);
		poseStack.popPose();
	}

	// The shadow is the entity's, under where it stands; a ragdoll body lies somewhere else, so it has none.
	@WrapOperation(
			method = "render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FLnet/minecraft/world/level/LevelReader;F)V"))
	private void ragdolldeaths$noShadowUnderBody(PoseStack poseStack, MultiBufferSource buffers, EntityRenderState state, float strength, LevelReader level, float radius,
			Operation<Void> original) {
		if (RagdollRenderData.pose(state) == null) {
			original.call(poseStack, buffers, state, strength, level, radius);
		}
	}
}
