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
import net.minecraft.world.phys.AABB;

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
		// A physics body always outlives the entity; an animated one only when its animation is not over yet.
		if (ragdoll.isPhysical() || ghosts.size() < config.maxActiveRagdolls && RagdollAnimator.needsGhost(ragdoll.profile, config, ragdoll.age(tick, 0.0F))) {
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
		} else {
			ragdoll.discard();
		}
	}

	public void tick(ClientLevel tickedLevel) {
		if (tickedLevel != level) {
			clear();
			level = tickedLevel;
		}
		tick++;
		RagdollConfig config = ConfigStore.get();
		deathCamera(config);

		active.values().removeIf(ragdoll -> {
			boolean gone = ragdoll.entity.isRemoved() || !ragdoll.entity.isDeadOrDying() || ragdoll.age(tick, 0.0F) > STALE_AFTER_TICKS;
			if (gone) {
				ragdoll.discard();
			}
			return gone;
		});
		for (ActiveRagdoll ragdoll : active.values()) {
			ragdoll.driveMuscles(tick, config);
		}
		for (ActiveRagdoll ghost : ghosts.values()) {
			ghost.driveMuscles(tick, config);
		}
		PhysicsHost.INSTANCE.tick();
		for (ActiveRagdoll ragdoll : active.values()) {
			ragdoll.checkKnockedOff(tick, config);
			ragdoll.step(tick, config);
			if (ragdoll.isFinished(config, tick)) {
				ragdoll.vanish();
			}
		}
		ghosts.values().removeIf(ghost -> {
			ghost.step(tick, config);
			if (!ghost.isFinished(config, tick)) {
				return false;
			}
			ghost.vanish();
			return true;
		});
	}

	private net.minecraft.client.CameraType viewBeforeDeath;

	/**
	 * The death camera: while you are dead the view is third person, so your own body is drawn and you watch it
	 * fall; it goes back to how you had it when you respawn.
	 */
	private void deathCamera(RagdollConfig config) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		boolean dead = minecraft.player.isDeadOrDying();
		if (dead && viewBeforeDeath == null && config.deathCamera && config.enabled && config.playerRagdolls) {
			viewBeforeDeath = minecraft.options.getCameraType();
			minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		} else if (!dead && viewBeforeDeath != null) {
			minecraft.options.setCameraType(viewBeforeDeath);
			viewBeforeDeath = null;
		}
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

	/** The box a living entity's ragdoll is culled by while it is being animated, or null to use its hitbox. */
	public AABB renderBounds(LivingEntity entity) {
		ActiveRagdoll ragdoll = active.get(entity.getId());
		return ragdoll == null || ragdoll.entity != entity ? null : ragdoll.renderBounds();
	}

	public void clear() {
		active.clear();
		ghosts.clear();
		PhysicsHost.INSTANCE.clear();
		RecentExplosions.clear();
	}

	/**
	 * For the visual test: one line per physics body saying whether it has come to rest and whether any part's
	 * centre is inside a block.
	 */
	public java.util.List<String> physicsReport() {
		java.util.List<String> lines = new java.util.ArrayList<>();
		for (PhysicsRagdoll ragdoll : PhysicsHost.INSTANCE.ragdolls()) {
			var torso = ragdoll.bodies[ragdoll.torso];
			lines.add(String.format(java.util.Locale.ROOT, "%s parts=%d asleep=%b speed=%.3f inside=%d failed=%b thrown=%.1f blocks torso y %+.2f v=(%.1f %.1f %.1f) at (%.1f %.1f %.1f) (%s)",
					EntityFilter.idOf(ragdoll.entity.getType()), ragdoll.bodies.length, ragdoll.island.asleep, ragdoll.maxSpeed(), ragdoll.partsInsideBlocks(),
					ragdoll.failed, Math.hypot(torso.x - ragdoll.startX, torso.z - ragdoll.startZ), torso.y - ragdoll.startY, torso.vx, torso.vy, torso.vz, torso.x, torso.y, torso.z, ragdoll.blow) + ragdoll.insideReport() + (ragdoll.special != null ? ragdoll.shapeReport() : ""));
		}
		return lines;
	}

	/** Makes room for one more physics body by letting the oldest one go. */
	private void makeRoomForBody(RagdollConfig config) {
		while (PhysicsHost.INSTANCE.count() >= config.maxBodies) {
			ActiveRagdoll oldest = null;
			for (ActiveRagdoll ghost : ghosts.values()) {
				if (ghost.isPhysical() && (oldest == null || ghost.startTick() < oldest.startTick())) {
					oldest = ghost;
				}
			}
			if (oldest == null) {
				return;
			}
			oldest.vanish();
			ghosts.remove(oldest.entity);
		}
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
		ActiveRagdoll ragdoll = new ActiveRagdoll(entity, ProfileFactory.create((LivingEntityRenderer<?, ?, ?>) renderer, entity, config), tick, config);
		active.put(entity.getId(), ragdoll);
		if (ragdoll.isPhysical()) {
			makeRoomForBody(config);
		}
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
		ActiveRagdoll dropped = active.remove(entity.getId());
		if (dropped != null) {
			dropped.discard();
		}
		dropped = ghosts.remove(entity);
		if (dropped != null) {
			dropped.discard();
		}
		RagdollRenderData.set(state, null, null);
		RagdollRenderData.setPhysics(state, null);
		markFailed(entity.getType(), error);
	}

	private void markFailed(EntityType<?> type, RuntimeException error) {
		if (failedTypes.add(type)) {
			RagdollDeathsClient.LOGGER.error("Disabling ragdolls for {} after an error; it will use the vanilla death instead", EntityFilter.idOf(type), error);
		}
	}
}
