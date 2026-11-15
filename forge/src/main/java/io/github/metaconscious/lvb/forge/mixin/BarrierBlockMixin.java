package io.github.metaconscious.lvb.forge.mixin;

import io.github.metaconscious.lvb.forge.RenderingPlugin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBarrier;
import net.minecraft.block.material.Material;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockBarrier.class)
public class BarrierBlockMixin extends Block {

    protected BarrierBlockMixin(Material material) {
        super(material);
    }

    @Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
    private void lvb$nativeModel(CallbackInfoReturnable<Integer> result) {
        if (RenderingPlugin.nativeVisible()) result.setReturnValue(3);
    }

    @Override
    public EnumWorldBlockLayer getBlockLayer() {
        return EnumWorldBlockLayer.TRANSLUCENT;
    }

    @Override
    public boolean shouldSideBeRendered(IBlockAccess view, BlockPos pos, EnumFacing facing) {
        return view.getBlockState(pos).getBlock() != this;
    }
}
