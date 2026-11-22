package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.BodyKind;
import com.frostfleee.ragdolldeaths.core.BodyMotion;
import com.frostfleee.ragdolldeaths.core.DeathCause;
import com.frostfleee.ragdolldeaths.core.DeathProfile;
import com.frostfleee.ragdolldeaths.core.JointPose;
import com.frostfleee.ragdolldeaths.core.LimbDynamics;
import com.frostfleee.ragdolldeaths.core.RagdollAnimator;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.core.RagdollPose;
import com.frostfleee.ragdolldeaths.core.SpecialDeaths;
import com.frostfleee.ragdolldeaths.core.WaterBehavior;
import net.minecraft.client.renderer.entity.state.GuardianRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class ActiveRagdoll {
	private static final double GHOST_SETTLE_GRAVITY = 0.04;
	private static final double GHOST_LANDING_REST = 10.0;
	private static final int KNOCK_OFF_WINDOW_TICKS = 8;
	/** How fast a body leaning on a wall settles back down once nothing holds it up, in radians per tick. */
	private static final double CONTACT_RELEASE = 0.15;
	/** A body in lava with Sink in Lava on is gone this long after it fell in: it has gone under by then. */
	private static final int LAVA_GONE_TICKS = FluidForces.LAVA_SINK_TICKS + 40;

	final LivingEntity entity;
	DeathProfile profile;
	private long startTick;
	private final long deathTick;
	private Vec3 anchor;
	private final float bodyYaw;
	private final RagdollPose pose = new RagdollPose();
	private final JointPose joints = new JointPose();
	private final RagdollPose stepPose = new RagdollPose();
	private final JointPose stepJoints = new JointPose();
	private final MotionWorld world;
	private BodyMotion motion;
	/**
	 * While the server's entity exists, a falling body follows it: the server simulates the corpse's fall exactly,
	 * so the body lands where the mob really lands. The body's own motion takes over once the entity is gone.
	 */
	private boolean followEntity;
	private int stillTicks;
	private double landedY = Double.NaN;
	private final LimbDynamics limbs;
	private LimbCollider collider;
	private boolean colliderLooked;
	/** How far the body is tilted back up to rest against a wall, this tick and the last, in radians. */
	private double contact;
	private double lastContact;
	private boolean keptAsGhost;
	private long ghostStartTick = -1L;
	private double ghostStartAge;
	private double ghostFloorDrop;
	private double idleBase = Double.NaN;
	private final RagdollPose lastPose = new RagdollPose();
	private Vec3 lastFeet;
	private boolean vanished;
	/** The body as real physics, when this mob gets one; the fields above then only serve as the fallback. */
	private PhysicsRagdoll physics;

	ActiveRagdoll(LivingEntity entity, DeathProfile profile, long currentTick, RagdollConfig config) {
		this.entity = entity;
		this.profile = profile;
		this.startTick = currentTick - entity.deathTime;
		this.deathTick = startTick;
		this.anchor = entity.position();
		this.bodyYaw = entity.yBodyRot;
		this.world = new MotionWorld(entity.level(), entity);
		this.limbs = hasLimbPhysics(profile) ? new LimbDynamics() : null;
		if (profile.simulated()) {
			startMotion(config);
			// A thrown rider (still seated on the server) and a blasted body (thrown harder than the server pushes it)
			// take their own path; a mob that just died in the air falls exactly like its entity.
			followEntity = profile.freeFall() && !profile.blasted() && !entity.isPassenger();
		}
		if (config.physicsRagdolls) {
			physics = new PhysicsRagdoll(entity);
			if (entity.isPassenger() && !profile.blasted()) {
				// A rider (on a living mount, in a boat or in a minecart) is still seated on the server, so its own last
				// move is the vehicle's: it leaves the seat with the throw the classic death uses, per second.
				physics.seatThrow = new Vec3(profile.velocityX(), profile.velocityY(), profile.velocityZ()).scale(20.0);
			}
			physics.bleeds = Blood.bleeds(entity, profile) && !entity.isInLava();
		} else if (config.blood && Blood.bleeds(entity, profile) && !entity.isInLava()) {
			Blood.blow(entity, 1.0);
		}
	}

	boolean isPhysical() {
		return physics != null && !physics.failed;
	}

	/** Removes the body from the physics world without the puff: the ragdoll is being dropped. */
	void discard() {
		if (physics != null) {
			PhysicsHost.INSTANCE.remove(physics);
		}
	}

	long startTick() {
		return startTick;
	}

	/** Before each physics tick: the fall reflexes and the twitches after landing. */
	void driveReflexes(long currentTick, RagdollConfig config) {
		if (!isPhysical() || !physics.built() || physics.reflexes == null) {
			return;
		}
		physics.reflexes.update(physics, age(currentTick, 0.0F), config);
	}

	/** Bodies that fall apart, melt or are made of loose rods animate their pieces their own way. */
	private static boolean hasLimbPhysics(DeathProfile profile) {
		if (profile.kind() == BodyKind.SHULKER || profile.kind() == BodyKind.CUBE) {
			return false;
		}
		return switch (profile.special()) {
			case SKELETON, SNOW_GOLEM, BLAZE, BREEZE -> false;
			default -> true;
		};
	}

	private void startMotion(RagdollConfig config) {
		WaterBehavior water = profile.kind() == BodyKind.AQUATIC ? WaterBehavior.FLOAT : config.waterBehavior;
		boolean driftOnly = !profile.freeFall() && !profile.blasted() && !profile.kind().airborne();
		motion = new BodyMotion(BodyMotion.Air.of(profile.kind()), profile.width(), profile.height(), water, config.bounceStrength, driftOnly,
				profile.velocityX(), profile.velocityY(), profile.velocityZ());
	}

	double age(long currentTick, float partialTicks) {
		return currentTick - startTick + partialTicks;
	}

	/**
	 * Advances the body's physics by one tick: its motion through the world (falling, bouncing, sliding, carried
	 * by a current), then its limbs, pushed by how the body moved.
	 */
	void step(long currentTick, RagdollConfig config) {
		double age = age(currentTick, 0.0F);
		if (age < 0.0) {
			return;
		}
		if (physics != null) {
			if (!physics.failed || physics.built()) {
				// The physics world moves the body. One that failed after it had already moved is simply removed.
				return;
			}
			// It could not be built at all: the classic animation takes over from the start.
			physics = null;
		}
		RagdollAnimator.compute(profile, config, age, stepPose, stepJoints);
		if (motion != null) {
			if (!followEntity || !follow()) {
				motion.step(world, anchor.x, anchor.y, anchor.z, stepPose.offsetY);
			}
			if (motion.justLanded()) {
				land(config, age);
			}
		}
		lastContact = contact;
		Vec3 feet = feet();
		if (profile.kind() != BodyKind.SHULKER) {
			// Pushed out of a wall at once; eased back down when the wall is gone (the body slid away from it).
			double needed = BodyContact.lift(entity.level(), stepPose, feet, profile.height(), profile.width());
			contact = needed >= contact ? needed : Math.max(needed, contact - CONTACT_RELEASE);
			stepPose.liftTop(contact, profile.height());
		}
		if (limbs != null && config.limbAnimation) {
			double[] middle = stepPose.toWorld(0.0, profile.height() / 2.0, 0.0);
			limbs.step(profile, config, stepPose, stepJoints, feet.x + middle[0], feet.y + middle[1], feet.z + middle[2], age * config.animationSpeed);
			if (!colliderLooked) {
				colliderLooked = true;
				collider = LimbCollider.of(rigOf(entity), profile.height());
			}
			if (collider != null) {
				collider.resolve(entity.level(), stepPose, stepJoints, limbs, feet, bodyYaw);
			}
		}
	}

	/**
	 * Moves the body with the server's entity for this tick; false once there is nothing to follow. A corpse that
	 * hangs in the air without moving (a mob with no AI never falls) is left behind, and the body falls on its own.
	 * A body that came to rest on an edge and then slid off falls again, and lands again further down.
	 */
	private boolean follow() {
		if (entity.isRemoved()) {
			return false;
		}
		Vec3 now = entity.position();
		Vec3 moved = now.subtract(entity.xo, entity.yo, entity.zo);
		boolean onGround = entity.onGround() || entity.isInWater() || entity.isInLava();
		stillTicks = !onGround && moved.lengthSqr() < 1.0E-8 ? stillTicks + 1 : 0;
		if (stillTicks >= 2) {
			followEntity = false;
			return false;
		}
		if (motion.landed() && !onGround && !Double.isNaN(landedY) && landedY - now.y > 1.0) {
			motion.unland();
			profile = profile.withLanding(Double.NaN, DeathCause.GROUND);
			landedY = Double.NaN;
		}
		motion.follow(world, anchor.x, anchor.y, anchor.z, now.x - anchor.x, now.y - anchor.y, now.z - anchor.z, moved.x, moved.y, moved.z, onGround);
		return true;
	}

	private static JointRig rigOf(LivingEntity entity) {
		var renderer = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
		return renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer living ? JointRig.of(Compat.root(living.getModel())) : null;
	}

	/** The body has touched the ground or a fluid: its landing death starts, fitted to the ground it lies on. */
	private void land(RagdollConfig config, double age) {
		profile = profile.withLanding(age * config.animationSpeed, motion.landedIn());
		landedY = anchor.y + motion.y();
		if (motion.landedIn() == DeathCause.GROUND) {
			profile = profile.withGround(GroundProbe.around(entity.level(), entity, anchor.x + motion.x(), anchor.y + motion.y(), anchor.z + motion.z(), profile.height()));
		}
	}

	/** Where the body's feet are this tick, before the animation's own offsets. */
	private Vec3 feet() {
		if (motion != null) {
			return anchor.add(motion.x(), motion.isDriftOnly() ? 0.0 : motion.y(), motion.z());
		}
		return profile.followsTrajectory() ? anchor : entity.position();
	}

	void apply(LivingEntityRenderState state, RagdollConfig config, long currentTick, float partialTicks) {
		if (isPhysical()) {
			applyPhysics(state, config, partialTicks);
			return;
		}
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
		if (motion != null) {
			pose.offsetX += motion.x(partialTicks);
			pose.offsetZ += motion.z(partialTicks);
			if (!motion.isDriftOnly()) {
				pose.offsetY += motion.y(partialTicks);
			}
		}
		pose.liftTop(lastContact + (contact - lastContact) * partialTicks, profile.height());
		if (limbs != null && config.limbAnimation) {
			limbs.applyTo(joints, partialTicks);
		}

		boolean trajectory = profile.followsTrajectory();
		if (ghostStartTick >= 0L && !trajectory) {
			double since = currentTick - ghostStartTick + partialTicks;
			pose.offsetY -= Math.min(ghostFloorDrop, GHOST_SETTLE_GRAVITY * since * since);
		}

		if (trajectory) {
			pose.offsetX += anchor.x - state.x;
			pose.offsetY += anchor.y - state.y;
			pose.offsetZ += anchor.z - state.z;
			Compat.hideShadow(state);
			Compat.setLight(state, lightAt(entity.level(), state.x + pose.offsetX, state.y + pose.offsetY + profile.height() / 2.0, state.z + pose.offsetZ));
		}

		if (config.hideDeathTint) {
			state.hasRedOverlay = false;
		}
		RagdollRenderData.set(state, pose, config.limbAnimation ? joints : null);
		lastPose.copyFrom(pose);
		lastFeet = new Vec3(state.x, state.y, state.z);
	}

	/**
	 * The model itself is posed later, part by part, from the bodies. Here the entity as a whole is moved onto the
	 * torso, so that everything drawn at "the entity" (flames, a name tag) is drawn where the body is, and the body
	 * is lit by where it lies.
	 */
	private void applyPhysics(LivingEntityRenderState state, RagdollConfig config, float partialTicks) {
		pose.reset();
		physics.alpha = partialTicks;
		if (state instanceof net.minecraft.client.renderer.entity.state.SlimeRenderState slime) {
			// A slime's renderer stretches the whole model by its squish, differently on each axis. The parts are
			// placed by undoing the renderer's transform, which only works when it scales every axis alike; the
			// body's own squash and wobble are drawn by the ragdoll instead.
			slime.squish = 0.0F;
		}
		if (physics.built()) {
			Vec3 torso = physics.torsoPosition(partialTicks);
			pose.offsetX = torso.x - state.x;
			pose.offsetY = torso.y - profile.height() / 2.0 - state.y;
			pose.offsetZ = torso.z - state.z;
			Compat.hideShadow(state);
			Compat.setLight(state, lightAt(entity.level(), torso.x, torso.y, torso.z));
			lastPose.copyFrom(pose);
			lastFeet = new Vec3(state.x, state.y, state.z);
		}
		if (config.hideDeathTint) {
			state.hasRedOverlay = false;
		}
		if (physics.charredWhole()) {
			// Tinted, not unlit: armor and held items burn on their own (or not at all, when they don't burn in lava).
			state.hasRedOverlay = false;
		}
		RagdollRenderData.set(state, pose, null);
		RagdollRenderData.setPhysics(state, physics);
	}

	/**
	 * Called once when the body disappears: leaves the death puff where the body last lay, as drawn. A body that was
	 * never drawn puffs where the entity stands. A shulker breaks like a shulker box of its color instead, and one
	 * that is not a physics body already broke when it died.
	 */
	void vanish() {
		if (vanished || profile.kind() == BodyKind.SHULKER && !isPhysical()) {
			return;
		}
		vanished = true;
		if (physics != null && physics.built()) {
			Vec3 torso = physics.torsoPosition(1.0F);
			PhysicsHost.INSTANCE.remove(physics);
			if (entity instanceof net.minecraft.world.entity.monster.Shulker shulker) {
				RagdollTracker.breakShell(shulker, torso);
				return;
			}
			DeathParticles.spawn(entity.level(), torso.subtract(0.0, 0.25, 0.0), new RagdollPose(), profile.height() * 0.4, 0.5, entity.getRandom());
			return;
		}
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

	/** Where this body's head is on the frame being drawn, or null while it has no physics body. */
	net.minecraft.world.phys.Vec3 physicsHead(float partialTicks) {
		return physics != null && physics.built() && !physics.failed ? physics.headPosition(partialTicks) : null;
	}

	boolean isFinished(RagdollConfig config, long currentTick) {
		boolean untilRespawn = keptUntilRespawn(config);
		if (untilRespawn && respawned()) {
			return true;
		}
		if (physics != null && (physics.built() || !physics.failed)) {
			boolean expired = !untilRespawn && age(currentTick, 0.0F) >= config.bodyLifetimeTicks;
			// With Sink in Lava on, a body in lava goes under and is gone sooner.
			if (physics.failed || physics.retired || expired || config.lavaSink && physics.lavaTicks > LAVA_GONE_TICKS) {
				return true;
			}
			// A body whose chunk was unloaded has no ground left under it. Only the chunk is looked at, not the height: the
			// game counts everything above the build limit (and below the bottom) as never loaded, and a mob that died up
			// there was let go on its first tick instead of falling.
			if (!physics.built()) {
				return false;
			}
			net.minecraft.world.phys.Vec3 torso = physics.torsoPosition(1.0F);
			return !entity.level().getChunkSource().hasChunk(Mth.floor(torso.x) >> 4, Mth.floor(torso.z) >> 4);
		}
		if (untilRespawn) {
			return false;
		}
		double end = RagdollAnimator.endTick(profile, config);
		if (ghostFloorDrop > 0.0) {
			double landed = ghostStartAge + Math.sqrt(ghostFloorDrop / GHOST_SETTLE_GRAVITY) + GHOST_LANDING_REST;
			end = Math.max(end, Math.min(config.ghostMaxTicks, landed));
		}
		return age(currentTick, 0.0F) >= end;
	}

	/** With Player Body Until Respawn on, a player's body has no lifetime: it goes when they respawn. */
	boolean keptUntilRespawn(RagdollConfig config) {
		return config.playerBodyUntilRespawn && entity instanceof net.minecraft.world.entity.player.Player;
	}

	/**
	 * Whether the player this body belongs to is back: a dead player stays the same entity until they respawn, when the
	 * game swaps in a new, living one with the same id.
	 */
	private boolean respawned() {
		net.minecraft.world.entity.player.Player now = entity.level().getPlayerByUUID(entity.getUUID());
		return now != null && now != entity && !now.isDeadOrDying();
	}

	/**
	 * The killing blow often knocks a mob off a ledge only after it has died standing on it. For the first few ticks
	 * the body is checked again, and once it is clearly airborne it switches to falling and plays its death on landing.
	 */
	void checkKnockedOff(long currentTick, RagdollConfig config) {
		if (physics != null || profile.followsTrajectory() || profile.kind() == BodyKind.SHULKER || age(currentTick, 0.0F) > KNOCK_OFF_WINDOW_TICKS) {
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
		profile = profile.asFreeFall(-drop, velocity.x, velocity.y, velocity.z, Double.MAX_VALUE)
				.withFallLimit(ProfileFactory.fallLimit(entity.level(), entity, entity.position(), config));
		startTick = currentTick;
		anchor = entity.position();
		startMotion(config);
		followEntity = true;
		stillTicks = 0;
	}

	/**
	 * Where the body was last drawn, padded by its own size and a little travel. Rendering is culled by this box
	 * instead of the entity's hitbox, which a pushed, falling or thrown body can leave far behind.
	 */
	/**
	 * Where the body is drawn: it can lie far from where the mob died (thrown, carried by water), and is kept or culled
	 * by distance from here.
	 */
	Vec3 drawnPosition() {
		AABB bounds = renderBounds();
		return bounds != null ? bounds.getCenter() : entity.position();
	}

	AABB renderBounds() {
		if (physics != null && physics.built()) {
			return physics.bounds();
		}
		if (lastFeet == null) {
			return null;
		}
		double reach = Math.max(profile.width(), profile.height()) + 1.0;
		double x = lastFeet.x + lastPose.offsetX;
		double y = lastFeet.y + lastPose.offsetY;
		double z = lastFeet.z + lastPose.offsetZ;
		return new AABB(x - reach, y - reach, z - reach, x + reach, y + reach, z + reach);
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
	 * body jitter. Pinning both to the last position stops that. A simulated body keeps moving on its own; any
	 * other body still a little above the floor settles onto it.
	 */
	void becomeGhost(long currentTick) {
		entity.setOldPosAndRot();
		entity.yBodyRotO = entity.yBodyRot;
		entity.yHeadRotO = entity.yHeadRot;
		ghostStartTick = currentTick;
		ghostStartAge = age(currentTick, 0.0F);
		if (!profile.followsTrajectory() && profile.cause() != DeathCause.WATER) {
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

	/**
	 * The light where the body is. Water dims the sky light by one level a block and the block the torso is in can
	 * be dark, so the brightest of that block and the one above it is used: a body floating at the surface is lit
	 * like the surface, not like the water under it.
	 */
	private static int lightAt(Level level, double x, double y, double z) {
		BlockPos pos = BlockPos.containing(x, y, z);
		BlockPos above = pos.above();
		int block = Math.max(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.BLOCK, above));
		int sky = Math.max(level.getBrightness(LightLayer.SKY, pos), level.getBrightness(LightLayer.SKY, above));
		return Compat.packLight(block, sky);
	}
}
