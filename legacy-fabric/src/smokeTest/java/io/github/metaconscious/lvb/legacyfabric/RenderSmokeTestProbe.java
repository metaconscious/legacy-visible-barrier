package io.github.metaconscious.lvb.legacyfabric;

/** Counters supplied exclusively by the test mod's Minecraft hooks. */
public final class RenderSmokeTestProbe {
    public static final Thread OWNER = Thread.currentThread();
    public static int barrierPasses;
    public static int cameraPasses;
    public static int eyeMask;
    public static boolean failLightmap;
    public static Runnable modelAction;
    public static boolean watchModels;
    public static boolean watchTerrain;
    public static boolean cancelTerrainLayer;
    public static int chunkBorders;
    public static float outlineWidth;
    public static float barrierProjectionW;
    public static int hitboxes;
    public static int highlights;
    public static int barriers;
    public static int otherBlocks;
    public static int vertices;
    public static int invalidations;
    public static int firstVertex;

    private RenderSmokeTestProbe() {
    }

    public static void resetModels() {
        barrierPasses = 0;
        cameraPasses = 0;
        eyeMask = 0;
        barriers = 0;
        otherBlocks = 0;
        vertices = 0;
    }

    static void expectFailure(Runnable action, Class<? extends Throwable> type) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (type.isInstance(cause)) {
                    return;
                }
            }
            throw failure;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    public static final class InjectedFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
