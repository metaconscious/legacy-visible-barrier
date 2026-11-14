package io.github.metaconscious.lvb.legacyfabric;

import io.github.metaconscious.lvb.core.ClientEffects;
import io.github.metaconscious.lvb.core.VisibilityController;
import net.fabricmc.api.ClientModInitializer;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.atomic.AtomicBoolean;

public final class LegacyFabricClient implements ClientModInitializer {
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
        MinecraftClient client = MinecraftClient.getInstance();
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
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.toggle_barrier_visibility", Keyboard.KEY_B, "key.category.legacy_visible_barriers"));
        ClientTickEvents.END_CLIENT_TICK.register(ignored -> {
            rendering.recover();
            while (key.wasPressed()) {
                controller.toggle();
            }
            rendering.finishTick();
        });
    }
}
