package io.github.metaconscious.mod.lvb.mixin;

import io.github.metaconscious.mod.lvb.Controllers;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.BarrierParticle;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(BarrierParticle.class)
public class BarrierParticleMixin {

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    public void draw(BufferBuilder builder, Entity entity, float tickDelta, float g, float h, float i, float j, float k, @NotNull CallbackInfo ci) {
        if (Controllers.VISIBILITY_CONTROLLER.isVisible()) {
            ci.cancel();
        }
    }

}
