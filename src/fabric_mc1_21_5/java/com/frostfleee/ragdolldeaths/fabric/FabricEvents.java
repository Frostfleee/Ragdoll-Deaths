package com.frostfleee.ragdolldeaths.fabric;

import com.frostfleee.ragdolldeaths.client.RagdollDeathsClient;
import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;

/**
 * Registers the game events the mod listens to. This copy is for 1.21.5 to 1.21.8, whose Fabric API still calls a level a
 * world; ghost bodies are added by a mixin on 1.21.x, as on NeoForge and Forge.
 */
final class FabricEvents {
	private FabricEvents() {
	}

	static void register(RagdollTracker tracker) {
		ClientTickEvents.END_WORLD_TICK.register(tracker::tick);
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> tracker.onUnload(entity));
		ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((minecraft, level) -> RagdollDeathsClient.onLevelChange());
	}
}
