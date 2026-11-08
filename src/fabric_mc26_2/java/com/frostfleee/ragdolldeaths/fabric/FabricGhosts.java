package com.frostfleee.ragdolldeaths.fabric;

import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;

/** Hooks ghost bodies into level extraction. This copy is for 26.2 and later. */
final class FabricGhosts {
	private FabricGhosts() {
	}

	static void register(RagdollTracker tracker) {
		LevelExtractionEvents.END_EXTRACTION.register(context -> tracker.extractGhosts(
				context.level(), context.levelState(), context.deltaTracker().getGameTimeDeltaPartialTick(true)));
	}
}
