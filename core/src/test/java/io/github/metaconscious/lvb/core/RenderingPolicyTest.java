package io.github.metaconscious.lvb.core;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicReference;
import static io.github.metaconscious.lvb.core.RenderingPolicy.*;
import static org.junit.Assert.*;

public class RenderingPolicyTest {
    @Test
    public void modesPreserveIntentAndRefreshOnlyChangedNativeGeometry() {
        for (Mode mode : Mode.values()) {
            RenderingPolicy policy = new RenderingPolicy(mode);
            assertFalse(policy.state().visible());
            policy.requestVisibility(true);
            assertTrue(policy.decision().requestedVisible);
            assertFalse(policy.state().visible());
            assertEquals(mode == Mode.NATIVE, policy.takeNativeRefresh());
            assertEquals(mode == Mode.ENHANCED || mode == Mode.NATIVE, policy.state().visible());
            assertFalse(policy.takeNativeRefresh());
            State published = policy.state();
            policy.requestVisibility(false);
            assertSame(published, policy.state());
            policy.requestVisibility(true);
            assertSame(published, policy.state());
            assertFalse(policy.takeNativeRefresh());
            policy.requestVisibility(false);
            assertEquals(mode == Mode.NATIVE, policy.takeNativeRefresh());
        }
    }

    @Test
    public void recoveryLatchesAndSharedFailureRemovesNativeGeometry() {
        RenderingPolicy policy = new RenderingPolicy(Mode.ENHANCED);
        policy.requestVisibility(true);
        assertFalse(policy.takeNativeRefresh());
        State enhanced = policy.state();
        assertTrue(policy.fail(Failure.ENHANCED));
        assertEquals(Mode.ENHANCED, enhanced.mode);
        assertSame(enhanced, policy.state());
        assertTrue(policy.decision().nativeVisible());
        assertTrue(policy.takeNativeRefresh());
        assertTrue(policy.state().nativeVisible());
        assertEquals(Failure.ENHANCED, policy.state().failure);
        assertFalse(policy.fail(Failure.ENHANCED));
        assertFalse(policy.takeNativeRefresh());
        assertTrue(policy.fail(Failure.MODEL));
        assertEquals(Mode.UNAVAILABLE, policy.decision().mode);
        assertTrue(policy.takeNativeRefresh());
        assertFalse(policy.state().visible());
        assertTrue(policy.state().requestedVisible);
        assertEquals(Failure.MODEL, policy.state().failure);
        assertFalse(policy.fail(Failure.ENHANCED));
        assertFalse(policy.fail(Failure.MODEL));
    }

    @Test
    public void workersReadSnapshotsButCannotMakeDecisions() throws InterruptedException {
        RenderingPolicy policy = new RenderingPolicy(Mode.NATIVE);
        policy.requestVisibility(true);
        policy.takeNativeRefresh();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                assertTrue(policy.state().nativeVisible());
                for (Runnable mutation : new Runnable[]{() -> policy.requestVisibility(false),
                        () -> policy.fail(Failure.MODEL), policy::takeNativeRefresh, policy::decision}) {
                    try { mutation.run(); fail("Worker mutation accepted"); }
                    catch (IllegalStateException expected) { /* Owner contract. */ }
                }
            } catch (Throwable problem) { failure.set(problem); }
        });
        worker.start();
        worker.join(5000);
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        assertTrue(policy.state().nativeVisible());
    }
}
