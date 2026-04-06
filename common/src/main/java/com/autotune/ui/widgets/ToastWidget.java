package com.autotune.ui.widgets;

import com.autotune.AutoTuneMod;
import com.autotune.live.ToastNotificationManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;

/**
 * Toast notification widget that delegates rendering to the
 * ToastNotificationManager from the live adaptive engine.
 * Placed in screen contexts to display non-intrusive notifications
 * about setting changes, benchmark progress, and status updates.
 */
public class ToastWidget implements Drawable {

    private int screenWidth;
    private int screenHeight;

    public ToastWidget(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public void setScreenSize(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /**
     * Shows a toast notification through the live engine's toast manager.
     */
    public void show(String message, ToastNotificationManager.ToastType type) {
        ToastNotificationManager manager = getManager();
        if (manager != null) {
            manager.show(message, type);
        }
    }

    /**
     * Shows an informational toast notification.
     */
    public void showInfo(String message) {
        show(message, ToastNotificationManager.ToastType.INFO);
    }

    /**
     * Shows a warning toast notification.
     */
    public void showWarning(String message) {
        show(message, ToastNotificationManager.ToastType.WARNING);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ToastNotificationManager manager = getManager();
        if (manager != null) {
            manager.render(context, screenWidth, screenHeight);
        }
    }

    private ToastNotificationManager getManager() {
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod != null && mod.getLiveEngine() != null) {
            return mod.getLiveEngine().getToastManager();
        }
        return null;
    }
}
