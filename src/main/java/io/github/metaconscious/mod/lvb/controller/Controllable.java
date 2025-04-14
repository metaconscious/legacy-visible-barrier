package io.github.metaconscious.mod.lvb.controller;

public interface Controllable {
    boolean isEnabled();

    void setEnabled(boolean enabled);

    default void enable() {
        setEnabled(true);
    }

    default void disable() {
        setEnabled(false);
    }
}
