package com.frostfleee.ragdolldeaths.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

/**
 * Blood, drawn on this client only: a drop that falls and leaves a small stain where it lands, a fine mist, or a flat
 * stain lying on the ground that fades away (see Blood). The textures are the mod's own, in the particle atlas.
 */
final class BloodParticle extends SingleQuadParticle {
	public static final int DROP = 0, MIST = 1, STAIN = 2;

	private final int mode;
	private final float fullAlpha;
	private final float yaw;
	private final Quaternionf flat = new Quaternionf();

	/**
	 * Adds one blood particle. Returns false when there is no world or the texture is missing.
	 *
	 * @param sprite the texture's name in the mod's particle textures
	 * @param rgb its colour; the textures are pale and tinted with it
	 */
	static boolean spawn(int mode, String sprite, double x, double y, double z, double vx, double vy, double vz, float size, int rgb, float alpha, int lifetime) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return false;
		}
		TextureAtlasSprite texture = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(Identifier.fromNamespaceAndPath(RagdollDeathsClient.MOD_ID, sprite));
		// A texture that is not in the atlas comes back as the missing one: nothing is drawn rather than that.
		if (texture == null || texture.contents().name().getPath().equals("missingno")) {
			return false;
		}
		BloodParticle particle = new BloodParticle(level, x, y, z, texture, mode, alpha);
		particle.xd = vx;
		particle.yd = vy;
		particle.zd = vz;
		particle.quadSize = size;
		particle.rCol = (rgb >> 16 & 0xFF) / 255.0F;
		particle.gCol = (rgb >> 8 & 0xFF) / 255.0F;
		particle.bCol = (rgb & 0xFF) / 255.0F;
		particle.lifetime = lifetime;
		minecraft.particleEngine.add(particle);
		return true;
	}

	private BloodParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, int mode, float alpha) {
		super(level, x, y, z, sprite);
		this.mode = mode;
		this.fullAlpha = alpha;
		this.alpha = alpha;
		this.yaw = random.nextFloat() * (float) (Math.PI * 2.0);
		switch (mode) {
			case DROP -> {
				gravity = 0.9F;
				friction = 0.98F;
				hasPhysics = true;
				setSize(0.05F, 0.05F);
			}
			case MIST -> {
				gravity = 0.06F;
				friction = 0.86F;
				hasPhysics = false;
			}
			default -> {
				gravity = 0.0F;
				friction = 0.0F;
				hasPhysics = false;
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (removed) {
			return;
		}
		switch (mode) {
			case DROP -> {
				if (onGround) {
					Blood.dropLanded(level, x, y, z, quadSize);
					remove();
				} else if (!level.getFluidState(BlockPos.containing(x, y, z)).isEmpty()) {
					remove();
				}
			}
			case MIST -> {
				alpha = fullAlpha * (1.0F - (float) age / lifetime);
				quadSize *= 1.02F;
			}
			default -> {
				xd = yd = zd = 0.0;
				// Full colour for most of its life, then it fades away.
				alpha = fullAlpha * Math.min(1.0F, (lifetime - age) / (0.4F * lifetime));
			}
		}
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partialTicks) {
		if (mode != STAIN) {
			super.extract(state, camera, partialTicks);
			return;
		}
		// Lying flat on the ground, facing up, turned its own way.
		extractRotatedQuad(state, camera, flat.rotationY(yaw).rotateX((float) (-Math.PI / 2.0)), partialTicks);
	}
}
