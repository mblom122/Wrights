package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.neoforged.neoforge.network.PacketDistributor;
import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
import com.wrights.network.BlueprintPayloads;

/** The blueprint shelf: list, folders, marks, drag and drop, sorting and the scope/tab strip. */
final class ShelfPanel {
    private final WrightsMenuScreen s;

    ShelfPanel(WrightsMenuScreen s) {
        this.s = s;
    }

    private long lastClick;

    private int sortMenuX;

    private int sortMenuY;

    private int sortMenuW;

    private int sortMenuH;

    private static final String[] SORT_LABELS = {
            "menu.wrights.sort_manual",
            "menu.wrights.sort_name",
            "menu.wrights.sort_size",
            "menu.wrights.sort_blocks",
            "menu.wrights.sort_used",
            "menu.wrights.sort_last"
    };

    void drawScope(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!WrightsConfig.allowGlobal()) {
            drawScopeChip(graphics, mouseX, mouseY, s.listX, s.headingY, s.listW, false, "menu.wrights.scope_world");
            return;
        }
        int half = s.listW / 2;
        drawScopeChip(graphics, mouseX, mouseY, s.listX, s.headingY, half - 2, false, "menu.wrights.scope_world");
        drawScopeChip(graphics, mouseX, mouseY, s.listX + half, s.headingY, s.listW - half, true, "menu.wrights.scope_global");
    }

    private void drawScopeChip(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, boolean global, String key) {
        boolean on = WrightsClientState.libraryGlobal == global;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
        WrightsUi.choice(graphics, x, y, w, 14, on, hover);
        String label = s.font().plainSubstrByWidth(Component.translatable(key).getString(), w - 4);
        graphics.drawString(s.font(), label, x + (w - s.font().width(label)) / 2, y + 3, on ? WrightsUi.TEXT : WrightsUi.MUTED, false);
    }

    void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        String[] keys = {"menu.wrights.tab_all", "menu.wrights.tab_last", "menu.wrights.tab_most", "menu.wrights.tab_fav"};
        int width = s.listW / keys.length;
        int tabY = s.headingY + 16;
        for (int i = 0; i < keys.length; i++) {
            int x = s.listX + i * width;
            int tabW = i == keys.length - 1 ? s.listX + s.listW - x : width;
            boolean on = WrightsMenuScreen.savedTab == i;
            boolean hover = mouseX >= x && mouseX < x + tabW && mouseY >= tabY && mouseY < tabY + 14;
            WrightsUi.choice(graphics, x, tabY, tabW - 1, 14, on, hover);
            String label = s.font().plainSubstrByWidth(Component.translatable(keys[i]).getString(), tabW - 4);
            graphics.drawString(s.font(), label, x + (tabW - s.font().width(label)) / 2, tabY + 3, on ? WrightsUi.TEXT : WrightsUi.MUTED, false);
        }
    }

    void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!WrightsClientState.libraryKnown) {
            graphics.drawString(s.font(), Component.translatable("menu.wrights.loading"), s.listX + 6, s.listY + 8, WrightsUi.MUTED, false);
            return;
        }
        if (s.rows.isEmpty()) {
            graphics.drawString(s.font(), Component.translatable(emptyKey()), s.listX + 6, s.listY + 8, WrightsUi.MUTED, false);
            return;
        }
        graphics.enableScissor(s.listX, s.listY, s.listX + s.listW, s.listY + s.listH);
        int shown = 0;
        for (int i = s.scroll; i < s.rows.size() && shown < s.visibleRows(); i++) {
            BlueprintList.Row entry = s.rows.get(i);
            int y = s.listY + shown * WrightsMenuScreen.ROW;
            boolean selected = s.marked.contains(entry.id());
            boolean hover = mouseX >= s.listX && mouseX < s.listX + s.listW && mouseY >= y && mouseY < y + WrightsMenuScreen.ROW;
            boolean into = s.shelfDragging && s.dropInto && entry.id().equals(s.dropHighlight);
            if (selected || hover || into) {
                graphics.fill(s.listX, y, s.listX + s.listW - 4, y + WrightsMenuScreen.ROW - 1, into ? 0x665C3E22 : (selected ? (entry.folder() ? 0x665C3E22 : 0x664A78B0) : 0x332A3C4C));
            } else if (entry.folder()) {
                graphics.fill(s.listX, y, s.listX + s.listW - 4, y + WrightsMenuScreen.ROW - 1, 0x221C140C);
            }
            if (selected) {
                graphics.fill(s.listX, y, s.listX + 2, y + WrightsMenuScreen.ROW - 1, entry.folder() ? WrightsUi.COPPER : WrightsUi.BLUE);
            }
            if (!entry.folder()) {
                drawStar(graphics, s.listX + 4, y + 4, entry.favorite());
            }
            int x = s.listX + (entry.folder() ? 4 : 16) + entry.depth() * 8;
            if (entry.folder()) {
                drawDisclosure(graphics, x, y + 6, entry.open());
                drawFolderGlyph(graphics, x + 8, y + 3, entry.open());
                x += 22;
            } else {
                int swatch = 0xFF000000 | (0x5A6E82 + (entry.name().hashCode() & 0x3F4F5F));
                graphics.fill(x, y + 5, x + 6, y + 11, swatch);
                x += 10;
            }
            int nameRoom = Math.max(8, s.listX + s.listW - 8 - x);
            String extra = entry.folder() ? Integer.toString(BlueprintList.childCount(entry.id())) : entry.summary().sizeX() + "×" + entry.summary().sizeY() + "×" + entry.summary().sizeZ();
            int extraW = s.font().width(extra) + 6;
            if (nameRoom - extraW < 24) {
                extra = "";
                extraW = 0;
            }
            String name = s.font().plainSubstrByWidth(entry.name(), Math.max(8, nameRoom - extraW));
            graphics.drawString(s.font(), name, x, y + 5, entry.folder() ? WrightsUi.COPPER : WrightsUi.TEXT, false);
            if (!extra.isEmpty()) {
                graphics.drawString(s.font(), extra, s.listX + s.listW - 8 - s.font().width(extra), y + 5,
                        entry.folder() ? WrightsUi.MUTED : WrightsUi.SIZE, false);
            }
            shown++;
        }
        graphics.disableScissor();
        if (s.shelfDragging && !s.dropInto && s.dropLineY >= s.listY && s.dropLineY <= s.listY + s.listH) {
            graphics.fill(s.listX + 4, s.dropLineY, s.listX + s.listW - 6, s.dropLineY + 2, WrightsUi.COPPER);
        }
        if (s.shelfDragging && s.shelfPress != null) {
            int draggingCount = dragIds().size();
            String ghost = "";
            if (draggingCount > 1) {
                ghost = Component.translatable("menu.wrights.drag_many", draggingCount).getString();
            } else {
                for (BlueprintList.Row entry : s.rows) {
                    if (entry.id().equals(s.shelfPress)) {
                        ghost = entry.name();
                        break;
                    }
                }
            }
            if (!ghost.isEmpty()) {
                String label = s.font().plainSubstrByWidth(ghost, 120);
                int gx = Mth.clamp(mouseX + 8, s.listX, s.listX + s.listW - s.font().width(label) - 8);
                int gy = Mth.clamp(mouseY - 8, s.listY, s.listY + s.listH - 12);
                graphics.fill(gx - 2, gy - 2, gx + s.font().width(label) + 2, gy + 10, 0xEE1A2633);
                graphics.drawString(s.font(), label, gx, gy, WrightsUi.TEXT, false);
            }
        }
        int maxScroll = Math.max(0, s.rows.size() - s.visibleRows());
        if (maxScroll > 0) {
            int track = s.listH - 4;
            int thumb = Math.max(12, track * s.visibleRows() / s.rows.size());
            int thumbY = s.listY + 2 + (track - thumb) * s.scroll / maxScroll;
            graphics.fill(s.listX + s.listW - 3, thumbY, s.listX + s.listW - 1, thumbY + thumb, WrightsUi.COPPER);
        }
    }

    private String emptyKey() {
        if (s.search != null && !s.search.getValue().trim().isEmpty()) {
            return "menu.wrights.no_match";
        }
        if (WrightsMenuScreen.savedTab == BlueprintList.Tab.LAST.ordinal() || WrightsMenuScreen.savedTab == BlueprintList.Tab.MOST.ordinal()) {
            return "menu.wrights.no_used";
        }
        if (WrightsMenuScreen.savedTab == BlueprintList.Tab.FAVORITES.ordinal()) {
            return "menu.wrights.no_favorites";
        }
        return "menu.wrights.empty";
    }

    private void drawStar(GuiGraphics graphics, int x, int y, boolean on) {
        int color = on ? 0xFFFFC44D : 0xFF4C5964;
        graphics.fill(x + 3, y, x + 5, y + 8, color);
        graphics.fill(x, y + 3, x + 8, y + 5, color);
        graphics.fill(x + 1, y + 1, x + 3, y + 3, color);
        graphics.fill(x + 5, y + 1, x + 7, y + 3, color);
        graphics.fill(x + 1, y + 5, x + 3, y + 7, color);
        graphics.fill(x + 5, y + 5, x + 7, y + 7, color);
    }

    private void drawDisclosure(GuiGraphics graphics, int x, int y, boolean open) {
        int color = WrightsUi.COPPER;
        if (open) {
            graphics.fill(x, y + 2, x + 5, y + 3, color);
            graphics.fill(x + 1, y + 3, x + 4, y + 4, color);
            graphics.fill(x + 2, y + 4, x + 3, y + 5, color);
            return;
        }
        graphics.fill(x, y, x + 1, y + 5, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 4, color);
        graphics.fill(x + 2, y + 2, x + 3, y + 3, color);
    }

    private void drawFolderGlyph(GuiGraphics graphics, int x, int y, boolean open) {
        graphics.fill(x, y + 1, x + 5, y + 3, open ? 0xFFE0A15A : WrightsUi.COPPER);
        graphics.fill(x, y + 3, x + 11, y + 10, open ? 0xFF8C5A2A : 0xFF5C3E22);
        if (open) {
            graphics.fill(x + 1, y + 4, x + 10, y + 6, 0xFF3D2A18);
        }
    }

    void renderFolder(GuiGraphics graphics, BlueprintList.Row row) {
        int cx = s.modelX + Math.max(0, s.modelW / 2 - 16);
        int cy = s.modelY + Math.max(8, s.modelH / 2 - 18);
        graphics.fill(cx, cy, cx + 14, cy + 6, WrightsUi.COPPER);
        graphics.fill(cx, cy + 6, cx + 32, cy + 26, 0xFF8C5A2A);
        graphics.drawString(s.font(), s.font().plainSubstrByWidth(Component.translatable("menu.wrights.folder_keep").getString(), s.modelW), s.modelX, cy + 32, WrightsUi.MUTED, false);
        graphics.fill(s.detailX + 4, s.detailSplit, s.detailX + s.detailW - 4, s.detailSplit + 1, 0xFF31404E);
        int info = s.detailSplit + 4;
        graphics.drawString(s.font(), s.font().plainSubstrByWidth(row.name(), s.detailW - 8), s.detailX + 4, info, WrightsUi.COPPER, false);
        graphics.drawString(s.font(), Component.translatable("menu.wrights.folder"), s.detailX + 4, info + 12, WrightsUi.TEXT, false);
        graphics.drawString(s.font(), Component.translatable("menu.wrights.folder_count", BlueprintList.childCount(row.id())), s.detailX + 4, info + 24, WrightsUi.MUTED, false);
        graphics.drawString(s.font(), Component.translatable("screen.wrights.name"), s.detailX + 4, s.detailSplit + 42, WrightsUi.MUTED, false);
    }

    boolean clickScope(double mouseX, double mouseY) {
        if (mouseY < s.headingY || mouseY >= s.headingY + 14 || mouseX < s.listX || mouseX >= s.listX + s.listW) {
            return false;
        }
        boolean global = WrightsConfig.allowGlobal() && mouseX >= s.listX + s.listW / 2.0D;
        WrightsClientState.setLibraryScope(global);
        s.refresh();
        return true;
    }

    boolean clickTab(double mouseX, double mouseY) {
        int tabY = s.headingY + 16;
        if (mouseY < tabY || mouseY >= tabY + 14 || mouseX < s.listX || mouseX >= s.listX + s.listW) {
            return false;
        }
        int tab = (int) ((mouseX - s.listX) * 4 / Math.max(1, s.listW));
        tab = Mth.clamp(tab, 0, 3);
        if (tab != WrightsMenuScreen.savedTab) {
            WrightsMenuScreen.savedTab = tab;
            s.scroll = 0;
            s.shelfPress = null;
            s.shelfDragging = false;
            s.refresh();
        }
        return true;
    }

    private void placeSortMenu() {
        int widest = 128;
        for (String key : SORT_LABELS) {
            widest = Math.max(widest, s.font().width(Component.translatable(key).getString()) + 72);
        }
        sortMenuW = widest;
        sortMenuH = 8 + SORT_LABELS.length * 16;
        sortMenuX = s.sortButton.getX() + s.sortButton.getWidth() - sortMenuW;
        sortMenuY = s.sortButton.getY() + s.sortButton.getHeight() + 2;
        if (sortMenuX < s.panelX + 8) {
            sortMenuX = s.panelX + 8;
        }
        if (sortMenuY + sortMenuH > s.panelY + s.panelH - 8) {
            sortMenuY = s.sortButton.getY() - sortMenuH - 2;
        }
    }

    void renderSortMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        if (s.sortButton == null) {
            return;
        }
        placeSortMenu();
        WrightsUi.panel(graphics, sortMenuX, sortMenuY, sortMenuW, sortMenuH);
        int selected = WrightsClientConfig.sortKey(WrightsMenuScreen.savedTab);
        boolean ascending = WrightsClientConfig.sortAscending(WrightsMenuScreen.savedTab);
        for (int i = 0; i < SORT_LABELS.length; i++) {
            int y = sortMenuY + 4 + i * 16;
            boolean on = selected == i;
            boolean hover = mouseX >= sortMenuX && mouseX < sortMenuX + sortMenuW && mouseY >= y && mouseY < y + 16;
            if (on || hover) {
                graphics.fill(sortMenuX + 3, y, sortMenuX + sortMenuW - 2, y + 15, on ? 0xFF24384A : 0xFF1C2A37);
            }
            String label = Component.translatable(SORT_LABELS[i]).getString();
            graphics.drawString(s.font(), label, sortMenuX + 8, y + 4, on ? WrightsUi.TEXT : WrightsUi.MUTED, false);
            if (on && i != BlueprintList.SortKey.MANUAL.ordinal()) {
                String direction = sortDirection(i, ascending);
                graphics.drawString(s.font(), direction, sortMenuX + sortMenuW - 8 - s.font().width(direction), y + 4, WrightsUi.COPPER, false);
            }
        }
    }

    private String sortDirection(int key, boolean ascending) {
        String id = switch (key) {
            case 1 -> ascending ? "menu.wrights.sort_az" : "menu.wrights.sort_za";
            case 2 -> ascending ? "menu.wrights.sort_small" : "menu.wrights.sort_large";
            case 3 -> ascending ? "menu.wrights.sort_few" : "menu.wrights.sort_many";
            case 4 -> ascending ? "menu.wrights.sort_least" : "menu.wrights.sort_most";
            case 5 -> ascending ? "menu.wrights.sort_oldest" : "menu.wrights.sort_newest";
            default -> "";
        };
        return id.isEmpty() ? "" : Component.translatable(id).getString();
    }

    boolean pickSort(double mouseX, double mouseY) {
        if (s.sortButton == null) {
            return false;
        }
        placeSortMenu();
        if (mouseX < sortMenuX || mouseX >= sortMenuX + sortMenuW || mouseY < sortMenuY || mouseY >= sortMenuY + sortMenuH) {
            return false;
        }
        int row = (int) ((mouseY - sortMenuY - 4) / 16);
        if (row >= 0 && row < SORT_LABELS.length) {
            chooseSort(row);
        }
        return true;
    }

    private void chooseSort(int key) {
        int current = WrightsClientConfig.sortKey(WrightsMenuScreen.savedTab);
        boolean ascending = WrightsClientConfig.sortAscending(WrightsMenuScreen.savedTab);
        if (key == BlueprintList.SortKey.MANUAL.ordinal()) {
            if (current == key) {
                return;
            }
            if (WrightsMenuScreen.savedTab != BlueprintList.Tab.ALL.ordinal() && WrightsClientConfig.manualOrder(WrightsMenuScreen.savedTab).isEmpty()) {
                List<UUID> ids = new ArrayList<>();
                for (BlueprintList.Row row : BlueprintList.rows(BlueprintList.Tab.values()[WrightsMenuScreen.savedTab], "")) {
                    ids.add(row.id());
                }
                WrightsClientConfig.setManualOrder(WrightsMenuScreen.savedTab, ids);
            }
            WrightsClientConfig.setListSort(WrightsMenuScreen.savedTab, key, true);
        } else {
            boolean next = key == current ? !ascending : key == BlueprintList.SortKey.NAME.ordinal();
            WrightsClientConfig.setListSort(WrightsMenuScreen.savedTab, key, next);
        }
        s.scroll = 0;
        s.refresh();
    }

    void rememberManual(UUID id, int toIndex) {
        List<UUID> visible = new ArrayList<>();
        for (BlueprintList.Row row : s.rows) {
            visible.add(row.id());
        }
        int from = visible.indexOf(id);
        if (from < 0) {
            return;
        }
        visible.remove(from);
        visible.add(Mth.clamp(toIndex, 0, visible.size()), id);
        List<UUID> merged = new ArrayList<>();
        int cursor = 0;
        for (UUID old : WrightsClientConfig.manualOrder(WrightsMenuScreen.savedTab)) {
            if (visible.contains(old)) {
                if (cursor < visible.size()) {
                    UUID next = visible.get(cursor++);
                    if (!merged.contains(next)) {
                        merged.add(next);
                    }
                }
            } else if (inLibrary(old) && !merged.contains(old)) {
                merged.add(old);
            }
        }
        while (cursor < visible.size()) {
            UUID next = visible.get(cursor++);
            if (!merged.contains(next)) {
                merged.add(next);
            }
        }
        WrightsClientConfig.setManualOrder(WrightsMenuScreen.savedTab, merged);
        s.refresh();
    }

    private static boolean inLibrary(UUID id) {
        for (BlueprintPayloads.Summary summary : WrightsClientState.library) {
            if (summary.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    void newFolder() {
        UUID id = UUID.randomUUID();
        BlueprintList.Row row = s.selectedRow();
        UUID parent = row == null ? Util.NIL_UUID : (row.folder() ? row.id() : row.parent());
        WrightsMenuScreen.savedTab = BlueprintList.Tab.ALL.ordinal();
        for (UUID folder : BlueprintList.chain(parent)) {
            WrightsClientState.setOpenLocal(folder, true);
            PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(BlueprintPayloads.ShelfEdit.OPEN, folder, true, Util.NIL_UUID, 1, ""));
        }
        s.pendingSelect = id;
        PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(
                BlueprintPayloads.ShelfEdit.CREATE,
                id,
                true,
                parent,
                0,
                Component.translatable("menu.wrights.new_folder").getString()));
        s.refresh();
    }

    void selectRow(UUID id) {
        int index = indexOf(id);
        if (index < 0) {
            return;
        }
        if (s.shelfRange && s.markAnchor != null && indexOf(s.markAnchor) >= 0) {
            int anchor = indexOf(s.markAnchor);
            int start = Math.min(anchor, index);
            int end = Math.max(anchor, index);
            if (!s.shelfAdditive) {
                s.marked.clear();
            }
            for (int i = start; i <= end; i++) {
                UUID rowId = s.rows.get(i).id();
                if (!s.marked.contains(rowId)) {
                    s.marked.add(rowId);
                }
            }
            WrightsClientState.selected = id;
            s.confirmDelete = null;
            s.confirmSet = List.of();
            s.refresh();
            return;
        }
        if (s.shelfAdditive) {
            if (s.marked.isEmpty() && WrightsClientState.selected != null && indexOf(WrightsClientState.selected) >= 0) {
                s.marked.add(WrightsClientState.selected);
            }
            if (s.marked.contains(id)) {
                s.marked.remove(id);
                if (id.equals(WrightsClientState.selected)) {
                    WrightsClientState.selected = s.marked.isEmpty() ? null : s.marked.get(s.marked.size() - 1);
                }
            } else {
                s.marked.add(id);
                WrightsClientState.selected = id;
                if (s.markAnchor == null) {
                    s.markAnchor = id;
                }
            }
            s.confirmDelete = null;
            s.confirmSet = List.of();
            s.refresh();
            return;
        }
        s.marked.clear();
        s.marked.add(id);
        s.markAnchor = id;
        activateRow(id);
    }

    void syncMarks() {
        s.marked.removeIf(id -> indexOf(id) < 0);
        if (s.markAnchor != null && indexOf(s.markAnchor) < 0) {
            s.markAnchor = null;
        }
        if (WrightsClientState.selected != null && indexOf(WrightsClientState.selected) < 0) {
            WrightsClientState.selected = s.marked.isEmpty() ? null : s.marked.get(s.marked.size() - 1);
        }
        if (s.marked.isEmpty() && WrightsClientState.selected != null && indexOf(WrightsClientState.selected) >= 0) {
            s.marked.add(WrightsClientState.selected);
        }
        if (s.markAnchor == null) {
            s.markAnchor = WrightsClientState.selected;
        }
        if (!s.marked.isEmpty() && (WrightsClientState.selected == null || !s.marked.contains(WrightsClientState.selected))) {
            WrightsClientState.selected = s.marked.get(s.marked.size() - 1);
        }
        if (!s.confirming()) {
            s.confirmSet = List.of();
            s.confirmDelete = null;
        }
    }

    List<BlueprintList.Row> markedRows() {
        List<BlueprintList.Row> picked = new ArrayList<>();
        for (BlueprintList.Row row : s.rows) {
            if (s.marked.contains(row.id())) {
                picked.add(row);
            }
        }
        if (picked.isEmpty()) {
            BlueprintList.Row row = s.selectedRow();
            if (row != null) {
                picked.add(row);
            }
        }
        return picked;
    }

    List<UUID> dragIds() {
        if (s.shelfPress == null) {
            return List.of();
        }
        if (s.marked.contains(s.shelfPress) && s.marked.size() > 1) {
            List<UUID> ids = new ArrayList<>();
            for (BlueprintList.Row row : s.rows) {
                if (s.marked.contains(row.id())) {
                    ids.add(row.id());
                }
            }
            if (!ids.isEmpty()) {
                return ids;
            }
        }
        return List.of(s.shelfPress);
    }

    private boolean dragHits(UUID target) {
        for (UUID id : dragIds()) {
            if (id.equals(target)) {
                return true;
            }
            BlueprintList.Row row = rowOf(id);
            if (row != null && row.folder() && BlueprintList.holds(id, target)) {
                return true;
            }
        }
        return false;
    }

    void dropMarkedInto(UUID parent, List<UUID> moving) {
        if (parent != null && !parent.equals(Util.NIL_UUID)) {
            WrightsClientState.setOpenLocal(parent, true);
            PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(
                    BlueprintPayloads.ShelfEdit.OPEN, parent, true, Util.NIL_UUID, 1, ""));
        }
        for (UUID id : moving) {
            BlueprintList.Row row = rowOf(id);
            if (row == null || nestedInDrag(id, moving)) {
                continue;
            }
            if (parent != null && (id.equals(parent) || (row.folder() && BlueprintList.holds(id, parent)))) {
                continue;
            }
            PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(
                    BlueprintPayloads.ShelfEdit.MOVE, id, row.folder(), parent, 1_000_000, ""));
        }
    }

    private boolean nestedInDrag(UUID id, List<UUID> moving) {
        BlueprintList.Row row = rowOf(id);
        if (row == null) {
            return false;
        }
        UUID parent = row.parent();
        for (int guard = 0; parent != null && !parent.equals(Util.NIL_UUID) && guard < 64; guard++) {
            if (moving.contains(parent)) {
                return true;
            }
            BlueprintList.Row folder = rowOf(parent);
            parent = folder == null ? null : folder.parent();
        }
        return false;
    }

    private int indexOf(UUID id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < s.rows.size(); i++) {
            if (s.rows.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private BlueprintList.Row rowOf(UUID id) {
        int index = indexOf(id);
        return index < 0 ? null : s.rows.get(index);
    }

    private void activateRow(UUID id) {
        BlueprintList.Row row = null;
        for (BlueprintList.Row candidate : s.rows) {
            if (candidate.id().equals(id)) {
                row = candidate;
                break;
            }
        }
        if (row == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean again = id.equals(WrightsClientState.selected) && now - lastClick < 350;
        WrightsClientState.selected = id;
        if (!id.equals(s.confirmDelete)) {
            s.confirmDelete = null;
            s.confirmSet = List.of();
        }
        lastClick = now;
        if (row.folder()) {
            if (WrightsMenuScreen.savedTab == BlueprintList.Tab.ALL.ordinal()) {
                boolean open = !WrightsClientState.openFolders.contains(id);
                WrightsClientState.setOpenLocal(id, open);
                PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(BlueprintPayloads.ShelfEdit.OPEN, id, true, Util.NIL_UUID, open ? 1 : 0, ""));
            }
            s.refresh();
            return;
        }
        if (again) {
            s.refresh();
            s.build();
            return;
        }
        s.refresh();
    }

    void updateDrop(double mouseX, double mouseY) {
        s.dropInto = false;
        s.dropHighlight = null;
        s.dropLineY = -1;
        s.dropIndex = -1;
        if (!s.insideList(mouseX, mouseY)) {
            s.dropParent = Util.NIL_UUID;
            s.dropIndex = 1_000_000;
            s.dropLineY = s.listY + Math.min(s.visibleRows(), Math.max(0, s.rows.size() - s.scroll)) * WrightsMenuScreen.ROW;
            return;
        }
        int shown = Math.min(s.visibleRows(), Math.max(0, s.rows.size() - s.scroll));
        int contentBottom = s.listY + shown * WrightsMenuScreen.ROW;
        if (s.rows.isEmpty() || mouseY >= contentBottom) {
            s.dropParent = Util.NIL_UUID;
            s.dropIndex = adjustedIndex(Util.NIL_UUID, 1_000_000);
            s.dropLineY = contentBottom;
            return;
        }
        int index = Mth.clamp(s.scroll + (int) ((mouseY - s.listY) / WrightsMenuScreen.ROW), 0, s.rows.size() - 1);
        BlueprintList.Row row = s.rows.get(index);
        int y = s.listY + (index - s.scroll) * WrightsMenuScreen.ROW;
        int local = (int) mouseY - y;
        if (row.folder() && local >= 4 && local < WrightsMenuScreen.ROW - 4) {
            if (dragHits(row.id())) {
                return;
            }
            s.dropInto = true;
            s.dropHighlight = row.id();
            s.dropParent = row.id();
            s.dropIndex = 1_000_000;
            return;
        }
        if (s.shelfPressFolder && BlueprintList.holds(s.shelfPress, row.parent())) {
            return;
        }
        boolean before = local < WrightsMenuScreen.ROW / 2;
        if (!before && row.folder() && index + 1 < s.rows.size() && row.id().equals(s.rows.get(index + 1).parent())) {
            if (s.shelfPress.equals(row.id()) || (s.shelfPressFolder && BlueprintList.holds(s.shelfPress, row.id()))) {
                return;
            }
            s.dropParent = row.id();
            s.dropIndex = adjustedIndex(row.id(), 0);
            s.dropLineY = y + WrightsMenuScreen.ROW - 1;
            return;
        }
        s.dropParent = row.parent();
        s.dropIndex = adjustedIndex(row.parent(), before ? row.index() : row.index() + 1);
        s.dropLineY = before ? y : y + WrightsMenuScreen.ROW - 1;
    }

    private int adjustedIndex(UUID parent, int index) {
        if (s.shelfPressParent.equals(parent) && s.shelfPressIndex < index) {
            return index - 1;
        }
        return index;
    }

    boolean clickShelf(double mouseX, double mouseY, int button) {
        if (button == 0 && !s.options && !s.jobsPage && !s.infoPage && s.sortOpen && pickSort(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && !s.options && !s.jobsPage && !s.infoPage && clickScope(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && !s.options && !s.jobsPage && !s.infoPage && clickTab(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && !s.options && !s.jobsPage && !s.infoPage && s.sortOpen && (s.sortButton == null || !s.sortButton.isMouseOver(mouseX, mouseY))) {
            s.sortOpen = false;
            if (s.sortButton != null) {
                s.sortButton.setPicked(false);
            }
            return true;
        }
        if (button == 0 && !s.options && !s.jobsPage && !s.infoPage && s.insideModel(mouseX, mouseY)) {
            s.spinning = true;
            return true;
        }
        if (button == 0 && !s.options && !s.jobsPage && !s.infoPage && s.insideList(mouseX, mouseY) && !s.rows.isEmpty() && !s.overWidget(mouseX, mouseY)) {
            int shown = (int) ((mouseY - s.listY) / WrightsMenuScreen.ROW);
            int index = s.scroll + shown;
            if (shown >= 0 && shown < s.visibleRows() && index < s.rows.size()) {
                BlueprintList.Row entry = s.rows.get(index);
                if (!entry.folder() && mouseX < s.listX + 14) {
                    WrightsClientState.toggleFavoriteLocal(entry.id());
                    PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(BlueprintPayloads.ShelfEdit.FAVORITE, entry.id(), false, Util.NIL_UUID, 0, ""));
                    s.refresh();
                    return true;
                }
                s.shelfPress = entry.id();
                s.shelfAdditive = Screen.hasControlDown();
                s.shelfRange = Screen.hasShiftDown();
                s.shelfPressFolder = entry.folder();
                s.shelfPressParent = entry.parent();
                s.shelfPressIndex = entry.index();
                s.shelfPressX = mouseX;
                s.shelfPressY = mouseY;
                s.shelfDragging = false;
                s.dropIndex = -1;
                return true;
            }
        }
        return false;
    }
}
