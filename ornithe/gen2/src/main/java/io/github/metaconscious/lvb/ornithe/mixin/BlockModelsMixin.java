package io.github.metaconscious.lvb.ornithe.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resource.model.BlockModelDefinitions;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Environment(EnvType.CLIENT)
@Mixin(BlockModelDefinitions.class)
public class BlockModelsMixin {

    @Shadow
    private Set<Block> invisibleBlocks;

    @Inject(method = "registerInvisible([Lnet/minecraft/block/Block;)V", at = @At("TAIL"))
    private void register(Block[] blocks, CallbackInfo ci) {
        this.invisibleBlocks.remove(Blocks.BARRIER);
    }

}
