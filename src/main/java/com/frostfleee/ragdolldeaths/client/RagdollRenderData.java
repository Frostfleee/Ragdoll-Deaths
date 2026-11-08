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

	public interface Holder {
		RagdollPose ragdolldeaths$pose();

		JointPose ragdolldeaths$joints();

		void ragdolldeaths$set(RagdollPose pose, JointPose joints);
	}
}
