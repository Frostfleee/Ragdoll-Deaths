package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.BodyKind;
import com.frostfleee.ragdolldeaths.core.RagdollAnimator;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.DyeColor;

public final class RagdollTracker {
	public static final RagdollTracker INSTANCE = new RagdollTracker();

	private static final int STALE_AFTER_TICKS = 40;

	private final Int2ObjectMap<ActiveRagdoll> active = new Int2ObjectOpenHashMap<>();
	private final Map<LivingEntity, ActiveRagdoll> ghosts = new IdentityHashMap<>();
	private final Set<EntityType<?>> failedTypes = new HashSet<>();
	private ClientLevel level;
	private long tick;

	private RagdollTracker() {
	}

	public void onStateCreated(EntityRenderer<?, ?> renderer, Entity entity, EntityRenderState state, float partialTicks) {
		if (!(entity instanceof LivingEntity living) || living.deathTime <= 0) {
			return;
		}
		if (!(state instanceof LivingEntityRenderState livingState) || !(renderer instanceof LivingEntityRenderer<?, ?, ?>)) {
			return;
		}

		try {
			ActiveRagdoll ragdoll = find(living);
			if (ragdoll == null) {
				ragdoll = capture(renderer, living);
				if (ragdoll == null) {
					return;
				}
			}
			ragdoll.apply(livingState, ConfigStore.get(), tick, partialTicks);
		} catch (RuntimeException e) {
			fail(living, state, e);
		}
	}

	/**
	 * Called from the death poof hook. Animated bodies never show the poof; when the body should outlive the entity
	 * it is also marked to continue as a ghost once the server removes the entity.
	 */
	public boolean suppressPoof(LivingEntity entity) {
		if (!entity.level().isClientSide()) {
			return false;
		}
		ActiveRagdoll ragdoll = active.get(entity.getId());
		if (ragdoll == null || ragdoll.entity != entity) {
			return false;
		}
		RagdollConfig config = ConfigStore.get();
		if (ghosts.size() < config.maxActiveRagdolls && RagdollAnimator.needsGhost(ragdoll.profile, config, ragdoll.age(tick, 0.0F))) {
			ragdoll.keepAsGhost();
		}
		return true;
	}

	public void onUnload(Entity entity) {
		ActiveRagdoll ragdoll = active.get(entity.getId());
		if (ragdoll == null || ragdoll.entity != entity) {
			return;
		}
		active.remove(entity.getId());
		if (ragdoll.keptAsGhost()) {
			ragdoll.becomeGhost(tick);
			ghosts.put(ragdoll.entity, ragdoll);
		} else if (entity.isRemoved() && ragdoll.entity.isDeadOrDying()) {
			ragdoll.vanish();
		}
	}

	public void tick(ClientLevel tickedLevel) {
		if (tickedLevel != level) {
			clear();
			level = tickedLevel;
		}
		tick++;
		RagdollConfig config = ConfigStore.get();

		active.values().removeIf(ragdoll -> ragdoll.entity.isRemoved() || !ragdoll.entity.isDeadOrDying() || ragdoll.age(tick, 0.0F) > STALE_AFTER_TICKS);
		for (ActiveRagdoll ragdoll : active.values()) {
			ragdoll.checkKnockedOff(tick);
			if (ragdoll.isFinished(config, tick)) {
				ragdoll.vanish();
			}
		}
		ghosts.values().removeIf(ghost -> {
			if (!ghost.isFinished(config, tick)) {
				return false;
			}
			ghost.vanish();
			return true;
		});
	}

	/** Adds the ghost bodies to the frame being extracted; each loader calls this at the end of level extraction. */
	public void extractGhosts(ClientLevel extractedLevel, LevelRenderState levelState, float partialTicks) {
		if (ghosts.isEmpty()) {
			return;
		}
		RagdollConfig config = ConfigStore.get();
		Iterator<ActiveRagdoll> iterator = ghosts.values().iterator();
		while (iterator.hasNext()) {
			ActiveRagdoll ghost = iterator.next();
			if (ghost.entity.level() != extractedLevel || !EntityFilter.withinRenderDistance(ghost.entity.position(), config)) {
				continue;
			}
			try {
				EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(ghost.entity, partialTicks);
				if (RagdollRenderData.pose(state) != null) {
					levelState.entityRenderStates.add(state);
				}
			} catch (RuntimeException e) {
				iterator.remove();
				markFailed(ghost.entity.getType(), e);
			}
		}
	}

	public void clear() {
		active.clear();
		ghosts.clear();
	}

	private ActiveRagdoll find(LivingEntity entity) {
		if (entity.isRemoved()) {
			return ghosts.get(entity);
		}
		ActiveRagdoll ragdoll = active.get(entity.getId());
		if (ragdoll != null && ragdoll.entity != entity) {
			active.remove(entity.getId());
			return null;
		}
		return ragdoll;
	}

	private ActiveRagdoll capture(EntityRenderer<?, ?> renderer, LivingEntity entity) {
		RagdollConfig config = ConfigStore.get();
		if (entity.isRemoved() || !entity.isDeadOrDying() || active.size() >= config.maxActiveRagdolls) {
			return null;
		}
		if (failedTypes.contains(entity.getType()) || !EntityFilter.isEligible(entity, config)) {
			return null;
		}
		ActiveRagdoll ragdoll = new ActiveRagdoll(entity, ProfileFactory.create((LivingEntityRenderer<?, ?, ?>) renderer, entity, config), tick);
		active.put(entity.getId(), ragdoll);
		if (ragdoll.profile.kind() == BodyKind.SHULKER && entity instanceof Shulker shulker) {
			breakShell(shulker);
		}
		return ragdoll;
	}

	/** The shulker breaks apart like a shulker box of its color: the box's own break particles, in the same spots and number. */
	private static void breakShell(Shulker shulker) {
		DyeColor color = shulker.getColor();
		shulker.level().addDestroyBlockEffect(shulker.blockPosition(), Compat.shulkerBox(color).defaultBlockState());
	}

	private void fail(LivingEntity entity, EntityRenderState state, RuntimeException error) {
		active.remove(entity.getId());
		ghosts.remove(entity);
		RagdollRenderData.set(state, null, null);
		markFailed(entity.getType(), error);
	}

	private void markFailed(EntityType<?> type, RuntimeException error) {
		if (failedTypes.add(type)) {
			RagdollDeathsClient.LOGGER.error("Disabling ragdolls for {} after an error; it will use the vanilla death instead", EntityFilter.idOf(type), error);
		}
	}
}
