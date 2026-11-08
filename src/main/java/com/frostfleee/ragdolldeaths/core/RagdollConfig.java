package com.frostfleee.ragdolldeaths.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class RagdollConfig {
	public static final int VANILLA_DEATH_TICKS = 20;
	public static final int MIN_LIFETIME_TICKS = 4;
	public static final int MAX_LIFETIME_TICKS = 60;
	public static final int DEFAULT_LIFETIME_TICKS = 30;
	public static final int MAX_GHOST_TICKS = 100;
	public static final int MAX_WARDEN_EXTRA_TICKS = 60;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public boolean enabled = true;
	public int lifetimeTicks = DEFAULT_LIFETIME_TICKS;
	public double animationSpeed = 1.0;
	public double maxRenderDistance = 64.0;
	public int maxActiveRagdolls = 64;
	public boolean playerRagdolls = true;
	public boolean hideDeathTint = true;

	public double hitReactionStrength = 1.0;
	public double bounceStrength = 1.0;
	public boolean limbAnimation = true;
	public boolean legKicks = true;

	public WaterBehavior waterBehavior = WaterBehavior.FLOAT;
	public boolean lavaSink = true;
	public boolean fallImpact = true;

	public int wardenExtraTicks = 20;

	public int ghostMaxTicks = 60;
	public List<String> excludedEntities = new ArrayList<>();
	public List<String> ghostEntities = new ArrayList<>(List.of("minecraft:ghast", "minecraft:happy_ghast"));

	private transient Set<String> excludedLookup = Set.of();
	private transient Set<String> ghostLookup = Set.of();

	public static RagdollConfig defaults() {
		return new RagdollConfig().sanitize();
	}

	/** Parses JSON, filling any missing or invalid values with defaults. Throws on malformed JSON. */
	public static RagdollConfig parse(String json) throws JsonParseException {
		RagdollConfig config = GSON.fromJson(json, RagdollConfig.class);
		return (config == null ? new RagdollConfig() : config).sanitize();
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	public RagdollConfig copy() {
		return parse(toJson());
	}

	public RagdollConfig sanitize() {
		RagdollConfig defaults = new RagdollConfig();
		lifetimeTicks = Math.clamp(lifetimeTicks, MIN_LIFETIME_TICKS, MAX_LIFETIME_TICKS);
		animationSpeed = finiteOr(animationSpeed, defaults.animationSpeed, 0.5, 2.0);
		maxRenderDistance = finiteOr(maxRenderDistance, defaults.maxRenderDistance, 4.0, 512.0);
		maxActiveRagdolls = Math.clamp(maxActiveRagdolls, 1, 512);
		hitReactionStrength = finiteOr(hitReactionStrength, defaults.hitReactionStrength, 0.0, 2.0);
		bounceStrength = finiteOr(bounceStrength, defaults.bounceStrength, 0.0, 2.0);
		wardenExtraTicks = Math.clamp(wardenExtraTicks, 0, MAX_WARDEN_EXTRA_TICKS);
		ghostMaxTicks = Math.clamp(ghostMaxTicks, Math.max(VANILLA_DEATH_TICKS, lifetimeTicks), MAX_GHOST_TICKS);
		if (waterBehavior == null) {
			waterBehavior = defaults.waterBehavior;
		}
		excludedEntities = cleanIds(excludedEntities, List.of());
		ghostEntities = cleanIds(ghostEntities, defaults.ghostEntities);
		excludedLookup = Set.copyOf(excludedEntities);
		ghostLookup = Set.copyOf(ghostEntities);
		return this;
	}

	public boolean isExcluded(String entityId) {
		return excludedLookup.contains(entityId);
	}

	public boolean usesGhost(String entityId) {
		return ghostLookup.contains(entityId);
	}

	public static List<String> splitIds(String text) {
		List<String> ids = new ArrayList<>();
		for (String part : text.split("[,\\s]+")) {
			if (!part.isBlank()) {
				ids.add(part);
			}
		}
		return cleanIds(ids, List.of());
	}

	private static double finiteOr(double value, double fallback, double min, double max) {
		return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
	}

	private static List<String> cleanIds(List<String> ids, List<String> fallback) {
		if (ids == null) {
			return new ArrayList<>(fallback);
		}
		return ids.stream()
				.filter(Objects::nonNull)
				.map(id -> id.trim().toLowerCase(Locale.ROOT))
				.filter(id -> !id.isEmpty())
				.map(id -> id.contains(":") ? id : "minecraft:" + id)
				.distinct()
				.collect(Collectors.toCollection(ArrayList::new));
	}
}
