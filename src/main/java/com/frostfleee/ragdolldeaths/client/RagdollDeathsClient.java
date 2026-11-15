package com.frostfleee.ragdolldeaths.client;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared startup. Each loader's entrypoint calls init, then forwards its client events to RagdollTracker. */
public final class RagdollDeathsClient {
	public static final String MOD_ID = "ragdolldeaths";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private RagdollDeathsClient() {
	}

	public static void init(Path configDirectory) {
		ConfigStore.setDirectory(configDirectory);
		ConfigStore.load();
		EmfCompat.init();
	}

	/** Called when the client switches to another level, or leaves one. */
	public static void onLevelChange() {
		RagdollTracker.INSTANCE.clear();
		ConfigStore.load();
	}
}
