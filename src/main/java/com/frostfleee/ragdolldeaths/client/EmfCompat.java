package com.frostfleee.ragdolldeaths.client;

import java.lang.reflect.Method;
import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;

/**
 * Works with Entity Model Features (EMF), the mod behind most "3D mobs" resource packs (Fresh Animations, AL's packs).
 * EMF animates a pack's model every frame, after the game has posed it, so it would undo a ragdoll's pose: bodies stood
 * frozen or snapped back. While a mob has a ragdoll body, EMF is asked to pause that mob's pack animations.
 *
 * EMF is optional: it is reached through reflection, so this does nothing (and needs nothing) when it is not installed.
 */
public final class EmfCompat {
	private static final String API = "traben.entity_model_features.EMFAnimationApi";

	private static java.lang.reflect.Field vanillaRootField;
	private static boolean vanillaRootLooked;

	private EmfCompat() {
	}

	/**
	 * For a model root swapped in by EMF: the vanilla parts it was made from, which EMF keeps (EMFModelPartRoot.vanillaRoot)
	 * at the same pivots, with the vanilla shapes. Null for any other root.
	 */
	static net.minecraft.client.model.geom.ModelPart vanillaRoot(net.minecraft.client.model.geom.ModelPart root) {
		if (!root.getClass().getName().startsWith("traben.")) {
			return null;
		}
		try {
			if (!vanillaRootLooked) {
				vanillaRootLooked = true;
				for (Class<?> c = root.getClass(); c != null && vanillaRootField == null; c = c.getSuperclass()) {
					try {
						vanillaRootField = c.getDeclaredField("vanillaRoot");
					} catch (NoSuchFieldException e) {
						// look in the superclass
					}
				}
			}
			return vanillaRootField != null && vanillaRootField.get(root) instanceof net.minecraft.client.model.geom.ModelPart vanilla ? vanilla : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	public static void init() {
		Class<?> api;
		try {
			api = Class.forName(API, false, EmfCompat.class.getClassLoader());
		} catch (ClassNotFoundException | LinkageError e) {
			return;
		}
		try {
			// EMF passes its own view of the entity (on 26.x a snapshot taken for drawing, not the entity itself); the
			// UUID it carries (ETF's etf$getUuid) finds the body.
			Method uuidOf = Class.forName("traben.entity_texture_features.utils.ETFEntity", false, EmfCompat.class.getClassLoader()).getMethod("etf$getUuid");
			Function<Object, Boolean> hasBody = entity -> {
				if (entity instanceof LivingEntity living) {
					return RagdollTracker.INSTANCE.hasBody(living);
				}
				try {
					return uuidOf.invoke(entity) instanceof java.util.UUID uuid && RagdollTracker.INSTANCE.hasBody(uuid);
				} catch (ReflectiveOperationException | RuntimeException e) {
					return false;
				}
			};
			api.getMethod("registerPauseCondition", Function.class).invoke(null, hasBody);
			// The body keeps the pack's model (a pack retextures its mobs for its own model, so the vanilla one would
			// look scrambled); RagdollBuilder measures its limbs from the pack's parts.
			RagdollDeathsClient.LOGGER.info("Entity Model Features found: its animations pause on ragdoll bodies");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			RagdollDeathsClient.LOGGER.warn("Entity Model Features found, but its animation API could not be used; ragdolls of its models may not pose", e);
		}
	}
}
