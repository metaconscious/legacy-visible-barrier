package io.github.metaconscious.lvb.forge;

import io.github.metaconscious.lvb.core.ClientEffects;
import io.github.metaconscious.lvb.core.VisibilityController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.atomic.AtomicBoolean;

@Mod(modid = "lvb", name = "Legacy Visible Barrier", useMetadata = true,
        acceptedMinecraftVersions = "[1.8.9]", clientSideOnly = true, acceptableRemoteVersions = "*",
        guiFactory = "io.github.metaconscious.lvb.forge.SettingsGuiFactory")
public final class ForgeClient {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private Minecraft client;
    private RenderingCoordinator rendering;
    private VisibilityController controller;
    private KeyBinding toggle;
    private KeyBinding settings;

    public static boolean barriersVisible() { return RenderingPlugin.visible(); }

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) { initialize(); }

    public void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        client = Minecraft.getMinecraft();
        rendering = new RenderingCoordinator(client);
        controller = new VisibilityController(new ClientEffects() {
            @Override public boolean isWorldAvailable() { return client.theWorld != null && client.thePlayer != null; }
            @Override public void showVisibility(boolean visible) { rendering.showVisibility(visible); }
        });
        toggle = new KeyBinding("key.toggle_barrier_visibility", Keyboard.KEY_B,
                "key.category.legacy_visible_barriers");
        settings = new KeyBinding("key.open_lvb_settings", Keyboard.KEY_NONE,
                "key.category.legacy_visible_barriers");
        ClientRegistry.registerKeyBinding(toggle);
        ClientRegistry.registerKeyBinding(settings);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        rendering.recover();
        while (toggle.isPressed()) controller.toggle();
        while (settings.isPressed()) {
            if (client.currentScreen == null && client.theWorld != null && client.thePlayer != null) {
                client.displayGuiScreen(new SettingsScreen(null));
            }
        }
        rendering.finishTick();
    }
}
