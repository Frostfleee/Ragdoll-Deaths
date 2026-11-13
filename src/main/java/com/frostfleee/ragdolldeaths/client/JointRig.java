package com.frostfleee.ragdolldeaths.client;

import com.frostfleee.ragdolldeaths.core.DeathProfile;
import com.frostfleee.ragdolldeaths.core.JointNames;
import com.frostfleee.ragdolldeaths.core.JointPose.Side;
import com.frostfleee.ragdolldeaths.core.JointRole;
import com.frostfleee.ragdolldeaths.core.Rig;
import com.frostfleee.ragdolldeaths.mixin.ModelPartAccessor;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;

/** Maps a model's named parts to joint roles and caches the result per model. */
final class JointRig {
	/**
	 * {@code groundY} is the ground's height in the part's parent space; {@code length} is how far the part's cubes
	 * reach below its pivot and {@code minZ} how far they reach toward its front, both in model pixels.
	 * {@code pivotX, pivotY, pivotZ} is the joint's resting pivot in the model's root space and {@code top} how far
	 * its cubes reach above the pivot (a head's), in model pixels.
	 */
	record Joint(ModelPart part, JointRole role, Side side, int index, float outwardX, float outwardZ, float groundY, float length, float minZ,
			float pivotX, float pivotY, float pivotZ, float top) {
	}

	private static final Map<ModelPart, JointRig> CACHE = new WeakHashMap<>();
	private static final float PIVOT_EPSILON = 1.0E-3F;
	private static final float MODEL_GROUND_Y = 24.0F;

	static final JointRig EMPTY = new JointRig(List.of(), Rig.OTHER, DeathProfile.DEFAULT_LEG_RATIO, Double.NaN, 0.0F);

	final List<Joint> joints;
	final Rig rig;
	final double legRatio;
	/** Half the torso's side-to-side width as a fraction of model height, or NaN when the model has no body part. */
	final double bodyHalfWidthRatio;
	/** The model's height in model pixels, from its lowest cube to its highest. */
	final float modelHeight;

	private JointRig(List<Joint> joints, Rig rig, double legRatio, double bodyHalfWidthRatio, float modelHeight) {
		this.joints = joints;
		this.rig = rig;
		this.legRatio = legRatio;
		this.bodyHalfWidthRatio = bodyHalfWidthRatio;
		this.modelHeight = modelHeight;
	}

	static JointRig of(ModelPart root) {
		return CACHE.computeIfAbsent(root, JointRig::build);
	}

	static void disable(ModelPart root) {
		CACHE.put(root, EMPTY);
	}

	private static JointRig build(ModelPart root) {
		List<Joint> joints = new ArrayList<>();
		Map<JointRole, Integer> counts = new EnumMap<>(JointRole.class);
		collect(root, joints, counts, null, 0.0F, 0.0F, 0.0F);
		float height = modelHeight(root);
		return new JointRig(List.copyOf(joints), classify(joints), legRatio(joints, height), bodyHalfWidthRatio(joints, height), height);
	}

	private static void collect(ModelPart parent, List<Joint> joints, Map<JointRole, Integer> counts, JointRole container, float parentX, float parentY, float parentZ) {
		Map<String, ModelPart> children = ((ModelPartAccessor) (Object) parent).ragdolldeaths$children();
		List<Joint> assignedHere = new ArrayList<>();
		List<ModelPart> unassigned = new ArrayList<>();

		children.forEach((name, part) -> {
			String[] tokens = JointNames.tokens(name);
			JointRole role = JointNames.roleOf(tokens);
			float childX = parentX + Rest.of(part).x();
			float childY = parentY + Rest.of(part).y();
			float childZ = parentZ + Rest.of(part).z();
			if (role == null || !allowedInside(container, role)) {
				unassigned.add(part);
				collect(part, joints, counts, container, childX, childY, childZ);
				return;
			}
			Joint joint = joint(part, role, JointNames.sideOf(tokens), counts, MODEL_GROUND_Y - parentY, childX, childY, childZ);
			joints.add(joint);
			assignedHere.add(joint);
			collect(part, joints, counts, role == JointRole.BODY ? container : role, childX, childY, childZ);
		});

		if (container == null) {
			adoptCoPivoted(unassigned, assignedHere, joints, counts);
		}
	}

	/** Parts nested in a limb move with it; only a few sub-joints, like ears on a head, get their own motion. */
	private static boolean allowedInside(JointRole container, JointRole role) {
		if (container == null || role == JointRole.EYE) {
			return true;
		}
		return switch (container) {
			case HEAD -> role == JointRole.EAR || role == JointRole.JAW || role == JointRole.SPIKE || role == JointRole.TAIL_SEGMENT;
			case TAIL_SEGMENT -> role == JointRole.TAIL_SEGMENT;
			case NECK -> role == JointRole.HEAD || role == JointRole.EAR || role == JointRole.JAW;
			default -> false;
		};
	}

	/** Parts that share a joint's pivot (a chicken's beak and wattle beside its head) move with that joint. */
	private static void adoptCoPivoted(List<ModelPart> unassigned, List<Joint> assigned, List<Joint> joints, Map<JointRole, Integer> counts) {
		for (ModelPart part : unassigned) {
			Rest pose = Rest.of(part);
			for (Joint joint : assigned) {
				Rest other = Rest.of(joint.part());
				if (joint.role() != JointRole.EYE && samePivot(pose, other) && sameRotation(pose, other)) {
					float[] reach = reach(part);
					joints.add(new Joint(part, joint.role(), joint.side(), joint.index(), joint.outwardX(), joint.outwardZ(), joint.groundY(), reach[0], reach[1],
							joint.pivotX(), joint.pivotY(), joint.pivotZ(), reach[2]));
					break;
				}
			}
		}
	}

	private static Joint joint(ModelPart part, JointRole role, Side side, Map<JointRole, Integer> counts, float groundY, float pivotX, float pivotY, float pivotZ) {
		int index = counts.merge(role, 1, Integer::sum) - 1;
		Rest pose = Rest.of(part);
		float length = (float) Math.hypot(pose.x(), pose.z());
		float outwardX = length > PIVOT_EPSILON ? pose.x() / length : 0.0F;
		float outwardZ = length > PIVOT_EPSILON ? pose.z() / length : 1.0F;
		float[] reach = reach(part);
		return new Joint(part, role, side, index, outwardX, outwardZ, groundY, reach[0], reach[1], pivotX, pivotY, pivotZ, reach[2]);
	}

	/** How far the part's own cubes reach below its pivot, toward its front and above its pivot. */
	private static float[] reach(ModelPart part) {
		float below = 0.0F;
		float front = 0.0F;
		float above = 0.0F;
		for (ModelPart.Cube cube : ((ModelPartAccessor) (Object) part).ragdolldeaths$cubes()) {
			below = Math.max(below, cube.maxY);
			front = Math.min(front, cube.minZ);
			above = Math.max(above, -cube.minY);
		}
		return new float[] {below, front, above};
	}

	private static Rig classify(List<Joint> joints) {
		List<JointNames.Limb> limbs = new ArrayList<>(joints.size());
		for (Joint joint : joints) {
			limbs.add(new JointNames.Limb(joint.role(), joint.side(), Rest.of(joint.part()).zRot()));
		}
		return JointNames.classify(limbs);
	}

	private static float modelHeight(ModelPart root) {
		float[] bounds = {Float.MAX_VALUE, -Float.MAX_VALUE};
		verticalBounds(root, 0.0F, bounds);
		return bounds[1] - bounds[0];
	}

	/** Leg length as a fraction of the model's height, measured from the model's own cubes. */
	private static double legRatio(List<Joint> joints, float height) {
		float leg = 0.0F;
		for (Joint joint : joints) {
			if (joint.role().isLeg()) {
				for (ModelPart.Cube cube : ((ModelPartAccessor) (Object) joint.part()).ragdolldeaths$cubes()) {
					leg = Math.max(leg, cube.maxY - cube.minY);
				}
			}
		}

		if (leg <= 0.0F || height <= 0.0F) {
			return DeathProfile.DEFAULT_LEG_RATIO;
		}
		return leg / height;
	}

	/**
	 * Many large animals have a collision box much wider than their torso, so resting them on their side by the
	 * box width leaves them floating. The torso's own width is measured from the body part's cubes instead.
	 */
	private static double bodyHalfWidthRatio(List<Joint> joints, float height) {
		float half = 0.0F;
		for (Joint joint : joints) {
			if (joint.role() == JointRole.BODY) {
				float x = Rest.of(joint.part()).x();
				for (ModelPart.Cube cube : ((ModelPartAccessor) (Object) joint.part()).ragdolldeaths$cubes()) {
					half = Math.max(half, Math.max(Math.abs(x + cube.minX), Math.abs(x + cube.maxX)));
				}
			}
		}
		return half <= 0.0F || height <= 0.0F ? Double.NaN : half / height;
	}

	private static void verticalBounds(ModelPart part, float offsetY, float[] bounds) {
		for (ModelPart.Cube cube : ((ModelPartAccessor) (Object) part).ragdolldeaths$cubes()) {
			bounds[0] = Math.min(bounds[0], offsetY + cube.minY);
			bounds[1] = Math.max(bounds[1], offsetY + cube.maxY);
		}
		for (ModelPart child : ((ModelPartAccessor) (Object) part).ragdolldeaths$children().values()) {
			verticalBounds(child, offsetY + Rest.of(child).y(), bounds);
		}
	}

	private static boolean samePivot(Rest a, Rest b) {
		return Math.abs(a.x() - b.x()) < PIVOT_EPSILON && Math.abs(a.y() - b.y()) < PIVOT_EPSILON && Math.abs(a.z() - b.z()) < PIVOT_EPSILON;
	}

	private static boolean sameRotation(Rest a, Rest b) {
		return Math.abs(a.xRot() - b.xRot()) < PIVOT_EPSILON && Math.abs(a.yRot() - b.yRot()) < PIVOT_EPSILON && Math.abs(a.zRot() - b.zRot()) < PIVOT_EPSILON;
	}
}
