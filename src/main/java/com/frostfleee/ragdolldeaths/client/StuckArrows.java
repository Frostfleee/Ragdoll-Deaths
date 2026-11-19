package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.ArrowPlacement;
import com.frostfleee.ragdolldeaths.core.RagdollConfig;
import com.frostfleee.ragdolldeaths.mixin.ModelPartAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The arrows stuck in a mob. The game counts them for every living thing and tells every client, but draws them only on
 * players; this draws them on the other mobs, dead and, with the second setting, alive.
 *
 * <p>They are placed on the model as it is posed for this frame, so on a ragdoll they follow the part they are in.
 */
public final class StuckArrows {
	/** How many arrows were drawn on dead and on living mobs; the self-test reads these. */
	public static long deadDraws, livingDraws;
	/** How many things stuck in a body were placed on the lower half of a bent limb; the self-test reads this. */
	public static long lowerHalfPlacements;

	/** Draws one arrow at the pose's origin, aimed by the two angles. Each game version does this its own way. */
	@FunctionalInterface
	public interface Sink {
		void draw(PoseStack pose, float yRot, float xRot);
	}

	/** One cube of the model and the parts leading to it, from the root down. */
	private record Box(ModelPart[] chain, ModelPart.Cube cube) {
	}

	private StuckArrows() {
	}

	/** How many arrows to draw on this mob. Players and mannequins are left out: the game draws theirs itself. */
	public static int shownOn(LivingEntity entity) {
		int count = entity.getArrowCount();
		if (count <= 0) {
			return 0;
		}
		RagdollConfig config = ConfigStore.get();
		boolean dead = entity.deathTime > 0 || entity.isDeadOrDying();
		if (!config.enabled || !config.stuckArrows || !dead && !config.stuckArrowsOnLiving || entity.isInvisible() || entity instanceof Player) {
			return 0;
		}
		String id = EntityFilter.idOf(entity.getType());
		if (id.equals("minecraft:mannequin") || config.isExcluded(id) || EntityFilter.keepsVanillaDeath(entity)) {
			return 0;
		}
		return Math.min(count, ArrowPlacement.MAX_ARROWS);
	}

	/** Puts what is needed on the render state, while the entity is at hand. */
	static void note(EntityRenderState state, LivingEntity entity) {
		RagdollRenderData.setArrows(state, shownOn(entity), entity.getId(), entity.deathTime > 0 || entity.isDeadOrDying());
	}

	/** Turns the pose the way the game turns a stuck arrow's model. */
	public static void turn(PoseStack pose, float yRot, float xRot) {
		PoseStack.Pose last = pose.last();
		float y = (float) Math.toRadians(yRot - 90.0F), z = (float) Math.toRadians(xRot);
		last.pose().rotateY(y).rotateZ(z);
		last.normal().rotateY(y).rotateZ(z);
	}

	/** @param pose the matrices the mob's model is drawn with */
	public static void draw(Model model, int count, int seed, boolean dead, PoseStack pose, Sink sink) {
		List<Box> boxes = new ArrayList<>();
		collect(Compat.root(model), new ModelPart[0], boxes);
		double[][] sizes = new double[boxes.size()][];
		for (int i = 0; i < sizes.length; i++) {
			ModelPart.Cube c = boxes.get(i).cube;
			sizes[i] = new double[] {c.maxX - c.minX, c.maxY - c.minY, c.maxZ - c.minZ};
		}
		for (ArrowPlacement.Place place : ArrowPlacement.pick(seed, sizes, count)) {
			Box box = boxes.get(place.box());
			ModelPart.Cube c = box.cube;
			float x = c.minX + (float) place.x() * (c.maxX - c.minX);
			float y = c.minY + (float) place.y() * (c.maxY - c.minY);
			float z = c.minZ + (float) place.z() * (c.maxZ - c.minZ);
			pose.pushPose();
			for (ModelPart part : box.chain) {
				part.translateAndRotate(pose);
			}
			if (!intoLowerHalf(pose, box.chain[box.chain.length - 1], x / 16.0F, y / 16.0F, z / 16.0F)) {
				pose.translate(x / 16.0F, y / 16.0F, z / 16.0F);
			}
			sink.draw(pose, place.yRot(), place.xRot());
			pose.popPose();
			if (dead) {
				deadDraws++;
			} else {
				livingDraws++;
			}
		}
	}

	/**
	 * For a spot in the lower half of a limb bent at a knee or an elbow: moves the pose there, through that half, so what
	 * is stuck in it follows the bend. The game's own stuck arrows and bee stingers on players come through here too.
	 *
	 * @param part the limb, with the pose already in its space
	 * @param x the spot in that space, in blocks; likewise {@code y} and {@code z}
	 * @return false when the spot is not in such a half, and the pose was left alone
	 */
	public static boolean intoLowerHalf(PoseStack pose, ModelPart part, float x, float y, float z) {
		if (!part.skipDraw) {
			return false;
		}
		ModelPart lower = ((ModelPartAccessor) (Object) part).ragdolldeaths$children().get(LimbSplitter.LOWER);
		if (lower == null || !lower.visible) {
			return false;
		}
		Rest cut = Rest.of(lower);
		if (y * 16.0F <= cut.y()) {
			return false;
		}
		lower.translateAndRotate(pose);
		pose.translate(x - cut.x() / 16.0F, y - cut.y() / 16.0F, z - cut.z() / 16.0F);
		lowerHalfPlacements++;
		return true;
	}

	/** Every cube that is drawn, in the model's own order. The halves of a cut limb count as the limb they came from. */
	private static void collect(ModelPart part, ModelPart[] above, List<Box> boxes) {
		if (!part.visible || part.xScale == 0.0F || part.yScale == 0.0F || part.zScale == 0.0F) {
			return;
		}
		ModelPart[] chain = java.util.Arrays.copyOf(above, above.length + 1);
		chain[above.length] = part;
		ModelPartAccessor access = (ModelPartAccessor) (Object) part;
		Map<String, ModelPart> children = access.ragdolldeaths$children();
		if (!part.skipDraw || children.containsKey(LimbSplitter.UPPER)) {
			for (ModelPart.Cube cube : access.ragdolldeaths$cubes()) {
				boxes.add(new Box(chain, cube));
			}
		}
		for (Map.Entry<String, ModelPart> child : children.entrySet()) {
			if (!child.getKey().startsWith("ragdolldeaths_")) {
				collect(child.getValue(), chain, boxes);
			}
		}
	}
}
