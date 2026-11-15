package io.github.metaconscious.lvb.ornithe;

import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBVertexArrayObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.nio.FloatBuffer;

/** Saves the state touched by Minecraft's lightmap and client-array uploader. Client thread only. */
final class BarrierRenderState {
    private final FloatBuffer color = BufferUtils.createFloatBuffer(16);
    private int activeTexture;
    private int matrixMode;
    private int lightmapTexture;
    private int atlasTexture;
    private boolean lightmapEnabled;
    private boolean coreVertexArrays;
    private boolean vertexArrays;
    private int vertexArray;

    void capture() {
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        color.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        ContextCapabilities capabilities = GLContext.getCapabilities();
        coreVertexArrays = capabilities.OpenGL30;
        vertexArrays = coreVertexArrays || capabilities.GL_ARB_vertex_array_object;
        if (vertexArrays) {
            vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            // CPU vertex pointers are only valid on the default VAO. Save its client state
            // after selecting it, so the incoming VAO's attributes and element buffer stay intact.
            bindVertexArray(0);
        }
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        // The uploader passes CPU pointers, which require no array buffer to be bound.
        if (GLX.useVbos) {
            GLX.bindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
        GlStateManager.activeTexture(GL13.GL_TEXTURE1);
        lightmapTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        lightmapEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GlStateManager.activeTexture(GL13.GL_TEXTURE0);
        atlasTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    void bindAtlas() {
        GlStateManager.activeTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(atlasTexture);
        // OptiFine's multi-texture uploader binds sprites without updating Minecraft's cache.
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTexture);
    }

    void restore(Minecraft client) {
        if (!lightmapEnabled) {
            client.gameRenderer.disableLightMap();
        }
        GlStateManager.activeTexture(GL13.GL_TEXTURE1);
        GlStateManager.bindTexture(lightmapTexture);
        if (lightmapEnabled) {
            GlStateManager.enableTexture();
        }
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glMatrixMode(matrixMode);
        bindAtlas();
        GlStateManager.activeTexture(activeTexture);
        GL11.glPopClientAttrib();
        if (vertexArrays) {
            bindVertexArray(vertexArray);
        }
        GL11.glPopAttrib();
        // Attribute stacks restore raw GL state; reconcile Minecraft's color cache explicitly.
        GlStateManager.clearColor();
        GlStateManager.color4f(color.get(0), color.get(1), color.get(2), color.get(3));
    }

    private void bindVertexArray(int array) {
        if (coreVertexArrays) {
            GL30.glBindVertexArray(array);
        } else {
            ARBVertexArrayObject.glBindVertexArray(array);
        }
    }
}
