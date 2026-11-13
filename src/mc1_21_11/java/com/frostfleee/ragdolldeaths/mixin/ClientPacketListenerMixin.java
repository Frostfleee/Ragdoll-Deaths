package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.RecentExplosions;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	// Remembers where each explosion went off, so a body killed by TNT (whose damage carries no position) can be
	// thrown away from the real blast. The tail is only reached on the client thread: the network thread hands the
	// packet over at the top of the method and leaves.
	@Inject(method = "handleExplosion", at = @At("TAIL"))
	private void ragdolldeaths$rememberExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
		RecentExplosions.record(packet.center(), packet.radius());
	}
}
