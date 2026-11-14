package io.github.metaconscious.lvb.core;

import java.util.Arrays;

/** Stable far-to-near quad ordering with contiguous ranges coalesced per source batch. */
public final class TranslucentOrder {
    private long[] keys = new long[256];
    private int[] batches = new int[256];
    private int[] quads = new int[256];
    private int size;

    public void clear() {
        size = 0;
    }

    /** Coordinates are the quad centre relative to the camera. Distances match float vertex data. */
    public void add(int batch, int quad, float x, float y, float z) {
        if (size == keys.length) {
            int capacity = size * 2;
            keys = Arrays.copyOf(keys, capacity);
            batches = Arrays.copyOf(batches, capacity);
            quads = Arrays.copyOf(quads, capacity);
        }
        float distance = x * x + y * y + z * z;
        long depth = Integer.MAX_VALUE - Float.floatToIntBits(distance);
        keys[size] = (depth << 32) | size;
        batches[size] = batch;
        quads[size] = quad;
        size++;
    }

    public void draw(Ranges ranges) {
        Arrays.sort(keys, 0, size);
        int cursor = 0;
        while (cursor < size) {
            int index = (int) keys[cursor++];
            int batch = batches[index];
            int first = quads[index];
            int count = 1;
            while (cursor < size) {
                int next = (int) keys[cursor];
                if (batches[next] != batch || quads[next] != first + count) {
                    break;
                }
                count++;
                cursor++;
            }
            ranges.draw(batch, first, count);
        }
    }

    @FunctionalInterface
    public interface Ranges {
        void draw(int batch, int firstQuad, int quadCount);
    }
}
