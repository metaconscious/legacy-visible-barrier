package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.RenderSmokeTestProbe;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.block.BlockLayer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererSmokeTestMixin {
    @Inject(method = "render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("HEAD"), cancellable = true)
    private void lvb$replacementRenderer(BlockLayer layer, double delta, int pass, Entity camera,
                                         CallbackInfoReturnable<Integer> result) {
        if (RenderSmokeTestProbe.cancelTerrainLayer) {
            result.setReturnValue(0);
        }
    }

    @Inject(method = {"reload()V", "markDirty", "onViewChanged"}, at = @At("HEAD"))
    private void lvb$terrainInvalidation(CallbackInfo ci) {
        if (RenderSmokeTestProbe.watchTerrain) {
            RenderSmokeTestProbe.invalidations++;
        }
    }
}
