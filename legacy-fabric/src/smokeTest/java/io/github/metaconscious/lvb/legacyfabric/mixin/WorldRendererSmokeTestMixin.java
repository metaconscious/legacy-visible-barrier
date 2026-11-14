package io.github.metaconscious.lvb.legacyfabric.mixin;

import io.github.metaconscious.lvb.legacyfabric.RenderSmokeTestProbe;
import io.github.metaconscious.lvb.legacyfabric.RenderingPlugin;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;

@Mixin(WorldRenderer.class)
public class WorldRendererSmokeTestMixin {
    @Inject(method = "drawBlockOutline", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glLineWidth(F)V", remap = false, shift = At.Shift.AFTER))
    private void lvb$outlineDrawn(CallbackInfo ci) {
        RenderSmokeTestProbe.outlineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
    }

    @Inject(method = "renderLayer(Lnet/minecraft/client/render/RenderLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("HEAD"), cancellable = true)
    private void lvb$replacementRenderer(RenderLayer layer, double delta, int pass, Entity camera,
                                         CallbackInfoReturnable<Integer> result) {
        if (RenderSmokeTestProbe.cancelTerrainLayer) {
            result.setReturnValue(0);
        }
    }

    @Inject(method = {"reload()V", "updateBlock", "scheduleTerrainUpdate"}, at = @At("HEAD"))
    private void lvb$terrainInvalidation(CallbackInfo ci) {
        if (RenderSmokeTestProbe.watchTerrain) {
            RenderSmokeTestProbe.invalidations++;
        }
    }

    @Inject(method = {"reload()V", "onRenderRegionUpdate"}, at = @At("HEAD"))
    private void lvb$nativeRefresh(CallbackInfo ci) {
        if (RenderSmokeTestProbe.watchTerrain) RenderSmokeTestProbe.nativeRefreshes++;
    }

    @Inject(method = "renderLayer(Lnet/minecraft/client/render/RenderLayer;DILnet/minecraft/entity/Entity;)I", at = @At("RETURN"))
    private void lvb$nativeSections(RenderLayer layer, double delta, int pass, Entity camera,
                                    CallbackInfoReturnable<Integer> result) {
        if (!RenderingPlugin.enhanced() && layer == RenderLayer.TRANSLUCENT) {
            RenderSmokeTestProbe.nativeSections = result.getReturnValue();
            java.nio.FloatBuffer projection = org.lwjgl.BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
            RenderSmokeTestProbe.barrierProjectionW = projection.get(15);
            RenderSmokeTestProbe.verifyShaderStage(true);
        }
    }
}
