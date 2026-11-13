package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.mixin.ModelPartAccessor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;

/**
 * Gives a model's arms and legs a knee or an elbow. Minecraft limbs are one stiff box each; a ragdoll that cannot
 * bend them stands propped against a fence like a plank instead of crumpling.
 *
 * <p>The limb's own cubes are never touched. Two extra child parts are added to it, an upper half and a lower half
 * that pivots at the cut, built by slicing every face of the limb's cubes at the middle and keeping each half's
 * share of the texture. Both stay invisible, so a living mob is drawn exactly as before. Only while a ragdoll is
 * being drawn is the whole limb hidden and the two halves shown.
 */
final class LimbSplitter {
	static final String UPPER = "ragdolldeaths_upper";
	static final String LOWER = "ragdolldeaths_lower";

	private static final float EDGE = 1.0E-3F;
	/** Limbs shorter than this many pixels, or not clearly longer than they are thick, are left whole. */
	private static final float MIN_LENGTH = 6.0F;

	/** A limb that has halves. {@code hiddenBefore} is whether the game itself had the limb's cubes hidden. */
	record Split(ModelPart limb, ModelPart upper, ModelPart lower, boolean hiddenBefore) {
	}

	private static final Map<ModelPart, List<Split>> BY_ROOT = new WeakHashMap<>();

	private LimbSplitter() {
	}

	/** Where along the limb (its own Y, in pixels) to cut it, or NaN for a part that should stay whole. */
	static float planeOf(ModelPart limb) {
		List<ModelPart.Cube> cubes = ((ModelPartAccessor) (Object) limb).ragdolldeaths$cubes();
		if (cubes.isEmpty()) {
			return Float.NaN;
		}
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (ModelPart.Cube cube : cubes) {
			minX = Math.min(minX, cube.minX);
			minY = Math.min(minY, cube.minY);
			minZ = Math.min(minZ, cube.minZ);
			maxX = Math.max(maxX, cube.maxX);
			maxY = Math.max(maxY, cube.maxY);
			maxZ = Math.max(maxZ, cube.maxZ);
		}
		float length = maxY - minY;
		if (length < MIN_LENGTH || length < 1.5F * Math.max(maxX - minX, maxZ - minZ)) {
			return Float.NaN;
		}
		return (minY + maxY) * 0.5F;
	}

	/**
	 * The limb's two halves, made on first use.
	 *
	 * @param root the model the limb belongs to, so the halves can be hidden again when that model is drawn for a
	 *             living mob
	 * @return null when the limb cannot be cut there
	 */
	static Split split(ModelPart root, ModelPart limb, float plane) {
		List<Split> known = BY_ROOT.computeIfAbsent(root, r -> new ArrayList<>());
		for (Split split : known) {
			if (split.limb == limb) {
				return split;
			}
		}
		ModelPartAccessor access = (ModelPartAccessor) (Object) limb;
		List<ModelPart.Cube> cubes = access.ragdolldeaths$cubes();
		float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (ModelPart.Cube cube : cubes) {
			minX = Math.min(minX, cube.minX);
			minZ = Math.min(minZ, cube.minZ);
			maxX = Math.max(maxX, cube.maxX);
			maxZ = Math.max(maxZ, cube.maxZ);
		}
		// The lower half turns about the middle of the cut.
		float pivotX = (minX + maxX) * 0.5F, pivotZ = (minZ + maxZ) * 0.5F;
		List<ModelPart.Cube> upper = new ArrayList<>();
		List<ModelPart.Cube> lower = new ArrayList<>();
		for (ModelPart.Cube cube : cubes) {
			if (!halve(cube, plane, pivotX, pivotZ, upper, lower)) {
				return null;
			}
		}
		if (upper.isEmpty() || lower.isEmpty()) {
			return null;
		}
		ModelPart upperPart = new ModelPart(upper, new HashMap<>());
		ModelPart lowerPart = new ModelPart(lower, new HashMap<>());
		lowerPart.setInitialPose(PartPose.offset(pivotX, plane, pivotZ));
		lowerPart.resetPose();
		upperPart.visible = false;
		lowerPart.visible = false;
		Map<String, ModelPart> children = new LinkedHashMap<>(access.ragdolldeaths$children());
		children.put(UPPER, upperPart);
		children.put(LOWER, lowerPart);
		access.ragdolldeaths$setChildren(children);
		Split split = new Split(limb, upperPart, lowerPart, limb.skipDraw);
		known.add(split);
		return split;
	}

	/** Shows the two halves in place of the whole limb, for a ragdoll about to be drawn. */
	static void show(Split split) {
		split.limb.skipDraw = true;
		split.upper.visible = true;
		split.lower.visible = true;
	}

	/** Puts a model back to whole limbs; called whenever it is drawn for anything that is not a ragdoll. */
	static void rest(ModelPart root) {
		if (BY_ROOT.isEmpty()) {
			return;
		}
		List<Split> known = BY_ROOT.get(root);
		if (known == null) {
			return;
		}
		for (Split split : known) {
			split.limb.skipDraw = split.hiddenBefore;
			split.upper.visible = false;
			split.lower.visible = false;
		}
	}

	/** Cuts one cube at the plane into the two lists. The lower piece is stored relative to the pivot. */
	private static boolean halve(ModelPart.Cube cube, float plane, float pivotX, float pivotZ, List<ModelPart.Cube> upper, List<ModelPart.Cube> lower) {
		List<ModelPart.Polygon> up = new ArrayList<>();
		List<ModelPart.Polygon> low = new ArrayList<>();
		for (ModelPart.Polygon polygon : Compat.polygons(cube)) {
			float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
			for (ModelPart.Vertex vertex : Compat.vertices(polygon)) {
				min = Math.min(min, Compat.vy(vertex));
				max = Math.max(max, Compat.vy(vertex));
			}
			if (max <= plane + EDGE) {
				up.add(polygon);
			} else if (min >= plane - EDGE) {
				low.add(polygon);
			} else {
				up.add(clip(polygon, plane, true));
				low.add(clip(polygon, plane, false));
			}
			// The cube's end faces also close the cut: its top, moved down, tops the lower half, and its bottom,
			// moved up, closes the upper half. They show when the limb bends.
			if (max - min < EDGE && cube.minY < plane - EDGE && cube.maxY > plane + EDGE) {
				(min < plane ? low : up).add(flatten(polygon, plane));
			}
		}
		if (!up.isEmpty()) {
			ModelPart.Cube made = cube(cube.minX, cube.minY, cube.minZ, cube.maxX, Math.min(cube.maxY, plane), cube.maxZ, up, 0.0F, 0.0F, 0.0F);
			if (made == null) {
				return false;
			}
			upper.add(made);
		}
		if (!low.isEmpty()) {
			ModelPart.Cube made = cube(cube.minX, Math.max(cube.minY, plane), cube.minZ, cube.maxX, cube.maxY, cube.maxZ, low, pivotX, plane, pivotZ);
			if (made == null) {
				return false;
			}
			lower.add(made);
		}
		return true;
	}

	/**
	 * A cube with the given faces, moved by minus the offset. The game only builds cubes from a texture layout, so
	 * one is built with the right number of faces and its faces are then replaced.
	 */
	private static ModelPart.Cube cube(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, List<ModelPart.Polygon> faces,
			float offsetX, float offsetY, float offsetZ) {
		Direction[] directions = Direction.values();
		if (faces.size() > directions.length) {
			return null;
		}
		Set<Direction> sides = EnumSet.noneOf(Direction.class);
		for (int i = 0; i < faces.size(); i++) {
			sides.add(directions[i]);
		}
		ModelPart.Cube cube = new ModelPart.Cube(0, 0, minX - offsetX, minY - offsetY, minZ - offsetZ, maxX - minX, maxY - minY, maxZ - minZ,
				0.0F, 0.0F, 0.0F, false, 64.0F, 64.0F, sides);
		if (Compat.polygons(cube).length != faces.size()) {
			return null;
		}
		for (int i = 0; i < faces.size(); i++) {
			ModelPart.Polygon face = faces.get(i);
			ModelPart.Vertex[] moved = new ModelPart.Vertex[Compat.vertices(face).length];
			for (int k = 0; k < moved.length; k++) {
				ModelPart.Vertex v = Compat.vertices(face)[k];
				moved[k] = Compat.vertex(Compat.vx(v) - offsetX, Compat.vy(v) - offsetY, Compat.vz(v) - offsetZ, Compat.vu(v), Compat.vv(v));
			}
			Compat.polygons(cube)[i] = Compat.newPolygon(moved, Compat.normal(face));
		}
		return cube;
	}

	/** The part of a side face above the plane (smaller Y) or below it, with the texture cut at the same place. */
	private static ModelPart.Polygon clip(ModelPart.Polygon polygon, float plane, boolean keepAbove) {
		ModelPart.Vertex[] from = Compat.vertices(polygon);
		ModelPart.Vertex[] to = new ModelPart.Vertex[from.length];
		for (int i = 0; i < from.length; i++) {
			ModelPart.Vertex v = from[i];
			boolean kept = keepAbove ? Compat.vy(v) <= plane : Compat.vy(v) >= plane;
			if (kept) {
				to[i] = v;
				continue;
			}
			// Slide this corner along its edge to the plane: its partner is the corner on the kept side straight
			// above or below it.
			ModelPart.Vertex partner = null;
			float nearest = Float.MAX_VALUE;
			for (ModelPart.Vertex other : from) {
				boolean otherKept = keepAbove ? Compat.vy(other) <= plane : Compat.vy(other) >= plane;
				float distance = Math.abs(Compat.vx(other) - Compat.vx(v)) + Math.abs(Compat.vz(other) - Compat.vz(v));
				if (otherKept && distance < nearest) {
					nearest = distance;
					partner = other;
				}
			}
			if (partner == null) {
				to[i] = v;
				continue;
			}
			float f = (plane - Compat.vy(v)) / (Compat.vy(partner) - Compat.vy(v));
			to[i] = Compat.vertex(Compat.vx(v) + (Compat.vx(partner) - Compat.vx(v)) * f, plane, Compat.vz(v) + (Compat.vz(partner) - Compat.vz(v)) * f,
					Compat.vu(v) + (Compat.vu(partner) - Compat.vu(v)) * f, Compat.vv(v) + (Compat.vv(partner) - Compat.vv(v)) * f);
		}
		return Compat.newPolygon(to, Compat.normal(polygon));
	}

	private static ModelPart.Polygon flatten(ModelPart.Polygon polygon, float y) {
		ModelPart.Vertex[] from = Compat.vertices(polygon);
		ModelPart.Vertex[] to = new ModelPart.Vertex[from.length];
		for (int i = 0; i < from.length; i++) {
			to[i] = Compat.vertex(Compat.vx(from[i]), y, Compat.vz(from[i]), Compat.vu(from[i]), Compat.vv(from[i]));
		}
		return Compat.newPolygon(to, Compat.normal(polygon));
	}
}
