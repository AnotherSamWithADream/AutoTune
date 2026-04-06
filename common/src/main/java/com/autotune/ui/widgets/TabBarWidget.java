package com.autotune.ui.widgets;

import com.autotune.AutoTuneLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Horizontal tab bar with clickable tabs, active tab highlighting,
 * and an onTabSelected callback.
 */
public class TabBarWidget extends ClickableWidget {

    private static final int INACTIVE_BG = 0xFF1A1A2E;
    private static final int ACTIVE_BG = 0xFF16213E;
    private static final int HOVER_BG = 0xFF1F2B47;
    private static final int TAB_BORDER = 0xFF333355;
    private static final int ACTIVE_ACCENT = 0xFF3498DB;
    private static final int INACTIVE_TEXT = 0xFF999999;
    private static final int ACTIVE_TEXT = 0xFFFFFFFF;
    private static final int HOVER_TEXT = 0xFFCCCCCC;
    private static final int BAR_BG = 0xFF0F0F23;

    private final List<String> tabNames;
    private int activeIndex;
    private Consumer<Integer> onTabSelected;
    private final List<TabRect> tabRects;

    // Track last known mouse position from render for click detection
    private double lastMouseX, lastMouseY;

    public TabBarWidget(int x, int y, int width, int height, List<String> tabNames) {
        super(x, y, width, height, Text.literal("Tab Bar"));
        this.tabNames = new ArrayList<>(tabNames);
        this.activeIndex = 0;
        this.tabRects = new ArrayList<>();
    }

    public void setOnTabSelected(Consumer<Integer> callback) {
        this.onTabSelected = callback;
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    public void setActiveIndex(int index) {
        if (index >= 0 && index < tabNames.size()) {
            this.activeIndex = index;
        }
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int barX = getX();
        int barY = getY();
        int barW = getWidth();
        int barH = getHeight();

        context.fill(barX, barY, barX + barW, barY + barH, BAR_BG);
        context.fill(barX, barY + barH - 1, barX + barW, barY + barH, TAB_BORDER);

        tabRects.clear();

        int tabCount = tabNames.size();
        if (tabCount == 0) return;

        int tabWidth = barW / tabCount;
        int remainder = barW - (tabWidth * tabCount);

        int currentX = barX;
        for (int i = 0; i < tabCount; i++) {
            int thisTabWidth = tabWidth + (i < remainder ? 1 : 0);
            boolean isActive = (i == activeIndex);
            boolean isHovered = mouseX >= currentX && mouseX < currentX + thisTabWidth
                    && mouseY >= barY && mouseY < barY + barH;

            tabRects.add(new TabRect(currentX, barY, thisTabWidth, barH, i));

            int bgColor = isActive ? ACTIVE_BG : isHovered ? HOVER_BG : INACTIVE_BG;
            context.fill(currentX, barY, currentX + thisTabWidth, barY + barH - 1, bgColor);

            if (isActive) {
                context.fill(currentX, barY + barH - 3, currentX + thisTabWidth, barY + barH - 1, ACTIVE_ACCENT);
            }

            if (i < tabCount - 1) {
                context.fill(currentX + thisTabWidth - 1, barY + 2, currentX + thisTabWidth, barY + barH - 3, TAB_BORDER);
            }

            String name = tabNames.get(i);
            int textWidth = textRenderer.getWidth(name);
            int textX = currentX + (thisTabWidth - textWidth) / 2;
            int textY = barY + (barH - 8) / 2;
            int textColor = isActive ? ACTIVE_TEXT : isHovered ? HOVER_TEXT : INACTIVE_TEXT;
            context.drawText(textRenderer, Text.literal(name), textX, textY, textColor, false);

            currentX += thisTabWidth;
        }
    }

    /**
     * Public entry point for poll-based click detection from AutoTuneMainScreen.tick().
     * This bypasses Minecraft's widget event system entirely, ensuring it works across all versions.
     */
    public boolean handleClickAt(double mouseX, double mouseY) {
        return handleTabClick(mouseX, mouseY);
    }

    /**
     * Core tab click logic.
     */
    private boolean handleTabClick(double mouseX, double mouseY) {
        for (TabRect rect : tabRects) {
            if (mouseX >= rect.x && mouseX < rect.x + rect.width
                    && mouseY >= rect.y && mouseY < rect.y + rect.height) {
                if (rect.index != activeIndex) {
                    AutoTuneLogger.debug("Tab clicked: {} -> {}", activeIndex, rect.index);
                    activeIndex = rect.index;
                    if (onTabSelected != null) {
                        onTabSelected.accept(activeIndex);
                    }
                }
                return true;
            }
        }
        return false;
    }

    // --- Cross-version click handling ---
    // MC 1.21.0-1.21.10: mouseClicked(double, double, int) and onClick(double, double)
    // MC 1.21.11: mouseClicked(Click, boolean) and onClick(Click, boolean)
    // We provide BOTH signatures. Java will use whichever matches the parent class.

    // Old API (1.21.0-1.21.10)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.active && this.visible) {
            return handleTabClick(mouseX, mouseY);
        }
        return false;
    }

    public void onClick(double mouseX, double mouseY) {
        handleTabClick(mouseX, mouseY);
    }

    // New API (1.21.11+) — uses Click object. We handle it via reflection-safe overloads.
    // The Click class may not exist in older versions, so we can't reference it directly.
    // Instead, we override using Object parameter matching via a catch-all.
    // ClickableWidget in 1.21.11 calls onClick(Click, boolean) which we intercept here.
    public boolean mouseClicked(Object clickOrMouseX, Object buttonOrBoolean) {
        // This catches the new Click-based API
        try {
            // Extract mouse position from the Click object via reflection
            double mx = lastMouseX;
            double my = lastMouseY;
            if (clickOrMouseX != null && !(clickOrMouseX instanceof Double)) {
                // It's a Click object — extract x and y
                Class<?> clickClass = clickOrMouseX.getClass();
                try {
                    Method xMethod = clickClass.getMethod("x");
                    Method yMethod = clickClass.getMethod("y");
                    mx = ((Number) xMethod.invoke(clickOrMouseX)).doubleValue();
                    my = ((Number) yMethod.invoke(clickOrMouseX)).doubleValue();
                } catch (Exception e) {
                    // Fallback to last known mouse position from render
                }
            }
            if (this.active && this.visible) {
                return handleTabClick(mx, my);
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void onClick(Object clickOrMouseX, Object buttonOrBoolean) {
        try {
            double mx = lastMouseX;
            double my = lastMouseY;
            if (clickOrMouseX != null && !(clickOrMouseX instanceof Double)) {
                Class<?> clickClass = clickOrMouseX.getClass();
                try {
                    Method xMethod = clickClass.getMethod("x");
                    Method yMethod = clickClass.getMethod("y");
                    mx = ((Number) xMethod.invoke(clickOrMouseX)).doubleValue();
                    my = ((Number) yMethod.invoke(clickOrMouseX)).doubleValue();
                } catch (Exception ignored) {}
            }
            handleTabClick(mx, my);
        } catch (Exception ignored) {}
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        if (activeIndex >= 0 && activeIndex < tabNames.size()) {
            builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE,
                    Text.literal("Tab: " + tabNames.get(activeIndex)));
        }
    }

    private record TabRect(int x, int y, int width, int height, int index) {}
}
