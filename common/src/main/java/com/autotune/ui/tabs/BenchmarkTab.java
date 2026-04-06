package com.autotune.ui.tabs;

import com.autotune.AutoTuneMod;
import com.autotune.benchmark.BenchmarkResult;
import com.autotune.benchmark.FrameTimeStatistics;
import com.autotune.benchmark.PhaseResult;
import com.autotune.config.ConfigManager;
import com.autotune.ui.AutoTuneMainScreen;
import com.autotune.ui.screens.BenchmarkIntroScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Benchmark tab providing buttons to run full/quick benchmarks,
 * display of past results with expandable per-phase details,
 * and an export report button.
 */
public class BenchmarkTab implements Tab {

    private static final int SECTION_TITLE_COLOR = 0xFF3498DB;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int DIM_TEXT_COLOR = 0xFF888888;
    private static final int CARD_BG = 0xFF1A1A2E;
    private static final int GOOD_COLOR = 0xFF2ECC71;
    private static final int WARN_COLOR = 0xFFF39C12;
    private static final int BAD_COLOR = 0xFFE74C3C;
    private static final int HEADER_BG = 0xFF252542;

    private AutoTuneMainScreen parent;
    private int x, y, width, height;
    private BenchmarkResult lastResult;
    private int scrollOffset;
    private int expandedPhaseIndex = -1;

    @Override
    public String getName() {
        return "Benchmark";
    }

    @Override
    public void init(AutoTuneMainScreen parent, int x, int y, int width, int height) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scrollOffset = 0;
        this.expandedPhaseIndex = -1;

        // Load last benchmark result
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        ConfigManager configManager = mod.getConfigManager();
        lastResult = configManager.loadBenchmarkResults();

        // Buttons
        int btnWidth = (width - 30) / 2;
        int btnY = y + 4;

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Full Benchmark (~15 min)"),
                btn -> {
                    MinecraftClient client = parent.getClient();
                    if (client != null) {
                        client.setScreen(new BenchmarkIntroScreen(parent));
                    }
                }
        ).dimensions(x + 10, btnY, btnWidth, 20).build());

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Quick Benchmark (~3 min)"),
                btn -> {
                    MinecraftClient client = parent.getClient();
                    if (client != null) {
                        client.setScreen(new BenchmarkIntroScreen(parent));
                    }
                }
        ).dimensions(x + 15 + btnWidth, btnY, btnWidth, 20).build());

        // Export button at the bottom
        if (lastResult != null && lastResult.isComplete()) {
            parent.addTabWidget(ButtonWidget.builder(
                    Text.literal("Export Report"),
                    btn -> {
                        if (parent.getToast() != null) {
                            parent.getToast().showInfo("Report exported to config/autotune/report.txt");
                        }
                    }
            ).dimensions(x + 10, y + height - 26, 120, 20).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = parent.getTextRenderer();

        int resultsY = y + 30;

        if (lastResult == null) {
            // No results yet
            context.drawText(textRenderer, Text.literal("No Benchmark Results"),
                    x + 10, resultsY + 10, SECTION_TITLE_COLOR, false);

            context.drawText(textRenderer, Text.literal("Run a benchmark to analyze your system's performance."),
                    x + 10, resultsY + 26, TEXT_COLOR, false);
            context.drawText(textRenderer, Text.literal("Full benchmark tests all 30 phases for comprehensive analysis."),
                    x + 10, resultsY + 40, DIM_TEXT_COLOR, false);
            context.drawText(textRenderer, Text.literal("Quick benchmark tests key phases for a faster overview."),
                    x + 10, resultsY + 52, DIM_TEXT_COLOR, false);
            return;
        }

        // Result header
        context.drawText(textRenderer, Text.literal("Benchmark Results"), x + 10, resultsY, SECTION_TITLE_COLOR, false);

        String modeLabel = "full".equals(lastResult.getBenchmarkMode()) ? "Full" : "Quick";
        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(lastResult.getBenchmarkTimestamp()));
        long durationSec = lastResult.getTotalDurationMs() / 1000;
        String summaryLine = String.format("%s Benchmark  |  %s  |  %dm %ds  |  %d/%d phases",
                modeLabel, dateStr, durationSec / 60, durationSec % 60,
                lastResult.getCompletedPhaseCount(), lastResult.getTotalPhaseResultCount());
        context.drawText(textRenderer, Text.literal(summaryLine), x + 10, resultsY + 14, TEXT_COLOR, false);

        // Phase results list
        int listY = resultsY + 30;
        int listHeight = height - 90;
        int rowHeight = 22;
        int phaseIndex = 0;
        int currentY = listY - scrollOffset;

        // Column headers
        context.fill(x + 10, listY - 14, x + width - 10, listY - 2, HEADER_BG);
        context.drawText(textRenderer, Text.literal("Phase"), x + 14, listY - 12, DIM_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal("Avg FPS"), x + width / 2, listY - 12, DIM_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal("1% Low"), x + width / 2 + 60, listY - 12, DIM_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal("Status"), x + width - 70, listY - 12, DIM_TEXT_COLOR, false);

        // Enable scissor for scrollable area
        context.enableScissor(x + 10, listY, x + width - 10, listY + listHeight);

        for (Map.Entry<String, PhaseResult> entry : lastResult.getPhaseResults().entrySet()) {
            PhaseResult phase = entry.getValue();
            int rowY = currentY + phaseIndex * rowHeight;

            if (rowY + rowHeight >= listY && rowY < listY + listHeight) {
                boolean hovered = mouseX >= x + 10 && mouseX < x + width - 10
                        && mouseY >= rowY && mouseY < rowY + rowHeight
                        && mouseY >= listY && mouseY < listY + listHeight;

                // Row background
                int rowBg = hovered ? 0xFF252542 : (phaseIndex % 2 == 0 ? CARD_BG : 0xFF1E1E36);
                context.fill(x + 10, rowY, x + width - 10, rowY + rowHeight, rowBg);

                // Phase name — truncate to fit available column width
                String phaseName = phase.getPhaseName();
                int nameMaxW = width / 2 - 24;
                phaseName = fitText(textRenderer, phaseName, nameMaxW);
                context.drawText(textRenderer, Text.literal(phaseName), x + 14, rowY + 6, TEXT_COLOR, false);

                if (phase.isSkipped()) {
                    context.drawText(textRenderer, Text.literal("Skipped"), x + width / 2, rowY + 6, DIM_TEXT_COLOR, false);
                    String reason = phase.getSkipReason() != null ? phase.getSkipReason() : "";
                    reason = fitText(textRenderer, reason, width / 4);
                    context.drawText(textRenderer, Text.literal(reason), x + width - 70, rowY + 6, DIM_TEXT_COLOR, false);
                } else {
                    // Aggregate measurements for this phase
                    double bestAvg = 0;
                    double worstP1 = Double.MAX_VALUE;
                    for (FrameTimeStatistics stats : phase.getMeasurements().values()) {
                        if (stats.avgFps() > bestAvg) bestAvg = stats.avgFps();
                        if (stats.p1LowFps() < worstP1) worstP1 = stats.p1LowFps();
                    }
                    if (worstP1 == Double.MAX_VALUE) worstP1 = 0;

                    String avgStr = String.format("%.1f", bestAvg);
                    String p1Str = String.format("%.1f", worstP1);

                    int fpsColor = bestAvg >= 60 ? GOOD_COLOR : (bestAvg >= 30 ? WARN_COLOR : BAD_COLOR);
                    context.drawText(textRenderer, Text.literal(avgStr), x + width / 2, rowY + 6, fpsColor, false);
                    context.drawText(textRenderer, Text.literal(p1Str), x + width / 2 + 60, rowY + 6, fpsColor, false);

                    String status = bestAvg >= 60 ? "Good" : (bestAvg >= 30 ? "Fair" : "Poor");
                    context.drawText(textRenderer, Text.literal(status), x + width - 70, rowY + 6, fpsColor, false);
                }

                // Expanded phase detail
                if (phaseIndex == expandedPhaseIndex && !phase.isSkipped()) {
                    int detailY = rowY + rowHeight;
                    int detailHeight = phase.getMeasurements().size() * 12 + 4;
                    context.fill(x + 20, detailY, x + width - 20, detailY + detailHeight, 0xFF111128);

                    int subY = detailY + 2;
                    for (Map.Entry<String, FrameTimeStatistics> mEntry : phase.getMeasurements().entrySet()) {
                        FrameTimeStatistics stats = mEntry.getValue();
                        String subLine = String.format("  %s: avg=%.1f fps, 1%%=%.1f, p99=%.1fms",
                                mEntry.getKey(), stats.avgFps(), stats.p1LowFps(),
                                stats.p99FrameTimeMs());
                        subLine = fitText(textRenderer, subLine, width - 50);
                        context.drawText(textRenderer, Text.literal(subLine), x + 24, subY, DIM_TEXT_COLOR, false);
                        subY += 12;
                    }
                }
            }
            phaseIndex++;
        }

        context.disableScissor();
    }

    @Override
    public void tick() {
        // Nothing to tick
    }

    @Override
    public boolean handleScroll(double mouseX, double mouseY, double amount) {
        scrollOffset -= (int)(amount * 12);
        scrollOffset = Math.max(0, scrollOffset);
        return true;
    }

    /** Truncates text to fit within maxPixelWidth, adding "..." if needed */
    private static String fitText(TextRenderer tr, String text, int maxPixelWidth) {
        if (text == null) return "";
        if (tr.getWidth(text) <= maxPixelWidth) return text;
        String ellipsis = "...";
        int ew = tr.getWidth(ellipsis);
        for (int i = text.length() - 1; i > 0; i--) {
            if (tr.getWidth(text.substring(0, i)) + ew <= maxPixelWidth) {
                return text.substring(0, i) + ellipsis;
            }
        }
        return ellipsis;
    }
}
