package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.JointPose;
import com.frostfleee.ragdolldeaths.core.RagdollPose;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** The ragdoll data carried on an entity's render state. EntityRenderStateMixin adds the fields to every state. */
public final class RagdollRenderData {
	private RagdollRenderData() {
	}

	public static RagdollPose pose(EntityRenderState state) {
		return ((Holder) state).ragdolldeaths$pose();
	}

	public static JointPose joints(EntityRenderState state) {
		return ((Holder) state).ragdolldeaths$joints();
	}

	public static void set(EntityRenderState state, RagdollPose pose, JointPose joints) {
		((Holder) state).ragdolldeaths$set(pose, joints);
	}

	/** The physics ragdoll this state is drawn from, or null. */
	static PhysicsRagdoll physics(EntityRenderState state) {
		return (PhysicsRagdoll) ((Holder) state).ragdolldeaths$physics();
	}

	/** Whether the state is a body that lava has burnt black. */
	public static boolean charred(EntityRenderState state) {
		PhysicsRagdoll ragdoll = physics(state);
		return ragdoll != null && ragdoll.charredWhole();
	}

	/** The texture to draw the state with: the body's own burnt copy when it has one. Also notes the mob's texture. */
	public static net.minecraft.resources.Identifier texture(EntityRenderState state, net.minecraft.resources.Identifier base) {
		PhysicsRagdoll ragdoll = physics(state);
		if (ragdoll == null) {
			return base;
		}
		ragdoll.baseTexture = base;
		return ragdoll.scorch != null ? ragdoll.scorch.id : base;
	}

	static void setPhysics(EntityRenderState state, PhysicsRagdoll ragdoll) {
		((Holder) state).ragdolldeaths$setPhysics(ragdoll);
	}

	public interface Holder {
		RagdollPose ragdolldeaths$pose();

		JointPose ragdolldeaths$joints();

		void ragdolldeaths$set(RagdollPose pose, JointPose joints);

		Object ragdolldeaths$physics();

		void ragdolldeaths$setPhysics(Object ragdoll);
	}
}
