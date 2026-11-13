package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.RagdollRenderData;
import com.frostfleee.ragdolldeaths.core.RagdollPose;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
	// A burning mob's flames are drawn where the entity is. Its body can be far from there (falling, thrown, or
	// after the server removed the entity), so the flames go where the body is drawn instead of hanging in the air.
	@WrapOperation(
			method = "submit",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitFlame(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lorg/joml/Quaternionf;)V"))
	private void ragdolldeaths$flamesOnBody(SubmitNodeCollector collector, PoseStack poseStack, EntityRenderState state, Quaternionf rotation, Operation<Void> original) {
		RagdollPose pose = RagdollRenderData.pose(state);
		if (pose == null) {
			original.call(collector, poseStack, state, rotation);
			return;
		}
		if (pose.hidden) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(pose.offsetX, pose.offsetY, pose.offsetZ);
		original.call(collector, poseStack, state, rotation);
		poseStack.popPose();
	}
}
