package com.frostfleee.ragdolldeaths.visualtest;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;

/**
 * Draws one frame at a point inside the current tick and saves it, without moving the game on. This copy is for
 * 26.2 and later, whose renderer can be driven a frame at a time.
 */
final class FrameCapture {
	static final boolean SUPPORTED = true;

	private FrameCapture() {
	}

	/** Draws the frame {@code partial} of a tick after the last one and queues its copy to {@code file}. */
	static void queue(Minecraft client, float partial, Path file, AtomicInteger pending) {
		net.minecraft.client.DeltaTracker delta = new net.minecraft.client.DeltaTracker() {
			@Override
			public float getGameTimeDeltaTicks() {
				return 0.4f;
			}

			@Override
			public float getGameTimeDeltaPartialTick(boolean runsNormally) {
				return partial;
			}

			@Override
			public float getRealtimeDeltaTicks() {
				return 0.4f;
			}
		};
		client.gameRenderer.update(delta);
		client.gameRenderer.extract(delta, true);
		client.gameRenderer.render();
		com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().submit();
		pending.incrementAndGet();
		net.minecraft.client.Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
			try (image) {
				image.writeToFile(file);
			} catch (java.io.IOException e) {
				RagdollVisualTest.LOGGER.error("could not save frame {}", file, e);
			} finally {
				pending.decrementAndGet();
			}
		});
	}

	/** Hides the HUD, so nothing covers the bodies: by the toggle key, as a player would. */
	static void hideHud(net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext context) {
		context.getInput().pressKey(options -> options.keyToggleGui);
	}
}
