package com.frostfleee.ragdolldeaths.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Turns off the game's own sitting, eating and similar poses for an animated body. This copy is for 1.21 and 1.21.1,
 * whose models read those poses from the entity itself rather than from a render state, so there is nothing to turn off
 * here; RagdollStates leaves them out instead.
 */
final class VanillaPoses {
	private VanillaPoses() {
	}

	static void neutralize(LivingEntityRenderState state) {
	}
}
