package io.github.metaconscious.lvb.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import java.util.Collections;
import java.util.Set;

/** Forge's Mods screen opens the same settings screen as the unbound settings control. */
public final class SettingsGuiFactory implements IModGuiFactory {
    @Override public void initialize(Minecraft client) { }
    @Override public Class<? extends GuiScreen> mainConfigGuiClass() { return SettingsScreen.class; }
    @Override public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() { return Collections.emptySet(); }
    @Override public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) { return null; }
}
