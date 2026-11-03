package com.wrights.hangar;

/**
 * Shared hangar panel metrics (texture → screen). Used by the wrightport menu slots
 * and the client hangar UI so both stay aligned.
 * <p>
 * Source art is high-res ({@link #TEX_W}×{@link #TEX_H}); slot/button coords stay in the
 * smaller {@link #LAYOUT_W}×{@link #LAYOUT_H} design space and {@link #SCALE} maps that to
 * on-screen pixels so the panel stays the same size while the blit keeps extra detail.
 */
public final class HangarLayout {
    /** Actual hangar.png pixel size (original art). */
    public static final int TEX_W = 2752;
    public static final int TEX_H = 1536;
    /** Design space used by bay/part/button coordinates (legacy 512 layout). */
    public static final int LAYOUT_W = 512;
    public static final int LAYOUT_H = 286;
    public static final float SCALE = 0.75F;

    public static final int WIDTH = sx(LAYOUT_W);
    public static final int HEIGHT = sx(LAYOUT_H);
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
        return 113;
    }

    /** First player-inventory slot Y relative to the screen topPos. */
    public static int invSlotTop() {
        return 248;
    }
}
