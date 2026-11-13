package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.physics.BoxBuffer;
import com.frostfleee.ragdolldeaths.physics.ColliderProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The blocks a ragdoll collides with: every box of every block's collision shape, so slabs, stairs, fences, walls,
 * panes, carpets, snow layers and modded shapes all work without special cases. Each box carries its block's grip
 * and bounce.
 */
final class BlockColliders implements ColliderProvider {
	private static final java.util.Set<net.minecraft.world.level.block.Block> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final Level level;
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	BlockColliders(Level level) {
		this.level = level;
	}

	@Override
	public void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, BoxBuffer out) {
		int x0 = Mth.floor(minX), x1 = Mth.floor(maxX);
		// One block lower too: fences and walls are taller than their own block.
		int y0 = Mth.floor(minY) - 1, y1 = Mth.floor(maxY);
		int z0 = Mth.floor(minZ), z1 = Mth.floor(maxZ);
		// X is the innermost loop so that neighbours along X arrive one after another and are joined into strips.
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					BlockState state = level.getBlockState(cursor.set(x, y, z));
					if (state.isAir()) {
						continue;
					}
					VoxelShape shape = state.getCollisionShape(level, cursor);
					if (shape.isEmpty()) {
						continue;
					}
					double friction = grip(state);
					if (PhysicsHost.DEBUG && friction < 0.5 && LOGGED.add(state.getBlock())) {
						RagdollDeathsClient.LOGGER.info("[physics] {} grips {} (slipperiness {})", state.getBlock().getDescriptionId(), friction, state.getBlock().getFriction());
					}
					double bounce = bounce(state);
					int bx = x, by = y, bz = z;
					shape.forAllBoxes((ax, ay, az, cx, cy, cz) -> out.add(bx + ax, by + ay, bz + az, bx + cx, by + cy, bz + cz, friction, bounce));
				}
			}
		}
	}

	/**
	 * The game gives every block a slipperiness: 0.6 for nearly everything, 0.98 for ice, 0.989 for blue ice. That is
	 * how much speed a walking mob keeps each tick, not a friction coefficient, so it is mapped: ordinary blocks grip
	 * well (0.8), ice hardly at all.
	 */
	private static double grip(BlockState state) {
		if (state.is(Blocks.HONEY_BLOCK)) {
			return 2.5;
		}
		double slipperiness = state.getBlock().getFriction();
		return Math.clamp((1.0 - slipperiness) * 2.0, 0.02, 1.0);
	}

	private static double bounce(BlockState state) {
		if (state.is(Blocks.SLIME_BLOCK)) {
			return 0.8;
		}
		return state.getBlock() instanceof BedBlock ? 0.5 : 0.0;
	}
}
