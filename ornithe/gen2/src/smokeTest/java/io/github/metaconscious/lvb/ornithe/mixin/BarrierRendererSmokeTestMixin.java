package io.github.metaconscious.lvb.ornithe.mixin;

import java.util.List;
import io.github.metaconscious.lvb.ornithe.TranslucentBatch;
import io.github.metaconscious.lvb.ornithe.BarrierRenderer;
import io.github.metaconscious.lvb.ornithe.RenderSmokeTestProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BarrierRenderer.class, remap = false)
public class BarrierRendererSmokeTestMixin {
    @Inject(method = "inVboRegion", at = @At("RETURN"))
    private static void lvb$region(CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValue()) {
            RenderSmokeTestProbe.regionTerrain();
        }
    }

    @Inject(method = "compiledTerrain", at = @At("RETURN"))
    private static void lvb$compiled(CallbackInfoReturnable<List<TranslucentBatch>> ci) {
        if (RenderSmokeTestProbe.watchModels) {
            RenderSmokeTestProbe.terrainBatches += ci.getReturnValue().size();
        }
    }

    @Inject(method = "renderWithTerrain", at = @At("HEAD"))
    private void lvb$count(CallbackInfoReturnable<Boolean> ci) {
        if (Thread.currentThread() == RenderSmokeTestProbe.OWNER && RenderSmokeTestProbe.watchModels) {
            RenderSmokeTestProbe.barrierPasses++;
            RenderSmokeTestProbe.verifyShaderStage();
        }
    }
}
