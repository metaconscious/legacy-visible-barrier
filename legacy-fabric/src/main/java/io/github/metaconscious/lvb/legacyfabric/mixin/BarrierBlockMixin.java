package io.github.metaconscious.lvb.legacyfabric.mixin;

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
