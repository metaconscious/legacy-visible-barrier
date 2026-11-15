package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.RenderSmokeTestProbe;
import io.github.metaconscious.lvb.ornithe.RenderingPlugin;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.block.BlockLayer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;

@Mixin(WorldRenderer.class)
public class WorldRendererSmokeTestMixin {
    @Inject(method = "renderBlockOutline", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glLineWidth(F)V", remap = false, shift = At.Shift.AFTER))
    private void lvb$outlineDrawn(CallbackInfo ci) {
        RenderSmokeTestProbe.outlineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
    }

    @Inject(method = "render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("HEAD"), cancellable = true)
    private void lvb$replacementRenderer(BlockLayer layer, double delta, int pass, Entity camera,
                                         CallbackInfoReturnable<Integer> result) {
        if (RenderSmokeTestProbe.cancelTerrainLayer) {
            result.setReturnValue(0);
        }
    }

    @Inject(method = {"reload()V", "markDirty", "onViewChanged"}, at = @At("HEAD"))
    private void lvb$terrainInvalidation(CallbackInfo ci) {
        if (RenderSmokeTestProbe.watchTerrain) {
            RenderSmokeTestProbe.invalidations++;
        }
    }

    @Inject(method = {"reload()V", "notifyRegionChanged"}, at = @At("HEAD"))
    private void lvb$nativeRefresh(CallbackInfo ci) {
        if (RenderSmokeTestProbe.watchTerrain) RenderSmokeTestProbe.nativeRefreshes++;
    }

    @Inject(method = "render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I", at = @At("RETURN"))
    private void lvb$nativeSections(BlockLayer layer, double delta, int pass, Entity camera,
                                    CallbackInfoReturnable<Integer> result) {
        if (!RenderingPlugin.enhanced() && layer == BlockLayer.TRANSLUCENT) {
            RenderSmokeTestProbe.nativeSections = result.getReturnValue();
            RenderSmokeTestProbe.verifyShaderStage(true);
        }
    }
}
