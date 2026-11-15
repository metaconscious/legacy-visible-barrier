package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.OrnitheClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.entity.particle.BarrierParticle;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.BufferBuilder;

@Environment(EnvType.CLIENT)
@Mixin(BarrierParticle.class)
public class BarrierParticleMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void lvb$render(BufferBuilder builder, Entity entity, float tickDelta, float g, float h, float i, float j, float k, CallbackInfo ci) {
        if (OrnitheClient.barriersVisible()) {
            ci.cancel();
        }
    }

}
