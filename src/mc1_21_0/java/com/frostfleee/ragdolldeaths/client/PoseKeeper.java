package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.mixin.ModelPartAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Gives a model back the pose it had before a ragdoll was drawn with it. Only 1.21 and 1.21.1 need this: every mob of
 * a kind is drawn with the same model, and these versions' models set only the parts their animation moves, without
 * first going back to their rest pose (later versions do). Whatever a ragdoll wrote to the other parts (a sheep's body,
 * a chicken's body and beak) stayed there, and the living mobs of that kind were drawn with it.
 *
 * <p>A model's pose is kept the first time a ragdoll is about to change it, which is right after the game posed it, and
 * put back when the ragdoll mob is done drawing. Drawing runs on one thread.
 */
public final class PoseKeeper {
	private static final int FLOATS = 9;

	private static final List<ModelPart> ROOTS = new ArrayList<>();
	private static final List<ModelPart> PARTS = new ArrayList<>();
	private static float[] values = new float[64 * FLOATS];

	private PoseKeeper() {
	}

	/** Keeps the model's pose, unless it is already kept for the mob being drawn. */
	public static void remember(Model model) {
		ModelPart root = Compat.root(model);
		for (int i = 0; i < ROOTS.size(); i++) {
			if (ROOTS.get(i) == root) {
				return;
			}
		}
		ROOTS.add(root);
		keep(root);
	}

	private static void keep(ModelPart part) {
		int at = PARTS.size() * FLOATS;
		if (at + FLOATS > values.length) {
			values = java.util.Arrays.copyOf(values, values.length * 2);
		}
		PARTS.add(part);
		values[at] = part.x;
		values[at + 1] = part.y;
		values[at + 2] = part.z;
		values[at + 3] = part.xRot;
		values[at + 4] = part.yRot;
		values[at + 5] = part.zRot;
		values[at + 6] = part.xScale;
		values[at + 7] = part.yScale;
		values[at + 8] = part.zScale;
		for (ModelPart child : ((ModelPartAccessor) (Object) part).ragdolldeaths$children().values()) {
			keep(child);
		}
	}

	/** Puts every kept model back: its parts' poses, whole limbs in place of bent ones, and the parts a ragdoll hid. */
	public static void restore() {
		if (ROOTS.isEmpty()) {
			return;
		}
		for (int i = 0; i < PARTS.size(); i++) {
			ModelPart part = PARTS.get(i);
			int at = i * FLOATS;
			part.x = values[at];
			part.y = values[at + 1];
			part.z = values[at + 2];
			part.xRot = values[at + 3];
			part.yRot = values[at + 4];
			part.zRot = values[at + 5];
			part.xScale = values[at + 6];
			part.yScale = values[at + 7];
			part.zScale = values[at + 8];
		}
		for (ModelPart root : ROOTS) {
			LimbSplitter.rest(root);
			PhysicsRagdoll.showHidden(root);
		}
		ROOTS.clear();
		PARTS.clear();
	}
}
