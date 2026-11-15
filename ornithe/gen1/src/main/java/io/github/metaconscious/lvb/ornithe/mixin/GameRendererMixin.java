package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.RenderingPlugin;
import io.github.metaconscious.lvb.core.RenderingPolicy;
import io.github.metaconscious.lvb.ornithe.BarrierRenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render(IFJ)V",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/render/block/BlockLayer;TRANSLUCENT:Lnet/minecraft/client/render/block/BlockLayer;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 0), require = 0, expect = 0, allow = 1)
    private void lvb$prepareBarriers(int pass, float tickDelta, long limitTime, CallbackInfo ci) {
        if (!RenderingPlugin.enhanced()) return;
        Minecraft client = Minecraft.getInstance();
        if (!(client.worldRenderer instanceof BarrierRenderPass)) {
            RenderingPlugin.report(RenderingPolicy.Failure.ENHANCED,
                    new IllegalStateException("World renderer has no barrier pass capability"));
            return;
        }
        ((BarrierRenderPass) client.worldRenderer).lvb$beginBarriers(client.getCamera(), tickDelta);
    }

    @Inject(method = "render(IFJ)V",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/render/block/BlockLayer;TRANSLUCENT:Lnet/minecraft/client/render/block/BlockLayer;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 0, shift = At.Shift.AFTER), require = 0, expect = 0, allow = 1)
    private void lvb$renderBarriers(int pass, float tickDelta, long limitTime, CallbackInfo ci) {
        if (!RenderingPlugin.enhanced()) return;
        Minecraft client = Minecraft.getInstance();
        if (!(client.worldRenderer instanceof BarrierRenderPass)) {
            RenderingPlugin.report(RenderingPolicy.Failure.ENHANCED,
                    new IllegalStateException("World renderer has no barrier pass capability"));
            return;
        }
        ((BarrierRenderPass) client.worldRenderer).lvb$endBarriers();
    }
}
