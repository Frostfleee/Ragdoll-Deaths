package com.frostfleee.ragdolldeaths.fabric;

import com.frostfleee.ragdolldeaths.client.RagdollDeathsClient;
import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** The Fabric and Quilt entrypoint. */
public final class FabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RagdollDeathsClient.init(FabricLoader.getInstance().getConfigDir());
		RagdollTracker tracker = RagdollTracker.INSTANCE;

		FabricEvents.register(tracker);
	}
}
