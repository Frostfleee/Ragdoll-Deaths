package com.frostfleee.ragdolldeaths.fabric;

import com.frostfleee.ragdolldeaths.client.RagdollDeathsClient;
import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/** The Fabric and Quilt entrypoint. */
public final class FabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RagdollDeathsClient.init(FabricLoader.getInstance().getConfigDir());
		RagdollTracker tracker = RagdollTracker.INSTANCE;

		ClientTickEvents.END_LEVEL_TICK.register(tracker::tick);
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> tracker.onUnload(entity));
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((minecraft, level) -> RagdollDeathsClient.onLevelChange());
		FabricGhosts.register(tracker);
	}
}
