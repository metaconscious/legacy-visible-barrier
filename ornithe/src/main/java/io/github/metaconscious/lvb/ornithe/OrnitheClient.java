package io.github.metaconscious.lvb.ornithe;

import io.github.metaconscious.lvb.core.ClientEffects;
import io.github.metaconscious.lvb.core.VisibilityController;
import io.github.metaconscious.lvb.core.VisibilityView;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.text.TranslatableText;
import net.ornithemc.osl.keybinds.api.KeyBindingEvents;
import net.ornithemc.osl.lifecycle.api.client.MinecraftClientEvents;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.atomic.AtomicBoolean;

public final class OrnitheClient implements ClientModInitializer {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static volatile VisibilityView visibility = () -> false;

    /** Safe before client initialization and from chunk rendering workers. */
    public static boolean barriersVisible() {
        return visibility.isVisible();
    }

    @Override
    public void onInitializeClient() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        VisibilityController controller = new VisibilityController(new ClientEffects() {
            @Override
            public boolean isWorldAvailable() {
                return client.world != null && client.player != null;
            }

            @Override
            public void showVisibility(boolean visible) {
                client.player.sendMessage(new TranslatableText(visible ? "lvb.to_visible" : "lvb.to_invisible"));
            }

            @Override
            public void refreshRenderer() {
                client.worldRenderer.reload();
            }
        });
        visibility = controller;
        KeyBinding key = new KeyBinding(
                "key.toggle_barrier_visibility", Keyboard.KEY_B, "key.category.legacy_visible_barriers");
        KeyBindingEvents.REGISTER_KEYBINDS.register(registry -> registry.register(key));
        MinecraftClientEvents.TICK_END.register(ignored -> {
            while (key.consumeClick()) {
                controller.toggle();
            }
        });
    }
}
