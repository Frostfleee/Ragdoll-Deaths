package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.SelfTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives the self-test, which does nothing unless the game was started with it switched on (see SelfTest). */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void ragdolldeaths$selfTest(CallbackInfo ci) {
		if (SelfTest.ENABLED) {
			SelfTest.clientTick((Minecraft) (Object) this);
		}
	}
}
