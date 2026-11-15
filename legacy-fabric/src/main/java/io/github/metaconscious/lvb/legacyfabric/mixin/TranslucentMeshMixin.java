package io.github.metaconscious.lvb.legacyfabric.mixin;

import io.github.metaconscious.lvb.legacyfabric.RenderingPlugin;
import io.github.metaconscious.lvb.legacyfabric.TranslucentBatch;
import io.github.metaconscious.lvb.legacyfabric.TranslucentMesh;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.world.ChunkAssemblyHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAssemblyHelper.class)
public class TranslucentMeshMixin implements TranslucentMesh {
    @Unique private volatile BufferBuilder.DrawArrayParameters lvb$published;
    @Unique private BufferBuilder.DrawArrayParameters lvb$cached;
    @Unique private TranslucentBatch lvb$batch;

    @Inject(method = "setDrawArrayParameters", at = @At("RETURN"))
    private void lvb$publish(BufferBuilder.DrawArrayParameters state, CallbackInfo ci) {
        if (RenderingPlugin.enhancedMode()) lvb$published = state;
    }

    @Override
    public TranslucentBatch lvb$translucentBatch() {
        if (!RenderingPlugin.enhancedMode()) return null;
        BufferBuilder.DrawArrayParameters state = lvb$published;
        if (state != lvb$cached) {
            lvb$batch = state == null ? null : new TranslucentBatch(state);
            lvb$cached = state;
        }
        return lvb$batch;
    }
}
