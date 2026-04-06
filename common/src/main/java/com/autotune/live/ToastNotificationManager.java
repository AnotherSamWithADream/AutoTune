package com.autotune.live;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages non-intrusive toast notifications above the hotbar.
 */
public class ToastNotificationManager {

    // [CODE-REVIEW-FIX] Use CopyOnWriteArrayList for thread-safe show() + render() access
    private final List<ToastEntry> activeToasts = new CopyOnWriteArrayList<>();
    private static final int MAX_VISIBLE = 3;
    private static final long DISPLAY_DURATION_MS = 3000;
    private static final long FADE_DURATION_MS = 500;

    public void show(String message, ToastType type) {
        activeToasts.add(new ToastEntry(message, type, System.currentTimeMillis()));
        if (activeToasts.size() > MAX_VISIBLE + 2) {
            activeToasts.removeFirst();
        }
    }

    public void showSettingChange(String settingName, String oldValue, String newValue, String reason) {
        String msg = String.format("AutoTune: %s %s\u2192%s (%s)", settingName, oldValue, newValue, reason);
        show(msg, ToastType.INFO);
    }

    public void showEmergency(String message) {
        show("AutoTune: " + message, ToastType.EMERGENCY);
    }

    public void render(DrawContext context, int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();

        // Remove expired toasts first (CopyOnWriteArrayList doesn't support Iterator.remove)
        activeToasts.removeIf(entry -> now - entry.timestamp > DISPLAY_DURATION_MS + FADE_DURATION_MS);

        int y = screenHeight - 60; // Above hotbar
        int visibleCount = 0;

        for (ToastEntry entry : activeToasts) {
            long age = now - entry.timestamp;

            if (visibleCount >= MAX_VISIBLE) continue;

            float alpha = 1.0f;
            if (age > DISPLAY_DURATION_MS) {
                alpha = 1.0f - (float) (age - DISPLAY_DURATION_MS) / FADE_DURATION_MS;
            }

            int textColor = entry.type == ToastType.EMERGENCY
                    ? (0x00FF4444 | ((int) (alpha * 255) << 24))
                    : (0x00FFFFFF | ((int) (alpha * 255) << 24));
            int bgColor = (int) (alpha * 160) << 24;

            MinecraftClient client = MinecraftClient.getInstance();
            int textWidth = client.textRenderer.getWidth(entry.message);
            int x = (screenWidth - textWidth) / 2;

            context.fill(x - 4, y - 2, x + textWidth + 4, y + 10, bgColor);
            context.drawText(client.textRenderer, Text.literal(entry.message), x, y, textColor, false);

            y -= 14;
            visibleCount++;
        }
    }

    public void clear() {
        activeToasts.clear();
    }

    public enum ToastType {
        INFO, WARNING, EMERGENCY
    }

    private record ToastEntry(String message, ToastType type, long timestamp) {}
}
