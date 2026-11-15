package io.github.metaconscious.lvb.forge;

import io.github.metaconscious.lvb.core.RenderingPolicy;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.WorldRenderer.State;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
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
@Mod(modid = "lvb_smoke", name = "LVB client contracts", version = "1", clientSideOnly = true,
        dependencies = "required-after:lvb")
public final class ClientSmokeTest {
    private static BarrierRenderer BARRIERS;
    private static final int WIDTH = 128;
    private static final int HEIGHT = 64;
    private static final BlockPos FIRST = new BlockPos(0, 64, 0);
    private static final BlockPos SECOND = new BlockPos(1, 64, 0);
    private static final BlockPos STONE = new BlockPos(3, 64, 0);
    private static final BlockPos OCCLUDED = new BlockPos(3, 64, -2);
    private KeyBinding binding;
    private boolean testingWorld;

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) { MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) verify(Minecraft.getMinecraft());
    }

    private void verify(Minecraft client) {
        if (testingWorld) {
            return;
        }
        try {
            require(!ForgeClient.barriersVisible(), "Visibility must start hidden");
            require(client.theWorld == null && client.thePlayer == null, "Client must start outside a world");
            if (binding == null) {
                new ForgeClient().initialize();
                int count = 0;
                int settingsCount = 0;
                for (KeyBinding key : client.gameSettings.keyBindings) {
                    if ("key.toggle_barrier_visibility".equals(key.getKeyDescription())) {
                        binding = key;
                        count++;
                    }
                    if ("key.open_lvb_settings".equals(key.getKeyDescription())) {
                        settingsCount++;
                        require(key.getKeyCodeDefault() == Keyboard.KEY_NONE, "Settings must start unbound");
                    }
                }
                require(settingsCount == 1, "Exactly one settings control must be registered");
                require(count == 1, "Exactly one toggle control must be registered");
                require(binding.getKeyCodeDefault() == Keyboard.KEY_B, "Default control must be B");
                require("key.category.legacy_visible_barriers".equals(binding.getKeyCategory()), "Control category changed");
                if (RenderingPlugin.state().available()) require(Blocks.barrier.getBlockLayer() == EnumWorldBlockLayer.TRANSLUCENT, "Barrier must be translucent");
                Class.forName(net.minecraft.client.particle.Barrier.class.getName());
                Class.forName(net.minecraft.client.renderer.block.statemap.BlockStateMapper.class.getName());
                KeyBinding.onTick(binding.getKeyCode());
                return;
            }
            require(!binding.isPressed(), "Queued input outside a world must be consumed");
            SmokeTestReport.record("startup-controls");
            testingWorld = true;
            SettingsSmokeTest.verify(client);
            SmokeTestReport.record("mode:" + RenderingPlugin.state().mode.name().toLowerCase(java.util.Locale.ROOT));
            String expectedMode = System.getProperty("lvb.smoke.expectedMode");
            require(expectedMode == null || RenderingPlugin.state().mode.name().equalsIgnoreCase(expectedMode),
                    "Unexpected rendering mode: " + RenderingPlugin.state().mode);
            if (RenderingPlugin.enhanced()) {
                BARRIERS = new BarrierRenderer();
                verifyWorld(client);
                verifyRecovery(client);
            } else {
                verifyNative(client);
            }
            SettingsSmokeTest.verifyRuntime(client);
            Path report = Paths.get(System.getProperty("lvb.smoke.report"));
            Files.createDirectories(report.getParent());
            SmokeTestReport.write(report);
            Files.write(report, "PASS: client rendering contracts verified\n".getBytes(StandardCharsets.UTF_8));
            FMLCommonHandler.instance().exitJava(0, false);
        } catch (Throwable failure) {
            failure.printStackTrace();
            FMLCommonHandler.instance().exitJava(1, false);
        }
    }

    private void verifyWorld(Minecraft client) throws ReflectiveOperationException {
        openWorld(client);
        populate(client);
        // Initialize the real world lightmap and camera before checking the isolated pass.
        client.entityRenderer.updateRenderer();
        client.entityRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
        for (boolean vbo : new boolean[]{false, true}) {
            client.gameSettings.useVbo = vbo;
            client.renderGlobal.loadRenderers();
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
        client.theWorld.setBlockState(SECOND, Blocks.air.getDefaultState(), 2);
        frame(client, 2, 48);
        client.theWorld.setBlockState(SECOND, Blocks.barrier.getDefaultState(), 2);
        frame(client, 3, 64);

        cache(client).loadChunk(1, 0);
        BlockPos edge = new BlockPos(15, 64, 0);
        BlockPos neighbor = new BlockPos(16, 64, 0);
        client.theWorld.setBlockState(edge, Blocks.barrier.getDefaultState(), 2);
        client.theWorld.setBlockState(neighbor, Blocks.barrier.getDefaultState(), 2);
        client.thePlayer.posX = client.thePlayer.lastTickPosX = 16;
        frame(client, 2, 40);
        client.theWorld.setBlockState(edge, Blocks.air.getDefaultState(), 2);
        client.theWorld.setBlockState(neighbor, Blocks.air.getDefaultState(), 2);
        client.thePlayer.posX = client.thePlayer.lastTickPosX = 1.5;
        BlockPos behindCamera = new BlockPos(0, 64, 8);
        client.theWorld.setBlockState(behindCamera, Blocks.barrier.getDefaultState(), 2);
        frame(client, 3, 64);
        client.theWorld.setBlockState(behindCamera, Blocks.air.getDefaultState(), 2);

        // Full packets replace old sections; partial packets preserve sections outside their mask.
        Chunk replacement = new Chunk(client.theWorld, 0, 0);
        replacement.setBlockState(SECOND, Blocks.barrier.getDefaultState());
        applyPacket(client.theWorld.getChunkFromChunkCoords(0, 0), new S21PacketChunkData(replacement, true, 0xffff));
        frame(client, 2, 48);
        client.theWorld.setBlockState(new BlockPos(0, 80, 0), Blocks.barrier.getDefaultState(), 2);
        replacement.setBlockState(SECOND, Blocks.air.getDefaultState());
        applyPacket(client.theWorld.getChunkFromChunkCoords(0, 0), new S21PacketChunkData(replacement, false, 1 << 4));
        frame(client, 1, 24);
        client.thePlayer.posY = client.thePlayer.lastTickPosY = 80.5;
        frame(client, 1, 24);
        client.thePlayer.posY = client.thePlayer.lastTickPosY = 64.5;
        cache(client).unloadChunk(0, -1);
        frame(client, 0, 0);
        cache(client).unloadChunk(0, 0);
        populate(client);
        frame(client, 3, 64);
        SmokeTestReport.record("block-chunk-lifecycle");
        client.refreshResources();
        require(ForgeClient.barriersVisible(), "Resource reload reset visibility");
        frame(client, 3, 64);
        ModCompatibilitySmokeTest.verify(client, () -> toggle(client));
        SmokeTestReport.record("resource-reload");
        openWorld(client);
        require(ForgeClient.barriersVisible(), "World switch reset visibility");
        frame(client, 0, 0);
        verifyFrameHook(client, false);
        populate(client);
        ModCompatibilitySmokeTest.verify(client, () -> toggle(client));
        SmokeTestReport.record("world-switch");
    }


    private void verifyNative(Minecraft client) throws ReflectiveOperationException {

        openWorld(client);
        populate(client);
        boolean cleanProfile = !Boolean.getBoolean("lvb.smoke.missingHook");
        if (cleanProfile) {
            require(!(client.renderGlobal instanceof BarrierRenderPass), "Native profile installed the enhanced frame hooks");
            require(!(client.theWorld.getChunkFromChunkCoords(0, 0) instanceof BarrierChunk), "Native profile installed the barrier index");
            require(!((Object) new net.minecraft.client.renderer.chunk.CompiledChunk() instanceof TranslucentMesh), "Native profile installed snapshot hooks");
            require(!((Object) new WorldRenderer(64).new State(new int[0], DefaultVertexFormats.BLOCK) instanceof VertexOrigin),
                    "Native profile installed origin hooks");
            SmokeTestReport.record("native-startup-isolation");
        } else {
            require(RenderingPlugin.state().mode == RenderingPolicy.Mode.NATIVE, "Missing hook did not select native mode");
            SmokeTestReport.record("missing-hook-recovery");
        }
        if (RenderingPlugin.state().mode == RenderingPolicy.Mode.OFF) {
            KeyBinding.onTick(binding.getKeyCode());
            MinecraftForge.EVENT_BUS.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            require(!ForgeClient.barriersVisible() && Blocks.barrier.getRenderType() == -1,
                    "Off mode changed vanilla visibility");
            SmokeTestReport.record("off-controls");
            return;
        }
        client.entityRenderer.updateRenderer();
        client.entityRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
        for (boolean vbo : new boolean[]{false, true}) {
            client.gameSettings.useVbo = vbo;
            client.renderGlobal.loadRenderers();
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
            KeyBinding.onTick(binding.getKeyCode());
            KeyBinding.onTick(binding.getKeyCode());
            MinecraftForge.EVENT_BUS.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            require(!ForgeClient.barriersVisible() && RenderSmokeTestProbe.nativeRefreshes == 0,
                    "Two cancelling clicks must not rebuild native terrain");
            RenderSmokeTestProbe.watchTerrain = false;
        }
        SmokeTestReport.record("native-toggles-vbo-on-off");
        toggle(client);
        client.refreshResources();
        requireBarrierPixels(frameHiddenReference(client), frame(client, 3, 64));
        awaitNativeTerrain(client, true);
        SmokeTestReport.record("resource-reload");
        openWorld(client);
        populate(client);
        require(ForgeClient.barriersVisible(), "World change reset visibility intent");
        awaitNativeTerrain(client, true);
        SmokeTestReport.record("world-switch");
        client.theWorld.setBlockState(new BlockPos(0, 64, 1), Blocks.stained_glass.getDefaultState(), 2);
        client.theWorld.setBlockState(new BlockPos(1, 64, 1), Blocks.water.getDefaultState(), 2);
        for (int i = 0; i < 5; i++) client.entityRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native translucent scene produced an OpenGL error");
        SmokeTestReport.record("native-translucent-scene");
        client.theWorld.setBlockState(new BlockPos(0, 64, 1), Blocks.air.getDefaultState(), 2);
        client.theWorld.setBlockState(new BlockPos(1, 64, 1), Blocks.air.getDefaultState(), 2);
        ModCompatibilitySmokeTest.verify(client, () -> toggle(client));
    }

    private byte[] frameHiddenReference(Minecraft client) {
        toggle(client);
        byte[] hidden = frame(client, 0, 0);
        toggle(client);
        return hidden;
    }

    static void awaitNativeTerrain(Minecraft client, boolean visible) {
        client.thePlayer.rotationYaw = client.thePlayer.prevRotationYaw = 180;
        client.thePlayer.rotationPitch = client.thePlayer.prevRotationPitch = 20;
        long deadline = System.nanoTime() + 5_000_000_000L;
        do {
            client.entityRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
            if ((RenderSmokeTestProbe.nativeSections > 0) == visible) break;
        } while (System.nanoTime() < deadline);
        require((RenderSmokeTestProbe.nativeSections > 0) == visible, "Native terrain visibility did not converge");
        require(RenderSmokeTestProbe.barrierPasses == 0, "Enhanced drawing ran in native mode");
    }

    private void verifyRecovery(Minecraft client) throws InterruptedException {
        openWorld(client);
        populate(client);
        frame(client, 3, 64);
        int[] before = RenderStateSmokeTest.glState();
        RenderSmokeTestProbe.modelAction = () -> RenderSmokeTestProbe.failLightmap = true;
        BarrierRenderPass pass = (BarrierRenderPass) client.renderGlobal;
        pass.lvb$beginBarriers(client.thePlayer, 1);
        pass.lvb$endBarriers();
        require(Arrays.equals(before, RenderStateSmokeTest.glState()), "Recovery leaked graphics state");
        require(!RenderingPlugin.enhanced(), "Failed enhanced backend was not latched off");
        RenderSmokeTestProbe.nativeRefreshes = 0;
        RenderSmokeTestProbe.watchTerrain = true;
        MinecraftForge.EVENT_BUS.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        require(RenderingPlugin.state().nativeVisible(), "Contained failure did not preserve visibility in native mode");
        require(RenderSmokeTestProbe.nativeRefreshes == 1, "Handover must refresh terrain exactly once");
        MinecraftForge.EVENT_BUS.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
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
        MinecraftForge.EVENT_BUS.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        require(RenderingPlugin.state().mode == RenderingPolicy.Mode.UNAVAILABLE
                        && !ForgeClient.barriersVisible() && Blocks.barrier.getRenderType() == -1,
                "Shared failure must stop visualization and restore vanilla particles");
        SmokeTestReport.record("shared-failure-unavailable");
    }

    private void openWorld(Minecraft client) {
        NetHandlerPlayClient network = new NetHandlerPlayClient(client, null,
                new NetworkManager(EnumPacketDirection.CLIENTBOUND), client.getSession().getProfile());
        client.theWorld = new WorldClient(network, new WorldSettings(0L, WorldSettings.GameType.CREATIVE,
                false, false, WorldType.DEFAULT), 0, EnumDifficulty.PEACEFUL, client.mcProfiler);
        client.thePlayer = new EntityPlayerSP(client, client.theWorld, network, new StatFileWriter());
        client.playerController = new PlayerControllerMP(client, network);
        client.thePlayer.posX = client.thePlayer.lastTickPosX = 1.5;
        client.thePlayer.posY = client.thePlayer.lastTickPosY = 64.5;
        client.thePlayer.posZ = client.thePlayer.lastTickPosZ = 4;
        client.gameSettings.renderDistanceChunks = 2;
        client.setRenderViewEntity(client.thePlayer);
        client.renderGlobal.setWorldAndLoadRenderers(client.theWorld);
        client.effectRenderer.clearEffects(client.theWorld);
    }

    private static ChunkProviderClient cache(Minecraft client) {
        return (ChunkProviderClient) client.theWorld.getChunkProvider();
    }

    private static void populate(Minecraft client) {
        cache(client).loadChunk(0, 0);
        cache(client).loadChunk(0, -1);
        client.theWorld.setBlockState(FIRST, Blocks.barrier.getDefaultState(), 2);
        client.theWorld.setBlockState(SECOND, Blocks.barrier.getDefaultState(), 2);
        client.theWorld.setBlockState(STONE, Blocks.stone.getDefaultState(), 2);
        client.theWorld.setBlockState(OCCLUDED, Blocks.barrier.getDefaultState(), 2);
    }

    private void toggle(Minecraft client) {
        boolean previous = ForgeClient.barriersVisible();
        KeyBinding.onTick(binding.getKeyCode());
        MinecraftForge.EVENT_BUS.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        require(ForgeClient.barriersVisible() != previous, "B did not toggle visibility");
        require(!binding.isPressed(), "B was not consumed");
        require(Blocks.barrier.getRenderType() == (RenderingPlugin.nativeVisible() ? 3 : -1),
                "Barrier terrain type disagrees with the effective mode");
    }

    private static void applyPacket(Chunk chunk, S21PacketChunkData packet) {
        chunk.fillChunk(packet.getExtractedDataBytes(), packet.getExtractedSize(), packet.func_149274_i());
    }

    private static byte[] frame(Minecraft client, int expectedModels, int expectedVertices) {
        return frame(client, expectedModels, expectedVertices, null);
    }

    private static byte[] frame(Minecraft client, int expectedModels, int expectedVertices, BlockPos[] neighbors) {
        return frame(client, expectedModels, expectedVertices, neighbors, false);
    }

    private static byte[] frame(Minecraft client, int expectedModels, int expectedVertices, BlockPos[] neighbors,
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
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.color(1, 1, 1, 1);
        client.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        BlockRendererDispatcher models = client.getBlockRendererDispatcher();
        Tessellator terrain = new Tessellator(4096);
        WorldRenderer buffer = terrain.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        buffer.setTranslation(-client.thePlayer.posX, -client.thePlayer.posY, -client.thePlayer.posZ);
        models.renderBlock(Blocks.stone.getDefaultState(), STONE, client.theWorld, buffer);
        terrain.draw();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        TranslucentBatch translucent = null;
        int atlas = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (neighbors != null) {
            prepareTranslucent(buffer);
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            // Compiled terrain can use a region origin rather than the camera or chunk origin.
            if (!referenceOrder && ForgeClient.barriersVisible()) {
                buffer.setTranslation(-256, 0, -256);
            } else {
                buffer.setTranslation(-client.thePlayer.posX, -client.thePlayer.posY, -client.thePlayer.posZ);
            }
            for (BlockPos pos : neighbors) {
                models.renderBlock(client.theWorld.getBlockState(pos), pos, client.theWorld, buffer);
            }
            try {
                if (Boolean.TRUE.equals(WorldRenderer.class.getMethod("isMultiTexture").invoke(buffer))) {
                    SmokeTestReport.record("multi-texture-terrain");
                }
            } catch (NoSuchMethodException vanilla) {
                // Vanilla uses the block atlas directly.
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot inspect terrain texture mode", failure);
            }
            if (referenceOrder) {
                for (BlockPos pos : new BlockPos[]{FIRST, SECOND, OCCLUDED}) {
                    models.getBlockModelRenderer().renderModel(client.theWorld,
                            models.getModelFromBlockState(Blocks.barrier.getDefaultState(), client.theWorld, pos),
                            Blocks.barrier.getDefaultState(), pos, buffer);
                }
                buffer.setTranslation(0, 0, 0);
                buffer.sortVertexData(0, 0, 0);
            }
            if (!referenceOrder && ForgeClient.barriersVisible()) {
                buffer.finishDrawing();
                WorldRenderer.State snapshot = buffer.getVertexState();
                buffer.setTranslation(17, 19, 23);
                net.minecraft.client.renderer.chunk.CompiledChunk compiled = new net.minecraft.client.renderer.chunk.CompiledChunk();
                compiled.setState(snapshot);
                translucent = ((TranslucentMesh) compiled).lvb$translucentBatch();
            } else {
                client.entityRenderer.enableLightmap();
                terrain.draw();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas);
                client.entityRenderer.disableLightmap();
            }
        }
        RenderSmokeTestProbe.resetModels();
        RenderSmokeTestProbe.watchModels = true;
        final TranslucentBatch capturedTerrain = translucent;
        try {
            if (RenderingPlugin.state().mode == RenderingPolicy.Mode.NATIVE) {
                prepareTranslucent(buffer);
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                buffer.setTranslation(-client.thePlayer.posX, -client.thePlayer.posY, -client.thePlayer.posZ);
                for (BlockPos pos : new BlockPos[]{FIRST, SECOND, OCCLUDED}) {
                    models.renderBlock(Blocks.barrier.getDefaultState(), pos, client.theWorld, buffer);
                }
                buffer.setTranslation(0, 0, 0);
                buffer.sortVertexData(0, 0, 0);
                client.entityRenderer.enableLightmap();
                terrain.draw();
                client.entityRenderer.disableLightmap();
            } else if (!referenceOrder) {
                BARRIERS.renderWithTerrain(client, client.thePlayer, 1,
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

    private static void verifyFrameHook(Minecraft client, boolean hasBarriers) {
        client.thePlayer.rotationYaw = client.thePlayer.prevRotationYaw = 180;
        client.thePlayer.rotationPitch = client.thePlayer.prevRotationPitch = 20;
        boolean previousAnaglyph = client.gameSettings.anaglyph;
        try {
            // Exercise each hook behavior once; the pixel fixture covers both terrain backends.
            for (int scenario = 0; scenario < (hasBarriers ? 3 : 1); scenario++) {
                boolean stereo = scenario == 1;
                client.gameSettings.anaglyph = stereo;
                RenderSmokeTestProbe.cancelTerrainLayer = scenario == 2;
                RenderSmokeTestProbe.resetModels();
                RenderSmokeTestProbe.watchModels = true;
                client.entityRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
                require(RenderSmokeTestProbe.cameraPasses == (stereo ? 2 : 1), "Incorrect camera pass count");
                require(RenderSmokeTestProbe.eyeMask == (stereo ? 3 : 4), "Camera eye was skipped");
                require(RenderSmokeTestProbe.barrierPasses == RenderSmokeTestProbe.cameraPasses,
                        "Expected exactly one barrier pass per camera eye");
                require((RenderSmokeTestProbe.barriers > 0) == hasBarriers, "Unexpected barrier submission");
            }
            SmokeTestReport.record(hasBarriers ? "camera-pass-counts" : "empty-camera-pass-counts");
        } finally {
            client.gameSettings.anaglyph = previousAnaglyph;
            RenderSmokeTestProbe.watchModels = false;
            RenderSmokeTestProbe.cancelTerrainLayer = false;
        }
    }

    private static void prepareTranslucent(WorldRenderer buffer) {
        try {
            WorldRenderer.class.getMethod("setBlockLayer", EnumWorldBlockLayer.class).invoke(buffer, EnumWorldBlockLayer.TRANSLUCENT);
        } catch (NoSuchMethodException vanilla) {
            // Vanilla has no per-sprite terrain uploader.
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot prepare translucent terrain fixture", failure);
        }
    }

    private static void verifyCompiledTerrain(Minecraft client) {
        boolean previousVbo = client.gameSettings.useVbo;
        try {
            for (boolean vbo : new boolean[]{false, true}) {
                client.gameSettings.useVbo = vbo;
                client.renderGlobal.loadRenderers();
                RenderSmokeTestProbe.resetModels();
                RenderSmokeTestProbe.watchModels = true;
                long deadline = System.nanoTime() + 5_000_000_000L;
                while (RenderSmokeTestProbe.terrainBatches == 0 && System.nanoTime() < deadline) {
                    client.entityRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
                }
                require(RenderSmokeTestProbe.terrainBatches > 0, "Compiled terrain did not enter the shared order");
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error in shared terrain submission");
            }
            SmokeTestReport.record("shared-terrain-submission");
        } finally {
            RenderSmokeTestProbe.watchModels = false;
            client.gameSettings.useVbo = previousVbo;
        }
    }

    private void verifyTranslucentNeighbors(Minecraft client) {
        for (Block[] materials : new Block[][]{{Blocks.stained_glass}, {Blocks.water},
                {Blocks.water, Blocks.stained_glass}}) {
            int[][] scenes = materials.length == 1 ? new int[][]{{-1}, {1}, {-1, 1}} : new int[][]{{-1, 1}};
            for (int[] depths : scenes) {
                BlockPos[] neighbors = new BlockPos[depths.length];
                for (int i = 0; i < depths.length; i++) {
                    neighbors[i] = new BlockPos(0, 64, depths[i]);
                    client.theWorld.setBlockState(neighbors[i], materials[i % materials.length].getDefaultState(), 2);
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
                    client.theWorld.setBlockState(pos, Blocks.air.getDefaultState(), 2);
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

    private static void verifyBatchRecovery(Minecraft client) {
        RenderSmokeTestProbe.modelAction = () -> { throw new RenderSmokeTestProbe.InjectedFailure(); };
        RenderSmokeTestProbe.expectFailure(() -> frame(client, 3, 64), RenderSmokeTestProbe.InjectedFailure.class);
        require(RenderSmokeTestProbe.modelAction == null, "Model failure was not exercised");
        frame(client, 3, 64);
        RenderSmokeTestProbe.modelAction = () -> BARRIERS.render(client, client.thePlayer, 1);
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
