package io.github.metaconscious.lvb.forge.mixin;

import io.github.metaconscious.lvb.forge.RenderSmokeTestProbe;
import io.github.metaconscious.lvb.forge.RenderingPlugin;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class WorldRendererSmokeTestMixin {
    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/EnumWorldBlockLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("HEAD"), cancellable = true)
    private void lvb$replacementRenderer(EnumWorldBlockLayer layer, double delta, int pass, Entity camera,
                                         CallbackInfoReturnable<Integer> result) {
        if (RenderSmokeTestProbe.cancelTerrainLayer) {
            result.setReturnValue(0);
        }
    }

    @Inject(method = {"loadRenderers()V", "markBlocksForUpdate", "setDisplayListEntitiesDirty"}, at = @At("HEAD"))
    private void lvb$terrainInvalidation(CallbackInfo ci) {
        if (RenderSmokeTestProbe.watchTerrain) {
            RenderSmokeTestProbe.invalidations++;
        }
    }

    @Inject(method = {"loadRenderers()V", "markBlockRangeForRenderUpdate"}, at = @At("HEAD"))
    private void lvb$nativeRefresh(CallbackInfo ci) {
        if (RenderSmokeTestProbe.watchTerrain) RenderSmokeTestProbe.nativeRefreshes++;
    }

    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/EnumWorldBlockLayer;DILnet/minecraft/entity/Entity;)I", at = @At("RETURN"))
    private void lvb$nativeSections(EnumWorldBlockLayer layer, double delta, int pass, Entity camera,
                                    CallbackInfoReturnable<Integer> result) {
        if (!RenderingPlugin.enhanced() && layer == EnumWorldBlockLayer.TRANSLUCENT) {
            RenderSmokeTestProbe.nativeSections = result.getReturnValue();
            RenderSmokeTestProbe.verifyShaderStage(true);
        }
    }
}
