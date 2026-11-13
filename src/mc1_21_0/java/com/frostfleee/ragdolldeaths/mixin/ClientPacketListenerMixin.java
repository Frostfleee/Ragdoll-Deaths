package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.RecentExplosions;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	// Remembers where each explosion went off, so a body killed by TNT (whose damage carries no position) can be
	// thrown away from the real blast. This copy is for 1.21 and 1.21.1, whose packet still carries the blast's power.
	@Inject(method = "handleExplosion", at = @At("TAIL"))
	private void ragdolldeaths$rememberExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
		RecentExplosions.record(new Vec3(packet.getX(), packet.getY(), packet.getZ()), packet.getPower());
	}
}
