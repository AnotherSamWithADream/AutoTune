package com.autotune.ui.screens;

import com.autotune.AutoTuneMod;
import com.autotune.profile.PerformanceProfile;
import com.autotune.profile.ProfileManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for creating a new performance profile or editing an existing one.
 * Provides a name text field, a "based on" dropdown selector (prev/next buttons)
 * for choosing an existing profile or questionnaire results,
 * a description field, and a list of current setting overrides.
 */
public class ProfileEditScreen extends Screen {

    private static final int BG_COLOR = 0xFF0F0F23;
    private static final int CARD_BG = 0xFF16213E;
    private static final int CARD_BORDER = 0xFF333355;
    private static final int TITLE_COLOR = 0xFF3498DB;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int DIM_COLOR = 0xFF888888;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int ERROR_COLOR = 0xFFE74C3C;
    private static final int VALUE_COLOR = 0xFF2ECC71;
    private static final int ROW_BG_EVEN = 0xFF1A1A2E;
    private static final int ROW_BG_ODD = 0xFF1E1E36;
    private static final int HEADER_BG = 0xFF252542;

    private final Screen parent;
    private final String existingName;
    private final boolean isEditing;
    private PerformanceProfile existingProfile;

    private TextFieldWidget nameField;
    private TextFieldWidget descriptionField;
    private int basedOnIndex;
    private final List<String> basedOnOptions = new ArrayList<>();
    private final List<OverrideEntry> overrides = new ArrayList<>();
    private String errorMessage;
    private int scrollOffset;

    /**
     * Constructor for editing an existing profile by reference.
     */
    public ProfileEditScreen(Screen parent, PerformanceProfile existingProfile) {
        super(Text.literal(existingProfile != null ? "Edit Profile" : "Create Profile"));
        this.parent = parent;
        this.existingProfile = existingProfile;
        this.existingName = existingProfile != null ? existingProfile.getName() : null;
        this.isEditing = existingProfile != null;
    }

    /**
     * Constructor for editing an existing profile by name.
     */
    public ProfileEditScreen(Screen parent, String profileName) {
        super(Text.literal(profileName != null ? "Edit Profile" : "Create Profile"));
        this.parent = parent;
        this.existingName = profileName;
        this.isEditing = profileName != null;

        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (profileName != null && mod != null) {
            ProfileManager pm = mod.getProfileManager();
            for (PerformanceProfile p : pm.getProfiles()) {
                if (p.getName().equals(profileName)) {
                    this.existingProfile = p;
                    break;
                }
            }
        }
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int formWidth = Math.min(340, width - 40);
        int formX = centerX - formWidth / 2;

        // Build based-on options
        basedOnOptions.clear();
        basedOnOptions.add("(Questionnaire Results)");
        basedOnOptions.add("default");
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod initMod = AutoTuneMod.getInstance();
        if (initMod != null) {
            ProfileManager pm = initMod.getProfileManager();
            for (PerformanceProfile p : pm.getProfiles()) {
                if (!basedOnOptions.contains(p.getName())) {
                    basedOnOptions.add(p.getName());
                }
            }
        }
        basedOnIndex = 0;

        int fieldX = formX + 80;
        int fieldWidth = formWidth - 90;

        // Name text field
        nameField = new TextFieldWidget(textRenderer, fieldX, 62, fieldWidth, 18, Text.literal("Name"));
        nameField.setMaxLength(64);
        nameField.setEditableColor(0xFFFFFFFF);
        if (existingProfile != null) {
            nameField.setText(existingProfile.getName());
        }
        nameField.setChangedListener(text -> errorMessage = null);
        addDrawableChild(nameField);

        // Based-on selector buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("<"),
                btn -> basedOnIndex = (basedOnIndex - 1 + basedOnOptions.size()) % basedOnOptions.size()
        ).dimensions(fieldX, 92, 20, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(">"),
                btn -> basedOnIndex = (basedOnIndex + 1) % basedOnOptions.size()
        ).dimensions(fieldX + fieldWidth - 20, 92, 20, 18).build());

        // Description text field
        descriptionField = new TextFieldWidget(textRenderer, fieldX, 122, fieldWidth, 18, Text.literal("Description"));
        descriptionField.setMaxLength(200);
        descriptionField.setEditableColor(0xFFFFFFFF);
        if (existingProfile != null && existingProfile.getDescription() != null) {
            descriptionField.setText(existingProfile.getDescription());
        }
        addDrawableChild(descriptionField);

        // Build overrides list
        buildOverrides();

        // Save / Cancel buttons
        int btnY = height - 34;
        int btnWidth = (formWidth - 20) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Save Profile"), btn -> saveProfile())
                .dimensions(formX, btnY, btnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> close())
                .dimensions(formX + btnWidth + 10, btnY, btnWidth, 20).build());

        setInitialFocus(nameField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Full background
        context.fill(0, 0, width, height, BG_COLOR);

        int centerX = width / 2;
        int formWidth = Math.min(340, width - 40);
        int formX = centerX - formWidth / 2;

        // Card background
        context.fill(formX - 4, 38, formX + formWidth + 4, height - 42, CARD_BG);
        drawBorder(context, formX - 4, 38, formWidth + 8, height - 80, CARD_BORDER);

        // Title
        String titleText = isEditing ? "Edit Profile" : "Create New Profile";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(titleText), centerX, 14, TITLE_COLOR);

        // Form labels
        context.drawText(textRenderer, Text.literal("Name:"), formX + 4, 66, LABEL_COLOR, false);
        context.drawText(textRenderer, Text.literal("Based On:"), formX + 4, 96, LABEL_COLOR, false);
        context.drawText(textRenderer, Text.literal("Notes:"), formX + 4, 126, LABEL_COLOR, false);

        // Based-on selector value display
        int fieldX = formX + 80;
        int fieldWidth = formWidth - 90;
        String basedOnText = basedOnOptions.get(basedOnIndex);
        // Truncate by pixel width to fit between the < > buttons
        int selectorInnerW = fieldWidth - 48;
        while (basedOnText.length() > 3 && textRenderer.getWidth(basedOnText) > selectorInnerW) {
            basedOnText = basedOnText.substring(0, basedOnText.length() - 1);
        }
        if (basedOnText.length() < basedOnOptions.get(basedOnIndex).length()) basedOnText += "...";
        context.fill(fieldX + 22, 92, fieldX + fieldWidth - 22, 110, 0xFF111128);
        int textW = textRenderer.getWidth(basedOnText);
        int textCenterX = fieldX + 22 + (fieldWidth - 44 - textW) / 2;
        context.drawText(textRenderer, Text.literal(basedOnText), textCenterX, 96, TEXT_COLOR, false);

        // Setting Overrides section
        int overridesLabelY = 148;
        context.drawText(textRenderer, Text.literal("Setting Overrides"), formX + 4, overridesLabelY, TITLE_COLOR, false);

        // Column headers
        int headerY = overridesLabelY + 14;
        context.fill(formX, headerY, formX + formWidth, headerY + 12, HEADER_BG);
        context.drawText(textRenderer, Text.literal("Setting"), formX + 6, headerY + 2, DIM_COLOR, false);
        context.drawText(textRenderer, Text.literal("Value"), formX + formWidth - 80, headerY + 2, DIM_COLOR, false);

        // Override rows
        int rowStartY = headerY + 14;
        int rowHeight = 14;
        int maxVisibleRows = (height - 50 - rowStartY) / rowHeight;

        context.enableScissor(formX, rowStartY, formX + formWidth, rowStartY + maxVisibleRows * rowHeight);

        for (int i = 0; i < overrides.size(); i++) {
            int rowY = rowStartY + i * rowHeight - scrollOffset;
            if (rowY + rowHeight < rowStartY || rowY > rowStartY + maxVisibleRows * rowHeight) continue;

            OverrideEntry entry = overrides.get(i);
            boolean hovered = mouseX >= formX && mouseX < formX + formWidth
                    && mouseY >= rowY && mouseY < rowY + rowHeight
                    && mouseY >= rowStartY && mouseY < rowStartY + maxVisibleRows * rowHeight;

            int bgColor = hovered ? 0xFF252542 : (i % 2 == 0 ? ROW_BG_EVEN : ROW_BG_ODD);
            context.fill(formX, rowY, formX + formWidth, rowY + rowHeight, bgColor);
            context.fill(formX, rowY + rowHeight - 1, formX + formWidth, rowY + rowHeight, CARD_BORDER);

            context.drawText(textRenderer, Text.literal(entry.name), formX + 6, rowY + 2, TEXT_COLOR, false);
            context.drawText(textRenderer, Text.literal(entry.value), formX + formWidth - 80, rowY + 2, VALUE_COLOR, false);
        }

        context.disableScissor();

        // Error message
        if (errorMessage != null) {
            int errWidth = textRenderer.getWidth(errorMessage);
            context.drawText(textRenderer, Text.literal(errorMessage),
                    centerX - errWidth / 2, height - 48, ERROR_COLOR, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    // No @Override - signature varies between MC versions (3 args in 1.21, 4 args in 1.21.4+)
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, overrides.size() * 14 - (height - 240));
        scrollOffset -= (int) (verticalAmount * 14);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);
        return true;
    }

    // No @Override - signature varies between MC versions
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257) {
            saveProfile();
            return true;
        }
        if (keyCode == 256) {
            close();
            return true;
        }
        return false;
    }

    private void buildOverrides() {
        overrides.clear();
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        var adapter = mod.getPlatformAdapter();

        overrides.add(new OverrideEntry("Render Distance", String.valueOf(adapter.getRenderDistance())));
        overrides.add(new OverrideEntry("Simulation Distance", String.valueOf(adapter.getSimulationDistance())));
        overrides.add(new OverrideEntry("Graphics Mode", adapter.getGraphicsMode().toString()));
        overrides.add(new OverrideEntry("Smooth Lighting", adapter.getSmoothLighting() ? "On" : "Off"));
        overrides.add(new OverrideEntry("Max Framerate", String.valueOf(adapter.getMaxFps())));
        overrides.add(new OverrideEntry("VSync", adapter.getVsync() ? "On" : "Off"));
        overrides.add(new OverrideEntry("Biome Blend", String.valueOf(adapter.getBiomeBlendRadius())));
        overrides.add(new OverrideEntry("Entity Shadows", adapter.getEntityShadows() ? "On" : "Off"));
        overrides.add(new OverrideEntry("Particles", switch (adapter.getParticles()) {
            case 0 -> "All"; case 1 -> "Decreased"; default -> "Minimal";
        }));
        overrides.add(new OverrideEntry("Mipmap Levels", String.valueOf(adapter.getMipmapLevels())));
        overrides.add(new OverrideEntry("Clouds", switch (adapter.getCloudRenderMode()) {
            case 0 -> "Off"; case 1 -> "Fast"; default -> "Fancy";
        }));
        overrides.add(new OverrideEntry("Field of View", String.valueOf(adapter.getFov())));
        overrides.add(new OverrideEntry("Entity Distance", String.format("%.0f%%", adapter.getEntityDistanceScaling() * 100)));
        overrides.add(new OverrideEntry("Brightness", String.format("%.0f%%", adapter.getGamma() * 100)));
    }

    private void saveProfile() {
        String name = nameField.getText().trim();
        String description = descriptionField.getText().trim();

        if (name.isEmpty()) {
            errorMessage = "Profile name cannot be empty.";
            return;
        }

        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) {
            errorMessage = "AutoTune not initialized.";
            return;
        }

        ProfileManager pm = mod.getProfileManager();

        if (isEditing && existingName != null) {
            if (!name.equals(existingName)) {
                for (PerformanceProfile p : pm.getProfiles()) {
                    if (p.getName().equals(name)) {
                        errorMessage = "A profile with this name already exists.";
                        return;
                    }
                }
                pm.deleteProfile(existingName);
            }

            PerformanceProfile profile = existingProfile != null ? existingProfile : new PerformanceProfile();
            profile.setName(name);
            profile.setDescription(description);
            pm.saveProfile(profile);
        } else {
            for (PerformanceProfile p : pm.getProfiles()) {
                if (p.getName().equals(name)) {
                    errorMessage = "A profile with this name already exists.";
                    return;
                }
            }

            PerformanceProfile newProfile = new PerformanceProfile();
            newProfile.setName(name);
            newProfile.setDescription(description);
            pm.saveProfile(newProfile);
        }

        close();
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void drawBorder(DrawContext context, int bx, int by, int bw, int bh, int color) {
        context.fill(bx, by, bx + bw, by + 1, color);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        context.fill(bx, by, bx + 1, by + bh, color);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }

    private record OverrideEntry(String name, String value) {}
}
