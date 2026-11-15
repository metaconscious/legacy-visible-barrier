package io.github.metaconscious.lvb.forge.mixin;

import io.github.metaconscious.lvb.forge.RenderingPlugin;
import io.github.metaconscious.lvb.core.RenderingPolicy;
import io.github.metaconscious.lvb.forge.BarrierRenderPass;
import io.github.metaconscious.lvb.forge.BarrierRenderer;
import io.github.metaconscious.lvb.forge.ForgeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(RenderGlobal.class)
public class WorldRendererMixin implements BarrierRenderPass {
    @Unique private BarrierRenderer lvb$renderer;
    @Unique private Entity lvb$camera;
    @Unique private double lvb$tickDelta;
    @Unique private boolean lvb$submitted;

    @Override
    public void lvb$beginBarriers(Entity camera, double tickDelta) {
        if (!RenderingPlugin.enhanced()) return;
        lvb$camera = camera;
        lvb$tickDelta = tickDelta;
        lvb$submitted = false;
    }

    @Override
    public void lvb$endBarriers() {
        Entity camera = lvb$camera;
        lvb$camera = null;
        if (!RenderingPlugin.enhanced()) {
            lvb$renderer = null;
            return;
        }
        if (!lvb$submitted && camera != null) {
            try {
                lvb$renderer().render(Minecraft.getMinecraft(), camera, lvb$tickDelta);
            } catch (BarrierRenderer.Failure failure) {
                RenderingPlugin.report(failure.kind, failure);
                lvb$renderer = null;
            }
        }
    }

    @Redirect(method = "renderBlockLayer(Lnet/minecraft/util/EnumWorldBlockLayer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ChunkRenderContainer;renderChunkLayer(Lnet/minecraft/util/EnumWorldBlockLayer;)V"), require = 0, expect = 0, allow = 1)
    private void lvb$orderedTerrain(ChunkRenderContainer list, EnumWorldBlockLayer layer) {
        if (!RenderingPlugin.enhanced() || layer != EnumWorldBlockLayer.TRANSLUCENT || lvb$camera == null) {
            list.renderChunkLayer(layer);
            return;
        }
        Entity camera = lvb$camera;
        lvb$camera = null;
        lvb$submitted = true;
        if (!ForgeClient.barriersVisible()) {
            list.renderChunkLayer(layer);
            return;
        }
        if (!(list instanceof ChunkRenderListAccess)) {
            RenderingPlugin.report(RenderingPolicy.Failure.ENHANCED,
                    new IllegalStateException("Terrain container has no chunk list capability"));
            list.renderChunkLayer(layer);
            return;
        }
        List<RenderChunk> chunks = ((ChunkRenderListAccess) list).lvb$chunks();
        try {
            if (lvb$renderer().renderChunks(Minecraft.getMinecraft(), camera, lvb$tickDelta, chunks)) {
                chunks.clear();
            } else {
                list.renderChunkLayer(layer);
            }
        } catch (BarrierRenderer.Failure failure) {
            RenderingPlugin.report(failure.kind, failure);
            lvb$renderer = null;
            // A partially drawn translucent pass cannot be replayed without double blending.
            if (failure.submitted) chunks.clear();
            else list.renderChunkLayer(layer);
        }
    }

    @Unique
    private BarrierRenderer lvb$renderer() {
        if (lvb$renderer == null) lvb$renderer = new BarrierRenderer();
        return lvb$renderer;
    }
}
