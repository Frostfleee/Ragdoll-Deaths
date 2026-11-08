package com.frostfleee.ragdolldeaths.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The game and Fabric API calls that differ between 26.x versions. This copy is for 26.2 and later. */
public final class Compat {
	private Compat() {
	}

	public static Vec3 cameraPosition() {
		return Minecraft.getInstance().gameRenderer.mainCamera().position();
	}

	public static boolean isCubeMob(LivingEntity entity) {
		return entity instanceof AbstractCubeMob;
	}

	public static boolean isMagmaCube(LivingEntity entity) {
		return entity instanceof MagmaCube;
	}

	public static Block shulkerBox(DyeColor color) {
		return color == null ? Blocks.SHULKER_BOX : Blocks.DYED_SHULKER_BOX.pick(color);
	}

	public static void addWide(OptionsList list, AbstractWidget widget) {
		list.addBig(widget);
	}

	public static void setScreen(Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}
}
