package com.frostfleee.ragdolldeaths.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.GuardianRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Slime;

/**
 * Minecraft 1.21 and 1.21.1 have no render states: an entity is drawn straight from the entity. For a dying mob this
 * makes one of the mod's stand-in states (see net.minecraft.client.renderer.entity.state.EntityRenderState in this
 * folder) just before it is drawn, so the shared code can work on it as on later versions; the 1.21 hooks then read it
 * back while the mob is drawn.
 */
public final class RagdollStates {
	private static final Map<LivingEntity, LivingEntityRenderState> STATES = new WeakHashMap<>();

	/** The state of the dying mob being drawn right now, or null. Drawing runs on one thread. */
	public static LivingEntityRenderState current;

	private RagdollStates() {
	}

	/** Called just before an entity is drawn. */
	@SuppressWarnings("rawtypes")
	public static void capture(EntityRenderer renderer, Entity entity, float partialTicks) {
		current = null;
		if (!(entity instanceof LivingEntity living) || living.deathTime <= 0 || !(renderer instanceof LivingEntityRenderer)) {
			return;
		}
		LivingEntityRenderState state = STATES.computeIfAbsent(living, RagdollStates::create);
		fill(state, living, partialTicks);
		RagdollTracker.INSTANCE.onStateCreated(renderer, living, state, partialTicks);
		current = state;
	}

	/** A state for an entity outside of drawing (unused on 1.21: ghost bodies are drawn from the entity list). */
	public static EntityRenderState stateFor(Entity entity, float partialTicks) {
		LivingEntityRenderState state = create((LivingEntity) entity);
		fill(state, (LivingEntity) entity, partialTicks);
		return state;
	}

	private static LivingEntityRenderState create(LivingEntity entity) {
		if (entity instanceof Guardian) {
			return new GuardianRenderState();
		}
		if (entity instanceof Slime) {
			return new SlimeRenderState();
		}
		return new LivingEntityRenderState();
	}

	private static void fill(LivingEntityRenderState state, LivingEntity entity, float partialTicks) {
		state.entity = entity;
		state.partialTicks = partialTicks;
		state.x = Mth.lerp(partialTicks, entity.xo, entity.getX());
		state.y = Mth.lerp(partialTicks, entity.yo, entity.getY());
		state.z = Mth.lerp(partialTicks, entity.zo, entity.getZ());
		state.ageInTicks = entity.tickCount + partialTicks;
		state.hideShadow = false;
		state.bodyRot = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
		state.yRot = Mth.wrapDegrees(Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot) - state.bodyRot);
		state.xRot = entity.getViewXRot(partialTicks);
		state.hasRedOverlay = entity.hurtTime > 0 || entity.deathTime > 0;
		state.walkAnimationPos = entity.walkAnimation.position(partialTicks);
		state.walkAnimationSpeed = entity.walkAnimation.speed(partialTicks);
		if (state instanceof GuardianRenderState guardian && entity instanceof Guardian mob) {
			guardian.spikesAnimation = mob.getSpikesAnimation(partialTicks);
			guardian.tailAnimation = mob.getTailAnimation(partialTicks);
		}
		if (state instanceof SlimeRenderState slime && entity instanceof Slime mob) {
			slime.squish = Mth.lerp(partialTicks, mob.oSquish, mob.squish);
		}
	}
}
