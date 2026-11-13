package com.frostfleee.ragdolldeaths.client;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;

/**
 * The render side of physics ragdolls. Called wherever a model has just been posed for drawing (the mob's own model
 * and every armor or other layer model): the first time, with the mob's own model, the ragdoll is built from that
 * pose; from then on the model's parts are moved onto the simulated bodies.
 */
public final class PhysicsPoseApplier {
	private PhysicsPoseApplier() {
	}

	/** Notes, before anything of this mob is drawn this frame, which model is its own and where the camera is. */
	public static void begin(LivingEntityRenderer<?, ?, ?> renderer, EntityRenderState state, CameraRenderState camera) {
		PhysicsRagdoll ragdoll = RagdollRenderData.physics(state);
		if (ragdoll == null) {
			return;
		}
		ragdoll.mainModel = renderer.getModel();
		ragdoll.camX = camera.pos.x;
		ragdoll.camY = camera.pos.y;
		ragdoll.camZ = camera.pos.z;
	}

	/**
	 * @param pose the matrix the model is drawn with
	 * @return true when the state belongs to a physics ragdoll, so the classic joint animation must not run
	 */
	public static boolean apply(Model<?> model, Object state, Matrix4f pose) {
		// A model that has knees and elbows for ragdolls is drawn with whole limbs for everything else.
		LimbSplitter.rest(model.root());
		PhysicsRagdoll.showHidden(model.root());
		if (!(state instanceof EntityRenderState renderState)) {
			return false;
		}
		PhysicsRagdoll ragdoll = RagdollRenderData.physics(renderState);
		if (ragdoll == null || ragdoll.failed) {
			return false;
		}
		try {
			if (!ragdoll.built()) {
				// Until the mob's own model comes by, everything keeps the pose the game gave it: that is the pose
				// the ragdoll will start from.
				if (model == ragdoll.mainModel) {
					RagdollBuilder.build(ragdoll, model, state, pose);
					PhysicsHost.INSTANCE.add(ragdoll);
				}
				return true;
			}
			ragdoll.write(model, pose);
			return true;
		} catch (RuntimeException e) {
			ragdoll.failed = true;
			RagdollDeathsClient.LOGGER.error("Physics ragdoll failed for {}; it falls back to the classic death", EntityFilter.idOf(ragdoll.entity.getType()), e);
			return false;
		}
	}
}
