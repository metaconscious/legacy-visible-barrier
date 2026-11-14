package io.github.metaconscious.lvb.legacyfabric;

import io.github.metaconscious.lvb.core.RenderingPolicy;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Bootstrap-safe hook selection and a bounded mailbox for client-thread recovery. */
public final class RenderingPlugin implements IMixinConfigPlugin {
    static final Logger LOG = LogManager.getLogger("lvb");
    private static final AtomicReference<Problem> PROBLEM = new AtomicReference<>();
    private static volatile RenderingPolicy policy;
    private static RenderingPolicy.Mode initialMode = RenderingPolicy.Mode.ENHANCED;
    private static boolean fullReload;

    @Override
    public void onLoad(String mixinPackage) {
        Properties properties = new Properties();
        Path path = FabricLoader.getInstance().getConfigDir().resolve("lvb.properties");
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
            String mode = System.getProperty("lvb.renderMode", properties.getProperty("renderMode", "auto"));
            switch (mode) {
                case "auto": case "enhanced": initialMode = RenderingPolicy.Mode.ENHANCED; break;
                case "native": initialMode = RenderingPolicy.Mode.NATIVE; break;
                case "off": initialMode = RenderingPolicy.Mode.OFF; break;
                default: throw new IllegalArgumentException("Unknown renderMode: " + mode);
            }
            String refresh = System.getProperty("lvb.nativeRefresh", properties.getProperty("nativeRefresh", "dirty"));
            if (!"dirty".equals(refresh) && !"reload".equals(refresh)) {
                throw new IllegalArgumentException("Unknown nativeRefresh: " + refresh);
            }
            fullReload = "reload".equals(refresh);
        } catch (IOException | IllegalArgumentException failure) {
            initialMode = RenderingPolicy.Mode.NATIVE;
            fullReload = false;
            LOG.warn("Cannot read LVB rendering configuration; using native mode with dirty refresh", failure);
        }
    }

    public static RenderingPolicy initialize() {
        RenderingPolicy result = new RenderingPolicy(initialMode);
        policy = result;
        return result;
    }

    public static RenderingPolicy.State state() {
        RenderingPolicy current = policy;
        return current == null ? null : current.state();
    }

    public static boolean enhanced() {
        return PROBLEM.get() == null && enhancedMode();
    }

    /** In-flight uploads finish under their original contract before client-tick recovery. */
    public static boolean enhancedMode() {
        RenderingPolicy.State current = state();
        return (current == null ? initialMode : current.mode) == RenderingPolicy.Mode.ENHANCED;
    }

    public static boolean visible() {
        RenderingPolicy.State current = state();
        return PROBLEM.get() == null && current != null && current.visible();
    }

    public static boolean nativeVisible() {
        RenderingPolicy.State current = state();
        return PROBLEM.get() == null && current != null && current.nativeVisible();
    }

    static boolean fullReload() { return fullReload; }

    /** Worker callers publish evidence only; the client coordinator owns the transition. */
    public static void report(RenderingPolicy.Failure kind, RuntimeException cause) {
        RenderingPolicy.State current = state();
        if (current != null && (!current.available()
                || kind == RenderingPolicy.Failure.ENHANCED && current.mode != RenderingPolicy.Mode.ENHANCED)) return;
        Problem next = new Problem(kind, cause);
        PROBLEM.updateAndGet(previous -> previous == null || previous.kind == RenderingPolicy.Failure.ENHANCED
                && kind == RenderingPolicy.Failure.MODEL ? next : previous);
    }

    static Problem problem() { return PROBLEM.get(); }
    static void acknowledge(Problem problem) { PROBLEM.compareAndSet(problem, null); }

    static final class Problem {
        final RenderingPolicy.Failure kind;
        final RuntimeException cause;
        Problem(RenderingPolicy.Failure kind, RuntimeException cause) {
            this.kind = kind;
            this.cause = cause;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (initialMode == RenderingPolicy.Mode.OFF) return false;
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        return initialMode == RenderingPolicy.Mode.ENHANCED || name.equals("BarrierBlockMixin")
                || name.equals("BarrierParticleMixin") || name.equals("BlockStateMapperMixin");
    }

    @Override
    public void postApply(String targetName, ClassNode target, String mixinName, IMixinInfo info) {
        if (mixinName.endsWith(".GameRendererMixin")) {
            verifyCall(target, "lvb$prepareBarriers");
            verifyCall(target, "lvb$renderBarriers");
        } else if (mixinName.endsWith(".WorldRendererMixin")) {
            verifyCall(target, "lvb$orderedTerrain");
        }
    }

    private static void verifyCall(ClassNode target, String handler) {
        for (MethodNode method : target.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode
                        && ((MethodInsnNode) instruction).name.endsWith(handler)) return;
            }
        }
        report(RenderingPolicy.Failure.ENHANCED,
                new IllegalStateException("Missing enhanced rendering hook: " + handler));
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
