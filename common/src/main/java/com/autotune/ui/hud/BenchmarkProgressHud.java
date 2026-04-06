package com.autotune.ui.hud;

import com.autotune.benchmark.BenchmarkRunner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Renders a benchmark progress overlay when BenchmarkRunner is active.
 * Displays the current phase name, a progress bar, estimated time remaining,
 * and the current sub-test label. Positioned at the top center of the screen.
 */
public class BenchmarkProgressHud {

    private static final int BG_COLOR = 0xCC0F0F23;
    private static final int BORDER_COLOR = 0xFF3498DB;
    private static final int BAR_BG_COLOR = 0xFF2C2C2C;
    private static final int BAR_FILL_LOW = 0xFF3498DB;
    private static final int BAR_FILL_HIGH = 0xFF2ECC71;
    private static final int TITLE_COLOR = 0xFF3498DB;
    private static final int PHASE_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int DIM_TEXT_COLOR = 0xFF888888;

    private static final int HUD_WIDTH = 300;
    private static final int HUD_HEIGHT = 64;
    private static final int BAR_HEIGHT = 10;
    private static final int PADDING = 6;

    private final BenchmarkRunner runner;

    // Additional progress state that can be fed by the executor callback
    private volatile String currentSubTest = "";
    private volatile long benchmarkStartTime;

    public BenchmarkProgressHud(BenchmarkRunner runner) {
        this.runner = runner;
        this.benchmarkStartTime = System.currentTimeMillis();
    }

    /**
     * Updates the current sub-test label from the phase executor callback.
     */
    public void setCurrentSubTest(String subTest) {
        this.currentSubTest = subTest != null ? subTest : "";
    }

    /**
     * Sets the benchmark start time for ETA calculation.
     */
    public void setBenchmarkStartTime(long startTime) {
        this.benchmarkStartTime = startTime;
    }

    /**
     * Renders the benchmark progress overlay.
     */
    public void render(DrawContext context) {
        if (!runner.isRunning()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = client.getWindow().getScaledWidth();

        int hudX = (screenWidth - HUD_WIDTH) / 2;
        int hudY = 8;

        // Background
        context.fill(hudX, hudY, hudX + HUD_WIDTH, hudY + HUD_HEIGHT, BG_COLOR);

        // Border with accent color
        drawBorder(context, hudX, hudY, HUD_WIDTH, HUD_HEIGHT, BORDER_COLOR);

        // Title line: "AutoTune Benchmark" with cancel hint
        context.drawText(textRenderer, Text.literal("AutoTune Benchmark"),
                hudX + PADDING, hudY + PADDING, TITLE_COLOR, true);
        String cancelHint = "ESC to cancel";
        int cancelWidth = textRenderer.getWidth(cancelHint);
        context.drawText(textRenderer, Text.literal(cancelHint),
                hudX + HUD_WIDTH - PADDING - cancelWidth, hudY + PADDING, DIM_TEXT_COLOR, false);

        // Phase name line — truncate to fit HUD width
        String phaseName = runner.getCurrentPhaseName();
        if (phaseName == null) phaseName = "Initializing...";

        String phaseCountStr = String.format("Phase %d/%d: ", runner.getCurrentPhaseIndex() + 1, runner.getTotalPhases());
        String fullPhaseStr = phaseCountStr + phaseName;
        int maxPhaseW = HUD_WIDTH - PADDING * 2;
        fullPhaseStr = fitText(textRenderer, fullPhaseStr, maxPhaseW);
        context.drawText(textRenderer, Text.literal(fullPhaseStr),
                hudX + PADDING, hudY + PADDING + 12, PHASE_COLOR, false);

        // Sub-test label — truncate to fit HUD width
        if (!currentSubTest.isEmpty()) {
            String subTestLine = "Sub-test: " + currentSubTest;
            subTestLine = fitText(textRenderer, subTestLine, maxPhaseW);
            context.drawText(textRenderer, Text.literal(subTestLine),
                    hudX + PADDING, hudY + PADDING + 22, DIM_TEXT_COLOR, false);
        }

        // Progress bar
        float progress = runner.getProgressPercent() / 100f;
        progress = Math.clamp(progress, 0f, 1f);

        int barX = hudX + PADDING;
        int barY = hudY + HUD_HEIGHT - PADDING - BAR_HEIGHT - 12;
        int barW = HUD_WIDTH - PADDING * 2;

        context.fill(barX, barY, barX + barW, barY + BAR_HEIGHT, BAR_BG_COLOR);

        int fillW = (int) (barW * progress);
        if (fillW > 0) {
            int barColor = progress < 0.5f ? BAR_FILL_LOW : BAR_FILL_HIGH;
            context.fill(barX, barY, barX + fillW, barY + BAR_HEIGHT, barColor);
        }

        // Percentage centered on bar
        String pctText = String.format("%.0f%%", progress * 100);
        int pctWidth = textRenderer.getWidth(pctText);
        context.drawText(textRenderer, Text.literal(pctText),
                barX + (barW - pctWidth) / 2, barY + 1, 0xFFFFFFFF, true);

        // Bottom line: ETA
        String etaText = calculateEta(progress);
        context.drawText(textRenderer, Text.literal(etaText),
                hudX + PADDING, barY + BAR_HEIGHT + 2, TEXT_COLOR, false);

        // Progress percentage on right side of bottom line
        String statusText = String.format("%d of %d phases", runner.getCurrentPhaseIndex(), runner.getTotalPhases());
        int statusWidth = textRenderer.getWidth(statusText);
        context.drawText(textRenderer, Text.literal(statusText),
                hudX + HUD_WIDTH - PADDING - statusWidth, barY + BAR_HEIGHT + 2, DIM_TEXT_COLOR, false);
    }

    private String calculateEta(float progress) {
        if (progress <= 0.01f) return "ETA: Calculating...";

        long elapsed = System.currentTimeMillis() - benchmarkStartTime;
        long estimatedTotal = (long) (elapsed / progress);
        long remaining = estimatedTotal - elapsed;

        if (remaining <= 0) return "ETA: Almost done";

        long seconds = remaining / 1000;
        if (seconds < 60) return "ETA: " + seconds + "s remaining";
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) return "ETA: " + minutes + "m " + seconds + "s remaining";
        long hours = minutes / 60;
        minutes = minutes % 60;
        return "ETA: " + hours + "h " + minutes + "m remaining";
    }

    private void drawBorder(DrawContext context, int bx, int by, int bw, int bh, int color) {
        context.fill(bx, by, bx + bw, by + 1, color);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        context.fill(bx, by, bx + 1, by + bh, color);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }

    private static String fitText(TextRenderer tr, String text, int maxW) {
        if (text == null) return "";
        if (tr.getWidth(text) <= maxW) return text;
        for (int i = text.length() - 1; i > 0; i--) {
            if (tr.getWidth(text.substring(0, i) + "...") <= maxW) return text.substring(0, i) + "...";
        }
        return "...";
    }
}
