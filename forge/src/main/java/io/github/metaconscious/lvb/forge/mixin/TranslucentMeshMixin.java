package io.github.metaconscious.lvb.forge.mixin;

import io.github.metaconscious.lvb.forge.RenderingPlugin;
import io.github.metaconscious.lvb.forge.TranslucentBatch;
import io.github.metaconscious.lvb.forge.TranslucentMesh;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledChunk.class)
public class TranslucentMeshMixin implements TranslucentMesh {
    @Unique private volatile WorldRenderer.State lvb$published;
    @Unique private WorldRenderer.State lvb$cached;
    @Unique private TranslucentBatch lvb$batch;

    @Inject(method = "setState", at = @At("RETURN"))
    private void lvb$publish(WorldRenderer.State state, CallbackInfo ci) {
        if (RenderingPlugin.enhancedMode()) lvb$published = state;
    }

    @Override
    public TranslucentBatch lvb$translucentBatch() {
        if (!RenderingPlugin.enhancedMode()) return null;
        WorldRenderer.State state = lvb$published;
        if (state != lvb$cached) {
            lvb$batch = state == null ? null : new TranslucentBatch(state);
            lvb$cached = state;
        }
        return lvb$batch;
    }
}
