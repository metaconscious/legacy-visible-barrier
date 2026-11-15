package io.github.metaconscious.lvb.forge;

/** Counters supplied exclusively by the test mod's Minecraft hooks. */
public final class RenderSmokeTestProbe {
    public static final Thread OWNER = Thread.currentThread();
    public static int terrainBatches;
    public static int barrierPasses;
    public static int cameraPasses;
    public static int eyeMask;
    public static boolean failLightmap;
    public static Runnable modelAction;
    public static boolean watchModels;
    public static boolean watchTerrain;
    public static boolean cancelTerrainLayer;
    public static int hitboxes;
    public static int highlights;
    public static int barriers;
    public static int otherBlocks;
    public static int vertices;
    public static int invalidations;
    public static int nativeRefreshes;
    public static int nativeSections;
    public static int firstVertex;

    private RenderSmokeTestProbe() {
    }

    public static void regionTerrain() {
        SmokeTestReport.record("vbo-region-terrain");
    }

    public static void verifyShaderStage() {
        verifyShaderStage(cameraPasses > 0);
    }

    public static void verifyShaderStage(boolean eligible) {
        String expected = System.getProperty("lvb.smoke.shaderPack");
        if (expected == null || !eligible) {
            return;
        }
        try {
            Class<?> shaders = Class.forName("net.optifine.shaders.Shaders");
            Object actual = shaders.getMethod("getShaderPackName").invoke(null);
            if (!expected.equals(actual)
                    || org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM) == 0) {
                throw new AssertionError("Requested shader pack is not active in the translucent pass");
            }
            SmokeTestReport.record("shader-pack-active");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot verify the requested shader pack", failure);
        }
    }

    public static void resetModels() {
        terrainBatches = 0;
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
