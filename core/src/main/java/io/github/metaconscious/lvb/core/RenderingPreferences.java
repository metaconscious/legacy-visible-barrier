package io.github.metaconscious.lvb.core;

import java.util.Map;
import java.util.Objects;

/** Immutable startup preferences. Parsing is independent of files, loaders and rendering. */
public final class RenderingPreferences {
    public static final RenderingPreferences DEFAULTS = new RenderingPreferences("auto", "dirty");
    public static final RenderingPreferences SAFE = new RenderingPreferences("native", "dirty");

    public final String renderMode;
    public final String nativeRefresh;

    public RenderingPreferences(String renderMode, String nativeRefresh) {
        if (!"auto".equals(renderMode) && !"enhanced".equals(renderMode)
                && !"native".equals(renderMode) && !"off".equals(renderMode)) {
            throw new IllegalArgumentException("Unknown renderMode: " + renderMode);
        }
        if (!"dirty".equals(nativeRefresh) && !"reload".equals(nativeRefresh)) {
            throw new IllegalArgumentException("Unknown nativeRefresh: " + nativeRefresh);
        }
        this.renderMode = renderMode;
        this.nativeRefresh = nativeRefresh;
    }

    /** Resolve overrides before validation, retaining the original startup precedence. */
    public static RenderingPreferences resolve(Map<?, ?> values, String modeOverride, String refreshOverride) {
        Object mode = values.get("renderMode");
        Object refresh = values.get("nativeRefresh");
        return new RenderingPreferences(modeOverride != null ? modeOverride : mode == null ? "auto" : mode.toString(),
                refreshOverride != null ? refreshOverride : refresh == null ? "dirty" : refresh.toString());
    }

    public RenderingPolicy.Mode initialMode() {
        return "off".equals(renderMode) ? RenderingPolicy.Mode.OFF
                : "native".equals(renderMode) ? RenderingPolicy.Mode.NATIVE : RenderingPolicy.Mode.ENHANCED;
    }

    public boolean fullReload() { return "reload".equals(nativeRefresh); }

    public boolean sameBehavior(RenderingPreferences other) {
        return initialMode() == other.initialMode() && fullReload() == other.fullReload();
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof RenderingPreferences)) return false;
        RenderingPreferences that = (RenderingPreferences) other;
        return renderMode.equals(that.renderMode) && nativeRefresh.equals(that.nativeRefresh);
    }

    @Override public int hashCode() { return Objects.hash(renderMode, nativeRefresh); }
}
