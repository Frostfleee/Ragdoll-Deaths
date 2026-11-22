package net.minecraft.client.renderer.entity.state;

import com.frostfleee.ragdolldeaths.client.RagdollRenderData;
import com.frostfleee.ragdolldeaths.core.JointPose;
import com.frostfleee.ragdolldeaths.core.RagdollPose;
import net.minecraft.world.entity.Entity;

/**
 * Ragdoll Deaths' own stand-in, for Minecraft 1.21 and 1.21.1 only: those versions draw an entity straight from the
 * entity, while the mod's shared code works on the render state the game has since 1.21.2. This class carries the few
 * values the mod reads and changes (filled in by RagdollStates); the mod's 1.21 hooks hand them back to the renderer.
 * The game itself never sees this class.
 */
public class EntityRenderState implements RagdollRenderData.Holder {
	public Entity entity;
	public float partialTicks;
	public double x;
	public double y;
	public double z;
	public float ageInTicks;
	/** Set by Compat.hideShadow: the entity's shadow is not drawn under its body. */
	public boolean hideShadow;

	private RagdollPose pose;
	private JointPose joints;
	private Object physics;
	private int light = -1;
	private int arrows;
	private int arrowSeed;
	private boolean arrowsOnDead;
	private int wounds;

	@Override
	public int ragdolldeaths$wounds() {
		return wounds;
	}

	@Override
	public void ragdolldeaths$setWounds(int wounds) {
		this.wounds = wounds;
	}

	@Override
	public int ragdolldeaths$arrows() {
		return arrows;
	}

	@Override
	public int ragdolldeaths$arrowSeed() {
		return arrowSeed;
	}

	@Override
	public boolean ragdolldeaths$arrowsOnDead() {
		return arrowsOnDead;
	}

	@Override
	public void ragdolldeaths$setArrows(int count, int seed, boolean dead) {
		arrows = count;
		arrowSeed = seed;
		arrowsOnDead = dead;
	}

	@Override
	public RagdollPose ragdolldeaths$pose() {
		return pose;
	}

	@Override
	public JointPose ragdolldeaths$joints() {
		return joints;
	}

	@Override
	public void ragdolldeaths$set(RagdollPose pose, JointPose joints) {
		this.pose = pose;
		this.joints = joints;
	}

	@Override
	public Object ragdolldeaths$physics() {
		return physics;
	}

	@Override
	public void ragdolldeaths$setPhysics(Object ragdoll) {
		physics = ragdoll;
	}

	@Override
	public int ragdolldeaths$light() {
		return light;
	}

	@Override
	public void ragdolldeaths$setLight(int light) {
		this.light = light;
	}
}
