package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.RagdollPose;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Keeps a toppling body out of walls. A body falling over beside a wall would otherwise swing straight through
 * it; instead it is tilted back up about its feet just far enough that no part of its length is inside a block,
 * so it comes to rest slumped against the wall.
 */
final class BodyContact {
	/** Points along the body, as fractions of its height from the feet, that must stay out of blocks. */
	private static final double[] SPINE = {0.45, 0.7, 0.95};
	private static final int SEARCH_STEPS = 7;
	/** Ignores contact this close to the feet, where the body touches the floor it lies on. */
	private static final double FLOOR_MARGIN = 0.12;

	private BodyContact() {
	}

	/**
	 * How far the body must be tilted back up, in radians, to clear the blocks around it; 0 when it is already
	 * clear, or when even standing upright wouldn't free it.
	 */
	static double lift(Level level, RagdollPose pose, Vec3 feet, double height, double width) {
		if (!inside(level, pose, feet, height, width, 0.0)) {
			return 0.0;
		}
		double tilt = pose.tilt();
		if (tilt < 0.05 || inside(level, pose, feet, height, width, tilt)) {
			return 0.0;
		}
		double free = tilt;
		double blocked = 0.0;
		for (int i = 0; i < SEARCH_STEPS; i++) {
			double middle = 0.5 * (free + blocked);
			if (inside(level, pose, feet, height, width, middle)) {
				blocked = middle;
			} else {
				free = middle;
			}
		}
		return free;
	}

	private static boolean inside(Level level, RagdollPose pose, Vec3 feet, double height, double width, double lift) {
		RagdollPose tried = new RagdollPose();
		tried.copyFrom(pose);
		tried.liftTop(lift, height);
		double[] base = tried.toWorld(0.0, 0.0, 0.0);
		double floor = feet.y + base[1] + FLOOR_MARGIN;
		for (double along : SPINE) {
			double[] point = tried.toWorld(0.0, height * along, 0.0);
			double y = feet.y + point[1];
			if (y > floor && solid(level, feet.x + point[0], y, feet.z + point[2])) {
				return true;
			}
		}
		return false;
	}

	static boolean solid(Level level, double x, double y, double z) {
		BlockPos pos = BlockPos.containing(x, y, z);
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return false;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		if (shape.isEmpty()) {
			return false;
		}
		for (AABB box : shape.toAabbs()) {
			if (box.contains(x - pos.getX(), y - pos.getY(), z - pos.getZ())) {
				return true;
			}
		}
		return false;
	}
}
