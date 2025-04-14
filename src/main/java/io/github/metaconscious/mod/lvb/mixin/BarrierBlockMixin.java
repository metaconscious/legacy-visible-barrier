package io.github.metaconscious.mod.lvb.mixin;

import io.github.metaconscious.mod.lvb.Controllers;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BarrierBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BarrierBlock.class)
public class BarrierBlockMixin extends Block {

    protected BarrierBlockMixin(Material material) {
        super(material);
    }

    /**
     * @author Mark Oven
     * @reason Change render type to 3
     */
    @Overwrite
    @Override
    public int getBlockType() {
        return Controllers.VISIBILITY_CONTROLLER.isVisible()
                ? 3
                : -1;
    }

    @Environment(EnvType.CLIENT)
    @Override
    public RenderLayer getRenderLayerType() {
        return RenderLayer.TRANSLUCENT;
    }

    @Environment(EnvType.CLIENT)
    @Override
    public boolean isSideInvisible(@NotNull BlockView view, BlockPos pos, Direction facing) {
        return view.getBlockState(pos).getBlock() != this;
    }
}
