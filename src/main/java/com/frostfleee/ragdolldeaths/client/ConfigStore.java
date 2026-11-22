package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigStore {
	private static final String FILE_NAME = RagdollDeathsClient.MOD_ID + ".json";

	private static volatile RagdollConfig current = RagdollConfig.defaults();
	private static Path directory = Path.of("config");

	private ConfigStore() {
	}

	/** Set by the loader entrypoint before the first load. */
	public static void setDirectory(Path configDirectory) {
		directory = configDirectory;
	}

	public static RagdollConfig get() {
		return current;
	}

	public static void load() {
		if (SelfTest.ENABLED) {
			RagdollDeathsClient.LOGGER.info("SELFTEST settings loaded from the file (on {})", Thread.currentThread().getName());
		}
		Path path = path();
		if (Files.notExists(path)) {
			current = RagdollConfig.defaults();
			write(path, current);
			return;
		}

		try {
			current = RagdollConfig.parse(Files.readString(path));
			write(path, current);
		} catch (IOException | JsonParseException e) {
			RagdollDeathsClient.LOGGER.warn("Could not read {}, using defaults until it is fixed: {}", path, e.getMessage());
			current = RagdollConfig.defaults();
		}
	}

	/** Applies new settings immediately and writes them to disk. */
	public static void save(RagdollConfig config) {
		current = config.sanitize();
		write(path(), current);
	}

	private static Path path() {
		return directory.resolve(FILE_NAME);
	}

	private static void write(Path path, RagdollConfig config) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, config.toJson());
		} catch (IOException e) {
			RagdollDeathsClient.LOGGER.warn("Could not write {}: {}", path, e.getMessage());
		}
	}
}
