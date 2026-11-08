package com.frostfleee.ragdolldeaths.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.frostfleee.ragdolldeaths.core.JointPose.Side;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class JointNamesTest {
	@ParameterizedTest
	@CsvSource({
			"head, HEAD, CENTER",
			"right_arm, ARM, RIGHT",
			"left_leg, LEG, LEFT",
			"right_hind_leg, HIND_LEG, RIGHT",
			"left_front_leg, FRONT_LEG, LEFT",
			"right_middle_front_leg, MID_LEG, RIGHT",
			"left_mid_leg, MID_LEG, LEFT",
			"back_legs, HIND_LEG, CENTER",
			"right_wing_base, WING, RIGHT",
			"tail_fin, FIN, CENTER",
			"back_fin, FIN, CENTER",
			"tentacle7, TENTACLE, CENTER",
			"left_ear, EAR, LEFT",
			"right_eye, EYE, RIGHT",
			"head_parts, HEAD, CENTER",
			"arms, ARM, CENTER",
			"mouth, JAW, CENTER",
			"part0, ROD, CENTER",
			"part11, ROD, CENTER",
			"rod_2, ROD, CENTER",
			"wind_body, WIND, CENTER"
	})
	void readsRoleAndSideFromPartNames(String name, JointRole role, Side side) {
		String[] tokens = JointNames.tokens(name);
		assertEquals(role, JointNames.roleOf(tokens));
		assertEquals(side, JointNames.sideOf(tokens));
	}

	@Test
	void unrelatedPartsHaveNoRole() {
		assertNull(JointNames.roleOf(JointNames.tokens("hat")));
		assertNull(JointNames.roleOf(JointNames.tokens("beard")));
		assertNull(JointNames.roleOf(JointNames.tokens("nose")));
		assertNull(JointNames.roleOf(JointNames.tokens("feet")));
		assertNull(JointNames.roleOf(JointNames.tokens("rods")), "a group of rods is not a rod, so each rod inside moves on its own");
		assertNull(JointNames.roleOf(JointNames.tokens("upper_mouth")), "a horse's upper lip is not a jaw");
	}

	@Test
	void classifiesVanillaBodyPlans() {
		assertEquals(Rig.BIPED, rig(0.0F, "head", "body", "right_arm", "left_arm", "right_leg", "left_leg"));
		assertEquals(Rig.BIPED, rig(0.0F, "head", "body", "arms", "right_leg", "left_leg"));
		assertEquals(Rig.QUADRUPED, rig(0.0F, "head", "body", "right_hind_leg", "left_hind_leg", "right_front_leg", "left_front_leg"));
		assertEquals(Rig.ARTHROPOD, rig(0.7F, "head", "right_hind_leg", "left_hind_leg", "right_middle_hind_leg", "left_middle_hind_leg",
				"right_middle_front_leg", "left_middle_front_leg", "right_front_leg", "left_front_leg"));
		assertEquals(Rig.QUADRUPED, rig(0.0F, "head", "body", "right_front_leg", "left_front_leg", "right_mid_leg", "left_mid_leg",
				"right_hind_leg", "left_hind_leg"));
		assertEquals(Rig.WINGED, rig(0.0F, "head", "body", "right_wing", "left_wing", "right_leg", "left_leg"));
		assertEquals(Rig.WINGED, rig(0.0F, "body", "front_legs", "middle_legs", "back_legs", "right_wing", "left_wing"));
		assertEquals(Rig.TENTACLED, rig(0.0F, "body", "tentacle0", "tentacle1", "tentacle2", "tentacle3"));
		assertEquals(Rig.FINNED, rig(0.0F, "body", "head", "top_fin", "tail_fin"));
		assertEquals(Rig.OTHER, rig(0.0F, "cube", "right_eye", "left_eye", "mouth"));
	}

	private static Rig rig(float legZRot, String... names) {
		List<JointNames.Limb> limbs = new ArrayList<>();
		for (String name : names) {
			String[] tokens = JointNames.tokens(name);
			JointRole role = JointNames.roleOf(tokens);
			if (role != null) {
				limbs.add(new JointNames.Limb(role, JointNames.sideOf(tokens), role.isLeg() ? legZRot : 0.0F));
			}
		}
		return JointNames.classify(limbs);
	}
}
