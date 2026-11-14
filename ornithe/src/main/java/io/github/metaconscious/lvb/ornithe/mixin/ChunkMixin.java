package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.BarrierChunk;
import io.github.metaconscious.lvb.ornithe.BarrierPositions;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public class ChunkMixin implements BarrierChunk {
    @Unique
    private final BarrierPositions lvb$positions = new BarrierPositions();

    @Override
    public Iterable<BlockPos> lvb$barriers() {
        return lvb$positions.positions();
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void lvb$blockChanged(BlockPos pos, BlockState state, CallbackInfoReturnable<BlockState> result) {
        if (result.getReturnValue() != null) {
            lvb$positions.update((WorldChunk) (Object) this, pos, state);
        }
    }

    @Inject(method = {"update", "setSections"}, at = @At("TAIL"))
    private void lvb$chunkDataChanged(CallbackInfo ci) {
        lvb$positions.replace((WorldChunk) (Object) this);
    }
}
