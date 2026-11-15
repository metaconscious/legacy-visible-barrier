package io.github.metaconscious.lvb.legacyfabric;

import io.github.metaconscious.lvb.core.RenderingPolicy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.block.BlockModelShapes;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.MathHelper;

/** Applies rendering decisions and coalesced native invalidation on the client thread. */
final class RenderingCoordinator {
    private final MinecraftClient client;
    private final RenderingPolicy policy = RenderingPlugin.initialize();
    private String notice;
    private RenderingPlugin.Problem handled;

    RenderingCoordinator(MinecraftClient client) {
        this.client = client;
        if (policy.decision().mode == RenderingPolicy.Mode.NATIVE) notice = "lvb.mode_native";
    }

    void recover() {
        if (client.world != null && policy.decision().available()) {
            try {
                BlockModelShapes models = client.getBlockRenderManager().getModels();
                if (models.getBakedModel(Blocks.BARRIER.getDefaultState()) == null
                        || models.getBakedModel(Blocks.BARRIER.getDefaultState()) == models.getBakedModelManager().getBakedModel()) {
                    RenderingPlugin.report(RenderingPolicy.Failure.MODEL,
                            new IllegalStateException("Barrier block model is missing"));
                }
            } catch (RuntimeException failure) {
                RenderingPlugin.report(RenderingPolicy.Failure.MODEL, failure);
            }
        }
        RenderingPlugin.Problem problem = RenderingPlugin.problem();
        if (problem != null && policy.fail(problem.kind)) {
            if (client.worldRenderer instanceof BarrierRenderPass) {
                ((BarrierRenderPass) client.worldRenderer).lvb$endBarriers();
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
        client.player.sendMessage(new TranslatableText(key));
    }

    void finishTick() {
        boolean refresh = policy.takeNativeRefresh();
        if (handled != null) {
            RenderingPlugin.acknowledge(handled);
            handled = null;
        }
        if (client.world == null || client.player == null) return;
        if (refresh) {
            if (RenderingPlugin.fullReload()) {
                client.worldRenderer.reload();
            } else {
                Entity camera = client.getCameraEntity();
                if (camera == null) camera = client.player;
                int x = (MathHelper.floor(camera.x) >> 4) << 4;
                int z = (MathHelper.floor(camera.z) >> 4) << 4;
                int radius = (client.options.viewDistance + 1) * 16;
                client.worldRenderer.onRenderRegionUpdate(x - radius, 0, z - radius, x + radius, 255, z + radius);
            }
        }
        if (notice != null) {
            client.player.sendMessage(new TranslatableText(notice));
            notice = null;
        }
    }
}
