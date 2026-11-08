package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.JointPose;
import com.frostfleee.ragdolldeaths.core.JointRole;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class JointApplier {
	private JointApplier() {
	}

	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof EntityRenderState renderState)) {
			return;
		}
		JointPose joints = RagdollRenderData.joints(renderState);
		if (joints == null) {
			return;
		}

		ModelPart root = model.root();
		try {
			for (JointRig.Joint joint : JointRig.of(root).joints) {
				pose(joint, joints);
			}
		} catch (RuntimeException e) {
			JointRig.disable(root);
			RagdollDeathsClient.LOGGER.error("Disabling joint animation for model {} after an error", model.getClass().getName(), e);
		}
	}

	private static final float MODEL_GROUND_Y = 24.0F;

	private static void pose(JointRig.Joint joint, JointPose joints) {
		ModelPart part = joint.part();
		relax(part, (float) joints.relax(joint.role()));
		melt(part, joint.role(), joints);
		if (joint.role() == JointRole.EYE) {
			return;
		}
		part.xRot = (float) joints.rotation(joint.role(), joint.side(), JointPose.X, joint.index(), part.xRot);
		part.yRot = (float) joints.rotation(joint.role(), joint.side(), JointPose.Y, joint.index(), part.yRot);
		part.zRot = (float) joints.rotation(joint.role(), joint.side(), JointPose.Z, joint.index(), part.zRot);

		part.xScale *= (float) joints.scale(joint.role(), JointPose.X);
		part.yScale *= (float) joints.scale(joint.role(), JointPose.Y);
		part.zScale *= (float) joints.scale(joint.role(), JointPose.Z);
		part.x += (float) joints.translation(joint.role(), joint.side(), JointPose.X);
		part.y += (float) joints.translation(joint.role(), joint.side(), JointPose.Y);
		part.z += (float) joints.translation(joint.role(), joint.side(), JointPose.Z);

		double lift = joints.lift(joint.role());
		if (lift != 0.0) {
			part.xRot += (float) (lift * joint.outwardZ());
			part.zRot -= (float) (lift * joint.outwardX());
		}
		if (joints.scatters(joint.role())) {
			scatter(joint, joints);
		}
	}

	/**
	 * Drops a loose part until it stands on the ground, then tips it over its bottom end until it lies flat
	 * facing its own way, sliding out from the body's center as it goes.
	 */
	private static void scatter(JointRig.Joint joint, JointPose joints) {
		JointRole role = joint.role();
		int index = joint.index();
		double drop = joints.scatterDrop(role, index);
		if (drop <= 0.0) {
			return;
		}
		ModelPart part = joint.part();
		double tip = joints.scatterTip(role, index);
		double angle = Math.PI / 2.0 * tip;
		double heading = joints.scatterHeading(role, index);
		double length = joint.length();
		double standing = joint.groundY() - length;

		double fromCenter = Math.hypot(part.x, part.z);
		double outX = fromCenter > 1.0E-3 ? part.x / fromCenter : joint.outwardX();
		double outZ = fromCenter > 1.0E-3 ? part.z / fromCenter : joint.outwardZ();
		double slide = joints.scatterSlide(role, index);
		double swing = length * Math.sin(angle);

		part.y = (float) (Mth.lerp(drop, part.y, standing) + length * (1.0 - Math.cos(angle)) + joint.minZ() * Math.sin(angle)
				- joints.scatterHop(role, index));
		part.x += (float) (outX * slide - swing * Math.sin(heading));
		part.z += (float) (outZ * slide - swing * Math.cos(heading));
		part.xRot = (float) Mth.lerp(tip, part.xRot, Math.PI / 2.0);
		part.yRot = (float) Mth.lerp(tip, part.yRot, heading);
		part.zRot = (float) Mth.lerp(tip, part.zRot, 0.0);
	}

	private static void relax(ModelPart part, float weight) {
		if (weight <= 0.0F) {
			return;
		}
		PartPose rest = part.getInitialPose();
		part.x = Mth.lerp(weight, part.x, rest.x());
		part.y = Mth.lerp(weight, part.y, rest.y());
		part.z = Mth.lerp(weight, part.z, rest.z());
		part.xRot = Mth.lerp(weight, part.xRot, rest.xRot());
		part.yRot = Mth.lerp(weight, part.yRot, rest.yRot());
		part.zRot = Mth.lerp(weight, part.zRot, rest.zRot());
	}

	private static void melt(ModelPart part, JointRole role, JointPose joints) {
		float height = (float) joints.meltHeight();
		float spread = (float) joints.meltSpread();
		if (height == 1.0F && spread == 1.0F) {
			return;
		}
		part.y = MODEL_GROUND_Y - (MODEL_GROUND_Y - part.y) * height;
		part.x *= spread;
		part.z *= spread;
		if (role == JointRole.BODY) {
			part.yScale *= height;
			part.xScale *= spread;
			part.zScale *= spread;
		}
	}
}
