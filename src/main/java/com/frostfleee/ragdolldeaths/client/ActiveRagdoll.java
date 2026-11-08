package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.BodyKind;
import com.frostfleee.ragdolldeaths.core.DeathCause;
import com.frostfleee.ragdolldeaths.core.DeathProfile;
import com.frostfleee.ragdolldeaths.core.JointPose;
import com.frostfleee.ragdolldeaths.core.RagdollAnimator;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.core.RagdollPose;
import com.frostfleee.ragdolldeaths.core.SpecialDeaths;
import net.minecraft.client.renderer.entity.state.GuardianRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class ActiveRagdoll {
	private static final double GHOST_SETTLE_GRAVITY = 0.04;
	private static final double GHOST_LANDING_REST = 10.0;
	private static final int KNOCK_OFF_WINDOW_TICKS = 8;

	final LivingEntity entity;
	DeathProfile profile;
	private long startTick;
	private final long deathTick;
	private Vec3 anchor;
	private final float bodyYaw;
	private final RagdollPose pose = new RagdollPose();
	private final JointPose joints = new JointPose();
	private boolean keptAsGhost;
	private long ghostStartTick = -1L;
	private double ghostStartAge;
	private double ghostFloorDrop;
	private double idleBase = Double.NaN;
	private final RagdollPose lastPose = new RagdollPose();
	private Vec3 lastFeet;
	private boolean vanished;

	ActiveRagdoll(LivingEntity entity, DeathProfile profile, long currentTick) {
		this.entity = entity;
		this.profile = profile;
		this.startTick = currentTick - entity.deathTime;
		this.deathTick = startTick;
		this.anchor = entity.position();
		this.bodyYaw = entity.yBodyRot;
	}

	double age(long currentTick, float partialTicks) {
		return currentTick - startTick + partialTicks;
	}

	void apply(LivingEntityRenderState state, RagdollConfig config, long currentTick, float partialTicks) {
		double age = age(currentTick, partialTicks);
		RagdollAnimator.compute(profile, config, age, pose, joints);
		holdFacing(state);
		VanillaPoses.neutralize(state);
		spinDown(state, currentTick - deathTick + partialTicks, config);
		if (state instanceof GuardianRenderState guardian) {
			double spikes = SpecialDeaths.guardianSpikes(profile, age * config.animationSpeed);
			if (!Double.isNaN(spikes)) {
				guardian.spikesAnimation = (float) spikes;
				guardian.tailAnimation = (float) SpecialDeaths.guardianTail(profile, age * config.animationSpeed);
			}
		}

		boolean trajectory = profile.kind().airborne() || profile.freeFall();
		if (ghostStartTick >= 0L && !trajectory) {
			double since = currentTick - ghostStartTick + partialTicks;
			pose.offsetY -= Math.min(ghostFloorDrop, GHOST_SETTLE_GRAVITY * since * since);
		}

		if (trajectory) {
			pose.offsetX += anchor.x - state.x;
			pose.offsetY += anchor.y - state.y;
			pose.offsetZ += anchor.z - state.z;
			state.shadowPieces.clear();
			state.lightCoords = lightAt(entity.level(), state.x + pose.offsetX, state.y + pose.offsetY + profile.height() / 2.0, state.z + pose.offsetZ);
		}

		if (config.hideDeathTint) {
			state.hasRedOverlay = false;
		}
		RagdollRenderData.set(state, pose, config.limbAnimation ? joints : null);
		lastPose.copyFrom(pose);
		lastFeet = new Vec3(state.x, state.y, state.z);
	}

	/**
	 * Called once when the body disappears: leaves the death puff where the body last lay, as drawn. A body that was
	 * never drawn puffs where the entity stands. Shulkers already broke apart into shulker box particles.
	 */
	void vanish() {
		if (vanished || profile.kind() == BodyKind.SHULKER) {
			return;
		}
		vanished = true;
		RagdollPose where = lastFeet != null ? lastPose : new RagdollPose();
		Vec3 feet = lastFeet != null ? lastFeet : entity.position();
		DeathParticles.spawn(entity.level(), feet, where, profile.width(), profile.height(), entity.getRandom());
	}

	/**
	 * A dead mob keeps turning toward its last heading for a few ticks. The fall and roll axes were fixed at the
	 * moment of death, so the body is held at that heading; otherwise a sideways roll would also tip it nose- or
	 * tail-first into the ground.
	 */
	private void holdFacing(LivingEntityRenderState state) {
		float drift = Mth.wrapDegrees(state.bodyRot - bodyYaw);
		state.bodyRot = bodyYaw;
		state.yRot = Mth.wrapDegrees(state.yRot + drift);
	}

	/**
	 * Slows the model's own age-driven idle motion (a blaze's circling rods) to a stop. The age at death is kept,
	 * since a removed entity's age stops counting and could otherwise run the motion backward. {@code age} counts
	 * from the actual death, even after a knocked-off body restarts its animation.
	 */
	private void spinDown(LivingEntityRenderState state, double age, RagdollConfig config) {
		double idle = SpecialDeaths.idleAge(profile, age * config.animationSpeed);
		if (Double.isNaN(idle)) {
			return;
		}
		if (Double.isNaN(idleBase)) {
			idleBase = state.ageInTicks - age;
		}
		state.ageInTicks = (float) (idleBase + idle);
	}

	boolean isFinished(RagdollConfig config, long currentTick) {
		double end = RagdollAnimator.endTick(profile, config);
		if (ghostFloorDrop > 0.0) {
			double landed = ghostStartAge + Math.sqrt(ghostFloorDrop / GHOST_SETTLE_GRAVITY) + GHOST_LANDING_REST;
			end = Math.max(end, Math.min(config.ghostMaxTicks, landed));
		}
		return age(currentTick, 0.0F) >= end;
	}

	/**
	 * The killing blow often knocks a mob off a ledge only after it has died standing on it. For the first few ticks
	 * the body is checked again, and once it is clearly airborne it switches to falling and plays its death on landing.
	 */
	void checkKnockedOff(long currentTick) {
		if (profile.freeFall() || profile.kind().airborne() || profile.kind() == BodyKind.SHULKER || age(currentTick, 0.0F) > KNOCK_OFF_WINDOW_TICKS) {
			return;
		}
		if (!ProfileFactory.canFreeFall(entity)) {
			return;
		}
		double drop = heightAboveFloor();
		if (drop <= ProfileFactory.FREE_FALL_MIN_DROP) {
			return;
		}
		Vec3 velocity = entity.position().subtract(entity.xo, entity.yo, entity.zo);
		profile = profile.asFreeFall(-drop, velocity.x, velocity.y, velocity.z, ProfileFactory.travelClearance(entity.level(), entity, velocity));
		startTick = currentTick;
		anchor = entity.position();
	}

	boolean keptAsGhost() {
		return keptAsGhost;
	}

	void keepAsGhost() {
		keptAsGhost = true;
	}

	/**
	 * Called once the server has removed the entity. A removed entity is no longer ticked, so its previous and
	 * current positions would stay different forever and every frame would interpolate between them, making the
	 * body jitter. Pinning both to the last position stops that. A body still in the air at that point keeps
	 * falling to the floor and stays until it has landed, up to the ghost time limit.
	 */
	void becomeGhost(long currentTick) {
		entity.setOldPosAndRot();
		entity.yBodyRotO = entity.yBodyRot;
		entity.yHeadRotO = entity.yHeadRot;
		ghostStartTick = currentTick;
		ghostStartAge = age(currentTick, 0.0F);
		if (!profile.kind().airborne() && !profile.freeFall() && profile.cause() != DeathCause.WATER) {
			ghostFloorDrop = heightAboveFloor();
		}
	}

	private double heightAboveFloor() {
		Vec3 from = entity.position().add(0.0, 0.05, 0.0);
		Vec3 to = from.add(0.0, -ProfileFactory.FLOOR_SCAN, 0.0);
		HitResult hit = entity.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, entity));
		if (hit.getType() == HitResult.Type.MISS) {
			return 0.0;
		}
		return Math.max(0.0, entity.getY() - hit.getLocation().y);
	}

	private static int lightAt(Level level, double x, double y, double z) {
		BlockPos pos = BlockPos.containing(x, y, z);
		return LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
	}
}
