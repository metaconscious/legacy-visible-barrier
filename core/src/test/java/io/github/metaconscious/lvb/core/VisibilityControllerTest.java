package io.github.metaconscious.lvb.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VisibilityControllerTest {
    @Test
    public void togglesStartHiddenAndPublishFeedbackAndRendererChanges() {
        RecordingClient client = new RecordingClient();
        VisibilityController controller = new VisibilityController(client);

        assertFalse(controller.isVisible());
        controller.toggle();
        assertTrue(controller.isVisible());
        controller.toggle();
        assertFalse(controller.isVisible());
        assertEquals(Arrays.asList("visible", "refresh", "hidden", "refresh"), client.events);
    }

    @Test
    public void clicksOutsideAWorldAreDiscardedAndVisibilitySurvivesReconnect() {
        RecordingClient client = new RecordingClient();
        VisibilityController controller = new VisibilityController(client);
        client.inWorld = false;
        controller.toggle();
        assertFalse(controller.isVisible());
        assertTrue(client.events.isEmpty());

        client.inWorld = true;
        controller.toggle();
        client.inWorld = false;
        controller.toggle();
        assertTrue(controller.isVisible());
        assertEquals(Arrays.asList("visible", "refresh"), client.events);

        client.inWorld = true;
        controller.toggle();
        assertFalse(controller.isVisible());
        assertEquals(Arrays.asList("visible", "refresh", "hidden", "refresh"), client.events);
    }

    @Test
    public void anotherThreadCannotChangeStateOrInvokeClientEffects() throws Exception {
        RecordingClient client = new RecordingClient();
        VisibilityController controller = new VisibilityController(client);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> toggle = worker.submit(controller::toggle);
            try {
                toggle.get(5, TimeUnit.SECONDS);
                fail("A render thread must not mutate visibility");
            } catch (ExecutionException expected) {
                assertTrue(expected.getCause() instanceof IllegalStateException);
            }
            assertFalse(controller.isVisible());
            assertTrue(client.events.isEmpty());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    public void renderingThreadCanObserveAnOwnerThreadToggle() throws Exception {
        VisibilityController controller = new VisibilityController(new RecordingClient());
        CountDownLatch reading = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> observed = worker.submit(() -> {
                reading.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!controller.isVisible() && System.nanoTime() < deadline) {
                    Thread.yield();
                }
                return controller.isVisible();
            });
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            controller.toggle();
            assertTrue(observed.get(6, TimeUnit.SECONDS));
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    public void aNewClientSessionStartsHidden() {
        VisibilityController previous = new VisibilityController(new RecordingClient());
        previous.toggle();
        assertTrue(previous.isVisible());
        assertFalse(new VisibilityController(new RecordingClient()).isVisible());
    }

    @Test(expected = NullPointerException.class)
    public void clientEffectsAreRequired() {
        new VisibilityController(null);
    }

    private static final class RecordingClient implements ClientEffects {
        private final List<String> events = new ArrayList<>();
        private boolean inWorld = true;

        @Override
        public boolean isWorldAvailable() { return inWorld; }

        @Override
        public void showVisibility(boolean visible) {
            events.add(visible ? "visible" : "hidden");
        }

        @Override
        public void refreshRenderer() { events.add("refresh"); }
    }
}
