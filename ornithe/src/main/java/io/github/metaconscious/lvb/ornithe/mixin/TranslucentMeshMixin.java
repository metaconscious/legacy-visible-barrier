package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.RenderingPlugin;
import io.github.metaconscious.lvb.ornithe.TranslucentBatch;
import io.github.metaconscious.lvb.ornithe.TranslucentMesh;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.render.world.CompiledChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledChunk.class)
public class TranslucentMeshMixin implements TranslucentMesh {
    @Unique private volatile BufferBuilder.State lvb$published;
    @Unique private BufferBuilder.State lvb$cached;
    @Unique private TranslucentBatch lvb$batch;

    @Inject(method = "setTransparencyState", at = @At("RETURN"))
    private void lvb$publish(BufferBuilder.State state, CallbackInfo ci) {
        if (RenderingPlugin.enhancedMode()) lvb$published = state;
    }

    @Override
    public TranslucentBatch lvb$translucentBatch() {
        if (!RenderingPlugin.enhancedMode()) return null;
        BufferBuilder.State state = lvb$published;
        if (state != lvb$cached) {
            lvb$batch = state == null ? null : new TranslucentBatch(state);
            lvb$cached = state;
        }
        return lvb$batch;
    }
}
