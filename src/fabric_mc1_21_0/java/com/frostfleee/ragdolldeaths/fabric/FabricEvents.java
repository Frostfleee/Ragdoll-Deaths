package com.frostfleee.ragdolldeaths.fabric;

import com.frostfleee.ragdolldeaths.client.RagdollDeathsClient;
import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Registers the game events the mod listens to. This copy is for 1.21.2 to 1.21.4, whose Fabric API still calls a level a
 * world; ghost bodies are added by a mixin on 1.21.x, as on NeoForge and Forge.
 */
final class FabricEvents {
	private static net.minecraft.client.multiplayer.ClientLevel lastLevel;

	private FabricEvents() {
	}

	static void register(RagdollTracker tracker) {
		ClientTickEvents.END_WORLD_TICK.register(tracker::tick);
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> tracker.onUnload(entity));
		// This Fabric API has no event for the level changing: it is noticed at the end of each client tick.
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level != lastLevel) {
				lastLevel = minecraft.level;
				if (lastLevel != null) {
					RagdollDeathsClient.onLevelChange();
				}
			}
		});
	}
}
