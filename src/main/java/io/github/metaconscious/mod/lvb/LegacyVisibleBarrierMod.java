package io.github.metaconscious.mod.lvb;

import net.fabricmc.api.ModInitializer;

public class LegacyVisibleBarrierMod implements ModInitializer {

    public static final String MOD_ID = "lvb";
    public static final String MOD_NAME = "Legacy Visible Barrier";
    public static final String MOD_VERSION = "1.0";

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        System.out.println("Hello Fabric world!");
    }
}
