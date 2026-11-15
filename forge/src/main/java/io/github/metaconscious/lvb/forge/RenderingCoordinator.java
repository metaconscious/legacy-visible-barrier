package io.github.metaconscious.lvb.forge;

import io.github.metaconscious.lvb.core.RenderingPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;

/** Applies rendering decisions and coalesced native invalidation on the client thread. */
final class RenderingCoordinator {
    private final Minecraft client;
    private final RenderingPolicy policy = RenderingPlugin.initialize();
    private String notice;
    private RenderingPlugin.Problem handled;

    RenderingCoordinator(Minecraft client) {
        this.client = client;
        if (policy.decision().mode == RenderingPolicy.Mode.NATIVE) notice = "lvb.mode_native";
    }

    void recover() {
        if (client.theWorld != null && policy.decision().available()) {
            try {
                BlockModelShapes models = client.getBlockRendererDispatcher().getBlockModelShapes();
                if (models.getModelForState(Blocks.barrier.getDefaultState()) == null
                        || models.getModelForState(Blocks.barrier.getDefaultState()) == models.getModelManager().getMissingModel()) {
                    RenderingPlugin.report(RenderingPolicy.Failure.MODEL,
                            new IllegalStateException("Barrier block model is missing"));
                }
            } catch (RuntimeException failure) {
                RenderingPlugin.report(RenderingPolicy.Failure.MODEL, failure);
            }
        }
        RenderingPlugin.Problem problem = RenderingPlugin.problem();
        if (problem != null && policy.fail(problem.kind)) {
            if (client.renderGlobal instanceof BarrierRenderPass) {
                ((BarrierRenderPass) client.renderGlobal).lvb$endBarriers();
            }
            notice = policy.decision().available() ? "lvb.mode_native" : "lvb.unavailable";
            RenderingPlugin.LOG.warn("LVB rendering changed to " + policy.decision().mode, problem.cause);
        }
        if (problem != null) handled = problem;
    }

    void showVisibility(boolean visible) {
        if (RenderingPlugin.problem() != null) recover();
        policy.requestVisibility(visible);
        RenderingPolicy.State state = policy.decision();
        String key = !state.available() ? (state.mode == RenderingPolicy.Mode.OFF ? "lvb.disabled" : "lvb.unavailable")
                : visible ? "lvb.to_visible" : "lvb.to_invisible";
        client.thePlayer.addChatMessage(new ChatComponentTranslation(key));
    }

    void finishTick() {
        boolean refresh = policy.takeNativeRefresh();
        if (handled != null) {
            RenderingPlugin.acknowledge(handled);
            handled = null;
        }
        if (client.theWorld == null || client.thePlayer == null) return;
        if (refresh) {
            if (RenderingPlugin.fullReload()) {
                client.renderGlobal.loadRenderers();
            } else {
                Entity camera = client.getRenderViewEntity();
                if (camera == null) camera = client.thePlayer;
                int x = (MathHelper.floor_double(camera.posX) >> 4) << 4;
                int z = (MathHelper.floor_double(camera.posZ) >> 4) << 4;
                int radius = (client.gameSettings.renderDistanceChunks + 1) * 16;
                client.renderGlobal.markBlockRangeForRenderUpdate(x - radius, 0, z - radius, x + radius, 255, z + radius);
            }
        }
        if (notice != null) {
            client.thePlayer.addChatMessage(new ChatComponentTranslation(notice));
            notice = null;
        }
    }
}
