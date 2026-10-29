package com.wrights.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.WrightsConfig;
import com.wrights.item.ClipboardExchange;
import com.wrights.schematic.Schematic;

/** The spinning block model used by the blueprint list, shared with import and item tooltips. */
public final class SchematicViews {
    public record Shot(List<WrightsClientState.Ghost> ghosts, int sizeX, int sizeY, int sizeZ, boolean concealed) {
        private static final Shot EMPTY = new Shot(List.of(), 1, 1, 1, false);

        public boolean visible() {
            return concealed || !ghosts.isEmpty();
        }
    }

    private static CompoundTag tagKey;
    private static Shot tagShot = Shot.EMPTY;
    private static byte[] clipKey;
    private static Shot clipShot = Shot.EMPTY;
    private static float yaw = 35.0F;

    private SchematicViews() {
    }

    public static Shot fromTag(CompoundTag tag) {
        if (tag == null || !tag.contains("blocks")) {
            return Shot.EMPTY;
        }
        if (tag == tagKey) {
            return tagShot;
        }
        tagKey = tag;
        tagShot = build(tag);
        return tagShot;
    }

    public static Shot fromClipboard() {
        byte[] data = BlueprintClipboard.data();
        if (data.length == 0) {
            return Shot.EMPTY;
        }
        if (data == clipKey) {
            return clipShot;
        }
        clipKey = data;
        CompoundTag wrapper = ClipboardExchange.decode(data);
        if (wrapper == null || wrapper.getBoolean("folder") || !wrapper.contains("body")) {
            clipShot = Shot.EMPTY;
            return clipShot;
        }
        clipShot = build(wrapper.getCompound("body"));
        return clipShot;
    }

    public static void draw(GuiGraphics graphics, Font font, int x, int y, int width, int height, Shot shot) {
        if (width < 8 || height < 8 || shot == null || !shot.visible()) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, 0xFF101820);
        WrightsUi.border(graphics, x, y, width, height, 0xFF31404E);
        if (shot.concealed() || shot.ghosts().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.wrights.preview_hidden"), x + width / 2, y + height / 2 - 4, WrightsUi.MUTED);
            return;
        }
        render(graphics, x + 1, y + 1, width - 2, height - 2, shot);
    }

    public static void render(GuiGraphics graphics, int x, int y, int width, int height, Shot shot) {
        if (shot == null || shot.ghosts().isEmpty() || width < 8 || height < 8) {
            return;
        }
        yaw += 0.35F;
        BlueprintModelView.render(graphics, x, y, width, height, shot.ghosts(), shot.sizeX(), shot.sizeY(), shot.sizeZ(), yaw, 28.0F);
    }

    private static Shot build(CompoundTag tag) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return Shot.EMPTY;
        }
        try {
            Schematic schematic = Schematic.load(tag, Schematic.lookup(minecraft.level.registryAccess()));
            int sizeX = Math.max(1, schematic.sizeX());
            int sizeY = Math.max(1, schematic.sizeY());
            int sizeZ = Math.max(1, schematic.sizeZ());
            if (schematic.entries().size() > WrightsConfig.maxPreviewBlocks()) {
                return new Shot(List.of(), sizeX, sizeY, sizeZ, true);
            }
            List<WrightsClientState.Ghost> ghosts = new ArrayList<>();
            for (Schematic.Entry entry : schematic.entries()) {
                BlockState state = entry.state();
                if (state.isAir()) {
                    continue;
                }
                ghosts.add(new WrightsClientState.Ghost(
                        Short.toUnsignedInt(entry.x()),
                        Short.toUnsignedInt(entry.y()),
                        Short.toUnsignedInt(entry.z()),
                        1.0F,
                        1.0F,
                        1.0F,
                        Block.getId(state)));
            }
            return new Shot(List.copyOf(ghosts), sizeX, sizeY, sizeZ, false);
        } catch (RuntimeException ignored) {
            return Shot.EMPTY;
        }
    }
}
