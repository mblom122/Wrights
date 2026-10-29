package com.wrights.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record BlueprintTip(CompoundTag tag) implements TooltipComponent {
    public static final class Renderer implements ClientTooltipComponent {
        private final BlueprintTip tip;

        public Renderer(BlueprintTip tip) {
            this.tip = tip;
        }

        @Override
        public int getHeight() {
            SchematicViews.Shot shot = SchematicViews.fromTag(tip.tag());
            return shot.concealed() ? 12 : 80;
        }

        @Override
        public int getWidth(Font font) {
            SchematicViews.Shot shot = SchematicViews.fromTag(tip.tag());
            if (shot.concealed()) {
                return font.width(Component.translatable("screen.wrights.preview_hidden"));
            }
            return 96;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            SchematicViews.Shot shot = SchematicViews.fromTag(tip.tag());
            if (shot.concealed()) {
                graphics.drawString(font, Component.translatable("screen.wrights.preview_hidden"), x, y + 2, WrightsUi.MUTED, false);
                return;
            }
            SchematicViews.render(graphics, x, y, 96, 76, shot);
        }
    }
}
