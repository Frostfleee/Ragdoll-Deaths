package com.frostfleee.ragdolldeaths.client;

import net.minecraft.world.phys.Vec3;

import net.minecraft.core.BlockPos;

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

	public void onStateCreated(EntityRenderer renderer, Entity entity, EntityRenderState state, float partialTicks) {
		RagdollRenderData.clear(state);
		if (!(entity instanceof LivingEntity living)) {
			return;
		}
		StuckArrows.note(state, living);
		Wounds.note(state, living);
		if (living.deathTime <= 0) {
			return;
		}
		if (!(state instanceof LivingEntityRenderState livingState) || !(renderer instanceof LivingEntityRenderer)) {
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
		if (keepsDeadPlayer(ragdoll)) {
			ragdoll.keepAsGhost();
		}
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
			// A dead player is not removed until they respawn, so their body never turns into a ghost: it is not stale,
			// and lasts its lifetime (isFinished) or until the respawn.
			boolean stale = ragdoll.age(tick, 0.0F) > STALE_AFTER_TICKS && !(ragdoll.entity instanceof net.minecraft.world.entity.player.Player);
			boolean gone = ragdoll.entity.isRemoved() || !ragdoll.entity.isDeadOrDying() || stale;
			if (gone && ragdoll.entity.isRemoved() && (ragdoll.keptAsGhost() || keepsDeadPlayer(ragdoll))) {
				// removed with no unload event yet (or none at all, for the dead local player on 1.21.x): kept, as onUnload would
				ragdoll.keepAsGhost();
				ragdoll.becomeGhost(tick);
				ghosts.put(ragdoll.entity, ragdoll);
			} else if (gone) {
				ragdoll.discard();
			}
			return gone;
		});
		for (ActiveRagdoll ragdoll : active.values()) {
			ragdoll.driveReflexes(tick, config);
		}
		for (ActiveRagdoll ghost : ghosts.values()) {
			ghost.driveReflexes(tick, config);
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

	/** Where a dead entity's physics body has its head on the frame being drawn, or null if it has none. */
	public net.minecraft.world.phys.Vec3 bodyHead(LivingEntity entity, float partialTicks) {
		ActiveRagdoll ragdoll = find(entity);
		return ragdoll == null ? null : ragdoll.physicsHead(partialTicks);
	}

	/** The ghost bodies to draw in this level, for versions that draw entities from a list instead of extracting them. */
	public java.util.List<LivingEntity> ghostEntities(ClientLevel drawnLevel) {
		java.util.List<LivingEntity> out = new java.util.ArrayList<>();
		RagdollConfig config = ConfigStore.get();
		for (ActiveRagdoll ghost : ghosts.values()) {
			if (ghost.entity.level() == drawnLevel && EntityFilter.withinRenderDistance(ghost.drawnPosition(), config)) {
				out.add(ghost.entity);
			}
		}
		return out;
	}

	/**
	 * A dead player is removed without the death poof on some versions (1.21.x), so it is never marked to be kept; a
	 * physics body of theirs is kept all the same, as a mob's would be, and so is any body of theirs that is to stay until
	 * they respawn.
	 */
	private static boolean keepsDeadPlayer(ActiveRagdoll ragdoll) {
		return !ragdoll.keptAsGhost() && ragdoll.entity instanceof net.minecraft.world.entity.player.Player && ragdoll.entity.isDeadOrDying()
				&& (ragdoll.isPhysical() || ragdoll.keptUntilRespawn(ConfigStore.get()));
	}

	/** Whether the entity is being drawn as a ragdoll right now (an animated or physics body, alive or as a ghost). */
	public boolean hasBody(LivingEntity entity) {
		return entity.deathTime > 0 && find(entity) != null;
	}

	/** Whether the entity with this UUID is being drawn as a ragdoll right now. */
	public boolean hasBody(java.util.UUID uuid) {
		for (ActiveRagdoll ragdoll : active.values()) {
			if (ragdoll.entity.getUUID().equals(uuid)) {
				return true;
			}
		}
		for (ActiveRagdoll ghost : ghosts.values()) {
			if (ghost.entity.getUUID().equals(uuid)) {
				return true;
			}
		}
		return false;
	}

	/** For the self-test: what the tracker holds for an entity. */
	String describe(LivingEntity entity) {
		ActiveRagdoll ragdoll = find(entity);
		return (ragdoll == null ? "no body" : ragdoll.isPhysical() ? "physics body" : "animated body") + ", eligible " + EntityFilter.isEligible(entity, ConfigStore.get())
				+ ", deathTime " + entity.deathTime + ", removed " + entity.isRemoved() + ", failed type " + failedTypes.contains(entity.getType());
	}

	/** Ticks counted by the tracker, for anything that moves smoothly over time. */
	public long ticks() {
		return tick;
	}

	/** Adds the ghost bodies to the frame being extracted; each loader calls this at the end of level extraction. */
	public void extractGhosts(ClientLevel extractedLevel, java.util.List<EntityRenderState> output, float partialTicks) {
		if (ghosts.isEmpty()) {
			return;
		}
		RagdollConfig config = ConfigStore.get();
		Iterator<ActiveRagdoll> iterator = ghosts.values().iterator();
		while (iterator.hasNext()) {
			ActiveRagdoll ghost = iterator.next();
			if (ghost.entity.level() != extractedLevel || !EntityFilter.withinRenderDistance(ghost.drawnPosition(), config)) {
				continue;
			}
			try {
				EntityRenderState state = Compat.extractState(ghost.entity, partialTicks);
				if (RagdollRenderData.pose(state) != null) {
					output.add(state);
				}
			} catch (RuntimeException e) {
				iterator.remove();
				markFailed(ghost.entity.getType(), e);
			}
		}
	}

	/** The box a living entity's ragdoll is culled by while it is being animated, or null to use its hitbox. */
	public AABB renderBounds(LivingEntity entity) {
		// A ghost (a removed mob whose body lingers) counts too: on 1.21.x it is culled like any entity.
		ActiveRagdoll ragdoll = entity.isRemoved() ? ghosts.get(entity) : active.get(entity.getId());
		return ragdoll == null || ragdoll.entity != entity ? null : ragdoll.renderBounds();
	}

	public void clear() {
		active.clear();
		ghosts.clear();
		PhysicsHost.INSTANCE.clear();
		RecentExplosions.clear();
		Wounds.clear();
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
		while (PhysicsHost.INSTANCE.count() >= config.maxActiveRagdolls) {
			ActiveRagdoll oldest = null;
			for (ActiveRagdoll ghost : ghosts.values()) {
				// a player's body kept until they respawn is not let go to make room
				if (ghost.isPhysical() && !ghost.keptUntilRespawn(config) && (oldest == null || ghost.startTick() < oldest.startTick())) {
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

	private ActiveRagdoll capture(EntityRenderer renderer, LivingEntity entity) {
		RagdollConfig config = ConfigStore.get();
		if (entity.isRemoved() || !entity.isDeadOrDying() || active.size() >= config.maxActiveRagdolls) {
			return null;
		}
		if (failedTypes.contains(entity.getType()) || !EntityFilter.isEligible(entity, config)) {
			return null;
		}
		ActiveRagdoll ragdoll = new ActiveRagdoll(entity, ProfileFactory.create((LivingEntityRenderer) renderer, entity, config), tick, config);
		active.put(entity.getId(), ragdoll);
		if (ragdoll.isPhysical()) {
			makeRoomForBody(config);
		}
		if (ragdoll.profile.kind() == BodyKind.SHULKER && entity instanceof Shulker shulker && !ragdoll.isPhysical()) {
			// The classic death: the shulker breaks at once. A physics shulker tumbles first and breaks when it goes.
			breakShell(shulker, shulker.position());
		}
		return ragdoll;
	}

	/**
	 * The shulker breaks apart like a shulker box of its color: the box's own break particles, in the same spots and
	 * number, and the sound of the box breaking, heard only by you.
	 */
	static void breakShell(Shulker shulker, Vec3 where) {
		DyeColor color = shulker.getColor();
		net.minecraft.world.level.block.state.BlockState box = Compat.shulkerBox(color).defaultBlockState();
		shulker.level().addDestroyBlockEffect(BlockPos.containing(where), box);
		if (ConfigStore.get().impactSounds) {
			net.minecraft.world.level.block.SoundType sound = box.getSoundType();
			shulker.level().playLocalSound(where.x, where.y, where.z, sound.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS,
					(sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F, false);
		}
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
