package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.RenderSmokeTestProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "kabopc.village_marker.client.KaboVillageMarkerRenderer", remap = false)
public class VillageMarkerSmokeTestMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPointSize(F)V"))
    private void lvb$overlayDrawn(CallbackInfo ci) {
        RenderSmokeTestProbe.villages++;
    }
}
