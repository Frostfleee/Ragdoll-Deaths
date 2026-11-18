package com.frostfleee.ragdolldeaths.visualtest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;

/**
 * Stages deaths in a fresh flat world and saves screenshots of them, so the animations can be looked at without
 * playing: limb physics up close, an explosion, falls (off a ledge and from high up) and a body in flowing water.
 */
public class RagdollVisualTest implements FabricClientGameTest {
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("ragdolldeaths-visual-test");
	private static final int WIDTH = 960;
	/** Skeletons burn in the test's daylight unless they wear something on their head. */
	private static final String HELMET = "equipment:{head:{id:\"minecraft:leather_helmet\"}}";
	private static final int HEIGHT = 540;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			waitForChunks(world);
			TestServerContext server = world.getServer();
			server.runCommand("difficulty easy");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set noon");
			// Spectators float, so the camera stays where it is put.
			server.runCommand("gamemode spectator @a");
			BlockPos base = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
			int x = base.getX();
			int y = base.getY();
			int z = base.getZ();
			// The scenes are far from spawn; their chunks must be loaded on the server before anything is built there.
			server.runCommand(String.format("forceload add %d %d %d %d", x + 150, z - 60, x + 250, z + 60));
			server.runCommand(String.format("forceload add %d %d %d %d", x - 215, z - 30, x - 170, z + 20));
			server.runCommand(String.format("forceload add %d %d %d %d", x - 15, z + 185, x + 30, z + 215));
			server.runCommand(String.format("forceload add %d %d %d %d", x - 110, z - 30, x - 60, z + 20));
			server.runCommand(String.format("forceload add %d %d %d %d", x - 20, z - 120, x + 140, z - 70));
			server.runCommand(String.format("forceload add %d %d %d %d", x - 20, z - 170, x + 150, z - 125));
			server.runCommand(String.format("forceload add %d %d %d %d", x - 20, z - 230, x + 100, z - 180));
			server.runCommand(String.format("forceload add %d %d %d %d", x - 20, z - 330, x + 220, z - 250));
			server.runCommand(String.format("forceload add %d %d %d %d", x - 20, z - 370, x + 150, z - 331));
			context.waitTicks(60);
			// Bodies vanish after five seconds by default; here they stay so each scene can be checked once it has come
			// to rest. RAGDOLL_KNEES=1 turns on the knees-and-elbows beta, which is off by default.
			context.runOnClient(client -> {
				com.frostfleee.ragdolldeaths.core.RagdollConfig config = com.frostfleee.ragdolldeaths.client.ConfigStore.get().copy();
				config.bodyLifetimeTicks = 600;
				config.kneesAndElbows = System.getenv("RAGDOLL_KNEES") != null;
				// RAGDOLL_LIMP=1 switches the dying muscles off, to compare against a body that is limp from the start.
				if (System.getenv("RAGDOLL_LIMP") != null) {
					config.muscleSeconds = 0.0;
				}
				com.frostfleee.ragdolldeaths.client.ConfigStore.save(config);
			});
			// Hides the HUD, so nothing covers the bodies.
			FrameCapture.hideHud(context);

			// RAGDOLL_SCENES=pillars,blast,... runs only those scenes; by default every scene runs.
			String only = System.getenv("RAGDOLL_SCENES");
			java.util.function.Predicate<String> runs = name -> only == null || only.isBlank() || java.util.Arrays.asList(only.split(",")).contains(name);
			// The clips for the mod page are only filmed when asked for by name.
			if (only != null && runs.test("showcase")) {
				showcase(context, world, server, x + 500, y, z + 500);
			}
			if (runs.test("dumpmodels")) {
				// Writes every entity model's part tree (names, roles, cube sizes) to models.txt, for building ragdolls.
				context.runOnClient(client -> {
					StringBuilder out = new StringBuilder();
					net.minecraft.client.model.geom.ModelLayers.getKnownLocations()
							.filter(layer -> layer.layer().equals("main"))
							.sorted(java.util.Comparator.comparing(layer -> layer.model().toString()))
							.forEach(layer -> {
								out.append("== ").append(layer.model()).append('\n');
								try {
									dump(client.getEntityModels().bakeLayer(layer), "", 1, out);
								} catch (RuntimeException e) {
									out.append("  (").append(e.getMessage()).append(")\n");
								}
							});
					try {
						java.nio.file.Files.writeString(java.nio.file.Path.of("models.txt"), out);
					} catch (java.io.IOException e) {
						throw new java.io.UncheckedIOException(e);
					}
				});
			}
			if (runs.test("settings")) {
				// The settings screen as a player opens it, to check the options are there and read well.
				var settings = context.computeOnClient(client -> new com.frostfleee.ragdolldeaths.client.config.RagdollConfigScreen(null));
				context.runOnClient(client -> com.frostfleee.ragdolldeaths.client.Compat.setScreen(settings));
				context.waitTicks(5);
				// One shot per page of the list, so every option is in some shot.
				for (int page = 0; page < 5; page++) {
					double scroll = page * 130.0;
					context.runOnClient(client -> {
						for (var child : settings.children()) {
							if (child instanceof net.minecraft.client.gui.components.AbstractSelectionList<?> options) {
								options.setScrollAmount(scroll);
							}
						}
					});
					context.waitTicks(2);
					context.takeScreenshot(TestScreenshotOptions.of("settings-0" + page).withSize(WIDTH, HEIGHT));
				}
				context.runOnClient(client -> com.frostfleee.ragdolldeaths.client.Compat.setScreen(null));
				context.waitTicks(5);
			}
			if (runs.test("limbs")) {
				limbs(context, world, server, x, y, z);
			}
			if (runs.test("wall")) {
				wall(context, world, server, x + 40, y, z);
			}
			if (runs.test("pillars")) {
				pillars(context, world, server, x - 100, y, z);
			}
			if (runs.test("blast")) {
				blast(context, world, server, x + 200, y, z);
			}
			if (runs.test("falls")) {
				falls(context, world, server, x - 200, y, z);
			}
			if (runs.test("water")) {
				water(context, world, server, x, y, z + 200);
			}
			// The physics ragdoll scenes, one per row of the acceptance table in PHYSICS_PROMPT.md.
			if (runs.test("row1")) {
				chest(context, world, server, x, y, z - 100);
			}
			if (runs.test("row5")) {
				pillarEdge(context, world, server, x + 30, y, z - 100);
			}
			if (runs.test("row6")) {
				besideWall(context, world, server, x + 60, y, z - 100);
			}
			if (runs.test("row7")) {
				stairs(context, world, server, x + 90, y, z - 100);
			}
			if (runs.test("row8")) {
				fence(context, world, server, x + 120, y, z - 100);
			}
			if (runs.test("row2")) {
				hardHit(context, world, server, x, y, z - 150);
			}
			if (runs.test("row3")) {
				headshot(context, world, server, x + 30, y, z - 150);
			}
			if (runs.test("row3b")) {
				punchArrow(context, world, server, x + 45, y, z - 150);
			}
			if (runs.test("row4")) {
				running(context, world, server, x + 60, y, z - 150);
			}
			if (runs.test("row9")) {
				creeper(context, world, server, x + 100, y, z - 150);
			}
			if (runs.test("tnt")) {
				realTnt(context, world, server, x + 100, y, z - 135);
			}
			if (runs.test("row10")) {
				mace(context, world, server, x + 130, y, z - 150);
			}
			if (runs.test("row8b")) {
				lowWall(context, world, server, x + 140, y, z - 100);
			}
			if (runs.test("pool")) {
				intoPool(context, world, server, x, y, z - 200, "water", "pool");
			}
			if (runs.test("lavapool")) {
				intoPool(context, world, server, x + 30, y, z - 200, "lava", "lavapool");
			}
			if (runs.test("charred")) {
				charred(context, world, server, x + 30, y, z - 220);
			}
			if (runs.test("columns")) {
				columns(context, world, server, x + 60, y, z - 200);
			}
			if (runs.test("plans1")) {
				lineup(context, world, server, x, y, z - 260, "plans1", "cow", "pig", "wolf", "horse", "chicken", "sheep");
			}
			if (runs.test("plans2")) {
				lineup(context, world, server, x + 50, y, z - 260, "plans2", "villager", "iron_golem", "creeper", "enderman", "fox", "witch", "wandering_trader");
			}
			if (runs.test("plans3")) {
				lineup(context, world, server, x + 100, y, z - 260, "plans3", "cave_spider", "bee", "squid", "cod", "goat", "ravager");
			}
			if (runs.test("row13")) {
				pile(context, world, server, x + 160, y, z - 260);
			}
			if (runs.test("row15")) {
				spider(context, world, server, x + 180, y, z - 260);
			}
			if (runs.test("row16")) {
				flyers(context, world, server, x, y, z - 300);
			}
			if (runs.test("row12")) {
				materials(context, world, server, x + 40, y, z - 300);
			}
			if (runs.test("row14")) {
				skeletons(context, world, server, x, y, z - 340);
			}
			if (runs.test("row17")) {
				blobs(context, world, server, x + 40, y, z - 340);
			}
			if (runs.test("specials")) {
				lineup(context, world, server, x + 80, y, z - 340, "specials", "snow_golem", "snow_golem|Pumpkin:0b", "blaze", "breeze", "wither_skeleton", "slime|Size:1",
						"skeleton|" + HELMET);
			}
			if (runs.test("gear")) {
				gear(context, world, server, x + 10, y, z - 80);
			}
		}
	}

	private static void dump(net.minecraft.client.model.geom.ModelPart part, String name, int depth, StringBuilder out) {
		var access = (com.frostfleee.ragdolldeaths.mixin.ModelPartAccessor) (Object) part;
		var pose = part.getInitialPose();
		StringBuilder cubes = new StringBuilder();
		for (var cube : access.ragdolldeaths$cubes()) {
			cubes.append(String.format(java.util.Locale.ROOT, " [%.0fx%.0fx%.0f]", cube.maxX - cube.minX, cube.maxY - cube.minY, cube.maxZ - cube.minZ));
		}
		if (depth > 1) {
			var role = com.frostfleee.ragdolldeaths.core.JointNames.roleOf(com.frostfleee.ragdolldeaths.core.JointNames.tokens(name));
			out.append("  ".repeat(depth - 1)).append(name).append(" role=").append(role)
					.append(String.format(java.util.Locale.ROOT, " at (%.1f, %.1f, %.1f) rot (%.2f, %.2f, %.2f)", pose.x(), pose.y(), pose.z(), pose.xRot(), pose.yRot(), pose.zRot()))
					.append(cubes).append('\n');
		}
		access.ragdolldeaths$children().forEach((child, childPart) -> dump(childPart, child, depth + 1, out));
	}

	/**
	 * Waits until the chunks around the player are drawn. The test API names the object that does this differently
	 * between game versions, so it is found by name.
	 */
	private static void waitForChunks(TestSingleplayerContext world) {
		for (String getter : new String[] {"getConnection", "getClientLevel", "getClientWorld"}) {
			try {
				Object target = world.getClass().getMethod(getter).invoke(world);
				java.lang.reflect.Method wait = target.getClass().getMethod("waitForChunksRender");
				wait.setAccessible(true);
				wait.invoke(target);
				return;
			} catch (ReflectiveOperationException e) {
				// Not this version's name; try the next.
			}
		}
		throw new IllegalStateException("this game version's test API has no way to wait for chunks");
	}

	private static void look(TestSingleplayerContext world, TestServerContext server, double x, double y, double z, double atX, double atY, double atZ) {
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f facing %.2f %.2f %.2f", x, y, z, atX, atY, atZ));
		waitForChunks(world);
	}

	private static void shots(ClientGameTestContext context, String name, int count, int every) {
		// RAGDOLL_FILM=1 saves a frame every tick instead, for turning a scene into a smooth clip.
		if (System.getenv("RAGDOLL_FILM") != null) {
			count *= every;
			every = 1;
		}
		for (int i = 0; i < count; i++) {
			context.takeScreenshot(TestScreenshotOptions.of(name + "-" + String.format("%03d", i * every)).withSize(WIDTH, HEIGHT));
			context.waitTicks(every);
		}
	}

	/**
	 * Logs, for every physics body, whether it has come to rest and whether any part ended up inside a block, and
	 * fails the test for a part inside a block.
	 */
	private static void report(ClientGameTestContext context, String scene) {
		java.util.List<String> lines = context.computeOnClient(client -> com.frostfleee.ragdolldeaths.client.RagdollTracker.INSTANCE.physicsReport());
		for (String line : lines) {
			LOGGER.info("[physics check] {}: {}", scene, line);
			if (!line.contains("inside=0")) {
				throw new AssertionError(scene + ": a body part is inside a block: " + line);
			}
		}
		if (lines.isEmpty()) {
			LOGGER.warn("[physics check] {}: no physics body exists", scene);
		}
	}

	/** Row 1: a husk struck in the chest from the front. */
	private static void chest(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 4.0, y + 1.5, z + 2.5, x + 0.5, y + 0.6, z + 2.5);
		server.runCommand(String.format("summon husk %d %d %d {NoAI:1b,Tags:[\"row1\"],Rotation:[180f,0f]}", x, y, z + 1));
		server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hit1\"]}", x, y + 1, z - 2));
		context.waitTicks(10);
		server.runCommand("damage @e[tag=row1,limit=1] 100 minecraft:mob_attack by @e[tag=hit1,limit=1]");
		shots(context, "row1", 24, 2);
		context.waitTicks(60);
		context.takeScreenshot(TestScreenshotOptions.of("row1-rest").withSize(WIDTH, HEIGHT));
		report(context, "row1");
	}

	/** Row 5: a husk on the edge of a pillar, knocked off it. */
	private static void pillarEdge(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 2.5, y + 5.0, z - 13.0, x + 2.5, y + 4.0, z);
		server.runCommand(String.format("fill %d %d %d %d %d %d stone", x, y, z, x, y + 7, z));
		server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hit5\"]}", x - 2, y + 8, z));
		server.runCommand(String.format("summon husk %d.8 %d %d.5 {NoAI:1b,Tags:[\"row5\"],Rotation:[90f,0f]}", x, y + 8, z));
		context.waitTicks(10);
		server.runCommand("damage @e[tag=row5,limit=1] 100 minecraft:mob_attack by @e[tag=hit5,limit=1]");
		shots(context, "row5", 24, 2);
		context.waitTicks(60);
		context.takeScreenshot(TestScreenshotOptions.of("row5-rest").withSize(WIDTH, HEIGHT));
		report(context, "row5");
	}

	/** Row 6: a husk with a wall right behind it, struck from the front, seen from the side. */
	private static void besideWall(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 0.5, y + 1.6, z - 4.5, x - 0.5, y + 0.7, z + 0.5);
		server.runCommand(String.format("fill %d %d %d %d %d %d stone", x - 2, y, z - 2, x - 2, y + 3, z + 3));
		server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,Tags:[\"row6\"],Rotation:[-90f,0f]}", x - 1, y, z));
		server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hit6\"]}", x + 2, y + 1, z));
		context.waitTicks(10);
		server.runCommand("damage @e[tag=row6,limit=1] 100 minecraft:mob_attack by @e[tag=hit6,limit=1]");
		shots(context, "row6", 24, 2);
		context.waitTicks(60);
		context.takeScreenshot(TestScreenshotOptions.of("row6-rest").withSize(WIDTH, HEIGHT));
		report(context, "row6");
	}

	/** Row 7: a husk at the top of a flight of stairs, struck so it goes down them. */
	private static void stairs(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 4.5, y + 5.0, z - 10.0, x + 3.5, y + 3.0, z + 0.5);
		// A landing at the top, then seven steps going down toward +X.
		server.runCommand(String.format("fill %d %d %d %d %d %d stone", x - 2, y, z - 1, x, y + 6, z + 1));
		for (int i = 1; i <= 7; i++) {
			if (i < 7) {
				server.runCommand(String.format("fill %d %d %d %d %d %d stone", x + i, y, z - 1, x + i, y + 6 - i, z + 1));
			}
			server.runCommand(String.format("fill %d %d %d %d %d %d stone_stairs[facing=west]", x + i, y + 7 - i, z - 1, x + i, y + 7 - i, z + 1));
		}
		server.runCommand(String.format("summon husk %d.6 %d %d.5 {NoAI:1b,Tags:[\"row7\"],Rotation:[90f,0f]}", x, y + 7, z));
		server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hit7\"]}", x - 2, y + 8, z));
		context.waitTicks(10);
		server.runCommand("damage @e[tag=row7,limit=1] 100 minecraft:mob_attack by @e[tag=hit7,limit=1]");
		shots(context, "row7", 30, 2);
		context.waitTicks(60);
		context.takeScreenshot(TestScreenshotOptions.of("row7-rest").withSize(WIDTH, HEIGHT));
		report(context, "row7");
	}

	/**
	 * A husk in full armor holding a sword, and a baby husk, struck from the front: armor, the held item and the
	 * smaller model must all follow the body, and the living husk beside them must still look whole.
	 */
	private static void gear(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x - 4.5, y + 1.6, z + 2.5, x + 0.5, y + 0.4, z + 2.5);
		String armor = "equipment:{head:{id:\"minecraft:iron_helmet\"},chest:{id:\"minecraft:iron_chestplate\"},legs:{id:\"minecraft:iron_leggings\"},"
				+ "feet:{id:\"minecraft:iron_boots\"},mainhand:{id:\"minecraft:iron_sword\"}}";
		server.runCommand(String.format("summon husk %d %d %d {NoAI:1b,Tags:[\"gear\"],Rotation:[180f,0f],%s}", x - 1, y, z + 1, armor));
		server.runCommand(String.format("summon husk %d %d %d {NoAI:1b,Tags:[\"gear\"],Rotation:[180f,0f],IsBaby:1b}", x + 1, y, z + 1));
		server.runCommand(String.format("summon husk %d %d %d {NoAI:1b,Tags:[\"alive\"],Rotation:[180f,0f],%s}", x + 3, y, z + 1, armor));
		server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hitgear\"]}", x, y + 1, z - 2));
		context.waitTicks(10);
		server.runCommand("execute as @e[tag=gear] run damage @s 100 minecraft:mob_attack by @e[tag=hitgear,limit=1]");
		shots(context, "gear", 16, 2);
		context.waitTicks(40);
		context.takeScreenshot(TestScreenshotOptions.of("gear-rest").withSize(WIDTH, HEIGHT));
		report(context, "gear");
		server.runCommand("kill @e[tag=alive]");
	}

	private static void finish(ClientGameTestContext context, String scene, int restTicks) {
		context.waitTicks(restTicks);
		context.takeScreenshot(TestScreenshotOptions.of(scene + "-rest").withSize(WIDTH, HEIGHT));
		report(context, scene);
	}

	/**
	 * Row 2: the same hit as row 1 but as hard as a sprinting hit with Knockback II. The server works out knockback
	 * from the motion the mob already has, so the husk is given motion first to make the blow that strong.
	 */
	private static void hardHit(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 7.0, y + 2.0, z + 4.0, x + 0.5, y + 0.6, z + 4.0);
		server.runCommand(String.format("summon husk %d %d %d {NoAI:1b,Tags:[\"row2\"],Rotation:[180f,0f]}", x, y, z + 1));
		server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hit2\"]}", x, y + 1, z - 2));
		context.waitTicks(10);
		server.runCommand("data merge entity @e[tag=row2,limit=1] {Motion:[0.0d,0.0d,2.6d]}");
		server.runCommand("damage @e[tag=row2,limit=1] 100 minecraft:mob_attack by @e[tag=hit2,limit=1]");
		shots(context, "row2", 24, 2);
		finish(context, "row2", 60);
	}

	/** Row 3: an arrow into the head of a standing husk, seen from the side. */
	private static void headshot(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 4.5, y + 1.7, z + 2.0, x + 0.5, y + 0.9, z + 2.0);
		server.runCommand(String.format("summon husk %d %d %d {NoAI:1b,Tags:[\"row3\"],Rotation:[180f,0f]}", x, y, z + 1));
		context.waitTicks(10);
		server.runCommand(String.format(java.util.Locale.ROOT, "summon arrow %d.5 %.2f %d.5 {Motion:[0.0d,0.0d,3.0d],damage:30d}", x, y + 1.97, z - 5));
		shots(context, "row3", 22, 2);
		finish(context, "row3", 50);
	}

	/** The arrow of row 3 from a Punch II bow: the husk is given motion first so the server's knockback is that strong. */
	private static void punchArrow(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 7.0, y + 2.0, z + 4.0, x + 0.5, y + 0.6, z + 4.0);
		server.runCommand(String.format("summon husk %d %d %d {NoAI:1b,Tags:[\"row3b\"],Rotation:[180f,0f]}", x, y, z + 1));
		context.waitTicks(10);
		server.runCommand("data merge entity @e[tag=row3b,limit=1] {Motion:[0.0d,0.0d,2.0d]}");
		server.runCommand(String.format(java.util.Locale.ROOT, "summon arrow %d.5 %.2f %d.5 {Motion:[0.0d,0.0d,3.0d],damage:30d}", x, y + 1.3, z - 5));
		shots(context, "row3b", 22, 2);
		finish(context, "row3b", 50);
	}

	/** Row 4: a husk killed while running after a villager; its momentum and its swinging legs carry on. */
	private static void running(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 6.0, y + 2.0, z - 7.0, x + 6.0, y + 0.6, z + 0.5);
		server.runCommand(String.format("summon villager %d %d %d {NoAI:1b,Tags:[\"bait\"]}", x + 16, y, z));
		server.runCommand(String.format("summon husk %d %d %d {Tags:[\"row4\"],PersistenceRequired:1b,Rotation:[-90f,0f],"
				+ "attributes:[{id:\"minecraft:movement_speed\",base:0.42d},{id:\"minecraft:follow_range\",base:48d}]}", x, y, z));
		context.waitTicks(45);
		server.runCommand("kill @e[tag=row4]");
		shots(context, "row4", 24, 2);
		server.runCommand("kill @e[tag=bait]");
		finish(context, "row4", 50);
	}

	/**
	 * Row 9: a creeper goes off in a group of husks. The ones around it are flung away, further the closer they
	 * stood; the one behind the low wall is mostly sheltered.
	 */
	private static void creeper(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 0.5, y + 11.0, z - 11.0, x + 0.5, y, z + 1.0);
		// Obsidian, so the wall is still there after the blast.
		server.runCommand(String.format("fill %d %d %d %d %d %d obsidian", x - 1, y, z - 2, x + 1, y, z - 2));
		int[][] around = {{2, 0}, {-2, 0}, {0, 2}, {3, 3}, {-1, 1}, {0, -3}};
		for (int[] at : around) {
			server.runCommand(String.format("summon husk %d %d %d {NoAI:1b,Health:0.4f,Tags:[\"row9\"]}", x + at[0], y, z + at[1]));
		}
		context.waitTicks(10);
		server.runCommand(String.format("summon creeper %d %d %d {NoAI:1b,Fuse:2s,ignited:1b}", x, y, z));
		context.waitTicks(1);
		shots(context, "row9", 22, 4);
		finish(context, "row9", 40);
	}

	/**
	 * TNT the way it goes off in play: a TNT block lit by redstone, with its full fuse, among zombies and husks that
	 * have their AI on and their full health. Every one it kills must be thrown, like a creeper's blast throws them.
	 */
	private static void realTnt(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 0.5, y + 11.0, z - 11.0, x + 0.5, y, z + 1.0);
		// On Easy a full-health zombie two blocks from TNT survives with a sliver of health; on Normal it dies.
		server.runCommand("difficulty normal");
		// A low fence keeps the mobs from wandering off during the fuse without sheltering them from the blast.
		// Just the ring: a one-layer "hollow" fill would fill the whole square.
		server.runCommand(String.format("fill %d %d %d %d %d %d oak_fence", x - 3, y, z - 3, x + 3, y, z - 3));
		server.runCommand(String.format("fill %d %d %d %d %d %d oak_fence", x - 3, y, z + 3, x + 3, y, z + 3));
		server.runCommand(String.format("fill %d %d %d %d %d %d oak_fence", x - 3, y, z - 3, x - 3, y, z + 3));
		server.runCommand(String.format("fill %d %d %d %d %d %d oak_fence", x + 3, y, z - 3, x + 3, y, z + 3));
		// TNT takes a full-health zombie only to about half, even right beside it, so these are already hurt, as
		// zombies usually are by the time TNT finishes them in play. Some close, some further out.
		int[][] around = {{1, 0}, {-2, 0}, {0, 2}, {0, -1}, {2, 2}};
		for (int i = 0; i < around.length; i++) {
			server.runCommand(String.format("summon %s %d %d %d {Tags:[\"tnt\"],PersistenceRequired:1b,Health:6f}", "husk", x + around[i][0], y, z + around[i][1]));
		}
		server.runCommand(String.format("setblock %d %d %d tnt", x, y, z));
		server.runCommand(String.format("setblock %d %d %d redstone_block", x, y - 1, z));
		// The fuse is four seconds.
		context.waitTicks(70);
		server.runCommand("execute as @e[tag=tnt] run data get entity @s Health");
		server.runCommand("execute as @e[type=tnt] run data get entity @s Pos");
		context.waitTicks(8);
		server.runCommand("execute as @e[tag=tnt] run data get entity @s Health");
		shots(context, "tnt", 22, 4);
		server.runCommand("execute as @e[tag=tnt] run data get entity @s Health");
		server.runCommand("execute as @e[tag=tnt] run data get entity @s Pos");
		finish(context, "tnt", 30);
		// The ones that lived are killed where they stand, in and around the crater.
		server.runCommand("kill @e[tag=tnt]");
		look(world, server, x + 0.5, y + 4.0, z - 4.5, x + 0.5, y - 1.0, z + 1.0);
		shots(context, "tntkill", 30, 4);
		context.takeScreenshot(TestScreenshotOptions.of("tntkill-rest").withSize(WIDTH, HEIGHT));
		report(context, "tnt after");
		server.runCommand("difficulty easy");
	}

	/**
	 * A husk on a ledge is knocked off it into a pool three blocks deep, seen from the side through a glass wall: it
	 * must slow down in the liquid and float up, not sink to the bottom as if the liquid were air.
	 */
	private static void intoPool(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z, String liquid,
			String scene) {
		look(world, server, x + 4.5, y + 2.5, z - 7.0, x + 4.5, y - 1.0, z + 0.5);
		// The pool: four wide, three deep, with glass on the camera's side.
		server.runCommand(String.format("fill %d %d %d %d %d %d stone", x + 1, y - 4, z - 2, x + 7, y - 1, z + 2));
		server.runCommand(String.format("fill %d %d %d %d %d %d %s", x + 2, y - 3, z - 1, x + 6, y - 1, z + 1, liquid));
		server.runCommand(String.format("fill %d %d %d %d %d %d glass", x + 2, y - 3, z - 2, x + 6, y - 1, z - 2));
		// The ledge, three blocks above the ground, on the pool's edge.
		server.runCommand(String.format("fill %d %d %d %d %d %d stone", x - 1, y, z - 1, x + 1, y + 2, z + 1));
		server.runCommand(String.format("summon husk %d.7 %d %d.5 {NoAI:1b,Tags:[\"%s\"],Rotation:[90f,0f]}", x + 1, y + 3, z, scene));
		server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hit%s\"]}", x - 2, y + 4, z, scene));
		context.waitTicks(10);
		server.runCommand(String.format("data merge entity @e[tag=%s,limit=1] {Motion:[0.6d,0.0d,0.0d]}", scene));
		server.runCommand(String.format("damage @e[tag=%s,limit=1] 100 minecraft:mob_attack by @e[tag=hit%s,limit=1]", scene, scene));
		shots(context, scene, 30, 2);
		finish(context, scene, 20);
	}

	/**
	 * A water tank eight deep with two bubble columns, one rising from soul sand and one pulled down by magma, and a
	 * husk killed at the bottom of each. Both must rise: the soul sand one fast, the magma one slowly.
	 */
	private static void columns(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		// The tank stands on the ground (the test world ends four blocks below it): stone walls, water eight deep,
		// glass on the camera's side.
		server.runCommand(String.format("fill %d %d %d %d %d %d stone", x - 1, y, z - 2, x + 6, y + 9, z + 2));
		server.runCommand(String.format("fill %d %d %d %d %d %d water", x, y + 2, z - 1, x + 5, y + 9, z + 1));
		server.runCommand(String.format("fill %d %d %d %d %d %d glass", x, y + 2, z - 2, x + 5, y + 9, z - 2));
		// Soul sand under the left column, magma under the right; the columns grow up from them on their own.
		server.runCommand(String.format("setblock %d %d %d soul_sand", x + 1, y + 1, z));
		server.runCommand(String.format("setblock %d %d %d magma_block", x + 4, y + 1, z));
		look(world, server, x + 2.5, y + 6.0, z - 9.0, x + 2.5, y + 5.5, z + 0.5);
		context.waitTicks(40);
		server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,Tags:[\"columns\"],Rotation:[0f,0f]}", x + 1, y + 2, z));
		server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,Tags:[\"columns\"],Rotation:[0f,0f]}", x + 4, y + 2, z));
		context.waitTicks(3);
		server.runCommand("kill @e[tag=columns]");
		// Heights every half second, to compare how fast each column lifts its body.
		for (int i = 0; i < 12; i++) {
			context.takeScreenshot(TestScreenshotOptions.of(String.format("columns-%02d", i * 10)).withSize(WIDTH, HEIGHT));
			report(context, String.format("columns %.1f s", i * 0.5));
			context.waitTicks(10);
		}
		finish(context, "columns", 20);
	}

	/**
	 * Mobs of different body plans, eight blocks apart, each struck from the front in turn and filmed up close from
	 * the side, so each one fills the picture.
	 */
	private static void lineup(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z, String scene,
			String... mobs) {
		for (int i = 0; i < mobs.length; i++) {
			int mx = x + i * 8;
			// "name|nbt" adds extra NBT to that mob (a helmet so a skeleton does not burn, a slime's size).
			String[] spec = mobs[i].split("\\|", 2);
			mobs[i] = spec[0];
			String extra = spec.length > 1 ? "," + spec[1] : "";
			server.runCommand(String.format("summon %s %d %d %d {NoAI:1b,Tags:[\"%s%d\"],Rotation:[180f,0f]%s}", mobs[i], mx, y, z + 1, scene, i, extra));
			server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hit%s%d\"]}", mx, y + 1, z - 2, scene, i));
		}
		context.waitTicks(10);
		for (int i = 0; i < mobs.length; i++) {
			int mx = x + i * 8;
			look(world, server, mx + 4.5, y + 2.2, z - 1.5, mx + 0.5, y + 0.4, z + 1.5);
			context.waitTicks(2);
			server.runCommand(String.format("damage @e[tag=%s%d,limit=1] 1000 minecraft:mob_attack by @e[tag=hit%s%d,limit=1]", scene, i, scene, i));
			shots(context, scene + mobs[i] + (i > 0 && mobs[i].equals(mobs[i - 1]) ? "b" : ""), 10, 3);
			report(context, scene + " " + mobs[i] + " t+30");
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of(scene + mobs[i] + (i > 0 && mobs[i].equals(mobs[i - 1]) ? "b" : "") + "-rest").withSize(WIDTH, HEIGHT));
		}
		report(context, scene);
		server.runCommand(String.format("kill @e[tag=hit%s0]", scene));
	}

	/** Row 13: a husk is killed lying on the ground, then a second one is killed two blocks above it and falls on it. */
	private static void pile(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 4.0, y + 2.0, z - 3.0, x + 0.5, y + 0.3, z + 0.5);
		server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,Tags:[\"row13a\"],Rotation:[90f,0f]}", x, y, z));
		context.waitTicks(5);
		server.runCommand("kill @e[tag=row13a]");
		context.waitTicks(50);
		server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,NoGravity:1b,Tags:[\"row13b\"],Rotation:[0f,0f]}", x, y + 2, z));
		context.waitTicks(5);
		server.runCommand("kill @e[tag=row13b]");
		shots(context, "row13", 20, 2);
		finish(context, "row13", 40);
	}

	/** Row 15: a spider struck from the front, close up: its legs should curl in as it dies. */
	private static void spider(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 3.5, y + 2.5, z - 2.5, x + 0.5, y + 0.3, z + 1.0);
		server.runCommand(String.format("summon spider %d %d %d {NoAI:1b,Tags:[\"row15\"],Rotation:[180f,0f]}", x, y, z + 1));
		server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hit15\"]}", x, y + 1, z - 2));
		context.waitTicks(10);
		server.runCommand("damage @e[tag=row15,limit=1] 100 minecraft:mob_attack by @e[tag=hit15,limit=1]");
		shots(context, "row15", 24, 2);
		finish(context, "row15", 40);
	}

	/** Row 16: a chicken, a parrot and a bat hanging in the air six blocks up, each shot by an arrow. */
	private static void flyers(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 6.0, y + 4.0, z - 7.5, x + 6.0, y + 3.0, z + 1.0);
		String[] mobs = {"chicken", "parrot", "bat"};
		for (int i = 0; i < mobs.length; i++) {
			server.runCommand(String.format("summon %s %d.5 %d %d.5 {NoAI:1b,NoGravity:1b,Tags:[\"row16\"],Rotation:[0f,0f]}", mobs[i], x + i * 6, y + 6, z));
		}
		context.waitTicks(10);
		for (int i = 0; i < mobs.length; i++) {
			server.runCommand(String.format(java.util.Locale.ROOT, "summon arrow %d.5 %.2f %d.5 {Motion:[0.0d,0.0d,3.0d],damage:30d}", x + i * 6, y + 6.3, z - 4));
		}
		shots(context, "row16", 16, 1);
		finish(context, "row16", 40);
	}

	/**
	 * Row 12: the ground changes how a body moves. Two husks are knocked hard along a lane, one of stone and one of
	 * blue ice; three more fall from four blocks up, onto a slime block, into a cobweb and into powder snow.
	 */
	private static void materials(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 12.0, y + 11.0, z - 10.0, x + 12.0, y, z + 8.0);
		// The two lanes, each twelve long, sunk into the ground so the husks slide along the surface.
		// Five wide, so a body's arms land on the lane too, not on the grass beside it.
		server.runCommand(String.format("fill %d %d %d %d %d %d stone", x - 2, y - 1, z - 2, x + 2, y - 1, z + 22));
		server.runCommand(String.format("fill %d %d %d %d %d %d blue_ice", x + 4, y - 1, z - 2, x + 8, y - 1, z + 22));
		String[] lanes = {"stone", "ice"};
		for (int i = 0; i < 2; i++) {
			server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,Tags:[\"row12%s\"],Rotation:[180f,0f]}", x + i * 6, y, z + 1, lanes[i]));
			server.runCommand(String.format("summon armor_stand %d.5 %d.5 %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hit12%s\"]}", x + i * 6, y + 1, z - 2, lanes[i]));
		}
		// The drops: a slime block, a cobweb two high, powder snow two deep.
		server.runCommand(String.format("setblock %d %d %d slime_block", x + 13, y, z + 3));
		server.runCommand(String.format("fill %d %d %d %d %d %d cobweb", x + 17, y, z + 2, x + 18, y + 1, z + 4));
		server.runCommand(String.format("fill %d %d %d %d %d %d powder_snow", x + 22, y - 2, z + 2, x + 24, y - 1, z + 4));
		int[] drops = {13, 17, 23};
		for (int drop : drops) {
			server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,NoGravity:1b,Tags:[\"row12drop\"],Rotation:[90f,0f]}", x + drop, y + 4, z + 3));
		}
		context.waitTicks(10);
		for (String lane : lanes) {
			server.runCommand(String.format("data merge entity @e[tag=row12%s,limit=1] {Motion:[0.0d,0.0d,2.0d]}", lane));
			server.runCommand(String.format("damage @e[tag=row12%s,limit=1] 100 minecraft:mob_attack by @e[tag=hit12%s,limit=1]", lane, lane));
		}
		server.runCommand("kill @e[tag=row12drop]");
		for (int i = 0; i < 15; i++) {
			context.takeScreenshot(TestScreenshotOptions.of(String.format("row12-%02d", i * 4)).withSize(WIDTH, HEIGHT));
			report(context, String.format("row12 t+%d", i * 4));
			context.waitTicks(4);
		}
		finish(context, "row12", 40);
	}

	/**
	 * Row 14: three skeletons. One is hit as hard as a Knockback II sprint hit and must shatter into separate bones;
	 * one takes a plain hit and falls in one piece; one is killed eight blocks up and breaks when it lands.
	 */
	private static void skeletons(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 5.0, y + 5.0, z - 9.0, x + 5.0, y + 1.0, z + 3.0);
		String[] tags = {"hard", "plain"};
		for (int i = 0; i < 2; i++) {
			server.runCommand(String.format("summon skeleton %d.5 %d %d.5 {NoAI:1b,Tags:[\"row14%s\"],Rotation:[180f,0f],%s}", x + i * 5, y, z + 1, tags[i], HELMET));
			server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hit14%s\"]}", x + i * 5, y + 1, z - 2, tags[i]));
		}
		server.runCommand(String.format("summon skeleton %d.5 %d %d.5 {NoAI:1b,NoGravity:1b,Tags:[\"row14fall\"],Rotation:[180f,0f],%s}", x + 10, y + 8, z + 1, HELMET));
		context.waitTicks(10);
		server.runCommand("data merge entity @e[tag=row14hard,limit=1] {Motion:[0.0d,0.0d,2.6d]}");
		for (String tag : tags) {
			server.runCommand(String.format("damage @e[tag=row14%s,limit=1] 100 minecraft:mob_attack by @e[tag=hit14%s,limit=1]", tag, tag));
		}
		server.runCommand("kill @e[tag=row14fall]");
		shots(context, "row14", 24, 2);
		finish(context, "row14", 40);
	}

	/** Row 17: a slime and a magma cube killed four blocks up: each must squash as it lands, wobble and settle flatter. */
	private static void blobs(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 3.0, y + 2.0, z - 7.0, x + 3.0, y + 1.0, z + 1.0);
		server.runCommand(String.format("summon slime %d.5 %d %d.5 {NoAI:1b,NoGravity:1b,Size:3,Tags:[\"row17\"]}", x, y + 4, z));
		server.runCommand(String.format("summon magma_cube %d.5 %d %d.5 {NoAI:1b,NoGravity:1b,Size:3,Tags:[\"row17\"]}", x + 6, y + 4, z));
		context.waitTicks(10);
		server.runCommand("kill @e[tag=row17]");
		shots(context, "row17", 30, 1);
		finish(context, "row17", 40);
	}

	/**
	 * An armored husk and a villager are knocked across a one-block strip of lava and land on the grass beyond: both
	 * must come out burnt black and stay black.
	 */
	private static void charred(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 3.0, y + 3.5, z - 0.5, x + 3.0, y, z + 3.5);
		// A trough of lava half a block deep and one block wide, with grass beyond it for the bodies to end up on.
		server.runCommand(String.format("fill %d %d %d %d %d %d stone", x - 1, y - 2, z + 2, x + 7, y - 1, z + 4));
		server.runCommand(String.format("fill %d %d %d %d %d %d lava", x, y - 1, z + 3, x + 6, y - 1, z + 3));
		server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,Tags:[\"charred\"],Rotation:[180f,0f]}", x + 1, y, z));
		server.runCommand(String.format("summon zombie %d.5 %d %d.5 {NoAI:1b,Tags:[\"charred\"],Rotation:[180f,0f],equipment:{head:{id:\"minecraft:leather_helmet\"}}}", x + 3, y, z));
		server.runCommand(String.format("summon cow %d.5 %d %d.5 {NoAI:1b,Tags:[\"charred\"],Rotation:[180f,0f]}", x + 5, y, z));
		for (int i = 1; i <= 5; i += 2) {
			server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hitcharred\"]}", x + i, y + 1, z - 3));
		}
		context.waitTicks(10);
		server.runCommand("execute as @e[tag=charred] at @s run damage @s 1000 minecraft:mob_attack by @e[tag=hitcharred,sort=nearest,limit=1]");
		shots(context, "charred", 20, 2);
		finish(context, "charred", 30);
	}

	/** Row 10: a mace smash from above; the attacker is an armor stand dropped from height so it is falling fast. */
	private static void mace(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 4.5, y + 1.6, z + 0.5, x + 0.5, y + 0.7, z + 0.5);
		server.runCommand(String.format("summon husk %d %d %d {NoAI:1b,Tags:[\"row10\"],Rotation:[-90f,0f]}", x, y, z));
		context.waitTicks(10);
		server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Tags:[\"hit10\"]}", x, y + 9, z));
		context.waitTicks(13);
		server.runCommand("damage @e[tag=row10,limit=1] 100 minecraft:mace_smash by @e[tag=hit10,limit=1]");
		shots(context, "row10", 18, 2);
		server.runCommand("kill @e[tag=hit10]");
		finish(context, "row10", 50);
	}

	/**
	 * Row 8 again with something a body can actually fold over: a wall one block high (a fence stops a mob at chest
	 * height, so nothing short of a throw gets a body over one).
	 */
	private static void lowWall(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 1.5, y + 2.0, z - 5.0, x + 1.5, y + 0.8, z + 0.5);
		server.runCommand(String.format("fill %d %d %d %d %d %d stone", x + 1, y, z - 2, x + 1, y, z + 3));
		server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,Tags:[\"row8b\"],Rotation:[90f,0f]}", x, y, z));
		server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hit8b\"]}", x - 3, y + 1, z));
		context.waitTicks(10);
		server.runCommand("damage @e[tag=row8b,limit=1] 100 minecraft:mob_attack by @e[tag=hit8b,limit=1]");
		shots(context, "row8b", 24, 2);
		finish(context, "row8b", 60);
	}

	/** Row 8: a husk standing against a fence, struck so it goes over it. */
	private static void fence(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 1.5, y + 1.8, z - 5.0, x + 1.0, y + 0.8, z + 0.5);
		server.runCommand(String.format("fill %d %d %d %d %d %d oak_fence", x + 1, y, z - 2, x + 1, y, z + 3));
		server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,Tags:[\"row8\"],Rotation:[90f,0f]}", x, y, z));
		server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hit8\"]}", x - 3, y + 1, z));
		context.waitTicks(10);
		server.runCommand("damage @e[tag=row8,limit=1] 100 minecraft:mob_attack by @e[tag=hit8,limit=1]");
		shots(context, "row8", 24, 2);
		context.waitTicks(60);
		context.takeScreenshot(TestScreenshotOptions.of("row8-rest").withSize(WIDTH, HEIGHT));
		report(context, "row8");
	}

	/** A zombie facing the camera, struck from the front: watch the arms and head lag, whip and flop. */
	private static void limbs(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 3.5, y + 1.4, z - 1.5, x + 0.5, y + 0.6, z + 1.5);
		server.runCommand(String.format("summon husk %d %d %d {NoAI:1b,Tags:[\"limb\"],Rotation:[180f,0f]}", x, y, z + 1));
		context.waitTicks(10);
		server.runCommand("damage @e[tag=limb,limit=1] 100 minecraft:player_attack by @p");
		shots(context, "limbs", 16, 2);
	}

	/**
	 * A husk struck from the front with a wall right behind it, seen from the side: it falls back against the wall,
	 * and its arms and head must stop at the wall and the ground instead of going into them.
	 */
	private static void wall(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 4.0, y + 1.6, z + 0.5, x + 0.5, y + 0.6, z + 0.5);
		server.runCommand(String.format("fill %d %d %d %d %d %d stone", x - 2, y, z - 2, x - 2, y + 3, z + 3));
		server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,Tags:[\"wall\"],Rotation:[-90f,0f]}", x - 1, y, z));
		server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"hitter\"]}", x + 2, y + 1, z));
		context.waitTicks(10);
		server.runCommand("damage @e[tag=wall,limit=1] 100 minecraft:mob_attack by @e[tag=hitter,limit=1]");
		shots(context, "wall", 14, 2);
	}

	/**
	 * Eight zombies, each on top of a 16-block pillar, struck so the blow knocks them off sideways, twice over. Every
	 * body must fall to the ground; none may stop in the air beside its pillar.
	 */
	private static void pillars(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 14.5, y + 9.0, z - 24.0, x + 14.5, y + 8.0, z);
		for (int round = 0; round < 2; round++) {
			for (int i = 0; i < 8; i++) {
				int px = x + i * 4;
				server.runCommand(String.format("fill %d %d %d %d %d %d stone", px, y, z, px, y + 15, z));
				server.runCommand(String.format("summon armor_stand %d.5 %d %d.5 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"pusher\"]}", px - 2, y + 16, z));
				// From the middle of the top to right at its edge, so some only just tip over it, late.
				double edge = 0.5 + i * 0.065 + round * 0.03;
				server.runCommand(String.format(java.util.Locale.ROOT, "summon zombie %.3f %d %d.5 {Tags:[\"pillar\"],PersistenceRequired:1b,Rotation:[90f,0f]}", px + edge, y + 16, z));
			}
			context.waitTicks(3);
			server.runCommand("execute as @e[tag=pillar] at @s run damage @s 100 minecraft:mob_attack by @e[tag=pusher,sort=nearest,limit=1]");
			shots(context, "pillars" + round, 16, 3);
			server.runCommand("kill @e[tag=pusher]");
			context.waitTicks(20);
		}
	}

	/** TNT among a row of zombies, seen from far back: the bodies should fly far and land, not hang in the air. */
	private static void blast(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		// From high above, looking down, so throws in every direction show their real distance.
		look(world, server, x + 0.5, y + 48.0, z - 6.0, x + 0.5, y, z + 4.0);
		for (int i = -2; i <= 2; i++) {
			server.runCommand(String.format("summon husk %d %d %d {NoAI:1b,Tags:[\"blast\"]}", x + i * 2, y, z + 4));
		}
		context.waitTicks(10);
		server.runCommand(String.format("summon tnt %d %d %d {fuse:1s}", x, y, z + 3));
		context.waitTicks(2);
		shots(context, "blast", 14, 5);
	}

	/**
	 * Two falls: a zombie knocked off a 10-block ledge (the floor straight below its death is the ledge, but it is
	 * carried past the edge) and one killed 40 blocks up. Both must land on the ground.
	 */
	private static void falls(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 6.5, y + 8.0, z - 14.0, x + 2.5, y + 4.0, z + 2.0);
		server.runCommand(String.format("fill %d %d %d %d %d %d stone", x - 3, y, z, x, y + 9, z + 3));
		// Standing on the ledge's edge and walking off it (with AI, so it moves), killed as it steps over.
		server.runCommand(String.format("summon husk %d.7 %d %d.5 {Tags:[\"ledge\"],Motion:[0.5d,0.25d,0.0d]}", x, y + 10, z + 2));
		context.waitTicks(2);
		server.runCommand("kill @e[tag=ledge]");
		shots(context, "ledge", 12, 4);

		// A burning zombie killed 40 blocks up: its flames must fall with the body, not hang where it died.
		look(world, server, x + 14.5, y + 15.0, z - 28.0, x + 14.5, y + 18.0, z + 2.0);
		server.runCommand(String.format("summon zombie %d %d %d {NoAI:1b,Tags:[\"high\"],Fire:200s}", x + 14, y + 40, z + 2));
		context.waitTicks(3);
		server.runCommand("kill @e[tag=high]");
		shots(context, "high", 16, 5);
	}

	/** A zombie killed in a flowing channel: the current should carry the body along it until it vanishes. */
	private static void water(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		look(world, server, x + 7.5, y + 7.0, z - 9.0, x + 7.5, y - 1.0, z);
		server.runCommand(String.format("fill %d %d %d %d %d %d stone", x - 1, y - 3, z - 2, x + 16, y - 1, z + 2));
		server.runCommand(String.format("fill %d %d %d %d %d %d air", x, y - 2, z - 1, x + 15, y - 1, z + 1));
		server.runCommand(String.format("fill %d %d %d %d %d %d water", x, y - 2, z - 1, x, y - 1, z + 1));
		context.waitTicks(40);
		look(world, server, x + 6.5, y + 4.5, z - 5.0, x + 6.5, y - 1.5, z + 0.5);
		server.runCommand(String.format("summon husk %d %d %d {NoAI:1b,Tags:[\"swim\"]}", x + 3, y - 2, z));
		context.waitTicks(5);
		server.runCommand("kill @e[tag=swim]");
		shots(context, "water", 14, 4);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Showcase clips. RAGDOLL_SCENES=showcase films these for the mod page: one clean stage far from every test
	// scene, rebuilt before each clip, a long lens up close, attackers that cannot be seen, and a frame every
	// fiftieth of a second (the frames between ticks are drawn by the game's own interpolation).
	// ---------------------------------------------------------------------------------------------------------

	private static final int FILM_WIDTH = 1920, FILM_HEIGHT = 1080;

	private static void showcase(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z) {
		server.runCommand(String.format("forceload add %d %d %d %d", x - 40, z - 40, x + 40, z + 40));
		server.runCommand("gamerule mob_drops false");
		// 1.21.x names it differently; on each version one of the two does nothing.
		server.runCommand("gamerule doMobLoot false");
		context.runOnClient(client -> client.options.fov().set(40));
		String only = System.getenv("RAGDOLL_CLIPS");
		java.util.function.Predicate<String> runs = name -> only == null || only.isBlank() || java.util.Arrays.asList(only.split(",")).contains(name);
		String hit = "damage @e[tag=star,limit=1] 100 minecraft:mob_attack by @e[tag=hit,limit=1]";

		if (runs.test("melee-hit")) {
			stage(context, world, server, x, y, z, x + 8.0, y + 3.0, z + 5.5, x + 0.5, y + 0.6, z + 2.0);
			server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,Tags:[\"star\"],Rotation:[180f,0f]}", x, y, z));
			attacker(server, x + 0.5, y + 1.0, z - 3.0);
			film(context, server, "melee-hit", 64, hit);
		}
		if (runs.test("knockback-hit")) {
			// A zombie in an iron helmet, seen from close by.
			stage(context, world, server, x, y, z, x + 9.0, y + 3.4, z + 3.0, x + 0.5, y + 0.6, z + 0.5);
			server.runCommand(String.format("summon zombie %d.5 %d %d.5 {NoAI:1b,Tags:[\"star\"],Rotation:[180f,0f],equipment:{head:{id:\"minecraft:iron_helmet\"}}}", x, y, z - 4));
			attacker(server, x + 0.5, y + 1.0, z - 7.0);
			film(context, server, "knockback-hit", 92, "data merge entity @e[tag=star,limit=1] {Motion:[0.0d,0.0d,2.6d]}", hit);
		}
		if (runs.test("arrow-headshot")) {
			// Seen from the side and a little behind, so the arrow's whole flight is in the picture; it flies at the
			// speed of a half-drawn bow so it can be followed.
			stage(context, world, server, x, y, z, x + 8.5, y + 3.2, z + 0.2, x + 0.5, y + 0.9, z + 0.2);
			server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,Tags:[\"star\"],Rotation:[180f,0f]}", x, y, z));
			film(context, server, "arrow-headshot", 64,
					String.format(java.util.Locale.ROOT, "summon arrow %d.5 %.2f %d.5 {Motion:[0.0d,0.05d,1.6d],damage:30d,pickup:0b}", x, y + 2.15, z - 7));
		}
		if (runs.test("armored-zombie")) {
			stage(context, world, server, x, y, z, x + 8.0, y + 3.0, z + 5.5, x + 0.5, y + 0.6, z + 2.0);
			server.runCommand(String.format("summon zombie %d.5 %d %d.5 {NoAI:1b,Tags:[\"star\"],Rotation:[180f,0f],equipment:{head:{id:\"minecraft:iron_helmet\"},"
					+ "chest:{id:\"minecraft:iron_chestplate\"},legs:{id:\"minecraft:iron_leggings\"},feet:{id:\"minecraft:iron_boots\"},"
					+ "mainhand:{id:\"minecraft:iron_sword\"}}}", x, y, z));
			attacker(server, x + 0.5, y + 1.0, z - 3.0);
			film(context, server, "armored-zombie", 64, hit);
		}
		if (runs.test("against-a-wall")) {
			stage(context, world, server, x, y, z, x + 5.0, y + 2.8, z - 6.0, x - 0.8, y + 1.0, z + 0.5);
			server.runCommand(String.format("fill %d %d %d %d %d %d stone_bricks", x - 2, y, z - 3, x - 2, y + 3, z + 4));
			server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,Tags:[\"star\"],Rotation:[-90f,0f]}", x - 1, y, z));
			attacker(server, x + 2.5, y + 1.0, z + 0.5);
			film(context, server, "against-a-wall", 64, hit);
		}
		if (runs.test("down-the-stairs")) {
			int ox = x - 4;
			stage(context, world, server, x, y, z, ox + 10.5, y + 6.5, z - 15.0, ox + 5.5, y + 2.6, z + 0.5);
			server.runCommand(String.format("fill %d %d %d %d %d %d stone_bricks", ox - 2, y, z - 1, ox, y + 6, z + 1));
			for (int i = 1; i <= 7; i++) {
				if (i < 7) {
					server.runCommand(String.format("fill %d %d %d %d %d %d stone_bricks", ox + i, y, z - 1, ox + i, y + 6 - i, z + 1));
				}
				server.runCommand(String.format("fill %d %d %d %d %d %d stone_brick_stairs[facing=west]", ox + i, y + 7 - i, z - 1, ox + i, y + 7 - i, z + 1));
			}
			server.runCommand(String.format("summon husk %d.6 %d %d.5 {NoAI:1b,Tags:[\"star\"],Rotation:[90f,0f]}", ox, y + 7, z));
			attacker(server, ox - 2.5, y + 8.0, z + 0.5);
			film(context, server, "down-the-stairs", 100, hit);
		}
		if (runs.test("skeleton-shatter")) {
			stage(context, world, server, x, y, z, x + 13.0, y + 4.5, z + 4.5, x + 0.5, y + 0.5, z + 1.5);
			server.runCommand(String.format("summon skeleton %d.5 %d %d.5 {NoAI:1b,Tags:[\"star\"],Rotation:[180f,0f],equipment:{head:{id:\"minecraft:iron_helmet\"}}}", x, y, z - 3));
			attacker(server, x + 0.5, y + 1.0, z - 6.0);
			film(context, server, "skeleton-shatter", 74, "data merge entity @e[tag=star,limit=1] {Motion:[0.0d,0.0d,2.6d]}", hit);
		}
		if (runs.test("spider-curl")) {
			stage(context, world, server, x, y, z, x + 8.0, y + 3.0, z + 5.5, x + 0.5, y + 0.4, z + 2.0);
			server.runCommand(String.format("summon spider %d.5 %d %d.5 {NoAI:1b,Tags:[\"star\"],Rotation:[180f,0f]}", x, y, z));
			attacker(server, x + 0.5, y + 1.0, z - 3.0);
			film(context, server, "spider-curl", 64, hit);
		}
		if (runs.test("snow-golem")) {
			stage(context, world, server, x, y, z, x + 8.0, y + 3.0, z + 5.5, x + 0.5, y + 0.6, z + 2.0);
			server.runCommand(String.format("summon snow_golem %d.5 %d %d.5 {NoAI:1b,Tags:[\"star\"],Rotation:[180f,0f]}", x, y, z));
			attacker(server, x + 0.5, y + 1.0, z - 3.0);
			film(context, server, "snow-golem", 70, "damage @e[tag=star,limit=1] 1000 minecraft:mob_attack by @e[tag=hit,limit=1]");
		}
		if (runs.test("many-mobs")) {
			String[] mobs = {"cow", "villager", "creeper", "enderman", "iron_golem"};
			stage(context, world, server, x, y, z, x + 0.5, y + 5.5, z + 15.0, x + 0.5, y + 0.6, z + 2.0);
			for (int i = 0; i < mobs.length; i++) {
				double mx = x + 0.5 + (i - 2) * 3.0;
				server.runCommand(String.format(java.util.Locale.ROOT, "summon %s %.2f %d %d.5 {NoAI:1b,Tags:[\"cast\"],Rotation:[0f,0f]}", mobs[i], mx, y, z));
				server.runCommand(String.format(java.util.Locale.ROOT, "summon interaction %.2f %d %d.5 {width:0f,height:0f,Tags:[\"hit\"]}", mx, y + 1, z - 3));
			}
			film(context, server, "many-mobs", 76, "execute as @e[tag=cast] at @s run damage @s 1000 minecraft:mob_attack by @e[tag=hit,sort=nearest,limit=1]");
		}
		if (runs.test("creeper-blast") || runs.test("tnt-blast")) {
			for (String clip : new String[] {"creeper-blast", "tnt-blast"}) {
				if (!runs.test(clip)) {
					continue;
				}
				stage(context, world, server, x, y, z, x + 0.5, y + 14.0, z - 17.0, x + 0.5, y + 0.0, z + 1.0);
				int[][] around = {{3, 0}, {-3, 0}, {0, 3}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}};
				for (int[] at : around) {
					server.runCommand(String.format("summon husk %d.5 %d %d.5 {NoAI:1b,Health:0.4f,Tags:[\"cast\"]}", x + at[0], y, z + at[1]));
				}
				film(context, server, clip, 84, clip.startsWith("creeper")
						? String.format("summon creeper %d.5 %d %d.5 {NoAI:1b,Fuse:1s,ignited:1b}", x, y, z)
						: String.format("summon tnt %d.5 %d %d.5 {fuse:1s}", x, y, z));
			}
		}
		if (runs.test("water-float")) {
			stage(context, world, server, x, y, z, x + 10.0, y + 6.5, z - 7.5, x + 3.0, y + 0.5, z + 0.5);
			// A pool three deep, set into the ground with a stone rim, and a ledge beside it to be knocked off.
			server.runCommand(String.format("fill %d %d %d %d %d %d stone_bricks", x, y - 4, z - 2, x + 6, y - 1, z + 2));
			server.runCommand(String.format("fill %d %d %d %d %d %d water", x + 1, y - 3, z - 1, x + 5, y - 1, z + 1));
			server.runCommand(String.format("fill %d %d %d %d %d %d stone_bricks", x - 3, y, z - 1, x - 1, y + 2, z + 1));
			server.runCommand(String.format("summon husk %d.3 %d %d.5 {NoAI:1b,Tags:[\"star\"],Rotation:[90f,0f]}", x - 1, y + 3, z));
			attacker(server, x - 3.5, y + 4.0, z + 0.5);
			context.waitTicks(20);
			film(context, server, "water-float", 240, "data merge entity @e[tag=star,limit=1] {Motion:[0.6d,0.0d,0.0d]}", hit);
		}
		if (runs.test("lava-burn")) {
			stage(context, world, server, x, y, z, x + 8.0, y + 4.5, z + 10.0, x + 0.5, y + 0.0, z + 4.0);
			// A strip of lava one block wide; the mobs are knocked across it and land burnt where it touched them.
			server.runCommand(String.format("fill %d %d %d %d %d %d stone_bricks", x - 5, y - 2, z + 1, x + 5, y - 1, z + 7));
			server.runCommand(String.format("fill %d %d %d %d %d %d lava", x - 4, y - 1, z + 2, x + 4, y - 1, z + 2));
			String[] mobs = {"husk", "villager", "cow"};
			for (int i = 0; i < mobs.length; i++) {
				int mx = x + (i - 1) * 3;
				server.runCommand(String.format("summon %s %d.5 %d %d.5 {NoAI:1b,Tags:[\"cast\"],Rotation:[0f,0f]}", mobs[i], mx, y, z));
				server.runCommand(String.format("summon interaction %d.5 %d %d.5 {width:0f,height:0f,Tags:[\"hit\"]}", mx, y + 1, z - 3));
			}
			context.waitTicks(20);
			film(context, server, "lava-burn", 90, "execute as @e[tag=cast] run data merge entity @s {Motion:[0.0d,0.0d,0.7d]}",
					"execute as @e[tag=cast] at @s run damage @s 1000 minecraft:mob_attack by @e[tag=hit,sort=nearest,limit=1]");
		}
		if (runs.test("shulker")) {
			// Two shulkers, a purple one on the ground and a red one on a wall: knocked over, they tumble, and when
			// their time is up they break like shulker boxes. Lifetime is cut to five seconds here so the break is filmed.
			stage(context, world, server, x, y, z, x + 1.5, y + 3.5, z + 9.0, x + 2.0, y + 0.8, z);
			server.runCommand(String.format("fill %d %d %d %d %d %d stone_bricks", x + 3, y, z, x + 3, y + 1, z));
			server.runCommand(String.format("summon shulker %d %d %d {NoAI:1b,Tags:[\"cast\"]}", x - 1, y, z));
			server.runCommand(String.format("summon shulker %d %d %d {NoAI:1b,Tags:[\"cast\"],Color:14b}", x + 3, y + 2, z));
			server.runCommand(String.format("summon interaction %d.5 %d %d.5 {width:0f,height:0f,Tags:[\"hit\"]}", x - 5, y + 2, z));
			context.runOnClient(client -> {
				com.frostfleee.ragdolldeaths.core.RagdollConfig config = com.frostfleee.ragdolldeaths.client.ConfigStore.get().copy();
				config.bodyLifetimeTicks = 100;
				com.frostfleee.ragdolldeaths.client.ConfigStore.save(config);
			});
			context.waitTicks(10);
			film(context, server, "shulker", 125, "execute as @e[tag=cast] at @s run damage @s 1000 minecraft:mob_attack by @e[tag=hit,limit=1]");
			context.runOnClient(client -> {
				com.frostfleee.ragdolldeaths.core.RagdollConfig config = com.frostfleee.ragdolldeaths.client.ConfigStore.get().copy();
				config.bodyLifetimeTicks = 600;
				com.frostfleee.ragdolldeaths.client.ConfigStore.save(config);
			});
		}
		if (runs.test("death-camera")) {
			// You die: hit from the front by something unseen, and the death camera follows your body.
			stage(context, world, server, x, y, z, x + 0.5, y + 1.62, z + 0.5, x + 0.5, y + 1.62, z - 5.0);
			context.runOnClient(client -> {
				com.frostfleee.ragdolldeaths.core.RagdollConfig config = com.frostfleee.ragdolldeaths.client.ConfigStore.get().copy();
				config.deathCamera = true;
				config.playerRagdolls = true;
				com.frostfleee.ragdolldeaths.client.ConfigStore.save(config);
			});
			server.runCommand("gamemode survival @a");
			server.runCommand("effect clear @a");
			attacker(server, x + 0.5, y + 1.0, z - 3.0);
			context.waitTicks(5);
			film(context, server, "death-camera", 120, "damage @p 100 minecraft:mob_attack by @e[tag=hit,limit=1]");
			context.runOnClient(client -> {
				client.player.respawn();
				com.frostfleee.ragdolldeaths.client.Compat.setScreen(null);
			});
			context.waitTicks(20);
			server.runCommand("gamemode spectator @a");
			context.runOnClient(client -> {
				com.frostfleee.ragdolldeaths.core.RagdollConfig config = com.frostfleee.ragdolldeaths.client.ConfigStore.get().copy();
				config.deathCamera = false;
				com.frostfleee.ragdolldeaths.client.ConfigStore.save(config);
			});
		}
		context.runOnClient(client -> client.options.fov().set(70));
	}

	/** Clears the stage back to bare grass, removes every body and entity, and puts the camera in place. */
	private static void stage(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, int x, int y, int z,
			double camX, double camY, double camZ, double atX, double atY, double atZ) {
		server.runCommand("kill @e[type=!player]");
		server.runCommand(String.format("fill %d %d %d %d %d %d air", x - 16, y, z - 16, x + 16, y + 12, z + 16));
		server.runCommand(String.format("fill %d %d %d %d %d %d dirt", x - 16, y - 3, z - 16, x + 16, y - 2, z + 16));
		server.runCommand(String.format("fill %d %d %d %d %d %d grass_block", x - 16, y - 1, z - 16, x + 16, y - 1, z + 16));
		context.waitTicks(3);
		server.runCommand("kill @e[type=!player]");
		context.runOnClient(client -> com.frostfleee.ragdolldeaths.client.RagdollTracker.INSTANCE.clear());
		// The camera is the spectator's eyes, 1.62 above where it is put.
		look(world, server, camX, camY - 1.62, camZ, atX, atY, atZ);
		context.waitTicks(10);
		settle(context, world);
	}

	/**
	 * Waits until the far land is all there: the server goes on sending chunks out to the view distance for a while
	 * after the camera moves, and each one popped in on the horizon in the middle of a clip. Done when no chunk has
	 * arrived for two seconds and every section is drawn, or after thirty seconds.
	 */
	private static void settle(ClientGameTestContext context, TestSingleplayerContext world) {
		int last = -1, still = 0;
		for (int tick = 0; tick < 600 && still < 40; tick++) {
			int loaded = context.computeOnClient(client -> client.level.getChunkSource().getLoadedChunksCount());
			boolean drawn = context.computeOnClient(client -> client.levelRenderer.hasRenderedAllSections());
			still = loaded == last && drawn ? still + 1 : 0;
			last = loaded;
			context.waitTick();
		}
		waitForChunks(world);
	}

	/** Something to be the attacker that nobody can see: an interaction entity has no model at all. */
	private static void attacker(TestServerContext server, double x, double y, double z) {
		server.runCommand(String.format(java.util.Locale.ROOT, "summon interaction %.2f %.2f %.2f {width:0f,height:0f,Tags:[\"hit\"]}", x, y, z));
	}

	/**
	 * Films a clip of the given length in game ticks at fifty frames a second of game time, running the commands
	 * ten ticks in, so the clip opens on the mob alive. Ends with one full-size still of the body at rest.
	 *
	 * <p>The test API's own screenshot cannot do this: it runs a game tick while it waits for the picture to come
	 * back from the graphics card, so every shot moves the game on. Here each frame is drawn at its point inside the
	 * tick and its copy is queued on the card; copies keep the frame they were queued after, so all of a tick's
	 * frames are queued first and then one tick passes, during which they are written out.
	 */
	private static void film(ClientGameTestContext context, TestServerContext server, String name, int ticks, String... commands) {
		context.waitTicks(12);
		java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger();
		context.runOnClient(client -> {
			client.getWindow().setWidth(FILM_WIDTH);
			client.getWindow().setHeight(FILM_HEIGHT);
			client.gameRenderer.resize(FILM_WIDTH, FILM_HEIGHT);
		});
		java.nio.file.Path dir = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("screenshots"));
		try {
			java.nio.file.Files.createDirectories(dir);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		int frames = ticks * 5 / 2;
		int k = 0;
		for (int tick = 0; tick < ticks; tick++) {
			if (tick == 10) {
				for (String command : commands) {
					server.runCommand(command);
				}
			}
			int first = k;
			// Frame k is at game time k * 0.4 ticks: two or three frames fall inside each tick.
			while (k < frames && k * 2 / 5 == tick) {
				k++;
			}
			int last = k;
			int at = tick;
			if (!FrameCapture.SUPPORTED) {
				// This game version has no way to draw a frame between ticks: one frame a tick, taken by the test API,
				// which itself moves the game on by the tick.
				context.takeScreenshot(TestScreenshotOptions.of(String.format("9000_%s-%04d", name, first)).disableCounterPrefix().withSize(FILM_WIDTH, FILM_HEIGHT));
				continue;
			}
			context.runOnClient(client -> {
				for (int f = first; f < last; f++) {
					FrameCapture.queue(client, f * 0.4f - at, dir.resolve(String.format("9000_%s-%04d.png", name, f)), pending);
				}
			});
			context.waitTick();
		}
		for (int i = 0; i < 200 && pending.get() > 0; i++) {
			context.waitTick();
		}
		LOGGER.info("[film] {}: {} frames, {} not written", name, frames, pending.get());
		context.waitTicks(20);
		context.takeScreenshot(TestScreenshotOptions.of(name + "-still").withSize(1920, 1080));
		report(context, "showcase " + name);
	}
}
