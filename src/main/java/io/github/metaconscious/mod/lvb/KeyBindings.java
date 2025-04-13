package io.github.metaconscious.mod.lvb;

import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.TranslatableText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

public final class KeyBindings {

    private static final Logger LOGGER = LogManager.getLogger();

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
        LOGGER.info("Key binding registered.");
        ClientTickEvents.END_CLIENT_TICK.register(this::onBarrierVisibilityToggled);
        LOGGER.info("Keypress event listener registered.");
    }

    public Visibility getVisibilityViewer() {
        return controller;
    }

    private void onBarrierVisibilityToggled(MinecraftClient minecraftClient) {
        while (barrierVisibilityTogglingKey.wasPressed()) {
            boolean visibleNow = controller.toggle();
            if (visibleNow) {
                minecraftClient.player.sendMessage(TO_VISIBLE);
            } else {
                minecraftClient.player.sendMessage(TO_INVISIBLE);
            }
            minecraftClient.worldRenderer.reload();
            LOGGER.debug("The visibility of barriers is '{}' now.", visibleNow ? "visible" : "invisible");
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
