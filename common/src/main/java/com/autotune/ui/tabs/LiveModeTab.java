package com.autotune.ui.tabs;

import com.autotune.AutoTuneMod;
import com.autotune.config.AutoTuneConfig;
import com.autotune.live.AdaptiveState;
import com.autotune.live.AdjustmentHistory;
import com.autotune.live.LiveAdaptiveEngine;
import com.autotune.ui.AutoTuneMainScreen;
import com.autotune.ui.widgets.FPSChartWidget;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Live Mode tab with enable/disable toggle, mode selector,
 * session statistics, adjustment history log, and parameter sliders.
 */
public class LiveModeTab implements Tab {

    private static final int SECTION_TITLE_COLOR = 0xFF3498DB;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int DIM_TEXT_COLOR = 0xFF888888;
    private static final int CARD_BG = 0xFF1A1A2E;
    private static final int CARD_BORDER = 0xFF333355;
    private static final int STABLE_COLOR = 0xFF2ECC71;
    private static final int ADAPTING_COLOR = 0xFFF39C12;
    private static final int EMERGENCY_COLOR = 0xFFE74C3C;
    private static final int BOOSTING_COLOR = 0xFF3498DB;

    private AutoTuneMainScreen parent;
    private int x, y, width, height;
    private FPSChartWidget fpsChart;
    private int scrollOffset = 0;
    private int logScrollOffset;
    private int tickCounter;

    @Override
    public String getName() {
        return "Live Mode";
    }

    @Override
    public void init(AutoTuneMainScreen parent, int x, int y, int width, int height) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scrollOffset = 0;
        this.logScrollOffset = 0;
        this.tickCounter = 0;

        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        LiveAdaptiveEngine engine = mod.getLiveEngine();
        AutoTuneConfig config = mod.getConfig();

        // Enable/disable toggle button
        boolean enabled = engine.isEnabled();
        parent.addTabWidget(ButtonWidget.builder(
                Text.literal(enabled ? "Disable Live Mode" : "Enable Live Mode"),
                btn -> {
                    engine.setEnabled(!engine.isEnabled());
                    btn.setMessage(Text.literal(engine.isEnabled() ? "Disable Live Mode" : "Enable Live Mode"));
                }
        ).dimensions(x + 10, y + 4, 140, 20).build());

        // Mode selector buttons
        String currentMode = config.getLiveModeConfig().getMode();
        int modeX = x + 160;
        int modeWidth = 80;

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Full"),
                btn -> config.getLiveModeConfig().setMode("full")
        ).dimensions(modeX, y + 4, modeWidth, 20).build());

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Conservative"),
                btn -> config.getLiveModeConfig().setMode("conservative")
        ).dimensions(modeX + modeWidth + 4, y + 4, modeWidth + 20, 20).build());

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Static"),
                btn -> config.getLiveModeConfig().setMode("static")
        ).dimensions(modeX + modeWidth * 2 + 28, y + 4, modeWidth, 20).build());

        // FPS Chart
        int chartHeight = 80;
        fpsChart = new FPSChartWidget(x + 10, y + 30, width - 20, chartHeight, config.getTargetFps());

        // Parameter sliders
        int sliderY = y + height - 80;
        int sliderWidth = (width - 40) / 3;

        // Cooldown slider
        parent.addTabWidget(new AutoTuneSlider(
                x + 10, sliderY, sliderWidth, 20,
                "Cooldown: " + config.getLiveModeConfig().getAdjustmentCooldownMs() + "ms",
                config.getLiveModeConfig().getAdjustmentCooldownMs() / 10000.0,
                val -> {
                    int ms = (int) (val * 10000);
                    ms = Math.clamp(ms, 500, 10000);
                    config.getLiveModeConfig().setAdjustmentCooldownMs(ms);
                },
                "Cooldown"
        ));

        // Hysteresis slider
        parent.addTabWidget(new AutoTuneSlider(
                x + 15 + sliderWidth, sliderY, sliderWidth, 20,
                "Hysteresis: " + String.format("%.0f%%", config.getLiveModeConfig().getHysteresisPercent()),
                config.getLiveModeConfig().getHysteresisPercent() / 25.0,
                val -> {
                    float pct = (float) (val * 25);
                    pct = Math.clamp(pct, 1, 25);
                    config.getLiveModeConfig().setHysteresisPercent(pct);
                },
                "Hysteresis"
        ));

        // Boost threshold slider
        parent.addTabWidget(new AutoTuneSlider(
                x + 20 + sliderWidth * 2, sliderY, sliderWidth, 20,
                "Boost: " + String.format("%.0f%%", config.getLiveModeConfig().getBoostThresholdPercent()),
                config.getLiveModeConfig().getBoostThresholdPercent() / 50.0,
                val -> {
                    float pct = (float) (val * 50);
                    pct = Math.clamp(pct, 5, 50);
                    config.getLiveModeConfig().setBoostThresholdPercent(pct);
                },
                "Boost"
        ));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = parent.getTextRenderer();
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        LiveAdaptiveEngine engine = mod.getLiveEngine();
        AutoTuneConfig config = mod.getConfig();

        // Scissor region for scrollable content (leave 100px at bottom for sliders/buttons)
        context.enableScissor(x, y, x + width, y + height - 100);

        // Mode indicator
        String currentMode = config.getLiveModeConfig().getMode();
        String modeLabel = "Mode: " + currentMode.substring(0, 1).toUpperCase() + currentMode.substring(1);
        context.drawText(textRenderer, Text.literal(modeLabel),
                x + width - 10 - textRenderer.getWidth(modeLabel), y + 10 - scrollOffset, SECTION_TITLE_COLOR, false);

        // FPS Chart
        fpsChart.setPosition(x + 10, y + 30 - scrollOffset);
        fpsChart.render(context, mouseX, mouseY, delta);

        // Stats section
        int statsY = y + 116 - scrollOffset;
        context.drawText(textRenderer, Text.literal("Session Statistics"), x + 10, statsY, SECTION_TITLE_COLOR, false);

        int statsCardY = statsY + 14;
        int statsCardWidth = (width - 30) / 3;
        int statsCardHeight = 30;

        // Adjustments this session
        int totalAdj = engine.getAdjustmentHistory().getTotalAdjustments();
        drawStatCard(context, textRenderer, x + 10, statsCardY, statsCardWidth, statsCardHeight,
                "Adjustments", String.valueOf(totalAdj));

        // Current state
        AdaptiveState state = engine.getState();
        int stateColor = getStateColor(state);
        drawStatCard(context, textRenderer, x + 15 + statsCardWidth, statsCardY, statsCardWidth, statsCardHeight,
                "State", state.getDisplayName());

        // Current FPS
        String fpsStr = String.format("%.0f FPS", engine.getCurrentFps());
        drawStatCard(context, textRenderer, x + 20 + statsCardWidth * 2, statsCardY, statsCardWidth, statsCardHeight,
                "Current FPS", fpsStr);

        // State time totals
        int timeY = statsCardY + statsCardHeight + 8;
        context.drawText(textRenderer, Text.literal("Time in States:"), x + 10, timeY, DIM_TEXT_COLOR, false);
        Map<AdaptiveState, Long> timeTotals = engine.getStateTimeTotals();
        int timeX = x + 10;
        timeY += 12;
        for (AdaptiveState s : AdaptiveState.values()) {
            long timeMs = timeTotals.getOrDefault(s, 0L);
            if (timeMs > 0 || s == AdaptiveState.STABLE) {
                String timeStr = formatDuration(timeMs);
                String label = s.getDisplayName() + ": " + timeStr;
                int labelWidth = textRenderer.getWidth(label) + 8;
                if (timeX + labelWidth > x + width - 10) {
                    timeX = x + 10;
                    timeY += 12;
                }
                context.drawText(textRenderer, Text.literal(label), timeX, timeY, getStateColor(s), false);
                timeX += labelWidth;
            }
        }

        // Adjustment History log
        int logY = timeY + 20;
        context.drawText(textRenderer, Text.literal("Adjustment History"), x + 10, logY, SECTION_TITLE_COLOR, false);
        logY += 14;

        List<AdjustmentHistory.AdjustmentEntry> entries = engine.getAdjustmentHistory().getEntries();
        int logHeight = Math.max(20, y + height - 100 - logY);
        int rowHeight = 12;
        int maxLogRows = logHeight / rowHeight;

        context.fill(x + 10, logY, x + width - 10, logY + logHeight, CARD_BG);
        drawBorder(context, x + 10, logY, width - 20, logHeight, CARD_BORDER);

        if (entries.isEmpty()) {
            context.drawText(textRenderer, Text.literal("No adjustments recorded this session."),
                    x + 14, logY + 4, DIM_TEXT_COLOR, false);
        } else {
            int startIdx = Math.max(0, entries.size() - maxLogRows - logScrollOffset);
            int endIdx = Math.min(entries.size(), startIdx + maxLogRows);

            for (int i = startIdx; i < endIdx; i++) {
                AdjustmentHistory.AdjustmentEntry entry = entries.get(i);
                int entryY = logY + 2 + (i - startIdx) * rowHeight;

                String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date(entry.timestamp()));
                String line = timeStr + " " + entry.toDisplayString();

                int lineColor = switch (entry.state()) {
                    case EMERGENCY -> EMERGENCY_COLOR;
                    case BOOSTING -> BOOSTING_COLOR;
                    case DEGRADING, RECOVERING -> ADAPTING_COLOR;
                    default -> TEXT_COLOR;
                };

                context.drawText(textRenderer, Text.literal(line), x + 14, entryY, lineColor, false);
            }
        }

        context.disableScissor();

        // Slider labels rendered outside scissor (fixed footer area)
        int sliderLabelY = y + height - 92;
        AutoTuneConfig.LiveModeConfig lmc = config.getLiveModeConfig();
        int sliderWidth = (width - 40) / 3;
        context.drawText(textRenderer, Text.literal("Cooldown: " + lmc.getAdjustmentCooldownMs() + "ms"),
                x + 10, sliderLabelY, DIM_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal(String.format("Hysteresis: %.0f%%", lmc.getHysteresisPercent())),
                x + 15 + sliderWidth, sliderLabelY, DIM_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal(String.format("Boost: %.0f%%", lmc.getBoostThresholdPercent())),
                x + 20 + sliderWidth * 2, sliderLabelY, DIM_TEXT_COLOR, false);
    }

    @Override
    public void tick() {
        tickCounter++;
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        LiveAdaptiveEngine engine = mod.getLiveEngine();

        // Feed FPS data to chart every 10 ticks (~6/sec)
        if (tickCounter % 3 == 0 && fpsChart != null) {
            fpsChart.recordFps(engine.getCurrentFps());
        }
    }

    @Override
    public boolean handleScroll(double mouseX, double mouseY, double amount) {
        scrollOffset -= (int)(amount * 12);
        scrollOffset = Math.max(0, scrollOffset);
        return true;
    }

    private int getStateColor(AdaptiveState state) {
        return switch (state) {
            case STABLE -> STABLE_COLOR;
            case DEGRADING, RECOVERING -> ADAPTING_COLOR;
            case EMERGENCY -> EMERGENCY_COLOR;
            case BOOSTING -> BOOSTING_COLOR;
            case LOCKED -> DIM_TEXT_COLOR;
        };
    }

    private void drawStatCard(DrawContext context, TextRenderer textRenderer,
                              int cx, int cy, int cw, int ch, String label, String value) {
        context.fill(cx, cy, cx + cw, cy + ch, CARD_BG);
        drawBorder(context, cx, cy, cw, ch, CARD_BORDER);
        context.drawText(textRenderer, Text.literal(label), cx + 4, cy + 3, DIM_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal(value), cx + 4, cy + 15, TEXT_COLOR, false);
    }

    private void drawBorder(DrawContext context, int bx, int by, int bw, int bh, int color) {
        context.fill(bx, by, bx + bw, by + 1, color);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        context.fill(bx, by, bx + 1, by + bh, color);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }

    private static String formatDuration(long ms) {
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) return minutes + "m " + seconds + "s";
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m";
    }

    /**
     * Custom slider widget for live mode parameters.
     */
    private static class AutoTuneSlider extends SliderWidget {

        private final java.util.function.DoubleConsumer onChange;
        private final String prefix;

        public AutoTuneSlider(int x, int y, int width, int height, String message,
                              double value, java.util.function.DoubleConsumer onChange, String prefix) {
            super(x, y, width, height, Text.literal(message), Math.clamp(value, 0, 1));
            this.onChange = onChange;
            this.prefix = prefix;
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(prefix + ": " + String.format("%.0f%%", value * 100)));
        }

        @Override
        protected void applyValue() {
            onChange.accept(value);
        }
    }
}
