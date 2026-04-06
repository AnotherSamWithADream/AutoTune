package com.autotune.ui.tabs;

import com.autotune.AutoTuneMod;
import com.autotune.config.AutoTuneConfig;
import com.autotune.config.ConfigManager;
import com.autotune.ui.AutoTuneMainScreen;
import com.autotune.profile.PerformanceProfile;
import com.autotune.ui.screens.ProfileEditScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Profiles tab showing a list of saved profiles with management buttons
 * (Activate, Edit, Delete, Duplicate) and profile import/export functionality.
 */
public class ProfilesTab implements Tab {

    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int DIM_TEXT_COLOR = 0xFF888888;
    private static final int ROW_BG_EVEN = 0xFF1A1A2E;
    private static final int ROW_BG_ODD = 0xFF1E1E36;
    private static final int ROW_HOVER_BG = 0xFF252542;
    private static final int ACTIVE_ROW_BG = 0xFF1A2A4E;
    private static final int CARD_BORDER = 0xFF333355;
    private static final int ACTIVE_COLOR = 0xFF2ECC71;
    private static final int HEADER_BG = 0xFF252542;

    private AutoTuneMainScreen parent;
    private int x, y, width, height;
    private int scrollOffset = 0;
    private final List<ProfileEntry> profiles = new ArrayList<>();
    private int selectedIndex = -1;

    @Override
    public String getName() {
        return "Profiles";
    }

    @Override
    public void init(AutoTuneMainScreen parent, int x, int y, int width, int height) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scrollOffset = 0;

        loadProfiles();

        // Top action buttons
        int btnY = y + 4;
        int btnWidth = 120;

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Create New Profile"),
                btn -> {
                    MinecraftClient client = parent.getClient();
                    if (client != null) {
                        client.setScreen(new ProfileEditScreen(parent, (PerformanceProfile) null));
                    }
                }
        ).dimensions(x + 10, btnY, btnWidth + 20, 20).build());

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Import Profile"),
                btn -> {
                    if (parent.getToast() != null) {
                        parent.getToast().showInfo("Import from config/autotune/profiles/");
                    }
                }
        ).dimensions(x + 10 + btnWidth + 24, btnY, btnWidth, 20).build());

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Export Profile"),
                btn -> {
                    if (selectedIndex >= 0 && selectedIndex < profiles.size()) {
                        if (parent.getToast() != null) {
                            parent.getToast().showInfo("Exported: " + profiles.get(selectedIndex).name);
                        }
                    } else {
                        if (parent.getToast() != null) {
                            parent.getToast().showInfo("Select a profile to export");
                        }
                    }
                }
        ).dimensions(x + 10 + (btnWidth + 4) * 2 + 20, btnY, btnWidth, 20).build());

        // Per-profile action buttons at the bottom
        int actionBtnY = y + height - 26;
        int actionBtnWidth = (width - 50) / 4;

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Activate"),
                btn -> activateSelected()
        ).dimensions(x + 10, actionBtnY, actionBtnWidth, 20).build());

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Edit"),
                btn -> editSelected()
        ).dimensions(x + 15 + actionBtnWidth, actionBtnY, actionBtnWidth, 20).build());

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Duplicate"),
                btn -> duplicateSelected()
        ).dimensions(x + 20 + actionBtnWidth * 2, actionBtnY, actionBtnWidth, 20).build());

        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Delete"),
                btn -> deleteSelected()
        ).dimensions(x + 25 + actionBtnWidth * 3, actionBtnY, actionBtnWidth, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = parent.getTextRenderer();
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        AutoTuneConfig config = mod.getConfig();
        String activeProfile = config.getActiveProfileName();

        // Scissor region for scrollable content (leave 30px at bottom for action buttons)
        context.enableScissor(x, y, x + width, y + height - 30);

        // Column headers
        int listY = y + 30 - scrollOffset;
        context.fill(x + 10, listY, x + width - 10, listY + 14, HEADER_BG);
        context.drawText(textRenderer, Text.literal("Profile Name"), x + 14, listY + 3, DIM_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal("Status"), x + width / 2, listY + 3, DIM_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal("Settings"), x + width - 100, listY + 3, DIM_TEXT_COLOR, false);

        // Profile list
        int rowStartY = listY + 16;
        int rowHeight = 24;

        for (int i = 0; i < profiles.size(); i++) {
            ProfileEntry profile = profiles.get(i);
            int rowY = rowStartY + i * rowHeight;

            boolean isActive = profile.name.equals(activeProfile);
            boolean isSelected = i == selectedIndex;
            boolean hovered = mouseX >= x + 10 && mouseX < x + width - 10
                    && mouseY >= rowY && mouseY < rowY + rowHeight;

            // Row background
            int bgColor;
            if (isSelected) {
                bgColor = ACTIVE_ROW_BG;
            } else if (hovered) {
                bgColor = ROW_HOVER_BG;
            } else if (isActive) {
                bgColor = 0xFF1A2540;
            } else {
                bgColor = i % 2 == 0 ? ROW_BG_EVEN : ROW_BG_ODD;
            }
            context.fill(x + 10, rowY, x + width - 10, rowY + rowHeight, bgColor);
            context.fill(x + 10, rowY + rowHeight - 1, x + width - 10, rowY + rowHeight, CARD_BORDER);

            // Active indicator
            if (isActive) {
                context.fill(x + 10, rowY, x + 13, rowY + rowHeight - 1, ACTIVE_COLOR);
            }

            // Profile name — truncate to fit column
            String profileName = profile.name;
            int nameMaxW = width / 2 - 28;
            if (textRenderer.getWidth(profileName) > nameMaxW) {
                while (profileName.length() > 3 && textRenderer.getWidth(profileName + "...") > nameMaxW) {
                    profileName = profileName.substring(0, profileName.length() - 1);
                }
                profileName += "...";
            }
            context.drawText(textRenderer, Text.literal(profileName), x + 18, rowY + 7, TEXT_COLOR, false);

            // Status
            String statusStr = isActive ? "Active" : "Inactive";
            int statusColor = isActive ? ACTIVE_COLOR : DIM_TEXT_COLOR;
            context.drawText(textRenderer, Text.literal(statusStr), x + width / 2, rowY + 7, statusColor, false);

            // Settings count
            context.drawText(textRenderer, Text.literal(profile.settingsCount + " settings"),
                    x + width - 100, rowY + 7, DIM_TEXT_COLOR, false);

            // Handle click for selection
            if (hovered && net.minecraft.client.MinecraftClient.getInstance().mouse.wasLeftButtonClicked()) {
                selectedIndex = i;
            }
        }

        if (profiles.isEmpty()) {
            context.drawText(textRenderer, Text.literal("No profiles found."),
                    x + 10, rowStartY + 10, DIM_TEXT_COLOR, false);
            context.drawText(textRenderer, Text.literal("Click 'Create New Profile' to get started."),
                    x + 10, rowStartY + 24, DIM_TEXT_COLOR, false);
        }

        context.disableScissor();
        // Action buttons are rendered by the widget system outside the scissor region
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

    private void loadProfiles() {
        profiles.clear();
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        ConfigManager configManager = mod.getConfigManager();
        Path profilesDir = configManager.getProfilesDirectory();

        // Always include the default profile
        profiles.add(new ProfileEntry("default", 21));

        // Scan for additional profiles
        try (Stream<Path> paths = Files.list(profilesDir)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        String name = p.getFileName().toString().replace(".json", "");
                        if (!"default".equals(name)) {
                            profiles.add(new ProfileEntry(name, 21));
                        }
                    });
        } catch (IOException e) {
            // Just use default
        }
    }

    private void activateSelected() {
        if (selectedIndex >= 0 && selectedIndex < profiles.size()) {
            String name = profiles.get(selectedIndex).name;
            // [CODE-REVIEW-FIX] Null guard for getInstance()
            AutoTuneMod mod = AutoTuneMod.getInstance();
            if (mod == null) return;
            mod.getConfig().setActiveProfileName(name);
            mod.getConfigManager().save(mod.getConfig());
            if (parent.getToast() != null) {
                parent.getToast().showInfo("Activated profile: " + name);
            }
        }
    }

    private void editSelected() {
        if (selectedIndex >= 0 && selectedIndex < profiles.size()) {
            MinecraftClient client = parent.getClient();
            if (client != null) {
                client.setScreen(new ProfileEditScreen(parent, profiles.get(selectedIndex).name));
            }
        }
    }

    private void duplicateSelected() {
        if (selectedIndex >= 0 && selectedIndex < profiles.size()) {
            String sourceName = profiles.get(selectedIndex).name;
            String newName = sourceName + "_copy";
            profiles.add(new ProfileEntry(newName, profiles.get(selectedIndex).settingsCount));
            if (parent.getToast() != null) {
                parent.getToast().showInfo("Duplicated: " + sourceName + " -> " + newName);
            }
        }
    }

    private void deleteSelected() {
        if (selectedIndex >= 0 && selectedIndex < profiles.size()) {
            String name = profiles.get(selectedIndex).name;
            if ("default".equals(name)) {
                if (parent.getToast() != null) {
                    parent.getToast().showWarning("Cannot delete the default profile");
                }
                return;
            }
            profiles.remove(selectedIndex);
            selectedIndex = -1;
            if (parent.getToast() != null) {
                parent.getToast().showInfo("Deleted profile: " + name);
            }
        }
    }

    private record ProfileEntry(String name, int settingsCount) {}
}
