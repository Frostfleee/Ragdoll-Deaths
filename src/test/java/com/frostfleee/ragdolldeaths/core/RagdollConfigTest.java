package com.frostfleee.ragdolldeaths.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParseException;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagdollConfigTest {
	@Test
	void defaultsMatchTheAgreedBehavior() {
		RagdollConfig config = RagdollConfig.defaults();
		assertTrue(config.enabled);
		assertEquals(100, config.bodyLifetimeTicks);
		assertTrue(config.deathCamera);
		assertTrue(config.playerRagdolls);
		assertEquals(WaterBehavior.FLOAT, config.waterBehavior);
		assertTrue(config.lavaSink);
		assertTrue(config.fallImpact);
		assertEquals(1.0, config.hitReactionStrength);
		assertEquals(60, config.ghostMaxTicks);
		assertFalse(config.isExcluded("minecraft:zombie"));
		assertTrue(config.stuckArrows);
		assertFalse(config.stuckArrowsOnLiving);
		assertFalse(config.kneesAndElbows);
	}

	@Test
	void missingFieldsFallBackToDefaults() {
		RagdollConfig config = RagdollConfig.parse("{\"playerRagdolls\": false}");
		assertFalse(config.playerRagdolls);
		assertEquals(100, config.bodyLifetimeTicks);
		assertTrue(config.excludedEntities.isEmpty());
	}

	@Test
	void settingsFromOlderVersionsAreIgnored() {
		RagdollConfig config = RagdollConfig.parse("{\"ghostEntities\": [\"phantom\"], \"explosionLaunch\": false, \"lavaSink\": false}");
		assertFalse(config.lavaSink);
		assertFalse(config.toJson().contains("ghostEntities"));
		assertFalse(config.toJson().contains("explosionLaunch"));
	}

	@Test
	void emptyDocumentGivesDefaults() {
		assertEquals(RagdollConfig.defaults().toJson(), RagdollConfig.parse("").toJson());
	}

	@Test
	void lifetimeIsClampedToItsRange() {
		assertEquals(RagdollConfig.MAX_BODY_LIFETIME_TICKS, RagdollConfig.parse("{\"bodyLifetimeTicks\": 99999}").bodyLifetimeTicks);
		assertEquals(RagdollConfig.VANILLA_DEATH_TICKS, RagdollConfig.parse("{\"bodyLifetimeTicks\": 0}").bodyLifetimeTicks);
		assertEquals(60, RagdollConfig.parse("{\"bodyLifetimeTicks\": 60}").bodyLifetimeTicks);
	}

	@Test
	void numericValuesAreClamped() {
		RagdollConfig config = RagdollConfig.parse("""
				{"maxRenderDistance": 99999, "bounceStrength": -3, "hitReactionStrength": 7, "animationSpeed": 9, "wardenExtraTicks": 500,
				 "ghostMaxTicks": 5, "maxActiveRagdolls": 0}
				""");
		assertEquals(512.0, config.maxRenderDistance);
		assertEquals(0.0, config.bounceStrength);
		assertEquals(2.0, config.animationSpeed);
		assertEquals(RagdollConfig.MAX_WARDEN_EXTRA_TICKS, config.wardenExtraTicks);
		assertEquals(2.0, config.hitReactionStrength);
		assertEquals(RagdollConfig.VANILLA_DEATH_TICKS, config.ghostMaxTicks);
		assertEquals(1, config.maxActiveRagdolls);
	}

	@Test
	void settingsRemovedIn121StillLoad() {
		// Files from 1.2.0 and older hold Max Physics Bodies, Dying Muscles and Skeletons Break Apart: they load, the
		// old values are dropped, and the rest of the file is kept.
		RagdollConfig config = RagdollConfig.parse("""
				{"maxBodies": 12, "muscleSeconds": 3.0, "skeletonsBreak": false, "maxActiveRagdolls": 30, "blood": true}
				""");
		assertEquals(30, config.maxActiveRagdolls);
		assertTrue(config.blood);
		String written = config.toJson();
		assertTrue(!written.contains("maxBodies") && !written.contains("muscleSeconds") && !written.contains("skeletonsBreak"), written);
	}

	@Test
	void entityListsParseFromTypedText() {
		assertEquals(List.of("minecraft:zombie", "somemod:worm"), RagdollConfig.splitIds(" zombie,somemod:worm ,, zombie "));
		assertTrue(RagdollConfig.splitIds("   ").isEmpty());
	}

	@Test
	void copyIsIndependent() {
		RagdollConfig original = RagdollConfig.defaults();
		RagdollConfig copy = original.copy();
		copy.enabled = false;
		assertTrue(original.enabled);
	}

	@Test
	void aBodyStaysItsWholeLifetimePastTheGhostLimit() {
		RagdollConfig config = RagdollConfig.parse("{\"bodyLifetimeTicks\": 600, \"ghostMaxTicks\": 25}");
		assertEquals(25, config.ghostMaxTicks);
		assertEquals(600.0, RagdollAnimator.endTick(DeathProfile.builder(BodyKind.STANDARD).size(0.9, 0.9).build(), config));
		assertEquals(600.0, RagdollAnimator.endTick(DeathProfile.builder(BodyKind.GHAST).size(4.0, 4.0).floorDrop(-2.0).build(), config));
	}

	@Test
	void theOldClassicLifetimeIsIgnored() {
		RagdollConfig config = RagdollConfig.parse("{\"lifetimeTicks\": 12}");
		assertEquals(RagdollConfig.DEFAULT_BODY_LIFETIME_TICKS, config.bodyLifetimeTicks);
		assertFalse(config.toJson().contains("\"lifetimeTicks\""));
	}

	@Test
	void unknownEnumValueFallsBackToDefault() {
		assertEquals(WaterBehavior.FLOAT, RagdollConfig.parse("{\"waterBehavior\": \"BOUNCE\"}").waterBehavior);
		assertEquals(WaterBehavior.SINK, RagdollConfig.parse("{\"waterBehavior\": \"SINK\"}").waterBehavior);
	}

	@Test
	void entityIdsAreNormalized() {
		RagdollConfig config = RagdollConfig.parse("""
				{"excludedEntities": [" Zombie ", "mod_name:Mob_Name", "", null, "zombie"]}
				""");
		assertEquals(List.of("minecraft:zombie", "mod_name:mob_name"), config.excludedEntities);
		assertTrue(config.isExcluded("mod_name:mob_name"));
	}

	@Test
	void nullListsRestoreDefaults() {
		RagdollConfig config = RagdollConfig.parse("{\"excludedEntities\": null}");
		assertTrue(config.excludedEntities.isEmpty());
	}

	@Test
	void malformedJsonIsReported() {
		assertThrows(JsonParseException.class, () -> RagdollConfig.parse("{\"enabled\": tru"));
	}

	@Test
	void roundTripsThroughJson() {
		RagdollConfig original = RagdollConfig.parse("{\"waterBehavior\": \"NEUTRAL\", \"bounceStrength\": 1.5}");
		RagdollConfig copy = RagdollConfig.parse(original.toJson());
		assertEquals(original.toJson(), copy.toJson());
		assertEquals(WaterBehavior.NEUTRAL, copy.waterBehavior);
	}
}
