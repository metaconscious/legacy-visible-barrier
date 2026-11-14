package io.github.metaconscious.lvb.legacyfabric.mixin;

import io.github.metaconscious.lvb.legacyfabric.TranslucentBatch;
import io.github.metaconscious.lvb.legacyfabric.VertexOrigin;
import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BufferBuilder.class)
public class BufferBuilderMixin {
    @Shadow private double offsetX;
    @Shadow private double offsetY;
    @Shadow private double offsetZ;

    @Inject(method = "method_9727", at = @At("RETURN"))
    private void lvb$captureOrigin(CallbackInfoReturnable<BufferBuilder.DrawArrayParameters> ci) {
        ((VertexOrigin) ci.getReturnValue()).lvb$setOrigin(-offsetX, -offsetY, -offsetZ);
    }

    @Dynamic("OptiFine adds the multi-texture range uploader to BufferBuilder")
    @Inject(method = "draw(II)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void lvb$textureRange(int first, int end, CallbackInfo ci) {
        if ((Object) this instanceof TranslucentBatch) {
            ((TranslucentBatch) (Object) this).drawTextureRange(first, end);
            ci.cancel();
        }
    }
}
