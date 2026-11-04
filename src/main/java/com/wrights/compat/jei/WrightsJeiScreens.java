package com.wrights.compat.jei;

import com.wrights.client.JeiPreview;
import com.wrights.client.WrightsGuiScale;

import mezz.jei.api.gui.handlers.IGuiProperties;
import net.minecraft.client.gui.screens.Screen;

final class WrightsJeiScreens {
    private WrightsJeiScreens() {
    }

    static IGuiProperties area(Screen screen) {
        if (!(screen instanceof JeiPreview preview)) {
            return null;
        }
        // Match JEI's GuiProperties.create: refuse unsized screens. A live Area that
        // reports 0×0 gets cached as invalid; later areEqual() sees the now-valid live
        // width/height and skips revalidation, so JEI stays hidden for the whole open.
        if (screen.width <= 0 || screen.height <= 0
                || preview.jeiWidth() <= 0 || preview.jeiHeight() <= 0) {
            return null;
        }
        return Area.snapshot(screen.getClass(), preview);
    }

    /**
     * Snapshot of hangar/menu bounds JEI should leave free. Values are frozen at creation
     * so JEI's equality/cache cannot latch onto pre-init 0×0 dimensions. When
     * {@link WrightsGuiScale} shrinks the screen around the center, visual bounds are
     * smaller than leftPos/imageWidth — without that adjustment JEI hides the list.
     */
    private record Area(
            Class<? extends Screen> screenClass,
            int guiLeft,
            int guiTop,
            int guiXSize,
            int guiYSize,
            int screenWidth,
            int screenHeight
    ) implements IGuiProperties {
        static Area snapshot(Class<? extends Screen> screenClass, JeiPreview preview) {
            float factor = WrightsGuiScale.factor();
            int screenW = preview.jeiScreenWidth();
            int screenH = preview.jeiScreenHeight();
            return new Area(
                    screenClass,
                    mapX(preview.jeiLeft(), screenW, factor),
                    mapY(preview.jeiTop(), screenH, factor),
                    mapSize(preview.jeiWidth(), factor),
                    mapSize(preview.jeiHeight(), factor),
                    screenW,
                    screenH);
        }

        private static int mapX(int x, int screenW, float factor) {
            if (factor >= 0.999F) {
                return x;
            }
            float mid = screenW * 0.5F;
            return Math.round(mid + (x - mid) * factor);
        }

        private static int mapY(int y, int screenH, float factor) {
            if (factor >= 0.999F) {
                return y;
            }
            float mid = screenH * 0.5F;
            return Math.round(mid + (y - mid) * factor);
        }

        private static int mapSize(int size, float factor) {
            if (factor >= 0.999F) {
                return size;
            }
            return Math.max(1, Math.round(size * factor));
        }
    }
}
