package com.autotune.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReportFormatter")
class ReportFormatterTest {

    // -----------------------------------------------------------------------
    // FPS formatting
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("formatFps")
    class FormatFps {

        @Test
        @DisplayName("60.0 FPS should format as '60.0 FPS'")
        void testFormatFps60() {
            assertEquals("60.0 FPS", ReportFormatter.formatFps(60.0));
        }

        @Test
        @DisplayName("144.5 FPS should format as '144.5 FPS'")
        void testFormatFps144_5() {
            assertEquals("144.5 FPS", ReportFormatter.formatFps(144.5));
        }

        @Test
        @DisplayName("0.0 FPS should format as '0.0 FPS'")
        void testFormatFpsZero() {
            assertEquals("0.0 FPS", ReportFormatter.formatFps(0.0));
        }

        @Test
        @DisplayName("59.99 FPS should round to one decimal as '60.0 FPS'")
        void testFormatFpsRounding() {
            assertEquals("60.0 FPS", ReportFormatter.formatFps(59.99));
        }

        @Test
        @DisplayName("999.9 FPS should format with one decimal")
        void testFormatFpsHighValue() {
            assertEquals("999.9 FPS", ReportFormatter.formatFps(999.9));
        }
    }

    // -----------------------------------------------------------------------
    // Millisecond formatting
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("formatMs")
    class FormatMs {

        @Test
        @DisplayName("16.6666 should format as '16.67 ms'")
        void testFormatMs() {
            assertEquals("16.67 ms", ReportFormatter.formatMs(16.6666));
        }

        @Test
        @DisplayName("0.0 should format as '0.00 ms'")
        void testFormatMsZero() {
            assertEquals("0.00 ms", ReportFormatter.formatMs(0.0));
        }

        @Test
        @DisplayName("6.944 should format as '6.94 ms'")
        void testFormatMsTwoDecimals() {
            assertEquals("6.94 ms", ReportFormatter.formatMs(6.944));
        }

        @Test
        @DisplayName("100.0 should format as '100.00 ms'")
        void testFormatMsWholeNumber() {
            assertEquals("100.00 ms", ReportFormatter.formatMs(100.0));
        }
    }

    // -----------------------------------------------------------------------
    // Memory formatting
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("formatMemory")
    class FormatMemory {

        @Test
        @DisplayName("512 bytes should format as '512 B'")
        void testFormatMemoryBytes() {
            assertEquals("512 B", ReportFormatter.formatMemory(512));
        }

        @Test
        @DisplayName("1024 bytes should format as '1.00 KB'")
        void testFormatMemoryKb() {
            assertEquals("1.00 KB", ReportFormatter.formatMemory(1024));
        }

        @Test
        @DisplayName("1048576 bytes (1 MB) should format as '1.00 MB'")
        void testFormatMemoryMb() {
            assertEquals("1.00 MB", ReportFormatter.formatMemory(1048576));
        }

        @Test
        @DisplayName("1073741824 bytes (1 GB) should format as '1.00 GB'")
        void testFormatMemoryGb() {
            assertEquals("1.00 GB", ReportFormatter.formatMemory(1073741824));
        }

        @Test
        @DisplayName("0 bytes should format as '0 B'")
        void testFormatMemoryZero() {
            assertEquals("0 B", ReportFormatter.formatMemory(0));
        }

        @Test
        @DisplayName("Negative bytes should format as 'N/A'")
        void testFormatMemoryNegative() {
            assertEquals("N/A", ReportFormatter.formatMemory(-1));
        }

        @Test
        @DisplayName("2.5 GB should format correctly")
        void testFormatMemory2_5Gb() {
            long bytes = (long) (2.5 * 1024 * 1024 * 1024);
            assertEquals("2.50 GB", ReportFormatter.formatMemory(bytes));
        }

        @Test
        @DisplayName("1 TB should format as '1.00 TB'")
        void testFormatMemoryTb() {
            long bytes = 1024L * 1024L * 1024L * 1024L;
            assertEquals("1.00 TB", ReportFormatter.formatMemory(bytes));
        }

        @Test
        @DisplayName("Small byte value (1) should format as '1 B' with no decimals")
        void testFormatMemorySingleByte() {
            assertEquals("1 B", ReportFormatter.formatMemory(1));
        }
    }

    // -----------------------------------------------------------------------
    // ASCII bar formatting
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("formatBar")
    class FormatBar {

        @Test
        @DisplayName("value=50, max=100 should produce a bar at ~50%")
        void testFormatBar50Percent() {
            String bar = ReportFormatter.formatBar(50, 100);
            assertTrue(bar.contains("50%"), "Bar should show 50%, got: " + bar);
            assertTrue(bar.startsWith("["), "Bar should start with '['");
            assertTrue(bar.contains("]"), "Bar should contain ']'");

            // Count filled blocks - at 50% of 16-width bar, expect 8 filled
            long filledCount = bar.chars().filter(c -> c == '\u2588').count();
            assertEquals(8, filledCount, "50% of 16-char bar should have 8 filled blocks");
        }

        @Test
        @DisplayName("value=0, max=100 should produce an empty bar at 0%")
        void testFormatBarZero() {
            String bar = ReportFormatter.formatBar(0, 100);
            assertTrue(bar.contains("0%"), "Bar should show 0%, got: " + bar);

            long filledCount = bar.chars().filter(c -> c == '\u2588').count();
            assertEquals(0, filledCount, "0% bar should have 0 filled blocks");

            long emptyCount = bar.chars().filter(c -> c == '\u2591').count();
            assertEquals(16, emptyCount, "0% bar should have 16 empty blocks");
        }

        @Test
        @DisplayName("value=100, max=100 should produce a full bar at 100%")
        void testFormatBarFull() {
            String bar = ReportFormatter.formatBar(100, 100);
            assertTrue(bar.contains("100%"), "Bar should show 100%, got: " + bar);

            long filledCount = bar.chars().filter(c -> c == '\u2588').count();
            assertEquals(16, filledCount, "100% bar should have 16 filled blocks");

            long emptyCount = bar.chars().filter(c -> c == '\u2591').count();
            assertEquals(0, emptyCount, "100% bar should have 0 empty blocks");
        }

        @Test
        @DisplayName("value exceeding max should be clamped to 100%")
        void testFormatBarExceedsMax() {
            String bar = ReportFormatter.formatBar(150, 100);
            assertTrue(bar.contains("100%"), "Value > max should be clamped to 100%");
        }

        @Test
        @DisplayName("max=0 should produce empty bar with 0%")
        void testFormatBarMaxZero() {
            String bar = ReportFormatter.formatBar(50, 0);
            assertTrue(bar.contains("0%"), "max=0 should produce 0%, got: " + bar);
        }

        @Test
        @DisplayName("Negative value should be clamped to 0")
        void testFormatBarNegativeValue() {
            String bar = ReportFormatter.formatBar(-10, 100);
            assertTrue(bar.contains("0%"), "Negative value should clamp to 0%, got: " + bar);
        }
    }

    // -----------------------------------------------------------------------
    // Duration formatting
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("formatDuration")
    class FormatDuration {

        @Test
        @DisplayName("5000ms should format as '5s'")
        void testFormatDurationSeconds() {
            assertEquals("5s", ReportFormatter.formatDuration(5000));
        }

        @Test
        @DisplayName("155000ms should format as '2m 35s'")
        void testFormatDurationMinutesSeconds() {
            assertEquals("2m 35s", ReportFormatter.formatDuration(155000));
        }

        @Test
        @DisplayName("4325000ms should format as '1h 12m 5s'")
        void testFormatDurationHours() {
            assertEquals("1h 12m 5s", ReportFormatter.formatDuration(4325000));
        }

        @Test
        @DisplayName("0ms should format as '0s'")
        void testFormatDurationZero() {
            assertEquals("0s", ReportFormatter.formatDuration(0));
        }
    }

    // -----------------------------------------------------------------------
    // Padding utilities
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Padding utilities")
    class Padding {

        @Test
        @DisplayName("padRight should pad with spaces to target length")
        void testPadRight() {
            assertEquals("hi   ", ReportFormatter.padRight("hi", 5));
        }

        @Test
        @DisplayName("padRight returns original when already at target length")
        void testPadRightAlreadyAtLength() {
            assertEquals("hello", ReportFormatter.padRight("hello", 5));
        }

        @Test
        @DisplayName("padRight returns original when longer than target")
        void testPadRightLongerThanTarget() {
            assertEquals("hello world", ReportFormatter.padRight("hello world", 5));
        }

        @Test
        @DisplayName("padLeft should pad with spaces on the left")
        void testPadLeft() {
            assertEquals("   hi", ReportFormatter.padLeft("hi", 5));
        }

        @Test
        @DisplayName("padLeft returns original when at or over target length")
        void testPadLeftAlreadyAtLength() {
            assertEquals("hello", ReportFormatter.padLeft("hello", 5));
        }
    }

    // -----------------------------------------------------------------------
    // Horizontal rule
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("horizontalRule")
    class HorizontalRule {

        @Test
        @DisplayName("horizontalRule with '-' and length 5 should produce '-----'")
        void testHorizontalRule() {
            assertEquals("-----", ReportFormatter.horizontalRule('-', 5));
        }

        @Test
        @DisplayName("horizontalRule with length 0 should produce empty string")
        void testHorizontalRuleZeroLength() {
            assertEquals("", ReportFormatter.horizontalRule('=', 0));
        }
    }
}
