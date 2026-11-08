package com.frostfleee.ragdolldeaths.fabric;

import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/** Hooks ghost bodies into level extraction. This copy is for 26.1.x, whose Fabric API names the event LevelRenderEvents. */
final class FabricGhosts {
	private FabricGhosts() {
	}

	static void register(RagdollTracker tracker) {
		LevelRenderEvents.END_EXTRACTION.register(context -> tracker.extractGhosts(
				context.level(), context.levelState(), context.deltaTracker().getGameTimeDeltaPartialTick(true)));
	}
}
