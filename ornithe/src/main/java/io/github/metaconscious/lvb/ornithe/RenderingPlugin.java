package io.github.metaconscious.lvb.ornithe;

import io.github.metaconscious.lvb.client.SettingsFile;

import io.github.metaconscious.lvb.core.RenderingPolicy;
import io.github.metaconscious.lvb.core.RenderingPreferences;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.metadata.CustomValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Bootstrap-safe hook selection and a bounded mailbox for client-thread recovery. */
public final class RenderingPlugin implements IMixinConfigPlugin {
    static final Logger LOG = LogManager.getLogger("lvb");
    private static final AtomicReference<Problem> PROBLEM = new AtomicReference<>();
    private static volatile RenderingPolicy policy;
    private static RenderingPolicy.Mode initialMode = RenderingPolicy.Mode.ENHANCED;
    private static RenderingPreferences startup = RenderingPreferences.DEFAULTS;
    private static SettingsFile settingsFile;
    private static String modeOverride;
    private static String refreshOverride;
    private static boolean configurationFailed;

    @Override
    public void onLoad(String mixinPackage) {
        verifyEnvironment();
        settingsFile = new SettingsFile(FabricLoader.getInstance().getConfigDir().resolve("lvb.properties"));
        modeOverride = System.getProperty("lvb.renderMode");
        refreshOverride = System.getProperty("lvb.nativeRefresh");
        try {
            startup = settingsFile.read().resolve(modeOverride, refreshOverride);
        } catch (IOException | IllegalArgumentException failure) {
            startup = RenderingPreferences.SAFE;
            configurationFailed = true;
            LOG.warn("Cannot read LVB rendering configuration; using native mode with dirty refresh", failure);
        }
        initialMode = startup.initialMode();
    }

    static SettingsFile settingsFile() { return settingsFile; }
    static boolean modeOverridden() { return modeOverride != null; }
    static boolean refreshOverridden() { return refreshOverride != null; }
    static boolean configurationFailed() { return configurationFailed; }

    static boolean restartRequired(SettingsFile.Snapshot saved) {
        try { return !startup.sameBehavior(saved.resolve(modeOverride, refreshOverride)); }
        catch (IllegalArgumentException invalid) { return false; }
    }

    /** Inspect mappings before any Minecraft type is loaded or rendering hooks are selected. */
    private static void verifyEnvironment() {
        FabricLoader loader = FabricLoader.getInstance();
        CustomValue.CvObject target = loader.getModContainer("lvb").get().getMetadata()
                .getCustomValue("lvb:target").getAsObject();
        MappingResolver mappings = loader.getMappingResolver();
        boolean matches = mappings.getNamespaces().contains("official")
                && mappings.getNamespaces().contains("intermediary");
        if (matches) {
            for (Map.Entry<String, CustomValue> sentinel : target.get("sentinels").getAsObject()) {
                String actual = mappings.unmapClassName("intermediary",
                        mappings.mapClassName("official", sentinel.getKey()));
                matches &= sentinel.getValue().getAsString().equals(actual);
            }
        }
        if (!matches) {
            // Mixin logs and ignores ordinary exceptions while selecting configurations.
            throw new MixinInitialisationError("LVB mapping generation mismatch: this JAR requires "
                    + target.get("name").getAsString()
                    + " for Minecraft 1.8.9. Install the LVB download matching your launcher profile.");
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

    static boolean fullReload() { return startup.fullReload(); }

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
                || name.equals("BarrierParticleMixin") || name.equals("BlockModelsMixin");
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
