package io.github.metaconscious.lvb.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TranslucentOrderTest {
    @Test
    public void interleavesBatchesAndSubmitsEveryQuadOnceInContiguousRanges() {
        TranslucentOrder order = new TranslucentOrder();
        order.add(0, 0, 0, 0, 8);
        order.add(0, 1, 0, 0, 7);
        order.add(0, 2, 0, 0, 2);
        order.add(1, 0, 0, 0, 5);
        order.add(1, 1, 0, 0, 1);
        List<String> draws = new ArrayList<>();
        order.draw((batch, first, count) -> draws.add(batch + ":" + first + ":" + count));
        assertEquals(Arrays.asList("0:0:2", "1:0:1", "0:2:1", "1:1:1"), draws);
    }

    @Test
    public void keepsEqualDepthsStableAndDiscardsPreviousFrames() {
        TranslucentOrder order = new TranslucentOrder();
        for (int i = 0; i < 300; i++) {
            order.add(i, 0, 1, 2, 3);
        }
        List<Integer> batches = new ArrayList<>();
        order.draw((batch, first, count) -> batches.add(batch));
        for (int i = 0; i < 300; i++) {
            assertEquals(Integer.valueOf(i), batches.get(i));
        }
        order.clear();
        batches.clear();
        order.draw((batch, first, count) -> batches.add(batch));
        assertEquals(0, batches.size());
        order.add(4, 2, 0, 0, 0);
        order.draw((batch, first, count) -> batches.add(batch));
        assertEquals(Arrays.asList(4), batches);
    }
}
