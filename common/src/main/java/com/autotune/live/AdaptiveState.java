package com.autotune.live;

public enum AdaptiveState {
    STABLE("Stable", "\u2705"),
    DEGRADING("Degrading", "\u26A0"),
    RECOVERING("Recovering", "\u21BB"),
    BOOSTING("Boosting", "\u2191"),
    EMERGENCY("Emergency", "\u26A0\uFE0F"),
    LOCKED("Locked", "\uD83D\uDD12");

    private final String displayName;
    private final String icon;

    AdaptiveState(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }
}
