package io.github.metaconscious.lvb.legacyfabric;

import net.minecraft.util.math.BlockPos;

/** Client-thread view of barrier positions owned by a loaded chunk. */
public interface BarrierChunk {
    Iterable<BlockPos> lvb$barriers();
}
