package io.github.metaconscious.lvb.ornithe;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tessellator;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.opengl.GL11;

/** A barrier-only batch drawn within the world's translucent pass, on the client thread. */
public final class BarrierRenderer {
    private final BarrierRenderState renderState = new BarrierRenderState();
    private Tessellator tessellator;
    private BufferBuilder.State empty;
    private boolean rendering;

    public void render(Minecraft client, Entity camera, double tickDelta) {
        if (!client.isOnSameThread()) {
            throw new IllegalStateException("Barrier rendering requires the client thread");
        }
        if (rendering) {
            throw new IllegalStateException("Barrier rendering is not reentrant");
        }
        if (!OrnitheClient.barriersVisible() || client.world == null) {
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

    private void draw(Minecraft client, Entity camera, double tickDelta) {
        if (tessellator == null) {
            tessellator = new Tessellator(4096);
            empty = tessellator.getBuilder().new State(new int[0], DefaultVertexFormat.BLOCK);
        }
        double cameraX = camera.prevTickX + (camera.x - camera.prevTickX) * tickDelta;
        double cameraY = camera.prevTickY + (camera.y - camera.prevTickY) * tickDelta;
        double cameraZ = camera.prevTickZ + (camera.z - camera.prevTickZ) * tickDelta;
        FrustumCuller frustum = new FrustumCuller();
        frustum.set(cameraX, cameraY, cameraZ);
        int centerX = MathHelper.floor(cameraX) >> 4;
        int centerZ = MathHelper.floor(cameraZ) >> 4;
        int distance = client.options.viewDistance;
        BlockRenderDispatcher models = client.getBlockRenderDispatcher();
        BlockState state = Blocks.BARRIER.defaultState();
        BufferBuilder buffer = tessellator.getBuilder();
        // Quad sorting narrows the integer view; begin() alone does not reset it in 1.8.9.
        buffer.setState(empty);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.BLOCK);
        buffer.offset(-cameraX, -cameraY, -cameraZ);
        try {
            for (int z = centerZ - distance; z <= centerZ + distance; z++) {
                for (int x = centerX - distance; x <= centerX + distance; x++) {
                    WorldChunk chunk = client.world.getChunkAt(x, z);
                    for (BlockPos pos : ((BarrierChunk) chunk).lvb$barriers()) {
                        if (frustum.isVisible(pos.getX(), pos.getY(), pos.getZ(),
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
            tessellator.end();
            return;
        }
        // The caller owns blend, fog, depth, atlas and projection state.
        renderState.capture();
        try {
            client.gameRenderer.enableLightMap();
            tessellator.end();
        } finally {
            renderState.restore(client);
        }
    }
}
