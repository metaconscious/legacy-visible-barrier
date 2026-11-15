package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.RenderSmokeTestProbe;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererSmokeTestMixin {
    @Inject(method = "render(IFJ)V", at = @At("HEAD"))
    private void lvb$cameraPass(int pass, float tickDelta, long limit, CallbackInfo ci) {
        if (RenderSmokeTestProbe.watchModels) {
            RenderSmokeTestProbe.cameraPasses++;
            RenderSmokeTestProbe.eyeMask |= 1 << pass;
        }
    }

    @Inject(method = "enableLightMap", at = @At("RETURN"))
    private void lvb$failAfterLightmap(CallbackInfo ci) {
        if (RenderSmokeTestProbe.failLightmap) {
            RenderSmokeTestProbe.failLightmap = false;
            throw new RenderSmokeTestProbe.InjectedFailure();
        }
    }
}
