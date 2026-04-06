package com.autotune.benchmark;

import java.util.Arrays;

/**
 * Immutable snapshot of computed statistics from a set of frame time samples.
 */
public record FrameTimeStatistics(
    double avgFps,
    double medianFps,
    double p1LowFps,
    double p01LowFps,
    double p99FrameTimeMs,
    double maxFrameTimeMs,
    double minFrameTimeMs,
    double stdDevMs,
    int sampleCount,
    double totalDurationMs,
    int stutterCount,
    double trendSlope
) {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double NANOS_PER_MS = 1_000_000.0;

    /**
     * Computes a full FrameTimeStatistics snapshot from raw nanosecond frame time data.
     *
     * @param frameTimesNanos array of frame times in nanoseconds
     * @return computed statistics
     */
    public static FrameTimeStatistics from(long[] frameTimesNanos) {
        if (frameTimesNanos == null || frameTimesNanos.length == 0) {
            return new FrameTimeStatistics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        int n = frameTimesNanos.length;
        long[] sorted = frameTimesNanos.clone();
        Arrays.sort(sorted);

        // Basic aggregates
        long sum = 0;
        long maxNanos = Long.MIN_VALUE;
        long minNanos = Long.MAX_VALUE;
        for (long ft : frameTimesNanos) {
            sum += ft;
            if (ft > maxNanos) maxNanos = ft;
            if (ft < minNanos) minNanos = ft;
        }

        double avgNanos = (double) sum / n;
        double avgFps = avgNanos > 0 ? NANOS_PER_SECOND / avgNanos : 0;

        // Median FPS
        double medianNanos;
        if (n % 2 == 0) {
            medianNanos = (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        } else {
            medianNanos = sorted[n / 2];
        }
        double medianFps = medianNanos > 0 ? NANOS_PER_SECOND / medianNanos : 0;

        // 1% low FPS (average of slowest 1%)
        int onePercentCount = Math.max(1, n / 100);
        long p1Sum = 0;
        for (int i = n - onePercentCount; i < n; i++) {
            p1Sum += sorted[i];
        }
        double p1AvgNanos = (double) p1Sum / onePercentCount;
        double p1LowFps = p1AvgNanos > 0 ? NANOS_PER_SECOND / p1AvgNanos : 0;

        // 0.1% low FPS (average of slowest 0.1%)
        int pointOnePercentCount = Math.max(1, n / 1000);
        long p01Sum = 0;
        for (int i = n - pointOnePercentCount; i < n; i++) {
            p01Sum += sorted[i];
        }
        double p01AvgNanos = (double) p01Sum / pointOnePercentCount;
        double p01LowFps = p01AvgNanos > 0 ? NANOS_PER_SECOND / p01AvgNanos : 0;

        // 99th percentile frame time
        int p99Index = Math.min((int) Math.ceil(n * 0.99) - 1, n - 1);
        p99Index = Math.max(0, p99Index);
        double p99FrameTimeMs = sorted[p99Index] / NANOS_PER_MS;

        // Max and min frame times in ms
        double maxFrameTimeMs = maxNanos / NANOS_PER_MS;
        double minFrameTimeMs = minNanos / NANOS_PER_MS;

        // Standard deviation in ms
        double sumSquaredDiff = 0;
        for (long ft : frameTimesNanos) {
            double diff = (ft / NANOS_PER_MS) - (avgNanos / NANOS_PER_MS);
            sumSquaredDiff += diff * diff;
        }
        double stdDevMs = Math.sqrt(sumSquaredDiff / n);

        // Total duration in ms
        double totalDurationMs = sum / NANOS_PER_MS;

        // Stutter count: frames > 3x average
        double stutterThreshold = avgNanos * 3.0;
        int stutterCount = 0;
        for (long ft : frameTimesNanos) {
            if (ft > stutterThreshold) {
                stutterCount++;
            }
        }

        // Trend slope via linear regression (nanos per frame)
        double trendSlope = computeTrendSlope(frameTimesNanos);

        return new FrameTimeStatistics(
            avgFps, medianFps, p1LowFps, p01LowFps,
            p99FrameTimeMs, maxFrameTimeMs, minFrameTimeMs, stdDevMs,
            n, totalDurationMs, stutterCount, trendSlope
        );
    }

    private static double computeTrendSlope(long[] frameTimesNanos) {
        int n = frameTimesNanos.length;
        if (n < 2) return 0.0;

        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;

        for (int i = 0; i < n; i++) {
            double y = frameTimesNanos[i];
            sumX += i;
            sumY += y;
            sumXY += i * y;
            sumXX += (double) i * i;
        }

        double denom = (double) n * sumXX - sumX * sumX;
        if (Math.abs(denom) < 1e-10) return 0.0;

        return ((double) n * sumXY - sumX * sumY) / denom;
    }
}
