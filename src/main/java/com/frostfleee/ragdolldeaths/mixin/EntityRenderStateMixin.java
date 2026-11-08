package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.RagdollRenderData;
import com.frostfleee.ragdolldeaths.core.JointPose;
import com.frostfleee.ragdolldeaths.core.RagdollPose;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
abstract class EntityRenderStateMixin implements RagdollRenderData.Holder {
	@Unique
	private RagdollPose ragdolldeaths$pose;
	@Unique
	private JointPose ragdolldeaths$joints;

	@Override
	public RagdollPose ragdolldeaths$pose() {
		return ragdolldeaths$pose;
	}

	@Override
	public JointPose ragdolldeaths$joints() {
		return ragdolldeaths$joints;
	}

	@Override
	public void ragdolldeaths$set(RagdollPose pose, JointPose joints) {
		ragdolldeaths$pose = pose;
		ragdolldeaths$joints = joints;
	}
}
