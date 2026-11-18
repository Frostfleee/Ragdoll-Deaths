package com.frostfleee.ragdolldeaths.client;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Matrix4f;

/**
 * The render side of physics ragdolls. Called wherever a model has just been posed for drawing (the mob's own model
 * and every armor or other layer model): the first time, with the mob's own model, the ragdoll is built from that
 * pose; from then on the model's parts are moved onto the simulated bodies.
 */
public final class PhysicsPoseApplier {
	/**
	 * The render state of the ragdoll mob being drawn (or, from 1.21.9, submitted) right now; null otherwise. Armor
	 * and held-item layers ask it which body they belong to. Drawing runs on one thread.
	 */
	public static Object drawing;

	private static PhysicsRagdoll drawingPhysics() {
		return drawing instanceof EntityRenderState state ? RagdollRenderData.physics(state) : null;
	}

	/**
	 * The texture to draw an armor (or other layer) piece with while a physics body is drawn: its burnt copy once lava
	 * has reached it. Anything else keeps its texture.
	 */
	public static <T> T layerTexture(T texture, Model model, net.minecraft.world.item.ItemStack stack) {
		PhysicsRagdoll ragdoll = drawingPhysics();
		return ragdoll == null || ragdoll.failed ? texture : ragdoll.layerTexture(texture, model, stack);
	}

	/** The light to draw a held item with: none (burnt black) when the hand holding it went into lava, else its own. */
	public static int heldItemLight(net.minecraft.world.entity.HumanoidArm arm, int light) {
		PhysicsRagdoll ragdoll = drawingPhysics();
		return ragdoll != null && !ragdoll.failed && ragdoll.built() && ragdoll.heldItemBurnt(arm) ? 0 : light;
	}

	/** How many times a model was posed onto a physics body; the self-test reports it to show bodies are drawn. */
	public static long writes;

	private PhysicsPoseApplier() {
	}

	/** Notes, before anything of this mob is drawn this frame, which model is its own and where the camera is. */
	public static void begin(LivingEntityRenderer renderer, EntityRenderState state, net.minecraft.world.phys.Vec3 camera) {
		PhysicsRagdoll ragdoll = RagdollRenderData.physics(state);
		if (ragdoll == null) {
			return;
		}
		ragdoll.mainModel = renderer.getModel();
		ragdoll.camX = camera.x;
		ragdoll.camY = camera.y;
		ragdoll.camZ = camera.z;
	}

	/**
	 * @param pose the matrix the model is drawn with
	 * @return true when the state belongs to a physics ragdoll, so the classic joint animation must not run
	 */
	public static boolean apply(Model model, Object state, Matrix4f pose) {
		// A model that has knees and elbows for ragdolls is drawn with whole limbs for everything else.
		LimbSplitter.rest(Compat.root(model));
		PhysicsRagdoll.showHidden(Compat.root(model));
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
			writes++;
			return true;
		} catch (RuntimeException e) {
			ragdoll.failed = true;
			RagdollDeathsClient.LOGGER.error("Physics ragdoll failed for {}; it falls back to the classic death", EntityFilter.idOf(ragdoll.entity.getType()), e);
			return false;
		}
	}
}
