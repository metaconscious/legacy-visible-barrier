package io.github.metaconscious.lvb.ornithe.mixin;

import net.minecraft.client.render.world.RenderChunkList;
import net.minecraft.client.render.world.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(RenderChunkList.class)
public interface ChunkRenderListAccess {
    @Accessor("chunks")
    List<RenderChunk> lvb$chunks();
}
