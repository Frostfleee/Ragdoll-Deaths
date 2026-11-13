package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.JointPose;
import com.frostfleee.ragdolldeaths.core.JointPose.Side;
import com.frostfleee.ragdolldeaths.core.JointRole;
import com.frostfleee.ragdolldeaths.core.LimbDynamics;
import com.frostfleee.ragdolldeaths.core.RagdollPose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the limbs' physics out of the world. Each tick, every swinging limb's middle and end are placed in the
 * world exactly the way the renderer will draw them (the model's part transform, the body's heading, then the
 * ragdoll's pose). A swing that would push one into a block is scaled back to where it touches, and the limb
 * stops there, resting against the ground or the wall it hit.
 */
final class LimbCollider {
	/** The model is drawn upside down and lifted by this much, in blocks, by every living entity renderer. */
	private static final double MODEL_LIFT = 1.501;
	private static final int SEARCH_STEPS = 6;
	/** Parts shorter than this, in model pixels, can't reach far enough to matter. */
	private static final float MIN_REACH = 1.0F;

	private final JointRig rig;
	/** Model pixels to blocks, for this mob's size: babies are smaller, giants bigger. */
	private final double scale;

	private LimbCollider(JointRig rig, double scale) {
		this.rig = rig;
		this.scale = scale;
	}

	static LimbCollider of(JointRig rig, double bodyHeight) {
		if (rig == null || rig.modelHeight <= 0.0F) {
			return null;
		}
		return new LimbCollider(rig, Math.clamp(bodyHeight / (rig.modelHeight / 16.0), 0.2, 10.0) / 16.0);
	}

	/**
	 * @param feet where the pose is applied in the world: the body's feet before the pose's own offsets
	 * @param bodyYaw the heading the body is held at, in degrees
	 */
	void resolve(Level level, RagdollPose pose, JointPose joints, LimbDynamics limbs, Vec3 feet, float bodyYaw) {
		double yaw = Math.toRadians(180.0 - bodyYaw);
		double cos = Math.cos(yaw);
		double sin = Math.sin(yaw);
		for (JointRig.Joint joint : rig.joints) {
			JointRole role = joint.role();
			if (!collides(role)) {
				continue;
			}
			boolean up = role == JointRole.HEAD;
			float reach = up ? joint.top() : joint.length();
			if (reach < MIN_REACH) {
				continue;
			}
			Side side = joint.side();
			double swingX = limbs.swing(role, side, JointPose.X);
			double swingZ = side == Side.CENTER ? 0.0 : limbs.swing(role, side, JointPose.Z);
			if (swingX == 0.0 && swingZ == 0.0) {
				continue;
			}
			if (!hits(level, pose, joints, joint, swingX, swingZ, 1.0, reach, up, feet, cos, sin)) {
				continue;
			}
			// The animation alone already puts it there (an arm flung back into a wall): fold it toward its resting
			// pose, hanging along the body, until it is clear.
			if (hits(level, pose, joints, joint, swingX, swingZ, 0.0, reach, up, feet, cos, sin)) {
				fold(level, pose, joints, limbs, joint, reach, up, feet, cos, sin);
				continue;
			}
			double free = 0.0;
			double blocked = 1.0;
			for (int i = 0; i < SEARCH_STEPS; i++) {
				double middle = 0.5 * (free + blocked);
				if (hits(level, pose, joints, joint, swingX, swingZ, middle, reach, up, feet, cos, sin)) {
					blocked = middle;
				} else {
					free = middle;
				}
			}
			limbs.constrain(role, side, free);
		}
	}

	private void fold(Level level, RagdollPose pose, JointPose joints, LimbDynamics limbs, JointRig.Joint joint, float reach, boolean up,
			Vec3 feet, double cos, double sin) {
		JointRole role = joint.role();
		Side side = joint.side();
		var rest = joint.part().getInitialPose();
		double toRestX = rest.xRot() - joints.rotation(role, side, JointPose.X, joint.index(), rest.xRot());
		double toRestZ = side == Side.CENTER ? 0.0 : rest.zRot() - joints.rotation(role, side, JointPose.Z, joint.index(), rest.zRot());
		double swingZ = side == Side.LEFT ? -toRestZ : toRestZ;
		if (hits(level, pose, joints, joint, toRestX, swingZ, 1.0, reach, up, feet, cos, sin)) {
			limbs.constrain(role, side, 0.0);
			return;
		}
		double free = 1.0;
		double blocked = 0.0;
		for (int i = 0; i < SEARCH_STEPS; i++) {
			double middle = 0.5 * (free + blocked);
			if (hits(level, pose, joints, joint, toRestX, swingZ, middle, reach, up, feet, cos, sin)) {
				blocked = middle;
			} else {
				free = middle;
			}
		}
		limbs.setSwing(role, side, toRestX * free, swingZ * free);
	}

	/** Arms, legs and heads: the parts long enough to stick into the ground or a wall. */
	private static boolean collides(JointRole role) {
		return role == JointRole.ARM || role == JointRole.HEAD || role.isLeg() && role != JointRole.MID_LEG;
	}

	/** Whether the limb, with {@code keep} of its swing, has its middle or its end inside a block. */
	private boolean hits(Level level, RagdollPose pose, JointPose joints, JointRig.Joint joint, double swingX, double swingZ, double keep,
			float reach, boolean up, Vec3 feet, double cos, double sin) {
		JointRole role = joint.role();
		Side side = joint.side();
		var rest = joint.part().getInitialPose();
		double rx = joints.rotation(role, side, JointPose.X, joint.index(), rest.xRot()) + swingX * keep;
		double ry = joints.rotation(role, side, JointPose.Y, joint.index(), rest.yRot());
		double rz = joints.rotation(role, side, JointPose.Z, joint.index(), rest.zRot()) + (side == Side.LEFT ? -swingZ : swingZ) * keep;
		double along = up ? -reach : reach;
		return solidAt(level, pose, joint, rx, ry, rz, along, feet, cos, sin)
				|| solidAt(level, pose, joint, rx, ry, rz, along * 0.5, feet, cos, sin);
	}

	/** Whether the point {@code along} model pixels down the part (up when negative) is inside a block. */
	private boolean solidAt(Level level, RagdollPose pose, JointRig.Joint joint, double rx, double ry, double rz, double along,
			Vec3 feet, double cos, double sin) {
		// The part's own rotation, as ModelPart applies it: Z, then Y, then X.
		double y1 = along * Math.cos(rx);
		double z1 = along * Math.sin(rx);
		double x2 = z1 * Math.sin(ry);
		double z2 = z1 * Math.cos(ry);
		double x3 = x2 * Math.cos(rz) - y1 * Math.sin(rz);
		double y3 = x2 * Math.sin(rz) + y1 * Math.cos(rz);
		double mx = (joint.pivotX() + x3) * scale;
		double my = (joint.pivotY() + y3) * scale;
		double mz = (joint.pivotZ() + z2) * scale;

		// The renderer flips the model upside down, lifts it, then turns it to the body's heading.
		double ex = -mx;
		double ey = MODEL_LIFT * scale * 16.0 - my;
		double ez = mz;
		double wx = ex * cos + ez * sin;
		double wz = -ex * sin + ez * cos;
		double[] world = pose.toWorld(wx, ey, wz);
		return solid(level, feet.x + world[0], feet.y + world[1], feet.z + world[2]);
	}

	private static boolean solid(Level level, double x, double y, double z) {
		return BodyContact.solid(level, x, y, z);
	}
}
