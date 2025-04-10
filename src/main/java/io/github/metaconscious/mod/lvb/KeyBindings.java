package io.github.metaconscious.mod.lvb;

import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.TranslatableText;
import org.lwjgl.input.Keyboard;

public final class KeyBindings {

    private static final TranslatableText TO_VISIBLE = new TranslatableText("lvb.to_visible");
    private static final TranslatableText TO_INVISIBLE = new TranslatableText("lvb.to_invisible");

    private final KeyBinding barrierVisibilityTogglingKey = new KeyBinding(
            "key.toggle_barrier_visibility",
            Keyboard.KEY_B,
            "key.category.legacy_visible_barriers"
    );

    private final VisibilityController controller = new VisibilityController();

    public void init() {
        KeyBindingHelper.registerKeyBinding(barrierVisibilityTogglingKey);
        ClientTickEvents.END_CLIENT_TICK.register(this::onBarrierVisibilityToggled);
    }

    public Visibility getVisibilityViewer() {
        return controller;
    }

    private void onBarrierVisibilityToggled(MinecraftClient minecraftClient) {
        while (barrierVisibilityTogglingKey.wasPressed()) {
            if (controller.toggle()) {
                minecraftClient.player.sendMessage(TO_VISIBLE);
            } else {
                minecraftClient.player.sendMessage(TO_INVISIBLE);
            }
            minecraftClient.worldRenderer.reload();
        }
    }

    public interface Visibility {
        boolean isVisible();
    }

    private static final class VisibilityController implements Visibility {

        private boolean visible = false;

        public boolean toggle() {
            this.visible = !this.visible;
            return this.visible;
        }

        @Override
        public boolean isVisible() {
            return visible;
        }
    }
}
