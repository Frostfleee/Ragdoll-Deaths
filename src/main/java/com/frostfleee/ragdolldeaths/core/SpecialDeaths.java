package com.frostfleee.ragdolldeaths.core;

import static com.frostfleee.ragdolldeaths.core.Motion.HALF_PI;
import static com.frostfleee.ragdolldeaths.core.Motion.bell;
import static com.frostfleee.ragdolldeaths.core.Motion.bounce;
import static com.frostfleee.ragdolldeaths.core.Motion.decay;
import static com.frostfleee.ragdolldeaths.core.Motion.easeInOut;
import static com.frostfleee.ragdolldeaths.core.Motion.easeOut;
import static com.frostfleee.ragdolldeaths.core.Motion.fall;
import static com.frostfleee.ragdolldeaths.core.Motion.lerp;
import static com.frostfleee.ragdolldeaths.core.Motion.progress;
import static com.frostfleee.ragdolldeaths.core.Motion.ring;
import static com.frostfleee.ragdolldeaths.core.Motion.smooth;

import com.frostfleee.ragdolldeaths.core.JointPose.Side;

/** Mob-specific deaths that replace the body-plan choreography. */
public final class SpecialDeaths {
	public static final double SNOW_MELT_START = 2.0;
	public static final double SNOW_MELT_END = 24.0;
	public static final double SKELETON_BREAK = 9.0;

	private static final double GROUND_PX = 24.0;
	private static final double SNOW_HEAD_HEIGHT_PX = 20.0;
	private static final double CREEPER_LEG_BUCKLE = 0.4;
	private static final double IDLE_SPIN_DOWN = 4.0;
	private static final double MIN_SCALE = 0.01;
	/** The blaze head's pivot sits at its center, 20 px above the ground; the breeze head's at its bottom, 20 px up. */
	private static final double BLAZE_HEAD_DROP = 20.0;
	private static final double BREEZE_HEAD_DROP = 20.0;

	private SpecialDeaths() {
	}

	static boolean handles(DeathProfile p, DeathCause cause, RagdollConfig config) {
		return switch (p.special()) {
			case NONE, MAGMA_CUBE -> false;
			case SNOW_GOLEM, CREEPER, BLAZE, BREEZE, GUARDIAN, NAUTILUS, ZOMBIE_NAUTILUS -> cause != DeathCause.LAVA;
			case WARDEN, ROLL_ONTO_BACK, SKELETON, RAVAGER -> cause == DeathCause.GROUND || cause == DeathCause.FALL;
		};
	}

	static void animate(DeathProfile p, RagdollConfig config, DeathCause cause, double t, RagdollPose pose, JointPose j) {
		switch (p.special()) {
			case SNOW_GOLEM -> snowGolem(p, config, cause, t, pose, j);
			case WARDEN -> warden(p, config, t, pose, j);
			case ROLL_ONTO_BACK -> rollOntoBack(p, config, t, pose, j);
			case SKELETON -> skeleton(p, t, j);
			case CREEPER -> creeper(p, config, cause, t, pose, j);
			case RAVAGER -> ravager(p, config, cause, t, pose, j);
			case BLAZE -> blaze(p, config, cause, t, pose, j);
			case BREEZE -> breeze(p, config, cause, t, pose, j);
			case GUARDIAN -> guardian(p, config, cause, t, pose, j);
			case NAUTILUS, ZOMBIE_NAUTILUS -> nautilus(p, config, cause, t, pose, j);
			case NONE, MAGMA_CUBE -> {
			}
		}
	}

	/**
	 * Age to draw the model's own idle motion at, for mobs whose circling parts run off the entity's age (a blaze's
	 * rods, a breeze's whirlwind): that motion slows to a stop instead of carrying on. NaN for every other mob.
	 */
	public static double idleAge(DeathProfile p, double t) {
		return switch (p.special()) {
			case BLAZE, BREEZE, NAUTILUS, ZOMBIE_NAUTILUS -> IDLE_SPIN_DOWN * (1.0 - Math.exp(-Math.max(0.0, t) / IDLE_SPIN_DOWN));
			default -> Double.NaN;
		};
	}

	/**
	 * How far a dying guardian's spikes stick out (vanilla's 0 withdrawn to 1 fully out): they shoot out at once,
	 * hold through the death throes, then slowly sink back in. NaN for every other mob.
	 */
	public static double guardianSpikes(DeathProfile p, double t) {
		if (p.special() != Special.GUARDIAN) {
			return Double.NaN;
		}
		double pace = RagdollAnimator.pace(p);
		return 1.0 - easeInOut(progress(t, 4.0 * pace, 16.0 * pace));
	}

	/**
	 * In water the guardian convulses, its spikes shooting out and its tail lashing, then slowly rolls belly-up as
	 * the spikes sink back in and the tail goes limp, and floats away. On land it flops twice, rolls onto its side
	 * and lies still. The elder guardian does the same, slower and heavier.
	 */
	private static void guardian(DeathProfile p, RagdollConfig config, DeathCause cause, double t, RagdollPose pose, JointPose j) {
		double k = RagdollAnimator.pace(p);
		// +1 rolls it toward its own left; a side hit always rolls it away from the blow.
		double side = p.hit() == HitDirection.LEFT || p.hit() == HitDirection.RIGHT ? RagdollAnimator.fallSide(p) : RagdollAnimator.asymmetry(p);
		boolean inWater = cause == DeathCause.WATER;
		pose.pivotY = p.height() / 2.0;
		j.relax(JointRole.HEAD, smooth(t, 0.0, 3.0));

		double convulse = ring(t, 0.0, 0.3, 1.6 / k, 0.25 / k);
		if (inWater) {
			double bellyUp = Math.min(22.0 * k, config.lifetimeTicks * config.animationSpeed - 4.0);
			pose.tipToward(p.fallDirX(), p.fallDirZ(), 0.2 * bell(t, 0.0, 5.0 * k));
			pose.rollAngle = -side * (convulse + Math.PI * easeInOut(progress(t, Math.min(5.0 * k, bellyUp - 8.0), bellyUp)));
			RagdollAnimator.applyPush(p, config, t, 8.0 * k, 0.4, pose);
			pose.offsetY += RagdollAnimator.groundLift(p, pose) + RagdollAnimator.waterDrift(p, config.waterBehavior, t);
		} else {
			boolean slam = cause == DeathCause.FALL;
			double rollStart = slam ? 1.0 : 10.0 * k;
			double rolled = rollStart + 5.0 * k;
			double flops = slam ? 0.0 : 0.3 * bell(t, 0.0, 5.0 * k) + 0.15 * bell(t, 5.5 * k, 9.5 * k);
			double rock = slam ? 0.0 : 0.3 * Math.sin(Math.PI * progress(t, 0.0, 9.5 * k) * 2.0) * (1.0 - smooth(t, 8.0 * k, rollStart));
			pose.rollAngle = -side * (rock + HALF_PI * fall(t, rollStart, rolled) - RagdollAnimator.settle(p, config, t - rolled));
			RagdollAnimator.applyPush(p, config, t, rolled, 0.5, pose);
			pose.offsetY += RagdollAnimator.groundLift(p, pose) + flops * Math.min(p.height(), 1.0) * config.bounceStrength
					+ (slam ? RagdollAnimator.impactBounceHeight(p, config, t, 1.0) : 0.0);
		}

		j.twitch(JointRole.SPIKE, 0.12 * decay(t, 0.0, 0.3 / k), 0.0, 0.0, 2.5);
	}

	/**
	 * Vanilla's tail swing phase for a dying guardian: the tail lashes hard, as far as vanilla ever swings it, then
	 * slows and goes still and straight. The tail's links pivot far from where they are drawn, so only vanilla's own
	 * side-to-side swing keeps them joined. NaN for every other mob.
	 */
	public static double guardianTail(DeathProfile p, double t) {
		if (p.special() != Special.GUARDIAN) {
			return Double.NaN;
		}
		double k = RagdollAnimator.pace(p);
		double strength = (p.cause() == DeathCause.WATER ? 0.8 : 1.0) * decay(t, 0.0, 0.12 / k) * (1.0 - smooth(t, 12.0 * k, 18.0 * k));
		return HALF_PI * strength * Math.sin(1.5 / k * t);
	}

	/**
	 * A nautilus pulls its body back into its shell; a zombie nautilus's rotten body instead slumps half out and
	 * hangs. In water the shell then slowly turns its opening up and floats away, turning a little. On land it
	 * rocks once and tips over onto its side.
	 */
	private static void nautilus(DeathProfile p, RagdollConfig config, DeathCause cause, double t, RagdollPose pose, JointPose j) {
		boolean zombie = p.special() == Special.ZOMBIE_NAUTILUS;
		double reach = p.baby() ? 0.5 : 1.0;
		j.relax(JointRole.BODY, smooth(t, 0.0, 2.0));
		if (zombie) {
			double slump = backOutProgress(t, 2.0, 10.0);
			j.move(JointRole.BODY, Side.CENTER, 0.0, 2.0 * reach * slump, 3.0 * reach * slump);
			j.offset(JointRole.BODY, Side.CENTER, 0.4 * slump + ring(t, 10.0, 0.08 * config.bounceStrength, 1.1, 0.35), 0.0, 0.0);
		} else {
			double withdraw = easeInOut(progress(t, 1.0, 7.0));
			j.move(JointRole.BODY, Side.CENTER, 0.0, 0.0, -5.0 * reach * withdraw);
			j.scale(JointRole.BODY, 1.0 - 0.08 * withdraw, 1.0 - 0.08 * withdraw, 1.0);
		}

		pose.pivotY = p.height() / 2.0;
		double side = RagdollAnimator.asymmetry(p);
		if (cause == DeathCause.WATER) {
			double turn = easeInOut(progress(t, 4.0, 24.0));
			pose.tipToward(p.forwardX(), p.forwardZ(), -0.15 * bell(t, 0.0, 4.0) + 1.1 * turn);
			pose.spinAngle = side * 0.6 * turn;
			pose.rollAngle = ring(t, 0.0, 0.12, 0.9, 0.2);
			RagdollAnimator.applyPush(p, config, t, 8.0, 0.4, pose);
			pose.offsetY += RagdollAnimator.groundLift(p, pose) + RagdollAnimator.waterDrift(p, config.waterBehavior, t);
		} else {
			boolean slam = cause == DeathCause.FALL;
			double tipStart = slam ? 1.0 : 8.0;
			double tipped = tipStart + 5.0;
			double rock = slam ? 0.0 : 0.25 * Math.sin(Math.PI * progress(t, 1.0, 8.0) * 2.0);
			pose.rollAngle = side * (rock + HALF_PI * fall(t, tipStart, tipped) - RagdollAnimator.settle(p, config, t - tipped));
			RagdollAnimator.applyPush(p, config, t, tipped, 0.5, pose);
			pose.offsetY += RagdollAnimator.groundLift(p, pose) + (slam ? RagdollAnimator.impactBounceHeight(p, config, t, 1.0) : 0.0);
		}
	}

	/** 0 to 1 between {@code from} and {@code to}, overshooting a little before settling, like a heavy limp part. */
	private static double backOutProgress(double t, double from, double to) {
		return Motion.backOut(progress(t, from, to), 1.4);
	}

	/**
	 * The fire goes out: the rods spin down to a stop and drop one by one, tipping over flat and rolling out around
	 * the body, while the head falls straight down, bounces and rolls over onto one face, away from the blow.
	 */
	private static void blaze(DeathProfile p, RagdollConfig config, DeathCause cause, double t, RagdollPose pose, JointPose j) {
		boolean fast = cause == DeathCause.FALL;
		j.scatter(JointRole.ROD, fast ? 0.0 : 3.0, fast ? 3.0 : 5.0, fast ? 2.0 : 7.0, 3.5, 1.5 * config.bounceStrength, p.seed());
		double headStart = fast ? 0.0 : 4.0;
		looseHead(p, config, t, j, headStart, headStart + (fast ? 3.0 : 6.0), BLAZE_HEAD_DROP, 4.0, 4.0, true);
		if (cause == DeathCause.WATER) {
			pose.offsetY += RagdollAnimator.waterDrift(p, config.waterBehavior, t);
		}
	}

	/**
	 * The whirlwind gives out: it flares once, then unwinds and shrinks down into the ground. With nothing left to
	 * hold them up, the head drops and bounces, landing upright and still facing ahead, and the three rods clatter
	 * down flat around it.
	 */
	private static void breeze(DeathProfile p, RagdollConfig config, DeathCause cause, double t, RagdollPose pose, JointPose j) {
		boolean fast = cause == DeathCause.FALL;
		double flare = fast ? 0.0 : 0.12 * bell(t, 0.0, 4.0);
		double fade = Motion.easeIn(progress(t, fast ? 0.0 : 3.0, fast ? 6.0 : 14.0));
		double width = Math.max(MIN_SCALE, (1.0 + flare) * (1.0 - fade));
		double height = Math.max(MIN_SCALE, (1.0 + 0.5 * flare) * (1.0 - 0.85 * fade));
		j.scale(JointRole.WIND, width, height, width);
		j.move(JointRole.WIND, Side.CENTER, 0.0, GROUND_PX * (1.0 - height), 0.0);
		j.offset(JointRole.WIND, Side.CENTER, 0.0, 2.5 * fade, 0.0);

		j.relax(JointRole.BODY, smooth(t, 0.0, 3.0));
		j.scatter(JointRole.ROD, fast ? 0.0 : 5.0, fast ? 3.0 : 5.0, fast ? 1.5 : 3.0, 3.0, 1.2 * config.bounceStrength, p.seed());
		double headStart = fast ? 0.0 : 6.0;
		looseHead(p, config, t, j, headStart, headStart + (fast ? 3.0 : 6.0), BREEZE_HEAD_DROP, 4.0, 0.0, false);
		if (cause == DeathCause.WATER) {
			pose.offsetY += RagdollAnimator.waterDrift(p, config.waterBehavior, t);
		}
	}

	/**
	 * A head with nothing left to hold it up: it jerks back, drops {@code drop} pixels to the ground between
	 * {@code start} and {@code land} and bounces. When {@code rolls}, it then rolls over its bottom edge onto its
	 * side, away from the blow. {@code half} is half the head's width and {@code pivotHeight} how far its pivot sits
	 * above its bottom face.
	 */
	private static void looseHead(DeathProfile p, RagdollConfig config, double t, JointPose j, double start, double land,
			double drop, double half, double pivotHeight, boolean rolls) {
		double[] dir = modelFallDirection(p);
		double jolt = start > 0.0 ? -0.45 * bell(t, 0.0, start + 1.0) : 0.0;
		double hop = bounce(t, land, 1.6 * config.bounceStrength, 1.3, 0.5);
		double roll = rolls ? HALF_PI * easeInOut(progress(t, land + 1.0, land + 6.0)) : 0.0;
		double shift = half * (1.0 - Math.cos(roll)) + pivotHeight * Math.sin(roll);
		double rise = pivotHeight - half * Math.sin(roll) - pivotHeight * Math.cos(roll);

		j.relax(JointRole.HEAD, smooth(t, 0.0, 2.0));
		j.set(JointRole.HEAD, Side.CENTER, jolt - dir[1] * roll, 0.0, dir[0] * roll, smooth(t, 0.0, 2.0));
		j.move(JointRole.HEAD, Side.CENTER, dir[0] * shift, drop * fall(t, start, land) + rise - hop, dir[1] * shift);
	}

	/** The snow body melts into a flat patch while the pumpkin rides down on top of it, then tips off backward. */
	private static void snowGolem(DeathProfile p, RagdollConfig config, DeathCause cause, double t, RagdollPose pose, JointPose j) {
		double melt = easeInOut(progress(t, SNOW_MELT_START, SNOW_MELT_END));
		double height = 1.0 - 0.82 * melt;
		j.melt(height, 1.0 + 0.4 * melt);
		pose.tipToward(p.fallDirX(), p.fallDirZ(), 0.06 * Math.sin(0.8 * t) * decay(t, 0.0, 0.15));
		if (cause == DeathCause.WATER) {
			pose.offsetY += RagdollAnimator.waterDrift(p, config.waterBehavior, t);
		}

		double topple = smooth(t, 7.0, 15.0);
		double restingPivot = GROUND_PX - 4.0;
		double meltedPivot = GROUND_PX - SNOW_HEAD_HEIGHT_PX * height;
		double headBounce = bounce(t, 15.0, 0.12, 1.0, 0.35) * config.bounceStrength;
		j.set(JointRole.HEAD, Side.CENTER, -1.35 * topple + headBounce, 0.4 * topple * RagdollAnimator.asymmetry(p), Double.NaN, 1.0);
		j.move(JointRole.HEAD, Side.CENTER, 0.0, (restingPivot - meltedPivot) * topple, 3.0 * topple);

		double droop = smooth(t, 3.0, 14.0);
		j.set(JointRole.ARM, Side.RIGHT, Double.NaN, Double.NaN, 0.0, droop);
		j.set(JointRole.ARM, Side.LEFT, Double.NaN, Double.NaN, 0.0, droop);
	}

	/**
	 * Staggers, sinks to its knees and stays there a moment with its head hanging, then slowly tips forward and
	 * lies face down with its arms stretched out past its head. The phases stretch to fill the Warden's longer lifetime.
	 */
	private static void warden(DeathProfile p, RagdollConfig config, double t, RagdollPose pose, JointPose j) {
		double duration = (config.lifetimeTicks + config.wardenExtraTicks) * config.animationSpeed;
		double kneel = 0.26 * duration;
		double tipStart = 0.46 * duration;
		double impact = 0.72 * duration;

		double fold = 1.4 * easeInOut(progress(t, 4.0, kneel)) * (1.0 - smooth(t, tipStart, impact));
		double lowered = p.legLength() * (1.0 - Math.cos(fold));
		double stagger = 0.12 * bell(t, 0.0, 5.0);
		double breathing = 0.03 * Math.sin(0.4 * t) * (smooth(t, kneel, kneel + 2.0) - smooth(t, tipStart, tipStart + 2.0));
		double angle = -stagger + 0.2 * smooth(t, 6.0, kneel) + breathing + (HALF_PI - 0.2) * fall(t, tipStart, impact)
				- RagdollAnimator.settle(p, config, t - impact) * 0.5;
		pose.tipToward(p.forwardX(), p.forwardZ(), angle);
		pose.offsetY += RagdollAnimator.groundLift(p, pose) - lowered;

		double down = smooth(t, impact, impact + 4.0);
		double headX = -0.5 * bell(t, 0.0, 5.0) + 0.85 * smooth(t, 5.0, kneel) - 0.6 * down;
		j.set(JointRole.HEAD, Side.CENTER, headX, 0.0, Double.NaN, smooth(t, 0.0, 2.0));
		for (Side side : new Side[] {Side.RIGHT, Side.LEFT}) {
			double flare = 0.6 * bell(t, 0.0, 6.0);
			double reach = side == Side.RIGHT ? -2.8 : -2.65;
			double hang = lerp(0.2 * smooth(t, 5.0, kneel), reach * 0.6, smooth(t, tipStart, impact));
			double x = lerp(hang, reach, down);
			double z = flare + 0.15 + (side == Side.RIGHT ? 0.2 : 0.14) * down;
			j.set(JointRole.ARM, side, x, 0.0, z, smooth(t, 0.0, 2.0));
			j.set(JointRole.LEG, side, fold + (side == Side.RIGHT ? 0.06 : -0.06) * (1.0 - down), 0.0, 0.08, smooth(t, 0.0, 2.0));
		}
	}

	/** Armadillos and frogs: flips onto its back with its head and tail tucked and the limbs curled up. */
	private static void rollOntoBack(DeathProfile p, RagdollConfig config, double t, RagdollPose pose, JointPose j) {
		double flipped = 10.0;
		double side = RagdollAnimator.fallSide(p);
		pose.pivotY = p.height() / 2.0;
		pose.rollAngle = -side * (Math.PI * easeInOut(progress(t, 2.0, flipped)) - RagdollAnimator.settle(p, config, t - flipped));
		RagdollAnimator.applyPush(p, config, t, flipped, 0.5, pose);
		pose.offsetY += RagdollAnimator.groundLift(p, pose);

		double curl = smooth(t, 4.0, flipped + 2.0);
		j.both(JointRole.HIND_LEG, -1.1 * curl, Double.NaN, 0.25 * curl, smooth(t, 0.0, 2.0));
		j.both(JointRole.FRONT_LEG, 1.1 * curl, Double.NaN, 0.25 * curl, smooth(t, 0.0, 2.0));
		j.twitch(JointRole.HIND_LEG, 0.25 * smooth(t, flipped, flipped + 1.0) * decay(t, flipped, 0.2), 0.0, 0.0, 1.5);
		j.offset(JointRole.HEAD, Side.CENTER, 0.9 * curl, 0.0, 0.0);
		j.offset(JointRole.TAIL, Side.CENTER, 0.8 * curl, 0.0, 0.0);
		j.offset(JointRole.EAR, Side.RIGHT, 0.0, 0.0, 0.4 * curl);
		j.offset(JointRole.EAR, Side.LEFT, 0.0, 0.0, 0.4 * curl);

		j.relaxAll(smooth(t, 0.0, 3.0), JointRole.ARM, JointRole.LEG);
		j.both(JointRole.ARM, 1.0 * curl, Double.NaN, 0.3 * curl, smooth(t, 0.0, 2.0));
		j.both(JointRole.LEG, -1.0 * curl, Double.NaN, 0.3 * curl, smooth(t, 0.0, 2.0));
		j.twitch(JointRole.LEG, 0.25 * smooth(t, flipped, flipped + 1.0) * decay(t, flipped, 0.2), 0.0, 0.0, 1.5);
	}

	/**
	 * The legs give way and the skeleton drops straight down, then it comes apart: the skull pops off and rolls
	 * away, the arms and legs clatter flat, and the ribcage lands in the middle of the pile. Everything here is in
	 * model pixels with +Y down, the feet at Y 24 and the model's front toward -Z.
	 */
	private static void skeleton(DeathProfile p, double t, JointPose j) {
		double[] dir = modelFallDirection(p);
		double fx = dir[0];
		double fz = dir[1];
		double turn = RagdollAnimator.asymmetry(p);

		double collapse = fall(t, 3.0, 9.0);
		double drop = 12.0 * collapse;

		double torso = fall(t, SKELETON_BREAK, 14.0);
		double torsoBounce = bounce(t, 14.0, 1.0, 1.0, 0.4);
		j.set(JointRole.BODY, Side.CENTER, -fz * HALF_PI * torso, Double.NaN, fx * HALF_PI * torso, 1.0);
		j.move(JointRole.BODY, Side.CENTER, fx * 9.0 * torso, drop + 10.0 * torso - torsoBounce, fz * 9.0 * torso);

		double skullFlight = progress(t, SKELETON_BREAK, 16.0);
		double skullArc = -5.0 * Math.sin(Math.PI * skullFlight);
		double skullDrop = lerp(drop, GROUND_PX - 4.0, fall(t, SKELETON_BREAK, 16.0)) + skullArc - bounce(t, 16.0, 1.2, 1.1, 0.4);
		double roll = 2.4 * easeInOut(skullFlight) * turn;
		j.set(JointRole.HEAD, Side.CENTER, -0.4 * bell(t, 0.0, 4.0) + 0.6 * skullFlight, roll, turn * HALF_PI * smooth(t, SKELETON_BREAK + 2.0, 16.0), 1.0);
		j.move(JointRole.HEAD, Side.CENTER, (fx * 15.0 + turn * 3.0) * skullFlight, skullDrop, (fz * 15.0 - 2.0) * skullFlight);

		for (Side side : new Side[] {Side.RIGHT, Side.LEFT}) {
			double outward = side == Side.RIGHT ? -1.0 : 1.0;
			double start = SKELETON_BREAK + (side == Side.RIGHT ? 0.0 : 1.5);
			double armFall = fall(t, start, start + 5.0);
			double armBounce = bounce(t, start + 5.0, 0.8, 1.2, 0.45);
			double flare = 0.7 * bell(t, 0.0, 5.0);
			j.set(JointRole.ARM, side, (side == Side.RIGHT ? -1.4 : 1.3) * armFall, 0.0, flare + 1.25 * armFall, 1.0);
			j.move(JointRole.ARM, side, (outward * 5.0 + fx * 6.0) * armFall, drop + (GROUND_PX - 3.0 - drop) * armFall - armBounce, (fz * 6.0 + outward * 2.0) * armFall);

			double legStart = 6.0 + (side == Side.RIGHT ? 0.0 : 1.0);
			double legFall = fall(t, legStart, legStart + 5.0);
			double kick = side == Side.RIGHT ? -1.0 : 1.0;
			j.set(JointRole.LEG, side, kick * (0.6 * collapse + 0.9 * legFall), 0.0, 0.5 * collapse + 0.3 * legFall, 1.0);
			j.move(JointRole.LEG, side, outward * 2.0 * legFall, 10.5 * legFall - bounce(t, legStart + 5.0, 0.6, 1.2, 0.45), -kick * 4.0 * legFall);
		}
	}

	/**
	 * The four stubby legs buckle out a little and the tall body sags onto them, then it tips over away from the
	 * blow (forward or backward when there is no blow) and lies flat with its head rolled aside.
	 */
	private static void creeper(DeathProfile p, RagdollConfig config, DeathCause cause, double t, RagdollPose pose, JointPose j) {
		boolean fast = cause == DeathCause.FALL;
		double splayStart = fast ? 0.0 : 2.0;
		double splayEnd = fast ? 3.0 : 8.0;
		double tipStart = fast ? 2.0 : 7.0;
		double impact = tipStart + (fast ? 5.0 : 9.0);
		j.relaxAll(smooth(t, 0.0, 2.0), JointRole.FRONT_LEG, JointRole.HIND_LEG, JointRole.HEAD, JointRole.BODY);

		double splay = easeOut(progress(t, splayStart, splayEnd));
		double legAngle = CREEPER_LEG_BUCKLE * splay;
		double tipping = fall(t, tipStart, impact);
		double lowered = p.legLength() * (1.0 - Math.cos(legAngle)) * (1.0 - tipping);

		double dirX = p.fallDirX();
		double dirZ = p.fallDirZ();
		if (p.hit() == HitDirection.NONE) {
			double sign = RagdollAnimator.ontoBack(p) ? -1.0 : 1.0;
			dirX = sign * p.forwardX();
			dirZ = sign * p.forwardZ();
		}
		pose.tipToward(dirX, dirZ, HALF_PI * tipping - RagdollAnimator.settle(p, config, t - impact));
		RagdollAnimator.applyPush(p, config, t, impact, 0.6, pose);
		pose.offsetY += RagdollAnimator.groundLift(p, pose) - lowered;
		if (cause == DeathCause.WATER) {
			pose.offsetY += RagdollAnimator.waterDrift(p, config.waterBehavior, t);
		}

		for (Side side : new Side[] {Side.RIGHT, Side.LEFT}) {
			j.offset(JointRole.FRONT_LEG, side, -legAngle, 0.0, 0.1 * splay);
			j.offset(JointRole.HIND_LEG, side, legAngle, 0.0, 0.1 * splay);
		}

		double snap = switch (p.hit()) {
			case FRONT -> -0.45;
			case BACK -> 0.4;
			default -> -0.2;
		};
		double landed = smooth(t, impact, impact + 4.0);
		double headX = snap * bell(t, 0.0, 5.0) + ring(t, impact, 0.1 * config.bounceStrength, 1.0, 0.35);
		j.offset(JointRole.HEAD, Side.CENTER, headX, 0.45 * landed * RagdollAnimator.asymmetry(p), 0.0);
	}

	/**
	 * A heavy collapse that stays upright: it reels from the blow, its legs splay out and it sinks onto its belly
	 * with a thud, then its jaw falls open. The head keeps its height, since the sinking body already brings it to
	 * the ground. It never rolls over. A fall flattens it onto its belly at once.
	 */
	private static void ravager(DeathProfile p, RagdollConfig config, DeathCause cause, double t, RagdollPose pose, JointPose j) {
		boolean splat = cause == DeathCause.FALL;
		j.relaxAll(smooth(t, 0.0, 3.0), JointRole.BODY, JointRole.FRONT_LEG, JointRole.HIND_LEG, JointRole.NECK, JointRole.HEAD, JointRole.JAW);

		double sinkStart = splat ? 0.0 : 4.0;
		double sinkEnd = splat ? 3.0 : 13.0;
		double slam = splat ? 2.0 : 12.0;
		double slamEnd = slam + 6.0;

		double sink = splat ? Motion.easeIn(progress(t, sinkStart, sinkEnd)) : easeInOut(progress(t, sinkStart, sinkEnd));
		boolean sideHit = p.hit() == HitDirection.LEFT || p.hit() == HitDirection.RIGHT;
		double side = sideHit ? RagdollAnimator.fallSide(p) : RagdollAnimator.asymmetry(p);

		double react = bell(t, 0.0, 5.0);
		double sway = sideHit ? 0.1 * react : p.hit() == HitDirection.NONE ? 0.05 * Math.sin(0.6 * t) * (1.0 - smooth(t, 4.0, 10.0)) : 0.0;
		pose.rollAngle = side * sway;
		RagdollAnimator.applyPush(p, config, t, 5.0, 0.3, pose);
		double thud = RagdollAnimator.impactBounceHeight(p, config, t, sinkEnd) * 0.5;
		pose.offsetY += -0.7 * p.legLength() * sink + thud;

		double hindBuckle = p.hit() == HitDirection.BACK ? -0.5 * react : 0.0;
		for (Side legSide : new Side[] {Side.RIGHT, Side.LEFT}) {
			j.offset(JointRole.FRONT_LEG, legSide, -0.5 * sink, 0.0, 1.1 * sink);
			j.offset(JointRole.HIND_LEG, legSide, 0.5 * sink + hindBuckle, 0.0, 1.1 * sink);
		}

		double toss = switch (p.hit()) {
			case FRONT -> -1.0;
			default -> 0.0;
		};
		double neckTurn = sideHit ? -side * 0.4 * react : 0.0;
		j.offset(JointRole.NECK, Side.CENTER, 0.35 * toss * react, neckTurn, 0.0);
		j.offset(JointRole.HEAD, Side.CENTER, 0.3 * toss * react, 0.0, 0.0);
		j.offset(JointRole.JAW, Side.CENTER, (p.hit() == HitDirection.FRONT ? 0.4 * react : 0.0) + 0.55 * smooth(t, slam, slamEnd + 3.0), 0.0, 0.0);
	}


	/** Fall direction in model space: away from the hit, with +X on the body's left and +Z behind it. */
	private static double[] modelFallDirection(DeathProfile p) {
		return switch (p.hit()) {
			case FRONT -> new double[] {0.0, 1.0};
			case BACK -> new double[] {0.0, -1.0};
			case LEFT -> new double[] {-1.0, 0.0};
			case RIGHT -> new double[] {1.0, 0.0};
			case NONE -> RagdollAnimator.ontoBack(p) ? new double[] {0.0, 1.0} : new double[] {0.0, -1.0};
		};
	}
}
