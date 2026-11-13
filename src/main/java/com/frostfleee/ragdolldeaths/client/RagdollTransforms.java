package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.RagdollPose;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class RagdollTransforms {
	// Rotations go through mulPose(Matrix4fc), which every 26.x PoseStack has (26.1 and 26.2 lack rotate).
	private static final Matrix4f MATRIX = new Matrix4f();
	private static final Quaternionf ROTATION = new Quaternionf();

	private RagdollTransforms() {
	}

	public static void apply(RagdollPose pose, PoseStack poseStack) {
		poseStack.translate(pose.offsetX, pose.offsetY, pose.offsetZ);
		if (pose.slopeAngle != 0.0) {
			poseStack.mulPose(MATRIX.rotation(ROTATION.rotationAxis((float) pose.slopeAngle, (float) pose.slopeAxisX, 0.0F, (float) pose.slopeAxisZ)));
		}
		float pivot = (float) pose.pivotY;
		poseStack.translate(0.0F, pivot, 0.0F);
		if (pose.spinAngle != 0.0) {
			poseStack.mulPose(MATRIX.rotation(ROTATION.rotationY((float) pose.spinAngle)));
		}
		if (pose.tipAngle != 0.0) {
			poseStack.mulPose(MATRIX.rotation(ROTATION.rotationAxis((float) pose.tipAngle, (float) pose.tipAxisX, 0.0F, (float) pose.tipAxisZ)));
		}
		if (pose.rollAngle != 0.0) {
			poseStack.mulPose(MATRIX.rotation(ROTATION.rotationAxis((float) pose.rollAngle, (float) pose.forwardX, 0.0F, (float) pose.forwardZ)));
		}
		poseStack.translate(0.0F, -pivot, 0.0F);
		if (pose.squashY != 1.0 || pose.squashXZ != 1.0) {
			poseStack.scale((float) pose.squashXZ, (float) pose.squashY, (float) pose.squashXZ);
		}
	}
}
