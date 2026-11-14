package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.BarrierRenderer;
import io.github.metaconscious.lvb.ornithe.RenderSmokeTestProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BarrierRenderer.class, remap = false)
public class BarrierRendererSmokeTestMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void lvb$count(CallbackInfo ci) {
        if (Thread.currentThread() == RenderSmokeTestProbe.OWNER && RenderSmokeTestProbe.watchModels) {
            RenderSmokeTestProbe.barrierPasses++;
        }
    }
}
