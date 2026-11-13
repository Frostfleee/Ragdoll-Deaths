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
	public static final int DEFAULT_GHOST_TICKS = 60;
	public static final int MAX_GHOST_TICKS = 100;
	public static final int MAX_WARDEN_EXTRA_TICKS = 60;
	public static final int DEFAULT_BODY_LIFETIME_TICKS = 100;
	public static final int MAX_BODY_LIFETIME_TICKS = 1200;

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
	public boolean throwRiders = true;

	public int wardenExtraTicks = 20;

	public int ghostMaxTicks = DEFAULT_GHOST_TICKS;

	/** Real physics bodies. Off brings back the animation-only deaths, for weak computers. */
	public boolean physicsRagdolls = true;
	/** How long a physics body lies there before it vanishes, in ticks. */
	public int bodyLifetimeTicks = DEFAULT_BODY_LIFETIME_TICKS;
	/**
	 * Beta, off by default: cuts arms and legs in two so they bend at a knee or an elbow. Bodies crumple more
	 * convincingly, at the cost of ten parts a body instead of six and limbs that are no longer vanilla's one piece.
	 */
	public boolean kneesAndElbows = false;
	/** How long a dying body's muscles keep pulling before it is completely limp, in seconds. Zero is limp at once. */
	public double muscleSeconds = 1.5;
	/** Skeletons come apart at the joints when hit or landing hard. Off, they fall in one piece like other mobs. */
	public boolean skeletonsBreak = true;
	/** How much of the physics is worked out: Low is lighter on the computer, High is steadier in crowded piles. */
	public enum Quality {
		LOW, MEDIUM, HIGH
	}

	public Quality physicsQuality = Quality.MEDIUM;
	/** How hard explosions throw bodies. */
	public double explosionStrength = 1.0;
	/** The thud of a body hitting a block, a splash in water, the clatter of loose bones. */
	public boolean impactSounds = true;
	/** A puff of the block's dust on a hard landing, drops of water on a splash. */
	public boolean impactParticles = true;
	/** When you die, the view switches to third person so you see your own body fall; it switches back when you respawn. */
	public boolean deathCamera = false;
	/** A player's body stays until that player respawns, instead of going after the body lifetime. */
	public boolean playerBodyUntilRespawn = true;
	/** Arms thrown out in a forward fall, chin tucked in a backward one. */
	public boolean reflexes = true;
	/** The most physics bodies at once; the oldest goes when another one is needed. */
	public int maxBodies = 48;
	public List<String> excludedEntities = new ArrayList<>();

	private transient Set<String> excludedLookup = Set.of();

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
		bodyLifetimeTicks = Math.clamp(bodyLifetimeTicks, VANILLA_DEATH_TICKS, MAX_BODY_LIFETIME_TICKS);
		maxBodies = Math.clamp(maxBodies, 1, 256);
		muscleSeconds = finiteOr(muscleSeconds, defaults.muscleSeconds, 0.0, 5.0);
		explosionStrength = finiteOr(explosionStrength, defaults.explosionStrength, 0.0, 2.0);
		if (physicsQuality == null) {
			physicsQuality = defaults.physicsQuality;
		}
		if (waterBehavior == null) {
			waterBehavior = defaults.waterBehavior;
		}
		excludedEntities = cleanIds(excludedEntities, List.of());
		excludedLookup = Set.copyOf(excludedEntities);
		return this;
	}

	public boolean isExcluded(String entityId) {
		return excludedLookup.contains(entityId);
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
