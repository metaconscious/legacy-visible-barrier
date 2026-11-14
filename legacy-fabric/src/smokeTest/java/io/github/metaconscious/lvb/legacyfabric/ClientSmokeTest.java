package io.github.metaconscious.lvb.legacyfabric;

import com.mojang.blaze3d.platform.GlStateManager;
import io.github.metaconscious.lvb.core.RenderingPolicy;
import net.fabricmc.api.ClientModInitializer;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

/** Exercises the installed client hooks, model tessellation and OpenGL output in a test world. */
public final class ClientSmokeTest implements ClientModInitializer {
    private static BarrierRenderer BARRIERS;
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
                if (RenderingPlugin.state().available()) require(Blocks.BARRIER.getRenderLayerType() == RenderLayer.TRANSLUCENT, "Barrier must be translucent");
                Class.forName(net.minecraft.client.particle.BarrierParticle.class.getName());
                Class.forName(net.minecraft.client.BlockStateMapper.class.getName());
                KeyBinding.onKeyPressed(binding.getCode());
                return;
            }
            require(!binding.wasPressed(), "Queued input outside a world must be consumed");
            SmokeTestReport.record("startup-controls");
            testingWorld = true;
            SmokeTestReport.record("mode:" + RenderingPlugin.state().mode.name().toLowerCase(java.util.Locale.ROOT));
            if (RenderingPlugin.enhanced()) {
                BARRIERS = new BarrierRenderer();
                verifyWorld(client);
                verifyRecovery(client);
            } else {
                verifyNative(client);
            }
            Path report = Paths.get(System.getProperty("lvb.smoke.report"));
            Files.createDirectories(report.getParent());
            SmokeTestReport.write(report);
            Files.write(report, "PASS: client rendering contracts verified\n".getBytes(StandardCharsets.UTF_8));
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private void verifyWorld(MinecraftClient client) throws ReflectiveOperationException {
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
        SmokeTestReport.record("block-chunk-lifecycle");
        client.reloadResources();
        require(LegacyFabricClient.barriersVisible(), "Resource reload reset visibility");
        frame(client, 3, 64);
        ModCompatibilitySmokeTest.verify(client, () -> toggle(client));
        SmokeTestReport.record("resource-reload");
        openWorld(client);
        require(LegacyFabricClient.barriersVisible(), "World switch reset visibility");
        frame(client, 0, 0);
        verifyFrameHook(client, false);
        populate(client);
        ModCompatibilitySmokeTest.verify(client, () -> toggle(client));
        SmokeTestReport.record("world-switch");
    }


    private void verifyNative(MinecraftClient client) throws ReflectiveOperationException {

        openWorld(client);
        populate(client);
        boolean cleanProfile = !Boolean.getBoolean("lvb.smoke.missingHook");
        if (cleanProfile) {
            require(!(client.worldRenderer instanceof BarrierRenderPass), "Native profile installed the enhanced frame hooks");
            require(!(client.world.getChunk(0, 0) instanceof BarrierChunk), "Native profile installed the barrier index");
            require(!((Object) new net.minecraft.client.world.ChunkAssemblyHelper() instanceof TranslucentMesh), "Native profile installed snapshot hooks");
            require(!((Object) new BufferBuilder(64).new DrawArrayParameters(new int[0], VertexFormats.BLOCK) instanceof VertexOrigin),
                    "Native profile installed origin hooks");
            SmokeTestReport.record("native-startup-isolation");
        } else {
            require(RenderingPlugin.state().mode == RenderingPolicy.Mode.NATIVE, "Missing hook did not select native mode");
            SmokeTestReport.record("missing-hook-recovery");
        }
        if (RenderingPlugin.state().mode == RenderingPolicy.Mode.OFF) {
            KeyBinding.onKeyPressed(binding.getCode());
            ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(client);
            require(!LegacyFabricClient.barriersVisible() && Blocks.BARRIER.getBlockType() == -1,
                    "Off mode changed vanilla visibility");
            SmokeTestReport.record("off-controls");
            return;
        }
        client.gameRenderer.tick();
        client.gameRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
        for (boolean vbo : new boolean[]{false, true}) {
            client.options.vbo = vbo;
            client.worldRenderer.reload();
            byte[] hidden = frame(client, 0, 0);
            RenderSmokeTestProbe.nativeRefreshes = 0;
            RenderSmokeTestProbe.watchTerrain = true;
            toggle(client);
            require(RenderSmokeTestProbe.nativeRefreshes == 1, "Native toggle must refresh exactly once");
            RenderSmokeTestProbe.watchTerrain = false;
            requireBarrierPixels(hidden, frame(client, 3, 64));
            awaitNativeTerrain(client, true);
            toggle(client);
            awaitNativeTerrain(client, false);
            require(Arrays.equals(hidden, frame(client, 0, 0)), "Native toggle did not hide barrier pixels");
            RenderSmokeTestProbe.nativeRefreshes = 0;
            RenderSmokeTestProbe.watchTerrain = true;
            KeyBinding.onKeyPressed(binding.getCode());
            KeyBinding.onKeyPressed(binding.getCode());
            ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(client);
            require(!LegacyFabricClient.barriersVisible() && RenderSmokeTestProbe.nativeRefreshes == 0,
                    "Two cancelling clicks must not rebuild native terrain");
            RenderSmokeTestProbe.watchTerrain = false;
        }
        SmokeTestReport.record("native-toggles-vbo-on-off");
        toggle(client);
        client.reloadResources();
        requireBarrierPixels(frameHiddenReference(client), frame(client, 3, 64));
        awaitNativeTerrain(client, true);
        SmokeTestReport.record("resource-reload");
        openWorld(client);
        populate(client);
        require(LegacyFabricClient.barriersVisible(), "World change reset visibility intent");
        awaitNativeTerrain(client, true);
        SmokeTestReport.record("world-switch");
        client.world.setBlockState(new BlockPos(0, 64, 1), Blocks.STAINED_GLASS.getDefaultState(), 2);
        client.world.setBlockState(new BlockPos(1, 64, 1), Blocks.WATER.getDefaultState(), 2);
        for (int i = 0; i < 5; i++) client.gameRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native translucent scene produced an OpenGL error");
        SmokeTestReport.record("native-translucent-scene");
        client.world.setBlockState(new BlockPos(0, 64, 1), Blocks.AIR.getDefaultState(), 2);
        client.world.setBlockState(new BlockPos(1, 64, 1), Blocks.AIR.getDefaultState(), 2);
        ModCompatibilitySmokeTest.verify(client, () -> toggle(client));
    }

    private byte[] frameHiddenReference(MinecraftClient client) {
        toggle(client);
        byte[] hidden = frame(client, 0, 0);
        toggle(client);
        return hidden;
    }

    static void awaitNativeTerrain(MinecraftClient client, boolean visible) {
        client.player.yaw = client.player.prevYaw = 180;
        client.player.pitch = client.player.prevPitch = 20;
        long deadline = System.nanoTime() + 5_000_000_000L;
        do {
            client.gameRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
            if ((RenderSmokeTestProbe.nativeSections > 0) == visible) break;
        } while (System.nanoTime() < deadline);
        require((RenderSmokeTestProbe.nativeSections > 0) == visible, "Native terrain visibility did not converge");
        require(RenderSmokeTestProbe.barrierPasses == 0, "Enhanced drawing ran in native mode");
    }

    private void verifyRecovery(MinecraftClient client) throws InterruptedException {
        openWorld(client);
        populate(client);
        frame(client, 3, 64);
        int[] before = RenderStateSmokeTest.glState();
        RenderSmokeTestProbe.modelAction = () -> RenderSmokeTestProbe.failLightmap = true;
        BarrierRenderPass pass = (BarrierRenderPass) client.worldRenderer;
        pass.lvb$beginBarriers(client.player, 1);
        pass.lvb$endBarriers();
        require(Arrays.equals(before, RenderStateSmokeTest.glState()), "Recovery leaked graphics state");
        require(!RenderingPlugin.enhanced(), "Failed enhanced backend was not latched off");
        RenderSmokeTestProbe.nativeRefreshes = 0;
        RenderSmokeTestProbe.watchTerrain = true;
        ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(client);
        require(RenderingPlugin.state().nativeVisible(), "Contained failure did not preserve visibility in native mode");
        require(RenderSmokeTestProbe.nativeRefreshes == 1, "Handover must refresh terrain exactly once");
        ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(client);
        require(RenderSmokeTestProbe.nativeRefreshes == 1, "Handover was retried");
        RenderSmokeTestProbe.watchTerrain = false;
        RenderSmokeTestProbe.resetModels();
        awaitNativeTerrain(client, true);
        requireBarrierPixels(frameHiddenReference(client), frame(client, 3, 64));
        SmokeTestReport.record("runtime-native-handover");
        Thread worker = new Thread(() -> RenderingPlugin.report(RenderingPolicy.Failure.MODEL,
                new RenderSmokeTestProbe.InjectedFailure()));
        worker.start();
        worker.join(5000);
        require(!worker.isAlive(), "Failure publication did not complete");
        ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(client);
        require(RenderingPlugin.state().mode == RenderingPolicy.Mode.UNAVAILABLE
                        && !LegacyFabricClient.barriersVisible() && Blocks.BARRIER.getBlockType() == -1,
                "Shared failure must stop visualization and restore vanilla particles");
        SmokeTestReport.record("shared-failure-unavailable");
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
        require(Blocks.BARRIER.getBlockType() == (RenderingPlugin.nativeVisible() ? 3 : -1),
                "Barrier terrain type disagrees with the effective mode");
    }

    private static void applyPacket(Chunk chunk, ChunkDataS2CPacket packet) {
        chunk.method_3895(packet.method_7757(), packet.method_7760(), packet.shouldLoad());
    }

    private static byte[] frame(MinecraftClient client, int expectedModels, int expectedVertices) {
        return frame(client, expectedModels, expectedVertices, null);
    }

    private static byte[] frame(MinecraftClient client, int expectedModels, int expectedVertices, BlockPos[] neighbors) {
        return frame(client, expectedModels, expectedVertices, neighbors, false);
    }

    private static byte[] frame(MinecraftClient client, int expectedModels, int expectedVertices, BlockPos[] neighbors,
                                boolean referenceOrder) {
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
        Tessellator terrain = new Tessellator(4096);
        BufferBuilder buffer = terrain.getBuffer();
        buffer.begin(GL11.GL_QUADS, VertexFormats.BLOCK);
        buffer.offset(-client.player.x, -client.player.y, -client.player.z);
        models.renderBlock(Blocks.STONE.getDefaultState(), STONE, client.world, buffer);
        terrain.draw();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        TranslucentBatch translucent = null;
        int atlas = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (neighbors != null) {
            prepareTranslucent(buffer);
            buffer.begin(GL11.GL_QUADS, VertexFormats.BLOCK);
            // Compiled terrain can use a region origin rather than the camera or chunk origin.
            if (!referenceOrder && LegacyFabricClient.barriersVisible()) {
                buffer.offset(-256, 0, -256);
            } else {
                buffer.offset(-client.player.x, -client.player.y, -client.player.z);
            }
            for (BlockPos pos : neighbors) {
                models.renderBlock(client.world.getBlockState(pos), pos, client.world, buffer);
            }
            try {
                if (Boolean.TRUE.equals(BufferBuilder.class.getMethod("isMultiTexture").invoke(buffer))) {
                    SmokeTestReport.record("multi-texture-terrain");
                }
            } catch (NoSuchMethodException vanilla) {
                // Vanilla uses the block atlas directly.
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot inspect terrain texture mode", failure);
            }
            if (referenceOrder) {
                for (BlockPos pos : new BlockPos[]{FIRST, SECOND, OCCLUDED}) {
                    models.getModelRenderer().render(client.world,
                            models.getModel(Blocks.BARRIER.getDefaultState(), client.world, pos),
                            Blocks.BARRIER.getDefaultState(), pos, buffer);
                }
                buffer.offset(0, 0, 0);
                buffer.sortQuads(0, 0, 0);
            }
            if (!referenceOrder && LegacyFabricClient.barriersVisible()) {
                buffer.end();
                BufferBuilder.DrawArrayParameters snapshot = buffer.method_9727();
                buffer.offset(17, 19, 23);
                net.minecraft.client.world.ChunkAssemblyHelper compiled = new net.minecraft.client.world.ChunkAssemblyHelper();
                compiled.setDrawArrayParameters(snapshot);
                translucent = ((TranslucentMesh) compiled).lvb$translucentBatch();
            } else {
                client.gameRenderer.enableLightmap();
                terrain.draw();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas);
                client.gameRenderer.disableLightmap();
            }
        }
        RenderSmokeTestProbe.resetModels();
        RenderSmokeTestProbe.watchModels = true;
        final TranslucentBatch capturedTerrain = translucent;
        try {
            if (RenderingPlugin.state().mode == RenderingPolicy.Mode.NATIVE) {
                prepareTranslucent(buffer);
                buffer.begin(GL11.GL_QUADS, VertexFormats.BLOCK);
                buffer.offset(-client.player.x, -client.player.y, -client.player.z);
                for (BlockPos pos : new BlockPos[]{FIRST, SECOND, OCCLUDED}) {
                    models.renderBlock(Blocks.BARRIER.getDefaultState(), pos, client.world, buffer);
                }
                buffer.offset(0, 0, 0);
                buffer.sortQuads(0, 0, 0);
                client.gameRenderer.enableLightmap();
                terrain.draw();
                client.gameRenderer.disableLightmap();
            } else if (!referenceOrder) {
                BARRIERS.renderWithTerrain(client, client.player, 1,
                        () -> capturedTerrain == null ? Collections.emptyList() : Collections.singletonList(capturedTerrain));
            }
        } finally {
            RenderSmokeTestProbe.watchModels = false;
        }
        require(RenderSmokeTestProbe.otherBlocks == 0, "Barrier pass tessellated another block type");
        require(referenceOrder || RenderSmokeTestProbe.barriers == expectedModels,
                "Expected " + expectedModels + " barrier models, got " + RenderSmokeTestProbe.barriers);
        require(referenceOrder || RenderSmokeTestProbe.vertices == expectedVertices,
                "Expected " + expectedVertices + " vertices, got " + RenderSmokeTestProbe.vertices);
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error in barrier pass");
        ByteBuffer pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        GL11.glReadPixels(0, 0, WIDTH, HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        byte[] result = new byte[pixels.remaining()];
        pixels.get(result);
        return result;
    }

    private static void verifyFrameHook(MinecraftClient client, boolean hasBarriers) {
        client.player.yaw = client.player.prevYaw = 180;
        client.player.pitch = client.player.prevPitch = 20;
        boolean previousAnaglyph = client.options.anaglyph3d;
        try {
            // Exercise each hook behavior once; the pixel fixture covers both terrain backends.
            for (int scenario = 0; scenario < (hasBarriers ? 3 : 1); scenario++) {
                boolean stereo = scenario == 1;
                client.options.anaglyph3d = stereo;
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
            client.options.anaglyph3d = previousAnaglyph;
            RenderSmokeTestProbe.watchModels = false;
            RenderSmokeTestProbe.cancelTerrainLayer = false;
        }
    }

    private static void prepareTranslucent(BufferBuilder buffer) {
        try {
            BufferBuilder.class.getMethod("setBlockLayer", RenderLayer.class).invoke(buffer, RenderLayer.TRANSLUCENT);
        } catch (NoSuchMethodException vanilla) {
            // Vanilla has no per-sprite terrain uploader.
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot prepare translucent terrain fixture", failure);
        }
    }

    private static void verifyCompiledTerrain(MinecraftClient client) {
        boolean previousVbo = client.options.vbo;
        try {
            for (boolean vbo : new boolean[]{false, true}) {
                client.options.vbo = vbo;
                client.worldRenderer.reload();
                RenderSmokeTestProbe.resetModels();
                RenderSmokeTestProbe.watchModels = true;
                long deadline = System.nanoTime() + 5_000_000_000L;
                while (RenderSmokeTestProbe.terrainBatches == 0 && System.nanoTime() < deadline) {
                    client.gameRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
                }
                require(RenderSmokeTestProbe.terrainBatches > 0, "Compiled terrain did not enter the shared order");
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error in shared terrain submission");
            }
            SmokeTestReport.record("shared-terrain-submission");
        } finally {
            RenderSmokeTestProbe.watchModels = false;
            client.options.vbo = previousVbo;
        }
    }

    private void verifyTranslucentNeighbors(MinecraftClient client) {
        for (Block[] materials : new Block[][]{{Blocks.STAINED_GLASS}, {Blocks.WATER},
                {Blocks.WATER, Blocks.STAINED_GLASS}}) {
            int[][] scenes = materials.length == 1 ? new int[][]{{-1}, {1}, {-1, 1}} : new int[][]{{-1, 1}};
            for (int[] depths : scenes) {
                BlockPos[] neighbors = new BlockPos[depths.length];
                for (int i = 0; i < depths.length; i++) {
                    neighbors[i] = new BlockPos(0, 64, depths[i]);
                    client.world.setBlockState(neighbors[i], materials[i % materials.length].getDefaultState(), 2);
                }
                if (materials.length == 2) {
                    verifyCompiledTerrain(client);
                }
                toggle(client);
                byte[] hidden = frame(client, 0, 0, neighbors);
                toggle(client);
                byte[] visible = frame(client, 3, 64, neighbors);
                requireBarrierPixels(hidden, visible);
                require(Arrays.equals(visible, frame(client, 3, 64, neighbors, true)),
                        "Barrier blending differs from a shared translucent order: " + Arrays.toString(materials)
                                + " " + Arrays.toString(depths));
                toggle(client);
                require(Arrays.equals(hidden, frame(client, 0, 0, neighbors)),
                        "Toggle changed neighbouring translucent terrain");
                toggle(client);
                for (BlockPos pos : neighbors) {
                    client.world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                }
            }
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

    private static void verifyBatchRecovery(MinecraftClient client) {
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
