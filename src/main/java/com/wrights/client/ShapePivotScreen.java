package com.wrights.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import com.wrights.WrightsClientConfig;
import com.wrights.blueprint.Shapes;

/**
 * Pick which point on the shape bounding box pins to the look / world-anchor cell.
 * Six faces × 3×3 (corners, edge mids, face center) plus the middle of the whole shape.
 */
public class ShapePivotScreen extends Screen {
    private static final String[] FACE_KEYS = {
            "screen.wrights.shape_pivot_face_down",
            "screen.wrights.shape_pivot_face_up",
            "screen.wrights.shape_pivot_face_north",
            "screen.wrights.shape_pivot_face_south",
            "screen.wrights.shape_pivot_face_west",
            "screen.wrights.shape_pivot_face_east"
    };

    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int face = 1;
    private final List<Hit> hits = new ArrayList<>();

    public ShapePivotScreen(Screen parent) {
        super(Component.translatable("screen.wrights.shape_pivot_title"));
        this.parent = parent;
        int pivot = Shapes.clampPivot(WrightsClientConfig.SHAPE_PIVOT.get());
        if (pivot < Shapes.PIVOT_CENTER) {
            face = Shapes.pivotFace(pivot);
        }
    }

    @Override
    protected void init() {
        panelW = WrightsUi.fitW(420, this.width);
        panelH = WrightsUi.fitH(380, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
        addRenderableWidget(new WrightsButton(
                panelX + 16,
                panelY + panelH - 36,
                panelW - 32,
                20,
                Component.translatable("screen.wrights.done"),
                button -> onClose()));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.scrim(graphics, this.width, this.height);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 16, panelY + 10, WrightsUi.COPPER, false);
        int y = panelY + 28;
        int inner = panelW - 32;
        for (var line : this.font.split(Component.translatable("screen.wrights.shape_pivot_hint"), inner)) {
            graphics.drawString(this.font, line, panelX + 16, y, WrightsUi.MUTED, false);
            y += 10;
        }
        y += 8;
        graphics.drawString(this.font, Component.translatable("screen.wrights.shape_pivot_face"), panelX + 16, y, WrightsUi.MUTED, false);
        y += 12;
        int gap = 4;
        int faceW = Math.max(48, (inner - gap * 5) / 6);
        int pivot = Shapes.clampPivot(WrightsClientConfig.SHAPE_PIVOT.get());
        for (int f = 0; f < 6; f++) {
            int bx = panelX + 16 + f * (faceW + gap);
            int bw = f == 5 ? panelX + 16 + inner - bx : faceW;
            boolean on = face == f;
            boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= y && mouseY < y + 18;
            WrightsUi.choice(graphics, bx, y, bw, 18, on, hover);
            String label = this.font.plainSubstrByWidth(Component.translatable(FACE_KEYS[f]).getString(), Math.max(8, bw - 6));
            graphics.drawCenteredString(this.font, label, bx + bw / 2, y + 5, on ? WrightsUi.TEXT : WrightsUi.MUTED);
            hits.add(new Hit(bx, y, bw, 18, "face", f));
        }
        y += 28;

        // Footer: Done at bottom, then Selected text, then Middle-of-shape — reserve that stack.
        int doneTop = panelY + panelH - 36;
        int selectedY = doneTop - 18;
        int centerY = selectedY - 26;
        int gridBudget = Math.max(72, centerY - y - 8);
        int grid = Math.min(150, gridBudget);
        int gridX = panelX + 16 + (inner - grid) / 2;
        int gridY = y;
        drawFaceWire(graphics, gridX, gridY, grid, face);
        int cell = Math.max(1, grid / 3);
        for (int slot = 0; slot < 9; slot++) {
            int col = slot % 3;
            int row = slot / 3;
            int cx = gridX + col * cell + cell / 2;
            int cy = gridY + row * cell + cell / 2;
            int id = Shapes.pivotId(face, slot);
            boolean on = pivot == id;
            boolean hover = mouseX >= cx - 10 && mouseX < cx + 10 && mouseY >= cy - 10 && mouseY < cy + 10;
            int r = on ? 7 : 5;
            graphics.fill(cx - r, cy - r, cx + r, cy + r, on || hover ? WrightsUi.COPPER : 0xFF3D4E5E);
            graphics.fill(cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1, on ? 0xFFE8C07A : hover ? 0xFF2A3A48 : 0xFF18222C);
            hits.add(new Hit(cx - 10, cy - 10, 20, 20, "slot", slot));
        }

        boolean centerOn = pivot == Shapes.PIVOT_CENTER;
        boolean centerHover = mouseX >= panelX + 16 && mouseX < panelX + 16 + inner && mouseY >= centerY && mouseY < centerY + 20;
        WrightsUi.choice(graphics, panelX + 16, centerY, inner, 20, centerOn, centerHover);
        graphics.drawCenteredString(this.font, Component.translatable("screen.wrights.shape_pivot_center"),
                panelX + 16 + inner / 2, centerY + 6, centerOn ? WrightsUi.TEXT : WrightsUi.MUTED);
        hits.add(new Hit(panelX + 16, centerY, inner, 20, "center", 0));
        graphics.drawString(this.font, Component.translatable("screen.wrights.shape_pivot_current", pivotSummary(pivot)),
                panelX + 16, selectedY, WrightsUi.MUTED, false);
    }

    private void drawFaceWire(GuiGraphics graphics, int x, int y, int size, int faceIndex) {
        graphics.fill(x, y, x + size, y + size, 0xFF101820);
        WrightsUi.border(graphics, x, y, size, size, 0xFF31404E);
        int step = size / 3;
        for (int i = 1; i < 3; i++) {
            graphics.fill(x + i * step, y + 1, x + i * step + 1, y + size - 1, 0xFF243040);
            graphics.fill(x + 1, y + i * step, x + size - 1, y + i * step + 1, 0xFF243040);
        }
        Direction dir = Direction.from3DDataValue(Mth.clamp(faceIndex, 0, 5));
        graphics.drawCenteredString(this.font, Component.translatable(FACE_KEYS[dir.get3DDataValue()]),
                x + size / 2, y + size / 2 - 4, 0x553D4E5E);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (Hit hit : hits) {
            if (mouseX < hit.x || mouseX >= hit.x + hit.w || mouseY < hit.y || mouseY >= hit.y + hit.h) {
                continue;
            }
            switch (hit.id) {
                case "face" -> face = hit.value;
                case "slot" -> WrightsClientConfig.setShapePivot(Shapes.pivotId(face, hit.value));
                case "center" -> WrightsClientConfig.setShapePivot(Shapes.PIVOT_CENTER);
                default -> {
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    static Component pivotSummary(int pivot) {
        int id = Shapes.clampPivot(pivot);
        if (id >= Shapes.PIVOT_CENTER) {
            return Component.translatable("screen.wrights.shape_pivot_center");
        }
        int f = Shapes.pivotFace(id);
        int slot = Shapes.pivotSlot(id);
        String slotKey = switch (slot) {
            case 0 -> "screen.wrights.shape_pivot_slot_nw";
            case 1 -> "screen.wrights.shape_pivot_slot_n";
            case 2 -> "screen.wrights.shape_pivot_slot_ne";
            case 3 -> "screen.wrights.shape_pivot_slot_w";
            case 4 -> "screen.wrights.shape_pivot_slot_c";
            case 5 -> "screen.wrights.shape_pivot_slot_e";
            case 6 -> "screen.wrights.shape_pivot_slot_sw";
            case 7 -> "screen.wrights.shape_pivot_slot_s";
            default -> "screen.wrights.shape_pivot_slot_se";
        };
        return Component.translatable("screen.wrights.shape_pivot_summary",
                Component.translatable(FACE_KEYS[Mth.clamp(f, 0, 5)]),
                Component.translatable(slotKey));
    }

    private record Hit(int x, int y, int w, int h, String id, int value) {
    }
}
