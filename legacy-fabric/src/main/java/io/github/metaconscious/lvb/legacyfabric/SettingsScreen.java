package io.github.metaconscious.lvb.legacyfabric;

import io.github.metaconscious.lvb.core.RenderingPolicy;
import io.github.metaconscious.lvb.core.RenderingPreferences;
import io.github.metaconscious.lvb.core.SettingsDraft;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.options.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** A client-owned edit session. Opening, closing and saving never mutate rendering policy. */
public final class SettingsScreen extends Screen {
    private final Screen parent;
    private SettingsFile.Snapshot snapshot;
    private SettingsDraft draft;
    private CompletableFuture<SettingsFile.Snapshot> saving;
    private boolean loaded;
    private boolean advanced;
    private boolean details;
    private String message;
    private int focused;
    private int scroll;
    private int maxScroll;
    private int left;
    private int contentWidth;

    public SettingsScreen(Screen parent) { this.parent = parent; }

    @Override
    public void init() {
        if (!loaded) load();
        contentWidth = Math.max(80, Math.min(420, width - 32));
        left = (width - contentWidth) / 2;
        buttons.clear();
        for (int id : new int[]{0, 1, 2, 3, 8, 7, 4, 5, 6}) {
            buttons.add(new ButtonWidget(id, left, 0, contentWidth, 20, ""));
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
    public void tick() {
        if (saving != null && saving.isDone()) {
            try {
                snapshot = saving.join();
                saving = null;
                client.setScreen(parent);
                return;
            } catch (CompletionException failure) {
                saving = null;
                message = failure.getCause() instanceof SettingsFile.ChangedException
                        ? "lvb.settings.changed" : "lvb.settings.save_failed";
                RenderingPlugin.LOG.warn("Cannot save LVB settings", failure.getCause());
            }
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active || saving != null) return;
        focused = button.id;
        switch (button.id) {
            case 0: draft.cycleMode(); break;
            case 1: advanced = !advanced; break;
            case 2: draft.cycleRefresh(); break;
            case 3: client.setScreen(new ControlsOptionsScreen(this, client.options)); return;
            case 4: draft.reset(); break;
            case 5: client.setScreen(parent); return;
            case 6:
                SettingsFile.Snapshot baseline = snapshot;
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
    protected void keyPressed(char character, int key) {
        if (saving != null) return;
        if (key == Keyboard.KEY_ESCAPE) {
            client.setScreen(parent);
        } else if (key == Keyboard.KEY_TAB || key == Keyboard.KEY_DOWN || key == Keyboard.KEY_UP) {
            List<ButtonWidget> choices = choices();
            int current = -1;
            for (int i = 0; i < choices.size(); i++) if (choices.get(i).id == focused) current = i;
            int direction = key == Keyboard.KEY_UP || key == Keyboard.KEY_TAB && hasShiftDown() ? -1 : 1;
            if (!choices.isEmpty()) {
                int next = current < 0 ? (direction > 0 ? 0 : choices.size() - 1)
                        : Math.floorMod(current + direction, choices.size());
                focused = choices.get(next).id;
            }
            revealFocus();
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_SPACE) {
            for (ButtonWidget button : choices()) if (button.id == focused) { buttonClicked(button); break; }
        } else if (key == Keyboard.KEY_NEXT || key == Keyboard.KEY_PRIOR) {
            scroll = Math.max(0, Math.min(maxScroll, scroll + (key == Keyboard.KEY_NEXT ? 1 : -1) * Math.max(20, height - 90)));
            layout(false);
        }
    }

    private List<ButtonWidget> choices() {
        List<ButtonWidget> result = new ArrayList<>();
        for (ButtonWidget button : buttons) {
            if (button.active && (button.id != 2 || advanced) && (button.id != 7 || message != null)) result.add(button);
        }
        return result;
    }

    private void revealFocus() {
        for (ButtonWidget button : buttons) {
            if (button.id == focused && button.id != 4 && button.id != 5 && button.id != 6) {
                int bottom = button.y + 20;
                if (button.id == 0) {
                    bottom = paragraph(tr("lvb.settings.restart"), bottom, false);
                    if (RenderingPlugin.modeOverridden()) bottom = paragraph(tr("lvb.settings.mode_override"), bottom, false);
                } else if (button.id == 2) {
                    bottom = paragraph(tr("lvb.settings.refresh_help"), bottom, false);
                    if (RenderingPlugin.refreshOverridden()) bottom = paragraph(tr("lvb.settings.refresh_override"), bottom, false);
                }
                if (button.y < 32) scroll -= 32 - button.y;
                else if (bottom > height - 38) scroll += bottom - (height - 38);
                scroll = Math.max(0, Math.min(maxScroll, scroll));
                layout(false);
                break;
            }
        }
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - Integer.signum(wheel) * 24));
            layout(false);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        renderBackground();
        textRenderer.drawWithShadow(tr("lvb.settings.title"), left, 12, 0xffffff);
        layout(true);
        super.render(mouseX, mouseY, tickDelta);
        for (ButtonWidget button : buttons) {
            if (button.id == focused && button.visible && button.active) {
                fill(button.x - 2, button.y - 2, button.x + button.getWidth() + 2, button.y - 1, 0xffffffff);
                fill(button.x - 2, button.y + 21, button.x + button.getWidth() + 2, button.y + 22, 0xffffffff);
                fill(button.x - 2, button.y - 1, button.x - 1, button.y + 21, 0xffffffff);
                fill(button.x + button.getWidth() + 1, button.y - 1, button.x + button.getWidth() + 2, button.y + 21, 0xffffffff);
            }
        }
        if (maxScroll > 0) {
            int track = Math.max(1, height - 76);
            int thumb = Math.max(8, track * track / (track + maxScroll));
            int y = 34 + (track - thumb) * scroll / maxScroll;
            fill(left + contentWidth + 7, y, left + contentWidth + 9, y + thumb, 0xffdddddd);
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
            net.fabricmc.loader.api.metadata.ModMetadata metadata = FabricLoader.getInstance().getModContainer("lvb").get().getMetadata();
            y = paragraph(metadata.getVersion().getFriendlyString(), y, draw);
            y = paragraph(metadata.getCustomValue("lvb:target").getAsObject().get("name").getAsString(), y, draw);
        }
        if (message != null) y = button(7, y, tr("lvb.settings.reload"), true);
        else hide(7);
        maxScroll = Math.max(0, y + scroll - (height - 38));
        scroll = Math.min(scroll, maxScroll);
        int footerWidth = (contentWidth - 8) / 3;
        for (ButtonWidget button : buttons) {
            if (button.id >= 4 && button.id <= 6) {
                button.x = left + (button.id - 4) * (footerWidth + 4);
                button.y = height - 28;
                button.setWidth(footerWidth);
                button.visible = true;
                button.active = saving == null && (button.id == 5 || draft != null
                        && (button.id == 4 || draft.dirty() || !snapshot.valid));
                button.message = tr(button.id == 4 ? "lvb.settings.reset" : button.id == 5 ? "gui.cancel" : "lvb.settings.save");
            }
        }
    }

    private int paragraph(String text, int y, boolean draw) {
        for (String line : textRenderer.wrapLines(text, contentWidth)) {
            if (draw && y >= 32 && y + textRenderer.fontHeight <= height - 38) {
                textRenderer.drawWithShadow(line, left, y, 0xffffff);
            }
            y += textRenderer.fontHeight + 2;
        }
        return y + 3;
    }

    private int button(int id, int y, String text, boolean active) {
        for (ButtonWidget button : buttons) if (button.id == id) {
            button.x = left;
            button.y = y;
            button.message = text;
            button.active = active && saving == null;
            button.visible = y >= 32 && y + 20 <= height - 38;
        }
        return y + 24;
    }

    private void hide(int id) { for (ButtonWidget button : buttons) if (button.id == id) button.visible = false; }

    private String bindingName() {
        for (KeyBinding key : client.options.allKeys) {
            if ("key.toggle_barrier_visibility".equals(key.getTranslationKey())) return GameOptions.getFormattedNameForKeyCode(key.getCode());
        }
        return "B";
    }

    private static String tr(String key, Object... arguments) { return I18n.translate(key, arguments); }
}
