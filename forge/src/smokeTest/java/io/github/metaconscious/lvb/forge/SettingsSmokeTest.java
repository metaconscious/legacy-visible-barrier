package io.github.metaconscious.lvb.forge;

import io.github.metaconscious.lvb.client.SettingsFile;

import io.github.metaconscious.lvb.core.RenderingPolicy;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Exercises actual mapped screens and optional providers in a production client. */
final class SettingsSmokeTest {
    static void verify(Minecraft client) throws Exception {
        require(!net.minecraft.client.resources.I18n.format("lvb.settings.title").equals("lvb.settings.title"),
                "LVB language resources are missing");
        Path path = Loader.instance().getConfigDir().toPath().resolve("lvb.properties");
        byte[] original = Files.exists(path) ? Files.readAllBytes(path) : null;
        GuiScreen parent = client.currentScreen;
        RenderingPolicy.State state = RenderingPlugin.state();
        try {
            SettingsScreen screen = create(parent);
            client.displayGuiScreen(screen);
            screen.setWorldAndResolution(client, 320, 240);
            screen.drawScreen(0, 0, 0);
            // Keyboard-only edit: Advanced, Refresh. Resize must retain the unsaved value.
            screen.keyTyped('\t', Keyboard.KEY_TAB);
            screen.keyTyped('\n', Keyboard.KEY_RETURN);
            screen.keyTyped('\t', Keyboard.KEY_TAB);
            screen.keyTyped(' ', Keyboard.KEY_SPACE);
            screen.setWorldAndResolution(client, 480, 270);
            screen.drawScreen(0, 0, 0);
            screen.keyTyped('\t', Keyboard.KEY_TAB);
            screen.keyTyped('\n', Keyboard.KEY_RETURN);
            require(client.currentScreen != screen && client.currentScreen != parent, "Controls navigation failed");
            client.displayGuiScreen(screen);
            // Remaining choices: Details, Reset, Cancel, Save.
            for (int i = 0; i < 4; i++) screen.keyTyped('\t', Keyboard.KEY_TAB);
            screen.keyTyped('\n', Keyboard.KEY_RETURN);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (client.currentScreen == screen && System.nanoTime() < deadline) {
                screen.updateScreen();
                Thread.sleep(5);
            }
            require(client.currentScreen == parent, "Save must return to its parent");
            SettingsFile.Snapshot saved = RenderingPlugin.settingsFile().read();
            require(saved.preferences.fullReload() != initialRefresh(original), "Resizing lost the edit or Save did not persist it");
            byte[] committed = Files.readAllBytes(path);
            screen = create(parent);
            client.displayGuiScreen(screen);
            screen.actionPerformed(new GuiButton(4, 0, 0, ""));
            screen.keyTyped((char) 0, Keyboard.KEY_ESCAPE);
            require(client.currentScreen == parent, "Escape must return to its parent");
            require(java.util.Arrays.equals(committed, Files.readAllBytes(path)), "Cancel saved a reset draft");
            require(RenderingPlugin.state() == state, "Settings changed the live rendering snapshot");
            SmokeTestReport.record("settings-ui");
            SmokeTestReport.record("forge-config-screen");
        } finally {
            if (original == null) Files.deleteIfExists(path);
            else Files.write(path, original);
            client.displayGuiScreen(parent);
        }
    }

    static void verifyRuntime(Minecraft client) {
        GuiScreen parent = client.currentScreen;
        RenderingPolicy.State state = RenderingPlugin.state();
        SettingsScreen screen = new SettingsScreen(parent);
        client.displayGuiScreen(screen);
        screen.drawScreen(0, 0, 0);
        screen.keyTyped((char) 0, Keyboard.KEY_ESCAPE);
        require(RenderingPlugin.state() == state, "Opening settings reset runtime recovery or visibility");
        require(client.currentScreen == parent, "Settings did not return to the world");
        SmokeTestReport.record("settings-runtime-isolation");
    }

    private static boolean initialRefresh(byte[] bytes) throws java.io.IOException {
        java.util.Properties values = new java.util.Properties();
        if (bytes != null) values.load(new java.io.StringReader(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
        return "reload".equals(values.getProperty("nativeRefresh", "dirty"));
    }

    private static SettingsScreen create(GuiScreen parent) throws Exception {
        IModGuiFactory factory = FMLClientHandler.instance().getGuiFactoryFor(
                Loader.instance().getIndexedModList().get("lvb"));
        require(factory instanceof SettingsGuiFactory, "Missing LVB Forge config factory");
        return (SettingsScreen) factory.mainConfigGuiClass().getConstructor(GuiScreen.class).newInstance(parent);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
