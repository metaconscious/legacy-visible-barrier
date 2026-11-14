package io.github.metaconscious.lvb.ornithe;

import io.github.metaconscious.lvb.core.ClientEffects;
import io.github.metaconscious.lvb.core.VisibilityController;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.KeyBinding;
import net.ornithemc.osl.keybinds.api.KeyBindingEvents;
import net.ornithemc.osl.lifecycle.api.client.MinecraftClientEvents;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.atomic.AtomicBoolean;

public final class OrnitheClient implements ClientModInitializer {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    /** Safe before client initialization and from rendering threads. */
    public static boolean barriersVisible() {
        return RenderingPlugin.visible();
    }

    @Override
    public void onInitializeClient() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        RenderingCoordinator rendering = new RenderingCoordinator(client);
        VisibilityController controller = new VisibilityController(new ClientEffects() {
            @Override
            public boolean isWorldAvailable() {
                return client.world != null && client.player != null;
            }

            @Override
            public void showVisibility(boolean visible) {
                rendering.showVisibility(visible);
            }
        });
        KeyBinding key = new KeyBinding(
                "key.toggle_barrier_visibility", Keyboard.KEY_B, "key.category.legacy_visible_barriers");
        KeyBindingEvents.REGISTER_KEYBINDS.register(registry -> registry.register(key));
        MinecraftClientEvents.TICK_END.register(ignored -> {
            rendering.recover();
            while (key.consumeClick()) {
                controller.toggle();
            }
            rendering.finishTick();
        });
    }
}
