package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.menu.ClipboardMenu;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerNet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Clipboard's order screen ({@link ClipboardMenu}): a sheet of lined paper on a board. Left, the site, the four
 * jobs, the areas, the order's name and Print; under them the inventory. Right, the twelve stop rows, each with its
 * unmark cross, its line (crossed out in red when its block is gone), and for a Drop-Off its "Everything else" box and
 * its ghost filter slot (the menu's slot of the same index, which sits on the row). Everything is read from the held
 * Clipboard's order as it syncs; every press goes to the server as a menu button, a ghost slot click or the name.
 */
public class ClipboardScreen extends AbstractContainerScreen<ClipboardMenu> {
    /** The sheet. */
    static final int W = 412, H = 240;
    /** The left column (site, jobs, areas, name) and the right one (stops); a stop row's text starts after its cross. */
    private static final int LX = 8, LW = 190, RX = 206, TEXT_X = RX + 13, RIGHT_END = W - 8;
    private static final int SITE_Y = 18, JOB_Y = 38, JOB_W = 94, JOB_H = 13, DESC_Y = 69, AREAS_Y = 90, NAME_Y = 142, NAME_H = 14, LINE = 9;
    /** The "Everything else" box and where a row's text must stop (the box, then the ghost slot's frame). */
    private static final int CHECK = 12, CHECK_X = ClipboardMenu.FILTER_X - 15, TEXT_END = ClipboardMenu.FILTER_X - 18;
    /** A Sorting Drop-Off's "more filters" box, left of its "Everything else" box; its line then stops before it. */
    private static final int MORE_X = CHECK_X - CHECK - 2, MORE_TEXT_END = MORE_X - 3;
    /** Ink and paper. Typed values in blue-black ink, printed labels in faded brown, a Drop-Off in green ink. */
    private static final int INK = 0xFF34405E, LABEL = 0xFF7A6544, DROP_INK = 0xFF2F5D3A, RED = 0xFFA02828, EDGE = 0xFFB89B62;
    private static final int PAPER = 0xFFF3ECDA, FIELD = 0xFFFBF7EC, RULE = 0xFFE2D5B6, MARGIN = 0xFFDDA6A0, HIGHLIGHT = 0xFFF4E07A;
    private static final int BOARD = 0xFF7B5530, BOARD_EDGE = 0xFF4E341C, STEEL = 0xFF9AA3AB, STEEL_DARK = 0xFF5E666E;
    /** Ticks after the last key typed before the name goes to the server. */
    private static final int NAME_WAIT = 20;

    private final Tab[] jobs = new Tab[WorkTable.JOBS.size()];
    private final Tab[] unmarkStop = new Tab[ClipboardMenu.GHOSTS], everything = new Tab[ClipboardMenu.GHOSTS];
    /** A Sorting Drop-Off's "more filters" box (0.7.2): opens its eight extra filter slots in the left column. */
    private final Tab[] more = new Tab[ClipboardMenu.GHOSTS];
    private final Tab[] unmarkArea = new Tab[Order.MAX_AREAS];
    /** What the pointer may ask about, drawn this frame (sheet px). */
    private final List<Tip> tips = new ArrayList<>();
    private EditBox name;
    private String sent;
    private int nameWait, paper;

    private record Tip(int x0, int y0, int x1, int y1, List<FormattedCharSequence> lines) {
    }

    public ClipboardScreen(ClipboardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
    }

    // --- widgets ------------------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < jobs.length; i++) {
            String id = WorkTable.JOBS.get(i);
            int button = ClipboardMenu.JOB + i;
            jobs[i] = addRenderableWidget(new Tab(leftPos + LX + (i % 2) * (JOB_W + 2), topPos + JOB_Y + (i / 2) * (JOB_H + 2), JOB_W, JOB_H,
                    Component.translatable("staff.headinajar.job." + id), Tab.TAB, () -> press(button)));
            jobs[i].setTooltip(Tooltip.create(Component.translatable("gui.headinajar.clipboard.job." + id)));
        }
        for (int i = 0; i < ClipboardMenu.GHOSTS; i++) {
            int y = topPos + rowY(i), unmark = ClipboardMenu.UNMARK_STOP + i, toggle = ClipboardMenu.EVERYTHING_ELSE + i;
            unmarkStop[i] = addRenderableWidget(new Tab(leftPos + RX, y + 4, 10, 10, Component.translatable("gui.headinajar.clipboard.unmark"), Tab.CROSS, () -> press(unmark)));
            unmarkStop[i].setTooltip(Tooltip.create(Component.translatable("gui.headinajar.clipboard.unmark")));
            everything[i] = addRenderableWidget(new Tab(leftPos + CHECK_X, y + 3, CHECK, CHECK, Component.translatable("gui.headinajar.clipboard.everything_else"), Tab.CHECK,
                    () -> press(toggle)));
            everything[i].setTooltip(Tooltip.create(Component.translatable("gui.headinajar.clipboard.everything_else")));
            int stop = i, pick = ClipboardMenu.MORE + i;
            more[i] = addRenderableWidget(new Tab(leftPos + MORE_X, y + 3, CHECK, CHECK, Component.translatable("gui.headinajar.clipboard.more"), Tab.MORE, () -> {
                // picked at once on this side; the server agrees (or refuses, and puts it back)
                menu.pick(stop);
                press(pick);
            }));
            more[i].setTooltip(Tooltip.create(Component.translatable("gui.headinajar.clipboard.more")));
        }
        for (int i = 0; i < unmarkArea.length; i++) {
            int unmark = ClipboardMenu.UNMARK_AREA + i;
            unmarkArea[i] = addRenderableWidget(new Tab(leftPos + LX, topPos + areaY(i) - 1, 9, 9, Component.translatable("gui.headinajar.clipboard.unmark"), Tab.CROSS,
                    () -> press(unmark)));
            unmarkArea[i].setTooltip(Tooltip.create(Component.translatable("gui.headinajar.clipboard.unmark")));
        }
        // the name field (drawn as a paper field in renderBg) and Print beside it
        int printW = printWidth();
        String was = name != null ? name.getValue() : menu.order().name();
        if (sent == null) sent = menu.order().name();
        name = new EditBox(font, leftPos + LX + 3, topPos + NAME_Y + 3, LW - 4 - printW - 6, LINE, Component.translatable("gui.headinajar.clipboard.name"));
        name.setBordered(false);
        name.setMaxLength(Order.NAME);
        name.setTextColor(INK);
        name.setValue(was);
        name.setHint(Component.translatable("gui.headinajar.clipboard.name").withColor(0xA8977A));
        name.setResponder(s -> nameWait = NAME_WAIT);
        addRenderableWidget(name);
        addRenderableWidget(new Tab(leftPos + LX + LW - printW, topPos + NAME_Y, printW, NAME_H, Component.translatable("gui.headinajar.clipboard.print"), Tab.PRINT, () -> {
            sendName();
            press(ClipboardMenu.PRINT);
        }));
        sync();
    }

    private int printWidth() {
        return Math.max(50, font.width(Component.translatable("gui.headinajar.clipboard.print")) + 12);
    }

    private static int rowY(int i) {
        return ClipboardMenu.FILTER_Y - 1 + i * ClipboardMenu.ROW;
    }

    private static int areaY(int i) {
        return AREAS_Y + 10 + i * 10;
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    /** Which widgets show, and which are marked, for the order as it stands now. */
    private void sync() {
        Order o = menu.order();
        for (int i = 0; i < jobs.length; i++) jobs[i].on = WorkTable.JOBS.get(i).equals(o.job());
        for (int i = 0; i < unmarkStop.length; i++) {
            Order.Stop s = i < o.stops().size() ? o.stops().get(i) : null;
            unmarkStop[i].visible = s != null;
            everything[i].visible = s != null && s.dropOff();
            everything[i].on = s != null && s.dropOff() && s.everythingElse();
            more[i].visible = ClipboardMenu.pickable(o, i);
            more[i].on = more[i].visible && menu.picked() == i;
        }
        // a Drop-Off's extra filters take the areas' place while they are open
        boolean extras = menu.picked() >= 0;
        for (int i = 0; i < unmarkArea.length; i++) unmarkArea[i].visible = !extras && i < o.areas().size();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        sync();
        if (nameWait > 0 && --nameWait == 0) sendName();
        int n = 0;
        Inventory inv = minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.PAPER)) n += s.getCount();
        }
        paper = n;
    }

    /** The name typed, to the server, when it changed since it was last sent (the server cleans it and keeps 24). */
    private void sendName() {
        nameWait = 0;
        if (name == null || name.getValue().equals(sent)) return;
        var connection = minecraft.getConnection();
        if (connection == null || !connection.hasChannel(WorkerNet.Text.TYPE)) return;
        PacketDistributor.sendToServer(new WorkerNet.Text(WorkerNet.ORDER, 0, name.getValue()));
        sent = name.getValue();
    }

    @Override
    public void onClose() {
        // before the menu closes: the server only takes a name while this screen's menu is open
        sendName();
        super.onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256) return super.keyPressed(key, scan, modifiers); // Escape closes, typing or not
        if (name != null && name.isFocused()) {
            if (key == 257 || key == 335) { // Enter files the name and leaves the field
                sendName();
                setFocused(null);
                return true;
            }
            // keys typed in the field stay there: the inventory key, the hotbar numbers and Q do not act
            if (name.keyPressed(key, scan, modifiers) || name.canConsumeInput()) return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (name != null) {
            double x = mouseX - leftPos, y = mouseY - topPos;
            boolean field = x >= LX && x < LX + LW - printWidth() - 4 && y >= NAME_Y && y < NAME_Y + NAME_H;
            if (field && !name.isMouseOver(mouseX, mouseY)) {
                // the field's paper edge, outside the text itself: into the field, at the end
                setFocused(name);
                name.moveCursorToEnd(false);
                return true;
            }
            if (!field && getFocused() == name) setFocused(null);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // --- drawing ------------------------------------------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        // the board, the sheet on it, the clip at the top
        g.fill(x, y, x + W, y + H, BOARD_EDGE);
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, BOARD);
        g.fill(x + 4, y + 4, x + W - 4, y + H - 4, PAPER);
        for (int i = 0; i < ClipboardMenu.GHOSTS; i++) {
            int ry = y + rowY(i) + 17;
            g.fill(x + RX, ry, x + CHECK_X - 2, ry + 1, RULE);
        }
        g.fill(x + RX - 5, y + 4, x + RX - 4, y + H - 4, MARGIN);
        int cx = x + W / 2;
        g.fill(cx - 28, y - 5, cx + 28, y + 5, STEEL_DARK);
        g.fill(cx - 27, y - 4, cx + 27, y + 4, STEEL);
        g.fill(cx - 27, y - 4, cx + 27, y - 3, 0xFFC4CBD1);
        g.fill(cx - 6, y - 2, cx + 6, y + 2, STEEL_DARK);
        // the name's field
        int fx1 = x + LX + LW - printWidth() - 4;
        g.fill(x + LX, y + NAME_Y, fx1, y + NAME_Y + NAME_H, name != null && name.isFocused() ? INK : EDGE);
        g.fill(x + LX + 1, y + NAME_Y + 1, fx1 - 1, y + NAME_Y + NAME_H - 1, FIELD);
        // slot frames: the inventory's, and each Drop-Off's ghost slot
        for (var slot : menu.slots) {
            if (slot.isActive()) frame(g, x + slot.x, y + slot.y);
        }
    }

    /** A slot's frame, pressed into the paper. */
    private static void frame(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFFA8977A);
        g.fill(x, y, x + 17, y + 17, 0xFFFFFCF4);
        g.fill(x, y, x + 16, y + 16, 0xFFDCD0B4);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        tips.clear();
        Order o = menu.order();
        // the title and the paper in the pockets; the stops' heading over the right column
        line(g, title, LX, 7, LW - 70, INK, null);
        Component paperLine = Component.translatable("gui.headinajar.clipboard.paper", paper);
        boolean none = paper == 0 && !minecraft.player.getAbilities().instabuild;
        g.drawString(font, paperLine, LX + LW - font.width(paperLine), 7, none ? RED : LABEL, false);
        Component stops = Component.translatable("gui.headinajar.clipboard.stops").append(" " + o.stops().size() + "/" + Order.MAX_STOPS);
        g.drawString(font, stops, RX, 7, LABEL, false);
        // the site
        int site = menu.site();
        Component siteLine;
        int siteColor;
        if (site == ClipboardMenu.BOUND && o.clock().isPresent()) {
            siteLine = Component.translatable("gui.headinajar.clipboard.site", pos(o.clock().get().pos()));
            siteColor = INK;
        } else if (site == ClipboardMenu.CLOSED) {
            siteLine = Component.translatable("gui.headinajar.clipboard.closed");
            siteColor = RED;
        } else if (site == ClipboardMenu.AWAY) {
            siteLine = Component.translatable("gui.headinajar.clipboard.away");
            siteColor = LABEL;
        } else {
            siteLine = Component.translatable("gui.headinajar.clipboard.unbound");
            siteColor = LABEL;
        }
        wrapped(g, siteLine, LX, SITE_Y, LW, 2, siteColor);
        // the job's line under the buttons: the one under the pointer, else the one chosen
        String job = o.job();
        for (int i = 0; i < jobs.length; i++) if (jobs[i].isHovered()) job = WorkTable.JOBS.get(i);
        if (WorkTable.JOBS.contains(job)) wrapped(g, Component.translatable("gui.headinajar.clipboard.job." + job), LX, DESC_Y, LW, 2, LABEL);
        // the areas, then a first corner waiting for its second; or a Sorting Drop-Off's extra filters, while open
        int picked = menu.picked();
        if (picked >= 0) {
            Component head = Component.translatable("gui.headinajar.clipboard.extras", pos(o.stops().get(picked).pos()));
            line(g, head, LX, AREAS_Y, LW, DROP_INK, font.split(Component.translatable("gui.headinajar.clipboard.more"), 220));
        } else {
            g.drawString(font, Component.translatable("gui.headinajar.clipboard.areas"), LX, AREAS_Y, LABEL, false);
        }
        int k = picked >= 0 ? Order.MAX_AREAS : 0;
        for (Order.Area a : picked >= 0 ? List.<Order.Area>of() : o.areas()) {
            if (k >= Order.MAX_AREAS) break;
            Component full = Component.translatable("gui.headinajar.clipboard.area", a.sizeX(), a.sizeZ(), pos(a.min()));
            Component shortLine = Component.literal(a.sizeX() + "×" + a.sizeZ() + "  " + pos(a.min()));
            fitted(g, full, shortLine, LX + 12, areaY(k), LW - 12, INK, null);
            k++;
        }
        if (o.corner().isPresent() && k < Order.MAX_AREAS) {
            Component full = Component.translatable("gui.headinajar.clipboard.corner", pos(o.corner().get()));
            fitted(g, full, Component.literal("+ " + pos(o.corner().get())), LX + 12, areaY(k), LW - 12, LABEL, null);
        }
        // the stops
        if (o.stops().isEmpty() && o.areas().isEmpty() && o.corner().isEmpty()) {
            wrapped(g, Component.translatable("gui.headinajar.clipboard.nothing"), RX, rowY(0) + 5, RIGHT_END - RX, 3, LABEL);
        }
        ClientLevel level = minecraft.level;
        for (int i = 0; i < o.stops().size() && i < ClipboardMenu.GHOSTS; i++) {
            Order.Stop s = o.stops().get(i);
            boolean gone = level != null && gone(level, o, s.pos());
            Component kind = Component.translatable(s.dropOff() ? "staff.headinajar.stop.drop_off" : "staff.headinajar.stop.pick_up");
            Component full = Component.translatable("gui.headinajar.clipboard.stop", kind, pos(s.pos()));
            Component shortLine = Component.literal(pos(s.pos()));
            if (gone) {
                full = full.copy().withStyle(ChatFormatting.STRIKETHROUGH);
                shortLine = shortLine.copy().withStyle(ChatFormatting.STRIKETHROUGH);
            }
            Component goneLine = gone ? Component.translatable("gui.headinajar.clipboard.stop_gone").withStyle(ChatFormatting.RED) : null;
            int end = ClipboardMenu.pickable(o, i) ? MORE_TEXT_END : TEXT_END;
            fitted(g, full, shortLine, TEXT_X, rowY(i) + 5, end - TEXT_X, gone ? RED : s.dropOff() ? DROP_INK : INK, goneLine);
        }
    }

    /** "x, y, z". */
    private static String pos(BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    /**
     * Whether a stop's block is gone, as this client can tell: its chunk is here and the block is air, or holds no
     * container any more (no block entity, and not a composter-like holder). Another dimension's or an unloaded
     * chunk's stop is not gone: nobody here can say.
     */
    static boolean gone(ClientLevel level, Order o, BlockPos pos) {
        if (o.clock().isEmpty() || !o.clock().get().dimension().equals(level.dimension()) || !level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        return level.getBlockEntity(pos) == null && !(state.getBlock() instanceof WorldlyContainerHolder);
    }

    /** One line at (x, y), the short form when the full one does not fit, cut if even that does not; the full one under the pointer then. */
    private void fitted(GuiGraphics g, Component full, Component shortLine, int x, int y, int room, int color, Component extra) {
        boolean fits = font.width(full) <= room;
        Component shown = fits ? full : shortLine;
        List<FormattedCharSequence> whole = new ArrayList<>();
        if (!fits) whole.addAll(font.split(full, 220));
        if (extra != null) whole.addAll(font.split(extra, 220));
        line(g, shown, x, y, room, color, whole.isEmpty() ? null : whole);
    }

    /** One line at (x, y), cut with an ellipsis past {@code room}; {@code tip} (or the whole line, when cut) under the pointer. */
    private void line(GuiGraphics g, Component text, int x, int y, int room, int color, List<FormattedCharSequence> tip) {
        FormattedCharSequence shown;
        List<FormattedCharSequence> lines = tip;
        if (font.width(text) <= room) {
            shown = text.getVisualOrderText();
        } else {
            FormattedText part = font.substrByWidth(text, Math.max(0, room - font.width("...")));
            shown = Language.getInstance().getVisualOrder(FormattedText.composite(part, FormattedText.of("...")));
            if (lines == null) lines = font.split(text, 220);
        }
        g.drawString(font, shown, x, y, color, false);
        if (lines != null) tips.add(new Tip(x, y - 1, x + font.width(shown), y + LINE, lines));
    }

    /** Text wrapped to {@code room}, at most {@code max} lines; the whole text under the pointer when it ran longer. */
    private void wrapped(GuiGraphics g, Component text, int x, int y, int room, int max, int color) {
        List<FormattedCharSequence> parts = font.split(text, room);
        for (int i = 0; i < parts.size() && i < max; i++) g.drawString(font, parts.get(i), x, y + i * LINE, color, false);
        if (parts.size() > max) tips.add(new Tip(x, y - 1, x + room, y + max * LINE, font.split(text, 220)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        sync();
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int x, int y) {
        if (hoveredSlot instanceof ClipboardMenu.FilterSlot slot) {
            // a ghost slot: its filter, if any, and what it is for
            List<FormattedCharSequence> lines = new ArrayList<>();
            if (slot.hasItem()) for (Component c : getTooltipFromContainerItem(slot.getItem())) lines.add(c.getVisualOrderText());
            lines.addAll(font.split(Component.translatable("gui.headinajar.clipboard.filter").withStyle(ChatFormatting.GRAY), 200));
            g.renderTooltip(font, lines, x, y);
            return;
        }
        if (hoveredSlot instanceof ClipboardMenu.ExtraSlot slot) {
            // an extra filter of the open Drop-Off: its item, if any, and who reads it
            List<FormattedCharSequence> lines = new ArrayList<>();
            if (slot.hasItem()) for (Component c : getTooltipFromContainerItem(slot.getItem())) lines.add(c.getVisualOrderText());
            lines.addAll(font.split(Component.translatable("gui.headinajar.clipboard.extra").withStyle(ChatFormatting.GRAY), 200));
            g.renderTooltip(font, lines, x, y);
            return;
        }
        if (hoveredSlot != null || !menu.getCarried().isEmpty()) {
            super.renderTooltip(g, x, y);
            return;
        }
        int sx = x - leftPos, sy = y - topPos;
        for (Tip t : tips) {
            if (sx >= t.x0() && sx < t.x1() && sy >= t.y0() && sy < t.y1()) {
                g.renderTooltip(font, t.lines(), x, y);
                return;
            }
        }
    }

    // --- the stationery -------------------------------------------------------------------------------------------------

    /** A press on the sheet: a job tab (marked when chosen), Print, an unmark cross, or an "Everything else" box (ticked when on). */
    private final class Tab extends AbstractButton {
        static final int TAB = 0, PRINT = 1, CROSS = 2, CHECK = 3, MORE = 4;
        private final int style;
        private final Runnable press;
        boolean on;

        Tab(int x, int y, int w, int h, Component label, int style, Runnable press) {
            super(x, y, w, h, label);
            this.style = style;
            this.press = press;
        }

        @Override
        public void onPress() {
            press.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = width, h = height;
            boolean hot = isHovered();
            switch (style) {
                case CROSS -> {
                    if (hot) g.fill(x, y, x + w, y + h, 0x40B0645A);
                    int c = hot ? 0xFFC02828 : 0xFFB0645A;
                    for (int i = 1; i < w - 1; i++) {
                        g.fill(x + i, y + i, x + i + 1, y + i + 1, c);
                        g.fill(x + w - 1 - i, y + i, x + w - i, y + i + 1, c);
                    }
                }
                case MORE -> {
                    // a plus in green ink; filled in while its extra filters are open
                    g.fill(x, y, x + w, y + h, hot || on ? DROP_INK : EDGE);
                    g.fill(x + 1, y + 1, x + w - 1, y + h - 1, on ? HIGHLIGHT : FIELD);
                    int c = DROP_INK, mx = x + w / 2, my = y + h / 2;
                    g.fill(x + 3, my - 1, x + w - 3, my + 1, c);
                    g.fill(mx - 1, y + 3, mx + 1, y + h - 3, c);
                }
                case CHECK -> {
                    g.fill(x, y, x + w, y + h, hot ? INK : EDGE);
                    g.fill(x + 1, y + 1, x + w - 1, y + h - 1, FIELD);
                    if (on) {
                        // a tick in green ink
                        int[][] px = {{2, 6}, {3, 7}, {4, 8}, {5, 7}, {6, 6}, {7, 5}, {8, 4}, {9, 3}};
                        for (int[] p : px) g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 2, DROP_INK);
                    }
                }
                default -> {
                    boolean print = style == PRINT;
                    int border = print ? RED : on ? INK : EDGE;
                    g.fill(x, y, x + w, y + h, border);
                    g.fill(x + 1, y + 1, x + w - 1, y + h - 1, on ? HIGHLIGHT : hot ? 0xFFE6D9BA : 0xFFEEE6D0);
                    if (on) g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, INK);
                    Component label = getMessage();
                    int room = w - 4;
                    FormattedCharSequence shown = font.width(label) <= room ? label.getVisualOrderText()
                            : Language.getInstance().getVisualOrder(FormattedText.composite(font.substrByWidth(label, Math.max(0, room - font.width("..."))), FormattedText.of("...")));
                    g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2, print ? RED : INK, false);
                }
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
