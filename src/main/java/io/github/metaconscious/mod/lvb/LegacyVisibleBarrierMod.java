package io.github.metaconscious.mod.lvb;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class LegacyVisibleBarrierMod implements ModInitializer {

    public static final String MOD_ID = "lvb";
    public static final String MOD_NAME = "Legacy Visible Barrier";
    public static final String MOD_VERSION = "1.0";

    private static final Logger LOGGER = LogManager.getLogger();
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
        LOGGER.info("Mod '{}'({}==v{}) has been initialized.", MOD_NAME, MOD_ID, MOD_VERSION);
    }

    public KeyBindings getKeyBindings() {
        return keyBindings;
    }
}
