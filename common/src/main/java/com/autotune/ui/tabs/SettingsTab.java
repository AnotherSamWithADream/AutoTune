package com.autotune.ui.tabs;

import com.autotune.AutoTuneMod;
import com.autotune.config.AutoTuneConfig;
import com.autotune.ui.AutoTuneMainScreen;
import com.autotune.ui.screens.SettingsPreviewScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.*;

/**
 * Settings tab displaying a full scrollable list of every managed setting.
 * Each row shows the setting name, current value, recommended value, and a
 * status badge (Match/Differs/Unknown). Provides Apply All, Apply Selected,
 * and Reset to Vanilla Defaults buttons.
 */
public class SettingsTab implements Tab {

    private static final int SECTION_TITLE_COLOR = 0xFF3498DB;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int DIM_TEXT_COLOR = 0xFF888888;
    private static final int ROW_BG_EVEN = 0xFF1A1A2E;
    private static final int ROW_BG_ODD = 0xFF1E1E36;
    private static final int ROW_HOVER_BG = 0xFF252542;
    private static final int HEADER_BG = 0xFF252542;
    private static final int CARD_BORDER = 0xFF333355;
    private static final int MATCH_COLOR = 0xFF2ECC71;
    private static final int DIFFER_COLOR = 0xFFF39C12;
    private static final int UNKNOWN_COLOR = 0xFF888888;

    private AutoTuneMainScreen parent;
    private int x, y, width, height;
    private int scrollOffset;
    private final Set<String> selectedSettings = new HashSet<>();

    // Cached setting entries for display
    private final List<SettingRow> settingRows = new ArrayList<>();

    @Override
    public String getName() {
        return "Settings";
    }

    @Override
    public void init(AutoTuneMainScreen parent, int x, int y, int width, int height) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scrollOffset = 0;
        this.selectedSettings.clear();

        buildSettingRows();

        // Buttons at the bottom
        int btnY = y + height - 26;
        int btnWidth = (width - 40) / 3;

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Apply All"),
                btn -> applyAll()
        ).dimensions(x + 10, btnY, btnWidth, 20).build());

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Apply Selected"),
                btn -> applySelected()
        ).dimensions(x + 15 + btnWidth, btnY, btnWidth, 20).build());

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Reset to Vanilla"),
                btn -> resetToDefaults()
        ).dimensions(x + 20 + btnWidth * 2, btnY, btnWidth, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = parent.getTextRenderer();

        int headerY = y + 4;
        context.drawText(textRenderer, Text.literal("Managed Settings"), x + 10, headerY, SECTION_TITLE_COLOR, false);

        String countStr = settingRows.size() + " settings";
        int countWidth = textRenderer.getWidth(countStr);
        context.drawText(textRenderer, Text.literal(countStr), x + width - 10 - countWidth, headerY, DIM_TEXT_COLOR, false);

        // Column headers
        int listY = y + 18;
        context.fill(x + 10, listY, x + width - 10, listY + 14, HEADER_BG);

        int col1 = x + 14;
        int col2 = x + (int)(width * 0.40);
        int col3 = x + (int)(width * 0.58);
        int col4 = x + (int)(width * 0.76);

        context.drawText(textRenderer, Text.literal("Setting"), col1, listY + 3, DIM_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal("Current"), col2, listY + 3, DIM_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal("Recommended"), col3, listY + 3, DIM_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal("Status"), col4, listY + 3, DIM_TEXT_COLOR, false);

        // Scrollable setting rows
        int rowStartY = listY + 16;
        int rowHeight = 18;
        int maxVisible = (height - 70) / rowHeight;

        int scissorBottom = Math.min(rowStartY + maxVisible * rowHeight, y + height - 30);
        context.enableScissor(x + 10, rowStartY, x + width - 10, scissorBottom);

        for (int i = 0; i < settingRows.size(); i++) {
            int rowY = rowStartY + i * rowHeight - scrollOffset;

            if (rowY + rowHeight < rowStartY || rowY > rowStartY + maxVisible * rowHeight) {
                continue;
            }

            SettingRow row = settingRows.get(i);
            boolean hovered = mouseX >= x + 10 && mouseX < x + width - 10
                    && mouseY >= rowY && mouseY < rowY + rowHeight
                    && mouseY >= rowStartY && mouseY < rowStartY + maxVisible * rowHeight;
            boolean selected = selectedSettings.contains(row.id);

            int bgColor;
            if (selected) {
                bgColor = 0xFF1A2A4E;
            } else if (hovered) {
                bgColor = ROW_HOVER_BG;
            } else {
                bgColor = i % 2 == 0 ? ROW_BG_EVEN : ROW_BG_ODD;
            }
            context.fill(x + 10, rowY, x + width - 10, rowY + rowHeight, bgColor);
            context.fill(x + 10, rowY + rowHeight - 1, x + width - 10, rowY + rowHeight, CARD_BORDER);

            // Selection checkbox area
            if (selected) {
                context.fill(x + 11, rowY + 4, x + 21, rowY + 14, 0xFF3498DB);
                context.drawText(textRenderer, Text.literal("x"), x + 13, rowY + 4, 0xFFFFFFFF, false);
            } else {
                drawBorder(context, x + 11, rowY + 4, 10, 10, CARD_BORDER);
            }

            // Name
            String name = row.name;
            if (textRenderer.getWidth(name) > col2 - col1 - 20) {
                while (textRenderer.getWidth(name + "...") > col2 - col1 - 20 && name.length() > 3) {
                    name = name.substring(0, name.length() - 1);
                }
                name += "...";
            }
            context.drawText(textRenderer, Text.literal(name), col1 + 14, rowY + 5, TEXT_COLOR, false);

            // Current value
            context.drawText(textRenderer, Text.literal(row.currentValue), col2, rowY + 5, TEXT_COLOR, false);

            // Recommended value
            context.drawText(textRenderer, Text.literal(row.recommendedValue), col3, rowY + 5, 0xFF3498DB, false);

            // Status badge
            int statusColor;
            String statusText;
            switch (row.status) {
                case MATCH -> { statusColor = MATCH_COLOR; statusText = "Match"; }
                case DIFFERS -> { statusColor = DIFFER_COLOR; statusText = "Differs"; }
                default -> { statusColor = UNKNOWN_COLOR; statusText = "Unknown"; }
            }
            context.drawText(textRenderer, Text.literal(statusText), col4, rowY + 5, statusColor, false);
        }

        context.disableScissor();

        // Scroll indicator
        if (settingRows.size() > maxVisible) {
            int trackHeight = scissorBottom - rowStartY;
            int scrollBarHeight = (int) ((double) maxVisible / settingRows.size() * trackHeight);
            scrollBarHeight = Math.max(scrollBarHeight, 10);
            int maxScroll = Math.max(1, settingRows.size() * rowHeight - maxVisible * rowHeight);
            int scrollBarY = rowStartY + (int) ((double) scrollOffset / maxScroll * (trackHeight - scrollBarHeight));
            scrollBarY = Math.min(scrollBarY, scissorBottom - scrollBarHeight);
            context.fill(x + width - 13, scrollBarY, x + width - 10, scrollBarY + scrollBarHeight, 0xFF555577);
        }
    }

    @Override
    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int rowStartY = y + 18 + 16;
        int rowHeight = 18;
        int maxVisible = (height - 70) / rowHeight;
        if (mouseX < x + 10 || mouseX > x + width - 10) return false;
        if (mouseY < rowStartY || mouseY > rowStartY + maxVisible * rowHeight) return false;

        int clickedIndex = ((int) mouseY - rowStartY + scrollOffset) / rowHeight;
        if (clickedIndex >= 0 && clickedIndex < settingRows.size()) {
            String id = settingRows.get(clickedIndex).id;
            if (selectedSettings.contains(id)) {
                selectedSettings.remove(id);
            } else {
                selectedSettings.add(id);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean handleScroll(double mouseX, double mouseY, double verticalAmount) {
        int rowHeight = 18;
        int maxVisible = (height - 70) / rowHeight;
        int maxScroll = Math.max(0, settingRows.size() * rowHeight - maxVisible * rowHeight);
        scrollOffset -= (int) (verticalAmount * rowHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        return true;
    }

    @Override
    public void tick() {
        // Refresh current values periodically
    }

    private void buildSettingRows() {
        settingRows.clear();

        // Build rows from all registered settings
        // Since SettingsRegistry may not expose a getAll() method, we create
        // representative rows from what we know the system manages
        addSettingRow("render_distance", "Render Distance", "Rendering");
        addSettingRow("simulation_distance", "Simulation Distance", "Rendering");
        addSettingRow("graphics_mode", "Graphics Mode", "Rendering");
        addSettingRow("smooth_lighting", "Smooth Lighting", "Rendering");
        addSettingRow("max_fps", "Max Framerate", "Performance");
        addSettingRow("vsync", "VSync", "Performance");
        addSettingRow("biome_blend", "Biome Blend Radius", "Rendering");
        addSettingRow("entity_shadows", "Entity Shadows", "Rendering");
        addSettingRow("particles", "Particles", "Rendering");
        addSettingRow("mipmap_levels", "Mipmap Levels", "Rendering");
        addSettingRow("clouds", "Cloud Render Mode", "Rendering");
        addSettingRow("fov", "Field of View", "Display");
        addSettingRow("entity_distance", "Entity Distance", "Rendering");
        addSettingRow("gamma", "Brightness", "Display");
        addSettingRow("fullscreen", "Fullscreen", "Display");
        addSettingRow("gui_scale", "GUI Scale", "Display");
        addSettingRow("view_bobbing", "View Bobbing", "Display");
        addSettingRow("attack_indicator", "Attack Indicator", "Display");
        addSettingRow("fov_effects", "FOV Effects", "Display");
        addSettingRow("screen_effects", "Distortion Effects", "Display");
        addSettingRow("darkness_effects", "Darkness Pulsing", "Display");
    }

    private void addSettingRow(String id, String name, String category) {
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        var adapter = mod.getPlatformAdapter();
        String currentValue;
        String recommendedValue;
        SettingStatus status;

        try {
            currentValue = readCurrentValue(id, adapter);
            recommendedValue = getRecommendedValue(id);
            status = currentValue.equals(recommendedValue) ? SettingStatus.MATCH : SettingStatus.DIFFERS;
        } catch (Exception e) {
            currentValue = "?";
            recommendedValue = "?";
            status = SettingStatus.UNKNOWN;
        }

        settingRows.add(new SettingRow(id, name, category, currentValue, recommendedValue, status));
    }

    private String readCurrentValue(String id, com.autotune.platform.PlatformAdapter adapter) {
        return switch (id) {
            case "render_distance" -> String.valueOf(adapter.getRenderDistance());
            case "simulation_distance" -> String.valueOf(adapter.getSimulationDistance());
            case "graphics_mode" -> adapter.getGraphicsMode().toString();
            case "smooth_lighting" -> adapter.getSmoothLighting() ? "On" : "Off";
            case "max_fps" -> String.valueOf(adapter.getMaxFps());
            case "vsync" -> adapter.getVsync() ? "On" : "Off";
            case "biome_blend" -> String.valueOf(adapter.getBiomeBlendRadius());
            case "entity_shadows" -> adapter.getEntityShadows() ? "On" : "Off";
            case "particles" -> switch (adapter.getParticles()) { case 0 -> "All"; case 1 -> "Decreased"; default -> "Minimal"; };
            case "mipmap_levels" -> String.valueOf(adapter.getMipmapLevels());
            case "clouds" -> switch (adapter.getCloudRenderMode()) { case 0 -> "Off"; case 1 -> "Fast"; default -> "Fancy"; };
            case "fov" -> String.valueOf(adapter.getFov());
            case "entity_distance" -> String.format("%.0f%%", adapter.getEntityDistanceScaling() * 100);
            case "gamma" -> String.format("%.0f%%", adapter.getGamma() * 100);
            case "fullscreen" -> adapter.getFullscreen() ? "On" : "Off";
            case "gui_scale" -> adapter.getGuiScale() == 0 ? "Auto" : String.valueOf(adapter.getGuiScale());
            case "view_bobbing" -> adapter.getViewBobbing() ? "On" : "Off";
            case "attack_indicator" -> switch (adapter.getAttackIndicator()) { case 0 -> "Off"; case 1 -> "Crosshair"; default -> "Hotbar"; };
            case "fov_effects" -> String.format("%.0f%%", adapter.getFovEffectScale() * 100);
            case "screen_effects" -> String.format("%.0f%%", adapter.getScreenEffectScale() * 100);
            case "darkness_effects" -> String.format("%.0f%%", adapter.getDarknessEffectScale() * 100);
            default -> "?";
        };
    }

    private String getRecommendedValue(String id) {
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return "?";
        AutoTuneConfig config = mod.getConfig();
        int target = config.getTargetFps();
        // Provide sensible recommendations based on target FPS
        return switch (id) {
            case "render_distance" -> target >= 120 ? "8" : target >= 60 ? "12" : "16";
            case "simulation_distance" -> target >= 120 ? "8" : target >= 60 ? "10" : "12";
            case "graphics_mode" -> target >= 120 ? "FAST" : "FANCY";
            case "smooth_lighting", "fullscreen", "view_bobbing" -> "On";
            case "max_fps" -> String.valueOf(target);
            case "vsync" -> "Off";
            case "biome_blend" -> target >= 120 ? "1" : target >= 60 ? "3" : "5";
            case "entity_shadows" -> target >= 120 ? "Off" : "On";
            case "particles" -> target >= 120 ? "Decreased" : "All";
            case "mipmap_levels" -> "4";
            case "clouds" -> target >= 120 ? "Off" : "Fast";
            case "fov" -> "70";
            case "entity_distance" -> target >= 120 ? "75%" : "100%";
            case "gamma" -> "50%";
            case "gui_scale" -> "Auto";
            case "attack_indicator" -> "Crosshair";
            case "fov_effects", "screen_effects", "darkness_effects" -> "100%";
            default -> "?";
        };
    }

    private void applyAll() {
        MinecraftClient client = parent.getClient();
        if (client != null) {
            // Build diff list and open preview
            List<SettingsPreviewScreen.SettingChange> changes = new ArrayList<>();
            for (SettingRow row : settingRows) {
                if (row.status == SettingStatus.DIFFERS) {
                    changes.add(new SettingsPreviewScreen.SettingChange(row.name, row.currentValue, row.recommendedValue));
                }
            }
            if (changes.isEmpty()) {
                if (parent.getToast() != null) {
                    parent.getToast().showInfo("All settings already match recommendations");
                }
            } else {
                client.setScreen(new SettingsPreviewScreen(parent, changes));
            }
        }
    }

    private void applySelected() {
        if (selectedSettings.isEmpty()) {
            if (parent.getToast() != null) {
                parent.getToast().showInfo("No settings selected");
            }
            return;
        }

        MinecraftClient client = parent.getClient();
        if (client != null) {
            List<SettingsPreviewScreen.SettingChange> changes = new ArrayList<>();
            for (SettingRow row : settingRows) {
                if (selectedSettings.contains(row.id) && row.status == SettingStatus.DIFFERS) {
                    changes.add(new SettingsPreviewScreen.SettingChange(row.name, row.currentValue, row.recommendedValue));
                }
            }
            if (changes.isEmpty()) {
                if (parent.getToast() != null) {
                    parent.getToast().showInfo("Selected settings already match recommendations");
                }
            } else {
                client.setScreen(new SettingsPreviewScreen(parent, changes));
            }
        }
    }

    private void resetToDefaults() {
        if (parent.getToast() != null) {
            parent.getToast().showWarning("Reset to vanilla defaults - restart required");
        }
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        var adapter = mod.getPlatformAdapter();
        adapter.setRenderDistance(12);
        adapter.setSimulationDistance(12);
        adapter.setSmoothLighting(true);
        adapter.setVsync(true);
        adapter.setBiomeBlendRadius(2);
        adapter.setEntityShadows(true);
        adapter.setParticles(0);
        adapter.setMipmapLevels(4);
        adapter.setCloudRenderMode(2);

        buildSettingRows();
    }

    private void drawBorder(DrawContext context, int bx, int by, int bw, int bh, int color) {
        context.fill(bx, by, bx + bw, by + 1, color);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        context.fill(bx, by, bx + 1, by + bh, color);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }

    private enum SettingStatus { MATCH, DIFFERS, UNKNOWN }

    private record SettingRow(String id, String name, String category,
                              String currentValue, String recommendedValue, SettingStatus status) {}
}
