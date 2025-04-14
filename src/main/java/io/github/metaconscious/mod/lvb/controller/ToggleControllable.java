package io.github.metaconscious.mod.lvb.controller;

public interface ToggleControllable extends Controllable {
    default boolean toggle() {
        setEnabled(!isEnabled());
        return isEnabled();
    }
}
