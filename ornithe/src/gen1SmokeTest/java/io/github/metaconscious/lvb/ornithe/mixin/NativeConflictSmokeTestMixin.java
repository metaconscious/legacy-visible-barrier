package io.github.metaconscious.lvb.ornithe.mixin;

import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.render.world.RenderChunkList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = WorldRenderer.class, priority = 2000)
public class NativeConflictSmokeTestMixin {
    @Redirect(method = "render(Lnet/minecraft/client/render/block/BlockLayer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/world/RenderChunkList;render(Lnet/minecraft/client/render/block/BlockLayer;)V"))
    private void lvb$competingTerrain(RenderChunkList list, BlockLayer layer) {
        list.render(layer);
    }
}
