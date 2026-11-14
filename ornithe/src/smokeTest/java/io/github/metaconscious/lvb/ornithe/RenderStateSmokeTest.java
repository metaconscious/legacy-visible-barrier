package io.github.metaconscious.lvb.ornithe;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBVertexArrayObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

/** Exercises state ownership against the active OpenGL context and Minecraft state cache. */
final class RenderStateSmokeTest {
    private RenderStateSmokeTest() {
    }

    static void verify(Minecraft client) {
        verifyForeignState(client, false);
        SmokeTestReport.record("foreign-gl-state-and-failure");
        if (GLContext.getCapabilities().OpenGL30 || GLContext.getCapabilities().GL_ARB_vertex_array_object) {
            verifyForeignState(client, true);
            SmokeTestReport.record("foreign-vao");
        }
    }

    private static void verifyForeignState(Minecraft client, boolean foreignVao) {
        // A world overlay may leave a lightmap, color and vertex array selected for its next draw.
        int texture = GL11.glGenTextures();
        int vbo = GL15.glGenBuffers();
        int ebo = GL15.glGenBuffers();
        int savedVao = foreignVao ? GL11.glGetInteger(ARBVertexArrayObject.GL_VERTEX_ARRAY_BINDING) : 0;
        int vao = foreignVao ? newVertexArray() : 0;
        GlStateManager.activeTexture(GL13.GL_TEXTURE1);
        GlStateManager.enableTexture();
        GlStateManager.bindTexture(texture);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.25F, 0.5F, 0.75F);
        float[] matrix = floats(GL11.GL_TEXTURE_MATRIX);
        GlStateManager.color4f(0.25F, 0.5F, 0.75F, 0.5F);
        float[] color = floats(GL11.GL_CURRENT_COLOR);
        GL13.glClientActiveTexture(GL13.GL_TEXTURE2);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GlStateManager.activeTexture(GL13.GL_TEXTURE2);
        int[] before = glState();
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        if (foreignVao) {
            bindVertexArray(vao);
        }
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, 256, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 4096, GL15.GL_STATIC_DRAW);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 16, 8L);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        // OptiFine's shader uploader uses generic attribute 8 for mid-texture coordinates.
        GL20.glVertexAttribPointer(8, 2, GL11.GL_FLOAT, false, 16, 8L);
        GL20.glEnableVertexAttribArray(8);
        int[] attribute = vertexAttribute(8);
        try {
            BarrierRenderer renderer = new BarrierRenderer();
            RenderSmokeTestProbe.failLightmap = true;
            RenderSmokeTestProbe.expectFailure(() -> renderer.render(client, client.player, 1),
                    RenderSmokeTestProbe.InjectedFailure.class);
            require(!RenderSmokeTestProbe.failLightmap, "Upload failure was not exercised");
            require(Arrays.equals(before, glState()), "Failed upload changed foreign GL state");
            renderer.render(client, client.player, 1);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error with foreign VAO=" + foreignVao);
            if (foreignVao) {
                require(GL11.glGetInteger(ARBVertexArrayObject.GL_VERTEX_ARRAY_BINDING) == vao,
                        "Barrier pass lost foreign VAO");
            }
            require(GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING) == ebo,
                    "Barrier pass changed element buffer");
            require(Arrays.equals(before, glState()), "Barrier pass changed foreign GL state");
            require(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) == vbo, "Barrier pass lost VBO binding");
            require(GL11.glIsEnabled(GL11.GL_VERTEX_ARRAY), "Barrier pass disabled foreign vertex array");
            require(GL11.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE) == 16, "Barrier pass replaced foreign vertex pointer");
            require(GL11.glGetInteger(GL15.GL_VERTEX_ARRAY_BUFFER_BINDING) == vbo, "Barrier pass replaced vertex array buffer");
            require(Arrays.equals(attribute, vertexAttribute(8)), "Barrier pass changed generic vertex attributes");
            require(Arrays.equals(color, floats(GL11.GL_CURRENT_COLOR)), "Barrier pass changed current color");
            GlStateManager.activeTexture(GL13.GL_TEXTURE1);
            require(GL11.glIsEnabled(GL11.GL_TEXTURE_2D), "Barrier pass disabled foreign lightmap");
            require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == texture, "Barrier pass replaced foreign lightmap");
            require(Arrays.equals(matrix, floats(GL11.GL_TEXTURE_MATRIX)), "Barrier pass changed lightmap matrix");
            // Check the cached color agrees with the restored OpenGL color.
            GlStateManager.color4f(1, 1, 1, 1);
            require(floats(GL11.GL_CURRENT_COLOR)[0] == 1, "GlStateManager color cache diverged");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error with foreign vertex state");
        } finally {
            if (foreignVao) {
                bindVertexArray(savedVao);
                deleteVertexArray(vao);
            }
            GL11.glPopClientAttrib();
            GlStateManager.activeTexture(GL13.GL_TEXTURE1);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GlStateManager.bindTexture(0);
            GlStateManager.disableTexture();
            GlStateManager.activeTexture(GL13.GL_TEXTURE0);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GlStateManager.color4f(1, 1, 1, 1);
            GL15.glDeleteBuffers(vbo);
            GL15.glDeleteBuffers(ebo);
            GL11.glDeleteTextures(texture);
        }
    }

    private static int newVertexArray() {
        return GLContext.getCapabilities().OpenGL30 ? GL30.glGenVertexArrays()
                : ARBVertexArrayObject.glGenVertexArrays();
    }

    private static void bindVertexArray(int vao) {
        if (GLContext.getCapabilities().OpenGL30) {
            GL30.glBindVertexArray(vao);
        } else {
            ARBVertexArrayObject.glBindVertexArray(vao);
        }
    }

    private static void deleteVertexArray(int vao) {
        if (GLContext.getCapabilities().OpenGL30) {
            GL30.glDeleteVertexArrays(vao);
        } else {
            ARBVertexArrayObject.glDeleteVertexArrays(vao);
        }
    }

    private static float[] floats(int name) {
        FloatBuffer values = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(name, values);
        float[] result = new float[16];
        values.get(result);
        return result;
    }

    private static int[] vertexAttribute(int index) {
        int[] properties = {GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE,
                GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE,
                GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING};
        int[] result = new int[properties.length];
        IntBuffer value = BufferUtils.createIntBuffer(4);
        try {
            java.lang.reflect.Method query;
            try {
                query = GL20.class.getMethod("glGetVertexAttrib", int.class, int.class, IntBuffer.class);
            } catch (NoSuchMethodException legacyNameAbsent) {
                // Legacy LWJGL3 exposes this query under the LWJGL3 name.
                query = GL20.class.getMethod("glGetVertexAttribiv", int.class, int.class, IntBuffer.class);
            }
            for (int i = 0; i < properties.length; i++) {
                query.invoke(null, index, properties[i], value);
                result[i] = value.get(0);
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect generic vertex attributes", failure);
        }
        return result;
    }

    static int[] glState() {
        return new int[]{GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), GL11.glGetInteger(GL11.GL_DEPTH_FUNC), GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
                GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE), GL11.glGetInteger(GL11.GL_MATRIX_MODE),
                GL11.glIsEnabled(GL11.GL_BLEND) ? 1 : 0, GL11.glIsEnabled(GL11.GL_CULL_FACE) ? 1 : 0,
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST) ? 1 : 0, GL11.glIsEnabled(GL11.GL_ALPHA_TEST) ? 1 : 0};
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
