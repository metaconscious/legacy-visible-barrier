package io.github.metaconscious.lvb.legacyfabric.mixin;

import io.github.metaconscious.lvb.legacyfabric.BarrierRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Unique
    private final BarrierRenderer lvb$barriers = new BarrierRenderer();

    @Inject(method = "renderWorld(IFJ)V",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/render/RenderLayer;TRANSLUCENT:Lnet/minecraft/client/render/RenderLayer;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 0, shift = At.Shift.AFTER), require = 1, allow = 1)
    private void lvb$renderBarriers(int pass, float tickDelta, long limitTime, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        lvb$barriers.render(client, client.getCameraEntity(), tickDelta);
    }
}
