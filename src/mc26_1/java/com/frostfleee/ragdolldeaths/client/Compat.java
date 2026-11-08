package com.frostfleee.ragdolldeaths.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The game and Fabric API calls that differ between 26.x versions. This copy is for 26.1.x. */
public final class Compat {
	private Compat() {
	}

	public static Vec3 cameraPosition() {
		return Minecraft.getInstance().gameRenderer.getMainCamera().position();
	}

	public static boolean isCubeMob(LivingEntity entity) {
		return entity instanceof Slime;
	}

	public static boolean isMagmaCube(LivingEntity entity) {
		return entity instanceof MagmaCube;
	}

	public static Block shulkerBox(DyeColor color) {
		if (color == null) {
			return Blocks.SHULKER_BOX;
		}
		return switch (color) {
			case WHITE -> Blocks.WHITE_SHULKER_BOX;
			case ORANGE -> Blocks.ORANGE_SHULKER_BOX;
			case MAGENTA -> Blocks.MAGENTA_SHULKER_BOX;
			case LIGHT_BLUE -> Blocks.LIGHT_BLUE_SHULKER_BOX;
			case YELLOW -> Blocks.YELLOW_SHULKER_BOX;
			case LIME -> Blocks.LIME_SHULKER_BOX;
			case PINK -> Blocks.PINK_SHULKER_BOX;
			case GRAY -> Blocks.GRAY_SHULKER_BOX;
			case LIGHT_GRAY -> Blocks.LIGHT_GRAY_SHULKER_BOX;
			case CYAN -> Blocks.CYAN_SHULKER_BOX;
			case PURPLE -> Blocks.PURPLE_SHULKER_BOX;
			case BLUE -> Blocks.BLUE_SHULKER_BOX;
			case BROWN -> Blocks.BROWN_SHULKER_BOX;
			case GREEN -> Blocks.GREEN_SHULKER_BOX;
			case RED -> Blocks.RED_SHULKER_BOX;
			case BLACK -> Blocks.BLACK_SHULKER_BOX;
		};
	}

	/** 26.1 has no full-width widget row; a lone small entry keeps the widget's own width. */
	public static void addWide(OptionsList list, AbstractWidget widget) {
		list.addSmall(widget, null);
	}

	public static void setScreen(Screen screen) {
		Minecraft.getInstance().setScreen(screen);
	}
}
