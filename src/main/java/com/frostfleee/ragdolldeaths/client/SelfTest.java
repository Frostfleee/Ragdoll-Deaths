package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.mixin.CreateWorldScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.server.MinecraftServer;

/**
 * A check that the mod works in a real world, for loaders and game versions without an automated test. It runs only
 * when the game is started with -Dragdolldeaths.selftest=true: it creates a world, kills a few mobs and then the player,
 * writes what happened to the log (lines starting with "SELFTEST"), and closes the game.
 */
public final class SelfTest {
	public static final boolean ENABLED = Boolean.getBoolean("ragdolldeaths.selftest");

	private static final String[] MOBS = {"zombie", "skeleton", "creeper", "spider", "cow", "shulker", "slime{Size:1}", "villager", "enderman", "piglin", "horse", "wolf", "cat", "pillager", "vindicator"};

	private static int ticks;
	private static int inWorld;
	private static int startServerTick = -1;
	private static int createScreenTicks;
	private static boolean opened;
	private static boolean created;
	private static final java.util.Set<String> missing = new java.util.TreeSet<>(java.util.List.of(MOBS));
	private static final java.util.List<String> problems = new java.util.ArrayList<>();
	private static long drawsAfterMobs;
	private static net.minecraft.world.entity.player.Player deadPlayer;
	private static boolean playerBody;
	private static boolean keptPastLifetime;
	private static long farDrawsBefore;
	private static net.minecraft.world.entity.LivingEntity farZombie;
	private static boolean farBodyDrawn;

	private SelfTest() {
	}

	public static void clientTick(Minecraft minecraft) {
		if (!ENABLED) {
			return;
		}
		ticks++;
		// The steps follow the server's ticks, which stop while the game is paused: a test window in the background
		// must not pause (for this run only, not saved).
		minecraft.options.pauseOnLostFocus = false;
		if (minecraft.level == null || minecraft.player == null) {
			if (ticks % 200 == 0) {
				log("waiting on " + (Compat.screen() == null ? "no screen" : Compat.screen().getClass().getName()));
			}
			// From the title screen, or after 30 seconds on whatever screen a loader put up first (a warning, say)
			if (!opened && (Compat.screen() instanceof TitleScreen || ticks > 600 && !(Compat.screen() instanceof CreateWorldScreen))) {
				opened = true;
				log("title screen reached; creating a world");
				Compat.openCreateWorld(minecraft);
			} else if (!created && Compat.screen() instanceof CreateWorldScreen screen && ++createScreenTicks > 20) {
				created = true;
				((CreateWorldScreenAccessor) screen).ragdolldeaths$create();
			}
			if (ticks > 20 * 600) {
				log("FAILED: no world after 10 minutes");
				minecraft.stop();
			}
			return;
		}
		// The steps follow the game's own (server) ticks: on a busy machine the server can fall behind the client, and its
		// commands land late.
		MinecraftServer server = minecraft.getSingleplayerServer();
		if (startServerTick < 0) {
			startServerTick = server.getTickCount();
		}
		int now = server.getTickCount() - startServerTick + 1;
		while (inWorld < now) {
			inWorld++;
			step(minecraft, server, inWorld);
		}
	}

	private static void step(Minecraft minecraft, MinecraftServer server, int inWorld) {
		// The zombie's body is built on the first frame it is drawn: it is looked for every tick until it is there.
		if (inWorld >= 222 && inWorld <= 238 && farZombie == null) {
			for (PhysicsRagdoll ragdoll : PhysicsHost.INSTANCE.ragdolls()) {
				if (ragdoll.built() && "minecraft:zombie".equals(EntityFilter.idOf(ragdoll.entity.getType()))) {
					ragdoll.moveForTest(0.0, 0.0, 90.0);
					farZombie = ragdoll.entity;
					run(server, "execute as @p at @p run tp @s ~ ~ ~90");
				}
			}
		}
		switch (inWorld) {
			case 1 -> log("in world");
			case 60 -> {
				log("player at " + minecraft.player.blockPosition());
				run(server, "gamerule doMobLoot false", "gamerule mob_drops false", "gamerule doTileDrops false", "gamerule block_drops false", "time set noon", "weather clear", "difficulty easy",
						"gamemode creative @a");
				// A stone stage up in the sky, the player standing on it looking at it, so every world gives the same scene
				// (a world can put the player in a forest, on an iceberg or by water, with its land still loading).
				run(server, "execute at @p positioned ~ 200 ~ rotated ~ 0 run fill ^-10 ^ ^-2 ^10 ^ ^14 minecraft:stone",
						"execute as @p at @p run tp @s ~ 201 ~ ~ 25");
				for (int i = 0; i < MOBS.length; i++) {
					String[] mob = MOBS[i].split("[{]", 2);
					String nbt = mob.length > 1 ? "{NoAI:1b," + mob[1] : "{NoAI:1b}";
					// two rows of eight, 2.5 blocks apart, all on the stage
					run(server, String.format(java.util.Locale.ROOT, "execute at @p positioned ~ 201 ~ rotated ~ 0 run summon minecraft:%s ^%.1f ^ ^%d %s", mob[0],
							(i % 8) * 2.5 - 8.75, i < 8 ? 7 : 11, nbt));
				}
			}
			case 99 -> {
				StringBuilder seen = new StringBuilder();
				for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
					if (entity.distanceTo(minecraft.player) < 20 && entity instanceof net.minecraft.world.entity.LivingEntity)
					seen.append(' ').append(EntityFilter.idOf(entity.getType()));
				}
				log("entities before the kill:" + seen);
			}
			case 100 -> run(server, "execute at @p run kill @e[type=!minecraft:player,distance=..20]");
			// Living mobs of kinds that have a body lying on the stage, close to the camera for the screenshots: a kind's
			// model is shared, and a body's pose must not show on the living (on 1.21 it did).
			case 104 -> {
				String[] living = {"cow", "horse", "wolf", "villager"};
				for (int i = 0; i < living.length; i++) {
					run(server, String.format(java.util.Locale.ROOT, "execute at @p positioned ~ 201 ~ rotated ~ 0 run summon minecraft:%s ^%.1f ^ ^4 {NoAI:1b}",
							living[i], i * 2.0 - 3.0));
				}
			}
			case 106, 115, 135, 370 -> {
				Compat.screenshot();
				log("screenshot taken");
			}
			case 140 -> {
				report(minecraft);
				for (PhysicsRagdoll ragdoll : PhysicsHost.INSTANCE.ragdolls()) {
					String id = EntityFilter.idOf(ragdoll.entity.getType()).replace("minecraft:", "");
					missing.remove(id.equals("slime") ? "slime{Size:1}" : id);
					if (ragdoll.failed || ragdoll.partsInsideBlocks() > 0) {
						problems.add(id + (ragdoll.failed ? " failed" : " inside a block"));
					}
				}
				drawsAfterMobs = PhysicsPoseApplier.writes;
			}
			// A body far from where its mob died, the camera following it: a zombie is killed on the stage, then its body
			// (not the entity, which stays where it died) is carried 90 blocks to a second stage, with the player. That is
			// past the game's ~67-block draw distance for a zombie, measured from the entity.
			case 210 -> run(server, "execute at @p positioned ~ 201 ~ rotated ~ 0 run summon minecraft:zombie ^ ^ ^5 {NoAI:1b}",
					"execute at @p run fill ~-12 200 ~78 ~12 200 ~102 minecraft:stone");
			case 215 -> run(server, "execute at @p run kill @e[type=minecraft:zombie,distance=..20]");
			case 240 -> farDrawsBefore = PhysicsPoseApplier.writes; // after the dying zombie is removed (deathTime 20)
			case 280 -> {
				long draws = PhysicsPoseApplier.writes - farDrawsBefore;
				farBodyDrawn = farZombie != null && draws > 0;
				log("body 90 blocks from where its zombie died: " + (farZombie == null ? "no body" : draws + " posed draws in 40 ticks, entity "
						+ String.format(java.util.Locale.ROOT, "%.0f", farZombie.distanceTo(minecraft.player)) + " blocks from the player"));
			}
			case 300 -> report(minecraft);
			case 320 -> {
				// For this run only (not saved): the player's own body is drawn, followed, and kept until the respawn.
				// Set late, as the settings are read again when a world is joined.
				ConfigStore.get().deathCamera = true;
				ConfigStore.get().playerRagdolls = true;
				ConfigStore.get().playerBodyUntilRespawn = true;
				ConfigStore.get().bodyLifetimeTicks = 100;
				run(server, "gamemode survival @a", "kill @a");
			}
			case 380 -> {
				report(minecraft);
				deadPlayer = minecraft.player;
				log("player: " + RagdollTracker.INSTANCE.describe(minecraft.player) + ", camera " + minecraft.options.getCameraType());
				playerBody = RagdollTracker.INSTANCE.bodyHead(minecraft.player, 0.0F) != null;
			}
			case 480 -> {
				// 160 ticks after death, well past the 100-tick body lifetime
				keptPastLifetime = RagdollTracker.INSTANCE.bodyHead(deadPlayer, 0.0F) != null;
				log("player body 160 ticks after death: " + (keptPastLifetime ? "still there" : "gone") + "; respawning");
				minecraft.player.respawn();
			}
			case 540 -> {
				boolean back = minecraft.player != deadPlayer && !minecraft.player.isDeadOrDying();
				boolean goneOnRespawn = RagdollTracker.INSTANCE.bodyHead(deadPlayer, 0.0F) == null;
				log("after respawn: player " + (back ? "back" : "not back") + ", old body " + (goneOnRespawn ? "gone" : "still there"));
				boolean pass = missing.isEmpty() && problems.isEmpty() && drawsAfterMobs > 0 && playerBody && keptPastLifetime && back && goneOnRespawn && farBodyDrawn;
				log("RESULT " + (pass ? "PASS" : "FAIL") + ": mobs without a body " + missing + ", problems " + problems
						+ ", posed draws " + drawsAfterMobs + ", player body " + (playerBody ? "followed by the camera" : "missing")
						+ (keptPastLifetime ? ", kept past its lifetime" : ", NOT kept past its lifetime")
						+ (back && goneOnRespawn ? ", gone on respawn" : ", NOT gone on respawn")
						+ (farBodyDrawn ? ", drawn far from its entity" : ", NOT drawn far from its entity"));
				minecraft.stop();
			}
			default -> {
			}
		}
	}

	private static void report(Minecraft minecraft) {
		var lines = RagdollTracker.INSTANCE.physicsReport();
		log(lines.size() + " physics bodies, " + PhysicsPoseApplier.writes + " posed draws so far");
		for (String line : lines) {
			log("  " + line);
		}
	}

	private static void run(MinecraftServer server, String... commands) {
		server.execute(() -> {
			for (String command : commands) {
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
			}
		});
	}

	private static void log(String message) {
		RagdollDeathsClient.LOGGER.info("SELFTEST {}", message);
	}
}
