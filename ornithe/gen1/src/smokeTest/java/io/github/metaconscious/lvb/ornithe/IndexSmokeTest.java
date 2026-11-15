package io.github.metaconscious.lvb.ornithe;

import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.concurrent.atomic.AtomicReference;

/** Rejects worker access before it can change the index or enter the renderer. */
final class IndexSmokeTest {
    private IndexSmokeTest() {
    }

    static void verify(Minecraft client) {
        WorldChunk chunk = client.world.getChunkAt(0, 0);
        BlockPos pos = new BlockPos(0, 64, 0);
        BarrierPositions index = new BarrierPositions();
        index.update(chunk, pos, Blocks.BARRIER.defaultState());
        AtomicReference<Throwable> result = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                RenderSmokeTestProbe.expectFailure(index::positions, IllegalStateException.class);
                RenderSmokeTestProbe.expectFailure(() -> index.update(chunk, pos, Blocks.AIR.defaultState()),
                        IllegalStateException.class);
                RenderSmokeTestProbe.expectFailure(() -> index.replace(chunk), IllegalStateException.class);
                RenderSmokeTestProbe.expectFailure(() -> new BarrierRenderer().render(client, null, 1),
                        IllegalStateException.class);
            } catch (Throwable failure) {
                result.set(failure);
            }
        }, "lvb-index-contract-test");
        worker.start();
        try {
            worker.join(5000);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
        if (worker.isAlive() || result.get() != null) {
            throw new AssertionError("Worker access was not rejected", result.get());
        }
        if (!index.positions().iterator().next().equals(pos)) {
            throw new AssertionError("Rejected worker changed the index");
        }
    }
}
