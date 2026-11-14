package io.github.metaconscious.lvb.core;

/** Client operations performed synchronously on the thread that owns the controller. */
public interface ClientEffects {
    /** Whether a world and a player are available for a toggle. */
    boolean isWorldAvailable();

    void showVisibility(boolean visible);

    void refreshRenderer();
}
