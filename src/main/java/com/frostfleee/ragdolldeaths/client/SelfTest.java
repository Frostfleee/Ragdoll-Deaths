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
 *
 * <p>New in 1.2.1: a zombie villager seated in a boat and a drowned in a minecart become bodies; a husk wearing an
 * elytra has its wings carried with its body; a sulfur cube's swallowed block moves with its body (26.2 and later);
 * skeletons always come apart, on landing after a gentle death and on their own when they never land; with Blood on,
 * drops, stains on the ground and blood on bodies and on hurt living mobs are all drawn; and the settings screen opens.
 */
public final class SelfTest {
	public static final boolean ENABLED = Boolean.getBoolean("ragdolldeaths.selftest");

	private static final String[] MOBS = {"zombie", "skeleton", "creeper", "spider", "cow", "shulker", "slime{Size:1}", "villager", "enderman", "piglin", "horse", "wolf", "cat", "pillager", "vindicator"};

	private static int ticks;
	/**
	 * The settings this run changes (for this run only, not saved), in order. Joining a world reloads the settings from
	 * the file, and when that lands after a change it would undo it: they are put back whenever the settings are new.
	 */
	private static final java.util.List<java.util.function.Consumer<com.frostfleee.ragdolldeaths.core.RagdollConfig>> TUNING = new java.util.ArrayList<>();
	private static com.frostfleee.ragdolldeaths.core.RagdollConfig tuned;
	/** The client tick the settings screen was opened on, or -1. */
	private static int screenOpenedAt = -1;
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
	/** The step its body's draws are counted from (its zombie removed), or -1. */
	private static int farStart = -1;
	private static PhysicsRagdoll farBody;
	private static boolean farMoved, farGoneLogged;
	/** The zombie whose body is carried 90 blocks away. */
	private static final java.util.UUID FAR_UUID = new java.util.UUID(0x52616764_6F6C6C44L, 0x65617468_73466172L);
	private static net.minecraft.world.entity.LivingEntity farZombie;
	private static boolean farBodyDrawn;
	private static boolean riderBody, mannequinBody, chestBurnt, netheriteKept, swordBurnt;
	// Push Dead Bodies: the player walks through the cow's body once it rests, and it must have moved.
	private static PhysicsRagdoll pushed;
	private static double pushFromX, pushFromZ, pushY, homeX, homeY, homeZ, restX, restZ;
	private static boolean pushMoved;
	/** A zombie killed above the build limit: its body must fall, not be let go at once. */
	private static boolean highBodyFell;
	// Stuck arrows: drawn on dead mobs, not on living ones until that setting is on.
	private static boolean arrowsOnBodies, noArrowsOnLiving, arrowsOnLiving;
	private static long livingDrawsBefore;
	/** A horse with white markings killed in lava: the markings burn as well as its coat. */
	private static boolean markingsBurnt;
	/** The zombie killed with knees and elbows on: it has all four, and the physics keeps them near their limits. */
	private static boolean kneesFine;
	/** The dead player has knees and elbows and arrows in it: some arrows sit on the lower halves of its limbs. */
	private static boolean playerArrowsBend, playerSleeveBends;
	private static long playerHalfBefore;
	// 1.2.1
	private static boolean boatBody, cartBody, elytraFollows, skeletonLanded, skeletonLoosened, bloodParticles, bloodStains, bloodOnBodies, bloodOnLiving,
			sulfurBlockFollows, screenOpens;
	/** Whether this game version has sulfur cubes (26.2 and later), and its boats' id (one per wood from 1.21.2). */
	private static final boolean HAS_SULFUR_CUBES = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().stream()
			.anyMatch(id -> id.getPath().equals("sulfur_cube"));
	private static final String BOAT = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().stream()
			.anyMatch(id -> id.getPath().equals("oak_boat")) ? "minecraft:oak_boat" : "minecraft:boat";
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
		if (screenOpenedAt >= 0 && ticks - screenOpenedAt >= 5) {
			screenOpens = Compat.screen() instanceof com.frostfleee.ragdolldeaths.client.config.RagdollConfigScreen;
			log("settings screen open: " + screenOpens);
			Compat.setScreen(null);
			screenOpenedAt = -1;
		}
		// A pause (the test window losing focus at a bad moment) would stop the server's ticks for good.
		if (Compat.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
			Compat.setScreen(null);
		}
		// One step a client tick at most: when the client falls behind, steps run together would kill the mobs before it
		// has seen them.
		if (inWorld < now) {
			inWorld++;
			step(minecraft, server, inWorld);
		}
	}

	/** Changes a setting for this run, and keeps it changed if the settings are reloaded. */
	private static void tune(java.util.function.Consumer<com.frostfleee.ragdolldeaths.core.RagdollConfig> change) {
		TUNING.add(change);
		change.accept(ConfigStore.get());
		tuned = ConfigStore.get();
	}

	private static void step(Minecraft minecraft, MinecraftServer server, int inWorld) {
		if (tuned != null && ConfigStore.get() != tuned) {
			log("settings reloaded from the file; this run's " + TUNING.size() + " changes put back");
			for (java.util.function.Consumer<com.frostfleee.ragdolldeaths.core.RagdollConfig> change : TUNING) {
				change.accept(ConfigStore.get());
			}
			tuned = ConfigStore.get();
		}
		// The zombie's body is built on the first frame this client draws it dying, which can be several ticks after the
		// server killed it: it is looked for every tick until it is there. Only that zombie (known by its UUID): picking
		// "the newest zombie body" once took another one, and the player was sent off to the wrong place.
		if (inWorld > 215 && inWorld <= 250 && farZombie == null) {
			for (PhysicsRagdoll ragdoll : PhysicsHost.INSTANCE.ragdolls()) {
				if (ragdoll.built() && FAR_UUID.equals(ragdoll.entity.getUUID())) {
					farBody = ragdoll;
					farZombie = ragdoll.entity;
					run(server, "execute as @p at @p run tp @s ~ ~ ~90");
					log("the zombie's body found at step " + inWorld + "; the player sent 90 blocks on");
					break;
				}
			}
		}
		// The body is carried there once this client has the chunk it goes to: a body in a chunk that is not loaded is
		// let go at once, as it should be, and on a slow run the chunk came in a few ticks after the player.
		if (farBody != null && !farMoved && inWorld <= 255) {
			var torso = farBody.bodies[farBody.torso];
			if (minecraft.level.getChunkSource().hasChunk(net.minecraft.util.Mth.floor(torso.x) >> 4, net.minecraft.util.Mth.floor(torso.z + 90.0) >> 4)) {
				farBody.moveForTest(0.0, 0.0, 90.0);
				farMoved = true;
				log("the zombie's body carried 90 blocks at step " + inWorld);
			}
		}
		// If the carried body is gone before step 280, why.
		if (farMoved && !farGoneLogged && inWorld < 280 && !PhysicsHost.INSTANCE.ragdolls().contains(farBody)) {
			farGoneLogged = true;
			var torso = farBody.bodies[farBody.torso];
			log("the carried body is gone at step " + inWorld + ": failed " + farBody.failed + ", let go for time " + farBody.retired + ", zombie removed "
					+ farZombie.isRemoved() + " (" + farZombie.getRemovalReason() + "), its chunk loaded "
					+ minecraft.level.getChunkSource().hasChunk(net.minecraft.util.Mth.floor(torso.x) >> 4, net.minecraft.util.Mth.floor(torso.z) >> 4));
		}
		// Its body's draws are counted from when the zombie itself is gone (deathTime 20), to step 280.
		if (inWorld >= 240 && inWorld < 270 && farMoved && farStart < 0 && farZombie.isRemoved()) {
			farStart = inWorld;
			farDrawsBefore = PhysicsPoseApplier.writes;
		}
		// Noted as they appear: when the physics runs over its time it lets the oldest bodies go, as it is meant to, and a
		// body that has gone by the later checks was still made.
		if (inWorld > 96 && inWorld < 140) {
			for (PhysicsRagdoll ragdoll : PhysicsHost.INSTANCE.ragdolls()) {
				riderBody |= ragdoll.seatThrow != null && ragdoll.built();
				notice(ragdoll);
				if (ragdoll.built()) {
					String id = EntityFilter.idOf(ragdoll.entity.getType()).replace("minecraft:", "");
					missing.remove(id.equals("slime") ? "slime{Size:1}" : id);
				}
			}
		}
		// The skeletons killed at step 100 are watched until their bodies go (100 ticks after death): this client sees a
		// death a few ticks after the server, and builds the body when the dying mob is first drawn.
		if (inWorld > 140 && inWorld < 200) {
			for (PhysicsRagdoll ragdoll : PhysicsHost.INSTANCE.ragdolls()) {
				if (ragdoll.special != null && "minecraft:skeleton".equals(EntityFilter.idOf(ragdoll.entity.getType()))) {
					notice(ragdoll);
				}
			}
		}
		if (inWorld == 199) {
			log("skeletons: broke on landing " + skeletonLanded + ", let go in the air 1.5 seconds after death " + skeletonLoosened);
		}
		if (pushed != null && inWorld > 150 && inWorld <= 177) {
			// 0.15 blocks a tick, a walk, from two blocks before the body's middle to two past it
			minecraft.player.setPos(pushFromX + (inWorld - 150) * 0.15, pushY, pushFromZ);
		}
		switch (inWorld) {
			case 1 -> {
				log("in world");
				// RAGDOLL_KNEES=1: every body in this run has knees and elbows (for this run only, not saved).
				tune(c -> c.kneesAndElbows = System.getenv("RAGDOLL_KNEES") != null);
				// Blood is off by default; this run checks it (for this run only, not saved).
				tune(c -> c.blood = true);
			}
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
						"item replace entity @e[tag=lava] weapon.mainhand with minecraft:iron_sword",
						// A second lava pit, with a horse that has white markings (the second skin must burn too).
						"execute at @p positioned ~ 199 ~ rotated ~ 0 run fill ^1 ^ ^13 ^5 ^ ^14 minecraft:stone",
						"execute at @p positioned ~ 200 ~ rotated ~ 0 run fill ^1 ^ ^13 ^5 ^ ^14 minecraft:lava",
							"execute at @p positioned ~ 201 ~ rotated ~ 0 run summon minecraft:horse ^3 ^ ^13.5 {NoAI:1b,Variant:259,active_effects:[{id:\"minecraft:fire_resistance\",duration:2000}]}");
					// 1.2.1: a zombie villager in a boat and a drowned in a minecart (both die seated, killed before the rest), a husk
					// wearing an elytra, and a skeleton in the air past the stage's far edge, with 100 blocks of nothing under it. All of
					// them well inside the camera's view: a dying mob gets its body when it is first drawn.
					run(server, "execute at @p positioned ~ 201 ~ rotated ~ 0 run summon " + BOAT + " ^-2 ^ ^4 {Passengers:[{id:\"minecraft:zombie_villager\",NoAI:1b,Tags:[\"seated\"]}]}",
							"execute at @p positioned ~ 201 ~ rotated ~ 0 run summon minecraft:minecart ^2 ^ ^4 {Passengers:[{id:\"minecraft:drowned\",NoAI:1b,Tags:[\"seated\"]}]}",
							"execute at @p positioned ~ 201 ~ rotated ~ 0 run summon minecraft:husk ^-5 ^ ^5.5 {NoAI:1b,Tags:[\"winged\"]}",
							"item replace entity @e[tag=winged] armor.chest with minecraft:elytra",
							"execute at @p positioned ~ 202 ~ rotated ~ 0 run summon minecraft:skeleton ^ ^ ^17 {NoAI:1b}");
					if (HAS_SULFUR_CUBES) {
						run(server, "execute at @p positioned ~ 201 ~ rotated ~ 0 run summon minecraft:sulfur_cube ^5 ^ ^5.5 {NoAI:1b,Tags:[\"holding\"]}",
								"item replace entity @e[tag=holding] armor.body with minecraft:gold_block");
					}
			}
			// Every mob gets three arrows stuck in it, as if it had been shot.
			case 90 -> stickArrows(server, 3);
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
			case 96 -> run(server, "kill @e[tag=rider]", "kill @e[tag=seated]");
			case 97, 98 -> {
				// The seated mobs as the client sees them while they die: still in their seats, and what the tracker made.
				for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
					String id = EntityFilter.idOf(entity.getType());
					if (entity instanceof net.minecraft.world.entity.LivingEntity living && (id.equals("minecraft:zombie_villager") || id.equals("minecraft:drowned"))) {
						log("seated " + EntityFilter.idOf(living.getType()) + ": vehicle " + (living.getVehicle() == null ? "none" : EntityFilter.idOf(living.getVehicle().getType()))
								+ ", dying " + living.isDeadOrDying() + ", " + RagdollTracker.INSTANCE.describe(living));
					}
				}
			}
			case 99 -> {
				StringBuilder seen = new StringBuilder();
				for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
					if (entity.distanceTo(minecraft.player) < 20 && entity instanceof net.minecraft.world.entity.LivingEntity)
					seen.append(' ').append(EntityFilter.idOf(entity.getType()));
				}
				log("entities before the kill:" + seen);
				noArrowsOnLiving = StuckArrows.livingDraws == 0;
				log("arrows drawn on living mobs with the setting off: " + StuckArrows.livingDraws);
			}
			case 100 -> run(server, "execute at @p run kill @e[type=!minecraft:player,distance=..20]");
			// The living mobs by the camera are hurt, to show their wounds (with Blood on).
			case 105 -> run(server, "execute at @p run damage @e[type=minecraft:cow,distance=..20,limit=1,sort=nearest] 8");
			// Living mobs of kinds that have a body lying on the stage, close to the camera for the screenshots: a kind's
			// model is shared, and a body's pose must not show on the living (on 1.21 it did).
			case 104 -> {
				String[] living = {"cow", "horse", "wolf", "villager"};
				for (int i = 0; i < living.length; i++) {
					run(server, String.format(java.util.Locale.ROOT, "execute at @p positioned ~ 201 ~ rotated ~ 0 run summon minecraft:%s ^%.1f ^ ^4 {NoAI:1b}",
							living[i], i * 2.0 - 3.0));
				}
			}
			// The dead player's knees and elbows are held bent for its screenshot, sleeves, trouser legs and arrows included.
			case 365 -> com.frostfleee.ragdolldeaths.core.LimbBend.testDegrees = 100.0;
			case 375 -> com.frostfleee.ragdolldeaths.core.LimbBend.testDegrees = Double.NaN;
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
					if (ragdoll.charred && id.equals("horse")) {
						log("horse in lava: layers " + ragdoll.layerReport());
						markingsBurnt |= ragdoll.layerBurnt("horse_markings_white");
					}
				}
				drawsAfterMobs = PhysicsPoseApplier.writes;
				for (PhysicsRagdoll ragdoll : PhysicsHost.INSTANCE.ragdolls()) {
					notice(ragdoll);
					if (ragdoll.special != null && "minecraft:skeleton".equals(EntityFilter.idOf(ragdoll.entity.getType()))) {
						int broken = 0;
						for (com.frostfleee.ragdolldeaths.physics.Joint joint : ragdoll.island.joints) {
							broken += joint.broken ? 1 : 0;
						}
						log(String.format(java.util.Locale.ROOT, "skeleton at y %.1f: broke by %s, %d of %d joints let go, asleep %s, %d ticks since death, first seen built at step %s",
								ragdoll.bodies[ragdoll.torso].y, ragdoll.special.brokeBy, broken, ragdoll.island.joints.length, ragdoll.island.asleep, ragdoll.special.ticks(),
								BUILT_AT.get(ragdoll)) + (ragdoll.special.brokeAt >= 0 ? ", came apart " + ragdoll.special.brokeAt + " ticks after death" : ""));
					}
				}
				sulfurBlockFollows = !HAS_SULFUR_CUBES || PhysicsRagdoll.carriedBlocks > 0;
				bloodParticles = Blood.particles > 0;
				bloodStains = Blood.stains > 0;
				bloodOnLiving = Wounds.drawn > 0;
				log("1.2.1: body in a boat " + boatBody + ", in a minecart " + cartBody + ", elytra carried " + elytraFollows + ", skeleton broke on landing "
						+ skeletonLanded + ", in the air " + skeletonLoosened + ", sulfur cube block carried " + PhysicsRagdoll.carriedBlocks + " times"
						+ (HAS_SULFUR_CUBES ? "" : " (no sulfur cubes here)") + ", blood particles " + Blood.particles + ", stains " + Blood.stains
						+ ", bloody bodies " + bloodOnBodies + ", hurt living mobs drawn with blood " + Wounds.drawn + " times");
				arrowsOnBodies = StuckArrows.deadDraws > 0;
				log("arrows drawn on dead mobs: " + StuckArrows.deadDraws);
			}
			// The living mobs by the camera get arrows too, now with the setting on (for this run only, not saved).
			case 141 -> {
				livingDrawsBefore = StuckArrows.livingDraws;
				tune(c -> c.stuckArrowsOnLiving = true);
				stickArrows(server, 3);
			}
			case 147 -> {
				Compat.screenshot();
				log("screenshot taken");
			}
			case 143, 145 -> logLivingArrows(minecraft);
			case 149 -> {
				logLivingArrows(minecraft);
				arrowsOnLiving = StuckArrows.livingDraws > livingDrawsBefore;
				log("arrows drawn on living mobs with the setting on: " + (StuckArrows.livingDraws - livingDrawsBefore));
				tune(c -> c.stuckArrowsOnLiving = false);
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
			// The settings screen opens (and is put away without saving, so this run's settings stay off the disk).
			// The settings screen pauses the game, and on some versions the server's ticks with it, so it is checked and
			// closed by the client's ticks (below), not by a later step.
			case 190 -> {
				Compat.setScreen(new com.frostfleee.ragdolldeaths.client.config.RagdollConfigScreen(null));
				screenOpenedAt = ticks;
			}
			case 210 -> run(server, "execute at @p positioned ~ 201 ~ rotated ~ 0 run summon minecraft:zombie ^ ^ ^5 {NoAI:1b,UUID:[I;"
					+ (int) (FAR_UUID.getMostSignificantBits() >> 32) + "," + (int) FAR_UUID.getMostSignificantBits() + "," + (int) (FAR_UUID.getLeastSignificantBits() >> 32)
					+ "," + (int) FAR_UUID.getLeastSignificantBits() + "]}", "execute at @p run fill ~-12 200 ~78 ~12 200 ~102 minecraft:stone");
			case 215 -> run(server, "execute at @p run kill @e[type=minecraft:zombie,distance=..20]");
			case 280 -> {
				long draws = PhysicsPoseApplier.writes - farDrawsBefore;
				// At least ten ticks of it with the zombie gone.
				farBodyDrawn = farMoved && farStart >= 0 && draws > 0;
				log("body 90 blocks from where its zombie died: " + (farZombie == null ? "no body" : !farMoved ? "never carried (its chunk never loaded)"
						: farStart < 0 ? "its zombie was never removed"
						: draws + " posed draws in " + (280 - farStart) + " ticks, entity " + String.format(java.util.Locale.ROOT, "%.0f", farZombie.distanceTo(minecraft.player))
								+ " blocks from the player"));
			}
			// Above the build limit (y 320 and up): a stone floor at y 318, the player on it looking at a zombie four blocks
			// higher. Its body used to be let go on its first tick there (the game counts those heights as never loaded).
			case 282 -> run(server, "execute at @p run fill ~-4 318 ~-2 ~4 318 ~10 minecraft:stone",
					"execute as @p at @p run tp @s ~ 319 ~ 0 -15",
					"execute at @p run summon minecraft:zombie ~ 323 ~5 {NoAI:1b}",
					"execute at @p run summon minecraft:mannequin ~2 319 ~4");
			case 284 -> stickArrows(server, 16);
			// This zombie's body has knees and elbows (for this run only, not saved).
			case 286 -> tune(c -> c.kneesAndElbows = true);
			case 288 -> run(server, "execute at @p run kill @e[type=minecraft:zombie,distance=..10]", "execute at @p run kill @e[type=minecraft:mannequin,distance=..10]");
			case 300 -> report(minecraft);
			// Held bent for the picture: the mannequin shows sleeves, trouser legs and stuck arrows on bent limbs.
			case 303 -> com.frostfleee.ragdolldeaths.core.LimbBend.testDegrees = 100.0;
			case 306 -> {
				Compat.screenshot();
				log("screenshot taken");
			}
			case 309 -> com.frostfleee.ragdolldeaths.core.LimbBend.testDegrees = Double.NaN;
			case 312 -> {
				for (PhysicsRagdoll ragdoll : PhysicsHost.INSTANCE.ragdolls()) {
					if (ragdoll.built() && !ragdoll.failed && "minecraft:zombie".equals(EntityFilter.idOf(ragdoll.entity.getType())) && ragdoll.entity.getY() > 318.0) {
						double y = ragdoll.bodies[ragdoll.torso].y;
						highBodyFell = y < 321.0;
						log(String.format(java.util.Locale.ROOT, "zombie killed at y %.1f: body torso at y %.1f 24 ticks later", ragdoll.entity.getY(), y));
						double[] outside = {0.0};
						int hinges = ragdoll.hinges(outside);
						kneesFine = hinges == 4 && outside[0] < 8.0;
						log(String.format(java.util.Locale.ROOT, "its knees and elbows: %d, at most %.1f degrees past a limit in the physics, last one drawn at %.1f degrees",
								hinges, outside[0], Math.toDegrees(ragdoll.lastBend)));
					}
				}
				if (!highBodyFell) {
					log("zombie killed above the build limit: no body that fell");
				}
			}
			case 320 -> {
				// For this run only (not saved): the player's own body is drawn, followed, and kept until the respawn.
				// Set late, as the settings are read again when a world is joined.
				tune(c -> c.deathCamera = true);
				tune(c -> c.playerRagdolls = true);
				tune(c -> c.playerBodyUntilRespawn = true);
				tune(c -> c.bodyLifetimeTicks = 100);
				// Arrows in the player, whose body has knees and elbows: those in a shin or forearm must follow the bend.
				playerHalfBefore = StuckArrows.lowerHalfPlacements;
				server.execute(() -> server.getPlayerList().getPlayers().forEach(player -> player.setArrowCount(24)));
				run(server, "gamemode survival @a", "kill @a");
			}
			case 380 -> {
				report(minecraft);
				deadPlayer = minecraft.player;
				log("player: " + RagdollTracker.INSTANCE.describe(minecraft.player) + ", camera " + minecraft.options.getCameraType());
				playerBody = RagdollTracker.INSTANCE.bodyHead(minecraft.player, 0.0F) != null;
				playerArrowsBend = StuckArrows.lowerHalfPlacements > playerHalfBefore;
				for (PhysicsRagdoll ragdoll : PhysicsHost.INSTANCE.ragdolls()) {
					playerSleeveBends |= ragdoll.entity == minecraft.player && ragdoll.built() && ragdoll.sleeveBends();
				}
				log("the dead player's sleeve bends with its arm: " + playerSleeveBends);
				log("pose watch so far: " + PoseWatch.mismatches + " parts drawn away from their bodies, " + PoseWatch.jumps + " jumps, " + PoseWatch.shakes + " shakes, fastest turn "
						+ String.format(java.util.Locale.ROOT, "%.0f", PoseWatch.fastest) + " degrees a tick (" + PoseWatch.fastestPart + ")");
				log("arrows placed on the lower half of the dead player's limbs: " + (StuckArrows.lowerHalfPlacements - playerHalfBefore));
				tune(c -> c.kneesAndElbows = false);
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
				boolean extras = riderBody && (mannequinBody || !HAS_MANNEQUINS) && chestBurnt && netheriteKept && swordBurnt && pushMoved && highBodyFell
						&& arrowsOnBodies && noArrowsOnLiving && arrowsOnLiving && markingsBurnt && kneesFine && playerArrowsBend && playerSleeveBends
						&& limbsSteady() && boatBody && cartBody && elytraFollows && skeletonLanded && skeletonLoosened && sulfurBlockFollows
						&& bloodParticles && bloodStains && bloodOnBodies && bloodOnLiving && screenOpens;
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
						+ (pushMoved ? ", body pushed" : ", body NOT pushed")						+ (highBodyFell ? ", body above the build limit fell" : ", body above the build limit did NOT fall")
						+ (arrowsOnBodies ? ", arrows on bodies" : ", NO arrows on bodies")
						+ (noArrowsOnLiving && arrowsOnLiving ? ", arrows on the living only with the setting" : ", arrows on the living WRONG")
						+ (markingsBurnt ? ", horse markings burnt" : ", horse markings NOT burnt")
						+ (kneesFine ? ", knees and elbows fine" : ", knees and elbows WRONG")
						+ (playerArrowsBend ? ", player arrows follow the bend" : ", player arrows do NOT follow the bend")
						+ (playerSleeveBends ? ", player sleeves bend" : ", player sleeves do NOT bend")
						+ (boatBody && cartBody ? ", boat and minecart riders bodies" : ", boat or minecart rider NOT a body")
						+ (elytraFollows ? ", elytra follows" : ", elytra does NOT follow")
						+ (skeletonLanded && skeletonLoosened ? ", skeletons break apart" : ", skeletons do NOT break apart (landing " + skeletonLanded + ", in the air " + skeletonLoosened + ")")
						+ (sulfurBlockFollows ? (HAS_SULFUR_CUBES ? ", sulfur cube block follows" : "") : ", sulfur cube block does NOT follow")
						+ (bloodParticles && bloodStains && bloodOnBodies && bloodOnLiving ? ", blood drawn" : ", blood NOT all drawn (particles " + bloodParticles + ", stains "
								+ bloodStains + ", bodies " + bloodOnBodies + ", living " + bloodOnLiving + ")")
						+ (screenOpens ? ", settings screen opens" : ", settings screen does NOT open")
						+ (limbsSteady() ? ", limbs steady" : ", limbs NOT steady (" + PoseWatch.mismatches + " misplaced, " + PoseWatch.jumps + " jumps, " + PoseWatch.shakes
								+ " shakes, fastest " + Math.round(PoseWatch.fastest) + " degrees a tick)"));
				minecraft.stop();
			}
			default -> {
			}
		}
	}

	/** The step each body was first seen built on. */
	private static final java.util.Map<PhysicsRagdoll, Integer> BUILT_AT = new java.util.IdentityHashMap<>();

	/** Notes what the 1.2.1 checks look for on a body. */
	private static void notice(PhysicsRagdoll ragdoll) {
		if (!ragdoll.built()) {
			return;
		}
		BUILT_AT.putIfAbsent(ragdoll, inWorld);
		String id = EntityFilter.idOf(ragdoll.entity.getType());
		boatBody |= id.equals("minecraft:zombie_villager") && ragdoll.seatThrow != null;
		cartBody |= id.equals("minecraft:drowned") && ragdoll.seatThrow != null;
		elytraFollows |= id.equals("minecraft:husk") && ragdoll.layersCarried > 0;
		if (ragdoll.special != null && ragdoll.special.brokeBy != null) {
			skeletonLanded |= ragdoll.special.brokeBy.equals("landing");
			// It lets go 1.5 seconds after death, to the tick (counted from when this client saw it die).
			skeletonLoosened |= ragdoll.special.brokeBy.equals("loosened") && ragdoll.special.brokeAt == SpecialBodies.LOOSEN_TICKS;
		}
		bloodOnBodies |= ragdoll.bloodPainted();
	}

	/** No body part drawn off its body, jumping, or thrashing: what a limb glitch looks like to the pose watch. */
	private static boolean limbsSteady() {
		return PoseWatch.mismatches == 0 && PoseWatch.jumps == 0 && PoseWatch.shakes <= 8 && PoseWatch.fastest <= 70.0;
	}

	private static void stickArrows(MinecraftServer server, int count) {
		server.execute(() -> {
			StringBuilder given = new StringBuilder();
			for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
				for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
					if (entity instanceof net.minecraft.world.entity.LivingEntity living && !(entity instanceof net.minecraft.world.entity.player.Player) && living.isAlive()) {
						living.setArrowCount(count);
						given.append(' ').append(EntityFilter.idOf(entity.getType()).replace("minecraft:", ""));
					}
				}
			}
			log(count + " arrows each, on the server, at server tick " + server.getTickCount() + ":" + given);
		});
	}

	/** The living mobs near the player as this client sees them, with their arrows. */
	private static void logLivingArrows(Minecraft minecraft) {
		StringBuilder seen = new StringBuilder();
		for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof net.minecraft.world.entity.LivingEntity living && !(entity instanceof net.minecraft.world.entity.player.Player) && living.isAlive()
					&& entity.distanceTo(minecraft.player) < 20) {
				seen.append(' ').append(EntityFilter.idOf(entity.getType()).replace("minecraft:", "")).append('=').append(living.getArrowCount()).append('/')
						.append(StuckArrows.shownOn(living));
			}
		}
		log("living mobs here (arrows / shown), step " + inWorld + " (server tick " + (startServerTick + inWorld - 1) + "), setting " + ConfigStore.get().stuckArrowsOnLiving + ":" + seen);
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
