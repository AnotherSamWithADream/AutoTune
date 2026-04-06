package com.autotune.report;

/**
 * Formatting utilities for benchmark reports. Provides human-readable formatting
 * for FPS values, millisecond timings, memory sizes, and ASCII progress bars.
 */
public class ReportFormatter {

    private static final String[] MEMORY_UNITS = {"B", "KB", "MB", "GB", "TB"};

    /**
     * Formats a frames-per-second value with one decimal place.
     *
     * @param fps the FPS value
     * @return formatted string like "144.0 FPS"
     */
    public static String formatFps(double fps) {
        return String.format("%.1f FPS", fps);
    }

    /**
     * Formats a millisecond timing value with two decimal places.
     *
     * @param ms the time in milliseconds
     * @return formatted string like "6.94 ms"
     */
    public static String formatMs(double ms) {
        return String.format("%.2f ms", ms);
    }

    /**
     * Formats a byte count into a human-readable memory size using the
     * appropriate unit (B, KB, MB, GB, TB).
     *
     * @param bytes the number of bytes
     * @return formatted string like "2.50 GB"
     */
    public static String formatMemory(long bytes) {
        if (bytes < 0) {
            return "N/A";
        }
        if (bytes == 0) {
            return "0 B";
        }

        double value = bytes;
        int unitIndex = 0;

        while (value >= 1024.0 && unitIndex < MEMORY_UNITS.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }

        if (unitIndex == 0) {
            return String.format("%d %s", bytes, MEMORY_UNITS[unitIndex]);
        }
        return String.format("%.2f %s", value, MEMORY_UNITS[unitIndex]);
    }

    /**
     * Formats a value as an ASCII progress bar with percentage.
     * The bar uses filled blocks and empty blocks to visually represent
     * the ratio of value to max.
     *
     * Example output: [████████░░░░░░░░] 50%
     *
     * @param value the current value
     * @param max   the maximum value (100% mark)
     * @return formatted ASCII bar string
     */
    public static String formatBar(int value, int max) {
        if (max <= 0) {
            return "[░░░░░░░░░░░░░░░░] 0%";
        }

        int barWidth = 16;
        int clamped = Math.clamp(value, 0, max);
        int filledCount = Math.round((float) clamped / max * barWidth);
        int emptyCount = barWidth - filledCount;

        int percent = Math.round((float) clamped / max * 100);

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        sb.repeat('\u2588', filledCount); // Full block
        sb.repeat('\u2591', emptyCount); // Light shade
        sb.append("] ");
        sb.append(percent);
        sb.append('%');

        return sb.toString();
    }

    /**
     * Formats a duration in milliseconds as a human-readable time string.
     * Handles seconds, minutes, and hours.
     *
     * @param durationMs duration in milliseconds
     * @return formatted string like "2m 35s" or "1h 12m 5s"
     */
    public static String formatDuration(long durationMs) {
        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Pads a string to the right with spaces to reach the target length.
     *
     * @param text   the text to pad
     * @param length the target length
     * @return the padded string
     */
    public static String padRight(String text, int length) {
        if (text.length() >= length) {
            return text;
        }
        return text + " ".repeat(length - text.length());
    }

    /**
     * Pads a string to the left with spaces to reach the target length.
     *
     * @param text   the text to pad
     * @param length the target length
     * @return the padded string
     */
    public static String padLeft(String text, int length) {
        if (text.length() >= length) {
            return text;
        }
        return " ".repeat(length - text.length()) + text;
    }

    /**
     * Creates a horizontal rule of the given character and length.
     *
     * @param ch     the character to repeat
     * @param length the number of repetitions
     * @return the rule string
     */
    public static String horizontalRule(char ch, int length) {
        return String.valueOf(ch).repeat(length);
    }
}
