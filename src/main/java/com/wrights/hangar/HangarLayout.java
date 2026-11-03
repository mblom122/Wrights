package com.wrights.hangar;

/**
 * Shared hangar panel metrics (texture → screen). Used by the wrightport menu slots
 * and the client hangar UI so both stay aligned.
 */
public final class HangarLayout {
    public static final int TEX_W = 512;
    public static final int TEX_H = 286;
    public static final float SCALE = 0.75F;

    public static final int WIDTH = sx(TEX_W);
    public static final int HEIGHT = sx(TEX_H);
    public static final int CHROME = 18;
    public static final int PANEL_H = HEIGHT + CHROME;

    public static final int INV_W = 176;
    public static final int INV_H = 90;
    public static final int INV_GAP = 6;
    public static final int SCREEN_H = PANEL_H + INV_GAP + INV_H;

    private HangarLayout() {
    }

    public static int sx(int texPx) {
        return Math.round(texPx * SCALE);
    }

    /** First player-inventory slot X relative to the screen leftPos. */
    public static int invSlotLeft() {
        return (WIDTH - INV_W) / 2 + 8;
    }

    /** First player-inventory slot Y relative to the screen topPos. */
    public static int invSlotTop() {
        return PANEL_H + INV_GAP + 8;
    }
}
