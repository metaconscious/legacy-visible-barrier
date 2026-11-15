package io.github.metaconscious.lvb.forge.mixin;

import io.github.metaconscious.lvb.forge.RenderingPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import io.github.metaconscious.lvb.core.RenderingPolicy;
import io.github.metaconscious.lvb.forge.BarrierRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class GameRendererMixin {
    @Inject(method = "renderWorldPass(IFJ)V",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/util/EnumWorldBlockLayer;TRANSLUCENT:Lnet/minecraft/util/EnumWorldBlockLayer;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/EnumWorldBlockLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 0), require = 0, expect = 0, allow = 1)
    private void lvb$prepareBarriers(int pass, float tickDelta, long limitTime, CallbackInfo ci) {
        if (!RenderingPlugin.enhanced()) return;
        Minecraft client = Minecraft.getMinecraft();
        if (!(client.renderGlobal instanceof BarrierRenderPass)) {
            RenderingPlugin.report(RenderingPolicy.Failure.ENHANCED,
                    new IllegalStateException("World renderer has no barrier pass capability"));
            return;
        }
        ((BarrierRenderPass) client.renderGlobal).lvb$beginBarriers(client.getRenderViewEntity(), tickDelta);
    }

    @Inject(method = "renderWorldPass(IFJ)V",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/util/EnumWorldBlockLayer;TRANSLUCENT:Lnet/minecraft/util/EnumWorldBlockLayer;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/EnumWorldBlockLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 0, shift = At.Shift.AFTER), require = 0, expect = 0, allow = 1)
    private void lvb$renderBarriers(int pass, float tickDelta, long limitTime, CallbackInfo ci) {
        if (!RenderingPlugin.enhanced()) return;
        Minecraft client = Minecraft.getMinecraft();
        if (!(client.renderGlobal instanceof BarrierRenderPass)) {
            RenderingPlugin.report(RenderingPolicy.Failure.ENHANCED,
                    new IllegalStateException("World renderer has no barrier pass capability"));
            return;
        }
        ((BarrierRenderPass) client.renderGlobal).lvb$endBarriers();
    }
}
