package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.LimbBend;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * For the self-test and with RAGDOLL_WATCH set: catches a body part that is not drawn where its physics body is, or that
 * jumps or spins impossibly fast from one frame to the next. Such a glitch lasts a frame or two, too short to see in a
 * screenshot, so every drawn part is compared.
 */
final class PoseWatch {
	static final boolean ON = SelfTest.ENABLED || System.getenv("RAGDOLL_WATCH") != null;

	private static final double MISMATCH_DEGREES = 3.0, MISMATCH_BLOCKS = 0.03;
	/** A jump: at least this big, and faster than any body really turns or moves. */
	private static final double JUMP_DEGREES = 30.0, JUMP_DEGREES_PER_TICK = 120.0, JUMP_BLOCKS = 1.5, JUMP_BLOCKS_PER_TICK = 6.0;
	/** How many times the same kind of trouble with the same part of the same mob is written to the log. */
	private static final int MAX_LOGGED = 2;

	/** How many times a part was drawn away from its body, and how many times one jumped. */
	static long mismatches, jumps;
	/** How many times a part reversed its turn at speed from one tick to the next: it is shaking. */
	static long shakes;
	/** The fastest any part turned, in degrees per tick, and which one. */
	static double fastest;
	static String fastestPart = "none";
	private static final double SHAKE_DEGREES_PER_TICK = 12.0;
	private static final Map<String, Integer> logged = new java.util.HashMap<>();

	private static final class Seen {
		final Quaternionf rotation = new Quaternionf();
		final Vector3f position = new Vector3f();
		double time;
		// Sampled once a tick, to see how it is turning.
		final Quaternionf sampled = new Quaternionf();
		final Vector3f turn = new Vector3f();
		double sampledAt = -1.0;
	}

	private final Map<Object, Seen> seen = new IdentityHashMap<>();
	private final Matrix4f drawn = new Matrix4f();
	private final Quaternionf wanted = new Quaternionf(), actual = new Quaternionf();
	private final Vector3f wantedAt = new Vector3f(), actualAt = new Vector3f();
	private final Quaternionf delta = new Quaternionf();
	private final Vector3f spin = new Vector3f();

	/** Forgets the last frame: the body was moved on purpose. */
	void reset() {
		seen.clear();
	}

	/**
	 * @param key the part in this model
	 * @param above where the part hangs from
	 * @param target where the part should be
	 * @param time the body's age, in ticks
	 */
	void check(PhysicsRagdoll ragdoll, Object key, ModelPart part, String[] path, Matrix4f above, Matrix4f target, double time, double cameraX, double cameraY, double cameraZ) {
		if (!Double.isNaN(LimbBend.testDegrees)) {
			seen.clear();
			return;
		}
		ModelMath.mul(drawn.set(above), part);
		target.getUnnormalizedRotation(wanted);
		drawn.getUnnormalizedRotation(actual);
		target.getTranslation(wantedAt);
		drawn.getTranslation(actualAt);
		double off = degrees(wanted, actual), away = wantedAt.distance(actualAt);
		if (off > MISMATCH_DEGREES || away > MISMATCH_BLOCKS || Double.isNaN(off) || Double.isNaN(away)) {
			mismatches++;
			log("drawn away from its body", ragdoll, path, off, away, part, above, target);
		}
		// Positions are relative to the camera, which moves: compared in the world.
		wantedAt.add((float) cameraX, (float) cameraY, (float) cameraZ);
		Seen last = seen.get(key);
		if (last == null) {
			last = new Seen();
			seen.put(key, last);
		} else {
			double dt = time - last.time;
			if (dt > 1.0E-3) {
				double turned = degrees(last.rotation, wanted), moved = last.position.distance(wantedAt);
				if (turned > JUMP_DEGREES && turned / dt > JUMP_DEGREES_PER_TICK || moved > JUMP_BLOCKS && moved / dt > JUMP_BLOCKS_PER_TICK) {
					jumps++;
					log("jumped, in " + String.format(java.util.Locale.ROOT, "%.2f", dt) + " ticks", ragdoll, path, turned, moved, part, above, target);
				}
			}
		}
		sampleTurn(last, wanted, time, ragdoll, path, part, above, target);
		last.rotation.set(wanted);
		last.position.set(wantedAt);
		last.time = time;
	}

	private void sampleTurn(Seen s, Quaternionf now, double time, PhysicsRagdoll ragdoll, String[] path, ModelPart part, Matrix4f above, Matrix4f target) {
		if (s.sampledAt < 0.0) {
			s.sampled.set(now);
			s.sampledAt = time;
			return;
		}
		double dt = time - s.sampledAt;
		if (dt < 1.0) {
			return;
		}
		now.mul(delta.set(s.sampled).conjugate(), delta);
		if (delta.w < 0.0F) {
			delta.set(-delta.x, -delta.y, -delta.z, -delta.w);
		}
		double sine = Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z);
		double perTick = Math.toDegrees(2.0 * Math.atan2(sine, delta.w)) / dt;
		spin.set(delta.x, delta.y, delta.z);
		if (sine > 1.0E-6) {
			spin.mul((float) (perTick / sine));
		} else {
			spin.zero();
		}
		if (perTick > fastest) {
			fastest = perTick;
			fastestPart = EntityFilter.idOf(ragdoll.entity.getType()) + " " + String.join("/", path);
		}
		double before = s.turn.length();
		if (perTick > SHAKE_DEGREES_PER_TICK && before > SHAKE_DEGREES_PER_TICK && spin.dot(s.turn) < -0.5 * perTick * before) {
			shakes++;
			log("shaking at age " + String.format(java.util.Locale.ROOT, "%.0f", time), ragdoll, path, perTick, before, part, above, target);
		}
		s.turn.set(spin);
		s.sampled.set(now);
		s.sampledAt = time;
	}

	private static double degrees(Quaternionf a, Quaternionf b) {
		double dot = Math.min(1.0, Math.abs(a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w));
		return Math.toDegrees(2.0 * Math.acos(dot));
	}

	private static void log(String what, PhysicsRagdoll ragdoll, String[] path, double degrees, double blocks, ModelPart part, Matrix4f above, Matrix4f target) {
		String id = EntityFilter.idOf(ragdoll.entity.getType()) + " " + String.join("/", path);
		if (logged.merge(id + what.charAt(0), 1, Integer::sum) <= MAX_LOGGED) {
			RagdollDeathsClient.LOGGER.warn("[pose watch] {}: {} ({} degrees, {} blocks; part at {} {} {} angles {} {} {} scale {} {} {}; above {}; target {})", id, what,
					number(degrees), number(blocks), number(part.x), number(part.y), number(part.z), number(part.xRot), number(part.yRot), number(part.zRot),
					number(part.xScale), number(part.yScale), number(part.zScale), row(above), row(target));
		}
	}

	private static String row(Matrix4f m) {
		return number(m.m00()) + " " + number(m.m01()) + " " + number(m.m02()) + " / " + number(m.m10()) + " " + number(m.m11()) + " " + number(m.m12()) + " / "
				+ number(m.m20()) + " " + number(m.m21()) + " " + number(m.m22());
	}

	private static String number(double value) {
		return String.format(java.util.Locale.ROOT, "%.3f", value);
	}
}
