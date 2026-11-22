package com.frostfleee.ragdolldeaths.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Random;

/**
 * Blood painted on a mob's texture: soft-edged splotches, each with a short run trickling down, only on pixels that
 * are drawn (not on the texture's empty areas). The amount per pixel (0 to 1) is kept apart from the image, so the
 * blood can be redrawn under lava burns and added to later.
 */
final class BloodPaint {
	/** The colour of blood on skin. */
	private static final int R = 104, G = 9, B = 9;

	private BloodPaint() {
	}

	/**
	 * Adds {@code count} splotches. A splotch's middle is a drawn pixel picked at random, from the whole texture or from
	 * {@code rect} ({x0, y0, x1, y1}, exclusive ends), and it stays inside {@code rect} when one is given.
	 *
	 * @param size 1 for an ordinary splotch, more for a bigger one (a wound)
	 */
	static void blobs(NativeImage image, float[] amount, int width, int height, Random random, int count, int[] rect, float size) {
		int x0 = rect == null ? 0 : Math.max(0, rect[0]), y0 = rect == null ? 0 : Math.max(0, rect[1]);
		int x1 = rect == null ? width : Math.min(width, rect[2]), y1 = rect == null ? height : Math.min(height, rect[3]);
		if (x1 <= x0 || y1 <= y0) {
			return;
		}
		// Most mob textures are 64 pixels wide; bigger ones get bigger splotches.
		float scale = Math.max(1.0F, width / 64.0F);
		for (int n = 0; n < count; n++) {
			int cx = -1, cy = -1;
			for (int tries = 0; tries < 40; tries++) {
				int x = x0 + random.nextInt(x1 - x0), y = y0 + random.nextInt(y1 - y0);
				if (opaque(image, x, y)) {
					cx = x;
					cy = y;
					break;
				}
			}
			// Drawn even when no middle was found, so the same seed always gives the same pattern.
			float radius = (0.9F + random.nextFloat() * 1.4F) * scale * size;
			int run = random.nextInt(3) + (int) scale;
			int rx = cx + random.nextInt(3) - 1;
			if (cx < 0) {
				continue;
			}
			int reach = (int) Math.ceil(radius + 1.0F);
			for (int y = Math.max(y0, cy - reach); y < Math.min(y1, cy + reach + 1); y++) {
				for (int x = Math.max(x0, cx - reach); x < Math.min(x1, cx + reach + 1); x++) {
					if (!opaque(image, x, y)) {
						continue;
					}
					// Ragged edges: each pixel is a little nearer or farther than it is.
					float d = (float) Math.hypot(x - cx, y - cy) + ((x * 7 + y * 13 + n * 5) % 7) / 10.0F;
					if (d < radius) {
						put(amount, y * width + x, d < radius * 0.55F ? 0.92F : 0.7F);
					}
				}
			}
			// A short run trickling down from it.
			if (rx >= x0 && rx < x1) {
				for (int k = 1; k <= run; k++) {
					int y = cy + (int) radius + k - 1;
					if (y >= y1 || !opaque(image, rx, y)) {
						break;
					}
					put(amount, y * width + rx, 0.75F - 0.1F * k);
				}
			}
		}
	}

	private static void put(float[] amount, int at, float value) {
		amount[at] = Math.max(amount[at], value);
	}

	private static boolean opaque(NativeImage image, int x, int y) {
		return (Compat.getPixel(image, x, y) >>> 24) > 16;
	}

	/** The pixel with this much blood over it; the skin's shading still shows through a little. */
	static int blend(int argb, float amount) {
		if (amount <= 0.0F) {
			return argb;
		}
		int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
		float light = (0.299F * r + 0.587F * g + 0.114F * b) / 255.0F;
		float shade = 0.65F + 0.45F * light;
		int br = (int) Math.min(255.0F, R * shade), bg = (int) Math.min(255.0F, G * shade), bb = (int) Math.min(255.0F, B * shade);
		r = (int) (r + (br - r) * amount);
		g = (int) (g + (bg - g) * amount);
		b = (int) (b + (bb - b) * amount);
		return argb & 0xFF000000 | r << 16 | g << 8 | b;
	}
}
