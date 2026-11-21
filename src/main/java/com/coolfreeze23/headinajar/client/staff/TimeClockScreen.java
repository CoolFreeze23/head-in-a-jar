package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.menu.TimeClockMenu;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Band;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.work.ClockNet;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerNet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The Time Clock's screen ({@link TimeClockMenu}), drawn with fills in the house's grey: four tabs under the title (the
 * Roster, the Job Rack, the Pay Drawer, the Shifts), the open one raised, and the player's inventory below on every tab.
 * Everything it shows is read from the menu each frame ({@code menu.roster}, the tab, the picked row); the screen keeps
 * only what is on screen: the scroll, and a name being typed.
 */
public class TimeClockScreen extends AbstractContainerScreen<TimeClockMenu> {
    private static final String KEY = "gui.headinajar.time_clock.";
    private static final String[] TAB_KEYS = {"roster", "rack", "drawer", "shifts"};
    // the panel's layout (panel px)
    private static final int X0 = 8, X1 = 168, TAB_Y = 15, TAB_H = 18, TAB_W = 39, TAB_STEP = 40, BODY_Y = TAB_Y + TAB_H;
    /** The roster's rows: eight of 10 px (the default clock's whole staff), then Rename and Recall. */
    private static final int ROW_Y = 35, ROW_H = 10, ROWS = 8, BUTTON_Y = ROW_Y + ROWS * ROW_H + 1, BUTTON_H = 12;
    /** The Shifts tab's rows: eight of 11 px, each with its three buttons. */
    private static final int SHIFT_H = 11, SHIFT_BUTTON_H = 10;
    /** Columns of a roster row: species, name, job card, shift, morale, status. */
    private static final int COL_NAME = 18, NAME_ROOM = 50, COL_JOB = 70, COL_SHIFT = 80, COL_FACE = 89, COL_STATUS = 99;
    private static final int SCROLL_W = 3;
    private static final int TEXT = 0x404040, FAINT = 0x8A8A8A, NOTE = 0x5A5A5A;
    private static final Pattern ARG = Pattern.compile("%(\\d+\\$)?s");

    private Button rename, recall;
    private EditBox nameBox;
    private Tooltip pickFirst, outOfReach;
    /** The first row shown (the Roster and the Shifts share it). */
    private int scroll;
    /** The worker whose name is being typed (its entity id), or -1. */
    private int renaming = -1;
    private final Map<EntityType<?>, ItemStack> eggs = new HashMap<>();
    private final Map<String, ItemStack> cards = new HashMap<>();
    private final ItemStack[] tabIcons;

    public TimeClockScreen(TimeClockMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = TimeClockMenu.WIDTH;
        imageHeight = TimeClockMenu.HEIGHT;
        inventoryLabelY = TimeClockMenu.INV_Y - 11;
        tabIcons = new ItemStack[]{new ItemStack(WorkRegistry.STAFF_LANYARD.get()), new ItemStack(WorkRegistry.JOB_CARD.get()),
                new ItemStack(Items.EMERALD), new ItemStack(Items.CLOCK)};
    }

    @Override
    protected void init() {
        super.init();
        Component renameLabel = Component.translatable(KEY + "rename");
        int renameW = Math.max(50, font.width(renameLabel) + 10), renameX = leftPos + X1 - renameW;
        String typed = nameBox != null ? nameBox.getValue() : "";
        nameBox = new EditBox(font, leftPos + X0 + 1, topPos + BUTTON_Y, renameX - leftPos - X0 - 4, BUTTON_H,
                Component.translatable("screen.headinajar.file.rename"));
        nameBox.setMaxLength(StaffFile.NAME);
        nameBox.setValue(typed);
        nameBox.setHint(Component.translatable("screen.headinajar.file.rename").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameBox);
        recall = addRenderableWidget(Button.builder(Component.translatable(KEY + "recall"), b -> recall())
                .bounds(leftPos + X0, topPos + BUTTON_Y, renameX - leftPos - X0 - 2, BUTTON_H).build());
        rename = addRenderableWidget(Button.builder(renameLabel, b -> pressRename()).bounds(renameX, topPos + BUTTON_Y, renameW, BUTTON_H).build());
        pickFirst = Tooltip.create(Component.translatable(KEY + "pick_first"));
        outOfReach = Tooltip.create(Component.translatable(KEY + "out_of_reach"));
        sync();
        if (renaming >= 0) setFocused(nameBox);
    }

    // --- what the menu says -------------------------------------------------------------------------------------------

    private List<ClockNet.Row> rows() {
        ClockNet.ClockRoster r = menu.roster;
        return r == null ? List.of() : r.rows();
    }

    /** The picked row, if it is still on the roster. */
    private ClockNet.Row picked() {
        List<ClockNet.Row> rows = rows();
        int s = menu.selected();
        return s >= 0 && s < rows.size() ? rows.get(s) : null;
    }

    private boolean textChannel() {
        var connection = minecraft.getConnection();
        return connection != null && connection.hasChannel(WorkerNet.Text.TYPE);
    }

    /** The buttons under the roster as the menu stands: shown on the Roster with rows, live with a row picked. */
    private void sync() {
        if (rename == null) return;
        List<ClockNet.Row> rows = rows();
        boolean roster = menu.tab() == TimeClockMenu.ROSTER && !rows.isEmpty();
        ClockNet.Row row = picked();
        if (renaming >= 0 && (!roster || row == null || row.entity() != renaming)) stopRenaming();
        boolean typing = renaming >= 0;
        rename.visible = roster;
        rename.active = row != null && row.entity() >= 0 && textChannel();
        rename.setTooltip(row == null ? pickFirst : row.entity() < 0 ? outOfReach : null);
        recall.visible = roster && !typing;
        recall.active = row != null;
        recall.setTooltip(row == null ? pickFirst : null);
        nameBox.visible = roster && typing;
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows()));
    }

    private int visibleRows() {
        return ROWS;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        sync();
    }

    // --- pressing things ----------------------------------------------------------------------------------------------

    private void send(int id) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    /** An order for the worker on a row, by its UUID (never by the row's place, which may have moved). */
    private void order(int kind, ClockNet.Row row, int arg) {
        var connection = minecraft.getConnection();
        if (connection != null && connection.hasChannel(ClockNet.ClockOrder.TYPE)) PacketDistributor.sendToServer(new ClockNet.ClockOrder(kind, row.id(), arg));
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private void showTab(int t) {
        if (t == menu.tab()) return;
        menu.showTab(t);
        send(TimeClockMenu.TAB + t);
        click();
        sync();
    }

    private void pick(int row) {
        menu.select(row);
        click();
        sync();
    }

    private void recall() {
        ClockNet.Row row = picked();
        if (row == null) return;
        order(ClockNet.RECALL, row, 0);
    }

    private void pressShift(int row, int shift) {
        menu.select(row);
        List<ClockNet.Row> rows = rows();
        if (row >= 0 && row < rows.size()) order(ClockNet.SHIFT, rows.get(row), shift);
        click();
        sync();
    }

    /** The first press opens the name for typing; the second (or Enter) files it. */
    private void pressRename() {
        ClockNet.Row row = picked();
        if (row == null || row.entity() < 0) return;
        if (renaming != row.entity()) {
            renaming = row.entity();
            String name = row.name().getString();
            nameBox.setValue(name.length() > StaffFile.NAME ? name.substring(0, StaffFile.NAME) : name);
            nameBox.moveCursorToEnd(false);
            nameBox.setHighlightPos(0);
            sync();
            setFocused(nameBox);
            return;
        }
        fileName();
    }

    /** Sends the name typed; the server checks the worker is still this clock's and yours, and cleans the name. */
    private void fileName() {
        if (renaming >= 0 && textChannel()) PacketDistributor.sendToServer(new WorkerNet.Text(WorkerNet.RENAME, renaming, nameBox.getValue()));
        stopRenaming();
        sync();
    }

    private void stopRenaming() {
        renaming = -1;
        if (getFocused() == nameBox) setFocused(null);
        if (nameBox != null) nameBox.setFocused(false);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (renaming >= 0) {
            if (key == 256) { // Escape puts the pen down
                stopRenaming();
                sync();
                return true;
            }
            if (key == 257 || key == 335) {
                fileName();
                return true;
            }
            if (nameBox.isFocused() && (nameBox.keyPressed(key, scan, modifiers) || nameBox.canConsumeInput())) return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int x = (int) Math.floor(mx) - leftPos, y = (int) Math.floor(my) - topPos;
            for (int t = 0; t < 4; t++) {
                int tx = X0 + t * TAB_STEP;
                if (x >= tx && x < tx + TAB_W && y >= TAB_Y && y < BODY_Y) {
                    showTab(t);
                    return true;
                }
            }
            List<ClockNet.Row> rows = rows();
            int tab = menu.tab();
            if (!rows.isEmpty() && (tab == TimeClockMenu.ROSTER || tab == TimeClockMenu.SHIFTS)) {
                int h = tab == TimeClockMenu.ROSTER ? ROW_H : SHIFT_H, x1 = listRight(rows);
                if (rows.size() > visibleRows() && x >= x1 + 1 && x < X1 && y >= ROW_Y && y < ROW_Y + visibleRows() * h) {
                    // the scroll bar's track pages up or down
                    scroll += y < ROW_Y + thumbY(rows, h) ? -visibleRows() : visibleRows();
                    sync();
                    return true;
                }
                int r = rowAt(rows, x, y, h, x1);
                if (r >= 0) {
                    if (tab == TimeClockMenu.SHIFTS) {
                        int[] b = shiftButtons(x1);
                        for (int k = 0; k < 3; k++) {
                            if (x >= b[k * 2] && x < b[k * 2] + b[k * 2 + 1]) {
                                pressShift(r, k);
                                return true;
                            }
                        }
                    }
                    pick(r);
                    return true;
                }
            }
        }
        boolean used = super.mouseClicked(mx, my, button);
        // the screen hands its focus to the button clicked (Rename): while a name is being written the box keeps it
        if (renaming >= 0 && nameBox != null && nameBox.visible) setFocused(nameBox);
        return used;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int tab = menu.tab();
        List<ClockNet.Row> rows = rows();
        if ((tab == TimeClockMenu.ROSTER || tab == TimeClockMenu.SHIFTS) && rows.size() > visibleRows() && sy != 0) {
            scroll += sy > 0 ? -1 : 1;
            sync();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private int listRight(List<ClockNet.Row> rows) {
        return rows.size() > visibleRows() ? X1 - SCROLL_W - 2 : X1;
    }

    /** The roster row under the pointer (panel px), or -1. */
    private int rowAt(List<ClockNet.Row> rows, int x, int y, int h, int x1) {
        if (x < X0 || x >= x1 || y < ROW_Y) return -1;
        int i = (y - ROW_Y) / h;
        if (i >= visibleRows()) return -1;
        int r = scroll + i;
        return r < rows.size() ? r : -1;
    }

    private int thumbH(List<ClockNet.Row> rows, int h) {
        int track = visibleRows() * h;
        return Math.max(6, track * visibleRows() / rows.size());
    }

    private int thumbY(List<ClockNet.Row> rows, int h) {
        int track = visibleRows() * h, room = rows.size() - visibleRows();
        return room <= 0 ? 0 : (track - thumbH(rows, h)) * scroll / room;
    }

    /** The Shifts tab's three buttons on a row ending at x1: x and width of Day, Night, Redstone (panel px). */
    private int[] shiftButtons(int x1) {
        int[] out = new int[6];
        int x = x1;
        for (int k = 2; k >= 0; k--) {
            int w = font.width(shiftName(k)) + 6;
            x -= w;
            out[k * 2] = x;
            out[k * 2 + 1] = w;
            x -= 1;
        }
        return out;
    }

    // --- drawing ------------------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        sync();
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        List<Component> tip = tooltip(mouseX - leftPos, mouseY - topPos);
        if (tip != null && menu.getCarried().isEmpty() && hoveredSlot == null) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // the open tab's name top right, opposite the clock's; where the two do not fit side by side, the tab's alone
        Component tab = Component.translatable(KEY + TAB_KEYS[menu.tab()]);
        int tabX = X1 - font.width(tab);
        if (titleLabelX + font.width(title) + 6 > tabX) {
            g.drawString(font, cut(tab, X1 - X0), titleLabelX, titleLabelY, TEXT, false);
        } else {
            g.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
            g.drawString(font, tab, tabX, titleLabelY, NOTE, false);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF000000);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFFFFFFFF);
        g.fill(x + 3, y + 3, x + imageWidth - 1, y + imageHeight - 1, 0xFF555555);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFFC6C6C6);
        tabs(g, mouseX - x, mouseY - y);
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            int sx = x + slot.x, sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
            g.fill(sx, sy, sx + 17, sy + 17, 0xFFFFFFFF);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
        }
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        int mx = mouseX - x, my = mouseY - y;
        switch (menu.tab()) {
            case TimeClockMenu.ROSTER -> roster(g, mx, my);
            case TimeClockMenu.RACK -> rack(g);
            case TimeClockMenu.DRAWER -> drawer(g);
            case TimeClockMenu.SHIFTS -> shifts(g, mx, my);
            default -> {
            }
        }
        pose.popPose();
    }

    /** Four flat tabs under the title; the open one raised and joined to the panel below, the rest set back. */
    private void tabs(GuiGraphics g, int mx, int my) {
        int x = leftPos, y = topPos, open = menu.tab();
        // the panel's edge under the tabs, broken where the open tab joins it
        g.fill(x + 4, y + BODY_Y - 1, x + imageWidth - 4, y + BODY_Y, 0xFF555555);
        g.fill(x + 4, y + BODY_Y, x + imageWidth - 4, y + BODY_Y + 1, 0xFFFFFFFF);
        for (int t = 0; t < 4; t++) {
            int tx = x + X0 + t * TAB_STEP, ty = y + TAB_Y;
            boolean up = t == open, hover = !up && mx >= X0 + t * TAB_STEP && mx < X0 + t * TAB_STEP + TAB_W && my >= TAB_Y && my < BODY_Y;
            if (up) {
                g.fill(tx, ty, tx + TAB_W, y + BODY_Y + 1, 0xFF000000);
                g.fill(tx + 1, ty + 1, tx + TAB_W - 1, y + BODY_Y + 1, 0xFFFFFFFF);
                g.fill(tx + 3, ty + 3, tx + TAB_W - 1, y + BODY_Y + 1, 0xFF555555);
                g.fill(tx + 3, ty + 3, tx + TAB_W - 3, y + BODY_Y + 1, 0xFFC6C6C6);
                g.renderItem(tabIcons[t], tx + (TAB_W - 16) / 2, ty + 1);
            } else {
                g.fill(tx, ty + 2, tx + TAB_W, y + BODY_Y - 1, 0xFF373737);
                g.fill(tx + 1, ty + 3, tx + TAB_W - 1, y + BODY_Y - 1, hover ? 0xFFB4B4B4 : 0xFF9C9C9C);
                g.fill(tx + 1, ty + 3, tx + TAB_W - 1, ty + 4, hover ? 0xFFD0D0D0 : 0xFFB8B8B8);
                g.renderItem(tabIcons[t], tx + (TAB_W - 16) / 2, ty + 2);
            }
        }
    }

    private void roster(GuiGraphics g, int mx, int my) {
        if (menu.roster == null) {
            wrapped(g, Component.translatable(KEY + "no_rows"), ROW_Y + 2, 6);
            return;
        }
        List<ClockNet.Row> rows = rows();
        if (rows.isEmpty()) {
            wrapped(g, Component.translatable(KEY + "nobody"), ROW_Y + 2, 6);
            return;
        }
        int x1 = listRight(rows), hovered = rowAt(rows, mx, my, ROW_H, x1);
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            int r = scroll + i, y = ROW_Y + i * ROW_H;
            ClockNet.Row row = rows.get(r);
            rowBand(g, r, y, ROW_H, x1, hovered);
            boolean far = row.entity() < 0;
            int color = far ? FAINT : TEXT;
            icon(g, egg(row), X0, y + 1, far);
            g.drawString(font, cut(row.name(), NAME_ROOM), COL_NAME, y + 1, color, false);
            if (!row.job().isEmpty()) icon(g, card(row.job()), COL_JOB, y + 1, far);
            shiftPip(g, row.shift(), COL_SHIFT, y + 1);
            face(g, row.face(), COL_FACE, y + 1);
            g.drawString(font, cut(status(row), x1 - COL_STATUS - 1), COL_STATUS, y + 1, color, false);
        }
        scrollBar(g, rows, ROW_H);
    }

    private void shifts(GuiGraphics g, int mx, int my) {
        if (menu.roster == null) {
            wrapped(g, Component.translatable(KEY + "no_rows"), ROW_Y + 2, 6);
            return;
        }
        List<ClockNet.Row> rows = rows();
        if (rows.isEmpty()) {
            wrapped(g, Component.translatable(KEY + "nobody"), ROW_Y + 2, 6);
            return;
        }
        int x1 = listRight(rows), hovered = rowAt(rows, mx, my, SHIFT_H, x1);
        int[] b = shiftButtons(x1);
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            int r = scroll + i, y = ROW_Y + i * SHIFT_H;
            ClockNet.Row row = rows.get(r);
            rowBand(g, r, y, SHIFT_H, x1, -1);
            boolean far = row.entity() < 0;
            icon(g, egg(row), X0, y + 1, far);
            g.drawString(font, cut(row.name(), b[0] - COL_NAME - 3), COL_NAME, y + 1, far ? FAINT : TEXT, false);
            for (int k = 0; k < 3; k++) {
                boolean on = row.shift() == k, dim = k == WorkerData.Shift.DAY.ordinal() && row.noDay();
                boolean hover = r == hovered && mx >= b[k * 2] && mx < b[k * 2] + b[k * 2 + 1];
                smallButton(g, b[k * 2], y, b[k * 2 + 1], SHIFT_BUTTON_H, shiftName(k), on, dim, hover);
            }
        }
        scrollBar(g, rows, SHIFT_H);
    }

    private void rack(GuiGraphics g) {
        ClockNet.ClockRoster roster = menu.roster;
        if (roster != null) {
            List<Integer> takers = roster.takers();
            for (int i = 0; i < takers.size() && i < 8; i++) {
                String n = Integer.toString(takers.get(i));
                int cx = TimeClockMenu.RACK_X + i * 18 + 8;
                g.drawString(font, n, cx - font.width(n) / 2, TimeClockMenu.RACK_Y + 19, takers.get(i) > 0 ? TEXT : FAINT, false);
            }
        }
        wrapped(g, Component.translatable(KEY + "rack_hint"), TimeClockMenu.RACK_Y + 34, 4);
    }

    private void drawer(GuiGraphics g) {
        ClockNet.ClockRoster roster = menu.roster;
        int y = TimeClockMenu.DRAWER_Y + 22;
        if (roster == null) {
            wrapped(g, Component.translatable(KEY + "no_rows"), y, 5);
            return;
        }
        g.drawString(font, cut(Component.translatable(KEY + "owed"), X1 - X0), X0, y, TEXT, false);
        List<ClockNet.Owed> owed = roster.owed();
        if (owed.isEmpty()) {
            wrapped(g, Component.translatable(KEY + "nothing_owed"), y + 12, 4);
            return;
        }
        for (int i = 0; i < owed.size() && i < 18; i++) {
            ClockNet.Owed o = owed.get(i);
            ItemStack stack = new ItemStack(o.asItem());
            int ix = X0 + (i % 9) * 18, iy = y + 11 + (i / 9) * 18;
            g.renderItem(stack, ix, iy);
            g.renderItemDecorations(font, stack, ix, iy, Integer.toString(o.count()));
        }
    }

    /** A row's ground: striped, lighter under the pointer, blue when picked. */
    private void rowBand(GuiGraphics g, int r, int y, int h, int x1, int hovered) {
        int color = r == menu.selected() ? 0xFF93A9BF : r == hovered ? 0xFFD6D6D6 : r % 2 == 1 ? 0xFFBCBCBC : 0;
        if (color != 0) g.fill(X0 - 1, y, x1, y + h, color);
    }

    private void scrollBar(GuiGraphics g, List<ClockNet.Row> rows, int h) {
        if (rows.size() <= visibleRows()) return;
        int x0 = X1 - SCROLL_W, track = visibleRows() * h;
        g.fill(x0 - 1, ROW_Y - 1, X1 + 1, ROW_Y + track + 1, 0xFF373737);
        g.fill(x0, ROW_Y, X1, ROW_Y + track, 0xFF8B8B8B);
        int ty = ROW_Y + thumbY(rows, h);
        g.fill(x0, ty, X1, ty + thumbH(rows, h), 0xFFE0E0E0);
        g.fill(x0 + 1, ty + 1, X1, ty + thumbH(rows, h), 0xFFAAAAAA);
    }

    /** A small flat button: raised, sunk when {@code on}, faded when {@code dim}. */
    private void smallButton(GuiGraphics g, int x, int y, int w, int h, Component label, boolean on, boolean dim, boolean hover) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        if (on) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF5E6E7E);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF404C58);
        } else {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, dim ? 0xFFB0B0B0 : hover ? 0xFFDADADA : 0xFFC6C6C6);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFF0F0F0);
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF8B8B8B);
        }
        int color = on ? 0xFFFFFF : dim ? 0x7A7A7A : TEXT;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 1, color, false);
    }

    /** Text wrapped across the panel from y, at most {@code lines} lines (the last cut with "..."). */
    private void wrapped(GuiGraphics g, Component text, int y, int lines) {
        List<FormattedCharSequence> split = font.split(text, X1 - X0);
        for (int i = 0; i < split.size() && i < lines; i++) {
            g.drawString(font, split.get(i), X0, y + i * 10, NOTE, false);
        }
    }

    /** An item drawn at half size (8 px), faded for a worker out of reach. */
    private void icon(GuiGraphics g, ItemStack stack, int x, int y, boolean faded) {
        if (stack.isEmpty()) return;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(0.5f, 0.5f, 0.5f);
        g.renderItem(stack, 0, 0);
        pose.popPose();
        if (faded) {
            pose.pushPose();
            pose.translate(0, 0, 300);
            g.fill(x, y, x + 8, y + 8, 0xA0C6C6C6);
            pose.popPose();
        }
    }

    /** A 7 px picture from rows of characters: each character's colour from {@code colors} ('.' is none). */
    private static void pixels(GuiGraphics g, int x, int y, String[] rows, Map<Character, Integer> colors) {
        for (int j = 0; j < rows.length; j++) {
            String row = rows[j];
            for (int i = 0; i < row.length(); i++) {
                Integer c = colors.get(row.charAt(i));
                if (c != null) g.fill(x + i, y + j, x + i + 1, y + j + 1, c);
            }
        }
    }

    private static final String[] SUN = {"...o...", ".o...o.", "..yyy..", "oyyyyyo", "..yyy..", ".o...o.", "...o..."};
    private static final String[] MOON = {"..mm...", ".mm....", "mm.....", "mm.....", "mm.....", ".mm....", "..mm..."};
    private static final String[] DUST = {"...d...", "..rrr..", ".rrrrr.", "drrbrrd", ".rrrrr.", "..rrr..", "...d..."};
    private static final Map<Character, Integer> SHIFT_COLORS = Map.of('o', 0xFFD08A28, 'y', 0xFFF2C744, 'm', 0xFF5A6E9C,
            'r', 0xFFB81C1C, 'd', 0xFF6E1010, 'b', 0xFFFF5A4A);

    private static void shiftPip(GuiGraphics g, int shift, int x, int y) {
        String[] p = switch (shift) {
            case 0 -> SUN;
            case 1 -> MOON;
            case 2 -> DUST;
            default -> null;
        };
        if (p != null) pixels(g, x, y, p, SHIFT_COLORS);
    }

    // the morale faces: a smile, a slight one, a flat line, a flat line under a frown
    private static final String[] ENGAGED = {".fffff.", "fffffff", "ffkfkff", "fffffff", "fkfffkf", "ffkkkff", ".fffff."};
    private static final String[] CONTENT = {".fffff.", "fffffff", "ffkfkff", "fffffff", "ffffffk", "fkkkkff", ".fffff."};
    private static final String[] DISENGAGED = {".fffff.", "fffffff", "ffkfkff", "fffffff", "fffffff", "fkkkkkf", ".fffff."};
    private static final String[] GRIEVANCE = {".fffff.", "fkfffkf", "ffkfkff", "fffffff", "fffffff", "fkkkkkf", ".fffff."};

    private static void face(GuiGraphics g, int face, int x, int y) {
        Band band = face >= 0 && face < Band.values().length ? Band.values()[face] : Band.NONE;
        String[] p;
        int skin;
        switch (band) {
            case ENGAGED -> {
                p = ENGAGED;
                skin = 0xFF7CC45A;
            }
            case CONTENT -> {
                p = CONTENT;
                skin = 0xFFC2CC5A;
            }
            case DISENGAGED -> {
                p = DISENGAGED;
                skin = 0xFFD9B04E;
            }
            case GRIEVANCE -> {
                p = GRIEVANCE;
                skin = 0xFFD0604A;
            }
            default -> {
                return;
            }
        }
        pixels(g, x, y, p, Map.of('f', skin, 'k', 0xFF2A2A2A));
    }

    // --- words --------------------------------------------------------------------------------------------------------

    /** A line cut with "..." to {@code room} px. */
    private FormattedCharSequence cut(Component text, int room) {
        if (font.width(text) <= room) return text.getVisualOrderText();
        FormattedText part = font.substrByWidth(text, Math.max(0, room - font.width("...")));
        return Language.getInstance().getVisualOrder(FormattedText.composite(part, FormattedText.of("...")));
    }

    private static Component shiftName(int shift) {
        WorkerData.Shift[] all = WorkerData.Shift.values();
        return shift >= 0 && shift < all.length
                ? Component.translatable("staff.headinajar.shift." + all[shift].name().toLowerCase(Locale.ROOT)) : Component.literal("?");
    }

    private static Component jobName(String job) {
        return job.isEmpty() ? Component.translatable(KEY + "no_job") : Component.translatable("staff.headinajar.job." + job);
    }

    /**
     * A row's status. A stop line sent without its argument (the tool it is out of, say) loses the sentence that needed
     * it; one that is nothing but that sentence takes the worker's name.
     */
    static Component status(ClockNet.Row row) {
        String key = row.status();
        Language lang = Language.getInstance();
        if (key.isEmpty()) return Component.empty();
        if (!lang.has(key)) return Component.translatable(key);
        String raw = lang.getOrDefault(key);
        if (!ARG.matcher(raw).find()) return Component.translatable(key);
        StringBuilder kept = new StringBuilder();
        for (String sentence : raw.split("(?<=[.!?])\\s+")) {
            if (ARG.matcher(sentence).find()) continue;
            if (!kept.isEmpty()) kept.append(' ');
            kept.append(sentence);
        }
        return kept.isEmpty() ? Component.translatable(key, row.name()) : Component.literal(kept.toString().replace("%%", "%"));
    }

    private ItemStack egg(ClockNet.Row row) {
        EntityType<?> type = row.type();
        if (type == null) return ItemStack.EMPTY;
        return eggs.computeIfAbsent(type, t -> {
            SpawnEggItem egg = SpawnEggItem.byId(t);
            return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
        });
    }

    /** A Job Card printed for a job, for its look. */
    private ItemStack card(String job) {
        return cards.computeIfAbsent(job, j -> {
            ItemStack stack = new ItemStack(WorkRegistry.JOB_CARD.get());
            stack.set(WorkRegistry.JOB_CARD_DATA.get(), new Order.Card(Order.EMPTY.withJob(j), "", 0L));
            return stack;
        });
    }

    /** What the pointer is over (panel px), as tooltip lines; null for nothing. */
    private List<Component> tooltip(int x, int y) {
        for (int t = 0; t < 4; t++) {
            int tx = X0 + t * TAB_STEP;
            if (x >= tx && x < tx + TAB_W && y >= TAB_Y && y < BODY_Y) return List.of(Component.translatable(KEY + TAB_KEYS[t]));
        }
        ClockNet.ClockRoster roster = menu.roster;
        if (roster == null) return null;
        List<ClockNet.Row> rows = roster.rows();
        switch (menu.tab()) {
            case TimeClockMenu.ROSTER -> {
                int r = rowAt(rows, x, y, ROW_H, listRight(rows));
                return r < 0 ? null : rowTooltip(rows.get(r));
            }
            case TimeClockMenu.SHIFTS -> {
                int x1 = listRight(rows), r = rowAt(rows, x, y, SHIFT_H, x1);
                if (r < 0) return null;
                ClockNet.Row row = rows.get(r);
                int[] b = shiftButtons(x1);
                if (row.noDay() && x >= b[0] && x < b[0] + b[1]) {
                    return List.of(Component.translatable("refusal.headinajar.shift.no_hard_hat").withStyle(ChatFormatting.RED));
                }
                return x < b[0] ? rowTooltip(row) : null;
            }
            case TimeClockMenu.RACK -> {
                List<Integer> takers = roster.takers();
                for (int i = 0; i < takers.size() && i < 8; i++) {
                    int sx = TimeClockMenu.RACK_X + i * 18;
                    if (x >= sx && x < sx + 16 && y >= TimeClockMenu.RACK_Y + 17 && y < TimeClockMenu.RACK_Y + 28) {
                        return List.of(Component.translatable(KEY + "takers", takers.get(i)));
                    }
                }
            }
            case TimeClockMenu.DRAWER -> {
                List<ClockNet.Owed> owed = roster.owed();
                int y0 = TimeClockMenu.DRAWER_Y + 33;
                for (int i = 0; i < owed.size() && i < 18; i++) {
                    int ix = X0 + (i % 9) * 18, iy = y0 + (i / 9) * 18;
                    if (x >= ix && x < ix + 16 && y >= iy && y < iy + 16) {
                        ClockNet.Owed o = owed.get(i);
                        return List.of(Component.empty().append(new ItemStack(o.asItem()).getHoverName()).append(" x" + o.count()));
                    }
                }
            }
            default -> {
            }
        }
        return null;
    }

    /** A worker's whole row: name, number and species, job, shift, morale, status. */
    private static List<Component> rowTooltip(ClockNet.Row row) {
        List<Component> tip = new ArrayList<>();
        tip.add(row.name());
        Component species = row.type() == null ? null : row.type().getDescription();
        if (row.number() > 0 || species != null) {
            var line = Component.empty().withStyle(ChatFormatting.GRAY);
            if (row.number() > 0) line.append(Component.translatable(KEY + "number", Staff.number(row.number())));
            if (row.number() > 0 && species != null) line.append(" · ");
            if (species != null) line.append(species);
            tip.add(line);
        }
        tip.add(Component.translatable(KEY + "job_line", jobName(row.job())).withStyle(ChatFormatting.GRAY));
        if (row.shift() >= 0) tip.add(Component.translatable(KEY + "shift_line", shiftName(row.shift())).withStyle(ChatFormatting.GRAY));
        Band band = row.face() >= 0 && row.face() < Band.values().length ? Band.values()[row.face()] : Band.NONE;
        if (band != Band.NONE && band != Band.TROPHY) tip.add(Component.translatable(KEY + "morale_line", Component.translatable(band.key())).withStyle(ChatFormatting.GRAY));
        tip.add(status(row).copy().withStyle(ChatFormatting.YELLOW));
        return tip;
    }
}
