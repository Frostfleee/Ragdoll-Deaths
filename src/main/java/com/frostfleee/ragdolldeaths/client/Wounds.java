package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;

/**
 * Blood on living mobs that are hurt (with Blood on): the more of its health a mob has lost, the more blood shows on
 * it, in three steps (below three quarters, half and a quarter of its health). It is only drawn on this client.
 *
 * <p>Each mob texture gets a few painted copies (each step, in a few patterns so a crowd does not all look alike),
 * made the first time they are needed and kept until the world is left. A dead body starts from the same pattern as
 * the last step, so a mob keeps its blood when it dies, and gets more around its wound.
 */
public final class Wounds {
	/** How many splotches each step adds, and how many a dead body has (the last step's and one more). */
	static final int BLOBS_PER_STEP = 3, DEAD_BLOBS = 3 * BLOBS_PER_STEP + 1;
	private static final int PATTERNS = 4;
	/** At most this many painted copies; past it they are all let go and made again as needed. */
	private static final int MAX_COPIES = 192;

	private static final Map<String, Object> COPIES = new HashMap<>();
	/** Textures that cannot be read (a player's skin): not tried again. */
	private static final Set<Object> UNREADABLE = new HashSet<>();
	private static int counter;
	/** For the self-test: how many times a living (not dying) mob was drawn with blood. */
	static long drawn;

	private Wounds() {
	}

	/** Which pattern of blood this mob gets: the same every time it is drawn, and for its dead body. */
	static int pattern(LivingEntity entity) {
		return Math.floorMod(entity.getId() * 31 + 7, PATTERNS);
	}

	/** The seed a texture's blood pattern is made from. */
	static long seed(Object texture, int pattern) {
		return texture.toString().hashCode() * 1_000_003L + pattern;
	}

	/** Puts on the render state how much blood this mob shows, while the entity is at hand. */
	static void note(EntityRenderState state, LivingEntity entity) {
		RagdollRenderData.setWounds(state, of(entity));
	}

	/**
	 * How much blood this mob shows, and in which pattern, as kept on a render state (0 for none). Public for 1.21 and
	 * 1.21.1, where a living mob has no render state and its texture is picked from the entity.
	 */
	public static int of(LivingEntity entity) {
		RagdollConfig config = ConfigStore.get();
		int step = 0;
		if (config.blood && config.enabled) {
			float max = entity.getMaxHealth();
			float left = max > 0.0F ? entity.getHealth() / max : 1.0F;
			step = entity.deathTime > 0 || left <= 0.25F ? 3 : left <= 0.5F ? 2 : left <= 0.75F ? 1 : 0;
			if (step > 0 && (!Blood.bleeds(entity) || entity.isInLava() || config.isExcluded(EntityFilter.idOf(entity.getType())))) {
				step = 0;
			}
		}
		return step == 0 ? 0 : step * PATTERNS + pattern(entity);
	}

	/** The texture a living mob is drawn with for its wounds (1.21 and 1.21.1, which have no render state for it). */
	public static <T> T livingTexture(T base, LivingEntity entity) {
		return texture(base, of(entity), true);
	}

	/** The texture to draw a mob with for its wounds: a painted copy, or the texture itself when there are none. */
	@SuppressWarnings("unchecked")
	static <T> T texture(T base, int wounds, boolean living) {
		if (wounds <= 0 || base == null || UNREADABLE.contains(base)) {
			return base;
		}
		int step = wounds / PATTERNS, pattern = wounds % PATTERNS;
		String key = base + "#" + step + "#" + pattern;
		Object id = COPIES.get(key);
		if (id == null) {
			if (COPIES.size() >= MAX_COPIES) {
				clear();
			}
			id = paint(base, step, pattern);
			if (id == null) {
				UNREADABLE.add(base);
				return base;
			}
			COPIES.put(key, id);
		}
		if (living) {
			drawn++;
		}
		return (T) id;
	}

	private static Object paint(Object base, int step, int pattern) {
		try (InputStream in = Compat.openResource(base)) {
			if (in == null) {
				return null;
			}
			try (NativeImage original = NativeImage.read(in)) {
				int width = original.getWidth(), height = original.getHeight();
				float[] amount = new float[width * height];
				BloodPaint.blobs(original, amount, width, height, new Random(seed(base, pattern)), step * BLOBS_PER_STEP, null, 1.0F);
				NativeImage copy = new NativeImage(width, height, false);
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						Compat.setPixel(copy, x, y, BloodPaint.blend(Compat.getPixel(original, x, y), amount[y * width + x]));
					}
				}
				return Compat.registerTexture(RagdollDeathsClient.MOD_ID, "wounds/" + counter++, Compat.newTexture("ragdolldeaths wounds", copy));
			}
		} catch (Exception e) {
			return null;
		}
	}

	/** Lets go of every painted copy: the world was left, or there were too many. */
	static void clear() {
		for (Object id : COPIES.values()) {
			Compat.releaseTexture(id);
		}
		COPIES.clear();
		UNREADABLE.clear();
	}
}
