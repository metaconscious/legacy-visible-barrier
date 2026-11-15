package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.RenderSmokeTestProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.github.axolotlclient.waypoints.waypoints.WaypointRenderer", remap = false)
public class WaypointSmokeTestMixin {
    @Inject(method = "drawFontBatch", at = @At("RETURN"))
    private void lvb$labelDrawn(CallbackInfo ci) {
        RenderSmokeTestProbe.waypoints++;
    }
}
