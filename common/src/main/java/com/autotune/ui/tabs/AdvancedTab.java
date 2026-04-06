package com.autotune.ui.tabs;

import com.autotune.AutoTuneMod;
import com.autotune.benchmark.hardware.HardwareProfile;
import com.autotune.config.AutoTuneConfig;
import com.autotune.config.ConfigManager;
import com.autotune.ui.AutoTuneMainScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced tab providing a full hardware dump, GL extensions list,
 * JVM info, GC statistics, config file paths, a system report copy button,
 * a reset all data button with confirmation, and a debug mode toggle.
 */
public class AdvancedTab implements Tab {

    private static final int SECTION_TITLE_COLOR = 0xFF3498DB;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int DIM_TEXT_COLOR = 0xFF888888;
    private static final int DATA_COLOR = 0xFF2ECC71;
    private static final int DANGER_COLOR = 0xFFE74C3C;

    private AutoTuneMainScreen parent;
    private int x, y, width, height;
    private int scrollOffset;
    private final List<InfoLine> infoLines = new ArrayList<>();
    private boolean confirmReset;
    private boolean debugMode;

    @Override
    public String getName() {
        return "Advanced";
    }

    @Override
    public void init(AutoTuneMainScreen parent, int x, int y, int width, int height) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scrollOffset = 0;
        this.confirmReset = false;

        buildInfoLines();

        // Buttons at the bottom
        int btnY = y + height - 26;
        int btnWidth = (width - 50) / 4;

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Copy System Report"),
                btn -> copyReport()
        ).dimensions(x + 10, btnY, btnWidth, 20).build());

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Reset All Data"),
                this::handleReset
        ).dimensions(x + 15 + btnWidth, btnY, btnWidth, 20).build());

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal(debugMode ? "Debug: ON" : "Debug: OFF"),
                btn -> {
                    debugMode = !debugMode;
                    btn.setMessage(Text.literal(debugMode ? "Debug: ON" : "Debug: OFF"));
                    if (parent.getToast() != null) {
                        parent.getToast().showInfo("Debug mode " + (debugMode ? "enabled" : "disabled"));
                    }
                }
        ).dimensions(x + 20 + btnWidth * 2, btnY, btnWidth, 20).build());

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Re-detect Hardware"),
                btn -> {
                    // [CODE-REVIEW-FIX] Null guard for getInstance()
                    AutoTuneMod mod = AutoTuneMod.getInstance();
                    if (mod == null) return;
                    mod.detectHardware();
                    buildInfoLines();
                    if (parent.getToast() != null) {
                        parent.getToast().showInfo("Hardware re-detected");
                    }
                }
        ).dimensions(x + 25 + btnWidth * 3, btnY, btnWidth, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = parent.getTextRenderer();

        int listY = y + 4;
        int rowHeight = 11;
        int maxVisible = (height - 40) / rowHeight;

        context.enableScissor(x + 10, listY, x + width - 10, listY + maxVisible * rowHeight);

        for (int i = 0; i < infoLines.size(); i++) {
            int rowY = listY + i * rowHeight - scrollOffset;

            if (rowY + rowHeight < listY || rowY > listY + maxVisible * rowHeight) {
                continue;
            }

            InfoLine line = infoLines.get(i);

            if (line.isHeader) {
                // Section header
                context.fill(x + 10, rowY, x + width - 10, rowY + rowHeight, 0xFF252542);
                context.drawText(textRenderer, Text.literal(line.label), x + 12, rowY + 1, SECTION_TITLE_COLOR, false);
            } else if (line.value != null) {
                // Key-value pair — truncate value to fit available width
                context.drawText(textRenderer, Text.literal(line.label), x + 16, rowY + 1, DIM_TEXT_COLOR, false);
                int valueX = x + (int) (width * 0.45);
                int maxValueW = x + width - 14 - valueX;
                String fittedValue = fitText(textRenderer, line.value, maxValueW);
                context.drawText(textRenderer, Text.literal(fittedValue), valueX, rowY + 1, DATA_COLOR, false);
            } else {
                // Plain text
                context.drawText(textRenderer, Text.literal(line.label), x + 16, rowY + 1, TEXT_COLOR, false);
            }
        }

        context.disableScissor();

        // Scroll bar
        if (infoLines.size() > maxVisible) {
            int scrollBarMaxH = maxVisible * rowHeight;
            int scrollBarH = (int) ((double) maxVisible / infoLines.size() * scrollBarMaxH);
            scrollBarH = Math.max(scrollBarH, 10);
            int maxScroll = Math.max(1, infoLines.size() * rowHeight - scrollBarMaxH);
            int scrollBarY = listY + (int) ((double) scrollOffset / maxScroll * (scrollBarMaxH - scrollBarH));
            context.fill(x + width - 13, scrollBarY, x + width - 10, scrollBarY + scrollBarH, 0xFF555577);
        }

        // Confirm reset overlay
        if (confirmReset) {
            int overlayW = 260;
            int overlayH = 60;
            int overlayX = x + (width - overlayW) / 2;
            int overlayY = y + (height - overlayH) / 2;

            context.fill(overlayX, overlayY, overlayX + overlayW, overlayY + overlayH, 0xEE0F0F23);
            drawBorder(context, overlayX, overlayY, overlayW, overlayH, DANGER_COLOR);

            context.drawText(textRenderer, Text.literal("Are you sure? This cannot be undone!"),
                    overlayX + 10, overlayY + 8, DANGER_COLOR, false);
            context.drawText(textRenderer, Text.literal("Click 'Reset All Data' again to confirm."),
                    overlayX + 10, overlayY + 22, TEXT_COLOR, false);
            context.drawText(textRenderer, Text.literal("Click anywhere else to cancel."),
                    overlayX + 10, overlayY + 36, DIM_TEXT_COLOR, false);
        }
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

    private void buildInfoLines() {
        infoLines.clear();
        // [CODE-REVIEW-FIX] Null guard for getInstance() and getHardwareProfile()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        HardwareProfile hw = mod != null ? mod.getHardwareProfile() : null;

        // Hardware Dump
        infoLines.add(new InfoLine("--- Hardware Profile ---", null, true));
        if (hw != null) {
            infoLines.add(new InfoLine("GPU Name", hw.gpuName()));
            infoLines.add(new InfoLine("GPU Vendor", hw.gpuVendor()));
            infoLines.add(new InfoLine("GPU VRAM", hw.gpuVramMb() + " MB"));
            infoLines.add(new InfoLine("GPU Driver", hw.gpuDriver()));
            infoLines.add(new InfoLine("GPU TFLOPS", String.format("%.1f", hw.gpuTflops())));
            infoLines.add(new InfoLine("GPU Architecture", hw.gpuArchitecture()));
            infoLines.add(new InfoLine("GPU Generation", String.valueOf(hw.gpuGeneration())));
            infoLines.add(new InfoLine("GPU Tier Hint", String.valueOf(hw.gpuTierHint())));
            infoLines.add(new InfoLine("OpenGL Version", hw.glVersion()));
            infoLines.add(new InfoLine("GL Renderer", hw.glRenderer()));
            infoLines.add(new InfoLine("CPU Name", hw.cpuName()));
            infoLines.add(new InfoLine("CPU Cores", hw.cpuCores() + " cores / " + hw.cpuThreads() + " threads"));
            infoLines.add(new InfoLine("CPU Clock", String.format("%.2f GHz (boost %.2f GHz)", hw.cpuBaseClockGhz(), hw.cpuBoostClockGhz())));
            infoLines.add(new InfoLine("CPU L3 Cache", hw.cpuL3CacheMb() + " MB"));
            infoLines.add(new InfoLine("CPU Architecture", hw.cpuArchitecture()));
            infoLines.add(new InfoLine("CPU Tier Hint", String.valueOf(hw.cpuTierHint())));
            infoLines.add(new InfoLine("Total RAM", hw.totalRamMb() + " MB (" + (hw.totalRamMb() / 1024) + " GB)"));
            infoLines.add(new InfoLine("Available RAM", hw.availableRamMb() + " MB"));
            infoLines.add(new InfoLine("Max JVM Heap", hw.maxHeapMb() + " MB"));
            infoLines.add(new InfoLine("Allocated Heap", hw.allocatedHeapMb() + " MB"));
            infoLines.add(new InfoLine("Display Resolution", hw.displayResolution()));
            infoLines.add(new InfoLine("Display Size", hw.displayWidth() + "x" + hw.displayHeight()));
            infoLines.add(new InfoLine("Refresh Rate", hw.displayRefreshRate() + " Hz"));
            infoLines.add(new InfoLine("Storage Type", hw.storageType()));
            infoLines.add(new InfoLine("Storage Free", formatBytes(hw.storageFreeBytes())));
            infoLines.add(new InfoLine("CPU Temperature", formatTemp(hw.cpuTemperature())));
            infoLines.add(new InfoLine("GPU Temperature", formatTemp(hw.gpuTemperature())));
            infoLines.add(new InfoLine("Thermal Throttling", hw.thermalThrottlingDetected() ? "DETECTED" : "None"));

            // GL Extensions
            infoLines.add(new InfoLine("--- GL Extensions ---", null, true));
            if (hw.glExtensions() != null) {
                infoLines.add(new InfoLine("Total Extensions", String.valueOf(hw.glExtensions().size())));
                int count = 0;
                for (String ext : hw.glExtensions()) {
                    infoLines.add(new InfoLine("  " + ext, null));
                    count++;
                    if (count > 100) {
                        infoLines.add(new InfoLine("  ... and " + (hw.glExtensions().size() - 100) + " more", null));
                        break;
                    }
                }
            }
        } else {
            infoLines.add(new InfoLine("No hardware profile detected yet.", null));
        }

        // JVM Info
        infoLines.add(new InfoLine("--- JVM Information ---", null, true));
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        infoLines.add(new InfoLine("Java Version", System.getProperty("java.version")));
        infoLines.add(new InfoLine("Java Vendor", System.getProperty("java.vendor")));
        infoLines.add(new InfoLine("JVM Name", runtime.getVmName()));
        infoLines.add(new InfoLine("JVM Version", runtime.getVmVersion()));
        infoLines.add(new InfoLine("OS Name", System.getProperty("os.name")));
        infoLines.add(new InfoLine("OS Arch", System.getProperty("os.arch")));
        infoLines.add(new InfoLine("OS Version", System.getProperty("os.version")));
        infoLines.add(new InfoLine("Uptime", formatDuration(runtime.getUptime())));

        List<String> jvmArgs = runtime.getInputArguments();
        infoLines.add(new InfoLine("JVM Arguments", jvmArgs.size() + " args"));
        for (String arg : jvmArgs) {
            infoLines.add(new InfoLine("  " + arg, null));
        }

        // GC Statistics
        infoLines.add(new InfoLine("--- GC Statistics ---", null, true));
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gc : gcBeans) {
            infoLines.add(new InfoLine("Collector: " + gc.getName(), null));
            infoLines.add(new InfoLine("  Collections", String.valueOf(gc.getCollectionCount())));
            infoLines.add(new InfoLine("  Total Time", gc.getCollectionTime() + " ms"));
        }

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        infoLines.add(new InfoLine("Heap Used", formatBytes(memBean.getHeapMemoryUsage().getUsed())));
        infoLines.add(new InfoLine("Heap Committed", formatBytes(memBean.getHeapMemoryUsage().getCommitted())));
        infoLines.add(new InfoLine("Heap Max", formatBytes(memBean.getHeapMemoryUsage().getMax())));
        infoLines.add(new InfoLine("Non-Heap Used", formatBytes(memBean.getNonHeapMemoryUsage().getUsed())));

        // Config Paths
        infoLines.add(new InfoLine("--- Config File Paths ---", null, true));
        infoLines.add(new InfoLine("Config Dir", "config/autotune/"));
        infoLines.add(new InfoLine("Main Config", "config/autotune/config.json"));
        infoLines.add(new InfoLine("Hardware Profile", "config/autotune/hardware.json"));
        infoLines.add(new InfoLine("Benchmark Results", "config/autotune/benchmark.json"));
        infoLines.add(new InfoLine("Profiles Dir", "config/autotune/profiles/"));

        // Minecraft info
        infoLines.add(new InfoLine("--- Minecraft ---", null, true));
        // [CODE-REVIEW-FIX] Null guard for getInstance() chain
        AutoTuneMod mcMod = AutoTuneMod.getInstance();
        if (mcMod != null) {
            infoLines.add(new InfoLine("MC Version", mcMod.getPlatformAdapter().getMinecraftVersion()));
            infoLines.add(new InfoLine("Detected Mods", String.valueOf(mcMod.getModsRegistry().getDetectedMods().size())));
        } else {
            infoLines.add(new InfoLine("MC Version", "N/A"));
            infoLines.add(new InfoLine("Detected Mods", "N/A"));
        }
    }

    private void copyReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== AutoTune System Report ===\n\n");
        for (InfoLine line : infoLines) {
            if (line.isHeader) {
                sb.append("\n").append(line.label).append("\n");
            } else if (line.value != null) {
                sb.append(String.format("%-30s %s\n", line.label, line.value));
            } else {
                sb.append(line.label).append("\n");
            }
        }

        MinecraftClient client = parent.getClient();
        if (client != null) {
            client.keyboard.setClipboard(sb.toString());
            if (parent.getToast() != null) {
                parent.getToast().showInfo("System report copied to clipboard");
            }
        }
    }

    private void handleReset(ButtonWidget btn) {
        if (!confirmReset) {
            confirmReset = true;
            btn.setMessage(Text.literal("CONFIRM RESET"));
        } else {
            // Perform reset
            confirmReset = false;
            btn.setMessage(Text.literal("Reset All Data"));

            // [CODE-REVIEW-FIX] Null guard for getInstance()
            AutoTuneMod resetMod = AutoTuneMod.getInstance();
            if (resetMod == null) return;
            ConfigManager configManager = resetMod.getConfigManager();
            AutoTuneConfig freshConfig = new AutoTuneConfig();
            configManager.save(freshConfig);

            if (parent.getToast() != null) {
                parent.getToast().showWarning("All AutoTune data has been reset");
            }
        }
    }

    private void drawBorder(DrawContext context, int bx, int by, int bw, int bh, int color) {
        context.fill(bx, by, bx + bw, by + 1, color);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        context.fill(bx, by, bx + 1, by + bh, color);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String formatTemp(double temp) {
        if (temp <= 0) return "N/A";
        return String.format("%.0f C", temp);
    }

    private static String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        minutes %= 60;
        seconds %= 60;
        if (hours > 0) return String.format("%dh %dm %ds", hours, minutes, seconds);
        if (minutes > 0) return String.format("%dm %ds", minutes, seconds);
        return seconds + "s";
    }

    private record InfoLine(String label, String value, boolean isHeader) {
        InfoLine(String label, String value) {
            this(label, value, false);
        }
    }

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
