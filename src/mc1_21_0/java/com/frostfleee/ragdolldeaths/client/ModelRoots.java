package com.frostfleee.ragdolldeaths.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The root part of a model, for 1.21 and 1.21.1, where most models do not keep it: a humanoid or four-legged model only
 * keeps its parts. Every model is built from a freshly baked layer, so the layer baked just before a model is made is
 * remembered (ModelRootMixin) and, once it is shown to hold the model's own parts, used as its root, with the parts'
 * real names. A model whose layer is not known gets a root made of its parts.
 */
public final class ModelRoots {
	private static final Map<Model, ModelPart> CANDIDATES = new WeakHashMap<>();
	private static final Map<Model, ModelPart> ROOTS = new WeakHashMap<>();
	private static ModelPart lastBaked;

	private ModelRoots() {
	}

	public static void baked(ModelPart root) {
		lastBaked = root;
	}

	/** Called when a model has been made: the layer baked just before it is probably its own. */
	public static void made(Model model) {
		if (lastBaked != null) {
			CANDIDATES.put(model, lastBaked);
			lastBaked = null;
		}
	}

	public static ModelPart of(Model model) {
		if (model instanceof HierarchicalModel<?> hierarchical) {
			return hierarchical.root();
		}
		ModelPart root = ROOTS.get(model);
		if (root == null) {
			root = find(model);
			ROOTS.put(model, root);
		}
		return root;
	}

	private static ModelPart find(Model model) {
		List<ModelPart> parts = new ArrayList<>();
		if (model instanceof AgeableListModel<?> ageable) {
			com.frostfleee.ragdolldeaths.mixin.AgeableListModelAccessor accessor = (com.frostfleee.ragdolldeaths.mixin.AgeableListModelAccessor) ageable;
			accessor.ragdolldeaths$headParts().forEach(parts::add);
			accessor.ragdolldeaths$bodyParts().forEach(parts::add);
		} else if (model instanceof ListModel<?> list) {
			list.parts().forEach(parts::add);
		}
		ModelPart candidate = CANDIDATES.get(model);
		if (candidate != null && (parts.isEmpty() || holdsAll(candidate, parts))) {
			return candidate;
		}
		Map<String, ModelPart> children = new HashMap<>();
		for (int i = 0; i < parts.size(); i++) {
			children.put("part" + i, parts.get(i));
		}
		return new ModelPart(List.of(), children);
	}

	private static boolean holdsAll(ModelPart root, List<ModelPart> parts) {
		Map<ModelPart, Boolean> found = new IdentityHashMap<>();
		collect(root, found);
		for (ModelPart part : parts) {
			if (!found.containsKey(part)) {
				return false;
			}
		}
		return true;
	}

	private static void collect(ModelPart part, Map<ModelPart, Boolean> found) {
		found.put(part, Boolean.TRUE);
		for (ModelPart child : ((com.frostfleee.ragdolldeaths.mixin.ModelPartAccessor) (Object) part).ragdolldeaths$children().values()) {
			collect(child, found);
		}
	}
}
