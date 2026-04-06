package com.autotune.live;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Circular buffer of nanosecond frame times for real-time performance monitoring.
 * Thread-safe for single-writer (render thread) / single-reader (evaluation thread) pattern.
 */
public class RollingFrameBuffer {

    // [CODE-REVIEW-FIX] Use AtomicLongArray instead of plain long[] to ensure
    // memory visibility of array element writes across threads (render -> tick).
    // volatile on writeIndex/count alone does NOT fence the array stores.
    private final AtomicLongArray buffer;
    private final int capacity;
    private final AtomicInteger writeIndex = new AtomicInteger(0);
    private final AtomicInteger count = new AtomicInteger(0);

    public RollingFrameBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new AtomicLongArray(capacity); // [CODE-REVIEW-FIX]
    }

    public void record(long frameTimeNanos) {
        int idx = writeIndex.get();
        buffer.set(idx, frameTimeNanos); // [CODE-REVIEW-FIX] AtomicLongArray.set()
        writeIndex.set((idx + 1) % capacity);
        int c = count.get();
        if (c < capacity) count.set(c + 1);
    }

    public double getAverageFps() {
        int n = count.get();
        if (n == 0) return 0;
        long sum = 0;
        int idx = (writeIndex.get() - n + capacity) % capacity;
        for (int i = 0; i < n; i++) {
            sum += buffer.get((idx + i) % capacity); // [CODE-REVIEW-FIX]
        }
        double avgNanos = (double) sum / n;
        return avgNanos > 0 ? 1_000_000_000.0 / avgNanos : 0;
    }

    public double get1PercentLowFps() {
        int n = count.get();
        if (n == 0) return 0;
        long[] sorted = getSortedFrameTimes(n);
        // 1% low = average of the worst 1% of frame times
        int worstCount = Math.max(1, n / 100);
        long sum = 0;
        for (int i = n - worstCount; i < n; i++) {
            sum += sorted[i];
        }
        double avgWorstNanos = (double) sum / worstCount;
        return avgWorstNanos > 0 ? 1_000_000_000.0 / avgWorstNanos : 0;
    }

    public double get99thPercentileFrameTimeMs() {
        int n = count.get();
        if (n == 0) return 0;
        long[] sorted = getSortedFrameTimes(n);
        int idx99 = (int) (n * 0.99);
        return sorted[Math.min(idx99, n - 1)] / 1_000_000.0;
    }

    /**
     * Linear regression slope of frame times.
     * Positive = getting worse, Negative = improving, ~0 = stable.
     * Returns nanoseconds per sample.
     */
    public double getFrameTimeTrendSlope() {
        int n = count.get();
        if (n < 10) return 0;

        int start = (writeIndex.get() - n + capacity) % capacity;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double y = buffer.get((start + i) % capacity); // [CODE-REVIEW-FIX]
            sumX += i;
            sumY += y;
            sumXY += i * y;
            sumX2 += (double) i * i;
        }
        double denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 1e-10) return 0;
        return (n * sumXY - sumX * sumY) / denom;
    }

    public boolean hasStutter() {
        int n = count.get();
        if (n < 10) return false;
        double avg = getAverageFrameTimeNanos();
        double threshold = avg * 3.0;
        int start = (writeIndex.get() - Math.min(n, 60) + capacity) % capacity;
        int check = Math.min(n, 60);
        for (int i = 0; i < check; i++) {
            if (buffer.get((start + i) % capacity) > threshold) return true; // [CODE-REVIEW-FIX]
        }
        return false;
    }

    public double getAverageFrameTimeNanos() {
        int n = count.get();
        if (n == 0) return 0;
        long sum = 0;
        int start = (writeIndex.get() - n + capacity) % capacity;
        for (int i = 0; i < n; i++) {
            sum += buffer.get((start + i) % capacity); // [CODE-REVIEW-FIX]
        }
        return (double) sum / n;
    }

    public double getAverageFrameTimeMs() {
        return getAverageFrameTimeNanos() / 1_000_000.0;
    }

    public int getCount() {
        return count.get();
    }

    public boolean isFull() {
        return count.get() >= capacity;
    }

    public void reset() {
        writeIndex.set(0);
        count.set(0);
    }

    private long[] getSortedFrameTimes(int n) {
        long[] copy = new long[n];
        int start = (writeIndex.get() - n + capacity) % capacity;
        for (int i = 0; i < n; i++) {
            copy[i] = buffer.get((start + i) % capacity); // [CODE-REVIEW-FIX]
        }
        Arrays.sort(copy);
        return copy;
    }
}
