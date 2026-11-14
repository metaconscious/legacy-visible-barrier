package io.github.metaconscious.lvb.ornithe;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.world.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

import java.util.UUID;
import java.util.List;

/** Exercises world overlays with a selectable barrier and another player in the scene. */
final class ModCompatibilitySmokeTest {
    private static final String[] FEATURES = {"villages", "waypoints", "axolotlOutlines", "miningProgress"};

    private ModCompatibilitySmokeTest() {
    }

    static void verify(Minecraft client, Runnable toggle) throws ReflectiveOperationException {
        boolean enabled = false;
        for (String feature : FEATURES) {
            enabled |= Boolean.getBoolean("lvb.smoke." + feature);
        }
        if (!enabled) {
            return;
        }

        configureMods(client);
        if (Boolean.getBoolean("lvb.smoke.miningProgress")) {
            client.worldRenderer.updateBlockMiningProgress(421, new BlockPos(3, 64, 0), 3);
        }
        double x = client.player.x;
        float pitch = client.player.pitch;
        float headYaw = client.player.headYaw;
        Entity other = new RemoteClientPlayerEntity(client.world,
                new GameProfile(new UUID(0, 42), "OverlayFixture"));
        other.setPosition(3.5, 64, 2);
        other.prevX = other.prevTickX = other.x;
        other.prevY = other.prevTickY = other.y;
        other.prevZ = other.prevTickZ = other.z;
        client.world.addEntity(420, other);
        client.player.setPosition(0.5, client.player.y, client.player.z);
        client.player.prevX = client.player.prevTickX = client.player.x;
        client.player.pitch = client.player.prevPitch = 28;
        client.player.headYaw = client.player.prevHeadYaw = client.player.yaw;
        try {
            // WorldRenderer defers entity rendering for the first frames after a world change.
            for (int i = 0; i < 3; i++) {
                client.gameRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
            }
            for (int i = 0; i < 4; i++) {
                RenderSmokeTestProbe.resetModels();
                RenderSmokeTestProbe.villages = 0;
                RenderSmokeTestProbe.waypoints = 0;
                RenderSmokeTestProbe.outlineWidth = 0;
                RenderSmokeTestProbe.watchModels = true;
                client.gameRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
                RenderSmokeTestProbe.watchModels = false;
                require(client.crosshairTarget != null && client.crosshairTarget.type == HitResult.Type.BLOCK
                        && new BlockPos(0, 64, 0).equals(client.crosshairTarget.getPos()),
                        "Overlay scene did not select the barrier: " + client.crosshairTarget);
                require((RenderSmokeTestProbe.barriers > 0) == OrnitheClient.barriersVisible(),
                        "World overlays changed barrier visibility");
                if (Boolean.getBoolean("lvb.smoke.villages")) {
                    require(RenderSmokeTestProbe.villages > 0, "Village marker did not draw");
                }
                if (Boolean.getBoolean("lvb.smoke.waypoints")) {
                    require(RenderSmokeTestProbe.waypoints > 0, "World waypoint label did not draw");
                }
                if (Boolean.getBoolean("lvb.smoke.axolotlOutlines")) {
                    require(RenderSmokeTestProbe.outlineWidth == 4, "Custom Axolotl outline did not draw");
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
            if (Boolean.getBoolean("lvb.smoke.miningProgress")) {
                client.worldRenderer.updateBlockMiningProgress(421, new BlockPos(3, 64, 0), -1);
            }
            client.player.setPosition(x, client.player.y, client.player.z);
            client.player.prevX = client.player.prevTickX = x;
            client.player.pitch = client.player.prevPitch = pitch;
            client.player.headYaw = client.player.prevHeadYaw = headYaw;
        }
    }

    @SuppressWarnings("unchecked")
    private static void configureMods(Minecraft client) throws ReflectiveOperationException {
        if (Boolean.getBoolean("lvb.smoke.villages")) {
            Class<?> type = Class.forName("kabopc.village_marker.client.KaboVillageMarkerClient");
            Object marker = type.getField("INSTANCE").get(null);
            List<Object> villages = (List<Object>) type.getField("villages").get(marker);
            villages.add(Class.forName("kabopc.village_marker.client.VillageView")
                    .getConstructor(int.class, BlockPos.class).newInstance(4, new BlockPos(1, 64, 0)));
        }
        if (Boolean.getBoolean("lvb.smoke.waypoints")) {
            Class<?> type = Class.forName("io.github.axolotlclient.waypoints.AxolotlClientWaypoints");
            Object storage = type.getField("WAYPOINT_STORAGE").get(null);
            List<Object> waypoints = (List<Object>) storage.getClass().getMethod("getWaypoints").invoke(storage);
            Class<?> colorType = Class.forName("io.github.axolotlclient.AxolotlClientConfig.api.util.Color");
            Object color = colorType.getConstructor(int.class, int.class, int.class, int.class)
                    .newInstance(20, 200, 240, 180);
            waypoints.add(Class.forName("io.github.axolotlclient.waypoints.waypoints.Waypoint")
                    .getConstructor(String.class, double.class, double.class, double.class,
                            colorType, String.class, String.class)
                    .newInstance(client.world.dimension.getName(), 0.5, 65.5, 2.0, color, "Barrier test", "LVB"));
        }
        if (Boolean.getBoolean("lvb.smoke.axolotlOutlines")) {
            Class<?> type = Class.forName("io.github.axolotlclient.AxolotlClient");
            Object config = type.getMethod("config").invoke(null);
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
