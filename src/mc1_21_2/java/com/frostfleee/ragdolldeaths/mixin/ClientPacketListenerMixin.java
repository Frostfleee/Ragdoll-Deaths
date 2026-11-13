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
	/** The explosion packet carries no radius before 1.21.9: about that of a creeper or of TNT. */
	private static final float USUAL_RADIUS = 4.0F;

	// Remembers where each explosion went off, so a body killed by TNT (whose damage carries no position) can be
	// thrown away from the real blast. This copy is for 1.21.2 to 1.21.8.
	@Inject(method = "handleExplosion", at = @At("TAIL"))
	private void ragdolldeaths$rememberExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
		RecentExplosions.record(packet.center(), USUAL_RADIUS);
	}
}
