package io.github.metaconscious.lvb.core;

import java.util.Objects;

/** Client-owned edits. Reset and navigation do not commit preferences. */
public final class SettingsDraft {
    private final RenderingPreferences saved;
    private RenderingPreferences value;

    public SettingsDraft(RenderingPreferences saved) {
        this.saved = Objects.requireNonNull(saved, "saved");
        value = saved;
    }

    public RenderingPreferences saved() { return saved; }
    public RenderingPreferences value() { return value; }
    public boolean dirty() { return !saved.equals(value); }
    public void reset() { value = RenderingPreferences.DEFAULTS; }

    public void cycleMode() {
        String next = value.initialMode() == RenderingPolicy.Mode.ENHANCED ? "native"
                : value.initialMode() == RenderingPolicy.Mode.NATIVE ? "off" : "auto";
        value = new RenderingPreferences(next, value.nativeRefresh);
    }

    public void cycleRefresh() {
        value = new RenderingPreferences(value.renderMode, value.fullReload() ? "dirty" : "reload");
    }
}
