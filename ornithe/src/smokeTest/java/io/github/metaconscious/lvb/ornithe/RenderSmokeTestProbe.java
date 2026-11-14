package io.github.metaconscious.lvb.ornithe;

/** Counters supplied exclusively by the test mod's Minecraft hooks. */
public final class RenderSmokeTestProbe {
    public static final Thread OWNER = Thread.currentThread();
    public static boolean watchModels;
    public static boolean watchTerrain;
    public static boolean cancelTerrainLayer;
    public static int barriers;
    public static int otherBlocks;
    public static int vertices;
    public static int invalidations;
    public static int firstVertex;

    private RenderSmokeTestProbe() {
    }

    public static void resetModels() {
        barriers = 0;
        otherBlocks = 0;
        vertices = 0;
    }
}
