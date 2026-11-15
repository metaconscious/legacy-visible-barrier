package io.github.metaconscious.lvb.legacyfabric.mixin;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.world.AbstractChunkRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = WorldRenderer.class, priority = 2000)
public class NativeConflictSmokeTestMixin {
    @Redirect(method = "renderLayer(Lnet/minecraft/client/render/RenderLayer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/world/AbstractChunkRenderManager;render(Lnet/minecraft/client/render/RenderLayer;)V"))
    private void lvb$competingTerrain(AbstractChunkRenderManager list, RenderLayer layer) {
        list.render(layer);
    }
}
