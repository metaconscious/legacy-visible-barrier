package io.github.metaconscious.lvb.legacyfabric.mixin;

import io.github.metaconscious.lvb.legacyfabric.RenderSmokeTestProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.tildejustin.chunkborders.ChunkBorderDebugRenderer", remap = false)
public class ChunkBorderSmokeTestMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void lvb$overlayDrawn(CallbackInfo ci) {
        RenderSmokeTestProbe.chunkBorders++;
    }
}
