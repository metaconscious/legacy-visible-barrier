package io.github.metaconscious.lvb.ornithe;

import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.vertex.VertexBuffer;
import io.github.metaconscious.lvb.core.TranslucentOrder;
import io.github.metaconscious.lvb.core.RenderingPolicy;
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
    private Tesselator tessellator;
    private BufferBuilder.State empty;
    private boolean rendering;
    private boolean stateRestored;
    private boolean submitted;
    private RenderingPolicy.Failure phase;

    /** Only raised when CPU buffers are discarded and graphics-state cleanup has completed. */
    public static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public final RenderingPolicy.Failure kind;
        public final boolean submitted;
        private Failure(RenderingPolicy.Failure kind, boolean submitted, RuntimeException cause) {
            super("Barrier rendering failed during " + kind, cause);
            this.kind = kind;
            this.submitted = submitted;
        }
    }

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
        if (!RenderingPlugin.enhanced() || !OrnitheClient.barriersVisible() || client.world == null) {
            return false;
        }
        rendering = true;
        stateRestored = true;
        submitted = false;
        phase = RenderingPolicy.Failure.ENHANCED;
        try {
            return draw(client, camera, tickDelta, terrain);
        } catch (RuntimeException | Error failure) {
            // A failed model or uploader can leave the builder open or partially consumed.
            // Recovery is possible only after the owned graphics state has been restored.
            tessellator = null;
            empty = null;
            if (failure instanceof RuntimeException && stateRestored) {
                throw new Failure(phase, submitted, (RuntimeException) failure);
            }
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
            tessellator = new Tesselator(4096);
            empty = tessellator.getBuffer().new State(new int[0], DefaultVertexFormat.BLOCK);
        }
        double cameraX = camera.prevX + (camera.x - camera.prevX) * tickDelta;
        double cameraY = camera.prevY + (camera.y - camera.prevY) * tickDelta;
        double cameraZ = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
        FrustumCuller frustum = new FrustumCuller();
        frustum.prepare(cameraX, cameraY, cameraZ);
        int centerX = MathHelper.floor(cameraX) >> 4;
        int centerZ = MathHelper.floor(cameraZ) >> 4;
        int distance = client.options.viewDistance;
        BlockRenderDispatcher models = client.getBlockRenderDispatcher();
        BlockState state = Blocks.BARRIER.defaultState();
        BufferBuilder buffer = tessellator.getBuffer();
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
                            phase = RenderingPolicy.Failure.MODEL;
                            models.getModelRenderer().render(client.world, models.getModel(state, client.world, pos),
                                    state, pos, buffer);
                            phase = RenderingPolicy.Failure.ENHANCED;
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
        if (!RenderingPlugin.enhanced()) return false;
        // The caller owns blend, fog, depth, atlas and projection state.
        stateRestored = false;
        renderState.capture();
        try {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            client.gameRenderer.enableLightMap();
            order.draw((batch, first, count) -> {
                renderState.bindAtlas();
                submitted = true;
                batches.get(batch).draw(first, count, cameraX, cameraY, cameraZ);
            });
        } finally {
            renderState.restore(client);
            stateRestored = true;
        }
        return true;
    }
}
