package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.JointRole;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.physics.Body;
import com.frostfleee.ragdolldeaths.physics.Joint;
import java.util.Random;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Turns the killing blow into what it does to the body: a push on the part that was hit, at the point it was hit,
 * with the rest of the body dragged along behind.
 *
 * <p>Everything comes from what the client already has: the damage event (its type, the attacker, the projectile,
 * where it came from) and the knockback the server gave the dying mob, which tells how hard the blow was (a
 * Knockback sword and a sprinting hit both show up there).
 *
 * <p>The server's knockback is not used as a speed directly. The game slows a knocked-back mob so heavily (9% a
 * tick in the air, almost half on the ground) that the same launch speed under real physics would send a body
 * several times as far. It is read as a strength instead, and strength grows the throw gently.
 */
final class DeathBlow {
	/** The server's sideways push for a plain hit, in blocks per tick. */
	private static final double PLAIN_KNOCKBACK = 0.4;
	/** Speed given to the struck part's neighbours by a plain melee hit, blocks per second. */
	private static final double MELEE_PUSH = 4.2;
	private static final double MELEE_LIFT = 2.6;
	/** How a stronger server knockback (a Knockback sword, a sprinting hit, a Punch bow) grows the throw. */
	private static final double KNOCKBACK_GROWTH = 0.65;
	private static final double PROJECTILE_PUSH = 5.0;
	/** How fast a four-legged body starts to roll onto its side, and a spider to flip over, radians per second. */
	private static final double QUADRUPED_ROLL = 3.5;
	private static final double ARTHROPOD_FLIP = 8.0;
	/** The little hop that lets a spider's body clear its own legs as it flips, blocks per second. */
	private static final double ARTHROPOD_POP = 3.5;
	/** How fast a body that died with nothing pushing it starts to tip over, radians per second. */
	private static final double SLUMP_TURN = 1.4;
	/** How fast a blow at the very top of the body sets it toppling, radians per second. */
	private static final double MELEE_TOPPLE = 5.0;
	private static final double PROJECTILE_TOPPLE = 1.6;
	/** An explosion's push right at the blast and at the edge of its reach, blocks per second. */
	private static final double BLAST_PUSH = 34.0;
	private static final double BLAST_EDGE_PUSH = 4.0;
	private static final double BLAST_REACH = 8.0;
	/** How high above the body's feet a blast whose position is unknown is placed. */
	private static final double BLAST_FOOT_HEIGHT = 0.1;
	private static final double BLAST_GUESS_DISTANCE = 1.5;
	/** A part with a block between it and the blast keeps this much of the push. */
	private static final double BLAST_SHELTERED = 0.12;
	private static final double MACE_PUSH = 6.0;
	private static final double MACE_MAX = 12.0;

	private DeathBlow() {
	}

	/**
	 * @param roles what each body is (head, body, arm, leg), in the same order as the bodies
	 * @return a few words for the debug log
	 */
	static String apply(LivingEntity entity, Body[] bodies, Joint[] joints, JointRole[] roles, RagdollConfig config) {
		String blow = blow(entity, bodies, joints, roles, config);
		String flop = flop(entity, bodies, roles, new Random(entity.getUUID().getMostSignificantBits() ^ entity.getId()));
		return flop.isEmpty() ? blow : blow + "; " + flop;
	}

	/**
	 * A body standing on four or more stiff legs (a pig, a fox, a spider) or on its tentacles (a squid on land) is
	 * balanced, and nothing in the physics tips it over: it would die standing, or sitting on its haunches. A dying
	 * animal's legs give way, so it is rolled onto its side, a little to one side or the other. A spider goes
	 * further: it flips onto its back, where its legs curl up.
	 */
	private static String flop(LivingEntity entity, Body[] bodies, JointRole[] roles, Random random) {
		int legs = 0, middleLegs = 0, tentacles = 0, arms = 0;
		for (JointRole role : roles) {
			switch (role) {
				case FRONT_LEG, HIND_LEG -> legs++;
				case MID_LEG -> {
					legs++;
					middleLegs++;
				}
				case TENTACLE -> tentacles++;
				case ARM -> arms++;
				default -> {
				}
			}
		}
		double rate, pop;
		String what;
		if (legs >= 6 || middleLegs > 0) {
			rate = ARTHROPOD_FLIP;
			pop = ARTHROPOD_POP;
			what = "flipping onto its back";
		} else if (legs >= 4 && arms == 0) {
			rate = QUADRUPED_ROLL;
			pop = 0.0;
			what = "rolling onto its side";
		} else if (tentacles >= 4) {
			rate = QUADRUPED_ROLL;
			pop = 0.0;
			what = "toppling off its tentacles";
		} else {
			return "";
		}
		double yaw = Math.toRadians(entity.yBodyRot);
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		double turn = (random.nextBoolean() ? 1.0 : -1.0) * rate * (0.85 + 0.3 * random.nextDouble());
		// Turning about the body's own front-to-back line, through its feet.
		double wx = fx * turn, wz = fz * turn;
		double px = entity.getX(), py = entity.getY(), pz = entity.getZ();
		for (Body body : bodies) {
			double rx = body.x - px, ry = body.y - py, rz = body.z - pz;
			body.vx += -wz * ry;
			body.vy += wz * rx - wx * rz + pop;
			body.vz += wx * ry;
			body.wx += wx;
			body.wz += wz;
		}
		return what;
	}

	/**
	 * How hard the blow just applied was, as a multiple of a plain hit's knockback: zero for a death with no blow,
	 * about one for a plain hit, more for Knockback, a sprint hit or Punch, and three for an explosion or a mace.
	 * Read right after {@link #apply}; everything here runs on the client thread.
	 */
	static double lastStrength;

	private static String blow(LivingEntity entity, Body[] bodies, Joint[] joints, JointRole[] roles, RagdollConfig config) {
		lastStrength = 0.0;
		DamageSource source = entity.getLastDamageSource();
		double scale = config.hitReactionStrength;
		Random random = new Random(entity.getUUID().getLeastSignificantBits() ^ entity.level().getGameTime());
		if (source == null || scale <= 0.0) {
			return slump(entity, bodies, random);
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			lastStrength = 3.0;
			return blast(entity, source, bodies, scale * config.explosionStrength, random);
		}
		if ("minecraft:mace_smash".equals(source.typeHolder().getRegisteredName())) {
			lastStrength = 3.0;
			return smash(source, bodies, roles, scale);
		}
		Entity direct = source.getDirectEntity();
		Entity attacker = source.getEntity();
		Vec3 from = source.getSourcePosition();
		if (from == null) {
			// Fire, poison, starvation, suffocation, the void, a command: nothing pushed it.
			return slump(entity, bodies, random);
		}
		Vec3 centre = entity.position().add(0.0, entity.getBbHeight() * 0.6, 0.0);
		Vec3 away = new Vec3(centre.x - from.x, 0.0, centre.z - from.z);
		away = away.lengthSqr() < 1.0E-6 ? Vec3.ZERO : away.normalize();
		// How hard the server pushed the mob away, as a multiple of a plain hit.
		Vec3 motion = entity.getDeltaMovement();
		double pushed = motion.x * away.x + motion.z * away.z;
		double strength = pushed > 0.1 ? Math.clamp(pushed / PLAIN_KNOCKBACK, 0.6, 6.0) : 1.0;
		double force = Math.pow(strength, KNOCKBACK_GROWTH) * scale;
		lastStrength = strength;

		if (source.is(DamageTypeTags.IS_PROJECTILE) && direct != null) {
			Vec3 flight = direct.getDeltaMovement();
			double speed = flight.length();
			Vec3 along = speed > 0.05 ? flight.scale(1.0 / speed) : away;
			double power = (speed > 0.05 ? Math.clamp(speed / 3.0, 0.4, 1.5) : 1.0) * scale;
			Vec3 origin = direct.position().subtract(along.scale(4.0));
			double[] point = new double[3];
			int hit = struck(bodies, origin, along, point);
			// The whole body is knocked back the way the arrow was flying, as far as the server's knockback says:
			// a plain arrow a little less than a sword hit, a Punch bow much further.
			Vec3 flat = new Vec3(along.x, 0.0, along.z);
			flat = flat.lengthSqr() < 1.0E-6 ? away : flat.normalize();
			double carried = motion.x * flat.x + motion.z * flat.z;
			lastStrength = carried > 0.1 ? Math.clamp(carried / PLAIN_KNOCKBACK, 0.6, 6.0) : 1.0;
			double punch = Math.pow(lastStrength, KNOCKBACK_GROWTH) * scale;
			for (Body body : bodies) {
				body.vx += flat.x * MELEE_PUSH * 0.75 * punch;
				body.vy += MELEE_LIFT * 0.7 + 0.8 * (punch - 1.0);
				body.vz += flat.z * MELEE_PUSH * 0.75 * punch;
			}
			Vec3 push = along.scale(PROJECTILE_PUSH * power).add(0.0, 1.0 * power, 0.0);
			spread(bodies, joints, hit, point, push, 2.2, 0.7, 0.3, 0.8, random);
			topple(bodies, entity, new Vec3(along.x, 0.0, along.z), point[1], PROJECTILE_TOPPLE * power);
			return String.format(java.util.Locale.ROOT, "projectile into part %d (%s), strength %.2f (server push %.2f)", hit, roles[hit], punch, carried);
		}

		// A melee hit. A player strikes where they are looking; a mob strikes at the chest.
		Vec3 origin = attacker != null ? attacker.getEyePosition() : from;
		Vec3 aim = attacker instanceof Player ? attacker.getLookAngle() : entity.position().add(0.0, entity.getBbHeight() * 0.72, 0.0).subtract(origin).normalize();
		double[] point = new double[3];
		int hit = struck(bodies, origin, aim, point);
		Vec3 push = away.scale(MELEE_PUSH * force).add(0.0, Math.max(1.0, MELEE_LIFT + 1.0 * (force - 1.0)), 0.0);
		spread(bodies, joints, hit, point, push, 1.5, 1.15, 0.95, 0.4, random);
		topple(bodies, entity, away, point[1], MELEE_TOPPLE * force);
		return String.format(java.util.Locale.ROOT, "melee into part %d (%s), strength %.2f (server push %.2f)", hit, roles[hit], strength, pushed);
	}

	/**
	 * Gives the push to the whole body, strongest on the struck part and weaker the further along the joints a part
	 * is from it, so the struck part leads and the rest is dragged after it. The struck part is also pushed at the
	 * point of the hit, which turns it.
	 */
	private static void spread(Body[] bodies, Joint[] joints, int hit, double[] point, Vec3 push, double struckShare, double nextShare, double restShare,
			double turnShare, Random random) {
		int[] steps = stepsFrom(bodies, joints, hit);
		for (int i = 0; i < bodies.length; i++) {
			Body body = bodies[i];
			double share = (steps[i] == 0 ? struckShare : steps[i] == 1 ? nextShare : restShare) * (0.88 + 0.24 * random.nextDouble());
			// Only the sideways push is shared out unevenly. An uneven lift would fold the legs up under the body.
			body.vx += push.x * share;
			body.vy += push.y;
			body.vz += push.z * share;
			double spin = 0.6 * push.length() / 4.0;
			body.wx += (random.nextDouble() - 0.5) * spin;
			body.wy += (random.nextDouble() - 0.5) * spin;
			body.wz += (random.nextDouble() - 0.5) * spin;
		}
		Body struck = bodies[hit];
		double m = struck.mass * turnShare;
		struck.applyImpulse(push.x * m, push.y * m, push.z * m, point[0], point[1], point[2]);
	}

	/**
	 * A body that died with nothing pushing it does not stay standing: a mob killed balanced on its feet, or wedged
	 * upright in a hole, would otherwise stand there for good, since nothing in the physics ever tips a perfectly
	 * balanced body over. It is given the start of a fall instead: tipping over from its feet, gently, in a direction
	 * of its own, as its knees give way. A mob that was already moving carries that along as well.
	 */
	private static String slump(LivingEntity entity, Body[] bodies, Random random) {
		double angle = random.nextDouble() * 2.0 * Math.PI;
		double ax = Math.cos(angle), az = Math.sin(angle);
		double turn = SLUMP_TURN * (0.8 + 0.4 * random.nextDouble());
		double feet = entity.getY();
		for (Body body : bodies) {
			double above = body.y - feet;
			// Turning about the level axis square to the fall, at the feet: the higher a part, the faster it goes.
			body.vx += ax * turn * above;
			body.vz += az * turn * above;
			body.wx += az * turn;
			body.wz -= ax * turn;
		}
		return String.format(java.util.Locale.ROOT, "no blow, slumping at %.1f rad/s", turn);
	}

	/**
	 * A blow above the body's middle tips its top over the way the blow went and a blow below sweeps its legs out
	 * from under it: the whole body is set turning about its middle, faster the further from the middle it was hit.
	 *
	 * @param away the direction of the blow along the ground
	 * @param rate the turn, in radians per second, for a blow at the very top or bottom
	 */
	private static void topple(Body[] bodies, LivingEntity entity, Vec3 away, double hitY, double rate) {
		double flat = Math.hypot(away.x, away.z);
		if (flat < 1.0E-6) {
			return;
		}
		double ax = away.x / flat, az = away.z / flat;
		double height = entity.getBbHeight();
		double middle = entity.getY() + 0.55 * height;
		double turn = rate * Math.clamp((hitY - middle) / (0.5 * height), -1.0, 1.0);
		for (Body body : bodies) {
			double above = body.y - middle;
			body.vx += ax * turn * above;
			body.vz += az * turn * above;
			// The turn itself is about the level axis square to the blow: up × away.
			body.wx += az * turn;
			body.wz -= ax * turn;
		}
	}

	/** How many joints each body is from the given one. */
	private static int[] stepsFrom(Body[] bodies, Joint[] joints, int start) {
		int[] steps = new int[bodies.length];
		java.util.Arrays.fill(steps, Integer.MAX_VALUE);
		steps[start] = 0;
		for (int pass = 0; pass < bodies.length; pass++) {
			boolean changed = false;
			for (Joint joint : joints) {
				int a = indexOf(bodies, joint.a), b = indexOf(bodies, joint.b);
				if (steps[a] != Integer.MAX_VALUE && steps[a] + 1 < steps[b]) {
					steps[b] = steps[a] + 1;
					changed = true;
				}
				if (steps[b] != Integer.MAX_VALUE && steps[b] + 1 < steps[a]) {
					steps[a] = steps[b] + 1;
					changed = true;
				}
			}
			if (!changed) {
				break;
			}
		}
		return steps;
	}

	private static int indexOf(Body[] bodies, Body body) {
		for (int i = 0; i < bodies.length; i++) {
			if (bodies[i] == body) {
				return i;
			}
		}
		return 0;
	}

	/**
	 * The first body a ray passes through, with the point where it enters; when it passes through none, the body
	 * whose centre it passes closest to, with the nearest point of the ray.
	 */
	private static int struck(Body[] bodies, Vec3 origin, Vec3 direction, double[] point) {
		int best = 0;
		double bestT = Double.MAX_VALUE;
		Quaterniond rotation = new Quaterniond();
		Vector3d o = new Vector3d(), d = new Vector3d();
		for (int i = 0; i < bodies.length; i++) {
			Body b = bodies[i];
			rotation.set(-b.qx, -b.qy, -b.qz, b.qw);
			rotation.transform(o.set(origin.x - b.x, origin.y - b.y, origin.z - b.z));
			rotation.transform(d.set(direction.x, direction.y, direction.z));
			double near = slab(o.x, d.x, b.hx, o.y, d.y, b.hy, o.z, d.z, b.hz);
			if (near >= 0.0 && near < bestT) {
				bestT = near;
				best = i;
			}
		}
		if (bestT != Double.MAX_VALUE) {
			point[0] = origin.x + direction.x * bestT;
			point[1] = origin.y + direction.y * bestT;
			point[2] = origin.z + direction.z * bestT;
			return best;
		}
		double nearest = Double.MAX_VALUE;
		for (int i = 0; i < bodies.length; i++) {
			Body b = bodies[i];
			double t = Math.max(0.0, (b.x - origin.x) * direction.x + (b.y - origin.y) * direction.y + (b.z - origin.z) * direction.z);
			double px = origin.x + direction.x * t, py = origin.y + direction.y * t, pz = origin.z + direction.z * t;
			double gap = (px - b.x) * (px - b.x) + (py - b.y) * (py - b.y) + (pz - b.z) * (pz - b.z);
			if (gap < nearest) {
				nearest = gap;
				best = i;
				// The hit lands on the body, on the side the ray passes.
				point[0] = b.x + (px - b.x) * 0.5;
				point[1] = b.y + (py - b.y) * 0.5;
				point[2] = b.z + (pz - b.z) * 0.5;
			}
		}
		return best;
	}

	/** Where a ray enters a box centred on the origin, as a distance along the ray, or -1 when it misses. */
	private static double slab(double ox, double dx, double hx, double oy, double dy, double hy, double oz, double dz, double hz) {
		double enter = 0.0, exit = Double.MAX_VALUE;
		double[] o = {ox, oy, oz}, d = {dx, dy, dz}, h = {hx, hy, hz};
		for (int axis = 0; axis < 3; axis++) {
			if (Math.abs(d[axis]) < 1.0E-9) {
				if (Math.abs(o[axis]) > h[axis]) {
					return -1.0;
				}
				continue;
			}
			double t1 = (-h[axis] - o[axis]) / d[axis], t2 = (h[axis] - o[axis]) / d[axis];
			enter = Math.max(enter, Math.min(t1, t2));
			exit = Math.min(exit, Math.max(t1, t2));
		}
		return enter <= exit ? enter : -1.0;
	}

	/**
	 * An explosion pushes every part away from its centre, harder the closer the part is, and hardly at all when a
	 * block shelters it. Parts at different distances get different pushes, which is what spins the body.
	 */
	private static String blast(LivingEntity entity, DamageSource source, Body[] bodies, double scale, Random random) {
		double[] found = blastCentre(entity, source);
		Vec3 centre = new Vec3(found[0], found[1], found[2]);
		double reach = found[3];
		String how = found[4] == 0.0 ? "damage" : found[4] == 1.0 ? "explosion packet" : "guessed from push";
		Level level = entity.level();
		double strongest = 0.0;
		int sheltered = 0;
		for (Body body : bodies) {
			Vec3 at = new Vec3(body.x, body.y, body.z);
			Vec3 out = at.subtract(centre);
			double distance = out.length();
			double closeness = Math.clamp(1.0 - distance / reach, 0.0, 1.0);
			HitResult wall = level.clip(new ClipContext(centre, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
			boolean blocked = wall.getType() != HitResult.Type.MISS && wall.getLocation().distanceTo(centre) < distance - 0.3;
			sheltered += blocked ? 1 : 0;
			double speed = (BLAST_EDGE_PUSH + (BLAST_PUSH - BLAST_EDGE_PUSH) * closeness) * scale * (blocked ? BLAST_SHELTERED : 1.0);
			// Thrown outward and up: a blast at a body's feet lifts it rather than sweeping it along the ground.
			Vec3 direction = (distance < 1.0E-3 ? new Vec3(0.0, 1.0, 0.0) : out.scale(1.0 / distance)).add(0.0, 0.55, 0.0).normalize();
			double share = 0.9 + 0.2 * random.nextDouble();
			body.vx += direction.x * speed * share;
			body.vy += direction.y * speed * share;
			body.vz += direction.z * speed * share;
			double spin = 5.0 * closeness * (blocked ? BLAST_SHELTERED : 1.0);
			body.wx += (random.nextDouble() - 0.5) * spin;
			body.wy += (random.nextDouble() - 0.5) * spin;
			body.wz += (random.nextDouble() - 0.5) * spin;
			strongest = Math.max(strongest, speed);
		}
		return String.format(java.util.Locale.ROOT, "blast, strongest push %.1f blocks/s, %d of %d parts sheltered, centre (%.2f, %.2f, %.2f) from %s, reach %.1f",
				strongest, sheltered, bodies.length, centre.x, centre.y, centre.z, how, reach);
	}

	/**
	 * Where the explosion was and how far it reaches. The damage tells the client for a creeper but not for TNT: the
	 * primed TNT is gone before the damage arrives, and the damage comes with no position. Every explosion is also
	 * sent to the client on its own, with its exact centre, so the one that just went off near the body is used.
	 * Only when that is missing too is the centre guessed, from the push the server gave the body, and then only from
	 * the push along the ground: the game always lifts a pushed mob, and following that upward part put the blast
	 * underground, where the ground itself shielded the body.
	 */
	private static double[] blastCentre(LivingEntity entity, DamageSource source) {
		Vec3 middle = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
		Vec3 known = source.getSourcePosition();
		double[] seen = RecentExplosions.near(middle);
		if (known != null) {
			return new double[] {known.x, known.y, known.z, seen != null ? seen[3] : BLAST_REACH, 0.0};
		}
		if (seen != null) {
			return new double[] {seen[0], seen[1], seen[2], seen[3], 1.0};
		}
		Vec3 motion = entity.getDeltaMovement();
		Vec3 flat = new Vec3(motion.x, 0.0, motion.z);
		double push = flat.length();
		// The push the client sees has already been slowed by the game, so the distance cannot be read from it; a
		// short one is the safer guess, since a far one can end up behind a wall.
		Vec3 back = push > 1.0E-3 ? flat.scale(-BLAST_GUESS_DISTANCE / push) : Vec3.ZERO;
		return new double[] {entity.getX() + back.x, entity.getY() + BLAST_FOOT_HEIGHT, entity.getZ() + back.z, BLAST_REACH, 2.0};
	}

	/** A mace smash drives the body straight down, harder the faster the attacker was falling. */
	private static String smash(DamageSource source, Body[] bodies, JointRole[] roles, double scale) {
		Entity attacker = source.getEntity();
		double falling = 0.0;
		if (attacker != null) {
			falling = Math.max(0.0, Math.max(-attacker.getDeltaMovement().y, attacker.yo - attacker.getY())) * 20.0;
		}
		double down = Math.min(MACE_MAX, (MACE_PUSH + 0.25 * falling) * scale);
		for (int i = 0; i < bodies.length; i++) {
			// A body hammered into the ground stays down: it must not spring back up.
			bodies[i].restitution = 0.0;
			// The blow lands on the head and shoulders; the limbs are carried down by the torso.
			double share = roles[i] == JointRole.BODY || roles[i] == JointRole.HEAD ? 1.0 : 0.5;
			bodies[i].vy -= down * share;
		}
		return String.format(java.util.Locale.ROOT, "mace smash, %.1f blocks/s down (attacker falling at %.1f)", down, falling);
	}
}
