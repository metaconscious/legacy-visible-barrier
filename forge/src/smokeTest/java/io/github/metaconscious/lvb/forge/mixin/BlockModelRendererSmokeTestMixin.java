package io.github.metaconscious.lvb.forge.mixin;

import io.github.metaconscious.lvb.forge.RenderSmokeTestProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;

@Mixin(BlockModelRenderer.class)
public class BlockModelRendererSmokeTestMixin {
    @Inject(method = "renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/resources/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;Lnet/minecraft/client/renderer/WorldRenderer;Z)Z", at = @At("HEAD"))
    private void lvb$begin(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos,
                          WorldRenderer buffer, boolean cull, CallbackInfoReturnable<Boolean> result) {
        if (Thread.currentThread() == RenderSmokeTestProbe.OWNER && RenderSmokeTestProbe.watchModels) {
            if (state.getBlock() == Blocks.barrier) {
                RenderSmokeTestProbe.barriers++;

            } else {
                RenderSmokeTestProbe.otherBlocks++;
            }
            RenderSmokeTestProbe.firstVertex = buffer.getVertexCount();
        }
    }

    @Inject(method = "renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/resources/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;Lnet/minecraft/client/renderer/WorldRenderer;Z)Z", at = @At("RETURN"))
    private void lvb$end(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos,
                        WorldRenderer buffer, boolean cull, CallbackInfoReturnable<Boolean> result) {
        if (Thread.currentThread() == RenderSmokeTestProbe.OWNER && RenderSmokeTestProbe.watchModels) {
            RenderSmokeTestProbe.vertices += buffer.getVertexCount() - RenderSmokeTestProbe.firstVertex;
        }
        if (Thread.currentThread() == RenderSmokeTestProbe.OWNER && state.getBlock() == Blocks.barrier
                && RenderSmokeTestProbe.modelAction != null) {
            Runnable action = RenderSmokeTestProbe.modelAction;
            RenderSmokeTestProbe.modelAction = null;
            action.run();
        }
    }
}
