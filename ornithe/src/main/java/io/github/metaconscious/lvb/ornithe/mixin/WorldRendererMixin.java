package io.github.metaconscious.lvb.ornithe.mixin;

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
    @Unique private final BarrierRenderer lvb$renderer = new BarrierRenderer();
    @Unique private Entity lvb$camera;
    @Unique private double lvb$tickDelta;
    @Unique private boolean lvb$submitted;

    @Override
    public void lvb$beginBarriers(Entity camera, double tickDelta) {
        lvb$camera = camera;
        lvb$tickDelta = tickDelta;
        lvb$submitted = false;
    }

    @Override
    public void lvb$endBarriers() {
        Entity camera = lvb$camera;
        lvb$camera = null;
        if (!lvb$submitted && camera != null) {
            lvb$renderer.render(Minecraft.getInstance(), camera, lvb$tickDelta);
        }
    }

    @Redirect(method = "render(Lnet/minecraft/client/render/block/BlockLayer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/world/RenderChunkList;render(Lnet/minecraft/client/render/block/BlockLayer;)V"))
    private void lvb$orderedTerrain(RenderChunkList list, BlockLayer layer) {
        if (layer != BlockLayer.TRANSLUCENT || lvb$camera == null) {
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
        List<RenderChunk> chunks = ((ChunkRenderListAccess) list).lvb$chunks();
        if (lvb$renderer.renderWithTerrain(Minecraft.getInstance(), camera, lvb$tickDelta,
                () -> BarrierRenderer.compiledTerrain(chunks))) {
            chunks.clear();
        } else {
            list.render(layer);
        }
    }
}
