package io.github.metaconscious.mod.lvb.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.BlockStateMapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Environment(EnvType.CLIENT)
@Mixin(BlockStateMapper.class)
public class BlockStateMapperMixin {

    @Shadow
    private Set<Block> blocks;

    @Inject(method = "putBlocks", at = @At("TAIL"))
    private void putBlocks(Block[] blocks, CallbackInfo ci) {
        this.blocks.remove(Blocks.BARRIER);
    }

}
