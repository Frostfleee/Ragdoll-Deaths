package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.BodyMotion;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The real world a body moves through: vanilla's block collision, and the water and lava in it. */
final class MotionWorld implements BodyMotion.World {
	private final Level level;
	private final Entity entity;

	MotionWorld(Level level, Entity entity) {
		this.level = level;
		this.entity = entity;
	}

	@Override
	public double[] collide(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double dx, double dy, double dz) {
		// The same sweep vanilla moves entities with, against blocks only: other mobs don't stop a body.
		Vec3 free = Entity.collideBoundingBox(entity, new Vec3(dx, dy, dz), new AABB(minX, minY, minZ, maxX, maxY, maxZ), level, List.of());
		return new double[] {free.x, free.y, free.z};
	}

	@Override
	public BodyMotion.Fluid fluidAt(double x, double y, double z) {
		BlockPos pos = BlockPos.containing(x, y, z);
		FluidState fluid = level.getFluidState(pos);
		if (fluid.isEmpty() || y > pos.getY() + fluid.getHeight(level, pos)) {
			return BodyMotion.Fluid.NONE;
		}
		boolean water = fluid.is(FluidTags.WATER);
		boolean lava = fluid.is(FluidTags.LAVA);
		if (!water && !lava) {
			return BodyMotion.Fluid.NONE;
		}
		Vec3 flow = fluid.getFlow(level, pos);
		return new BodyMotion.Fluid(water, lava, flow.x, flow.z);
	}
}
