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

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void ragdolldeaths$followBody(float partialTicks, CallbackInfo callback) {
		DeathCamera.Pose pose = DeathCamera.place(partialTicks);
		if (pose != null) {
			setRotation(pose.yaw(), pose.pitch());
			setPosition(pose.position());
		}
	}
}
