package io.github.metaconscious.lvb.forge.mixin;

import io.github.metaconscious.lvb.forge.RenderSmokeTestProbe;
import net.minecraft.client.renderer.entity.RenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderManager.class)
public class HitboxSmokeTestMixin {
    @Inject(method = "renderDebugBoundingBox", at = @At("RETURN"))
    private void lvb$overlayDrawn(CallbackInfo ci) {
        RenderSmokeTestProbe.hitboxes++;
    }
}
