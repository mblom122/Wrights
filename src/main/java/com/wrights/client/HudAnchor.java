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
    /** Vanilla hotbar width; used to park Ctrl/Shift menus beside it. */
    public static final int HOTBAR_WIDTH = 182;
    /** Gap between the mode menu and the hotbar edge. */
    public static final int HOTBAR_GAP = 4;

    private HudAnchor() {
    }

    /** Left edge of the centered vanilla hotbar. */
    public static int hotbarLeft(int screen) {
        return (screen - HOTBAR_WIDTH) / 2;
    }

    /** Right edge of the centered vanilla hotbar. */
    public static int hotbarRight(int screen) {
        return hotbarLeft(screen) + HOTBAR_WIDTH;
    }

    /** Top edge of the vanilla hotbar strip ({@code screenHeight - 22}). */
    public static int hotbarTop(int screen) {
        return screen - 22;
    }

    /** Y so {@code size} sits flush above the hotbar with a small gap. */
    public static int aboveHotbarY(int screen, int size) {
        return Mth.clamp(hotbarTop(screen) - HOTBAR_GAP - size, 2, Math.max(2, screen - size - 2));
    }

    /** Horizontal center over the hotbar. */
    public static int aboveHotbarX(int screen, int size) {
        return Mth.clamp(hotbarLeft(screen) + (HOTBAR_WIDTH - size) / 2, 2, Math.max(2, screen - size - 2));
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
            // Flush above the hotbar (mode title default). HOTBAR_CLEARANCE kept for callers that need action-bar room.
            case BOTTOM -> hotbarTop(screen) - HOTBAR_GAP - size;
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

    /**
     * Left/Right park beside the hotbar: Left ends just left of it, Right starts just right of
     * it, both clear of the centered action-bar text. Other spots match hint/toast placement.
     */
    public static int modeX(int screen, int size) {
        int spot = modeSpot();
        int raw = switch (spot) {
            case LEFT -> hotbarLeft(screen) - HOTBAR_GAP - size;
            case RIGHT -> hotbarRight(screen) + HOTBAR_GAP;
            case CUSTOM -> WrightsClientConfig.HUD_MODE_X.get() * screen / 1000 - size / 2;
            default -> (screen - size) / 2;
        };
        return Mth.clamp(raw, 2, Math.max(2, screen - size - 2));
    }

    /**
     * Left/right sit on the bottom edge beside the hotbar. Other spots match hint/toast placement.
     */
    public static int modeY(int screen, int size) {
        int spot = modeSpot();
        if (spot == LEFT || spot == RIGHT) {
            return Mth.clamp(screen - size - 2, 2, Math.max(2, screen - size - 2));
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
