package com.autotune.benchmark;

import java.util.Arrays;

/**
 * Records nanosecond frame times into a circular buffer and computes
 * real-time statistics from the recorded samples.
 *
 * // [CODE-REVIEW-FIX] Thread-safe: all public methods are synchronized for cross-thread
 * // access during benchmarks.
 */
public class FrameTimeSampler {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double NANOS_PER_MS = 1_000_000.0;

    private final long[] samples;
    private int writeIndex;
    private int count;

    public FrameTimeSampler(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        this.samples = new long[capacity];
        this.writeIndex = 0;
        this.count = 0;
    }

    /**
     * Records a single frame time in nanoseconds into the circular buffer.
     */
    // [CODE-REVIEW-FIX] synchronized: FrameTimeSampler is used from both render and tick
    // threads by BenchmarkRunner. Lock contention is acceptable for benchmarks.
    public synchronized void record(long frameTimeNanos) {
        samples[writeIndex] = frameTimeNanos;
        writeIndex = (writeIndex + 1) % samples.length;
        if (count < samples.length) {
            count++;
        }
    }

    /**
     * Returns the average FPS computed from all recorded samples.
     */
    // [CODE-REVIEW-FIX] synchronized: thread safety for cross-thread benchmark access
    public synchronized double getAverageFps() {
        if (count == 0) return 0.0;
        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += samples[i];
        }
        double avgNanos = (double) sum / count;
        if (avgNanos <= 0) return 0.0;
        return NANOS_PER_SECOND / avgNanos;
    }

    /**
     * Returns the 1% low FPS: the average FPS of the slowest 1% of frames.
     * This represents the worst-case sustained performance.
     */
    // [CODE-REVIEW-FIX] synchronized: thread safety for cross-thread benchmark access
    public synchronized double get1PercentLowFps() {
        if (count == 0) return 0.0;
        long[] sorted = getSortedSamples();
        // Slowest 1% are the largest frame times (at the end of sorted array)
        int onePercentCount = Math.max(1, count / 100);
        long sum = 0;
        for (int i = count - onePercentCount; i < count; i++) {
            sum += sorted[i];
        }
        double avgNanos = (double) sum / onePercentCount;
        if (avgNanos <= 0) return 0.0;
        return NANOS_PER_SECOND / avgNanos;
    }

    /**
     * Returns the 99th percentile frame time in milliseconds.
     * 99% of frames are faster than this value.
     */
    // [CODE-REVIEW-FIX] synchronized: thread safety for cross-thread benchmark access
    public synchronized double get99thPercentileFrameTime() {
        if (count == 0) return 0.0;
        long[] sorted = getSortedSamples();
        int index = (int) Math.ceil(count * 0.99) - 1;
        index = Math.clamp(index, 0, count - 1);
        return sorted[index] / NANOS_PER_MS;
    }

    /**
     * Computes the linear regression slope of frame times over the sample window.
     * A positive slope indicates degrading performance (frame times increasing).
     * A negative slope indicates improving performance.
     * Returns the slope in nanoseconds per frame.
     */
    // [CODE-REVIEW-FIX] synchronized: thread safety for cross-thread benchmark access
    public synchronized double getFrameTimeTrendSlope() {
        if (count < 2) return 0.0;

        // Compute linear regression: y = a + b*x
        // where x is the frame index and y is the frame time
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;

        // Read samples in chronological order
        int startIndex;
        if (count < samples.length) {
            startIndex = 0;
        } else {
            startIndex = writeIndex; // oldest sample in circular buffer
        }

        for (int i = 0; i < count; i++) {
            int sampleIndex = (startIndex + i) % samples.length;
            double y = samples[sampleIndex];
            sumX += i;
            sumY += y;
            sumXY += i * y;
            sumXX += (double) i * i;
        }

        double n = count;
        double denominator = n * sumXX - sumX * sumX;
        if (Math.abs(denominator) < 1e-10) return 0.0;

        return (n * sumXY - sumX * sumY) / denominator;
    }

    /**
     * Detects stutter by checking if any recorded frame took more than
     * 3x the average frame time.
     */
    // [CODE-REVIEW-FIX] synchronized: thread safety for cross-thread benchmark access
    public synchronized boolean hasStutter() {
        if (count < 2) return false;

        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += samples[i];
        }
        double average = (double) sum / count;
        double threshold = average * 3.0;

        for (int i = 0; i < count; i++) {
            if (samples[i] > threshold) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resets the sampler, clearing all recorded data.
     */
    // [CODE-REVIEW-FIX] synchronized: thread safety for cross-thread benchmark access
    public synchronized void reset() {
        writeIndex = 0;
        count = 0;
        Arrays.fill(samples, 0L);
    }

    /**
     * Returns the number of samples currently stored.
     */
    // [CODE-REVIEW-FIX] synchronized: thread safety for cross-thread benchmark access
    public synchronized int getCount() {
        return count;
    }

    /**
     * Returns a copy of all valid samples in chronological order.
     */
    // [CODE-REVIEW-FIX] synchronized: thread safety for cross-thread benchmark access
    public synchronized long[] getSamplesSnapshot() {
        long[] snapshot = new long[count];
        if (count < samples.length) {
            System.arraycopy(samples, 0, snapshot, 0, count);
        } else {
            int firstPartLen = samples.length - writeIndex;
            System.arraycopy(samples, writeIndex, snapshot, 0, firstPartLen);
            System.arraycopy(samples, 0, snapshot, firstPartLen, writeIndex);
        }
        return snapshot;
    }

    /**
     * Returns sorted copy of current samples (ascending order).
     */
    private long[] getSortedSamples() {
        long[] copy = new long[count];
        if (count < samples.length) {
            System.arraycopy(samples, 0, copy, 0, count);
        } else {
            int firstPartLen = samples.length - writeIndex;
            System.arraycopy(samples, writeIndex, copy, 0, firstPartLen);
            System.arraycopy(samples, 0, copy, firstPartLen, writeIndex);
        }
        Arrays.sort(copy);
        return copy;
    }
}
