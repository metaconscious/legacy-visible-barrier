package io.github.metaconscious.lvb.ornithe;

import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Updated with client chunk data, independently of visibility and terrain meshes. */
public final class BarrierPositions {
    private final Set<BlockPos> positions = new HashSet<>();
    private final Set<BlockPos> view = Collections.unmodifiableSet(positions);

    public Iterable<BlockPos> positions() {
        return view;
    }

    public void update(WorldChunk chunk, BlockPos pos, BlockState state) {
        if (!chunk.getWorld().isClient) {
            return;
        }
        if (state.getBlock() == Blocks.BARRIER) {
            positions.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        } else {
            positions.remove(pos);
        }
    }

    public void replace(WorldChunk chunk) {
        if (!chunk.getWorld().isClient) {
            return;
        }
        positions.clear();
        for (WorldChunkSection section : chunk.getSections()) {
            if (section == null || section.isEmpty()) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlock(x, y, z) == Blocks.BARRIER) {
                            positions.add(new BlockPos((chunk.chunkX << 4) + x,
                                    section.getOffsetY() + y, (chunk.chunkZ << 4) + z));
                        }
                    }
                }
            }
        }
    }
}
