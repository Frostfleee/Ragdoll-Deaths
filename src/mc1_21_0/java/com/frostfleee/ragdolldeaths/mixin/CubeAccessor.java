package com.frostfleee.ragdolldeaths.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21 and 1.21.1 only: a cube's faces, private there (public from 1.21.2). */
@Mixin(ModelPart.Cube.class)
public interface CubeAccessor {
	@Accessor("polygons")
	ModelPart.Polygon[] ragdolldeaths$polygons();
}
