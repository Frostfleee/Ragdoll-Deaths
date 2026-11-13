package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.DeathCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves the camera to follow your body while you are dead, once the game has placed it as usual. */
@Mixin(Camera.class)
abstract class CameraMixin {
	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	// 1.21 places the camera in setup(), whose first parameter changes type between versions (26.x moved this into
	// alignWithEntity()); only the partial tick is taken from it.
	@Inject(method = "setup", at = @At("TAIL"))
	private void ragdolldeaths$followBody(CallbackInfo callback, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) float partialTicks) {
		DeathCamera.Pose pose = DeathCamera.place(partialTicks);
		if (pose != null) {
			setRotation(pose.yaw(), pose.pitch());
			setPosition(pose.position());
		}
	}
}
