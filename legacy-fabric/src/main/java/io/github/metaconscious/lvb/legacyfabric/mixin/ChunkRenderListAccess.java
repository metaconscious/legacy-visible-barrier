package io.github.metaconscious.lvb.legacyfabric.mixin;

import net.minecraft.client.render.world.AbstractChunkRenderManager;
import net.minecraft.client.world.BuiltChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(AbstractChunkRenderManager.class)
public interface ChunkRenderListAccess {
    @Accessor("helpers")
    List<BuiltChunk> lvb$chunks();
}
