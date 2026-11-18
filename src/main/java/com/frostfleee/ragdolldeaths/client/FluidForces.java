package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.JointRole;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.core.WaterBehavior;
import com.frostfleee.ragdolldeaths.physics.Body;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Water and lava acting on a body, part by part, once a tick.
 *
 * <p>Each part is lifted in proportion to how much of it is under the surface, so a body that falls into water
 * slows, bobs up and floats instead of sinking to the bottom as if the water were air. The torso floats; arms and
 * legs are about as heavy as water and hang below it. The water also holds each part back (drag) and carries it
 * along its current. Lava is thicker: it holds a body far more, and with Sink in Lava on, it slowly swallows it.
 *
 * <p>Bubble columns: one rising from soul sand carries a body up quickly; one pulled down by magma holds it back,
 * so it rises only slowly.
 */
final class FluidForces {
	private static final double GRAVITY = 32.0;
	/** How readily each part floats, as its buoyancy against its weight: above 1 it rises, below it sinks. */
	private static final double TORSO_FLOAT = 1.35;
	private static final double TORSO_NEUTRAL = 1.0;
	private static final double TORSO_SINK = 0.75;
	private static final double HEAD_FLOAT = 1.1;
	private static final double LIMB_FLOAT = 0.97;
	/** Lava is denser than water: everything floats higher in it, until it starts to sink in. */
	private static final double LAVA_FLOAT = 1.6;
	private static final double LAVA_SINKING = 0.9;
	/** How strongly water and lava hold a fully submerged part back, per second, moving and turning. */
	private static final double WATER_DRAG = 2.2;
	private static final double WATER_SPIN_DRAG = 2.5;
	private static final double LAVA_DRAG = 7.0;
	/**
	 * Drag that grows with speed, per block of speed: a body hitting the water fast is stopped within a block or two,
	 * as water really does, while one floating gently is held back no more than before.
	 */
	private static final double WATER_SPEED_DRAG = 0.3;
	private static final double LAVA_SPEED_DRAG = 0.6;
	/** How fast a current carries a floating body along, blocks per second, for the strongest flow. */
	private static final double CURRENT = 1.6;
	/** The speed a bubble column pulls a body towards, blocks per second: up from soul sand, down from magma. */
	private static final double SOUL_SAND_LIFT = 4.0;
	private static final double MAGMA_PULL = -0.9;
	private static final double COBWEB_DRAG = 40.0;
	private static final double BERRY_BUSH_DRAG = 6.0;
	private static final double POWDER_SNOW_DRAG = 9.0;
	/** Powder snow holds a body up like a thick liquid would: it ends a little more than half sunk in. */
	private static final double POWDER_SNOW_LIFT = 1.7;

	/** After this long in lava, with Sink in Lava on, the body starts to go under. */
	static final int LAVA_SINK_TICKS = 50;

	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
	// What the last sample found for the part.
	private double submerged;
	private boolean lava;
	// Blocks a body sinks into instead of landing on: how much of the part is in them, and how they act.
	private double inMedium;
	private double mediumDrag, mediumLift;
	private double flowX, flowY, flowZ;

	/**
	 * Sets every part's fluid forces for the coming tick.
	 *
	 * @return whether any part is in water or lava
	 */
	boolean apply(PhysicsRagdoll ragdoll, RagdollConfig config) {
		Level level = ragdoll.entity.level();
		boolean any = false, inLava = false;
		for (int i = 0; i < ragdoll.bodies.length; i++) {
			Body body = ragdoll.bodies[i];
			sample(level, body);
			if (submerged <= 0.0) {
				body.lift = GRAVITY * mediumLift * inMedium;
				body.fluidDrag = mediumDrag * inMedium;
				body.fluidSpinDrag = mediumDrag * inMedium;
				body.flowX = body.flowY = body.flowZ = 0.0;
				any |= inMedium > 0.0;
				if (ragdoll.wet != null) {
					ragdoll.wet[i] = false;
				}
				if (ragdoll.lavaNow != null) {
					ragdoll.lavaNow[i] = false;
				}
				continue;
			}
			any = true;
			inLava |= lava;
			if (ragdoll.wet == null) {
				ragdoll.wet = new boolean[ragdoll.bodies.length];
			}
			ragdoll.wet[i] = !lava;
			if (ragdoll.lavaNow == null) {
				ragdoll.lavaNow = new boolean[ragdoll.bodies.length];
			}
			ragdoll.lavaNow[i] = lava;
			if (lava) {
				if (ragdoll.burnt == null) {
					ragdoll.burnt = new boolean[ragdoll.bodies.length];
				}
				ragdoll.burnt[i] = true;
			}
			double floats = lava ? lavaFloat(ragdoll, config) : waterFloat(ragdoll.roles[i], i == ragdoll.torso, config);
			body.lift = GRAVITY * floats * submerged;
			double speed = Math.sqrt((body.vx - flowX) * (body.vx - flowX) + (body.vy - flowY) * (body.vy - flowY) + (body.vz - flowZ) * (body.vz - flowZ));
			body.fluidDrag = (lava ? LAVA_DRAG + LAVA_SPEED_DRAG * speed : WATER_DRAG + WATER_SPEED_DRAG * speed) * submerged;
			body.fluidSpinDrag = (lava ? LAVA_DRAG : WATER_SPIN_DRAG) * submerged;
			body.flowX = flowX;
			body.flowY = flowY;
			body.flowZ = flowZ;
		}
		ragdoll.lavaTicks = inLava ? ragdoll.lavaTicks + 1 : 0;
		// Lava burns a body black, and it stays black wherever it ends up afterwards.
		ragdoll.charred |= inLava;
		if (inLava) {
			ragdoll.scorchTick();
		}
		return any;
	}

	/** Whether the body is in water or lava that is moving it: a sleeping body there has to wake. */
	boolean moving(PhysicsRagdoll ragdoll) {
		Level level = ragdoll.entity.level();
		for (Body body : ragdoll.bodies) {
			sample(level, body);
			if (submerged > 0.0 && (flowX != 0.0 || flowY != 0.0 || flowZ != 0.0)) {
				return true;
			}
		}
		return false;
	}

	private static double waterFloat(JointRole role, boolean torso, RagdollConfig config) {
		if (torso) {
			return switch (config.waterBehavior) {
				case FLOAT -> TORSO_FLOAT;
				case SINK -> TORSO_SINK;
				case NEUTRAL -> TORSO_NEUTRAL;
			};
		}
		double sink = config.waterBehavior == WaterBehavior.SINK ? 0.85 : 1.0;
		return (role == JointRole.HEAD ? HEAD_FLOAT : LIMB_FLOAT) * sink;
	}

	private static double lavaFloat(PhysicsRagdoll ragdoll, RagdollConfig config) {
		return config.lavaSink && ragdoll.lavaTicks > LAVA_SINK_TICKS ? LAVA_SINKING : LAVA_FLOAT;
	}

	/**
	 * How much of the part is under the surface (0 to 1), whether that is lava, and which way the fluid carries it.
	 * The part is measured as the column of blocks from its lowest point to its highest.
	 */
	private void sample(Level level, Body body) {
		submerged = 0.0;
		lava = false;
		flowX = flowY = flowZ = 0.0;
		inMedium = 0.0;
		mediumDrag = mediumLift = 0.0;
		double half = body.halfHeight();
		double bottom = body.y - half, top = body.y + half;
		int x = Mth.floor(body.x), z = Mth.floor(body.z);
		double wet = 0.0;
		boolean sampledFlow = false;
		for (int y = Mth.floor(bottom); y <= Mth.floor(top); y++) {
			cursor.set(x, y, z);
			FluidState fluid = level.getFluidState(cursor);
			if (fluid.isEmpty()) {
				medium(level.getBlockState(cursor), Math.max(0.0, Math.min(top, y + 1.0) - Math.max(bottom, y)));
				continue;
			}
			boolean isLava = fluid.is(FluidTags.LAVA);
			if (!isLava && !fluid.is(FluidTags.WATER)) {
				continue;
			}
			double surface = y + fluid.getHeight(level, cursor);
			wet += Math.max(0.0, Math.min(top, surface) - Math.max(bottom, y));
			lava |= isLava;
			if (!sampledFlow) {
				sampledFlow = true;
				Vec3 flow = fluid.getFlow(level, cursor);
				flowX = flow.x * CURRENT;
				flowZ = flow.z * CURRENT;
				BlockState state = level.getBlockState(cursor);
				if (state.is(Blocks.BUBBLE_COLUMN)) {
					flowY = state.getValue(BubbleColumnBlock.DRAG_DOWN) ? MAGMA_PULL : SOUL_SAND_LIFT;
				}
			}
		}
		submerged = top > bottom ? Math.clamp(wet / (top - bottom), 0.0, 1.0) : 0.0;
		inMedium = top > bottom ? Math.clamp(inMedium / (top - bottom), 0.0, 1.0) : 0.0;
	}

	/**
	 * Blocks with no collision that a body sinks into: a cobweb catches it and holds it almost still, a sweet berry
	 * bush slows it, powder snow lets it sink part way in and holds it there.
	 */
	private void medium(BlockState state, double depth) {
		if (depth <= 0.0) {
			return;
		}
		double drag, lift;
		if (state.is(Blocks.COBWEB)) {
			drag = COBWEB_DRAG;
			lift = 0.0;
		} else if (state.is(Blocks.POWDER_SNOW)) {
			drag = POWDER_SNOW_DRAG;
			lift = POWDER_SNOW_LIFT;
		} else if (state.is(Blocks.SWEET_BERRY_BUSH)) {
			drag = BERRY_BUSH_DRAG;
			lift = 0.0;
		} else {
			return;
		}
		inMedium += depth;
		mediumDrag = Math.max(mediumDrag, drag);
		mediumLift = Math.max(mediumLift, lift);
	}
}
