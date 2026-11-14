package io.github.metaconscious.lvb.core;

import java.util.Objects;

/**
 * Process-lifetime visibility state and its toggle use case.
 * Construct and toggle on the client thread; rendering threads may read at any time.
 */
public final class VisibilityController implements VisibilityView {
    private final ClientEffects client;
    private final Thread owner = Thread.currentThread();
    private volatile boolean visible;

    public VisibilityController(ClientEffects client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    public void toggle() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Visibility must be changed on the owning client thread");
        }
        if (!client.isWorldAvailable()) {
            return;
        }
        visible = !visible;
        client.showVisibility(visible);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }
}
