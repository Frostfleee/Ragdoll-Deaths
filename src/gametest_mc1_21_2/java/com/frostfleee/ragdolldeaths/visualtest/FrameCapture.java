package com.frostfleee.ragdolldeaths.visualtest;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;

/** Frames between ticks cannot be drawn on this game version; the visual test films one frame a tick instead. */
final class FrameCapture {
	static final boolean SUPPORTED = false;

	private FrameCapture() {
	}

	static void queue(Minecraft client, float partial, Path file, AtomicInteger pending) {
		throw new UnsupportedOperationException("no frame capture between ticks on this game version");
	}

	/** Hides the HUD, so nothing covers the bodies. This version has no toggle key binding of its own for it. */
	static void hideHud(net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext context) {
		context.runOnClient(client -> client.options.hideGui = true);
	}
}
