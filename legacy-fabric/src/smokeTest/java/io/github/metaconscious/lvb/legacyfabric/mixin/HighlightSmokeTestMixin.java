package io.github.metaconscious.lvb.legacyfabric.mixin;

import io.github.metaconscious.lvb.legacyfabric.RenderSmokeTestProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.infloat.legacyblockhighlight.BlockHighlightRenderer", remap = false)
public class HighlightSmokeTestMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopAttrib()V"))
    private static void lvb$overlayDrawn(CallbackInfo ci) {
        RenderSmokeTestProbe.highlights++;
    }
}
