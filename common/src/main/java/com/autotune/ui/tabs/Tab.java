package com.autotune.ui.tabs;

import com.autotune.ui.AutoTuneMainScreen;
import net.minecraft.client.gui.DrawContext;

/**
 * Interface for all tabs displayed in the AutoTune main screen.
 * Each tab manages its own widgets, layout, and rendering within
 * its allocated content area.
 */
public interface Tab {

    /**
     * Returns the display name shown on the tab bar.
     */
    String getName();

    /**
     * Initializes the tab's widgets and layout within the given content area.
     *
     * @param parent the parent screen that hosts this tab
     * @param x      left edge of the content area
     * @param y      top edge of the content area
     * @param width  width of the content area
     * @param height height of the content area
     */
    void init(AutoTuneMainScreen parent, int x, int y, int width, int height);

    /**
     * Renders the tab content each frame.
     *
     * @param context draw context for rendering
     * @param mouseX  current mouse x position
     * @param mouseY  current mouse y position
     * @param delta   partial tick delta
     */
    void render(DrawContext context, int mouseX, int mouseY, float delta);

    /**
     * Called every client tick while this tab is active.
     */
    void tick();

    /**
     * Handles a mouse click within the tab content area.
     *
     * @param mouseX mouse x position
     * @param mouseY mouse y position
     * @param button mouse button (0=left, 1=right, 2=middle)
     * @return true if the click was consumed
     */
    default boolean handleClick(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handles mouse scroll within the tab content area.
     *
     * @param mouseX          mouse x position
     * @param mouseY          mouse y position
     * @param verticalAmount  scroll amount (positive = up)
     * @return true if the scroll was consumed
     */
    default boolean handleScroll(double mouseX, double mouseY, double verticalAmount) {
        return false;
    }
}
