package io.github.metaconscious.lvb.legacyfabric.mixin;

import io.github.metaconscious.lvb.legacyfabric.RenderingPlugin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BarrierBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BarrierBlock.class)
public class BarrierBlockMixin extends Block {

    protected BarrierBlockMixin(Material material) {
        super(material);
    }

    @Inject(method = "getBlockType", at = @At("RETURN"), cancellable = true)
    private void lvb$nativeModel(CallbackInfoReturnable<Integer> result) {
        if (RenderingPlugin.nativeVisible()) result.setReturnValue(3);
    }

    @Environment(EnvType.CLIENT)
    @Override
    public RenderLayer getRenderLayerType() {
        return RenderLayer.TRANSLUCENT;
    }

    @Environment(EnvType.CLIENT)
    @Override
    public boolean isSideInvisible(BlockView view, BlockPos pos, Direction facing) {
        return view.getBlockState(pos).getBlock() != this;
    }
}
