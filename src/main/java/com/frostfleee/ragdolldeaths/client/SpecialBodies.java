package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.JointRole;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.core.Special;
import com.frostfleee.ragdolldeaths.physics.Body;
import com.frostfleee.ragdolldeaths.physics.Joint;
import java.util.Random;
import net.minecraft.world.entity.LivingEntity;

/**
 * The mobs that do not simply fall over.
 *
 * <ul>
 * <li><b>Skeletons</b> always come apart at the joints. A hard blow breaks them at once and the skull, ribcage and
 * limbs scatter; after a gentle death the body falls whole and breaks where it lands. One that never lands (in the
 * void, in deep water) lets its bones go after {@link #LOOSEN_TICKS}, with no throw: they drift apart on their own.</li>
 * <li><b>Slimes and magma cubes</b> are one lump that tumbles and comes to rest, keeping its shape.</li>
 * <li><b>The snow golem</b> loses its pumpkin head, which falls off and rolls; its snowballs keep their shape.</li>
 * <li><b>Blazes and breezes</b> are held together by their own power: when it goes, their rods drop free.</li>
 * </ul>
 */
final class SpecialBodies {
	enum Kind {
		SKELETON, BLOB, SNOW_GOLEM, RODS
	}

	/** A blow at least this many times a plain hit's knockback shatters a skeleton outright. */
	private static final double SHATTER_STRENGTH = 1.5;
	/** A bone whose speed changes by this much within one tick (blocks per second) has hit something hard enough to snap off. */
	private static final double SNAP_SPEED = 9.0;
	private static final double TICK = 1.0 / 20.0;
	/** A skeleton that has not landed this long after death (1.5 seconds) lets its bones go where they are. */
	static final int LOOSEN_TICKS = 30;
	/** How hard the bones scatter when a skeleton that fell whole breaks on landing. */
	private static final double LANDING_SCATTER = 0.6;

	private final Kind kind;
	private final Random random;
	/** Each body's velocity after the last tick, to tell when it hit something. */
	private final double[] before;
	private boolean first = true;
	private boolean snapped;
	/** Physics ticks since death. */
	private int ticks;
	/** How a skeleton came apart, for the self-test: "blow", "landing", "loosened", or null while still whole. */
	String brokeBy;
	/** For the self-test: the ticks since death it came apart at, or -1. */
	int brokeAt = -1;

	/** For the self-test: physics ticks since death. */
	int ticks() {
		return ticks;
	}

	/** Whether this is a skeleton that has come apart, whose pieces clatter. */
	boolean isLooseBones() {
		return kind == Kind.SKELETON && snapped;
	}

	private SpecialBodies(Kind kind, int bodies, long seed) {
		this.kind = kind;
		this.before = new double[bodies * 3];
		this.random = new Random(seed);
	}

	/**
	 * Sets up the special behaviour for a ragdoll that has just been built and struck, or returns null for an
	 * ordinary body.
	 *
	 * @param blowStrength how hard the killing blow was, as a multiple of a plain hit's knockback
	 */
	static SpecialBodies create(PhysicsRagdoll ragdoll, LivingEntity entity, double blowStrength, RagdollConfig config) {
		long seed = entity.getUUID().getLeastSignificantBits() ^ 0x5DEECE66DL;
		Special special = ProfileFactory.specialOf(entity);
		Body[] bodies = ragdoll.bodies;
		if (special == Special.SKELETON) {
			SpecialBodies made = new SpecialBodies(Kind.SKELETON, bodies.length, seed);
			// The body is built the first time the dying mob is drawn, which can be a while after it died (it was out of
			// sight, or the game was busy): the time to let go counts from the death.
			made.ticks = Math.max(0, entity.deathTime);
			if (blowStrength >= SHATTER_STRENGTH) {
				made.snapAll(ragdoll, 2.5);
				made.brokeBy = "blow";
				ragdoll.blow += "; shattered";
			}
			return made;
		}
		if (Compat.isCubeMob(entity)) {
			return new SpecialBodies(Kind.BLOB, bodies.length, seed);
		}
		if (special == Special.SNOW_GOLEM) {
			SpecialBodies made = new SpecialBodies(Kind.SNOW_GOLEM, bodies.length, seed);
			ragdoll.hideHead = Mobs.snowGolemHasPumpkin(entity);
			// The pumpkin is only resting on top: it comes off, nudged a little to one side.
			for (Joint joint : ragdoll.island.joints) {
				if (ragdoll.roles[indexOf(bodies, joint.b)] == JointRole.HEAD) {
					made.snap(ragdoll, joint, 1.2);
				}
			}
			return made;
		}
		if (special == Special.BLAZE || special == Special.BREEZE) {
			SpecialBodies made = new SpecialBodies(Kind.RODS, bodies.length, seed);
			Body core = bodies[ragdoll.torso];
			for (Joint joint : ragdoll.island.joints) {
				int index = indexOf(bodies, joint.b);
				if (ragdoll.roles[index] != JointRole.ROD) {
					continue;
				}
				// A rod leaves the way it was circling: outward from the middle, and tumbling.
				Body rod = joint.b;
				double ox = rod.x - core.x, oz = rod.z - core.z;
				double flat = Math.hypot(ox, oz);
				if (flat > 1.0E-3) {
					rod.vx += ox / flat * 2.5;
					rod.vz += oz / flat * 2.5;
				}
				made.snap(ragdoll, joint, 1.5);
				rod.wx += (made.random.nextDouble() - 0.5) * 12.0;
				rod.wz += (made.random.nextDouble() - 0.5) * 12.0;
			}
			return made;
		}
		return null;
	}

	/** Lets a joint go, and sends the freed part off with a little speed of its own so the pieces do not fall as one. */
	private void snap(PhysicsRagdoll ragdoll, Joint joint, double scatter) {
		if (joint.broken) {
			return;
		}
		ragdoll.island.release(joint);
		snapped = true;
		Body part = joint.b;
		part.vx += (random.nextDouble() - 0.5) * 2.0 * scatter;
		part.vy += random.nextDouble() * scatter;
		part.vz += (random.nextDouble() - 0.5) * 2.0 * scatter;
		part.wx += (random.nextDouble() - 0.5) * 4.0 * scatter;
		part.wy += (random.nextDouble() - 0.5) * 4.0 * scatter;
		part.wz += (random.nextDouble() - 0.5) * 4.0 * scatter;
	}

	private void snapAll(PhysicsRagdoll ragdoll, double scatter) {
		for (Joint joint : ragdoll.island.joints) {
			snap(ragdoll, joint, scatter);
		}
	}

	/** Lets every joint go without pushing anything: the bones only part where they already are. */
	private void loosenAll(PhysicsRagdoll ragdoll) {
		for (Joint joint : ragdoll.island.joints) {
			if (!joint.broken) {
				ragdoll.island.release(joint);
			}
		}
		snapped = true;
	}

	/** Whether every joint of the body has let go. */
	private static boolean allBroken(PhysicsRagdoll ragdoll) {
		for (Joint joint : ragdoll.island.joints) {
			if (!joint.broken) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether some part of the body is resting on, or has just struck, something that is not part of it: it landed.
	 * The legs do not count, as a skeleton killed standing has its feet on the ground from the start: it has landed
	 * once it has fallen over onto something.
	 */
	private static boolean touchesOutside(PhysicsRagdoll ragdoll) {
		Body[] bodies = ragdoll.bodies;
		for (int i = 0; i < bodies.length; i++) {
			Body b = bodies[i];
			if (ragdoll.roles[i] == JointRole.LEG) {
				continue;
			}
			for (int m = 0; m < b.heldCount(); m++) {
				if (indexOf(bodies, b.heldWith(m)) < 0) {
					return true;
				}
			}
		}
		return false;
	}

	/** Called after each tick of physics. */
	void afterTick(PhysicsRagdoll ragdoll) {
		Body[] bodies = ragdoll.bodies;
		switch (kind) {
			case SKELETON -> {
				ticks++;
				if (!allBroken(ragdoll)) {
					if (ragdoll.island.asleep || touchesOutside(ragdoll)) {
						// It fell whole and has come down on something: it breaks apart where it lies.
						snapAll(ragdoll, LANDING_SCATTER);
						if (brokeBy == null) {
							brokeBy = "landing";
							brokeAt = ticks;
						}
					} else if (ticks >= LOOSEN_TICKS) {
						// Still falling or floating (the void, deep water): the bones only part, with no throw.
						loosenAll(ragdoll);
						if (brokeBy == null) {
							brokeBy = "loosened";
							brokeAt = ticks;
						}
					}
				}
				// A bone that was just stopped short (it hit the ground, a wall) breaks off at its joints.
				if (!first && !ragdoll.island.asleep) {
					for (int i = 0; i < bodies.length; i++) {
						Body b = bodies[i];
						double dx = b.vx - before[i * 3], dy = b.vy - before[i * 3 + 1], dz = b.vz - before[i * 3 + 2];
						if (dx * dx + dy * dy + dz * dz < SNAP_SPEED * SNAP_SPEED) {
							continue;
						}
						for (Joint joint : ragdoll.island.joints) {
							if (joint.a == b || joint.b == b) {
								snap(ragdoll, joint, 0.8);
							}
						}
					}
				}
			}
			case BLOB, SNOW_GOLEM, RODS -> {
			}
		}
		for (int i = 0; i < bodies.length; i++) {
			before[i * 3] = bodies[i].vx;
			before[i * 3 + 1] = bodies[i].vy;
			before[i * 3 + 2] = bodies[i].vz;
		}
		first = false;
	}

	private static int indexOf(Body[] bodies, Body body) {
		for (int i = 0; i < bodies.length; i++) {
			if (bodies[i] == body) {
				return i;
			}
		}
		return -1;
	}
}
