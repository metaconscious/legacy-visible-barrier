package io.github.metaconscious.lvb.forge;

import io.github.metaconscious.lvb.client.SettingsFile;

import io.github.metaconscious.lvb.core.RenderingPolicy;
import io.github.metaconscious.lvb.core.RenderingPreferences;
import io.github.metaconscious.lvb.core.SettingsDraft;
import net.minecraftforge.fml.common.Loader;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** A client-owned edit session. Opening, closing and saving never mutate rendering policy. */
public final class SettingsScreen extends GuiScreen {
    private final GuiScreen parent;
    private io.github.metaconscious.lvb.client.SettingsFile.Snapshot snapshot;
    private SettingsDraft draft;
    private CompletableFuture<io.github.metaconscious.lvb.client.SettingsFile.Snapshot> saving;
    private boolean loaded;
    private boolean advanced;
    private boolean details;
    private String message;
    private int focused;
    private int scroll;
    private int maxScroll;
    private int left;
    private int contentWidth;

    public SettingsScreen(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        if (!loaded) load();
        contentWidth = Math.max(80, Math.min(420, width - 32));
        left = (width - contentWidth) / 2;
        buttonList.clear();
        for (int id : new int[]{0, 1, 2, 3, 8, 7, 4, 5, 6}) {
            buttonList.add(new GuiButton(id, left, 0, contentWidth, 20, ""));
        }
        layout(false);
    }

    private void load() {
        loaded = true;
        try {
            snapshot = RenderingPlugin.settingsFile().read();
            draft = new SettingsDraft(snapshot.preferences);
            message = null;
        } catch (IOException failure) {
            snapshot = null;
            draft = null;
            message = "lvb.settings.read_failed";
            RenderingPlugin.LOG.warn("Cannot open LVB settings", failure);
        }
    }

    @Override
    public void updateScreen() {
        if (saving != null && saving.isDone()) {
            try {
                snapshot = saving.join();
                saving = null;
                mc.displayGuiScreen(parent);
                return;
            } catch (CompletionException failure) {
                saving = null;
                message = failure.getCause() instanceof io.github.metaconscious.lvb.client.SettingsFile.ChangedException
                        ? "lvb.settings.changed" : "lvb.settings.save_failed";
                RenderingPlugin.LOG.warn("Cannot save LVB settings", failure.getCause());
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled || saving != null) return;
        focused = button.id;
        switch (button.id) {
            case 0: draft.cycleMode(); break;
            case 1: advanced = !advanced; break;
            case 2: draft.cycleRefresh(); break;
            case 3: mc.displayGuiScreen(new GuiControls(this, mc.gameSettings)); return;
            case 4: draft.reset(); break;
            case 5: mc.displayGuiScreen(parent); return;
            case 6:
                io.github.metaconscious.lvb.client.SettingsFile.Snapshot baseline = snapshot;
                RenderingPreferences value = draft.value();
                saving = CompletableFuture.supplyAsync(() -> {
                    try { return RenderingPlugin.settingsFile().save(baseline, value); }
                    catch (IOException failure) { throw new CompletionException(failure); }
                });
                break;
            case 7: load(); break;
            case 8: details = !details; break;
            default: break;
        }
        layout(false);
        revealFocus();
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (saving != null) return;
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else if (key == Keyboard.KEY_TAB || key == Keyboard.KEY_DOWN || key == Keyboard.KEY_UP) {
            List<GuiButton> choices = choices();
            int current = -1;
            for (int i = 0; i < choices.size(); i++) if (choices.get(i).id == focused) current = i;
            int direction = key == Keyboard.KEY_UP || key == Keyboard.KEY_TAB && isShiftKeyDown() ? -1 : 1;
            if (!choices.isEmpty()) {
                int next = current < 0 ? (direction > 0 ? 0 : choices.size() - 1)
                        : Math.floorMod(current + direction, choices.size());
                focused = choices.get(next).id;
            }
            revealFocus();
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_SPACE) {
            for (GuiButton button : choices()) if (button.id == focused) { actionPerformed(button); break; }
        } else if (key == Keyboard.KEY_NEXT || key == Keyboard.KEY_PRIOR) {
            scroll = Math.max(0, Math.min(maxScroll, scroll + (key == Keyboard.KEY_NEXT ? 1 : -1) * Math.max(20, height - 90)));
            layout(false);
        }
    }

    private List<GuiButton> choices() {
        List<GuiButton> result = new ArrayList<>();
        for (GuiButton button : buttonList) {
            if (button.enabled && (button.id != 2 || advanced) && (button.id != 7 || message != null)) result.add(button);
        }
        return result;
    }

    private void revealFocus() {
        for (GuiButton button : buttonList) {
            if (button.id == focused && button.id != 4 && button.id != 5 && button.id != 6) {
                int bottom = button.yPosition + 20;
                if (button.id == 0) {
                    bottom = paragraph(tr("lvb.settings.restart"), bottom, false);
                    if (RenderingPlugin.modeOverridden()) bottom = paragraph(tr("lvb.settings.mode_override"), bottom, false);
                } else if (button.id == 2) {
                    bottom = paragraph(tr("lvb.settings.refresh_help"), bottom, false);
                    if (RenderingPlugin.refreshOverridden()) bottom = paragraph(tr("lvb.settings.refresh_override"), bottom, false);
                }
                if (button.yPosition < 32) scroll -= 32 - button.yPosition;
                else if (bottom > height - 38) scroll += bottom - (height - 38);
                scroll = Math.max(0, Math.min(maxScroll, scroll));
                layout(false);
                break;
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - Integer.signum(wheel) * 24));
            layout(false);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float tickDelta) {
        drawDefaultBackground();
        fontRendererObj.drawStringWithShadow(tr("lvb.settings.title"), left, 12, 0xffffff);
        layout(true);
        super.drawScreen(mouseX, mouseY, tickDelta);
        for (GuiButton button : buttonList) {
            if (button.id == focused && button.visible && button.enabled) {
                drawRect(button.xPosition - 2, button.yPosition - 2, button.xPosition + button.getButtonWidth() + 2, button.yPosition - 1, 0xffffffff);
                drawRect(button.xPosition - 2, button.yPosition + 21, button.xPosition + button.getButtonWidth() + 2, button.yPosition + 22, 0xffffffff);
                drawRect(button.xPosition - 2, button.yPosition - 1, button.xPosition - 1, button.yPosition + 21, 0xffffffff);
                drawRect(button.xPosition + button.getButtonWidth() + 1, button.yPosition - 1, button.xPosition + button.getButtonWidth() + 2, button.yPosition + 21, 0xffffffff);
            }
        }
        if (maxScroll > 0) {
            int track = Math.max(1, height - 76);
            int thumb = Math.max(8, track * track / (track + maxScroll));
            int y = 34 + (track - thumb) * scroll / maxScroll;
            drawRect(left + contentWidth + 7, y, left + contentWidth + 9, y + thumb, 0xffdddddd);
        }
    }

    private void layout(boolean draw) {
        int y = 34 - scroll;
        RenderingPolicy.State state = RenderingPlugin.state();
        String backend = state == null ? "starting" : state.mode.name().toLowerCase(Locale.ROOT);
        y = paragraph(tr("lvb.settings.active", tr("lvb.settings.backend." + backend)), y, draw);
        if (state != null) {
            y = paragraph(tr("lvb.settings.visibility", tr(state.requestedVisible ? "options.on" : "options.off")), y, draw);
            if (state.failure != null) y = paragraph(tr(state.failure == RenderingPolicy.Failure.MODEL
                    ? "lvb.settings.model_failed" : "lvb.settings.recovered"), y, draw);
        }
        if (RenderingPlugin.configurationFailed()) y = paragraph(tr("lvb.settings.startup_failed"), y, draw);
        if (snapshot != null && !snapshot.valid) y = paragraph(tr("lvb.settings.invalid"), y, draw);
        if (saving != null) y = paragraph(tr("lvb.settings.saving"), y, draw);
        if (message != null) y = paragraph(tr(message), y, draw);
        if (snapshot != null && snapshot.valid && RenderingPlugin.restartRequired(snapshot)) {
            y = paragraph(tr("lvb.settings.pending"), y, draw);
        }
        y = paragraph(tr("lvb.settings.mode"), y + 4, draw);
        String mode = draft == null ? "auto" : draft.value().renderMode;
        y = button(0, y, tr("lvb.settings.mode." + ("enhanced".equals(mode) ? "auto" : mode)), draft != null);
        y = paragraph(tr("lvb.settings.restart"), y, draw);
        if (RenderingPlugin.modeOverridden()) y = paragraph(tr("lvb.settings.mode_override"), y, draw);
        y = button(1, y + 4, tr("lvb.settings.advanced") + (advanced ? " -" : " +"), true);
        if (advanced) {
            y = paragraph(tr("lvb.settings.refresh"), y, draw);
            y = button(2, y, tr("lvb.settings.refresh." + (draft == null ? "dirty" : draft.value().nativeRefresh)), draft != null);
            y = paragraph(tr("lvb.settings.refresh_help"), y, draw);
            if (RenderingPlugin.refreshOverridden()) y = paragraph(tr("lvb.settings.refresh_override"), y, draw);
        } else hide(2);
        y = paragraph(tr("lvb.settings.binding", bindingName()), y + 4, draw);
        y = button(3, y, tr("options.controls"), true);
        y = button(8, y, tr("lvb.settings.details") + (details ? " -" : " +"), true);
        if (details) {
            net.minecraftforge.fml.common.ModContainer metadata = Loader.instance().getIndexedModList().get("lvb");
            y = paragraph(metadata.getVersion(), y, draw);
            y = paragraph("Forge 1.8.9", y, draw);
        }
        if (message != null) y = button(7, y, tr("lvb.settings.reload"), true);
        else hide(7);
        maxScroll = Math.max(0, y + scroll - (height - 38));
        scroll = Math.min(scroll, maxScroll);
        int footerWidth = (contentWidth - 8) / 3;
        for (GuiButton button : buttonList) {
            if (button.id >= 4 && button.id <= 6) {
                button.xPosition = left + (button.id - 4) * (footerWidth + 4);
                button.yPosition = height - 28;
                button.setWidth(footerWidth);
                button.visible = true;
                button.enabled = saving == null && (button.id == 5 || draft != null
                        && (button.id == 4 || draft.dirty() || !snapshot.valid));
                button.displayString = tr(button.id == 4 ? "lvb.settings.reset" : button.id == 5 ? "gui.cancel" : "lvb.settings.save");
            }
        }
    }

    private int paragraph(String text, int y, boolean draw) {
        for (String line : fontRendererObj.listFormattedStringToWidth(text, contentWidth)) {
            if (draw && y >= 32 && y + fontRendererObj.FONT_HEIGHT <= height - 38) {
                fontRendererObj.drawStringWithShadow(line, left, y, 0xffffff);
            }
            y += fontRendererObj.FONT_HEIGHT + 2;
        }
        return y + 3;
    }

    private int button(int id, int y, String text, boolean active) {
        for (GuiButton button : buttonList) if (button.id == id) {
            button.xPosition = left;
            button.yPosition = y;
            button.displayString = text;
            button.enabled = active && saving == null;
            button.visible = y >= 32 && y + 20 <= height - 38;
        }
        return y + 24;
    }

    private void hide(int id) { for (GuiButton button : buttonList) if (button.id == id) button.visible = false; }

    private String bindingName() {
        for (KeyBinding key : mc.gameSettings.keyBindings) {
            if ("key.toggle_barrier_visibility".equals(key.getKeyDescription())) return GameSettings.getKeyDisplayString(key.getKeyCode());
        }
        return "B";
    }

    private static String tr(String key, Object... arguments) { return I18n.format(key, arguments); }
}
