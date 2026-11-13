package com.frostfleee.ragdolldeaths.forge;

import com.frostfleee.ragdolldeaths.client.RagdollDeathsClient;
import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import com.frostfleee.ragdolldeaths.client.config.RagdollConfigScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/** The Forge client half for Forge 51 to 55 (Minecraft 1.21 to 1.21.5), which still use the older event bus. */
final class ForgeClient {
	private ForgeClient() {
	}

	static void init(FMLJavaModLoadingContext context) {
		RagdollDeathsClient.init(FMLPaths.CONFIGDIR.get());
		RagdollTracker tracker = RagdollTracker.INSTANCE;
		// Forge 51 (Minecraft 1.21) has this on ModLoadingContext only.
		net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new RagdollConfigScreen(parent)));

		MinecraftForge.EVENT_BUS.addListener((TickEvent.LevelTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END && event.level instanceof ClientLevel level) {
				tracker.tick(level);
			}
		});
		MinecraftForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
			if (event.getLevel().isClientSide()) {
				tracker.onUnload(event.getEntity());
			}
		});
		MinecraftForge.EVENT_BUS.addListener((LevelEvent.Load event) -> {
			if (event.getLevel() instanceof ClientLevel) {
				RagdollDeathsClient.onLevelChange();
			}
		});
	}
}
