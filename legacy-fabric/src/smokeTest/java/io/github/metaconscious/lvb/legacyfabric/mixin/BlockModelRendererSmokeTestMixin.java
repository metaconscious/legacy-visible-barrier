package io.github.metaconscious.lvb.legacyfabric.mixin;

import io.github.metaconscious.lvb.legacyfabric.RenderSmokeTestProbe;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModelRenderer.class)
public class BlockModelRendererSmokeTestMixin {
    @Inject(method = "render(Lnet/minecraft/world/BlockView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/BufferBuilder;Z)Z", at = @At("HEAD"))
    private void lvb$begin(BlockView world, BakedModel model, BlockState state, BlockPos pos,
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

    @Inject(method = "render(Lnet/minecraft/world/BlockView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/BufferBuilder;Z)Z", at = @At("RETURN"))
    private void lvb$end(BlockView world, BakedModel model, BlockState state, BlockPos pos,
                        BufferBuilder buffer, boolean cull, CallbackInfoReturnable<Boolean> result) {
        if (Thread.currentThread() == RenderSmokeTestProbe.OWNER && RenderSmokeTestProbe.watchModels) {
            RenderSmokeTestProbe.vertices += buffer.getVertexCount() - RenderSmokeTestProbe.firstVertex;
        }
    }
}
