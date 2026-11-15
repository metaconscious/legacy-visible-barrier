package io.github.metaconscious.lvb.legacyfabric;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.OtherClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

import java.util.UUID;

/** Exercises world overlays with a selectable barrier and another player in the scene. */
final class ModCompatibilitySmokeTest {
    private static final String[] FEATURES = {"chunkBorders", "expectHitboxes", "expectHighlights", "orthographic", "axolotlOutlines"};

    private ModCompatibilitySmokeTest() {
    }

    static void verify(MinecraftClient client, Runnable toggle) throws ReflectiveOperationException {
        boolean enabled = false;
        for (String feature : FEATURES) {
            enabled |= Boolean.getBoolean("lvb.smoke." + feature);
        }
        if (!enabled) {
            return;
        }

        configureMods();
        double x = client.player.x;
        float pitch = client.player.pitch;
        float headYaw = client.player.headYaw;
        Entity other = new OtherClientPlayerEntity(client.world,
                new GameProfile(new UUID(0, 42), "OverlayFixture"));
        other.updatePosition(3.5, 64, 2);
        other.prevX = other.prevTickX = other.x;
        other.prevY = other.prevTickY = other.y;
        other.prevZ = other.prevTickZ = other.z;
        client.world.addEntity(420, other);
        client.player.updatePosition(0.5, client.player.y, client.player.z);
        client.player.prevX = client.player.prevTickX = client.player.x;
        client.player.pitch = client.player.prevPitch = 28;
        client.player.headYaw = client.player.prevHeadYaw = client.player.yaw;
        try {
            // WorldRenderer defers entity rendering for the first frames after a world change.
            for (int i = 0; i < 3; i++) {
                client.gameRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
            }
            for (int i = 0; i < 4; i++) {
                if (!RenderingPlugin.enhanced()) {
                    ClientSmokeTest.awaitNativeTerrain(client, LegacyFabricClient.barriersVisible());
                    client.player.pitch = client.player.prevPitch = 28;
                }
                RenderSmokeTestProbe.resetModels();
                RenderSmokeTestProbe.hitboxes = 0;
                RenderSmokeTestProbe.highlights = 0;
                RenderSmokeTestProbe.chunkBorders = 0;
                RenderSmokeTestProbe.outlineWidth = 0;
                RenderSmokeTestProbe.barrierProjectionW = Float.NaN;
                RenderSmokeTestProbe.watchModels = true;
                client.gameRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
                RenderSmokeTestProbe.watchModels = false;
                require(client.result != null && client.result.type == BlockHitResult.Type.BLOCK
                        && new BlockPos(0, 64, 0).equals(client.result.getBlockPos()),
                        "Overlay scene did not select the barrier: " + client.result);
                if (RenderingPlugin.enhanced()) {
                    require((RenderSmokeTestProbe.barriers > 0) == LegacyFabricClient.barriersVisible(),
                            "World overlays changed barrier visibility");
                }
                if (Boolean.getBoolean("lvb.smoke.expectHitboxes")) {
                    require(RenderSmokeTestProbe.hitboxes > 0, "Enabled hitbox overlay did not draw");
                }
                if (Boolean.getBoolean("lvb.smoke.expectHighlights")) {
                    require(RenderSmokeTestProbe.highlights > 0, "Enabled block highlight did not draw");
                }
                if (Boolean.getBoolean("lvb.smoke.chunkBorders")) {
                    require(RenderSmokeTestProbe.chunkBorders > 0, "Enabled chunk borders did not draw");
                }
                if (Boolean.getBoolean("lvb.smoke.axolotlOutlines")) {
                    require(RenderSmokeTestProbe.outlineWidth == 4, "Custom Axolotl outline did not draw");
                }
                if (Boolean.getBoolean("lvb.smoke.orthographic") && LegacyFabricClient.barriersVisible()) {
                    require(RenderSmokeTestProbe.barrierProjectionW == 1,
                            "Barrier pass did not inherit orthographic projection");
                }
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error with active mod overlays");
                toggle.run();
            }
            SmokeTestReport.record("overlay-scene");
            for (String feature : FEATURES) {
                if (Boolean.getBoolean("lvb.smoke." + feature)) {
                    SmokeTestReport.record("feature:" + feature);
                }
            }
        } finally {
            RenderSmokeTestProbe.watchModels = false;
            client.world.removeEntity(420);
            client.player.updatePosition(x, client.player.y, client.player.z);
            client.player.prevX = client.player.prevTickX = x;
            client.player.pitch = client.player.prevPitch = pitch;
            client.player.headYaw = client.player.prevHeadYaw = headYaw;
        }
    }

    private static void configureMods() throws ReflectiveOperationException {
        if (Boolean.getBoolean("lvb.smoke.chunkBorders")) {
            Class<?> type = Class.forName("dev.tildejustin.chunkborders.DebugRenderer");
            Object renderer = type.getField("instance").get(null);
            if (!(Boolean) type.getMethod("isEnabled").invoke(renderer)) {
                type.getMethod("toggleChunkBorders").invoke(renderer);
            }
        }
        if (Boolean.getBoolean("lvb.smoke.axolotlOutlines")) {
            Class<?> type = Class.forName("io.github.axolotlclient.AxolotlClient");
            Object config = type.getField("CONFIG").get(null);
            Object enabled = config.getClass().getField("enableCustomOutlines").get(config);
            enabled.getClass().getMethod("set", Object.class).invoke(enabled, true);
            Object width = config.getClass().getField("outlineWidth").get(config);
            width.getClass().getMethod("set", Object.class).invoke(width, 3);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
