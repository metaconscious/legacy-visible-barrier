package io.github.metaconscious.lvb.ornithe.mixin;

import io.github.metaconscious.lvb.ornithe.RenderingPlugin;
import io.github.metaconscious.lvb.core.RenderingPolicy;
import io.github.metaconscious.lvb.ornithe.BarrierRenderPass;
import io.github.metaconscious.lvb.ornithe.BarrierRenderer;
import io.github.metaconscious.lvb.ornithe.OrnitheClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.render.world.RenderChunkList;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(WorldRenderer.class)
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
                lvb$renderer().render(Minecraft.getInstance(), camera, lvb$tickDelta);
            } catch (BarrierRenderer.Failure failure) {
                RenderingPlugin.report(failure.kind, failure);
                lvb$renderer = null;
            }
        }
    }

    @Redirect(method = "renderLastChunks(Lnet/minecraft/client/render/block/BlockLayer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/world/RenderChunkList;render(Lnet/minecraft/client/render/block/BlockLayer;)V"), require = 0, expect = 0, allow = 1)
    private void lvb$orderedTerrain(RenderChunkList list, BlockLayer layer) {
        if (!RenderingPlugin.enhanced() || layer != BlockLayer.TRANSLUCENT || lvb$camera == null) {
            list.render(layer);
            return;
        }
        Entity camera = lvb$camera;
        lvb$camera = null;
        lvb$submitted = true;
        if (!OrnitheClient.barriersVisible()) {
            list.render(layer);
            return;
        }
        if (!(list instanceof ChunkRenderListAccess)) {
            RenderingPlugin.report(RenderingPolicy.Failure.ENHANCED,
                    new IllegalStateException("Terrain container has no chunk list capability"));
            list.render(layer);
            return;
        }
        List<RenderChunk> chunks = ((ChunkRenderListAccess) list).lvb$chunks();
        try {
            if (lvb$renderer().renderWithTerrain(Minecraft.getInstance(), camera, lvb$tickDelta,
                    () -> BarrierRenderer.compiledTerrain(chunks))) {
                chunks.clear();
            } else {
                list.render(layer);
            }
        } catch (BarrierRenderer.Failure failure) {
            RenderingPlugin.report(failure.kind, failure);
            lvb$renderer = null;
            // A partially drawn translucent pass cannot be replayed without double blending.
            if (failure.submitted) chunks.clear();
            else list.render(layer);
        }
    }

    @Unique
    private BarrierRenderer lvb$renderer() {
        if (lvb$renderer == null) lvb$renderer = new BarrierRenderer();
        return lvb$renderer;
    }
}
