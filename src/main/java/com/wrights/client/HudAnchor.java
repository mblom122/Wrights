package com.wrights.client;

import net.minecraft.util.Mth;

import com.wrights.WrightsClientConfig;

/** Where a hint stack or a toast stack is drawn on the screen. */
public final class HudAnchor {
    public static final int TOP = 0;
    public static final int BOTTOM = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;
    public static final int CENTER = 4;
    public static final int CUSTOM = 5;
    /** Leaves the vanilla action-bar text and the hotbar clear. */
    public static final int HOTBAR_CLEARANCE = 70;

    private HudAnchor() {
    }

    public static int hintSpot() {
        return Mth.clamp(WrightsClientConfig.HUD_HINT.get(), TOP, CUSTOM);
    }

    public static int toastSpot() {
        return Mth.clamp(WrightsClientConfig.HUD_TOAST.get(), TOP, CUSTOM);
    }

    public static int x(int spot, int custom, int screen, int size) {
        int raw = switch (spot) {
            case LEFT -> 6;
            case RIGHT -> screen - 6 - size;
            case CUSTOM -> custom * screen / 1000 - size / 2;
            default -> (screen - size) / 2;
        };
        return Mth.clamp(raw, 2, Math.max(2, screen - size - 2));
    }

    public static int y(int spot, int custom, int screen, int size) {
        int raw = switch (spot) {
            case TOP -> 6;
            case BOTTOM -> screen - HOTBAR_CLEARANCE - size;
            case CUSTOM -> custom * screen / 1000 - size / 2;
            default -> (screen - size) / 2;
        };
        return Mth.clamp(raw, 2, Math.max(2, screen - size - 2));
    }

    public static int hintX(int screen, int size) {
        return x(hintSpot(), WrightsClientConfig.HUD_HINT_X.get(), screen, size);
    }

    public static int hintY(int screen, int size) {
        return y(hintSpot(), WrightsClientConfig.HUD_HINT_Y.get(), screen, size);
    }

    public static int toastX(int screen, int size) {
        return x(toastSpot(), WrightsClientConfig.HUD_TOAST_X.get(), screen, size);
    }

    public static int toastY(int screen, int size) {
        return y(toastSpot(), WrightsClientConfig.HUD_TOAST_Y.get(), screen, size);
    }

    public static int modeSpot() {
        return Mth.clamp(WrightsClientConfig.HUD_MODE.get(), TOP, CUSTOM);
    }

    public static int modeX(int screen, int size) {
        return x(modeSpot(), WrightsClientConfig.HUD_MODE_X.get(), screen, size);
    }

    /**
     * Left/right sit above the hotbar on that side so Shift/Ctrl menus clear the action bar.
     * Other spots match hint/toast placement.
     */
    public static int modeY(int screen, int size) {
        int spot = modeSpot();
        if (spot == LEFT || spot == RIGHT) {
            return Mth.clamp(screen - HOTBAR_CLEARANCE - size, 2, Math.max(2, screen - size - 2));
        }
        return y(spot, WrightsClientConfig.HUD_MODE_Y.get(), screen, size);
    }

    public static int fraction(int center, int screen) {
        if (screen <= 0) {
            return 500;
        }
        return Mth.clamp(center * 1000 / screen, 0, 1000);
    }
}
