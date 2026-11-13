package com.frostfleee.ragdolldeaths.client;

import net.minecraft.client.renderer.entity.state.BreezeRenderState;
import net.minecraft.client.renderer.entity.state.CamelRenderState;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import net.minecraft.client.renderer.entity.state.FoxRenderState;
import net.minecraft.client.renderer.entity.state.GoatRenderState;
import net.minecraft.client.renderer.entity.state.GuardianRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.NautilusRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PandaRenderState;
import net.minecraft.client.renderer.entity.state.PolarBearRenderState;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.client.renderer.entity.state.SnifferRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;

/**
 * Clears the special poses vanilla models draw from their render state (a rearing horse, a sitting wolf, a
 * sleeping fox) so a dying animal is drawn standing before its death animation is layered on top. These models
 * build such poses in code each frame, so their stored rest pose can't be used to undo them.
 */
final class VanillaPoses {
	private VanillaPoses() {
	}

	static void neutralize(LivingEntityRenderState state) {
		// A rider thrown off its mount drops the sitting pose.
		if (state instanceof HumanoidRenderState humanoid) {
			humanoid.isPassenger = false;
		}
		if (state instanceof IllagerRenderState illager) {
			illager.isRiding = false;
		}
		switch (state) {
			case EquineRenderState equine -> {
				equine.standAnimation = 0.0F;
				equine.eatAnimation = 0.0F;
				equine.feedingAnimation = 0.0F;
				equine.animateTail = false;
			}
			case WolfRenderState wolf -> {
				wolf.isSitting = false;
				wolf.shakeAnim = 0.0F;
				wolf.headRollAngle = 0.0F;
			}
			case FelineRenderState feline -> {
				feline.isSitting = false;
				feline.isCrouching = false;
				feline.isSprinting = false;
				feline.lieDownAmount = 0.0F;
				feline.lieDownAmountTail = 0.0F;
				feline.relaxStateOneAmount = 0.0F;
			}
			case FoxRenderState fox -> {
				fox.isSitting = false;
				fox.isSleeping = false;
				fox.isCrouching = false;
				fox.isFaceplanted = false;
				fox.isPouncing = false;
				fox.crouchAmount = 0.0F;
				fox.headRollAngle = 0.0F;
			}
			case PandaRenderState panda -> {
				panda.isSitting = false;
				panda.isEating = false;
				panda.isSneezing = false;
				panda.isScared = false;
				panda.sitAmount = 0.0F;
				panda.lieOnBackAmount = 0.0F;
				panda.rollAmount = 0.0F;
			}
			case CamelRenderState camel -> {
				camel.sitAnimationState.stop();
				camel.sitPoseAnimationState.stop();
				camel.sitUpAnimationState.stop();
				camel.dashAnimationState.stop();
			}
			case SnifferRenderState sniffer -> {
				sniffer.isSearching = false;
				sniffer.diggingAnimationState.stop();
				sniffer.sniffingAnimationState.stop();
				sniffer.risingAnimationState.stop();
				sniffer.feelingHappyAnimationState.stop();
				sniffer.scentingAnimationState.stop();
			}
			case SheepRenderState sheep -> {
				sheep.headEatPositionScale = 0.0F;
				sheep.headEatAngleScale = 0.0F;
			}
			case BreezeRenderState breeze -> {
				breeze.shoot.stop();
				breeze.slide.stop();
				breeze.slideBack.stop();
				breeze.inhale.stop();
				breeze.longJump.stop();
			}
			case GuardianRenderState guardian -> {
				guardian.lookAtPosition = null;
				guardian.attackTargetPosition = null;
				guardian.tailAnimation = 0.0F;
			}
			case NautilusRenderState nautilus -> nautilus.walkAnimationSpeed = 0.0F;
			case GoatRenderState goat -> goat.rammingXHeadRot = 0.0F;
			case PolarBearRenderState bear -> bear.standScale = 0.0F;
			default -> {
			}
		}
	}
}
