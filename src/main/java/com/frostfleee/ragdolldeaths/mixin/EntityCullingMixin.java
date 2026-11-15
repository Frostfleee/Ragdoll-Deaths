package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
abstract class EntityCullingMixin {
	// The game hides an entity farther than its render distance from the camera, measured from the entity, which stays
	// where the mob died; a body thrown or carried far from there vanished. It is measured from the body instead, with
	// the game's own distance rule for that mob.
	@ModifyExpressionValue(
			method = "shouldRender",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;shouldRender(DDD)Z"))
	private boolean ragdolldeaths$distanceFromBody(boolean near, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true, ordinal = 0) double camX,
			@Local(argsOnly = true, ordinal = 1) double camY, @Local(argsOnly = true, ordinal = 2) double camZ) {
		if (near || !(entity instanceof LivingEntity living)) {
			return near;
		}
		AABB bounds = RagdollTracker.INSTANCE.renderBounds(living);
		return bounds != null && entity.shouldRenderAtSqrDistance(bounds.getCenter().distanceToSqr(camX, camY, camZ));
	}

	// A pushed, falling or thrown body can be drawn well away from the hitbox; it is culled by where it is drawn.
	// The method is matched by name only: 26.3 added a partial-ticks parameter to it.
	@ModifyExpressionValue(
			method = "shouldRender",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z", ordinal = 0))
	private boolean ragdolldeaths$cullByRagdoll(boolean visible, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true) Frustum culler) {
		if (visible || !(entity instanceof LivingEntity living)) {
			return visible;
		}
		AABB bounds = RagdollTracker.INSTANCE.renderBounds(living);
		return bounds != null && culler.isVisible(bounds);
	}
}
