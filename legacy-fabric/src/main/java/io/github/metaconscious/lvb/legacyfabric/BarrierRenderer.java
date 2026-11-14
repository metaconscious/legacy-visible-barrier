package io.github.metaconscious.lvb.legacyfabric;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.CullingCameraView;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.opengl.GL11;

/** A barrier-only batch drawn within the world's translucent pass, on the client thread. */
public final class BarrierRenderer {
    private final BarrierRenderState renderState = new BarrierRenderState();
    private Tessellator tessellator;
    private BufferBuilder.DrawArrayParameters empty;
    private boolean rendering;

    public void render(MinecraftClient client, Entity camera, double tickDelta) {
        if (!client.isOnThread()) {
            throw new IllegalStateException("Barrier rendering requires the client thread");
        }
        if (rendering) {
            throw new IllegalStateException("Barrier rendering is not reentrant");
        }
        if (!LegacyFabricClient.barriersVisible() || client.world == null) {
            return;
        }
        rendering = true;
        try {
            draw(client, camera, tickDelta);
        } catch (RuntimeException | Error failure) {
            // A failed model or uploader can leave the builder open or partially consumed.
            // Discard only our CPU batch; preserve the exception and allow the next frame to build anew.
            tessellator = null;
            empty = null;
            throw failure;
        } finally {
            rendering = false;
        }
    }

    private void draw(MinecraftClient client, Entity camera, double tickDelta) {
        if (tessellator == null) {
            tessellator = new Tessellator(4096);
            empty = tessellator.getBuffer().new DrawArrayParameters(new int[0], VertexFormats.BLOCK);
        }
        double cameraX = camera.prevTickX + (camera.x - camera.prevTickX) * tickDelta;
        double cameraY = camera.prevTickY + (camera.y - camera.prevTickY) * tickDelta;
        double cameraZ = camera.prevTickZ + (camera.z - camera.prevTickZ) * tickDelta;
        CullingCameraView frustum = new CullingCameraView();
        frustum.setPos(cameraX, cameraY, cameraZ);
        int centerX = MathHelper.floor(cameraX) >> 4;
        int centerZ = MathHelper.floor(cameraZ) >> 4;
        int distance = client.options.viewDistance;
        BlockRenderManager models = client.getBlockRenderManager();
        BlockState state = Blocks.BARRIER.getDefaultState();
        BufferBuilder buffer = tessellator.getBuffer();
        // Quad sorting narrows the integer view; begin() alone does not reset it in 1.8.9.
        buffer.restoreState(empty);
        buffer.begin(GL11.GL_QUADS, VertexFormats.BLOCK);
        buffer.offset(-cameraX, -cameraY, -cameraZ);
        try {
            for (int z = centerZ - distance; z <= centerZ + distance; z++) {
                for (int x = centerX - distance; x <= centerX + distance; x++) {
                    Chunk chunk = client.world.getChunk(x, z);
                    for (BlockPos pos : ((BarrierChunk) chunk).lvb$barriers()) {
                        if (frustum.isBoxInFrustum(pos.getX(), pos.getY(), pos.getZ(),
                                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)) {
                            models.getModelRenderer().render(client.world, models.getModel(state, client.world, pos),
                                    state, pos, buffer);
                        }
                    }
                }
            }
        } finally {
            buffer.offset(0, 0, 0);
        }
        // Vertices already use camera-relative coordinates, preserving precision far from the origin.
        buffer.sortQuads(0, 0, 0);
        if (buffer.getVertexCount() == 0) {
            tessellator.draw();
            return;
        }
        // The caller owns blend, fog, depth, atlas and projection state.
        renderState.capture();
        try {
            client.gameRenderer.enableLightmap();
            tessellator.draw();
        } finally {
            renderState.restore(client);
        }
    }
}
