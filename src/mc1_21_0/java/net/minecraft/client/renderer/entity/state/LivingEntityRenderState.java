package net.minecraft.client.renderer.entity.state;

/** See EntityRenderState: Ragdoll Deaths' stand-in for 1.21 and 1.21.1, with the values of a living entity. */
public class LivingEntityRenderState extends EntityRenderState {
	public float bodyRot;
	/** The head's turn relative to the body, in degrees. */
	public float yRot;
	public float xRot;
	public boolean hasRedOverlay;
	public float walkAnimationPos;
	public float walkAnimationSpeed;
}
