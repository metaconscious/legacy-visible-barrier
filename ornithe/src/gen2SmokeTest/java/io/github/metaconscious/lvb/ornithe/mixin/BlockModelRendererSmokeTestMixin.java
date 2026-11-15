package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.RenderSmokeTestProbe;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModelRenderer.class)
public class BlockModelRendererSmokeTestMixin {
    @Inject(method = "render(Lnet/minecraft/world/WorldView;Lnet/minecraft/client/render/model/block/BakedModel;Lnet/minecraft/block/state/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/vertex/BufferBuilder;Z)Z", at = @At("HEAD"))
    private void lvb$begin(WorldView world, BakedModel model, BlockState state, BlockPos pos,
                          BufferBuilder buffer, boolean cull, CallbackInfoReturnable<Boolean> result) {
        if (Thread.currentThread() == RenderSmokeTestProbe.OWNER && RenderSmokeTestProbe.watchModels) {
            if (state.getBlock() == Blocks.BARRIER) {
                RenderSmokeTestProbe.barriers++;
            } else {
                RenderSmokeTestProbe.otherBlocks++;
            }
            RenderSmokeTestProbe.firstVertex = buffer.getVertexCount();
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/WorldView;Lnet/minecraft/client/render/model/block/BakedModel;Lnet/minecraft/block/state/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/vertex/BufferBuilder;Z)Z", at = @At("RETURN"))
    private void lvb$end(WorldView world, BakedModel model, BlockState state, BlockPos pos,
                        BufferBuilder buffer, boolean cull, CallbackInfoReturnable<Boolean> result) {
        if (Thread.currentThread() == RenderSmokeTestProbe.OWNER && RenderSmokeTestProbe.watchModels) {
            RenderSmokeTestProbe.vertices += buffer.getVertexCount() - RenderSmokeTestProbe.firstVertex;
        }
        if (Thread.currentThread() == RenderSmokeTestProbe.OWNER && state.getBlock() == Blocks.BARRIER
                && RenderSmokeTestProbe.modelAction != null) {
            Runnable action = RenderSmokeTestProbe.modelAction;
            RenderSmokeTestProbe.modelAction = null;
            action.run();
        }
    }
}
