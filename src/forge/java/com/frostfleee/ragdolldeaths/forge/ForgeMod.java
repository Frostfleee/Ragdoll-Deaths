package com.frostfleee.ragdolldeaths.forge;

import com.frostfleee.ragdolldeaths.client.RagdollDeathsClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** The Forge entrypoint. Does nothing on a dedicated server; the client half lives in ForgeClient. */
@Mod(RagdollDeathsClient.MOD_ID)
public final class ForgeMod {
	/** Forge for Minecraft 1.21 (51.x) can only create a mod this way; later versions use the constructor below. */
	public ForgeMod() {
		this(FMLJavaModLoadingContext.get());
	}

	public ForgeMod(FMLJavaModLoadingContext context) {
		if (FMLEnvironment.dist == Dist.CLIENT) {
			ForgeClient.init(context);
		}
	}
}
