package com.frostfleee.ragdolldeaths.fabric;

import com.frostfleee.ragdolldeaths.client.RagdollDeathsClient;
import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/** Registers the game events the mod listens to, and hooks ghost bodies into level extraction. This copy is for 26.1.x, whose Fabric API names the event LevelRenderEvents. */
final class FabricEvents {
	private FabricEvents() {
	}

	static void register(RagdollTracker tracker) {
		ClientTickEvents.END_LEVEL_TICK.register(tracker::tick);
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> tracker.onUnload(entity));
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((minecraft, level) -> RagdollDeathsClient.onLevelChange());
		LevelRenderEvents.END_EXTRACTION.register(context -> tracker.extractGhosts(
				context.level(), context.levelState().entityRenderStates, context.deltaTracker().getGameTimeDeltaPartialTick(true)));
	}
}
