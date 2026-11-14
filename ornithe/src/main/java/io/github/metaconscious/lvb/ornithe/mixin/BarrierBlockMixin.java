package io.github.metaconscious.lvb.ornithe.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BarrierBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BarrierBlock.class)
public class BarrierBlockMixin extends Block {

    protected BarrierBlockMixin(Material material) {
        super(material);
    }

    @Environment(EnvType.CLIENT)
    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.TRANSLUCENT;
    }

    @Environment(EnvType.CLIENT)
    @Override
    public boolean shouldRenderFace(WorldView view, BlockPos pos, Direction facing) {
        return view.getBlockState(pos).getBlock() != this;
    }
}
