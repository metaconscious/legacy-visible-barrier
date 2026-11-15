package io.github.metaconscious.lvb.forge;

import io.github.metaconscious.lvb.core.TranslucentOrder;
import io.github.metaconscious.lvb.core.RenderingPolicy;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.WorldRenderer.State;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.chunk.Chunk;
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
    private WorldRenderer.State empty;
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
        if (!client.isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Barrier rendering requires the client thread");
        }
        if (rendering) {
            throw new IllegalStateException("Barrier rendering is not reentrant");
        }
        if (!RenderingPlugin.enhanced() || !ForgeClient.barriersVisible() || client.theWorld == null) {
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

    public boolean renderChunks(Minecraft client, Entity camera, double tickDelta, List<RenderChunk> chunks) {
        return renderWithTerrain(client, camera, tickDelta, () -> compiledTerrain(chunks));
    }

    public static List<TranslucentBatch> compiledTerrain(List<RenderChunk> chunks) {
        List<TranslucentBatch> terrain = new ArrayList<>(chunks.size());
        for (RenderChunk chunk : chunks) {
            TranslucentBatch batch = ((TranslucentMesh) chunk.getCompiledChunk()).lvb$translucentBatch();
            if (batch != null) {
                batch.transform(inVboRegion(chunk) ? null : chunk::multModelviewMatrix);
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
        if (VBO_REGION == null || !OpenGlHelper.useVbo()) {
            return false;
        }
        try {
            return VBO_REGION.invoke(chunk.getVertexBufferByLayer(net.minecraft.util.EnumWorldBlockLayer.TRANSLUCENT.ordinal())) != null;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect the terrain vertex buffer region", failure);
        }
    }

    private boolean draw(Minecraft client, Entity camera, double tickDelta,
                         Supplier<List<TranslucentBatch>> terrain) {
        if (tessellator == null) {
            tessellator = new Tessellator(4096);
            empty = tessellator.getWorldRenderer().new State(new int[0], DefaultVertexFormats.BLOCK);
        }
        double cameraX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * tickDelta;
        double cameraY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * tickDelta;
        double cameraZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * tickDelta;
        Frustum frustum = new Frustum();
        frustum.setPosition(cameraX, cameraY, cameraZ);
        int centerX = MathHelper.floor_double(cameraX) >> 4;
        int centerZ = MathHelper.floor_double(cameraZ) >> 4;
        int distance = client.gameSettings.renderDistanceChunks;
        BlockRendererDispatcher models = client.getBlockRendererDispatcher();
        IBlockState state = Blocks.barrier.getDefaultState();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        // Quad sorting narrows the integer view; begin() alone does not reset it in 1.8.9.
        buffer.setVertexState(empty);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        buffer.setTranslation(-cameraX, -cameraY, -cameraZ);
        try {
            for (int z = centerZ - distance; z <= centerZ + distance; z++) {
                for (int x = centerX - distance; x <= centerX + distance; x++) {
                    Chunk chunk = client.theWorld.getChunkFromChunkCoords(x, z);
                    for (BlockPos pos : ((BarrierChunk) chunk).lvb$barriers()) {
                        if (frustum.isBoxInFrustum(pos.getX(), pos.getY(), pos.getZ(),
                                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)) {
                            phase = RenderingPolicy.Failure.MODEL;
                            models.getBlockModelRenderer().renderModel(client.theWorld, models.getModelFromBlockState(state, client.theWorld, pos),
                                    state, pos, buffer);
                            phase = RenderingPolicy.Failure.ENHANCED;
                        }
                    }
                }
            }
        } finally {
            buffer.setTranslation(0, 0, 0);
        }
        // Vertices already use camera-relative coordinates, preserving precision far from the origin.
        buffer.sortVertexData(0, 0, 0);
        buffer.finishDrawing();
        if (buffer.getVertexCount() == 0) {
            return false;
        }
        List<TranslucentBatch> batches = new ArrayList<>(terrain.get());
        TranslucentBatch barriers = new TranslucentBatch(buffer.getVertexState());
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
            client.entityRenderer.enableLightmap();
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
