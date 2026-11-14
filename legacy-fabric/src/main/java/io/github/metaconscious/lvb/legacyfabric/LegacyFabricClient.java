package io.github.metaconscious.lvb.legacyfabric;

import io.github.metaconscious.lvb.core.ClientEffects;
import io.github.metaconscious.lvb.core.VisibilityController;
import io.github.metaconscious.lvb.core.VisibilityView;
import net.fabricmc.api.ClientModInitializer;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.TranslatableText;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.atomic.AtomicBoolean;

public final class LegacyFabricClient implements ClientModInitializer {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static volatile VisibilityView visibility = () -> false;

    /** Safe before client initialization and from rendering threads. */
    public static boolean barriersVisible() {
        return visibility.isVisible();
    }

    @Override
    public void onInitializeClient() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        VisibilityController controller = new VisibilityController(new ClientEffects() {
            @Override
            public boolean isWorldAvailable() {
                return client.world != null && client.player != null;
            }

            @Override
            public void showVisibility(boolean visible) {
                client.player.sendMessage(new TranslatableText(visible ? "lvb.to_visible" : "lvb.to_invisible"));
            }
        });
        visibility = controller;
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.toggle_barrier_visibility", Keyboard.KEY_B, "key.category.legacy_visible_barriers"));
        ClientTickEvents.END_CLIENT_TICK.register(ignored -> {
            while (key.wasPressed()) {
                controller.toggle();
            }
        });
    }
}
