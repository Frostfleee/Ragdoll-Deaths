package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.Ground;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Measures the ground a body comes to rest on, with a few straight-down raycasts made once when it settles. */
final class GroundProbe {
	/** How far below the resting floor a sample looks before it counts as a drop-off. */
	private static final double MAX_DROP = 2.5;

	private GroundProbe() {
	}

	/** The rise of the floor around (x, floorY, z), sampled about as far out as the body will lie. */
	static Ground around(Level level, Entity entity, double x, double floorY, double z, double height) {
		double radius = Math.clamp(0.55 * height, 0.35, 2.0);
		double top = floorY + Math.clamp(height, 0.6, 1.6);
		double[] rise = new double[Ground.DIRECTIONS];
		for (int k = 0; k < Ground.DIRECTIONS; k++) {
			double sx = x + Ground.directionX(k) * radius;
			double sz = z + Ground.directionZ(k) * radius;
			HitResult hit = level.clip(new ClipContext(new Vec3(sx, top, sz), new Vec3(sx, floorY - MAX_DROP, sz), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, entity));
			rise[k] = hit.getType() == HitResult.Type.MISS ? -MAX_DROP : hit.getLocation().y - floorY;
		}
		return new Ground(radius, rise);
	}

	/** The floor's height below {@code from}, or {@code from.y - scan} when there is none within reach. */
	static double floorBelow(Level level, Entity entity, Vec3 from, double scan) {
		Vec3 start = from.add(0.0, 0.05, 0.0);
		HitResult hit = level.clip(new ClipContext(start, start.add(0.0, -scan, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, entity));
		return hit.getType() == HitResult.Type.MISS ? from.y - scan : Math.min(from.y, hit.getLocation().y);
	}
}
