package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.JointApplier;
import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import com.frostfleee.ragdolldeaths.client.PoseKeeper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Poses every model drawn while a ragdoll mob is being rendered: its own model and its layers (armor, clothes). This
 * copy is for 1.21 and 1.21.1, where Model's draw method is abstract: it hooks every model class that draws itself. A
 * model whose draw calls its parent's is posed twice, to the same pose.
 */
@Mixin({net.minecraft.client.model.HierarchicalModel.class, net.minecraft.client.model.AgeableHierarchicalModel.class,
		net.minecraft.client.model.ColorableHierarchicalModel.class, net.minecraft.client.model.AgeableListModel.class,
		net.minecraft.client.model.ColorableAgeableListModel.class, net.minecraft.client.model.ListModel.class,
		net.minecraft.client.model.CamelModel.class, net.minecraft.client.model.LlamaModel.class, net.minecraft.client.model.RabbitModel.class,
		net.minecraft.client.model.TurtleModel.class})
abstract class ModelFeatureRendererMixin {
	@Inject(method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"))
	private void ragdolldeaths$poseJoints(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
		Object state = PhysicsPoseApplier.drawing;
		if (state == null) {
			return;
		}
		Model model = (Model) (Object) this;
		PoseKeeper.remember(model);
		if (!PhysicsPoseApplier.apply(model, state, poseStack.last().pose())) {
			JointApplier.apply(model, state);
		}
	}
}
