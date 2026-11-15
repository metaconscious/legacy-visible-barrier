package io.github.metaconscious.lvb.forge;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.opengl.GL11;

import java.util.UUID;

/** Exercises world overlays with a selectable barrier and another player in the scene. */
final class ModCompatibilitySmokeTest {
    private static final String[] FEATURES = {"expectHitboxes", "expectHighlights"};

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

        client.getRenderManager().setDebugBoundingBox(Boolean.getBoolean("lvb.smoke.expectHitboxes"));
        double x = client.thePlayer.posX;
        float pitch = client.thePlayer.rotationPitch;
        float headYaw = client.thePlayer.rotationYawHead;
        Entity other = new EntityOtherPlayerMP(client.theWorld,
                new GameProfile(new UUID(0, 42), "OverlayFixture"));
        other.setPosition(1.5, 64, 0);
        other.prevPosX = other.lastTickPosX = other.posX;
        other.prevPosY = other.lastTickPosY = other.posY;
        other.prevPosZ = other.lastTickPosZ = other.posZ;
        client.theWorld.addEntityToWorld(420, other);
        client.thePlayer.setPosition(0.5, client.thePlayer.posY, client.thePlayer.posZ);
        client.thePlayer.prevPosX = client.thePlayer.lastTickPosX = client.thePlayer.posX;
        client.thePlayer.rotationPitch = client.thePlayer.prevRotationPitch = 28;
        client.thePlayer.rotationYawHead = client.thePlayer.prevRotationYawHead = client.thePlayer.rotationYaw;
        try {
            // WorldRenderer defers entity rendering for the first frames after a world change.
            for (int i = 0; i < 3; i++) {
                client.entityRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
            }
            for (int i = 0; i < 4; i++) {
                if (!RenderingPlugin.enhanced()) {
                    ClientSmokeTest.awaitNativeTerrain(client, ForgeClient.barriersVisible());
                    client.thePlayer.rotationPitch = client.thePlayer.prevRotationPitch = 28;
                }
                RenderSmokeTestProbe.resetModels();
                RenderSmokeTestProbe.hitboxes = 0;
                RenderSmokeTestProbe.highlights = 0;
                RenderSmokeTestProbe.watchModels = true;
                client.entityRenderer.renderWorld(1, System.nanoTime() + 100_000_000L);
                RenderSmokeTestProbe.watchModels = false;
                require(client.objectMouseOver != null && client.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                        && new BlockPos(0, 64, 0).equals(client.objectMouseOver.getBlockPos()),
                        "Overlay scene did not select the barrier: " + client.objectMouseOver);
                if (RenderingPlugin.enhanced()) {
                    require((RenderSmokeTestProbe.barriers > 0) == ForgeClient.barriersVisible(),
                            "World overlays changed barrier visibility");
                }
                if (Boolean.getBoolean("lvb.smoke.expectHitboxes")) {
                    require(RenderSmokeTestProbe.hitboxes > 0, "Enabled hitbox overlay did not draw");
                }
                if (Boolean.getBoolean("lvb.smoke.expectHighlights")) {
                    require(RenderSmokeTestProbe.highlights > 0, "Enabled block highlight did not draw");
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
            client.theWorld.removeEntityFromWorld(420);
            client.thePlayer.setPosition(x, client.thePlayer.posY, client.thePlayer.posZ);
            client.thePlayer.prevPosX = client.thePlayer.lastTickPosX = x;
            client.thePlayer.rotationPitch = client.thePlayer.prevRotationPitch = pitch;
            client.thePlayer.rotationYawHead = client.thePlayer.prevRotationYawHead = headYaw;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
