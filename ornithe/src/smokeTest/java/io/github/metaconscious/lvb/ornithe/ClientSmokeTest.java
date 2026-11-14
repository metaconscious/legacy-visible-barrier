package io.github.metaconscious.lvb.ornithe;

import com.mojang.blaze3d.platform.GlStateManager;
import net.fabricmc.api.ClientModInitializer;
import net.ornithemc.osl.lifecycle.api.client.MinecraftClientEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.client.options.KeyBinding;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.render.block.BlockLayer;
import com.mojang.blaze3d.vertex.Tessellator;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketFlow;
import net.minecraft.network.packet.s2c.play.WorldChunkS2CPacket;
import net.minecraft.stat.PlayerStats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.client.world.chunk.ClientChunkCache;
import net.minecraft.world.gen.WorldGeneratorType;
import net.minecraft.world.WorldSettings;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
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
        MinecraftClientEvents.TICK_END.register(this::verify);
    }

    private void verify(Minecraft client) {
        if (testingWorld) {
            return;
        }
        try {
            require(!OrnitheClient.barriersVisible(), "Visibility must start hidden");
            require(client.world == null && client.player == null, "Client must start outside a world");
            if (binding == null) {
                new OrnitheClient().onInitializeClient();
                int count = 0;
                for (KeyBinding key : client.options.keyBindings) {
                    if ("key.toggle_barrier_visibility".equals(key.getName())) {
                        binding = key;
                        count++;
                    }
                }
                require(count == 1, "Exactly one toggle control must be registered");
                require(binding.getDefaultKeyCode() == Keyboard.KEY_B, "Default control must be B");
                require("key.category.legacy_visible_barriers".equals(binding.getCategory()), "Control category changed");
                require(Blocks.BARRIER.getRenderLayer() == BlockLayer.TRANSLUCENT, "Barrier must be translucent");
                Class.forName(net.minecraft.client.entity.particle.BarrierParticle.class.getName());
                Class.forName(net.minecraft.client.resource.model.BlockModels.class.getName());
                KeyBinding.click(binding.getKeyCode());
                return;
            }
            require(!binding.consumeClick(), "Queued input outside a world must be consumed");
            SmokeTestReport.record("startup-controls");
            testingWorld = true;
            verifyWorld(client);
            Path report = Paths.get(System.getProperty("lvb.smoke.report"));
            Files.createDirectories(report.getParent());
            SmokeTestReport.write(report);
            Files.write(report, ("PASS: Java " + System.getProperty("java.version")
                    + "; startup and B control; hidden terrain type; barrier-only pixels and models"
                    + "; adjacent-face culling; opaque occlusion; foreign GL state and VAO; exception recovery; index thread ownership; mono/stereo pass counts; replaced terrain renderer; zero toggle terrain invalidations"
                    + "; world overlay scene; block and chunk packets; unload/reload; world switch; resource reload; VBO on/off\n")
                    .getBytes(StandardCharsets.UTF_8));
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private void verifyWorld(Minecraft client) throws ReflectiveOperationException {
        // Follow multiplayer setup: discard the unstarted integrated-server placeholder.
        client.setWorld(null);
        openWorld(client);
        populate(client);
        // Initialize the real world lightmap and camera before checking the isolated pass.
        client.gameRenderer.tick();
        client.gameRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
        for (boolean vbo : new boolean[]{false, true}) {
            client.options.useVbo = vbo;
            client.worldRenderer.reload();
            RenderSmokeTestProbe.watchTerrain = true;
            for (int i = 0; i < 3; i++) {
                byte[] hidden = frame(client, 0, 0);
                int[] hiddenState = RenderStateSmokeTest.glState();
                toggle(client);
                byte[] visible = frame(client, 3, 64);
                require(Arrays.equals(hiddenState, RenderStateSmokeTest.glState()), "Visibility changed OpenGL state");
                requireBarrierPixels(hidden, visible);
                toggle(client);
                require(Arrays.equals(hidden, frame(client, 0, 0)), "Hiding barriers changed the base scene");
            }
            require(RenderSmokeTestProbe.invalidations == 0, "Toggling invalidated terrain");
            RenderSmokeTestProbe.watchTerrain = false;
        }
        toggle(client);
        verifyFrameHook(client, true);
        SmokeTestReport.record("barrier-pixels-vbo-on-off");
        SmokeTestReport.record("zero-toggle-terrain-invalidations");
        verifyTranslucentNeighbors(client);
        verifyBatchRecovery(client);
        SmokeTestReport.record("batch-failure-recovery");
        IndexSmokeTest.verify(client);
        SmokeTestReport.record("index-thread-ownership");
        RenderStateSmokeTest.verify(client);
        ModCompatibilitySmokeTest.verify(client, () -> toggle(client));
        client.world.setBlockState(SECOND, Blocks.AIR.defaultState(), 2);
        frame(client, 2, 48);
        client.world.setBlockState(SECOND, Blocks.BARRIER.defaultState(), 2);
        frame(client, 3, 64);

        cache(client).generateChunk(1, 0);
        BlockPos edge = new BlockPos(15, 64, 0);
        BlockPos neighbor = new BlockPos(16, 64, 0);
        client.world.setBlockState(edge, Blocks.BARRIER.defaultState(), 2);
        client.world.setBlockState(neighbor, Blocks.BARRIER.defaultState(), 2);
        client.player.x = client.player.prevTickX = 16;
        frame(client, 2, 40);
        client.world.setBlockState(edge, Blocks.AIR.defaultState(), 2);
        client.world.setBlockState(neighbor, Blocks.AIR.defaultState(), 2);
        client.player.x = client.player.prevTickX = 1.5;
        BlockPos behindCamera = new BlockPos(0, 64, 8);
        client.world.setBlockState(behindCamera, Blocks.BARRIER.defaultState(), 2);
        frame(client, 3, 64);
        client.world.setBlockState(behindCamera, Blocks.AIR.defaultState(), 2);

        // Full packets replace old sections; partial packets preserve sections outside their mask.
        WorldChunk replacement = new WorldChunk(client.world, 0, 0);
        replacement.setBlockState(SECOND, Blocks.BARRIER.defaultState());
        applyPacket(client.world.getChunkAt(0, 0), new WorldChunkS2CPacket(replacement, true, 0xffff));
        frame(client, 2, 48);
        client.world.setBlockState(new BlockPos(0, 80, 0), Blocks.BARRIER.defaultState(), 2);
        replacement.setBlockState(SECOND, Blocks.AIR.defaultState());
        applyPacket(client.world.getChunkAt(0, 0), new WorldChunkS2CPacket(replacement, false, 1 << 4));
        frame(client, 1, 24);
        client.player.y = client.player.prevTickY = 80.5;
        frame(client, 1, 24);
        client.player.y = client.player.prevTickY = 64.5;
        cache(client).unloadChunk(0, -1);
        frame(client, 0, 0);
        cache(client).unloadChunk(0, 0);
        populate(client);
        frame(client, 3, 64);
        SmokeTestReport.record("block-chunk-lifecycle");
        client.reloadResources();
        require(OrnitheClient.barriersVisible(), "Resource reload reset visibility");
        frame(client, 3, 64);
        ModCompatibilitySmokeTest.verify(client, () -> toggle(client));
        SmokeTestReport.record("resource-reload");
        openWorld(client);
        require(OrnitheClient.barriersVisible(), "World switch reset visibility");
        frame(client, 0, 0);
        verifyFrameHook(client, false);
        populate(client);
        ModCompatibilitySmokeTest.verify(client, () -> toggle(client));
        SmokeTestReport.record("world-switch");
    }

    private void openWorld(Minecraft client) {
        ClientPlayNetworkHandler network = new ClientPlayNetworkHandler(client, null,
                new Connection(PacketFlow.CLIENTBOUND), client.getSession().getProfile());
        client.world = new ClientWorld(network, new WorldSettings(0L, WorldSettings.GameMode.CREATIVE,
                false, false, WorldGeneratorType.DEFAULT), 0, Difficulty.PEACEFUL, client.profiler);
        client.player = new LocalClientPlayerEntity(client, client.world, network, new PlayerStats());
        client.interactionManager = new ClientPlayerInteractionManager(client, network);
        client.player.x = client.player.prevTickX = 1.5;
        client.player.y = client.player.prevTickY = 64.5;
        client.player.z = client.player.prevTickZ = 4;
        client.options.viewDistance = 2;
        client.setCamera(client.player);
        client.worldRenderer.setWorld(client.world);
        client.particleManager.setWorld(client.world);
    }

    private static ClientChunkCache cache(Minecraft client) {
        return (ClientChunkCache) client.world.getChunkSource();
    }

    private static void populate(Minecraft client) {
        cache(client).generateChunk(0, 0);
        cache(client).generateChunk(0, -1);
        client.world.setBlockState(FIRST, Blocks.BARRIER.defaultState(), 2);
        client.world.setBlockState(SECOND, Blocks.BARRIER.defaultState(), 2);
        client.world.setBlockState(STONE, Blocks.STONE.defaultState(), 2);
        client.world.setBlockState(OCCLUDED, Blocks.BARRIER.defaultState(), 2);
    }

    private void toggle(Minecraft client) {
        boolean previous = OrnitheClient.barriersVisible();
        KeyBinding.click(binding.getKeyCode());
        MinecraftClientEvents.TICK_END.invoker().accept(client);
        require(OrnitheClient.barriersVisible() != previous, "B did not toggle visibility");
        require(!binding.consumeClick(), "B was not consumed");
        require(Blocks.BARRIER.getRenderType() == -1, "Barriers entered terrain meshes");
    }

    private static void applyPacket(WorldChunk chunk, WorldChunkS2CPacket packet) {
        chunk.update(packet.getChunkData(), packet.getSections(), packet.isFull());
    }

    private static byte[] frame(Minecraft client, int expectedModels, int expectedVertices) {
        return frame(client, expectedModels, expectedVertices, null);
    }

    private static byte[] frame(Minecraft client, int expectedModels, int expectedVertices, BlockPos glass) {
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
        GlStateManager.color4f(1, 1, 1, 1);
        client.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
        BlockRenderDispatcher models = client.getBlockRenderDispatcher();
        Tessellator terrain = new Tessellator(256);
        BufferBuilder buffer = terrain.getBuilder();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.BLOCK);
        buffer.offset(-client.player.x, -client.player.y, -client.player.z);
        models.tessellate(Blocks.STONE.defaultState(), STONE, client.world, buffer);
        terrain.end();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        if (glass != null) {
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.BLOCK);
            buffer.offset(-client.player.x, -client.player.y, -client.player.z);
            models.tessellate(Blocks.STAINED_GLASS.defaultState(), glass, client.world, buffer);
            terrain.end();
        }
        RenderSmokeTestProbe.resetModels();
        RenderSmokeTestProbe.watchModels = true;
        try {
            BARRIERS.render(client, client.player, 1);
        } finally {
            RenderSmokeTestProbe.watchModels = false;
        }
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

    private static void verifyFrameHook(Minecraft client, boolean hasBarriers) {
        client.player.yaw = client.player.prevYaw = 180;
        client.player.pitch = client.player.prevPitch = 20;
        boolean previousAnaglyph = client.options.anaglyph;
        try {
            // Exercise each hook behavior once; the pixel fixture covers both terrain backends.
            for (int scenario = 0; scenario < (hasBarriers ? 3 : 1); scenario++) {
                boolean stereo = scenario == 1;
                client.options.anaglyph = stereo;
                RenderSmokeTestProbe.cancelTerrainLayer = scenario == 2;
                RenderSmokeTestProbe.resetModels();
                RenderSmokeTestProbe.watchModels = true;
                client.gameRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
                require(RenderSmokeTestProbe.cameraPasses == (stereo ? 2 : 1), "Incorrect camera pass count");
                require(RenderSmokeTestProbe.eyeMask == (stereo ? 3 : 4), "Camera eye was skipped");
                require(RenderSmokeTestProbe.barrierPasses == RenderSmokeTestProbe.cameraPasses,
                        "Expected exactly one barrier pass per camera eye");
                require((RenderSmokeTestProbe.barriers > 0) == hasBarriers, "Unexpected barrier submission");
            }
            SmokeTestReport.record(hasBarriers ? "camera-pass-counts" : "empty-camera-pass-counts");
        } finally {
            client.options.anaglyph = previousAnaglyph;
            RenderSmokeTestProbe.watchModels = false;
            RenderSmokeTestProbe.cancelTerrainLayer = false;
        }
    }

    private void verifyTranslucentNeighbors(Minecraft client) {
        for (int z : new int[]{-1, 1}) {
            BlockPos glass = new BlockPos(0, 64, z);
            client.world.setBlockState(glass, Blocks.STAINED_GLASS.defaultState(), 2);
            toggle(client);
            byte[] hidden = frame(client, 0, 0, glass);
            toggle(client);
            requireBarrierPixels(hidden, frame(client, 3, 64, glass));
            toggle(client);
            require(Arrays.equals(hidden, frame(client, 0, 0, glass)), "Toggle changed neighbouring glass");
            toggle(client);
            client.world.setBlockState(glass, Blocks.AIR.defaultState(), 2);
        }
        SmokeTestReport.record("translucent-neighbor-pixels");
    }

    private static void requireBarrierPixels(byte[] hidden, byte[] visible) {
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
    }

    private static void verifyBatchRecovery(Minecraft client) {
        RenderSmokeTestProbe.modelAction = () -> { throw new RenderSmokeTestProbe.InjectedFailure(); };
        RenderSmokeTestProbe.expectFailure(() -> frame(client, 3, 64), RenderSmokeTestProbe.InjectedFailure.class);
        require(RenderSmokeTestProbe.modelAction == null, "Model failure was not exercised");
        frame(client, 3, 64);
        RenderSmokeTestProbe.modelAction = () -> BARRIERS.render(client, client.player, 1);
        RenderSmokeTestProbe.expectFailure(() -> frame(client, 3, 64), IllegalStateException.class);
        require(RenderSmokeTestProbe.modelAction == null, "Reentrant batch was not exercised");
        frame(client, 3, 64);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
