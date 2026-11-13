package com.frostfleee.ragdolldeaths.forge;

import com.frostfleee.ragdolldeaths.client.RagdollDeathsClient;
import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import com.frostfleee.ragdolldeaths.client.config.RagdollConfigScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/** The Forge client half for Forge 56 to 58 (Minecraft 1.21.6 to 1.21.8): the new event bus, whose events are still classes. */
final class ForgeClient {
	private ForgeClient() {
	}

	static void init(FMLJavaModLoadingContext context) {
		RagdollDeathsClient.init(FMLPaths.CONFIGDIR.get());
		RagdollTracker tracker = RagdollTracker.INSTANCE;
		context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new RagdollConfigScreen(parent)));

		TickEvent.LevelTickEvent.Post.BUS.addListener(event -> {
			if (event.level instanceof ClientLevel level) {
				tracker.tick(level);
			}
		});
		EntityLeaveLevelEvent.BUS.addListener(event -> {
			if (event.getLevel().isClientSide()) {
				tracker.onUnload(event.getEntity());
			}
		});
		LevelEvent.Load.BUS.addListener(event -> {
			if (event.getLevel() instanceof ClientLevel) {
				RagdollDeathsClient.onLevelChange();
			}
		});
	}
}
