package com.frostfleee.ragdolldeaths.mixin;

import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.21 and 1.21.1 only: the parts of a model that keeps no root (see ModelRoots). */
@Mixin(AgeableListModel.class)
public interface AgeableListModelAccessor {
	@Invoker("headParts")
	Iterable<ModelPart> ragdolldeaths$headParts();

	@Invoker("bodyParts")
	Iterable<ModelPart> ragdolldeaths$bodyParts();
}
