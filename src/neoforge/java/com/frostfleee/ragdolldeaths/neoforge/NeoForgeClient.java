package com.frostfleee.ragdolldeaths.neoforge;

import com.frostfleee.ragdolldeaths.client.RagdollDeathsClient;
import com.frostfleee.ragdolldeaths.client.RagdollTracker;
import com.frostfleee.ragdolldeaths.client.config.RagdollConfigScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** The NeoForge entrypoint. Client only: on a server the mod is never constructed. */
@Mod(value = RagdollDeathsClient.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
	public NeoForgeClient(ModContainer container) {
		RagdollDeathsClient.init(FMLPaths.CONFIGDIR.get());
		RagdollTracker tracker = RagdollTracker.INSTANCE;
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new RagdollConfigScreen(parent));

		NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, event -> {
			if (event.getLevel() instanceof ClientLevel level) {
				tracker.tick(level);
			}
		});
		NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, event -> {
			if (event.getLevel().isClientSide()) {
				tracker.onUnload(event.getEntity());
			}
		});
		NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, event -> {
			if (event.getLevel() instanceof ClientLevel) {
				RagdollDeathsClient.onLevelChange();
			}
		});
	}
}
