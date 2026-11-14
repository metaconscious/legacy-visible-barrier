package io.github.metaconscious.lvb.legacyfabric.mixin;

import io.github.metaconscious.lvb.legacyfabric.RenderSmokeTestProbe;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererSmokeTestMixin {
    @Inject(method = "renderLayer(Lnet/minecraft/client/render/RenderLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("HEAD"), cancellable = true)
    private void lvb$replacementRenderer(RenderLayer layer, double delta, int pass, Entity camera,
                                         CallbackInfoReturnable<Integer> result) {
        if (RenderSmokeTestProbe.cancelTerrainLayer) {
            result.setReturnValue(0);
        }
    }

    @Inject(method = {"reload()V", "updateBlock", "scheduleTerrainUpdate"}, at = @At("HEAD"))
    private void lvb$terrainInvalidation(CallbackInfo ci) {
        if (RenderSmokeTestProbe.watchTerrain) {
            RenderSmokeTestProbe.invalidations++;
        }
    }
}
