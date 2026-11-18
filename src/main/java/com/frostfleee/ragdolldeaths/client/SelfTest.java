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
 *
 * <p>Besides the row of mobs it checks a skeleton killed on its skeleton horse (a rider becomes a physics body), a
 * mannequin where the game has them (a body like a player's), and a zombie killed in lava wearing a netherite helmet
 * and an iron chestplate and holding an iron sword: the chestplate and the sword burn, the netherite does not.
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
	private static boolean riderBody, mannequinBody, chestBurnt, netheriteKept, swordBurnt;
	// Push Dead Bodies: the player walks through the cow's body once it rests, and it must have moved.
	private static PhysicsRagdoll pushed;
	private static double pushFromX, pushFromZ, pushY, homeX, homeY, homeZ, restX, restZ;
	private static boolean pushMoved;
	/** A zombie killed above the build limit: its body must fall, not be let go at once. */
	private static boolean highBodyFell;
	/** Whether this game version has mannequins (1.21.9 and later). */
	private static final boolean HAS_MANNEQUINS = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().stream()
			.anyMatch(id -> id.getPath().equals("mannequin"));

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
		if (pushed != null && inWorld > 150 && inWorld <= 177) {
			// 0.15 blocks a tick, a walk, from two blocks before the body's middle to two past it
			minecraft.player.setPos(pushFromX + (inWorld - 150) * 0.15, pushY, pushFromZ);
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
				// A skeleton on a skeleton horse, a mannequin, and a lava pit in the stage's corner (stone under it) with an
				// armored zombie in it that the lava cannot kill before the others die.
				run(server, "execute at @p positioned ~ 201 ~ rotated ~ 0 run summon minecraft:skeleton_horse ^-6 ^ ^14 {NoAI:1b,Passengers:[{id:\"minecraft:skeleton\",NoAI:1b,Tags:[\"rider\"]}]}",
						"execute at @p positioned ~ 201 ~ rotated ~ 0 run summon minecraft:mannequin ^-9 ^ ^14",
						"execute at @p positioned ~ 199 ~ rotated ~ 0 run fill ^7 ^ ^12 ^9 ^ ^14 minecraft:stone",
						"execute at @p positioned ~ 200 ~ rotated ~ 0 run fill ^7 ^ ^12 ^9 ^ ^14 minecraft:lava",
						"execute at @p positioned ~ 201 ~ rotated ~ 0 run summon minecraft:zombie ^8 ^ ^13 {NoAI:1b,Tags:[\"lava\"],active_effects:[{id:\"minecraft:fire_resistance\",duration:2000}]}",
						"item replace entity @e[tag=lava] armor.head with minecraft:netherite_helmet",
						"item replace entity @e[tag=lava] armor.chest with minecraft:iron_chestplate",
						"item replace entity @e[tag=lava] weapon.mainhand with minecraft:iron_sword");
			}
			// A summon that did not land (it happened once, with no error) is logged and tried again before the kill.
			case 80 -> {
				boolean there = false;
				for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
					there |= EntityFilter.idOf(entity.getType()).equals("minecraft:mannequin");
				}
				if (HAS_MANNEQUINS && !there) {
					log("no mannequin after the summon; summoning it again");
					run(server, "execute at @p positioned ~ 201 ~ rotated ~ 0 run summon minecraft:mannequin ^-9 ^ ^14");
				}
			}
			// The rider first, so that it dies still seated on its living mount.
			case 96 -> run(server, "kill @e[tag=rider]");
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
					if (ragdoll.failed || ragdoll.partsInsideBlocks() > 0 && !ragdoll.charred) {
						problems.add(id + (ragdoll.failed ? " failed" : " inside a block"));
					}
					riderBody |= ragdoll.seatThrow != null && ragdoll.built();
					mannequinBody |= id.equals("mannequin") && ragdoll.built();
					if (ragdoll.charred && id.equals("zombie")) {
						java.util.List<String> layers = ragdoll.layerReport();
						log("zombie in lava: layers " + layers + ", sword " + (ragdoll.heldItemBurnt(net.minecraft.world.entity.HumanoidArm.RIGHT) ? "burnt" : "kept"));
						chestBurnt = layers.stream().anyMatch(l -> l.contains("iron") && l.endsWith("burnt"));
						netheriteKept = layers.stream().noneMatch(l -> l.contains("netherite"));
						swordBurnt = ragdoll.heldItemBurnt(net.minecraft.world.entity.HumanoidArm.RIGHT);
					}
				}
				drawsAfterMobs = PhysicsPoseApplier.writes;
			}
			// A body far from where its mob died, the camera following it: a zombie is killed on the stage, then its body
			// (not the entity, which stays where it died) is carried 90 blocks to a second stage, with the player. That is
			// past the game's ~67-block draw distance for a zombie, measured from the entity.
			case 150 -> {
				for (PhysicsRagdoll ragdoll : PhysicsHost.INSTANCE.ragdolls()) {
					if (ragdoll.built() && !ragdoll.failed && "minecraft:cow".equals(EntityFilter.idOf(ragdoll.entity.getType()))) {
						pushed = ragdoll;
					}
				}
				if (pushed != null) {
					var torso = pushed.bodies[pushed.torso];
					homeX = minecraft.player.getX();
					homeY = minecraft.player.getY();
					homeZ = minecraft.player.getZ();
					restX = torso.x;
					restZ = torso.z;
					pushFromX = torso.x - 2.0;
					pushFromZ = torso.z;
					pushY = homeY;
					log("walking through the cow's body, asleep " + pushed.island.asleep);
				}
			}
			case 180 -> {
				if (pushed != null) {
					var torso = pushed.bodies[pushed.torso];
					double moved = Math.hypot(torso.x - restX, torso.z - restZ);
					// A resting body that nobody pushes moves 0.00; how far a pushed one goes depends on whether it is shoved ahead or
					// nudged aside (0.28 to 0.72 across loaders), so the check is that it clearly moved.
					pushMoved = moved > 0.15;
					log(String.format(java.util.Locale.ROOT, "the cow's body moved %.2f blocks when walked through", moved));
					minecraft.player.setPos(homeX, homeY, homeZ);
				}
			}
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
			// Above the build limit (y 320 and up): a stone floor at y 318, the player on it looking at a zombie four blocks
			// higher. Its body used to be let go on its first tick there (the game counts those heights as never loaded).
			case 282 -> run(server, "execute at @p run fill ~-4 318 ~-2 ~4 318 ~10 minecraft:stone",
					"execute as @p at @p run tp @s ~ 319 ~ 0 -15",
					"execute at @p run summon minecraft:zombie ~ 323 ~5 {NoAI:1b}");
			case 288 -> run(server, "execute at @p run kill @e[type=minecraft:zombie,distance=..10]");
			case 300 -> report(minecraft);
			case 312 -> {
				for (PhysicsRagdoll ragdoll : PhysicsHost.INSTANCE.ragdolls()) {
					if (ragdoll.built() && !ragdoll.failed && "minecraft:zombie".equals(EntityFilter.idOf(ragdoll.entity.getType())) && ragdoll.entity.getY() > 318.0) {
						double y = ragdoll.bodies[ragdoll.torso].y;
						highBodyFell = y < 321.0;
						log(String.format(java.util.Locale.ROOT, "zombie killed at y %.1f: body torso at y %.1f 24 ticks later", ragdoll.entity.getY(), y));
					}
				}
				if (!highBodyFell) {
					log("zombie killed above the build limit: no body that fell");
				}
			}
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
				boolean extras = riderBody && (mannequinBody || !HAS_MANNEQUINS) && chestBurnt && netheriteKept && swordBurnt && pushMoved && highBodyFell;
				boolean pass = missing.isEmpty() && problems.isEmpty() && drawsAfterMobs > 0 && playerBody && keptPastLifetime && back && goneOnRespawn && farBodyDrawn && extras;
				log("RESULT " + (pass ? "PASS" : "FAIL") + ": mobs without a body " + missing + ", problems " + problems
						+ ", posed draws " + drawsAfterMobs + ", player body " + (playerBody ? "followed by the camera" : "missing")
						+ (keptPastLifetime ? ", kept past its lifetime" : ", NOT kept past its lifetime")
						+ (back && goneOnRespawn ? ", gone on respawn" : ", NOT gone on respawn")
						+ (farBodyDrawn ? ", drawn far from its entity" : ", NOT drawn far from its entity")
						+ (riderBody ? ", rider a physics body" : ", rider NOT a physics body")
						+ (!HAS_MANNEQUINS ? "" : mannequinBody ? ", mannequin a body" : ", mannequin NOT a body")
						+ (chestBurnt && swordBurnt ? ", armor and sword burnt" : ", armor or sword NOT burnt")
						+ (netheriteKept ? ", netherite kept" : ", netherite burnt")
						+ (pushMoved ? ", body pushed" : ", body NOT pushed")
						+ (highBodyFell ? ", body above the build limit fell" : ", body above the build limit did NOT fall"));
				minecraft.stop();
			}
			default -> {
			}
		}
	}

	private static void report(Minecraft minecraft) {
		var lines = RagdollTracker.INSTANCE.physicsReport();
		log(lines.size() + " physics bodies, " + PhysicsPoseApplier.writes + " posed draws so far, " + PhysicsHost.retiredForTime + " let go early for time");
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
