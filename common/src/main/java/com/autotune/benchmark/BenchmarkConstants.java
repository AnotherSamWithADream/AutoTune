package com.autotune.benchmark;

/**
 * Static constants used throughout the benchmark system.
 */
public final class BenchmarkConstants {

    private BenchmarkConstants() {}

    // Frame count presets for different measurement durations
    public static final int FRAMES_SHORT = 300;
    public static final int FRAMES_MEDIUM = 600;
    public static final int FRAMES_LONG = 1000;
    public static final int FRAMES_STRESS = 3000;
    public static final int FRAMES_EXTREME = 5000;

    // Number of warmup frames to discard before recording measurements
    public static final int WARMUP_FRAMES = 60;

    // Number of settle frames between sub-tests to let the engine stabilize
    public static final int SETTLE_FRAMES = 30;
}
