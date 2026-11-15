package io.github.metaconscious.lvb.forge.mixin;

import io.github.metaconscious.lvb.forge.RenderingPlugin;
import io.github.metaconscious.lvb.core.RenderingPolicy;
import io.github.metaconscious.lvb.forge.BarrierChunk;
import io.github.metaconscious.lvb.forge.BarrierPositions;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
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
        return RenderingPlugin.enhanced() ? lvb$positions.positions() : java.util.Collections.emptyList();
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void lvb$blockChanged(BlockPos pos, IBlockState state, CallbackInfoReturnable<IBlockState> result) {
        if (RenderingPlugin.enhanced() && result.getReturnValue() != null) {
            try {
                lvb$positions.update((Chunk) (Object) this, pos, state);
            } catch (RuntimeException failure) {
                RenderingPlugin.report(RenderingPolicy.Failure.ENHANCED, failure);
            }
        }
    }

    @Inject(method = {"fillChunk", "setStorageArrays"}, at = @At("TAIL"))
    private void lvb$chunkDataChanged(CallbackInfo ci) {
        if (!RenderingPlugin.enhanced()) return;
        try {
            lvb$positions.replace((Chunk) (Object) this);
        } catch (RuntimeException failure) {
            RenderingPlugin.report(RenderingPolicy.Failure.ENHANCED, failure);
        }
    }
}
