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
	@Unique
	private Object ragdolldeaths$physics;
	@Unique
	private int ragdolldeaths$light = -1;
	@Unique
	private int ragdolldeaths$arrows;
	@Unique
	private int ragdolldeaths$arrowSeed;
	@Unique
	private boolean ragdolldeaths$arrowsOnDead;

	@Override
	public int ragdolldeaths$arrows() {
		return ragdolldeaths$arrows;
	}

	@Override
	public int ragdolldeaths$arrowSeed() {
		return ragdolldeaths$arrowSeed;
	}

	@Override
	public boolean ragdolldeaths$arrowsOnDead() {
		return ragdolldeaths$arrowsOnDead;
	}

	@Override
	public void ragdolldeaths$setArrows(int count, int seed, boolean dead) {
		ragdolldeaths$arrows = count;
		ragdolldeaths$arrowSeed = seed;
		ragdolldeaths$arrowsOnDead = dead;
	}

	@Override
	public int ragdolldeaths$light() {
		return ragdolldeaths$light;
	}

	@Override
	public void ragdolldeaths$setLight(int light) {
		ragdolldeaths$light = light;
	}

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

	@Override
	public Object ragdolldeaths$physics() {
		return ragdolldeaths$physics;
	}

	@Override
	public void ragdolldeaths$setPhysics(Object ragdoll) {
		ragdolldeaths$physics = ragdoll;
	}
}
