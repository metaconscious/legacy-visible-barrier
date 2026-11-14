package io.github.metaconscious.lvb.core;

/** Read-only visibility available to rendering threads. */
public interface VisibilityView {
    boolean isVisible();
}
