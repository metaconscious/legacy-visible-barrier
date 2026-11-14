package io.github.metaconscious.lvb.ornithe;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tessellator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import io.github.metaconscious.lvb.core.TranslucentOrder;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Builds barrier geometry and orders it with compiled translucent terrain on the client thread. */
public final class BarrierRenderer {
    private static final Method VBO_REGION = vboRegionMethod();
    private final BarrierRenderState renderState = new BarrierRenderState();
    private final TranslucentOrder order = new TranslucentOrder();
    private Tessellator tessellator;
    private BufferBuilder.State empty;
    private boolean rendering;

    public void render(Minecraft client, Entity camera, double tickDelta) {
        renderWithTerrain(client, camera, tickDelta, Collections::emptyList);
    }

    public boolean renderWithTerrain(Minecraft client, Entity camera, double tickDelta,
                                     Supplier<List<TranslucentBatch>> terrain) {
        if (!client.isOnSameThread()) {
            throw new IllegalStateException("Barrier rendering requires the client thread");
        }
        if (rendering) {
            throw new IllegalStateException("Barrier rendering is not reentrant");
        }
        if (!OrnitheClient.barriersVisible() || client.world == null) {
            return false;
        }
        rendering = true;
        try {
            return draw(client, camera, tickDelta, terrain);
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

    public static List<TranslucentBatch> compiledTerrain(List<RenderChunk> chunks) {
        List<TranslucentBatch> terrain = new ArrayList<>(chunks.size());
        for (RenderChunk chunk : chunks) {
            TranslucentBatch batch = ((TranslucentMesh) chunk.getCompiledChunk()).lvb$translucentBatch();
            if (batch != null) {
                batch.transform(inVboRegion(chunk) ? null : chunk::glMultMatrix);
                terrain.add(batch);
            }
        }
        return terrain;
    }

    private static Method vboRegionMethod() {
        try {
            return VertexBuffer.class.getMethod("getVboRegion");
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    private static boolean inVboRegion(RenderChunk chunk) {
        if (VBO_REGION == null || !GLX.useVbo()) {
            return false;
        }
        try {
            return VBO_REGION.invoke(chunk.getBuffer(net.minecraft.client.render.block.BlockLayer.TRANSLUCENT.ordinal())) != null;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect the terrain vertex buffer region", failure);
        }
    }

    private boolean draw(Minecraft client, Entity camera, double tickDelta,
                         Supplier<List<TranslucentBatch>> terrain) {
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
        buffer.end();
        if (buffer.getVertexCount() == 0) {
            return false;
        }
        List<TranslucentBatch> batches = new ArrayList<>(terrain.get());
        TranslucentBatch barriers = new TranslucentBatch(buffer.getState());
        barriers.place(cameraX, cameraY, cameraZ, null);
        batches.add(barriers);
        order.clear();
        for (int i = 0; i < batches.size(); i++) {
            batches.get(i).appendOrder(order, i, cameraX, cameraY, cameraZ);
        }
        // The caller owns blend, fog, depth, atlas and projection state.
        renderState.capture();
        try {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            client.gameRenderer.enableLightMap();
            order.draw((batch, first, count) -> {
                renderState.bindAtlas();
                batches.get(batch).draw(first, count, cameraX, cameraY, cameraZ);
            });
        } finally {
            renderState.restore(client);
        }
        return true;
    }
}
