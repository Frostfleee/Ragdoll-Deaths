package com.frostfleee.ragdolldeaths.client;

import net.minecraft.client.model.geom.ModelPart;

/**
 * A model part's rest pose: where it sits and how it is turned and scaled before any animation. The game's own PartPose
 * changed shape over the versions (plain fields and no scale before 1.21.2), so the mod reads it into this.
 */
record Rest(float x, float y, float z, float xRot, float yRot, float zRot, float xScale, float yScale, float zScale) {
	static final Rest ZERO = new Rest(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);

	static Rest of(ModelPart part) {
		return Compat.initialPose(part);
	}
}
