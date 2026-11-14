package io.github.metaconscious.lvb.legacyfabric.mixin;

import io.github.metaconscious.lvb.legacyfabric.BarrierChunk;
import io.github.metaconscious.lvb.legacyfabric.BarrierPositions;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Chunk.class)
public class ChunkMixin implements BarrierChunk {
    @Unique
    private final BarrierPositions lvb$positions = new BarrierPositions();

    @Override
    public Iterable<BlockPos> lvb$barriers() {
        return lvb$positions.positions();
    }

    @Inject(method = "getBlockState", at = @At("RETURN"))
    private void lvb$blockChanged(BlockPos pos, BlockState state, CallbackInfoReturnable<BlockState> result) {
        if (result.getReturnValue() != null) {
            lvb$positions.update((Chunk) (Object) this, pos, state);
        }
    }

    @Inject(method = {"method_3895", "setLevelChunkSections"}, at = @At("TAIL"))
    private void lvb$chunkDataChanged(CallbackInfo ci) {
        lvb$positions.replace((Chunk) (Object) this);
    }
}
