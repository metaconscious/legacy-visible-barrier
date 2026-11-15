package io.github.metaconscious.lvb.forge.mixin;

import io.github.metaconscious.lvb.forge.ForgeClient;
import net.minecraft.client.particle.Barrier;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Barrier.class)
public class BarrierParticleMixin {

    @Inject(method = "renderParticle", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void lvb$draw(WorldRenderer builder, Entity entity, float tickDelta, float g, float h, float i, float j, float k, CallbackInfo ci) {
        if (ForgeClient.barriersVisible()) {
            ci.cancel();
        }
    }

}
