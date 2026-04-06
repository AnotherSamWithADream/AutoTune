package com.autotune.ui.tabs;

import com.autotune.AutoTuneMod;
import com.autotune.benchmark.hardware.HardwareProfile;
import com.autotune.config.AutoTuneConfig;
import com.autotune.live.LiveAdaptiveEngine;
import com.autotune.ui.AutoTuneMainScreen;
import com.autotune.ui.screens.BenchmarkIntroScreen;
import com.autotune.ui.widgets.ScoreBarWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard tab showing hardware summary cards, performance score bars,
 * bottleneck indicator, current profile info, and quick action buttons.
 */
public class DashboardTab implements Tab {

    private static final int CARD_BG = 0xFF1A1A2E;
    private static final int CARD_BORDER = 0xFF333355;
    private static final int CARD_TITLE_COLOR = 0xFF3498DB;
    private static final int CARD_VALUE_COLOR = 0xFFFFFFFF;
    private static final int CARD_LABEL_COLOR = 0xFF999999;
    private static final int SECTION_TITLE_COLOR = 0xFF3498DB;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int STATUS_ACTIVE_COLOR = 0xFF2ECC71;
    private static final int STATUS_INACTIVE_COLOR = 0xFFE74C3C;

    private AutoTuneMainScreen parent;
    private int x, y, width, height;
    private int scrollOffset = 0;

    private final List<ScoreBarWidget> scoreBars = new ArrayList<>();
    private int tickCounter;

    // Cached hardware info
    private String gpuText = "Unknown";
    private String cpuText = "Unknown";
    private String ramText = "Unknown";
    private String displayText = "Unknown";

    // Scores
    private int gpuScore = 0;
    private int cpuScore = 0;
    private int memoryScore = 0;
    private int storageScore = 0;
    private int thermalScore = 0;

    // Bottleneck
    private String bottleneckName = "None";
    private String bottleneckExplanation = "";
    private int bottleneckColor = TEXT_COLOR;

    @Override
    public String getName() {
        return "Dashboard";
    }

    @Override
    public void init(AutoTuneMainScreen parent, int x, int y, int width, int height) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scrollOffset = 0;
        this.tickCounter = 0;

        loadHardwareData();
        calculateScores();
        detectBottleneck();

        // Score bars
        scoreBars.clear();
        int barY = y + 80;
        int barWidth = width - 20;
        int barHeight = 14;
        int barSpacing = 18;

        scoreBars.add(new ScoreBarWidget(x + 10, barY, barWidth, barHeight, "GPU", gpuScore));
        scoreBars.add(new ScoreBarWidget(x + 10, barY + barSpacing, barWidth, barHeight, "CPU", cpuScore));
        scoreBars.add(new ScoreBarWidget(x + 10, barY + barSpacing * 2, barWidth, barHeight, "Memory", memoryScore));
        scoreBars.add(new ScoreBarWidget(x + 10, barY + barSpacing * 3, barWidth, barHeight, "Storage", storageScore));
        scoreBars.add(new ScoreBarWidget(x + 10, barY + barSpacing * 4, barWidth, barHeight, "Thermal", thermalScore));

        // Quick action buttons
        int buttonY = y + height - 30;
        int buttonWidth = (width - 40) / 3;
        int buttonSpacing = 5;

        parent.addTabWidget(ButtonWidget.builder(Text.literal("Run Benchmark"), btn -> {
            MinecraftClient client = parent.getClient();
            if (client != null) {
                client.setScreen(new BenchmarkIntroScreen(parent));
            }
        }).dimensions(x + 10, buttonY, buttonWidth, 20).build());

        parent.addTabWidget(ButtonWidget.builder(Text.literal("Questionnaire"), btn -> {
            com.autotune.questionnaire.QuestionnaireManager.getInstance().startQuestionnaire(parent);
        }).dimensions(x + 15 + buttonWidth, buttonY, buttonWidth, 20).build());

        parent.addTabWidget(ButtonWidget.builder(Text.literal("Toggle Live Mode"), btn -> {
            // [CODE-REVIEW-FIX] Null guard for getInstance()
            AutoTuneMod mod = AutoTuneMod.getInstance();
            if (mod == null) return;
            LiveAdaptiveEngine engine = mod.getLiveEngine();
            engine.setEnabled(!engine.isEnabled());
        }).dimensions(x + 20 + buttonWidth * 2, buttonY, buttonWidth, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = parent.getTextRenderer();
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        AutoTuneConfig config = mod.getConfig();
        LiveAdaptiveEngine engine = mod.getLiveEngine();

        int footerHeight = 30;
        int contentBottom = y + height - footerHeight;

        // Enable scissor clipping for scrollable content area
        context.enableScissor(x, y, x + width, contentBottom);

        int so = scrollOffset; // shorthand for scroll offset

        // Section: Hardware Summary
        context.drawText(textRenderer, Text.literal("Hardware Summary"), x + 10, y + 4 - so, SECTION_TITLE_COLOR, false);

        int cardW = (width - 30) / 4;
        int cardH = 40; // Tall enough for title + 2 lines of value text
        int cardY = y + 16 - so;
        int cardGap = 3;

        drawCard(context, textRenderer, x + 10, cardY, cardW, cardH, "GPU", gpuText);
        drawCard(context, textRenderer, x + 10 + (cardW + cardGap), cardY, cardW, cardH, "CPU", cpuText);
        drawCard(context, textRenderer, x + 10 + (cardW + cardGap) * 2, cardY, cardW, cardH, "RAM", ramText);
        drawCard(context, textRenderer, x + 10 + (cardW + cardGap) * 3, cardY, cardW, cardH, "Display", displayText);

        // Section: Performance Scores
        context.drawText(textRenderer, Text.literal("Performance Scores"), x + 10, y + 66 - so, SECTION_TITLE_COLOR, false);

        // Update score bar positions with scroll offset and render
        int barY = y + 80 - so;
        int barSpacing = 18;
        for (int i = 0; i < scoreBars.size(); i++) {
            scoreBars.get(i).setPosition(x + 10, barY + barSpacing * i);
            scoreBars.get(i).render(context, mouseX, mouseY, delta);
        }

        // Section: Bottleneck Indicator
        int bottleneckY = y + 170 - so;
        context.drawText(textRenderer, Text.literal("Bottleneck Analysis"), x + 10, bottleneckY, SECTION_TITLE_COLOR, false);
        context.fill(x + 10, bottleneckY + 12, x + width - 10, bottleneckY + 42, CARD_BG);
        drawBorder(context, x + 10, bottleneckY + 12, width - 20, 30, CARD_BORDER);
        context.drawText(textRenderer, Text.literal("Bottleneck: " + bottleneckName),
                x + 16, bottleneckY + 16, bottleneckColor, false);
        String fittedExplanation = fitTextToWidth(textRenderer, bottleneckExplanation, width - 32);
        context.drawText(textRenderer, Text.literal(fittedExplanation),
                x + 16, bottleneckY + 28, CARD_LABEL_COLOR, false);

        // Section: Status Info
        int statusY = bottleneckY + 52;
        context.drawText(textRenderer, Text.literal("Status"), x + 10, statusY, SECTION_TITLE_COLOR, false);

        String profileName = config.getActiveProfileName();
        context.drawText(textRenderer, Text.literal("Active Profile: " + profileName),
                x + 16, statusY + 14, TEXT_COLOR, false);

        boolean liveEnabled = engine.isEnabled();
        String liveStatus = liveEnabled ? "ACTIVE" : "INACTIVE";
        int liveColor = liveEnabled ? STATUS_ACTIVE_COLOR : STATUS_INACTIVE_COLOR;
        context.drawText(textRenderer, Text.literal("Live Mode: "), x + 16, statusY + 26, TEXT_COLOR, false);
        int liveLabelEnd = x + 16 + textRenderer.getWidth("Live Mode: ");
        context.drawText(textRenderer, Text.literal(liveStatus), liveLabelEnd, statusY + 26, liveColor, false);

        if (liveEnabled) {
            String stateStr = engine.getState().getDisplayName();
            context.drawText(textRenderer, Text.literal("State: " + stateStr),
                    x + 16, statusY + 38, TEXT_COLOR, false);

            String fpsStr = String.format("%.0f FPS (1%% low: %.0f)",
                    engine.getCurrentFps(), engine.getCurrent1PercentLow());
            context.drawText(textRenderer, Text.literal(fpsStr),
                    x + 16, statusY + 50, TEXT_COLOR, false);
        }

        // Disable scissor before rendering footer buttons
        context.disableScissor();

        // Footer buttons are rendered by the parent widget system at their fixed positions
    }

    @Override
    public void tick() {
        tickCounter++;
    }

    @Override
    public boolean handleScroll(double mouseX, double mouseY, double amount) {
        scrollOffset -= (int) (amount * 12);
        scrollOffset = Math.max(0, scrollOffset);
        int totalContentHeight = calculateContentHeight();
        int visibleHeight = height - 30; // minus footer
        scrollOffset = Math.min(scrollOffset, Math.max(0, totalContentHeight - visibleHeight));
        return true;
    }

    /**
     * Calculates the total height of all content sections for scroll bounds.
     */
    private int calculateContentHeight() {
        // Hardware Summary title (4) + cards (16 + 36 = 52) + gap (14)
        // Performance Scores title (66) + 5 bars * 18 spacing (80 + 90 = 170)
        // Bottleneck section (170 + 42 = 212) + gap (10)
        // Status section (222 + up to 62 lines)
        // Total: approximately 284 pixels of content
        // Use conservative estimate that accounts for live mode extra lines
        return 290;
    }

    private void loadHardwareData() {
        // [CODE-REVIEW-FIX] Null guard for getInstance() and getHardwareProfile()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        HardwareProfile hw = mod.getHardwareProfile();
        if (hw == null) return;

        // Store full names — truncation to pixel width happens at render time in drawCard()
        gpuText = hw.gpuName() != null ? hw.gpuName() : "Unknown";
        cpuText = hw.cpuName() != null ? hw.cpuName() : "Unknown";
        ramText = (hw.totalRamMb() / 1024) + " GB";
        displayText = hw.displayWidth() + "x" + hw.displayHeight() + " @" + hw.displayRefreshRate() + "Hz";
    }

    private void calculateScores() {
        // [CODE-REVIEW-FIX] Null guard for getInstance() and getHardwareProfile()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) {
            gpuScore = cpuScore = memoryScore = storageScore = thermalScore = 50;
            return;
        }
        HardwareProfile hw = mod.getHardwareProfile();
        if (hw == null) {
            gpuScore = cpuScore = memoryScore = storageScore = thermalScore = 50;
            return;
        }

        // GPU score based on VRAM and TFLOPS
        int vramScore = Math.min(100, hw.gpuVramMb() / 80);
        int tflopsScore = Math.min(100, (int) (hw.gpuTflops() * 10));
        gpuScore = clamp((vramScore + tflopsScore) / 2);

        // CPU score based on cores and clock
        int coreScore = Math.min(100, hw.cpuCores() * 12);
        int clockScore = Math.min(100, (int) (hw.cpuBoostClockGhz() * 22));
        cpuScore = clamp((coreScore + clockScore) / 2);

        // Memory score based on total RAM and heap
        int ramScore = Math.min(100, hw.totalRamMb() / 160);
        int heapScore = Math.min(100, (int) (hw.maxHeapMb() / 40));
        memoryScore = clamp((ramScore + heapScore) / 2);

        // Storage score
        long freeGb = hw.storageFreeBytes() / (1024L * 1024L * 1024L);
        storageScore = clamp("SSD".equalsIgnoreCase(hw.storageType()) ? 80 : 40);
        storageScore = clamp((storageScore + Math.min(100, (int) (freeGb * 2))) / 2);

        // Thermal score
        if (hw.thermalThrottlingDetected()) {
            thermalScore = 20;
        } else if (hw.cpuTemperature() > 85 || hw.gpuTemperature() > 85) {
            thermalScore = 40;
        } else if (hw.cpuTemperature() > 70 || hw.gpuTemperature() > 70) {
            thermalScore = 65;
        } else {
            thermalScore = 90;
        }
    }

    private void detectBottleneck() {
        int minScore = Math.min(gpuScore, Math.min(cpuScore, Math.min(memoryScore, Math.min(storageScore, thermalScore))));
        if (minScore >= 60) {
            bottleneckName = "None Detected";
            bottleneckExplanation = "System is well-balanced for Minecraft.";
            bottleneckColor = STATUS_ACTIVE_COLOR;
        } else if (minScore == gpuScore) {
            bottleneckName = "GPU";
            bottleneckExplanation = "Graphics card is the limiting factor. Lower render distance or graphics quality.";
            bottleneckColor = 0xFFE74C3C;
        } else if (minScore == cpuScore) {
            bottleneckName = "CPU";
            bottleneckExplanation = "Processor is the limiting factor. Reduce simulation distance and entity count.";
            bottleneckColor = 0xFFE74C3C;
        } else if (minScore == memoryScore) {
            bottleneckName = "Memory";
            bottleneckExplanation = "RAM/heap allocation is limiting. Increase JVM heap or close other applications.";
            bottleneckColor = 0xFFF39C12;
        } else if (minScore == thermalScore) {
            bottleneckName = "Thermal";
            bottleneckExplanation = "Thermal throttling detected. Improve cooling or reduce workload.";
            bottleneckColor = 0xFFE74C3C;
        } else {
            bottleneckName = "Storage";
            bottleneckExplanation = "Slow storage may cause chunk loading stutter. Consider an SSD.";
            bottleneckColor = 0xFFF39C12;
        }
    }

    private void drawCard(DrawContext context, TextRenderer textRenderer,
                          int cx, int cy, int cw, int ch, String title, String value) {
        context.fill(cx, cy, cx + cw, cy + ch, CARD_BG);
        drawBorder(context, cx, cy, cw, ch, CARD_BORDER);
        context.drawText(textRenderer, Text.literal(title), cx + 4, cy + 4, CARD_TITLE_COLOR, false);

        // Fit value text to card width — truncate by pixel width, not char count
        int maxTextWidth = cw - 8; // 4px padding each side
        String displayValue = fitTextToWidth(textRenderer, value, maxTextWidth);

        // If text was truncated significantly, try two lines
        if (displayValue.length() < value.length() - 3 && ch >= 28) {
            // Split into two lines
            String line1 = fitTextToWidth(textRenderer, value, maxTextWidth);
            String remainder = value.substring(Math.max(0, line1.length() - 3)).trim(); // overlap to find better break
            // Find the actual break point by measuring
            int breakIdx = 0;
            for (int i = 1; i <= value.length(); i++) {
                if (textRenderer.getWidth(value.substring(0, i)) > maxTextWidth) {
                    breakIdx = i - 1;
                    break;
                }
                breakIdx = i;
            }
            line1 = value.substring(0, breakIdx);
            String line2 = value.substring(breakIdx).trim();
            if (!line2.isEmpty()) {
                line2 = fitTextToWidth(textRenderer, line2, maxTextWidth);
            }
            context.drawText(textRenderer, Text.literal(line1), cx + 4, cy + 14, CARD_VALUE_COLOR, false);
            if (!line2.isEmpty()) {
                context.drawText(textRenderer, Text.literal(line2), cx + 4, cy + 24, CARD_LABEL_COLOR, false);
            }
        } else {
            context.drawText(textRenderer, Text.literal(displayValue), cx + 4, cy + 16, CARD_VALUE_COLOR, false);
        }
    }

    /** Truncates text to fit within maxPixelWidth, adding "..." if needed */
    private static String fitTextToWidth(TextRenderer textRenderer, String text, int maxPixelWidth) {
        if (text == null) return "Unknown";
        if (textRenderer.getWidth(text) <= maxPixelWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = textRenderer.getWidth(ellipsis);
        for (int i = text.length() - 1; i > 0; i--) {
            if (textRenderer.getWidth(text.substring(0, i)) + ellipsisWidth <= maxPixelWidth) {
                return text.substring(0, i) + ellipsis;
            }
        }
        return ellipsis;
    }

    private void drawBorder(DrawContext context, int bx, int by, int bw, int bh, int color) {
        context.fill(bx, by, bx + bw, by + 1, color);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        context.fill(bx, by, bx + 1, by + bh, color);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }

    private static int clamp(int value) {
        return Math.clamp(value, 0, 100);
    }
}
