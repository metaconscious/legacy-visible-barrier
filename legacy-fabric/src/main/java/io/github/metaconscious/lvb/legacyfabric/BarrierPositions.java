package io.github.metaconscious.lvb.legacyfabric;

import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

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

    public void update(Chunk chunk, BlockPos pos, BlockState state) {
        if (!chunk.getWorld().isClient) {
            return;
        }
        if (state.getBlock() == Blocks.BARRIER) {
            positions.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        } else {
            positions.remove(pos);
        }
    }

    public void replace(Chunk chunk) {
        if (!chunk.getWorld().isClient) {
            return;
        }
        positions.clear();
        for (ChunkSection section : chunk.getBlockStorage()) {
            if (section == null || section.isEmpty()) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlock(x, y, z) == Blocks.BARRIER) {
                            positions.add(new BlockPos((chunk.chunkX << 4) + x,
                                    section.getYOffset() + y, (chunk.chunkZ << 4) + z));
                        }
                    }
                }
            }
        }
    }
}
