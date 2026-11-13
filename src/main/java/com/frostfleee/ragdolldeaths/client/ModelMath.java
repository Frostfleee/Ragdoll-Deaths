package com.frostfleee.ragdolldeaths.client;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A model part's transform as a matrix and back, exactly the way {@code ModelPart.translateAndRotate} applies it. */
final class ModelMath {
	private ModelMath() {
	}

	/** Multiplies a matrix by a part's current transform: move by its pivot (in pixels), turn Z then Y then X, scale. */
	static Matrix4f mul(Matrix4f m, ModelPart part) {
		m.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
		if (part.xRot != 0.0F || part.yRot != 0.0F || part.zRot != 0.0F) {
			m.rotateZYX(part.zRot, part.yRot, part.xRot);
		}
		if (part.xScale != 1.0F || part.yScale != 1.0F || part.zScale != 1.0F) {
			m.scale(part.xScale, part.yScale, part.zScale);
		}
		return m;
	}

	static Matrix4f mul(Matrix4f m, Rest pose) {
		m.translate(pose.x() / 16.0F, pose.y() / 16.0F, pose.z() / 16.0F);
		if (pose.xRot() != 0.0F || pose.yRot() != 0.0F || pose.zRot() != 0.0F) {
			m.rotateZYX(pose.zRot(), pose.yRot(), pose.xRot());
		}
		if (pose.xScale() != 1.0F || pose.yScale() != 1.0F || pose.zScale() != 1.0F) {
			m.scale(pose.xScale(), pose.yScale(), pose.zScale());
		}
		return m;
	}

	/** Sets a part's pivot, angles and scale so that its transform is the given matrix. */
	static void store(Matrix4f m, ModelPart part, Quaternionf rotation, Vector3f angles) {
		part.x = m.m30() * 16.0F;
		part.y = m.m31() * 16.0F;
		part.z = m.m32() * 16.0F;
		part.xScale = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
		part.yScale = (float) Math.sqrt(m.m10() * m.m10() + m.m11() * m.m11() + m.m12() * m.m12());
		part.zScale = (float) Math.sqrt(m.m20() * m.m20() + m.m21() * m.m21() + m.m22() * m.m22());
		m.getNormalizedRotation(rotation).getEulerAnglesZYX(angles);
		part.xRot = angles.x;
		part.yRot = angles.y;
		part.zRot = angles.z;
	}
}
