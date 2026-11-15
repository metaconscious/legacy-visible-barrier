package io.github.metaconscious.lvb.forge.mixin;

import io.github.metaconscious.lvb.forge.RenderingPlugin;
import io.github.metaconscious.lvb.core.RenderingPolicy;
import io.github.metaconscious.lvb.forge.TranslucentBatch;
import io.github.metaconscious.lvb.forge.VertexOrigin;
import net.minecraft.client.renderer.WorldRenderer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public class BufferBuilderMixin {
    @Shadow private double xOffset;
    @Shadow private double yOffset;
    @Shadow private double zOffset;

    @Inject(method = "getVertexState", at = @At("RETURN"))
    private void lvb$captureOrigin(CallbackInfoReturnable<WorldRenderer.State> ci) {
        if (!RenderingPlugin.enhancedMode()) return;
        if (ci.getReturnValue() instanceof VertexOrigin) {
            ((VertexOrigin) ci.getReturnValue()).lvb$setOrigin(-xOffset, -yOffset, -zOffset);
        } else {
            RenderingPlugin.report(RenderingPolicy.Failure.ENHANCED,
                    new IllegalStateException("Vertex snapshot has no origin capability"));
        }
    }

    @Dynamic("OptiFine adds the multi-texture range uploader to BufferBuilder")
    @Inject(method = "draw(II)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void lvb$textureRange(int first, int end, CallbackInfo ci) {
        if (RenderingPlugin.enhancedMode() && (Object) this instanceof TranslucentBatch) {
            ((TranslucentBatch) (Object) this).drawTextureRange(first, end);
            ci.cancel();
        }
    }
}
