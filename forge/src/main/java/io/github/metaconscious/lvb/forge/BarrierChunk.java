package io.github.metaconscious.lvb.forge;

import net.minecraft.util.BlockPos;

/** Client-thread view of barrier positions owned by a loaded chunk. */
public interface BarrierChunk {
    Iterable<BlockPos> lvb$barriers();
}
