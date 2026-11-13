package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.RagdollRenderData;
import com.frostfleee.ragdolldeaths.client.RagdollStates;
import com.frostfleee.ragdolldeaths.core.RagdollPose;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This copy is for 1.21 and 1.21.1. It makes a dying mob's stand-in render state just before the mob is drawn (see
 * RagdollStates), and moves its flames and skips its shadow for a ragdoll body, as on later versions.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
	private static final String RENDER = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

	@Inject(method = RENDER, at = @At("HEAD"))
	private void ragdolldeaths$makeState(Entity entity, double x, double y, double z, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
			int light, CallbackInfo ci) {
		RagdollStates.capture(((EntityRenderDispatcher) (Object) this).getRenderer(entity), entity, partialTicks);
	}

	@Inject(method = RENDER, at = @At("TAIL"))
	private void ragdolldeaths$doneWithState(Entity entity, double x, double y, double z, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
			int light, CallbackInfo ci) {
		RagdollStates.current = null;
	}

	// A burning mob has its flames drawn where the entity is. Its body can be far from there, so they go where the
	// body is drawn instead of hanging in the air.
	@WrapOperation(method = RENDER, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderFlame(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;Lorg/joml/Quaternionf;)V"))
	private void ragdolldeaths$flamesOnBody(EntityRenderDispatcher dispatcher, PoseStack poseStack, MultiBufferSource buffers, Entity entity, Quaternionf rotation,
			Operation<Void> original) {
		LivingEntityRenderState state = RagdollStates.current;
		RagdollPose pose = state != null && state.entity == entity ? RagdollRenderData.pose(state) : null;
		if (pose == null) {
			original.call(dispatcher, poseStack, buffers, entity, rotation);
			return;
		}
		if (pose.hidden) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(pose.offsetX, pose.offsetY, pose.offsetZ);
		original.call(dispatcher, poseStack, buffers, entity, rotation);
		poseStack.popPose();
	}

	// The shadow is the entity's, under where it stands; a ragdoll body lies somewhere else, so it has none.
	@WrapOperation(method = RENDER, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V"))
	private void ragdolldeaths$noShadowUnderBody(PoseStack poseStack, MultiBufferSource buffers, Entity entity, float strength, float partialTicks, LevelReader level,
			float radius, Operation<Void> original) {
		LivingEntityRenderState state = RagdollStates.current;
		if (state == null || state.entity != entity || RagdollRenderData.pose(state) == null && !state.hideShadow) {
			original.call(poseStack, buffers, entity, strength, partialTicks, level, radius);
		}
	}
}
