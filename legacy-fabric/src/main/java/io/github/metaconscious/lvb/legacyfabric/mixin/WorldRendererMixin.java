package io.github.metaconscious.lvb.legacyfabric.mixin;

import io.github.metaconscious.lvb.legacyfabric.RenderingPlugin;
import io.github.metaconscious.lvb.core.RenderingPolicy;
import io.github.metaconscious.lvb.legacyfabric.BarrierRenderPass;
import io.github.metaconscious.lvb.legacyfabric.BarrierRenderer;
import io.github.metaconscious.lvb.legacyfabric.LegacyFabricClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.world.AbstractChunkRenderManager;
import net.minecraft.client.world.BuiltChunk;
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
                lvb$renderer().render(MinecraftClient.getInstance(), camera, lvb$tickDelta);
            } catch (BarrierRenderer.Failure failure) {
                RenderingPlugin.report(failure.kind, failure);
                lvb$renderer = null;
            }
        }
    }

    @Redirect(method = "renderLayer(Lnet/minecraft/client/render/RenderLayer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/world/AbstractChunkRenderManager;render(Lnet/minecraft/client/render/RenderLayer;)V"), require = 0, expect = 0, allow = 1)
    private void lvb$orderedTerrain(AbstractChunkRenderManager list, RenderLayer layer) {
        if (!RenderingPlugin.enhanced() || layer != RenderLayer.TRANSLUCENT || lvb$camera == null) {
            list.render(layer);
            return;
        }
        Entity camera = lvb$camera;
        lvb$camera = null;
        lvb$submitted = true;
        if (!LegacyFabricClient.barriersVisible()) {
            list.render(layer);
            return;
        }
        if (!(list instanceof ChunkRenderListAccess)) {
            RenderingPlugin.report(RenderingPolicy.Failure.ENHANCED,
                    new IllegalStateException("Terrain container has no chunk list capability"));
            list.render(layer);
            return;
        }
        List<BuiltChunk> chunks = ((ChunkRenderListAccess) list).lvb$chunks();
        try {
            if (lvb$renderer().renderWithTerrain(MinecraftClient.getInstance(), camera, lvb$tickDelta,
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
