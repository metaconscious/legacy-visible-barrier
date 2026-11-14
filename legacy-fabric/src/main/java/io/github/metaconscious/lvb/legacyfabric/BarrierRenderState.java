package io.github.metaconscious.lvb.legacyfabric;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import java.nio.FloatBuffer;

/** Saves the state touched by Minecraft's lightmap and client-array uploader. Client thread only. */
final class BarrierRenderState {
    private final FloatBuffer color = BufferUtils.createFloatBuffer(16);
    private int activeTexture;
    private int matrixMode;
    private int lightmapTexture;
    private boolean lightmapEnabled;

    void capture() {
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        color.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        // The uploader passes CPU pointers, which require no array buffer to be bound.
        if (GLX.vboSupported) {
            GLX.gl15BindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
        GlStateManager.activeTexture(GL13.GL_TEXTURE1);
        lightmapTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        lightmapEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GlStateManager.activeTexture(GL13.GL_TEXTURE0);
    }

    void restore(MinecraftClient client) {
        if (!lightmapEnabled) {
            client.gameRenderer.disableLightmap();
        }
        GlStateManager.activeTexture(GL13.GL_TEXTURE1);
        GlStateManager.bindTexture(lightmapTexture);
        if (lightmapEnabled) {
            GlStateManager.enableTexture();
        }
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glMatrixMode(matrixMode);
        GlStateManager.activeTexture(activeTexture);
        GL11.glPopClientAttrib();
        GL11.glPopAttrib();
        // Attribute stacks restore raw GL state; reconcile Minecraft's color cache explicitly.
        GlStateManager.clearColor();
        GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
    }
}
