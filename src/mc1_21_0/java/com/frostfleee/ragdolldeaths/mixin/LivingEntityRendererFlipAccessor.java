package com.frostfleee.ragdolldeaths.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.21 and 1.21.1 only (their renderer asks the entity): see LivingEntityRendererAccessor. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererFlipAccessor {
	@Invoker("getFlipDegrees")
	float ragdolldeaths$getFlipDegrees(LivingEntity entity);
}
