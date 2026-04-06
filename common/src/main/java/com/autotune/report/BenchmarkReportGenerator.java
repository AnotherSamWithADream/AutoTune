package com.autotune.report;

import com.autotune.benchmark.BenchmarkResult;
import com.autotune.benchmark.FrameTimeStatistics;
import com.autotune.benchmark.PhaseResult;
import com.autotune.benchmark.hardware.HardwareProfile;
import com.autotune.optimizer.OptimalSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Generates a human-readable benchmark report from the results of a benchmark run,
 * hardware profile, and computed optimal settings. The report includes sections for
 * hardware summary, benchmark results per phase, bottleneck analysis, optimal
 * settings, and explanations.
 */
public class BenchmarkReportGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(BenchmarkReportGenerator.class);
    private static final int REPORT_WIDTH = 72;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Generates the full benchmark report as a single formatted string.
     *
     * @param result   the benchmark results containing per-phase measurements
     * @param hardware the hardware profile of the system that ran the benchmark
     * @param optimal  the computed optimal settings from the optimizer
     * @return the complete report text
     */
    public String generate(BenchmarkResult result, HardwareProfile hardware, OptimalSettings optimal) {
        StringBuilder report = new StringBuilder();

        appendHeader(report, result);
        appendHardwareSummary(report, hardware);
        appendBenchmarkResults(report, result);
        appendBottleneckAnalysis(report, result, hardware);
        appendOptimalSettings(report, optimal);
        appendExplanations(report, optimal);
        appendFooter(report);

        String text = report.toString();
        LOGGER.info("Generated benchmark report ({} chars)", text.length());
        return text;
    }

    private void appendHeader(StringBuilder sb, BenchmarkResult result) {
        sb.append(ReportFormatter.horizontalRule('=', REPORT_WIDTH)).append('\n');
        sb.append(centerText("AutoTune Benchmark Report")).append('\n');
        sb.append(ReportFormatter.horizontalRule('=', REPORT_WIDTH)).append('\n');
        sb.append('\n');

        String timestamp = DATE_FORMAT.format(Instant.ofEpochMilli(result.getBenchmarkTimestamp()));
        sb.append("Date:           ").append(timestamp).append('\n');
        sb.append("Benchmark Mode: ").append(result.getBenchmarkMode()).append('\n');
        sb.append("Duration:       ").append(ReportFormatter.formatDuration(result.getTotalDurationMs())).append('\n');
        sb.append("Phases Run:     ").append(result.getCompletedPhaseCount())
                .append(" / ").append(result.getTotalPhaseResultCount()).append('\n');
        sb.append("Status:         ").append(result.isComplete() ? "Complete" : "Incomplete").append('\n');
        sb.append('\n');
    }

    private void appendHardwareSummary(StringBuilder sb, HardwareProfile hardware) {
        sb.append(sectionHeader("Hardware Summary")).append('\n');

        if (hardware == null) {
            sb.append("  Hardware profile not available.\n\n");
            return;
        }

        sb.append("  GPU\n");
        sb.append("    Name:         ").append(hardware.gpuName()).append('\n');
        sb.append("    Vendor:       ").append(hardware.gpuVendor()).append('\n');
        sb.append("    VRAM:         ").append(ReportFormatter.formatMemory((long) hardware.gpuVramMb() * 1024 * 1024)).append('\n');
        sb.append("    Driver:       ").append(hardware.gpuDriver()).append('\n');
        sb.append("    Architecture: ").append(hardware.gpuArchitecture()).append('\n');
        sb.append("    TFLOPS:       ").append(String.format("%.1f", hardware.gpuTflops())).append('\n');
        sb.append('\n');

        sb.append("  CPU\n");
        sb.append("    Name:         ").append(hardware.cpuName()).append('\n');
        sb.append("    Cores/Threads:").append(hardware.cpuCores()).append(" / ").append(hardware.cpuThreads()).append('\n');
        sb.append("    Clock:        ").append(String.format("%.1f GHz", hardware.cpuBaseClockGhz()));
        if (hardware.cpuBoostClockGhz() > 0) {
            sb.append(" (boost: ").append(String.format("%.1f GHz", hardware.cpuBoostClockGhz())).append(')');
        }
        sb.append('\n');
        sb.append("    L3 Cache:     ").append(hardware.cpuL3CacheMb()).append(" MB\n");
        sb.append('\n');

        sb.append("  Memory\n");
        sb.append("    Total RAM:    ").append(ReportFormatter.formatMemory((long) hardware.totalRamMb() * 1024 * 1024)).append('\n');
        sb.append("    Max Heap:     ").append(ReportFormatter.formatMemory(hardware.maxHeapMb() * 1024 * 1024)).append('\n');
        sb.append('\n');

        sb.append("  Display\n");
        sb.append("    Resolution:   ").append(hardware.displayWidth()).append('x').append(hardware.displayHeight()).append('\n');
        sb.append("    Refresh Rate: ").append(hardware.displayRefreshRate()).append(" Hz\n");
        sb.append('\n');

        sb.append("  System\n");
        sb.append("    OS:           ").append(hardware.osName()).append(" (").append(hardware.osArch()).append(")\n");
        sb.append("    Java:         ").append(hardware.javaVersion()).append(" (").append(hardware.javaVendor()).append(")\n");

        if (hardware.thermalThrottlingDetected()) {
            sb.append('\n');
            sb.append("  !! THERMAL THROTTLING DETECTED !!\n");
            if (hardware.cpuTemperature() > 0) {
                sb.append("    CPU Temp: ").append(String.format("%.0f", hardware.cpuTemperature())).append(" C\n");
            }
            if (hardware.gpuTemperature() > 0) {
                sb.append("    GPU Temp: ").append(String.format("%.0f", hardware.gpuTemperature())).append(" C\n");
            }
        }
        sb.append('\n');
    }

    private void appendBenchmarkResults(StringBuilder sb, BenchmarkResult result) {
        sb.append(sectionHeader("Benchmark Results")).append('\n');

        if (result.getPhaseResults().isEmpty()) {
            sb.append("  No benchmark phases recorded.\n\n");
            return;
        }

        for (Map.Entry<String, PhaseResult> entry : result.getPhaseResults().entrySet()) {
            PhaseResult phase = entry.getValue();

            sb.append("  Phase: ").append(phase.getPhaseName())
                    .append(" (").append(phase.getPhaseId()).append(")\n");

            if (phase.isSkipped()) {
                sb.append("    SKIPPED: ").append(phase.getSkipReason()).append('\n');
                sb.append('\n');
                continue;
            }

            sb.append("    Duration: ").append(ReportFormatter.formatDuration(phase.getDurationMs())).append('\n');

            if (phase.getNotes() != null && !phase.getNotes().isEmpty()) {
                sb.append("    Notes:    ").append(phase.getNotes()).append('\n');
            }

            for (Map.Entry<String, FrameTimeStatistics> measurement : phase.getMeasurements().entrySet()) {
                String label = measurement.getKey();
                FrameTimeStatistics stats = measurement.getValue();

                sb.append("    [").append(label).append("]\n");
                sb.append("      Avg FPS:      ").append(ReportFormatter.formatFps(stats.avgFps())).append('\n');
                sb.append("      Median FPS:   ").append(ReportFormatter.formatFps(stats.medianFps())).append('\n');
                sb.append("      1% Low:       ").append(ReportFormatter.formatFps(stats.p1LowFps())).append('\n');
                sb.append("      0.1% Low:     ").append(ReportFormatter.formatFps(stats.p01LowFps())).append('\n');
                sb.append("      P99 Frame:    ").append(ReportFormatter.formatMs(stats.p99FrameTimeMs())).append('\n');
                sb.append("      Max Frame:    ").append(ReportFormatter.formatMs(stats.maxFrameTimeMs())).append('\n');
                sb.append("      Std Dev:      ").append(ReportFormatter.formatMs(stats.stdDevMs())).append('\n');
                sb.append("      Stutters:     ").append(stats.stutterCount()).append('\n');
                sb.append("      Samples:      ").append(stats.sampleCount()).append('\n');

                // Visual FPS bar (scaled to 240 FPS max for display)
                int barValue = (int) Math.round(stats.avgFps());
                sb.append("      ").append(ReportFormatter.formatBar(barValue, 240)).append('\n');
            }

            sb.append('\n');
        }
    }

    private void appendBottleneckAnalysis(StringBuilder sb, BenchmarkResult result, HardwareProfile hardware) {
        sb.append(sectionHeader("Bottleneck Analysis")).append('\n');

        if (hardware == null) {
            sb.append("  Cannot analyze bottlenecks without hardware profile.\n\n");
            return;
        }

        // Analyze GPU vs CPU bottleneck based on phase performance patterns
        double totalAvgFps = 0;
        double totalP1Low = 0;
        int phaseCount = 0;

        for (PhaseResult phase : result.getPhaseResults().values()) {
            if (phase.isSkipped()) continue;
            for (FrameTimeStatistics stats : phase.getMeasurements().values()) {
                totalAvgFps += stats.avgFps();
                totalP1Low += stats.p1LowFps();
                phaseCount++;
            }
        }

        if (phaseCount == 0) {
            sb.append("  No completed phases to analyze.\n\n");
            return;
        }

        double avgFpsOverall = totalAvgFps / phaseCount;
        double avgP1LowOverall = totalP1Low / phaseCount;
        double fpsStabilityRatio = avgP1LowOverall / Math.max(1.0, avgFpsOverall);

        sb.append("  Overall Average FPS: ").append(ReportFormatter.formatFps(avgFpsOverall)).append('\n');
        sb.append("  Overall 1% Low FPS:  ").append(ReportFormatter.formatFps(avgP1LowOverall)).append('\n');
        sb.append("  Stability Ratio:     ").append(String.format("%.1f%%", fpsStabilityRatio * 100)).append('\n');
        sb.append('\n');

        // VRAM analysis
        long vramBytes = (long) hardware.gpuVramMb() * 1024 * 1024;
        sb.append("  VRAM:   ").append(ReportFormatter.formatBar(hardware.gpuVramMb(), 16384))
                .append(" (").append(ReportFormatter.formatMemory(vramBytes)).append(")\n");

        // RAM analysis
        long ramBytes = (long) hardware.totalRamMb() * 1024 * 1024;
        sb.append("  RAM:    ").append(ReportFormatter.formatBar(hardware.totalRamMb(), 32768))
                .append(" (").append(ReportFormatter.formatMemory(ramBytes)).append(")\n");
        sb.append('\n');

        // Stability assessment
        if (fpsStabilityRatio < 0.3) {
            sb.append("  DIAGNOSIS: Severe frame time instability detected.\n");
            sb.append("  The 1% lows are significantly below the average, indicating\n");
            sb.append("  frequent heavy stuttering. Likely causes: GC pauses, chunk\n");
            sb.append("  loading spikes, or insufficient VRAM causing texture thrashing.\n");
        } else if (fpsStabilityRatio < 0.5) {
            sb.append("  DIAGNOSIS: Moderate frame time instability.\n");
            sb.append("  Noticeable stuttering present. Consider reducing render distance\n");
            sb.append("  or particle settings to smooth out frame timing.\n");
        } else if (fpsStabilityRatio < 0.7) {
            sb.append("  DIAGNOSIS: Acceptable frame time stability.\n");
            sb.append("  Some occasional dips but generally playable. Minor tweaks\n");
            sb.append("  could improve consistency.\n");
        } else {
            sb.append("  DIAGNOSIS: Good frame time stability.\n");
            sb.append("  Frame delivery is consistent with minimal stuttering.\n");
        }

        // GPU tier hint
        if (hardware.gpuTierHint() > 0) {
            sb.append('\n');
            sb.append("  GPU Tier: ").append(hardware.gpuTierHint()).append("/5");
            if (hardware.gpuTierHint() <= 2) {
                sb.append(" (entry-level: prioritize FPS over visuals)");
            } else if (hardware.gpuTierHint() == 3) {
                sb.append(" (mid-range: balanced settings recommended)");
            } else {
                sb.append(" (high-end: can handle most visual effects)");
            }
            sb.append('\n');
        }

        sb.append('\n');
    }

    private void appendOptimalSettings(StringBuilder sb, OptimalSettings optimal) {
        sb.append(sectionHeader("Optimal Settings")).append('\n');

        if (optimal == null || optimal.size() == 0) {
            sb.append("  No optimal settings computed.\n\n");
            return;
        }

        sb.append("  Predicted Average FPS: ").append(ReportFormatter.formatFps(optimal.getPredictedFps())).append('\n');
        sb.append("  Predicted 1% Low FPS:  ").append(ReportFormatter.formatFps(optimal.getPredictedP1LowFps())).append('\n');
        sb.append('\n');

        String headerSetting = ReportFormatter.padRight("  Setting", 32);
        String headerValue = ReportFormatter.padRight("Value", 20);
        sb.append(headerSetting).append(headerValue).append('\n');
        sb.append("  ").append(ReportFormatter.horizontalRule('-', REPORT_WIDTH - 2)).append('\n');

        for (Map.Entry<String, Object> entry : optimal.getValues().entrySet()) {
            String settingId = entry.getKey();
            Object value = entry.getValue();

            String settingCol = ReportFormatter.padRight("  " + settingId, 32);
            String valueCol = ReportFormatter.padRight(String.valueOf(value), 20);
            sb.append(settingCol).append(valueCol).append('\n');
        }

        sb.append('\n');
    }

    private void appendExplanations(StringBuilder sb, OptimalSettings optimal) {
        sb.append(sectionHeader("Setting Explanations")).append('\n');

        if (optimal == null || optimal.getExplanations().isEmpty()) {
            sb.append("  No explanations available.\n\n");
            return;
        }

        for (Map.Entry<String, String> entry : optimal.getExplanations().entrySet()) {
            String settingId = entry.getKey();
            String explanation = entry.getValue();

            sb.append("  ").append(settingId).append(":\n");
            sb.append("    ").append(explanation).append('\n');
            sb.append('\n');
        }
    }

    private void appendFooter(StringBuilder sb) {
        sb.append(ReportFormatter.horizontalRule('=', REPORT_WIDTH)).append('\n');
        sb.append(centerText("Generated by AutoTune")).append('\n');
        sb.append(ReportFormatter.horizontalRule('=', REPORT_WIDTH)).append('\n');
    }

    private String sectionHeader(String title) {
        return ReportFormatter.horizontalRule('-', REPORT_WIDTH) + '\n' +
                "  " + title + '\n' +
                ReportFormatter.horizontalRule('-', REPORT_WIDTH);
    }

    private String centerText(String text) {
        int padding = Math.max(0, (REPORT_WIDTH - text.length()) / 2);
        return " ".repeat(padding) + text;
    }
}
