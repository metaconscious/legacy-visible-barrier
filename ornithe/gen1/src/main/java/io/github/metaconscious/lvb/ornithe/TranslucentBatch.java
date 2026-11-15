package io.github.metaconscious.lvb.ornithe;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import io.github.metaconscious.lvb.core.TranslucentOrder;
import net.minecraft.client.render.block.BlockLayer;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/** Immutable compiled vertices; range selection and placement are confined to the client thread. */
public final class TranslucentBatch extends BufferBuilder {
    private static final BufferUploader UPLOADER = new BufferUploader();
    private static final Method BLOCK_LAYER = blockLayerMethod();
    private final float[] centres;
    private double originX;
    private double originY;
    private double originZ;
    private Runnable transform;
    private boolean drawingRange;
    private int firstQuad;
    private int quadCount;

    public TranslucentBatch(BufferBuilder.State state) {
        super(Math.max(16, state.getBuffer().length));
        selectTranslucentLayer();
        begin(GL11.GL_QUADS, state.getFormat());
        setState(state);
        end();
        VertexOrigin origin = (VertexOrigin) state;
        place(origin.lvb$originX(), origin.lvb$originY(), origin.lvb$originZ(), null);
        int stride = getFormat().getVertexSize();
        centres = new float[super.getVertexCount() / 4 * 3];
        ByteBuffer vertices = super.getBuffer();
        for (int quad = 0; quad < centres.length / 3; quad++) {
            for (int axis = 0; axis < 3; axis++) {
                float sum = 0;
                for (int vertex = 0; vertex < 4; vertex++) {
                    sum += vertices.getFloat((quad * 4 + vertex) * stride + axis * 4);
                }
                centres[quad * 3 + axis] = sum * 0.25F;
            }
        }
    }

    private static Method blockLayerMethod() {
        try {
            return BufferBuilder.class.getMethod("setBlockLayer", BlockLayer.class);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    private void selectTranslucentLayer() {
        if (BLOCK_LAYER != null) {
            try {
                // OptiFine must preserve quad order instead of grouping opaque sprites.
                BLOCK_LAYER.invoke(this, BlockLayer.TRANSLUCENT);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot select the translucent vertex layer", failure);
            }
        }
    }

    public void place(double x, double y, double z, Runnable matrix) {
        originX = x;
        originY = y;
        originZ = z;
        transform = matrix;
    }

    void transform(Runnable matrix) {
        transform = matrix;
    }

    void appendOrder(TranslucentOrder order, int batch, double cameraX, double cameraY, double cameraZ) {
        for (int quad = 0; quad < centres.length / 3; quad++) {
            order.add(batch, quad, (float) (centres[quad * 3] + originX - cameraX),
                    (float) (centres[quad * 3 + 1] + originY - cameraY),
                    (float) (centres[quad * 3 + 2] + originZ - cameraZ));
        }
    }

    void draw(int first, int count, double cameraX, double cameraY, double cameraZ) {
        firstQuad = first;
        quadCount = count;
        drawingRange = true;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translated(originX - cameraX, originY - cameraY, originZ - cameraZ);
            if (transform != null) {
                transform.run();
            }
            UPLOADER.end(this);
        } finally {
            GlStateManager.popMatrix();
            drawingRange = false;
        }
    }

    @Override
    public ByteBuffer getBuffer() {
        ByteBuffer buffer = super.getBuffer();
        if (!drawingRange) {
            return buffer;
        }
        int verticesPerQuad = getDrawMode() == GL11.GL_TRIANGLES ? 6 : 4;
        int stride = getFormat().getVertexSize();
        ByteBuffer range = buffer.duplicate().order(buffer.order());
        range.position(firstQuad * verticesPerQuad * stride);
        range.limit((firstQuad + quadCount) * verticesPerQuad * stride);
        return range.slice().order(buffer.order());
    }

    @Override
    public int getVertexCount() {
        return drawingRange ? quadCount * (getDrawMode() == GL11.GL_TRIANGLES ? 6 : 4) : super.getVertexCount();
    }

    @Override
    public void clear() {
        if (!drawingRange) {
            super.clear();
        }
    }

    /** OptiFine's multi-texture uploader groups quads by sprite; clip each group to this range. */
    public void drawTextureRange(int start, int end) {
        int first = Math.max(start, firstQuad);
        int last = Math.min(end, firstQuad + quadCount);
        if (last > first) {
            GL11.glDrawArrays(GL11.GL_QUADS, (first - firstQuad) * 4, (last - first) * 4);
        }
    }
}
