package com.autotune.ui;

import com.autotune.AutoTuneLogger;
import com.autotune.ui.tabs.*;
import com.autotune.ui.widgets.TabBarWidget;
import com.autotune.ui.widgets.ToastWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The main AutoTune configuration screen, accessible via F10.
 * Contains a tab bar across the top with seven tabs: Dashboard, Benchmark,
 * Preferences, Settings, Live Mode, Profiles, and Advanced.
 * Only the active tab's content is rendered and ticked.
 */
public class AutoTuneMainScreen extends Screen {

    private static final int HEADER_HEIGHT = 30;
    private static final int TAB_BAR_HEIGHT = 22;
    private static final int TITLE_COLOR = 0xFF3498DB;
    private static final int BG_COLOR = 0xFF0F0F23;
    private static final int CONTENT_BG = 0xFF16213E;
    private static final int FOOTER_COLOR = 0xFF0A0A1A;

    private final Screen parent;
    private final List<Tab> tabs;
    private TabBarWidget tabBar;
    private ToastWidget toastWidget;
    private int activeTabIndex;

    private boolean wasMouseDown; // Track previous frame's mouse state for edge detection

    // Content area bounds
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;

    public AutoTuneMainScreen(Screen parent) {
        super(Text.literal("AutoTune"));
        this.parent = parent;
        this.tabs = new ArrayList<>();
        this.activeTabIndex = 0;
    }

    @Override
    protected void init() {
        super.init();

        // Build tab list
        tabs.clear();
        tabs.add(new DashboardTab());
        tabs.add(new BenchmarkTab());
        tabs.add(new PreferencesTab());
        tabs.add(new SettingsTab());
        tabs.add(new LiveModeTab());
        tabs.add(new ProfilesTab());
        tabs.add(new AdvancedTab());

        // Tab bar names
        List<String> tabNames = new ArrayList<>();
        for (Tab tab : tabs) {
            tabNames.add(tab.getName());
        }

        // Layout
        int screenPadding = 10;
        int tabBarY = screenPadding + HEADER_HEIGHT;
        int tabBarWidth = width - screenPadding * 2;

        tabBar = new TabBarWidget(screenPadding, tabBarY, tabBarWidth, TAB_BAR_HEIGHT, tabNames);
        tabBar.setActiveIndex(activeTabIndex);
        tabBar.setOnTabSelected(this::switchTab);
        addDrawableChild(tabBar);

        // Content area below the tab bar
        contentX = screenPadding + 1;
        contentY = tabBarY + TAB_BAR_HEIGHT + 2;
        contentWidth = tabBarWidth - 2;
        contentHeight = height - contentY - 24; // Leave space for footer

        // Toast widget
        toastWidget = new ToastWidget(width, height);

        // Initialize the active tab
        initActiveTab();

        // Close button in top right corner
        int closeButtonWidth = 60;
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                .dimensions(width - screenPadding - closeButtonWidth, screenPadding + 4, closeButtonWidth, 20)
                .build());
    }

    private void switchTab(int newIndex) {
        if (newIndex == activeTabIndex) return;
        activeTabIndex = newIndex;
        tabBar.setActiveIndex(activeTabIndex);
        // Re-init screen to rebuild widgets for new tab
        clearAndInit();
    }

    private void initActiveTab() {
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            tabs.get(activeTabIndex).init(this, contentX, contentY, contentWidth, contentHeight);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Full background
        context.fill(0, 0, width, height, BG_COLOR);

        // Content area background
        context.fill(contentX - 1, contentY - 1, contentX + contentWidth + 1, contentY + contentHeight + 1, CONTENT_BG);

        // Header
        int screenPadding = 10;
        context.drawText(textRenderer, Text.literal("AutoTune"), screenPadding + 4, screenPadding + 8, TITLE_COLOR, true);
        String versionStr = "v1.0.0";
        int versionWidth = textRenderer.getWidth(versionStr);
        context.drawText(textRenderer, Text.literal(versionStr),
                screenPadding + 4 + textRenderer.getWidth("AutoTune") + 8,
                screenPadding + 8, 0xFF666666, false);

        // Footer
        int footerY = height - 20;
        context.fill(0, footerY, width, height, FOOTER_COLOR);
        String footerText = "Press F10 to toggle  |  Tab " + (activeTabIndex + 1) + "/" + tabs.size();
        context.drawText(textRenderer, Text.literal(footerText), screenPadding + 4, footerY + 6, 0xFF555555, false);

        // Render all parent widgets (tab bar, close button, etc.)
        super.render(context, mouseX, mouseY, delta);

        // Render active tab content
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            tabs.get(activeTabIndex).render(context, mouseX, mouseY, delta);
        }

        // Render toast overlay last (on top of everything)
        toastWidget.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();

        // Poll-based tab click detection using GLFW directly — bypasses MC's mouse API entirely
        if (tabBar != null && client != null && client.getWindow() != null) {
            long windowHandle = client.getWindow().getHandle();
            int leftState = org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
            boolean mouseDown = (leftState == org.lwjgl.glfw.GLFW.GLFW_PRESS);

            if (mouseDown && !wasMouseDown) {
                double[] xArr = new double[1], yArr = new double[1];
                org.lwjgl.glfw.GLFW.glfwGetCursorPos(windowHandle, xArr, yArr);
                double scale = client.getWindow().getScaleFactor();
                double mx = xArr[0] / scale;
                double my = yArr[0] / scale;

                AutoTuneLogger.debug("Mouse click at ({}, {}) — tab bar at ({},{} {}x{})",
                    String.format("%.0f", mx), String.format("%.0f", my),
                    tabBar.getX(), tabBar.getY(), tabBar.getWidth(), tabBar.getHeight());

                if (my >= tabBar.getY() && my < tabBar.getY() + tabBar.getHeight()
                        && mx >= tabBar.getX() && mx < tabBar.getX() + tabBar.getWidth()) {
                    tabBar.handleClickAt(mx, my);
                }
            }
            wasMouseDown = mouseDown;
        }

        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            tabs.get(activeTabIndex).tick();
        }
    }

    // Scroll delegation to active tab — no @Override to avoid cross-version signature issues
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            if (tabs.get(activeTabIndex).handleScroll(mouseX, mouseY, verticalAmount)) {
                return true;
            }
        }
        return false;
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

    // --- Public API for tabs to access ---

    /**
     * Adds a drawable child widget to this screen, allowing tabs to register their widgets.
     */
    public <T extends net.minecraft.client.gui.Element & net.minecraft.client.gui.Drawable & net.minecraft.client.gui.Selectable>
    T addTabWidget(T widget) {
        return addDrawableChild(widget);
    }

    /**
     * Returns the text renderer for tabs to use in layout calculations.
     */
    public net.minecraft.client.font.TextRenderer getTextRenderer() {
        return textRenderer;
    }

    /**
     * Returns the toast widget for tabs to show notifications.
     */
    public ToastWidget getToast() {
        return toastWidget;
    }

    /**
     * Returns the underlying MinecraftClient.
     */
    public net.minecraft.client.MinecraftClient getClient() {
        return client;
    }
}
