package com.frostfleee.ragdolldeaths.fabric;

import com.frostfleee.ragdolldeaths.client.config.RagdollConfigScreen;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Loaded by Mod Menu only when it is installed; nothing else references this class. */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return RagdollConfigScreen::new;
	}
}
