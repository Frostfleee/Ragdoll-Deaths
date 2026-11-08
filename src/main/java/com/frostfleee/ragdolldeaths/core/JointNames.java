package com.frostfleee.ragdolldeaths.core;

import com.frostfleee.ragdolldeaths.core.JointPose.Side;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Vanilla and most modded models share one part naming scheme ({@code head}, {@code right_arm},
 * {@code left_hind_leg}, {@code tentacle3}, ...), so joint roles are read from the name's tokens.
 */
public final class JointNames {
	public record Limb(JointRole role, Side side, float restZRot) {
	}

	private static final double ANGLED_LEG_THRESHOLD = 0.3;
	/** The blaze names its twelve rods {@code part0} to {@code part11}. */
	private static final Pattern BLAZE_ROD = Pattern.compile("part\\d+");
	private static final Pattern TAIL_SEGMENT = Pattern.compile("tail\\d+");
	private static final Pattern SPIKE = Pattern.compile("spike\\d+");

	private JointNames() {
	}

	public static String[] tokens(String name) {
		return name.toLowerCase(Locale.ROOT).split("_");
	}

	public static JointRole roleOf(String[] tokens) {
		if (tokens.length == 1 && tokens[0].equals("mouth")) {
			return JointRole.JAW;
		}
		boolean front = false;
		boolean hind = false;
		boolean middle = false;
		JointRole found = null;
		for (String token : tokens) {
			switch (token) {
				case "front" -> front = true;
				case "hind", "back", "rear" -> hind = true;
				case "mid", "middle" -> middle = true;
				default -> {
				}
			}
			JointRole role = tokenRole(token);
			if (role != null && (found == null || priority(role) > priority(found))) {
				found = role;
			}
		}
		if (found == JointRole.LEG) {
			return middle ? JointRole.MID_LEG : front ? JointRole.FRONT_LEG : hind ? JointRole.HIND_LEG : JointRole.LEG;
		}
		return found;
	}

	public static Side sideOf(String[] tokens) {
		for (String token : tokens) {
			if (token.equals("right")) {
				return Side.RIGHT;
			}
			if (token.equals("left")) {
				return Side.LEFT;
			}
		}
		return Side.CENTER;
	}

	public static Rig classify(List<Limb> limbs) {
		Map<JointRole, Integer> counts = new EnumMap<>(JointRole.class);
		int legs = 0;
		double angled = 0.0;
		int sidedLegs = 0;
		for (Limb limb : limbs) {
			counts.merge(limb.role(), 1, Integer::sum);
			if (limb.role().isLeg()) {
				legs++;
				angled += Math.abs(limb.restZRot());
				if (limb.role() == JointRole.LEG && limb.side() != Side.CENTER) {
					sidedLegs++;
				}
			}
		}

		if (legs >= 6 && angled / legs > ANGLED_LEG_THRESHOLD) {
			return Rig.ARTHROPOD;
		}
		if (counts.getOrDefault(JointRole.ARM, 0) >= 1 && sidedLegs >= 2) {
			return Rig.BIPED;
		}
		if (counts.getOrDefault(JointRole.FRONT_LEG, 0) >= 2 && counts.getOrDefault(JointRole.HIND_LEG, 0) >= 2) {
			return Rig.QUADRUPED;
		}
		if (counts.getOrDefault(JointRole.WING, 0) >= 2) {
			return Rig.WINGED;
		}
		if (counts.getOrDefault(JointRole.TENTACLE, 0) >= 3) {
			return Rig.TENTACLED;
		}
		if (counts.getOrDefault(JointRole.FIN, 0) >= 1) {
			return Rig.FINNED;
		}
		return Rig.OTHER;
	}

	private static JointRole tokenRole(String token) {
		if (token.startsWith("tentacle")) {
			return JointRole.TENTACLE;
		}
		if (BLAZE_ROD.matcher(token).matches()) {
			return JointRole.ROD;
		}
		if (TAIL_SEGMENT.matcher(token).matches()) {
			return JointRole.TAIL_SEGMENT;
		}
		if (SPIKE.matcher(token).matches()) {
			return JointRole.SPIKE;
		}
		return switch (token) {
			case "head" -> JointRole.HEAD;
			case "neck" -> JointRole.NECK;
			case "body", "torso" -> JointRole.BODY;
			case "arm", "arms" -> JointRole.ARM;
			case "leg", "legs" -> JointRole.LEG;
			case "wing" -> JointRole.WING;
			case "tail" -> JointRole.TAIL;
			case "fin" -> JointRole.FIN;
			case "ear", "ears" -> JointRole.EAR;
			case "jaw" -> JointRole.JAW;
			case "eye", "eyes", "lid" -> JointRole.EYE;
			case "rod" -> JointRole.ROD;
			case "wind" -> JointRole.WIND;
			default -> null;
		};
	}

	/** In compound names such as {@code tail_fin} or {@code wing_base} the more specific role wins. */
	private static int priority(JointRole role) {
		return switch (role) {
			case EYE -> 6;
			case FIN, TENTACLE -> 5;
			case WING, EAR, JAW -> 4;
			case ARM, LEG, FRONT_LEG, HIND_LEG, MID_LEG, ROD, WIND -> 3;
			case TAIL, TAIL_SEGMENT, SPIKE, HEAD, NECK -> 2;
			case BODY -> 1;
		};
	}
}
