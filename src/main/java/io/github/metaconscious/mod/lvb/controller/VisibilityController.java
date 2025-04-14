package io.github.metaconscious.mod.lvb.controller;

import io.github.metaconscious.mod.lvb.render.Visibility;

public class VisibilityController implements Visibility, ToggleControllable {

    private boolean visible;

    public VisibilityController(boolean initialVisibility) {
        this.visible = initialVisibility;
    }

    @Override
    public boolean isVisible() {
        return isEnabled();
    }

    @Override
    public boolean isEnabled() {
        return visible;
    }

    @Override
    public void setEnabled(boolean enabled) {
        visible = enabled;
    }
}
