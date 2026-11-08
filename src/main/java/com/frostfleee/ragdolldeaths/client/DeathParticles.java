package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.RagdollPose;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The puff a body leaves when it vanishes: vanilla's death poof plus the silverfish (infested) specks. Both are
 * scattered the way vanilla scatters them over a standing mob, then moved onto the body as it last lay, so they
 * appear where the body actually was rather than where the entity stood.
 */
final class DeathParticles {
	private static final int POOF_COUNT = 20;
	private static final int INFESTED_COUNT = 16;

	private DeathParticles() {
	}

	static void spawn(Level level, Vec3 feet, RagdollPose pose, double width, double height, RandomSource random) {
		for (int i = 0; i < POOF_COUNT; i++) {
			double xa = random.nextGaussian() * 0.02;
			double ya = random.nextGaussian() * 0.02;
			double za = random.nextGaussian() * 0.02;
			Vec3 at = onBody(feet, pose, width * (2.0 * random.nextDouble() - 1.0), height * random.nextDouble(), width * (2.0 * random.nextDouble() - 1.0));
			level.addParticle(ParticleTypes.POOF, at.x - xa * 10.0, at.y - ya * 10.0, at.z - za * 10.0, xa, ya, za);
		}
		for (int i = 0; i < INFESTED_COUNT; i++) {
			Vec3 at = onBody(feet, pose, 0.5 * width * (2.0 * random.nextDouble() - 1.0), height * random.nextDouble(), 0.5 * width * (2.0 * random.nextDouble() - 1.0));
			level.addParticle(ParticleTypes.INFESTED, at.x, at.y, at.z, 1.0, 1.0, 1.0);
		}
	}

	private static Vec3 onBody(Vec3 feet, RagdollPose pose, double x, double y, double z) {
		double[] moved = pose.toWorld(x, y, z);
		return feet.add(moved[0], moved[1], moved[2]);
	}
}
