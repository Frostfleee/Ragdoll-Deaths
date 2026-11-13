package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.mixin.ModelPartAccessor;
import com.frostfleee.ragdolldeaths.physics.Body;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Burn marks, pixel by pixel. A body that goes into lava gets its own copy of the mob's texture, and every pixel of
 * its skin that was actually under the lava's surface is burnt black on it, for good. Around the black there are two
 * softer rings: first the original colours much darker, then only a little darker. A leg dipped in lava comes out
 * with a black foot and a scorched shin on an otherwise untouched body.
 *
 * <p>Each pixel of the texture is found on the model (every face of every cube knows which patch of the texture it
 * shows), placed in the world from where the body part is, and tested against the lava there. This only runs on
 * ticks when part of the body is in lava.
 */
final class Scorch {
	/** How dark each stage is: the burnt pixels themselves, then the two rings around them. */
	private static final float BURNT = 0.08F, RING_NEAR = 0.38F, RING_FAR = 0.72F;
	/** How many pixels wide each ring is. */
	private static final int RING = 2;
	private static int counter;

	/** The burnt copy's texture id; its type is the game version's own resource id class. */
	final Object id;
	private final NativeImage original;
	private final DynamicTexture texture;
	private final int width, height;
	private final boolean[] burnt;
	private final byte[] distance;
	private boolean closed;

	private final Matrix4f matrix = new Matrix4f();
	private final Quaternionf rotation = new Quaternionf();
	private final Vector3f point = new Vector3f();
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	private Scorch(NativeImage original) {
		this.original = original;
		width = original.getWidth();
		height = original.getHeight();
		burnt = new boolean[width * height];
		distance = new byte[width * height];
		NativeImage copy = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				Compat.setPixel(copy, x, y, Compat.getPixel(original, x, y));
			}
		}
		texture = Compat.newTexture("ragdolldeaths scorch", copy);
		id = Compat.registerTexture(RagdollDeathsClient.MOD_ID, "scorch/" + counter++, texture);
	}

	/** A scorchable copy of a texture, or null when its pixels cannot be read (a player's skin, say). */
	static Scorch of(Object base) {
		if (base == null) {
			return null;
		}
		try {
			try (InputStream in = Compat.openResource(base)) {
				return in == null ? null : new Scorch(NativeImage.read(in));
			}
		} catch (Exception e) {
			return null;
		}
	}

	void close() {
		if (!closed) {
			closed = true;
			Compat.releaseTexture(id);
			original.close();
		}
	}

	/** Burns every pixel of the body's skin that is under lava right now. */
	void update(PhysicsRagdoll ragdoll) {
		if (closed || ragdoll.mainModel == null) {
			return;
		}
		ModelPart root = Compat.root(ragdoll.mainModel);
		Level level = ragdoll.entity.level();
		// Which parts are placed by a body; the rest are carried along inside one of those.
		Map<ModelPart, PhysicsRagdoll.Link> driven = new IdentityHashMap<>();
		for (PhysicsRagdoll.Link link : ragdoll.links) {
			ModelPart part = find(root, link.path());
			if (part != null) {
				driven.put(part, link);
			}
		}
		Body origin = ragdoll.bodies[ragdoll.torso];
		boolean changed = false;
		for (Map.Entry<ModelPart, PhysicsRagdoll.Link> entry : driven.entrySet()) {
			Body body = ragdoll.bodies[entry.getValue().body()];
			// Relative to the torso, so the numbers stay small enough for float matrices.
			matrix.translation((float) (body.x - origin.x), (float) (body.y - origin.y), (float) (body.z - origin.z))
					.rotate(rotation.set((float) body.qx, (float) body.qy, (float) body.qz, (float) body.qw))
					.mul(entry.getValue().offset());
			changed |= burn(entry.getKey(), new Matrix4f(matrix), driven, level, origin);
		}
		if (changed) {
			repaint();
		}
	}

	private boolean burn(ModelPart part, Matrix4f space, Map<ModelPart, PhysicsRagdoll.Link> driven, Level level, Body origin) {
		boolean changed = false;
		ModelPartAccessor access = (ModelPartAccessor) (Object) part;
		Map<String, ModelPart> children = access.ragdolldeaths$children();
		// A limb cut in two is drawn as its halves, not as itself.
		if (!children.containsKey(LimbSplitter.UPPER)) {
			for (ModelPart.Cube cube : access.ragdolldeaths$cubes()) {
				for (ModelPart.Polygon face : Compat.polygons(cube)) {
					changed |= burn(face, space, level, origin);
				}
			}
		}
		for (ModelPart child : children.values()) {
			if (!driven.containsKey(child) && child.visible) {
				changed |= burn(child, ModelMath.mul(new Matrix4f(space), child), driven, level, origin);
			}
		}
		return changed;
	}

	/** One face of a cube: the patch of texture it shows, each pixel of it placed on the face and tested. */
	private boolean burn(ModelPart.Polygon face, Matrix4f space, Level level, Body origin) {
		ModelPart.Vertex[] v = Compat.vertices(face);
		if (v.length < 4) {
			return false;
		}
		// The face is a parallelogram and its texture patch a rectangle: corner 1 is the patch's origin, corner 0
		// lies along U from it and corner 2 along V.
		float du = Compat.vu(v[0]) - Compat.vu(v[1]), dv = Compat.vv(v[2]) - Compat.vv(v[1]);
		if (Math.abs(du) < 1.0E-6F || Math.abs(dv) < 1.0E-6F) {
			return false;
		}
		int x0 = Math.max(0, (int) Math.floor(Math.min(Compat.vu(v[0]), Compat.vu(v[1])) * width)), x1 = Math.min(width, (int) Math.ceil(Math.max(Compat.vu(v[0]), Compat.vu(v[1])) * width));
		int y0 = Math.max(0, (int) Math.floor(Math.min(Compat.vv(v[1]), Compat.vv(v[2])) * height)), y1 = Math.min(height, (int) Math.ceil(Math.max(Compat.vv(v[1]), Compat.vv(v[2])) * height));
		boolean changed = false;
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				int at = y * width + x;
				if (burnt[at]) {
					continue;
				}
				float s = ((x + 0.5F) / width - Compat.vu(v[1])) / du, t = ((y + 0.5F) / height - Compat.vv(v[1])) / dv;
				point.set((Compat.vx(v[1]) + s * (Compat.vx(v[0]) - Compat.vx(v[1])) + t * (Compat.vx(v[2]) - Compat.vx(v[1]))) / 16.0F,
						(Compat.vy(v[1]) + s * (Compat.vy(v[0]) - Compat.vy(v[1])) + t * (Compat.vy(v[2]) - Compat.vy(v[1]))) / 16.0F,
						(Compat.vz(v[1]) + s * (Compat.vz(v[0]) - Compat.vz(v[1])) + t * (Compat.vz(v[2]) - Compat.vz(v[1]))) / 16.0F);
				space.transformPosition(point);
				double wx = origin.x + point.x, wy = origin.y + point.y, wz = origin.z + point.z;
				cursor.set(wx, wy, wz);
				FluidState fluid = level.getFluidState(cursor);
				if (fluid.is(FluidTags.LAVA) && wy < cursor.getY() + fluid.getHeight(level, cursor)) {
					burnt[at] = true;
					changed = true;
				}
			}
		}
		return changed;
	}

	/** Redraws the texture: black where burnt, the two rings around that, the original everywhere else. */
	private void repaint() {
		int far = RING * 2;
		for (int i = 0; i < distance.length; i++) {
			distance[i] = burnt[i] ? 0 : (byte) (far + 1);
		}
		// Spread outwards one pixel at a time, as far as the outer ring reaches.
		for (int step = 1; step <= far; step++) {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int at = y * width + x;
					if (distance[at] <= step) {
						continue;
					}
					boolean near = false;
					for (int oy = -1; oy <= 1 && !near; oy++) {
						for (int ox = -1; ox <= 1; ox++) {
							int nx = x + ox, ny = y + oy;
							if (nx >= 0 && ny >= 0 && nx < width && ny < height && distance[ny * width + nx] == step - 1) {
								near = true;
								break;
							}
						}
					}
					if (near) {
						distance[at] = (byte) step;
					}
				}
			}
		}
		NativeImage pixels = texture.getPixels();
		if (pixels == null) {
			return;
		}
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int d = distance[y * width + x];
				float keep = d == 0 ? BURNT : d <= RING ? RING_NEAR : d <= far ? RING_FAR : 1.0F;
				int argb = Compat.getPixel(original, x, y);
				int r = (int) ((argb >> 16 & 0xFF) * keep), g = (int) ((argb >> 8 & 0xFF) * keep), b = (int) ((argb & 0xFF) * keep);
				Compat.setPixel(pixels, x, y, argb & 0xFF000000 | r << 16 | g << 8 | b);
			}
		}
		texture.upload();
	}

	private static ModelPart find(ModelPart root, String[] path) {
		ModelPart part = root;
		for (String name : path) {
			part = part == null ? null : ((ModelPartAccessor) (Object) part).ragdolldeaths$children().get(name);
		}
		return part;
	}
}
