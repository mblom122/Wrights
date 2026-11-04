package com.wrights.compat.jei;

import com.wrights.client.JeiPreview;
import com.wrights.client.WrightsGuiScale;

import mezz.jei.api.gui.handlers.IGuiProperties;
import net.minecraft.client.gui.screens.Screen;

final class WrightsJeiScreens {
    private WrightsJeiScreens() {
    }

    static IGuiProperties area(Screen screen) {
        if (!(screen instanceof JeiPreview preview) || preview.jeiWidth() <= 0 || preview.jeiHeight() <= 0) {
            return null;
        }
        return new Area(screen.getClass(), preview);
    }

    /**
     * Reports the hangar/menu bounds JEI should leave free. When {@link WrightsGuiScale} shrinks
     * the screen around the center, those visual bounds are smaller than leftPos/imageWidth —
     * without that adjustment JEI thinks there is no side lane and hides the ingredient list.
     */
    private record Area(Class<? extends Screen> screenClass, JeiPreview preview) implements IGuiProperties {
        private float factor() {
            return WrightsGuiScale.factor();
        }

        private int mapX(int x) {
            float factor = factor();
            if (factor >= 0.999F) {
                return x;
            }
            float mid = preview.jeiScreenWidth() * 0.5F;
            return Math.round(mid + (x - mid) * factor);
        }

        private int mapY(int y) {
            float factor = factor();
            if (factor >= 0.999F) {
                return y;
            }
            float mid = preview.jeiScreenHeight() * 0.5F;
            return Math.round(mid + (y - mid) * factor);
        }

        private int mapSize(int size) {
            float factor = factor();
            if (factor >= 0.999F) {
                return size;
            }
            return Math.max(1, Math.round(size * factor));
        }

        @Override
        public int guiLeft() {
            return mapX(preview.jeiLeft());
        }

        @Override
        public int guiTop() {
            return mapY(preview.jeiTop());
        }

        @Override
        public int guiXSize() {
            return mapSize(preview.jeiWidth());
        }

        @Override
        public int guiYSize() {
            return mapSize(preview.jeiHeight());
        }

        @Override
        public int screenWidth() {
            return preview.jeiScreenWidth();
        }

        @Override
        public int screenHeight() {
            return preview.jeiScreenHeight();
        }
    }
}
