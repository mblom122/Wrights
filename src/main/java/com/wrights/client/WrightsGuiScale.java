package com.wrights.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shrinks Wrights screens at high Minecraft GUI scales so panels stay usable.
 * Scale 1–3: unchanged; 4: 90%; 5+: 70%. One matrix + matching mouse remap keeps
 * buttons, text, and hitboxes aligned.
 */
public final class WrightsGuiScale {
    private WrightsGuiScale() {
    }

    /** Effective Minecraft GUI scale (Auto resolved), rounded. */
    public static int minecraftScale() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return 1;
        }
        return Math.max(1, (int) Math.round(minecraft.getWindow().getGuiScale()));
    }

    /** Content scale for Wrights screens. */
    public static float factor() {
        int scale = minecraftScale();
        if (scale >= 5) {
            return 0.70F;
        }
        if (scale >= 4) {
            return 0.90F;
        }
        return 1.0F;
    }

    public static boolean active() {
        return factor() < 0.999F;
    }

    public static int localX(int mouseX, int width) {
        return (int) Math.round(localX((double) mouseX, width));
    }

    public static int localY(int mouseY, int height) {
        return (int) Math.round(localY((double) mouseY, height));
    }

    public static double localX(double mouseX, int width) {
        float factor = factor();
        if (factor >= 0.999F) {
            return mouseX;
        }
        double mid = width * 0.5D;
        return (mouseX - mid) / factor + mid;
    }

    public static double localY(double mouseY, int height) {
        float factor = factor();
        if (factor >= 0.999F) {
            return mouseY;
        }
        double mid = height * 0.5D;
        return (mouseY - mid) / factor + mid;
    }

    public static double localDelta(double screenDelta) {
        float factor = factor();
        return factor >= 0.999F ? screenDelta : screenDelta / factor;
    }

    /** Scale around the screen center. Always paired with {@link #end}. */
    public static void begin(GuiGraphics graphics, int width, int height) {
        graphics.pose().pushPose();
        float factor = factor();
        if (factor < 0.999F) {
            graphics.pose().translate(width * 0.5F, height * 0.5F, 0.0F);
            graphics.pose().scale(factor, factor, 1.0F);
            graphics.pose().translate(-width * 0.5F, -height * 0.5F, 0.0F);
        }
    }

    public static void end(GuiGraphics graphics) {
        graphics.pose().popPose();
    }
}
