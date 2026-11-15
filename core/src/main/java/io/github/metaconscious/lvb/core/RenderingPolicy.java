package io.github.metaconscious.lvb.core;

import java.util.Objects;

/** Client-owned rendering decisions, published atomically to rendering workers. */
public final class RenderingPolicy {
    public enum Mode { ENHANCED, NATIVE, OFF, UNAVAILABLE }
    public enum Failure { ENHANCED, MODEL }

    public static final class State {
        public final Mode mode;
        public final boolean requestedVisible;
        public final Failure failure;

        private State(Mode mode, boolean requestedVisible, Failure failure) {
            this.mode = mode;
            this.requestedVisible = requestedVisible;
            this.failure = failure;
        }

        public boolean available() { return mode == Mode.ENHANCED || mode == Mode.NATIVE; }
        public boolean visible() { return available() && requestedVisible; }
        public boolean nativeVisible() { return mode == Mode.NATIVE && requestedVisible; }
    }

    private final Thread owner = Thread.currentThread();
    private volatile State state;
    private State decision;

    public RenderingPolicy(Mode initial) {
        state = new State(Objects.requireNonNull(initial, "initial"), false, null);
        decision = state;
    }

    public State state() { return state; }

    /** Pending client decisions are not exposed to rendering workers before the tick ends. */
    public State decision() {
        requireOwner();
        return decision;
    }

    public void requestVisibility(boolean visible) {
        requireOwner();
        decision = new State(decision.mode, visible, decision.failure);
    }

    /** Recovery is monotonic for the session; a failed enhanced backend is never retried. */
    public boolean fail(Failure failure) {
        requireOwner();
        Objects.requireNonNull(failure, "failure");
        Mode next = decision.mode;
        if (decision.available()) {
            if (failure == Failure.MODEL) next = Mode.UNAVAILABLE;
            else if (next == Mode.ENHANCED) next = Mode.NATIVE;
        }
        if (next == decision.mode) return false;
        decision = new State(next, decision.requestedVisible, failure);
        return true;
    }

    /** Publish once after all input and recovery decisions; return whether native meshes changed. */
    public boolean takeNativeRefresh() {
        requireOwner();
        State previous = state;
        state = decision;
        return state.nativeVisible() != previous.nativeVisible();
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Rendering decisions require the owning client thread");
        }
    }
}
