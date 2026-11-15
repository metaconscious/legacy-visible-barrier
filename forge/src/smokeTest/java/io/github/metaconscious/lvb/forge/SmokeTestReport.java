package io.github.metaconscious.lvb.forge;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.lwjgl.Sys;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/** Records assertions only after their scenarios complete successfully. */
final class SmokeTestReport {
    private static final Set<String> ASSERTIONS = new TreeSet<>();

    private SmokeTestReport() {
    }

    static void record(String assertion) {
        ASSERTIONS.add(assertion);
    }

    static void write(Path report) throws IOException {
        JsonObject result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("platform", "forge");
        JsonObject runtime = new JsonObject();
        runtime.addProperty("java.version", System.getProperty("java.version"));
        runtime.addProperty("lwjgl", Sys.getVersion());
        runtime.addProperty("gl.version", GL11.glGetString(GL11.GL_VERSION));
        runtime.addProperty("vao", GLContext.getCapabilities().OpenGL30 ? "core"
                : GLContext.getCapabilities().GL_ARB_vertex_array_object ? "arb" : "unavailable");
        result.add("runtime", runtime);
        JsonArray assertions = new JsonArray();
        for (String assertion : ASSERTIONS) {
            assertions.add(new com.google.gson.JsonPrimitive(assertion));
        }
        result.add("assertions", assertions);
        Path output = report.resolveSibling("client.json");
        Files.write(output, (new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n")
                .getBytes(StandardCharsets.UTF_8));
    }
}
