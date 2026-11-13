package com.frostfleee.ragdolldeaths.core;

import static com.frostfleee.ragdolldeaths.core.Motion.HALF_PI;

public final class RagdollAnimator {
	public static final int LAVA_SUBMERGE_TICKS = 16;

	static final double BASE_PUSH = 0.35;
	static final double SETTLE_AMPLITUDE = Math.toRadians(5.0);

	private static final double MODEL_DEPTH_FACTOR = 0.85;
	private static final double BODY_WIDTH_MARGIN = 1.15;

	private static final double WINGED_GRAVITY = 0.05;
	private static final double WINGED_DRAG = 0.08;
	private static final double WINGED_SPIN = Math.toRadians(14.0);
	private static final double SPIRIT_GRAVITY = 0.008;
	private static final double SPIRIT_TERMINAL = 0.08;
	private static final double SPIRIT_DRAG = 0.25;
	private static final double GHAST_GRAVITY = 0.06;
	private static final double GHAST_TERMINAL = 1.5;
	private static final double GHAST_DRAG = 0.1;
	private static final int GHAST_SETTLE_TICKS = 10;
	private static final double FREE_FALL_GRAVITY = 0.08;
	private static final double FREE_FALL_TERMINAL = 3.0;
	private static final double FREE_FALL_DRAG = 0.09;
	private static final double FALLING_POSE_BLEND_TICKS = 3.0;

	/** A blasted body slides to a stop this fast once it lands, in 1/ticks. */
	private static final double BLAST_GROUND_FRICTION = 0.5;
	/** How strongly a blasted body's limbs trail while it flies, compared with a plain fall. */
	private static final double BLAST_TRAIL = 0.7;

	/** A body on uneven ground tilts by at most this much, up a rise or down a drop. */
	private static final double MAX_SLOPE_UP = Math.toRadians(40.0);
	private static final double MAX_SLOPE_DOWN = Math.toRadians(30.0);
	private static final double MIN_SLOPE = Math.toRadians(2.0);
	/** Ticks a landed body takes to settle into the slope it landed on. */
	private static final double SLOPE_SETTLE_TICKS = 4.0;

	/** A body in water speeds up as it floats up or sinks, to a steady speed, in blocks per tick. */
	private static final double WATER_ACCELERATION = 0.006;
	private static final double WATER_RISE_SPEED = 0.1;
	private static final double WATER_SINK_SPEED = 0.07;
	/** Ticks a body floats at the surface (or rests on the bottom) after getting there, before it vanishes. */
	private static final double WATER_ARRIVAL_REST = 10.0;

	private RagdollAnimator() {
	}

	/** Age in ticks at which the body vanishes. Anything past the vanilla window is drawn by a client ghost. */
	public static double endTick(DeathProfile profile, RagdollConfig config) {
		if (profile.kind() == BodyKind.SHULKER) {
			return 0.0;
		}
		if (lands(profile)) {
			return simulatedEnd(profile, config);
		}
		if (profile.freeFall()) {
			double land = landTick(profile, config);
			double rest = config.lifetimeTicks + (profile.special() == Special.WARDEN ? config.wardenExtraTicks : 0);
			return land >= config.ghostMaxTicks ? config.ghostMaxTicks : land / config.animationSpeed + rest;
		}
		if (profile.blasted()) {
			// The death plays while it flies, so it needs only about half its usual rest once it has landed.
			double land = landTick(profile, config);
			double end = config.lifetimeTicks + (profile.special() == Special.WARDEN ? config.wardenExtraTicks : 0);
			return land >= config.ghostMaxTicks ? config.ghostMaxTicks
					: Math.min(config.ghostMaxTicks, Math.max(end, land / config.animationSpeed + config.lifetimeTicks * 0.5));
		}
		if (profile.ghostCapable()) {
			double landing = landTick(profile, config) / config.animationSpeed + GHAST_SETTLE_TICKS;
			return Math.clamp(landing, config.lifetimeTicks, config.ghostMaxTicks);
		}
		DeathCause cause = effectiveCause(profile, config);
		if (profile.special() == Special.WARDEN && SpecialDeaths.handles(profile, cause, config)) {
			return config.lifetimeTicks + config.wardenExtraTicks;
		}
		double end = config.lifetimeTicks;
		if (cause == DeathCause.LAVA) {
			end = Math.min(end, LAVA_SUBMERGE_TICKS + 1);
		}
		if (cause == DeathCause.WATER) {
			double arrival = waterArrivalTick(profile, config);
			if (arrival > 0.0) {
				end = Math.max(end, Math.min(config.ghostMaxTicks, arrival / config.animationSpeed + WATER_ARRIVAL_REST));
			}
		}
		return end;
	}

	/**
	 * Tick at which a body dying in water has floated up to the surface or sunk to the bottom; 0 when it stays
	 * where it is. Its death only ends once it gets there, or at the ghost time limit.
	 */
	public static double waterArrivalTick(DeathProfile profile, RagdollConfig config) {
		return switch (waterBehavior(profile, config.waterBehavior)) {
			case FLOAT -> travelTicks(profile.waterRise(), WATER_RISE_SPEED);
			case SINK -> travelTicks(profile.waterSink(), WATER_SINK_SPEED);
			case NEUTRAL -> 0.0;
		};
	}

	private static double travelTicks(double distance, double speed) {
		if (distance <= 0.0) {
			return 0.0;
		}
		double reachSpeed = speed / WATER_ACCELERATION;
		double accelerating = 0.5 * WATER_ACCELERATION * reachSpeed * reachSpeed;
		return distance <= accelerating ? Math.sqrt(2.0 * distance / WATER_ACCELERATION) : reachSpeed + (distance - accelerating) / speed;
	}

	/** Fish, squid and other water mobs always float up; everything else follows the water setting. */
	private static WaterBehavior waterBehavior(DeathProfile profile, WaterBehavior configured) {
		return profile.kind() == BodyKind.AQUATIC ? WaterBehavior.FLOAT : configured;
	}

	/**
	 * A simulated body that falls, flies or is thrown, and so has a landing; a body that died in water is also
	 * simulated, but only drifts with the current.
	 */
	static boolean lands(DeathProfile profile) {
		return profile.simulated() && (profile.kind().airborne() || profile.freeFall() || profile.blasted());
	}

	/**
	 * A simulated body stays until it has landed and rested, however long the fall takes, up to its fall limit
	 * (over the void it vanishes in the air at that limit).
	 */
	private static double simulatedEnd(DeathProfile profile, RagdollConfig config) {
		if (!profile.hasLanded()) {
			return profile.fallLimit();
		}
		double land = profile.landedAt() / config.animationSpeed;
		if (profile.landedIn() == DeathCause.LAVA) {
			return land + LAVA_SUBMERGE_TICKS + 1;
		}
		double rest = config.lifetimeTicks + (profile.special() == Special.WARDEN ? config.wardenExtraTicks : 0);
		if (profile.blasted()) {
			// The death plays while it flies, so it needs only about half its usual rest once it has landed.
			return Math.max(rest, land + config.lifetimeTicks * 0.5);
		}
		if (profile.kind().airborne()) {
			return Math.max(config.lifetimeTicks, land + (profile.kind() == BodyKind.GHAST ? GHAST_SETTLE_TICKS : config.lifetimeTicks * 0.5));
		}
		return land + rest;
	}

	public static boolean needsGhost(DeathProfile profile, RagdollConfig config, double ageTicks) {
		return endTick(profile, config) > ageTicks + 1.0;
	}

	/** First tick at which an airborne body touches its floor, or the ghost limit if it never does. */
	public static double landTick(DeathProfile profile, RagdollConfig config) {
		if (lands(profile)) {
			return profile.hasLanded() ? profile.landedAt() : Double.POSITIVE_INFINITY;
		}
		if (!profile.followsTrajectory()) {
			return 0.0;
		}
		// A blasted body starts on the floor it is thrown from, so it can only land from the next tick on.
		for (int tick = profile.blasted() ? 1 : 0; tick <= config.ghostMaxTicks; tick++) {
			if (verticalTravel(profile, tick) <= profile.floorDrop()) {
				return tick;
			}
		}
		return config.ghostMaxTicks;
	}

	/** {@code age} is real ticks since death; the choreography runs on that time scaled by the animation speed. */
	public static void compute(DeathProfile profile, RagdollConfig config, double age, RagdollPose pose, JointPose joints) {
		age = Math.max(0.0, age);
		double t = age * config.animationSpeed;
		pose.reset();
		pose.forwardX = profile.forwardX();
		pose.forwardZ = profile.forwardZ();
		joints.reset(t);

		// A shulker vanishes the moment it dies, like a shulker box being broken; the client spawns the box's break particles.
		if (profile.kind() == BodyKind.SHULKER) {
			pose.hidden = true;
			return;
		}
		if (profile.freeFall()) {
			freeFall(profile, config, t, pose, joints);
		} else if (profile.blasted()) {
			blast(profile, config, t, pose, joints);
		} else {
			animateBody(profile, config, effectiveCause(profile, config), t, pose, joints);
		}
		fitToGround(profile, config, t, pose);
		if (!config.legKicks) {
			for (JointRole leg : new JointRole[] {JointRole.LEG, JointRole.FRONT_LEG, JointRole.HIND_LEG, JointRole.MID_LEG}) {
				joints.twitch(leg, 0.0, 0.0, 0.0, 0.0);
			}
		}

		pose.hidden = age >= endTick(profile, config);
	}

	/**
	 * A mob that died in mid-air keeps falling under gravity with its limbs trailing, and its death animation (the
	 * fall-impact version) only starts once the body actually reaches the floor.
	 */
	private static void freeFall(DeathProfile profile, RagdollConfig config, double t, RagdollPose pose, JointPose joints) {
		double land = landTick(profile, config);
		if (t < land) {
			fallingPose(joints, Motion.smooth(t, 0.0, 6.0));
		} else {
			animateBody(profile, config, landingCause(profile, config), t - land, pose, joints);
			fallingPose(joints, 1.0 - Motion.smooth(t - land, 0.0, FALLING_POSE_BLEND_TICKS));
		}
		if (profile.simulated()) {
			return;
		}

		double[] path = trajectory(profile, config, t);
		pose.offsetX += path[0];
		pose.offsetY += path[1];
		pose.offsetZ += path[2];
	}

	/** A body landing in water rolls over and floats, in lava it sinks; on the ground it takes the fall impact. */
	private static DeathCause landingCause(DeathProfile profile, RagdollConfig config) {
		if (profile.simulated() && profile.landedIn() == DeathCause.WATER) {
			return DeathCause.WATER;
		}
		if (profile.simulated() && profile.landedIn() == DeathCause.LAVA) {
			return config.lavaSink ? DeathCause.LAVA : DeathCause.GROUND;
		}
		return config.fallImpact ? DeathCause.FALL : DeathCause.GROUND;
	}

	/**
	 * A body launched by an explosion flies in an arc, playing its normal death (away from the blast) on the way
	 * with its limbs trailing, then lands with a bounce and slides to a stop. Walls stop it via {@code maxPush}.
	 */
	private static void blast(DeathProfile profile, RagdollConfig config, double t, RagdollPose pose, JointPose joints) {
		double land = landTick(profile, config);
		animateBody(profile, config, DeathCause.GROUND, t, pose, joints);
		fallingPose(joints, BLAST_TRAIL * Motion.smooth(t, 0.0, 3.0) * (1.0 - Motion.smooth(t, land, land + FALLING_POSE_BLEND_TICKS)));
		if (profile.simulated()) {
			// The flight, the bounce and the slide come from the body's own motion through the world.
			return;
		}

		double[] path = trajectory(profile, config, t);
		double totalX = pose.offsetX + path[0];
		double totalZ = pose.offsetZ + path[2];
		double total = Math.hypot(totalX, totalZ);
		if (total > profile.maxPush() && total > 0.0) {
			totalX *= profile.maxPush() / total;
			totalZ *= profile.maxPush() / total;
		}
		pose.offsetX = totalX;
		pose.offsetZ = totalZ;
		pose.offsetY += path[1] + impactBounceHeight(profile, config, t, land);
	}

	/**
	 * Where a falling or blasted body has travelled to at choreography time {@code t}, relative to where it died:
	 * x, y and z in blocks. It never goes below {@code floorDrop} or further sideways than {@code maxPush}.
	 * Bodies that don't follow such a path stay at zero.
	 */
	public static double[] trajectory(DeathProfile profile, RagdollConfig config, double t) {
		if (!profile.freeFall() && !profile.blasted()) {
			return new double[3];
		}
		double carried;
		double y;
		if (profile.blasted()) {
			double land = landTick(profile, config);
			double flight = Math.min(t, land);
			double slide = t > land ? Math.exp(-FREE_FALL_DRAG * land) * (1.0 - Math.exp(-BLAST_GROUND_FRICTION * (t - land))) / BLAST_GROUND_FRICTION : 0.0;
			carried = carry(flight, FREE_FALL_DRAG) + slide;
			y = t < land ? Math.max(profile.floorDrop(), verticalTravel(profile, t)) : profile.floorDrop();
		} else {
			carried = carry(t, FREE_FALL_DRAG);
			y = Math.max(profile.floorDrop(), verticalTravel(profile, t));
		}
		double travelX = profile.velocityX() * carried;
		double travelZ = profile.velocityZ() * carried;
		double travel = Math.hypot(travelX, travelZ);
		if (travel > profile.maxPush() && travel > 0.0) {
			travelX *= profile.maxPush() / travel;
			travelZ *= profile.maxPush() / travel;
		}
		return new double[] {travelX, y, travelZ};
	}

	/**
	 * Tilts a body that has toppled over so it rests on the ground around it, measured where it settled: up a
	 * stair, a slab or a hillside it falls against, or down over a step. The tilt grows as the body tips over,
	 * so an upright body is unaffected, and a body that has just landed eases into it.
	 */
	private static void fitToGround(DeathProfile profile, RagdollConfig config, double t, RagdollPose pose) {
		Ground ground = profile.ground();
		if (ground.flat() || profile.kind().airborne() || profile.kind() == BodyKind.SHULKER) {
			return;
		}
		DeathCause cause = effectiveCause(profile, config);
		boolean landsLater = profile.freeFall() || profile.blasted();
		if (!landsLater && (cause == DeathCause.WATER || cause == DeathCause.LAVA)) {
			return;
		}
		if (profile.simulated() && profile.landedIn() != DeathCause.GROUND) {
			return;
		}
		double settle = 1.0;
		if (profile.followsTrajectory()) {
			double land = landTick(profile, config);
			if (t < land) {
				return;
			}
			settle = Motion.smooth(t, land, land + SLOPE_SETTLE_TICKS);
		}

		double reach = 0.6 * profile.height();
		double[] top = pose.toWorld(0.0, reach, 0.0);
		double dirX = top[0] - pose.offsetX;
		double dirZ = top[2] - pose.offsetZ;
		double lean = Math.hypot(dirX, dirZ);
		if (lean < 0.05 * reach) {
			return;
		}
		double angle = Math.clamp(Math.atan2(ground.riseToward(dirX, dirZ), ground.radius()), -MAX_SLOPE_DOWN, MAX_SLOPE_UP);
		if (Math.abs(angle) < MIN_SLOPE) {
			return;
		}
		// Rotating about (-z, 0, x) lifts the side of the body that lies toward (x, z).
		pose.slopeAxisX = -dirZ / lean;
		pose.slopeAxisZ = dirX / lean;
		pose.slopeAngle = angle * Math.min(1.0, lean / reach) * settle;
	}

	/** Limbs trailing upward in the rushing air; {@code weight} blends it in while falling and out after landing. */
	private static void fallingPose(JointPose joints, double weight) {
		if (weight <= 0.0) {
			return;
		}
		joints.offset(JointRole.ARM, JointPose.Side.RIGHT, -0.9 * weight, 0.0, 0.6 * weight);
		joints.offset(JointRole.ARM, JointPose.Side.LEFT, -0.9 * weight, 0.0, 0.6 * weight);
		joints.offset(JointRole.LEG, JointPose.Side.RIGHT, 0.0, 0.0, 0.2 * weight);
		joints.offset(JointRole.LEG, JointPose.Side.LEFT, 0.0, 0.0, 0.2 * weight);
		joints.offsetAll(JointRole.FRONT_LEG, -0.35 * weight, 0.0, 0.0);
		joints.offsetAll(JointRole.HIND_LEG, 0.35 * weight, 0.0, 0.0);
		joints.offset(JointRole.HEAD, JointPose.Side.CENTER, -0.25 * weight, 0.0, 0.0);
		joints.offset(JointRole.TAIL, JointPose.Side.CENTER, -0.4 * weight, 0.0, 0.0);
	}

	private static void animateBody(DeathProfile profile, RagdollConfig config, DeathCause cause, double t, RagdollPose pose, JointPose joints) {
		if (SpecialDeaths.handles(profile, cause, config)) {
			SpecialDeaths.animate(profile, config, cause, t, pose, joints);
		} else if (profile.kind() == BodyKind.STANDARD && profile.rig() == Rig.BIPED) {
			BipedDeath.animate(profile, config, cause, t, pose, joints);
		} else if (profile.kind() == BodyKind.STANDARD && profile.rig() == Rig.QUADRUPED) {
			QuadrupedDeath.animate(profile, config, cause, t, pose, joints);
		} else if (profile.kind() == BodyKind.STANDARD && profile.rig() == Rig.ARTHROPOD) {
			ArthropodDeath.animate(profile, config, cause, t, pose, joints);
		} else {
			world(profile, config, cause, t, pose);
			CreatureJoints.animate(profile, cause, t, landTick(profile, config), joints);
		}
	}

	static DeathCause effectiveCause(DeathProfile profile, RagdollConfig config) {
		DeathCause cause = profile.cause();
		if (cause == DeathCause.LAVA && (!config.lavaSink || profile.kind() == BodyKind.SHULKER || profile.kind().airborne())) {
			return DeathCause.GROUND;
		}
		if (cause == DeathCause.FALL && !config.fallImpact) {
			return DeathCause.GROUND;
		}
		return cause;
	}

	private static void world(DeathProfile profile, RagdollConfig config, DeathCause cause, double age, RagdollPose pose) {
		if (cause == DeathCause.LAVA) {
			lava(profile, config, age, pose, 0.8);
			return;
		}
		switch (profile.kind()) {
			case STANDARD -> topple(profile, config, cause, age, pose);
			case AQUATIC -> aquatic(profile, config, cause, age, pose);
			case CUBE -> cube(profile, config, cause, age, pose);
			case SHULKER -> {
			}
			case WINGED -> winged(profile, config, age, pose);
			case SPIRIT -> spirit(profile, age, pose);
			case GHAST -> ghast(profile, config, age, pose);
		}
	}

	private static void topple(DeathProfile profile, RagdollConfig config, DeathCause cause, double age, RagdollPose pose) {
		if (cause == DeathCause.FALL) {
			fallSlam(profile, config, age, pose);
			return;
		}

		boolean inWater = cause == DeathCause.WATER;
		double collapse = collapseTicks(profile, config) * (inWater ? 1.6 : 1.0);
		double angle = HALF_PI * Motion.fall(age, 0.0, collapse) - settle(profile, config, age - collapse);

		if (profile.flipsOnBack()) {
			pose.pivotY = profile.height() / 2.0;
			pose.rollAngle = fallSide(profile) * angle * 2.0;
		} else {
			pose.tipToward(profile.fallDirX(), profile.fallDirZ(), angle);
		}

		applyPush(profile, config, age, collapse, inWater ? 0.5 : 1.0, pose);
		pose.offsetY += groundLift(profile, pose);
		if (inWater) {
			pose.offsetY += waterDrift(profile, config.waterBehavior, age);
		}
	}

	private static void fallSlam(DeathProfile profile, RagdollConfig config, double age, RagdollPose pose) {
		double collapse = 4.0;
		double sign = ontoBack(profile) ? -1.0 : 1.0;
		pose.tipToward(sign * profile.forwardX(), sign * profile.forwardZ(), HALF_PI * Motion.fall(age, 0.0, collapse) - impactBounceAngle(config, age, collapse));
		pose.offsetY = groundLift(profile, pose) + impactBounceHeight(profile, config, age, collapse);
	}

	static double impactBounceAngle(RagdollConfig config, double age, double impact) {
		return Motion.bounce(age, impact, Math.toRadians(3.0) * config.bounceStrength, 0.6, 0.35);
	}

	static double impactBounceHeight(DeathProfile profile, RagdollConfig config, double age, double impact) {
		double height = 0.06 * Math.min(profile.height(), 2.5) * (1.0 - 0.5 * profile.heaviness()) * config.bounceStrength;
		return Motion.bounce(age, impact, height, 0.6, 0.35);
	}

	static void lava(DeathProfile profile, RagdollConfig config, double age, RagdollPose pose, double tipFraction) {
		double collapse = 7.0;
		if (profile.kind() == BodyKind.CUBE) {
			squash(Motion.progress(age, 0.0, collapse), pose);
		} else {
			pose.tipToward(profile.fallDirX(), profile.fallDirZ(), HALF_PI * Motion.fall(age, 0.0, collapse) * tipFraction);
		}
		applyPush(profile, config, age, collapse, 0.3, pose);
		pose.offsetY = -profile.height() * 1.1 * Motion.easeIn(Motion.progress(age, 0.0, LAVA_SUBMERGE_TICKS));
	}

	private static void aquatic(DeathProfile profile, RagdollConfig config, DeathCause cause, double age, RagdollPose pose) {
		if (cause == DeathCause.WATER) {
			pose.pivotY = profile.height() / 2.0;
			pose.rollAngle = Math.PI * Motion.easeInOut(Motion.progress(age, 0.0, 16.0));
			applyPush(profile, config, age, 16.0, 0.3, pose);
			pose.offsetY += waterDrift(profile, config.waterBehavior, age);
			return;
		}

		if (profile.alreadySideways()) {
			pose.rollAngle = Motion.ring(age, 0.0, Math.toRadians(10.0), 0.9, 0.15);
			return;
		}

		double collapse = 8.0;
		pose.rollAngle = HALF_PI * Motion.fall(age, 0.0, collapse) - settle(profile, config, age - collapse);
		applyPush(profile, config, age, collapse, 0.6, pose);
		pose.offsetY += groundLift(profile, pose);
	}

	/**
	 * Slimes and sulfur cubes: the hit stretches the body tall as it leans away, then it slumps into a wide puddle
	 * and wobbles like jelly before settling. Magma cubes are stiffer and fold down in three stacked steps.
	 * A fall skips the stretch and splats at once.
	 */
	private static void cube(DeathProfile profile, RagdollConfig config, DeathCause cause, double age, RagdollPose pose) {
		boolean splat = cause == DeathCause.FALL;
		double pace = 1.0 + 0.4 * profile.heaviness();
		boolean magma = profile.special() == Special.MAGMA_CUBE;
		double stretch = splat || magma ? 0.0 : Motion.bell(age, 0.0, 4.0 * pace);
		double lean = splat ? 0.0 : 0.22 * Motion.bell(age, 0.0, 6.0 * pace);
		pose.tipToward(profile.fallDirX(), profile.fallDirZ(), lean);

		double flat;
		double settled;
		if (magma) {
			double step = splat ? 1.0 : 2.5 * pace;
			flat = 0.0;
			for (int i = 0; i < 3; i++) {
				double start = (splat ? 0.0 : 2.0 * pace) + i * step;
				flat += Motion.easeIn(Motion.progress(age, start, start + step * 0.6)) / 3.0;
				flat -= Motion.bounce(age, start + step * 0.6, 0.02 * config.bounceStrength, 1.6, 0.6);
			}
			settled = (splat ? 3.0 : 2.0 * pace + 3.0 * step);
			flat = Math.min(1.0, flat);
		} else {
			double start = splat ? 0.0 : 2.0 * pace;
			settled = start + (splat ? 3.0 : 7.0 * pace);
			flat = Motion.easeIn(Motion.progress(age, start, settled));
		}

		pose.squashY = Motion.lerp(1.0 + 0.15 * stretch, 0.28, flat);
		pose.squashXZ = Motion.lerp(1.0 - 0.08 * stretch, 1.5, flat);
		if (!magma) {
			double jelly = Motion.ring(age, settled, 0.09 * config.bounceStrength, 1.1, 0.25);
			pose.squashY += jelly;
			pose.squashXZ -= jelly * 0.6;
		}
		applyPush(profile, config, age, settled, 0.5, pose);
		if (cause == DeathCause.WATER) {
			pose.offsetY += waterDrift(profile, config.waterBehavior, age);
		}
	}

	private static void squash(double progress, RagdollPose pose) {
		double eased = Motion.easeOut(progress);
		pose.squashY = 1.0 - 0.55 * eased;
		pose.squashXZ = 1.0 + 0.3 * eased;
	}

	private static void winged(DeathProfile profile, RagdollConfig config, double age, RagdollPose pose) {
		airborneTravel(profile, age, WINGED_DRAG, pose);
		double land = landTick(profile, config);
		double horizontalSpeed = Math.hypot(profile.velocityX(), profile.velocityZ());
		double dirX = horizontalSpeed > 0.02 ? profile.velocityX() : profile.fallDirX();
		double dirZ = horizontalSpeed > 0.02 ? profile.velocityZ() : profile.fallDirZ();

		pose.pivotY = profile.height() / 2.0;
		pose.tipToward(dirX, dirZ, HALF_PI * Motion.easeOut(Motion.progress(age, 0.0, 10.0)));
		pose.spinAngle = WINGED_SPIN * Math.min(age, land) * ((profile.seed() & 4L) == 0L ? 1.0 : -1.0);
		pose.offsetY += groundLift(profile, pose);
	}

	private static void spirit(DeathProfile profile, double age, RagdollPose pose) {
		airborneTravel(profile, age, SPIRIT_DRAG, pose);
		pose.pivotY = profile.height() / 2.0;
		pose.tipToward(profile.fallDirX(), profile.fallDirZ(), Math.toRadians(70.0) * Motion.easeInOut(Motion.progress(age, 0.0, 18.0)));
		pose.rollAngle = Math.toRadians(6.0) * Math.sin(0.25 * age);
		pose.offsetY += groundLift(profile, pose);
	}

	private static void ghast(DeathProfile profile, RagdollConfig config, double age, RagdollPose pose) {
		airborneTravel(profile, age, GHAST_DRAG, pose);
		double land = landTick(profile, config);
		pose.pivotY = profile.height() / 2.0;
		pose.tipToward(profile.fallDirX(), profile.fallDirZ(), HALF_PI * Motion.easeInOut(Motion.progress(age, 0.0, 30.0)));
		if (age > land) {
			double impact = 0.075 * config.bounceStrength * Math.exp(-0.3 * (age - land));
			pose.squashY = 1.0 - impact;
			pose.squashXZ = 1.0 + impact * 0.5;
		}
		pose.offsetY += groundLift(profile, pose);
	}

	private static void airborneTravel(DeathProfile profile, double age, double drag, RagdollPose pose) {
		if (profile.simulated()) {
			return;
		}
		double carry = carry(age, drag);
		double travelX = profile.velocityX() * carry;
		double travelZ = profile.velocityZ() * carry;
		double travel = Math.hypot(travelX, travelZ);
		if (travel > profile.maxPush() && travel > 0.0) {
			double scale = profile.maxPush() / travel;
			travelX *= scale;
			travelZ *= scale;
		}
		pose.offsetX = travelX;
		pose.offsetZ = travelZ;
		pose.offsetY = Math.max(profile.floorDrop(), verticalTravel(profile, age));
	}

	static double verticalTravel(DeathProfile profile, double age) {
		if (profile.freeFall() || profile.blasted()) {
			return profile.velocityY() * carry(age, FREE_FALL_DRAG) - terminalFall(age, FREE_FALL_GRAVITY, FREE_FALL_TERMINAL);
		}
		return switch (profile.kind()) {
			case WINGED -> profile.velocityY() * carry(age, WINGED_DRAG) - 0.5 * WINGED_GRAVITY * age * age;
			case SPIRIT -> -terminalFall(age, SPIRIT_GRAVITY, SPIRIT_TERMINAL);
			case GHAST -> -terminalFall(age, GHAST_GRAVITY, GHAST_TERMINAL);
			default -> 0.0;
		};
	}

	private static double carry(double age, double drag) {
		return (1.0 - Math.exp(-drag * age)) / drag;
	}

	private static double terminalFall(double age, double gravity, double terminal) {
		double reachTerminal = terminal / gravity;
		if (age <= reachTerminal) {
			return 0.5 * gravity * age * age;
		}
		return 0.5 * gravity * reachTerminal * reachTerminal + terminal * (age - reachTerminal);
	}

	static void applyPush(DeathProfile profile, RagdollConfig config, double age, double collapse, double factor, RagdollPose pose) {
		if (profile.hit() == HitDirection.NONE) {
			return;
		}
		double push = BASE_PUSH * config.hitReactionStrength * factor * (1.0 - 0.5 * profile.heaviness()) * (profile.baby() ? 1.2 : 1.0);
		push = Math.min(push, profile.maxPush()) * Motion.easeOut(Motion.progress(age, 0.0, collapse));
		pose.offsetX += profile.fallDirX() * push;
		pose.offsetZ += profile.fallDirZ() * push;
	}

	static double waterDrift(DeathProfile profile, WaterBehavior behavior, double age) {
		return switch (waterBehavior(profile, behavior)) {
			case FLOAT -> Math.min(terminalFall(age, WATER_ACCELERATION, WATER_RISE_SPEED), profile.waterRise());
			case SINK -> -Math.min(terminalFall(age, WATER_ACCELERATION, WATER_SINK_SPEED), profile.waterSink());
			case NEUTRAL -> 0.0;
		};
	}

	static double collapseTicks(DeathProfile profile, RagdollConfig config) {
		return Math.min(11.0 * pace(profile), config.lifetimeTicks * 0.6);
	}

	/** Time multiplier: heavy bodies move slower, babies a little faster. */
	static double pace(DeathProfile profile) {
		return (1.0 + 0.45 * profile.heaviness()) * (profile.baby() ? 0.85 : 1.0);
	}

	static double settle(DeathProfile profile, RagdollConfig config, double sinceImpact) {
		if (sinceImpact <= 0.0) {
			return 0.0;
		}
		double amplitude = SETTLE_AMPLITUDE * (1.0 - 0.6 * profile.heaviness()) * config.bounceStrength * (profile.baby() ? 1.3 : 1.0);
		return Motion.bounce(sinceImpact, 0.0, amplitude, 0.9, 0.3);
	}

	/** +1 when the body falls toward its own left, -1 toward its right. */
	static double fallSide(DeathProfile profile) {
		double left = profile.fallDirX() * profile.forwardZ() - profile.fallDirZ() * profile.forwardX();
		return left >= 0.0 ? 1.0 : -1.0;
	}

	static boolean ontoBack(DeathProfile profile) {
		return (profile.seed() & 2L) == 0L;
	}

	static double asymmetry(DeathProfile profile) {
		return (profile.seed() & 8L) == 0L ? 1.0 : -1.0;
	}

	/** How far the rotated body box would dip below the feet plane, so it can be lifted back to rest on the ground. */
	static double groundLift(DeathProfile profile, RagdollPose pose) {
		double angle = Math.min(Math.PI, Math.max(Math.abs(pose.tipAngle), Math.abs(pose.rollAngle)));
		double halfWidth = profile.width() / 2.0 * MODEL_DEPTH_FACTOR;
		boolean rollingOnFeet = pose.pivotY == 0.0 && Math.abs(pose.rollAngle) > Math.abs(pose.tipAngle);
		if (rollingOnFeet && !Double.isNaN(profile.bodyHalfWidth())) {
			halfWidth = Math.min(halfWidth, profile.bodyHalfWidth() * BODY_WIDTH_MARGIN);
		}
		double pivot = pose.pivotY;
		double cos = Math.cos(angle);
		double lowest = pivot - halfWidth * Math.sin(angle) + Math.min(-pivot * cos, (profile.height() - pivot) * cos);
		return Math.max(0.0, -lowest);
	}
}
