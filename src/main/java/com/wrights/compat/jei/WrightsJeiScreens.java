package com.wrights.compat.jei;

import com.wrights.client.JeiPreview;

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

    private record Area(Class<? extends Screen> screenClass, JeiPreview preview) implements IGuiProperties {
        @Override
        public int guiLeft() {
            return preview.jeiLeft();
        }

        @Override
        public int guiTop() {
            return preview.jeiTop();
        }

        @Override
        public int guiXSize() {
            return preview.jeiWidth();
        }

        @Override
        public int guiYSize() {
            return preview.jeiHeight();
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
