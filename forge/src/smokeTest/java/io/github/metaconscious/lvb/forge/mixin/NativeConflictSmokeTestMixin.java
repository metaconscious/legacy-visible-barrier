package io.github.metaconscious.lvb.forge.mixin;

import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderGlobal.class, priority = 2000)
public class NativeConflictSmokeTestMixin {
    @Redirect(method = "renderBlockLayer(Lnet/minecraft/util/EnumWorldBlockLayer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ChunkRenderContainer;renderChunkLayer(Lnet/minecraft/util/EnumWorldBlockLayer;)V"))
    private void lvb$competingTerrain(ChunkRenderContainer list, EnumWorldBlockLayer layer) {
        list.renderChunkLayer(layer);
    }
}
