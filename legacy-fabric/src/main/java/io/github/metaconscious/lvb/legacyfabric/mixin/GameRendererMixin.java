package io.github.metaconscious.lvb.legacyfabric.mixin;

import io.github.metaconscious.lvb.legacyfabric.RenderingPlugin;
import io.github.metaconscious.lvb.core.RenderingPolicy;
import io.github.metaconscious.lvb.legacyfabric.BarrierRenderPass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "renderWorld(IFJ)V",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/render/RenderLayer;TRANSLUCENT:Lnet/minecraft/client/render/RenderLayer;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 0), require = 0, expect = 0, allow = 1)
    private void lvb$prepareBarriers(int pass, float tickDelta, long limitTime, CallbackInfo ci) {
        if (!RenderingPlugin.enhanced()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.worldRenderer instanceof BarrierRenderPass)) {
            RenderingPlugin.report(RenderingPolicy.Failure.ENHANCED,
                    new IllegalStateException("World renderer has no barrier pass capability"));
            return;
        }
        ((BarrierRenderPass) client.worldRenderer).lvb$beginBarriers(client.getCameraEntity(), tickDelta);
    }

    @Inject(method = "renderWorld(IFJ)V",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/render/RenderLayer;TRANSLUCENT:Lnet/minecraft/client/render/RenderLayer;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 0, shift = At.Shift.AFTER), require = 0, expect = 0, allow = 1)
    private void lvb$renderBarriers(int pass, float tickDelta, long limitTime, CallbackInfo ci) {
        if (!RenderingPlugin.enhanced()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.worldRenderer instanceof BarrierRenderPass)) {
            RenderingPlugin.report(RenderingPolicy.Failure.ENHANCED,
                    new IllegalStateException("World renderer has no barrier pass capability"));
            return;
        }
        ((BarrierRenderPass) client.worldRenderer).lvb$endBarriers();
    }
}
