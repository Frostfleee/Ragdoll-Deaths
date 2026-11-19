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
	public static PhysicsRagdoll physics(EntityRenderState state) {
		return (PhysicsRagdoll) ((Holder) state).ragdolldeaths$physics();
	}

	/** Whether the state is a body that lava has burnt black. */
	public static boolean charred(EntityRenderState state) {
		PhysicsRagdoll ragdoll = physics(state);
		return ragdoll != null && ragdoll.charredWhole();
	}

	/** The texture to draw the state with: the body's own burnt copy when it has one. Also notes the mob's texture. */
	@SuppressWarnings("unchecked")
	public static <T> T texture(EntityRenderState state, T base) {
		PhysicsRagdoll ragdoll = physics(state);
		if (ragdoll == null) {
			return base;
		}
		ragdoll.baseTexture = base;
		return ragdoll.scorch != null ? (T) ragdoll.scorch.id : base;
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

		/** The light to draw the body with, or -1 for the entity's own; for versions whose state has no light. */
		int ragdolldeaths$light();

		void ragdolldeaths$setLight(int light);

		/** How many stuck arrows to draw on this mob, and the mob's id, which decides where they sit. */
		int ragdolldeaths$arrows();

		int ragdolldeaths$arrowSeed();

		boolean ragdolldeaths$arrowsOnDead();

		void ragdolldeaths$setArrows(int count, int seed, boolean dead);
	}

	/** Forgets everything about the last body this state was filled for: some versions reuse one state per renderer. */
	public static void clear(EntityRenderState state) {
		set(state, null, null);
		setPhysics(state, null);
		((Holder) state).ragdolldeaths$setLight(-1);
		setArrows(state, 0, 0, false);
	}

	public static int arrows(EntityRenderState state) {
		return ((Holder) state).ragdolldeaths$arrows();
	}

	public static int arrowSeed(EntityRenderState state) {
		return ((Holder) state).ragdolldeaths$arrowSeed();
	}

	public static boolean arrowsOnDead(EntityRenderState state) {
		return ((Holder) state).ragdolldeaths$arrowsOnDead();
	}

	static void setArrows(EntityRenderState state, int count, int seed, boolean dead) {
		((Holder) state).ragdolldeaths$setArrows(count, seed, dead);
	}

	/** The light to draw the state with: the body's own when it set one, else the light the game passes. */
	public static int light(EntityRenderState state, int light) {
		int own = ((Holder) state).ragdolldeaths$light();
		return own >= 0 ? own : light;
	}

	static void setLight(EntityRenderState state, int light) {
		((Holder) state).ragdolldeaths$setLight(light);
	}
}
