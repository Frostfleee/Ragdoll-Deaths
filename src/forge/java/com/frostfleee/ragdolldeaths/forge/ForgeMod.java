package com.frostfleee.ragdolldeaths.forge;

import com.frostfleee.ragdolldeaths.client.RagdollDeathsClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** The Forge entrypoint. Does nothing on a dedicated server; the client half lives in ForgeClient. */
@Mod(RagdollDeathsClient.MOD_ID)
public final class ForgeMod {
	public ForgeMod(FMLJavaModLoadingContext context) {
		if (FMLEnvironment.dist == Dist.CLIENT) {
			ForgeClient.init(context);
		}
	}
}
