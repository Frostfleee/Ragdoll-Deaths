package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The death camera: while you are dead, the view follows your own body's head from a few blocks away (Death Camera
 * Distance) and slowly circles it (Death Camera Circling), so you watch yourself fall and come to rest. It starts behind where you were facing, keeps out of
 * walls, and hands the view back when you respawn.
 */
public final class DeathCamera {
	/** How far it looks down at the head, in degrees. */
	private static final float PITCH = 28.0F;
	/** How fast it circles, in degrees per tick: a full turn takes about thirty seconds. */
	private static final float TURN_PER_TICK = 0.6F;
	/** How far it keeps from a wall it has been pulled in by. */
	private static final double WALL_GAP = 0.25;

	private static long startTick = -1;
	private static float startYaw;

	private DeathCamera() {
	}

	/** Where the camera goes this frame and which way it looks, or null to leave the camera as the game set it. */
	public static Pose place(float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		RagdollConfig config = ConfigStore.get();
		if (player == null || !player.isDeadOrDying() || !config.deathCamera || !config.enabled || !config.playerRagdolls) {
			startTick = -1;
			return null;
		}
		Vec3 head = RagdollTracker.INSTANCE.bodyHead(player, partialTicks);
		if (head == null) {
			return null;
		}
		long now = RagdollTracker.INSTANCE.ticks();
		if (startTick < 0) {
			startTick = now;
			startYaw = player.getYRot();
		}
		// Death Camera Distance and Circling are settings; without circling it holds the angle it started from.
		float yaw = startYaw + (config.deathCameraCircles ? (now - startTick + partialTicks) * TURN_PER_TICK : 0.0F);
		double yawRad = Math.toRadians(yaw), pitchRad = Math.toRadians(PITCH);
		// The way the camera looks; it sits that far back from the head along it.
		Vec3 look = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
		Vec3 wanted = head.subtract(look.scale(config.deathCameraDistance));
		HitResult hit = player.level().clip(new ClipContext(head, wanted, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		Vec3 at = wanted;
		if (hit.getType() != HitResult.Type.MISS) {
			double reach = Math.max(0.3, hit.getLocation().distanceTo(head) - WALL_GAP);
			at = head.subtract(look.scale(reach));
		}
		return new Pose(at, yaw, PITCH);
	}

	/** A camera position and the way it looks, in the game's degrees. */
	public record Pose(Vec3 position, float yaw, float pitch) {
	}
}
