package io.github.metaconscious.lvb.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.chunk.RenderChunk;

@Mixin(ChunkRenderContainer.class)
public interface ChunkRenderListAccess {
    @Accessor("renderChunks")
    List<RenderChunk> lvb$chunks();
}
