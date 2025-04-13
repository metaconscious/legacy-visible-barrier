package io.github.metaconscious.mod.lvb;

import io.github.prospector.modmenu.api.ConfigScreenFactory;
import io.github.prospector.modmenu.api.ModMenuApi;
import io.github.prospector.modmenu.gui.AbstractScreen;
import net.minecraft.client.gui.screen.Screen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LegacyVisibleBarrierModMenu implements ModMenuApi {

    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModMenuConfigScreen::new;
    }

    private static final class ModMenuConfigScreen extends AbstractScreen {
        public ModMenuConfigScreen(Screen previous) {
            super(previous);
        }
    }
}
