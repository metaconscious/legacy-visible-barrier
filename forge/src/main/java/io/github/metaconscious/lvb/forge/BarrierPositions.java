package io.github.metaconscious.lvb.forge;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Client-thread index with a read-only live view; no world or chunk is retained. */
public final class BarrierPositions {
    private final Set<BlockPos> positions = new HashSet<>();
    private final Set<BlockPos> view = Collections.unmodifiableSet(positions);

    public Iterable<BlockPos> positions() {
        requireClientThread();
        return view;
    }

    public void update(Chunk chunk, BlockPos pos, IBlockState state) {
        if (!chunk.getWorld().isRemote) {
            return;
        }
        requireClientThread();
        if (state.getBlock() == Blocks.barrier) {
            positions.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        } else {
            positions.remove(pos);
        }
    }

    public void replace(Chunk chunk) {
        if (!chunk.getWorld().isRemote) {
            return;
        }
        requireClientThread();
        positions.clear();
        for (ExtendedBlockStorage section : chunk.getBlockStorageArray()) {
            if (section == null || section.isEmpty()) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockByExtId(x, y, z) == Blocks.barrier) {
                            positions.add(new BlockPos((chunk.xPosition << 4) + x,
                                    section.getYLocation() + y, (chunk.zPosition << 4) + z));
                        }
                    }
                }
            }
        }
    }

    private static void requireClientThread() {
        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Barrier index access requires the client thread");
        }
    }
}
