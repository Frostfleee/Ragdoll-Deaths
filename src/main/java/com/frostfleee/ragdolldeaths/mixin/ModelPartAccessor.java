package com.frostfleee.ragdolldeaths.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.class)
public interface ModelPartAccessor {
	@Accessor("children")
	Map<String, ModelPart> ragdolldeaths$children();

	@Accessor("cubes")
	List<ModelPart.Cube> ragdolldeaths$cubes();

	/** Replaces the child map, to add the two halves of a limb (see LimbSplitter). */
	@Mutable
	@Accessor("children")
	void ragdolldeaths$setChildren(Map<String, ModelPart> children);
}
