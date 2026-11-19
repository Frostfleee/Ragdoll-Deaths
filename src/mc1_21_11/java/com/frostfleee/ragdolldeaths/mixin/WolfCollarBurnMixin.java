package com.frostfleee.ragdolldeaths.mixin;

import com.frostfleee.ragdolldeaths.client.PhysicsPoseApplier;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A wolf's collar burns where lava touched it (see Scorch). */
@Mixin(WolfCollarLayer.class)
abstract class WolfCollarBurnMixin {
	@ModifyArg(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/WolfRenderState;FF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entityCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
	@SuppressWarnings("rawtypes")
	private net.minecraft.resources.Identifier ragdolldeaths$burnt(net.minecraft.resources.Identifier texture) {
		return PhysicsPoseApplier.layerTexture(texture, ((RenderLayer) (Object) this).getParentModel(), null);
	}
}
