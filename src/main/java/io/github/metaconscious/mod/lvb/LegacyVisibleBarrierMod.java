package io.github.metaconscious.mod.lvb;

import net.fabricmc.api.ModInitializer;

public class LegacyVisibleBarrierMod implements ModInitializer {

    public static final String MOD_ID = "lvb";
    public static final String MOD_NAME = "Legacy Visible Barrier";
    public static final String MOD_VERSION = "1.0";

    private static LegacyVisibleBarrierMod INSTANCE;

    private final KeyBindings keyBindings = new KeyBindings();

    public static LegacyVisibleBarrierMod getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Legacy Visible Barrier Mod has not been initialized yet.");
        }
        return INSTANCE;
    }

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        INSTANCE = this;
        keyBindings.init();
    }

    public KeyBindings getKeyBindings() {
        return keyBindings;
    }
}
