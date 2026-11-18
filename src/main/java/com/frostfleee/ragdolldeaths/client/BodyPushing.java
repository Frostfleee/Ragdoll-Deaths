package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.physics.Body;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Push Dead Bodies: a living player or mob that walks into a physics body pushes it along, the way you would nudge
 * something lying on the floor. It only goes one way. The body never pushes back, so the player and the mobs move
 * exactly as in vanilla, and the server never hears of it. Only this client sees the body move.
 *
 * <p>Each part is treated as a ball the size of its box, and each living thing as its hitbox, which acts like a wall
 * that cannot be moved: a part inside it is given at least the speed the walker moves into it with, plus a little to
 * get it back out, sideways only. Standing on a body does not press it into the ground.
 *
 * <p>The walkers near a body are found once a tick ({@link #find}); the push is given before every physics step
 * ({@link #push}), with the walker where it is at that moment of the tick, so ground friction does not eat it.
 */
final class BodyPushing {
	/** How fast a part that is inside a walker is moved back out, per block it is in, in blocks per second. */
	private static final double SEPARATE = 6.0;
	/** The fastest a part is moved out of a walker that is standing still, in blocks per second. */
	private static final double MAX_SEPARATE = 2.5;
	/** The most of a walker's own speed a part takes on: a sprinting player nudges a body, it does not bat it away. */
	private static final double CARRY = 1.1;
	/** A part only this far into the walker's sides (in blocks) is left alone, so a body lying against a mob rests. */
	private static final double SLOP = 0.02;

	private BodyPushing() {
	}

	/** Notes the living things near the body for this tick's pushes; an empty list when there are none. */
	static void find(PhysicsRagdoll ragdoll) {
		Body[] bodies = ragdoll.bodies;
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (Body b : bodies) {
			double r = radius(b);
			minX = Math.min(minX, b.x - r);
			minY = Math.min(minY, b.y - r);
			minZ = Math.min(minZ, b.z - r);
			maxX = Math.max(maxX, b.x + r);
			maxY = Math.max(maxY, b.y + r);
			maxZ = Math.max(maxZ, b.z + r);
		}
		// A little wider than the body: a walker reaching it later in the tick still pushes.
		ragdoll.walkers = ragdoll.entity.level().getEntitiesOfClass(LivingEntity.class, new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.5, 0.0, 0.5),
				walker -> walker != ragdoll.entity && walker.isAlive() && !walker.isDeadOrDying() && !walker.isSpectator() && !walker.isPassenger()
						&& !(walker instanceof Player player && player.getAbilities().flying));
	}

	/**
	 * Pushes the body's parts out of every walker found this tick, each where it is at {@code along} (0 to 1) through
	 * the tick; wakes the body if anything moved it.
	 */
	static void push(PhysicsRagdoll ragdoll, double along) {
		List<LivingEntity> walkers = ragdoll.walkers;
		if (walkers == null || walkers.isEmpty()) {
			return;
		}
		boolean pushed = false;
		for (LivingEntity walker : walkers) {
			AABB box = walker.getBoundingBox();
			// How the walker moved over the last tick, in blocks per second, and where it is at this point of the tick.
			double stepX = walker.getX() - walker.xo, stepZ = walker.getZ() - walker.zo;
			double wx = stepX * 20.0, wz = stepZ * 20.0;
			double cx = (box.minX + box.maxX) * 0.5 - stepX * (1.0 - along), cz = (box.minZ + box.maxZ) * 0.5 - stepZ * (1.0 - along);
			double halfWidth = (box.maxX - box.minX) * 0.5;
			for (Body b : ragdoll.bodies) {
				double r = radius(b);
				// Above the walker's feet (a part it stands on is left alone) and below the top of its head.
				if (b.y + r < box.minY + 0.2 || b.y - r > box.maxY) {
					continue;
				}
				double dx = b.x - cx, dz = b.z - cz;
				double distance = Math.sqrt(dx * dx + dz * dz);
				double depth = halfWidth + r - distance;
				if (depth <= SLOP) {
					continue;
				}
				double nx, nz;
				if (distance > 1.0E-4) {
					nx = dx / distance;
					nz = dz / distance;
				} else {
					// Right in the middle: out the way the walker is going, or any way if it stands still.
					double speed = Math.sqrt(wx * wx + wz * wz);
					nx = speed > 1.0E-4 ? wx / speed : 1.0;
					nz = speed > 1.0E-4 ? wz / speed : 0.0;
				}
				double walking = Math.max(0.0, wx * nx + wz * nz) * CARRY;
				double wanted = walking + Math.min(MAX_SEPARATE, (depth - SLOP) * SEPARATE);
				double now = b.vx * nx + b.vz * nz;
				if (now < wanted) {
					b.vx += (wanted - now) * nx;
					b.vz += (wanted - now) * nz;
					pushed = true;
				}
			}
		}
		if (pushed && ragdoll.island.asleep) {
			ragdoll.island.wake();
		}
	}

	/** A part as a ball: about the size of its box, a little smaller so a body lying flat is not pushed by a foot beside it. */
	private static double radius(Body b) {
		return Math.max(b.hx, Math.max(b.hy, b.hz)) * 0.8;
	}
}
