package io.github.metaconscious.lvb.legacyfabric;

import com.mojang.blaze3d.platform.GlStateManager;
import net.fabricmc.api.ClientModInitializer;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.stat.StatHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ClientChunkProvider;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** Exercises the installed client hooks, model tessellation and OpenGL output in a test world. */
public final class ClientSmokeTest implements ClientModInitializer {
    private static final BarrierRenderer BARRIERS = new BarrierRenderer();
    private static final int WIDTH = 128;
    private static final int HEIGHT = 64;
    private static final BlockPos FIRST = new BlockPos(0, 64, 0);
    private static final BlockPos SECOND = new BlockPos(1, 64, 0);
    private static final BlockPos STONE = new BlockPos(3, 64, 0);
    private static final BlockPos OCCLUDED = new BlockPos(3, 64, -2);
    private KeyBinding binding;
    private boolean testingWorld;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::verify);
    }

    private void verify(MinecraftClient client) {
        if (testingWorld) {
            return;
        }
        try {
            require(!LegacyFabricClient.barriersVisible(), "Visibility must start hidden");
            require(client.world == null && client.player == null, "Client must start outside a world");
            if (binding == null) {
                new LegacyFabricClient().onInitializeClient();
                int count = 0;
                for (KeyBinding key : client.options.allKeys) {
                    if ("key.toggle_barrier_visibility".equals(key.getTranslationKey())) {
                        binding = key;
                        count++;
                    }
                }
                require(count == 1, "Exactly one toggle control must be registered");
                require(binding.getDefaultCode() == Keyboard.KEY_B, "Default control must be B");
                require("key.category.legacy_visible_barriers".equals(binding.getCategory()), "Control category changed");
                require(Blocks.BARRIER.getRenderLayerType() == RenderLayer.TRANSLUCENT, "Barrier must be translucent");
                Class.forName(net.minecraft.client.particle.BarrierParticle.class.getName());
                Class.forName(net.minecraft.client.BlockStateMapper.class.getName());
                KeyBinding.onKeyPressed(binding.getCode());
                return;
            }
            require(!binding.wasPressed(), "Queued input outside a world must be consumed");
            testingWorld = true;
            verifyWorld(client);
            Path report = Paths.get(System.getProperty("lvb.smoke.report"));
            Files.createDirectories(report.getParent());
            Files.write(report, ("PASS: Java " + System.getProperty("java.version")
                    + "; startup and B control; hidden terrain type; barrier-only pixels and models"
                    + "; adjacent-face culling; opaque occlusion; foreign GL state; replaced terrain renderer; zero toggle terrain invalidations"
                    + "; block and chunk packets; unload/reload; world switch; resource reload; VBO on/off\n")
                    .getBytes(StandardCharsets.UTF_8));
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private void verifyWorld(MinecraftClient client) {
        openWorld(client);
        populate(client);
        // Initialize the real world lightmap and camera before checking the isolated pass.
        client.gameRenderer.tick();
        client.gameRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
        for (boolean vbo : new boolean[]{false, true}) {
            client.options.vbo = vbo;
            client.worldRenderer.reload();
            RenderSmokeTestProbe.watchTerrain = true;
            for (int i = 0; i < 3; i++) {
                byte[] hidden = frame(client, 0, 0);
                int[] hiddenState = glState();
                toggle(client);
                byte[] visible = frame(client, 3, 64);
                require(Arrays.equals(hiddenState, glState()), "Visibility changed OpenGL state");
                int changed = 0;
                for (int y = 0; y < HEIGHT; y++) {
                    for (int x = 0; x < WIDTH; x++) {
                        int offset = (y * WIDTH + x) * 4;
                        boolean different = false;
                        for (int channel = 0; channel < 3; channel++) {
                            different |= hidden[offset + channel] != visible[offset + channel];
                        }
                        if (different) {
                            require(x >= 40 && x < 72 && y >= 24 && y < 40,
                                    "A toggle changed pixels outside the two exposed barriers: " + x + "," + y);
                            changed++;
                        }
                    }
                }
                require(changed > 0, "Visible barriers did not draw any pixels");
                toggle(client);
                require(Arrays.equals(hidden, frame(client, 0, 0)), "Hiding barriers changed the base scene");
            }
            require(RenderSmokeTestProbe.invalidations == 0, "Toggling invalidated terrain");
            RenderSmokeTestProbe.watchTerrain = false;
            toggle(client);
            verifyFrameHook(client);
            toggle(client);
        }
        toggle(client);
        verifyForeignState(client);
        client.world.setBlockState(SECOND, Blocks.AIR.getDefaultState(), 2);
        frame(client, 2, 48);
        client.world.setBlockState(SECOND, Blocks.BARRIER.getDefaultState(), 2);
        frame(client, 3, 64);

        cache(client).getOrGenerateChunk(1, 0);
        BlockPos edge = new BlockPos(15, 64, 0);
        BlockPos neighbor = new BlockPos(16, 64, 0);
        client.world.setBlockState(edge, Blocks.BARRIER.getDefaultState(), 2);
        client.world.setBlockState(neighbor, Blocks.BARRIER.getDefaultState(), 2);
        client.player.x = client.player.prevTickX = 16;
        frame(client, 2, 40);
        client.world.setBlockState(edge, Blocks.AIR.getDefaultState(), 2);
        client.world.setBlockState(neighbor, Blocks.AIR.getDefaultState(), 2);
        client.player.x = client.player.prevTickX = 1.5;
        BlockPos behindCamera = new BlockPos(0, 64, 8);
        client.world.setBlockState(behindCamera, Blocks.BARRIER.getDefaultState(), 2);
        frame(client, 3, 64);
        client.world.setBlockState(behindCamera, Blocks.AIR.getDefaultState(), 2);

        // Full packets replace old sections; partial packets preserve sections outside their mask.
        Chunk replacement = new Chunk(client.world, 0, 0);
        replacement.getBlockState(SECOND, Blocks.BARRIER.getDefaultState());
        applyPacket(client.world.getChunk(0, 0), new ChunkDataS2CPacket(replacement, true, 0xffff));
        frame(client, 2, 48);
        client.world.setBlockState(new BlockPos(0, 80, 0), Blocks.BARRIER.getDefaultState(), 2);
        replacement.getBlockState(SECOND, Blocks.AIR.getDefaultState());
        applyPacket(client.world.getChunk(0, 0), new ChunkDataS2CPacket(replacement, false, 1 << 4));
        frame(client, 1, 24);
        client.player.y = client.player.prevTickY = 80.5;
        frame(client, 1, 24);
        client.player.y = client.player.prevTickY = 64.5;
        cache(client).unloadChunk(0, -1);
        frame(client, 0, 0);
        cache(client).unloadChunk(0, 0);
        populate(client);
        frame(client, 3, 64);
        client.reloadResources();
        require(LegacyFabricClient.barriersVisible(), "Resource reload reset visibility");
        frame(client, 3, 64);
        openWorld(client);
        require(LegacyFabricClient.barriersVisible(), "World switch reset visibility");
        frame(client, 0, 0);
    }

    private void openWorld(MinecraftClient client) {
        ClientPlayNetworkHandler network = new ClientPlayNetworkHandler(client, null,
                new ClientConnection(NetworkSide.CLIENTBOUND), client.getSession().getProfile());
        client.world = new ClientWorld(network, new LevelInfo(0L, LevelInfo.GameMode.CREATIVE,
                false, false, LevelGeneratorType.DEFAULT), 0, Difficulty.PEACEFUL, client.profiler);
        client.player = new ClientPlayerEntity(client, client.world, network, new StatHandler());
        client.interactionManager = new ClientPlayerInteractionManager(client, network);
        client.player.x = client.player.prevTickX = 1.5;
        client.player.y = client.player.prevTickY = 64.5;
        client.player.z = client.player.prevTickZ = 4;
        client.options.viewDistance = 2;
        client.setCameraEntity(client.player);
        client.worldRenderer.setWorld(client.world);
        client.particleManager.setWorld(client.world);
    }

    private static ClientChunkProvider cache(MinecraftClient client) {
        return (ClientChunkProvider) client.world.getChunkProvider();
    }

    private static void populate(MinecraftClient client) {
        cache(client).getOrGenerateChunk(0, 0);
        cache(client).getOrGenerateChunk(0, -1);
        client.world.setBlockState(FIRST, Blocks.BARRIER.getDefaultState(), 2);
        client.world.setBlockState(SECOND, Blocks.BARRIER.getDefaultState(), 2);
        client.world.setBlockState(STONE, Blocks.STONE.getDefaultState(), 2);
        client.world.setBlockState(OCCLUDED, Blocks.BARRIER.getDefaultState(), 2);
    }

    private void toggle(MinecraftClient client) {
        boolean previous = LegacyFabricClient.barriersVisible();
        KeyBinding.onKeyPressed(binding.getCode());
        ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(client);
        require(LegacyFabricClient.barriersVisible() != previous, "B did not toggle visibility");
        require(!binding.wasPressed(), "B was not consumed");
        require(Blocks.BARRIER.getBlockType() == -1, "Barriers entered terrain meshes");
    }

    private static void applyPacket(Chunk chunk, ChunkDataS2CPacket packet) {
        chunk.method_3895(packet.method_7757(), packet.method_7760(), packet.shouldLoad());
    }

    private static byte[] frame(MinecraftClient client, int expectedModels, int expectedVertices) {
        GL11.glViewport(0, 0, WIDTH, HEIGHT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(-4, 4, -2, 2, 0.1, 32);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GlStateManager.depthMask(true);
        GlStateManager.colorMask(true, true, true, true);
        GL11.glClearColor(0.8F, 0.8F, 0.8F, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableDepthTest();
        GlStateManager.disableBlend();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableCull();
        GlStateManager.enableTexture();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.enableAlphaTest();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.color(1, 1, 1, 1);
        client.getTextureManager().bindTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEX);
        BlockRenderManager models = client.getBlockRenderManager();
        Tessellator terrain = new Tessellator(256);
        BufferBuilder buffer = terrain.getBuffer();
        buffer.begin(GL11.GL_QUADS, VertexFormats.BLOCK);
        buffer.offset(-client.player.x, -client.player.y, -client.player.z);
        models.renderBlock(Blocks.STONE.getDefaultState(), STONE, client.world, buffer);
        terrain.draw();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        RenderSmokeTestProbe.resetModels();
        RenderSmokeTestProbe.watchModels = true;
        BARRIERS.render(client, client.player, 1);
        RenderSmokeTestProbe.watchModels = false;
        require(RenderSmokeTestProbe.otherBlocks == 0, "Barrier pass tessellated another block type");
        require(RenderSmokeTestProbe.barriers == expectedModels,
                "Expected " + expectedModels + " barrier models, got " + RenderSmokeTestProbe.barriers);
        require(RenderSmokeTestProbe.vertices == expectedVertices,
                "Expected " + expectedVertices + " vertices, got " + RenderSmokeTestProbe.vertices);
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error in barrier pass");
        ByteBuffer pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        GL11.glReadPixels(0, 0, WIDTH, HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        byte[] result = new byte[pixels.remaining()];
        pixels.get(result);
        return result;
    }

    private static void verifyFrameHook(MinecraftClient client) {
        // A replacement renderer can cancel the vanilla method at HEAD.
        client.player.yaw = client.player.prevYaw = 180;
        client.player.pitch = client.player.prevPitch = 20;
        for (boolean replacement : new boolean[]{false, true}) {
            RenderSmokeTestProbe.cancelTerrainLayer = replacement;
            RenderSmokeTestProbe.resetModels();
            RenderSmokeTestProbe.watchModels = true;
            try {
                client.gameRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
                require(RenderSmokeTestProbe.barriers > 0,
                        "World frame bypassed barrier pass (replacement=" + replacement + ")");
            } finally {
                RenderSmokeTestProbe.watchModels = false;
                RenderSmokeTestProbe.cancelTerrainLayer = false;
            }
        }
    }

    private static void verifyForeignState(MinecraftClient client) {
        // A world overlay may leave a lightmap, color and vertex array selected for its next draw.
        int texture = GL11.glGenTextures();
        int vbo = GL15.glGenBuffers();
        GlStateManager.activeTexture(GL13.GL_TEXTURE1);
        GlStateManager.enableTexture();
        GlStateManager.bindTexture(texture);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.25F, 0.5F, 0.75F);
        float[] matrix = floats(GL11.GL_TEXTURE_MATRIX);
        GlStateManager.color(0.25F, 0.5F, 0.75F, 0.5F);
        float[] color = floats(GL11.GL_CURRENT_COLOR);
        GL13.glClientActiveTexture(GL13.GL_TEXTURE2);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GlStateManager.activeTexture(GL13.GL_TEXTURE2);
        int[] before = glState();
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 4096, GL15.GL_STATIC_DRAW);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 16, 8L);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        // OptiFine's shader uploader uses generic attribute 8 for mid-texture coordinates.
        GL20.glVertexAttribPointer(8, 2, GL11.GL_FLOAT, false, 16, 8L);
        GL20.glEnableVertexAttribArray(8);
        int[] attribute = vertexAttribute(8);
        try {
            new BarrierRenderer().render(client, client.player, 1);
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
            GlStateManager.color(1, 1, 1, 1);
            require(floats(GL11.GL_CURRENT_COLOR)[0] == 1, "GlStateManager color cache diverged");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error with foreign vertex state");
        } finally {
            GL11.glPopClientAttrib();
            GlStateManager.activeTexture(GL13.GL_TEXTURE1);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GlStateManager.bindTexture(0);
            GlStateManager.disableTexture();
            GlStateManager.activeTexture(GL13.GL_TEXTURE0);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GlStateManager.color(1, 1, 1, 1);
            GL15.glDeleteBuffers(vbo);
            GL11.glDeleteTextures(texture);
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
        for (int i = 0; i < properties.length; i++) {
            GL20.glGetVertexAttrib(index, properties[i], value);
            result[i] = value.get(0);
        }
        return result;
    }

    private static int[] glState() {
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
