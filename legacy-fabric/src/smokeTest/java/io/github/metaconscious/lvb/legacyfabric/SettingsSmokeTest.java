package io.github.metaconscious.lvb.legacyfabric;

import io.github.metaconscious.lvb.core.RenderingPolicy;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Exercises actual mapped screens and optional providers in a production client. */
final class SettingsSmokeTest {
    static void verify(MinecraftClient client) throws Exception {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("lvb.properties");
        byte[] original = Files.exists(path) ? Files.readAllBytes(path) : null;
        Screen parent = client.currentScreen;
        RenderingPolicy.State state = RenderingPlugin.state();
        try {
            SettingsScreen screen = create(parent);
            client.setScreen(screen);
            screen.init(client, 320, 240);
            screen.render(0, 0, 0);
            // Keyboard-only edit: Advanced, Refresh. Resize must retain the unsaved value.
            screen.keyPressed('\t', Keyboard.KEY_TAB);
            screen.keyPressed('\n', Keyboard.KEY_RETURN);
            screen.keyPressed('\t', Keyboard.KEY_TAB);
            screen.keyPressed(' ', Keyboard.KEY_SPACE);
            screen.init(client, 480, 270);
            screen.render(0, 0, 0);
            screen.keyPressed('\t', Keyboard.KEY_TAB);
            screen.keyPressed('\n', Keyboard.KEY_RETURN);
            require(client.currentScreen != screen && client.currentScreen != parent, "Controls navigation failed");
            client.setScreen(screen);
            // Remaining choices: Details, Reset, Cancel, Save.
            for (int i = 0; i < 4; i++) screen.keyPressed('\t', Keyboard.KEY_TAB);
            screen.keyPressed('\n', Keyboard.KEY_RETURN);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (client.currentScreen == screen && System.nanoTime() < deadline) {
                screen.tick();
                Thread.sleep(5);
            }
            require(client.currentScreen == parent, "Save must return to its parent");
            SettingsFile.Snapshot saved = RenderingPlugin.settingsFile().read();
            require(saved.preferences.fullReload() != initialRefresh(original), "Resizing lost the edit or Save did not persist it");
            byte[] committed = Files.readAllBytes(path);
            screen = create(parent);
            client.setScreen(screen);
            screen.buttonClicked(new ButtonWidget(4, 0, 0, ""));
            screen.keyPressed((char) 0, Keyboard.KEY_ESCAPE);
            require(client.currentScreen == parent, "Escape must return to its parent");
            require(java.util.Arrays.equals(committed, Files.readAllBytes(path)), "Cancel saved a reset draft");
            require(RenderingPlugin.state() == state, "Settings changed the live rendering snapshot");
            SmokeTestReport.record("settings-ui");
            if (FabricLoader.getInstance().isModLoaded("modmenu")) SmokeTestReport.record("modmenu-screen");
        } finally {
            if (original == null) Files.deleteIfExists(path);
            else Files.write(path, original);
            client.setScreen(parent);
        }
    }

    static void verifyRuntime(MinecraftClient client) {
        Screen parent = client.currentScreen;
        RenderingPolicy.State state = RenderingPlugin.state();
        SettingsScreen screen = new SettingsScreen(parent);
        client.setScreen(screen);
        screen.render(0, 0, 0);
        screen.keyPressed((char) 0, Keyboard.KEY_ESCAPE);
        require(RenderingPlugin.state() == state, "Opening settings reset runtime recovery or visibility");
        require(client.currentScreen == parent, "Settings did not return to the world");
        SmokeTestReport.record("settings-runtime-isolation");
    }

    private static boolean initialRefresh(byte[] bytes) throws java.io.IOException {
        java.util.Properties values = new java.util.Properties();
        if (bytes != null) values.load(new java.io.StringReader(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
        return "reload".equals(values.getProperty("nativeRefresh", "dirty"));
    }

    private static SettingsScreen create(Screen parent) throws Exception {
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("modmenu")) return new SettingsScreen(parent);
        Class<?> api = Class.forName("io.github.prospector.modmenu.api.ModMenuApi");
        for (EntrypointContainer<?> entry : loader.getEntrypointContainers("modmenu", api)) {
            if (entry.getProvider().getMetadata().getId().equals("lvb")) {
                Object factory = api.getMethod("getModConfigScreenFactory").invoke(entry.getEntrypoint());
                Class<?> type = Class.forName("io.github.prospector.modmenu.api.ConfigScreenFactory");
                return (SettingsScreen) type.getMethod("create", Screen.class).invoke(factory, parent);
            }
        }
        throw new AssertionError("Missing LVB Mod Menu entrypoint");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
