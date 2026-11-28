package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.genetics.BloodType;
import com.coolfreeze23.headinajar.genetics.Directory;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneArchive;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.menu.LabMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.SawTable;
import com.coolfreeze23.headinajar.staff.Staff;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The Staff Directory, the DNA Sequencer's second tab: every species the company keeps a cell for, by department (the
 * department tags), each cell its spawn egg (a personnel folder for a creature with none) with the team's stamps at its
 * corner; [REDACTED] for the three Head Office has no record of until one is filed; other mods' creatures under
 * "Other firms: <mod>", greyed when the mod is gone. It covers the whole panel (the player's inventory is hidden in
 * this mode), scrolls by the wheel and a 6 px bar, says a cell's details on hover, and shows a teammate's filing as a
 * one-line toast at its top for 60 ticks.
 */
public final class DirectoryView {
    public static final ResourceLocation SHEET = HeadInAJar.id("textures/gui/directory.png");
    public static final ResourceLocation CHIPS = HeadInAJar.id("textures/gui/lab_chips.png");
    static final int CELL = 18, COLUMNS = 9, HEADER = 13;
    /** The grid's place on the 176x166 panel: x, top and bottom (eight rows of cells), and the scroll bar's x. */
    static final int GRID_X = 3, GRID_TOP = 17, GRID_BOTTOM = 161, BAR_X = 166, BAR_W = 6, THUMB_H = 15;
    /** How long a filing's toast stays (60 ticks). */
    static final long TOAST_MS = 3000;
    private static final String[] CHIP_ORDER;
    /** Where each stamp's pictogram sits on the sheet (the corner of its 16x16 overlay at y 96): sampled, jarred, ridden, hired. */
    private static final int[][] STAMP_CORNERS = {{0, 106}, {26, 106}, {42, 96}, {48, 96}};

    static {
        List<String> order = new ArrayList<>(Staff.DEPARTMENTS);
        order.add(Staff.BOARD);
        order.add(Staff.TEMPS);
        order.add(Staff.GENERAL);
        CHIP_ORDER = order.toArray(String[]::new);
    }

    /** One cell: the species' id, its type (null: the firm is no longer trading), its egg (empty: a folder). */
    record Cell(String id, EntityType<?> type, ItemStack egg) {
        boolean redacted() {
            return Directory.REDACTED.contains(id) && !Directory.Client.any(id);
        }
    }

    /** A headed row of cells: a department (its chip), or another firm (no chip). */
    record Section(Component title, int chip, List<Cell> cells) {
        int height() {
            return HEADER + Mth.positiveCeilDiv(cells.size(), COLUMNS) * CELL;
        }
    }

    private final Font font;
    private List<Section> sections = List.of();
    private int builtFor = -1, contentHeight, scroll;
    private boolean dragging;

    public DirectoryView(Font font) {
        this.font = font;
    }

    // --- the cells -----------------------------------------------------------------------------------------------

    /** The sections, built again whenever a new copy of the Directory arrives. */
    List<Section> sections() {
        if (builtFor != Directory.Client.version) {
            builtFor = Directory.Client.version;
            sections = build();
            contentHeight = 0;
            for (Section s : sections) contentHeight += s.height();
            scroll = Mth.clamp(scroll, 0, maxScroll());
        }
        return sections;
    }

    static List<Section> build() {
        Map<String, List<Cell>> byDepartment = new LinkedHashMap<>();
        for (String d : CHIP_ORDER) byDepartment.put(d, new ArrayList<>());
        for (String id : Directory.VANILLA) {
            EntityType<?> type = type(id);
            // a department with no column (one outside the company's) files the creature under General Staff
            if (type != null) byDepartment.getOrDefault(Staff.department(type), byDepartment.get(Staff.GENERAL)).add(cell(id, type));
        }
        // other firms: every mod creature a department tag or a gene names, and any id on file (a mod since removed)
        Set<String> firms = new LinkedHashSet<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String id = Directory.id(type);
            if (!id.startsWith("minecraft:") && !id.startsWith(HeadInAJar.MOD_ID + ":") && !Staff.department(type).equals(Staff.GENERAL)) firms.add(id);
        }
        for (Gene g : Gene.values()) for (String id : g.sources) if (!id.startsWith("minecraft:") && type(id) != null) firms.add(id);
        for (String id : Directory.Client.all()) if (!id.startsWith("minecraft:")) firms.add(id);
        Map<String, List<Cell>> byFirm = new TreeMap<>();
        for (String id : firms.stream().sorted().toList()) {
            String ns = id.substring(0, Math.max(0, id.indexOf(':')));
            byFirm.computeIfAbsent(ns, k -> new ArrayList<>()).add(cell(id, type(id)));
        }
        List<Section> out = new ArrayList<>();
        for (int i = 0; i < CHIP_ORDER.length; i++) {
            List<Cell> cells = byDepartment.get(CHIP_ORDER[i]);
            if (!cells.isEmpty()) out.add(new Section(Staff.departmentName(CHIP_ORDER[i]), i, cells));
        }
        byFirm.forEach((ns, cells) -> out.add(new Section(Component.translatable("gui.headinajar.directory.other_firms", firmName(ns)), -1, cells)));
        // 0.8.0: the last row, New Arrivals: one cell for each newer creature this game has (a ghastling is its happy
        // ghast's cell), drawn only when there is at least one; none of them counts towards the census
        List<Cell> arrivals = new ArrayList<>();
        for (String id : com.coolfreeze23.headinajar.supplier.Supplier.delivered()) arrivals.add(cell(id, type(id)));
        if (!arrivals.isEmpty()) out.add(new Section(Component.translatable("gui.headinajar.directory.new_arrivals"), -1, arrivals));
        return out;
    }

    private static Cell cell(String id, EntityType<?> type) {
        SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
        return new Cell(id, type, egg == null ? ItemStack.EMPTY : new ItemStack(egg));
    }

    static EntityType<?> type(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
    }

    private static String firmName(String ns) {
        return net.neoforged.fml.ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(ns);
    }

    // --- drawing -------------------------------------------------------------------------------------------------

    int maxScroll() {
        return Math.max(0, contentHeight - (GRID_BOTTOM - GRID_TOP));
    }

    /** The title row (or the toast), the grid and the bar; {@code x, y} are the panel's corner. */
    void render(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<Section> all = sections();
        title(g, x, y);
        g.enableScissor(x + GRID_X, y + GRID_TOP, x + GRID_X + COLUMNS * CELL, y + GRID_BOTTOM);
        int top = y + GRID_TOP - scroll;
        for (Section s : all) {
            int h = s.height();
            if (top + h >= y + GRID_TOP && top < y + GRID_BOTTOM) section(g, s, x + GRID_X, top);
            top += h;
        }
        g.disableScissor();
        bar(g, x, y);
    }

    private void title(GuiGraphics g, int x, int y) {
        if (!Directory.Client.toastSpecies.isEmpty() && System.currentTimeMillis() - Directory.Client.toastAt < TOAST_MS) {
            // a filing: the stamp, then "Pufferfish: filed by Alex" (a machine's filing names the machine)
            int stamp = Directory.Client.toastStamp;
            int tx = x + 7;
            if (stamp >= 0 && stamp < STAMP_CORNERS.length) {
                // the stamp's 6x6 pictogram, cut from the corner of its overlay
                g.blit(SHEET, tx, y + 6, STAMP_CORNERS[stamp][0], STAMP_CORNERS[stamp][1], 6, 6);
                tx += 8;
            }
            Component line = Component.translatable("gui.headinajar.directory.filed", speciesName(Directory.Client.toastSpecies), filer(Directory.Client.toastBy));
            g.drawString(font, font.plainSubstrByWidth(line.getString(), x + 169 - tx), tx, y + 6, 0x206020, false);
            return;
        }
        int on = 0;
        for (String id : Directory.ON_THE_BOOKS) if (Directory.Client.any(id)) on++;
        Component count = Component.translatable("gui.headinajar.directory.count", on, Directory.ON_THE_BOOKS.size());
        Component name = Component.translatable("gui.headinajar.sequencer.tab.directory");
        int countX = x + 169 - font.width(count);
        // when the two do not fit side by side (Portuguese), the count is what matters: the tab already says where you are
        if (x + 7 + font.width(name) + 6 <= countX) g.drawString(font, name, x + 7, y + 6, 0x404040, false);
        g.drawString(font, count, countX, y + 6, 0x404040, false);
    }

    private void section(GuiGraphics g, Section s, int x, int top) {
        if (s.chip >= 0) {
            boolean pt = Minecraft.getInstance().getLanguageManager().getSelected().startsWith("pt");
            g.blit(CHIPS, x + 1, top, 16 * s.chip, pt ? 16 : 0, 12, 12, 256, 32);
        }
        int tx = s.chip >= 0 ? x + 15 : x + 1;
        g.drawString(font, font.plainSubstrByWidth(s.title.getString(), x + COLUMNS * CELL - tx), tx, top + 2, 0x404040, false);
        for (int i = 0; i < s.cells.size(); i++) {
            int cx = x + (i % COLUMNS) * CELL, cy = top + HEADER + (i / COLUMNS) * CELL;
            cell(g, s.cells.get(i), cx, cy);
        }
    }

    private void cell(GuiGraphics g, Cell c, int cx, int cy) {
        // a slot frame, as the game draws one
        g.fill(cx, cy, cx + 18, cy + 18, 0xFF373737);
        g.fill(cx + 1, cy + 1, cx + 18, cy + 18, 0xFFFFFFFF);
        g.fill(cx + 1, cy + 1, cx + 17, cy + 17, 0xFF8B8B8B);
        if (c.egg.isEmpty()) g.blit(SHEET, cx + 1, cy + 1, 0, 64, 16, 16);
        else g.renderItem(c.egg, cx + 1, cy + 1);
        boolean filed = Directory.Client.any(c.id);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        // a blank cell (or a firm that closed) is dimmed: the egg shows what to look for, not that it is on file
        if (!filed || c.type == null) g.fill(cx + 1, cy + 1, cx + 17, cy + 17, 0x998B8B8B);
        if (c.redacted()) {
            // Head Office has no record: the bar across the egg (or the folder)
            g.blit(SHEET, cx + 1, cy + 6, 0, 48, 16, 6);
        } else {
            // each stamp is a 16x16 overlay with its pictogram in its own corner, so all of them fit at once
            for (Directory.Stamp s : Directory.Stamp.values()) {
                if (Directory.Client.has(s, c.id)) g.blit(SHEET, cx + 1, cy + 1, 16 * s.ordinal(), 96, 16, 16);
            }
        }
        g.pose().popPose();
    }

    private void bar(GuiGraphics g, int x, int y) {
        int bx = x + BAR_X, top = y + GRID_TOP, bottom = y + GRID_BOTTOM;
        g.fill(bx, top, bx + BAR_W, bottom, 0xFF373737);
        int max = maxScroll();
        if (max <= 0) {
            g.blit(SHEET, bx, top, 38, 32, BAR_W, THUMB_H);
            return;
        }
        g.blit(SHEET, bx, top + (bottom - top - THUMB_H) * scroll / max, 32, 32, BAR_W, THUMB_H);
    }

    // --- hover ---------------------------------------------------------------------------------------------------

    /** The cell under the mouse, or null. */
    Cell at(int x, int y, double mouseX, double mouseY) {
        if (mouseX < x + GRID_X || mouseX >= x + GRID_X + COLUMNS * CELL || mouseY < y + GRID_TOP || mouseY >= y + GRID_BOTTOM) return null;
        int top = y + GRID_TOP - scroll;
        for (Section s : sections()) {
            int cellsTop = top + HEADER;
            if (mouseY >= cellsTop && mouseY < top + s.height()) {
                int i = (int) ((mouseY - cellsTop) / CELL) * COLUMNS + (int) ((mouseX - x - GRID_X) / CELL);
                return i < s.cells.size() ? s.cells.get(i) : null;
            }
            top += s.height();
        }
        return null;
    }

    /** What a cell says when pointed at (H12): who filed it and its stamps, blood, the tool, genes and reads, title, jar; a blank cell gives its hint. */
    List<FormattedCharSequence> tooltip(Cell c) {
        List<Component> rows = new ArrayList<>();
        List<FormattedCharSequence> out = new ArrayList<>();
        if (c.redacted()) {
            out.add(Component.translatable("gui.headinajar.directory.redacted").getVisualOrderText());
            out.addAll(font.split(Component.translatable("gui.headinajar.directory.no_record").withStyle(ChatFormatting.GRAY), 200));
            return out;
        }
        Component name = speciesName(c.id);
        String by = Directory.Client.filedBy(c.id);
        boolean filed = Directory.Client.any(c.id);
        out.add((filed && by != null ? Component.translatable("gui.headinajar.directory.filed", name, filer(by)) : name).getVisualOrderText());
        if (c.type == null) {
            out.add(Component.translatable("gui.headinajar.directory.closed").withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }
        for (Directory.Stamp s : Directory.Stamp.values()) {
            if (Directory.Client.has(s, c.id)) rows.add(Component.translatable("gui.headinajar.directory.stamp." + s.id()).withStyle(ChatFormatting.GREEN));
        }
        if (c.type != null) {
            if (!filed) hint(out, c);
            BloodType blood = BloodType.of(c.type);
            rows.add(Component.translatable("gui.headinajar.directory.blood",
                    Component.translatable("blood.headinajar." + blood.name().toLowerCase(java.util.Locale.ROOT))).withStyle(ChatFormatting.GRAY));
            rows.add(Component.translatable("gui.headinajar.directory.tool", tool(c.type, blood).getDescription()).withStyle(ChatFormatting.GRAY));
            List<Gene> genes = Gene.of(c.id);
            if (genes.isEmpty()) rows.add(Component.translatable("gui.headinajar.directory.no_genes").withStyle(ChatFormatting.DARK_GRAY));
            for (Gene gene : genes) {
                rows.add(Component.translatable("gui.headinajar.directory.gene", Component.translatable("gene.headinajar." + gene.id()),
                        Math.round(GeneArchive.clientLibrary.getOrDefault(gene, 0f))).withStyle(ChatFormatting.AQUA));
            }
            if (filed) {
                String title = com.coolfreeze23.headinajar.block.HeadSpeak.titleKey(c.type, new CompoundTag());
                if (I18n.exists(title)) rows.add(Component.translatable("gui.headinajar.directory.title", Component.translatable(title)).withStyle(ChatFormatting.GOLD));
                rows.add(jar(c.type).copy().withStyle(ChatFormatting.GRAY));
            }
        }
        for (Component row : rows) out.addAll(font.split(row, 200));
        return out;
    }

    /** A blank cell's two hint rows (where it lives, the jar its head wants), when this language has them. */
    private void hint(List<FormattedCharSequence> out, Cell c) {
        String key = "hint.headinajar." + c.id.replace(':', '.');
        for (String part : new String[]{".where", ".jar"}) {
            if (I18n.exists(key + part)) out.addAll(font.split(Component.translatable(key + part).withStyle(ChatFormatting.YELLOW), 200));
        }
    }

    /** The tool a species' sample is drawn with: its bloodless tool, the Leviathan Needle through a thick hide, or a Syringe. */
    static Item tool(EntityType<?> type, BloodType blood) {
        if (blood.bloodless) return GeneticsItems.SyringeItem.toolFor(blood);
        if (type.is(BloodType.THICK_HIDE)) return ModRegistry.LEVIATHAN_NEEDLE.get();
        return ModRegistry.SYRINGE.get();
    }

    /** The jar its head needs, from the saw's table (the tags). */
    static Component jar(EntityType<?> type) {
        if (type.is(SawTable.NEVER)) return Component.translatable("gui.headinajar.directory.jar.none");
        Item jar = type.is(SawTable.EXECUTIVE) ? ModRegistry.EXECUTIVE_JAR.get() : type.is(SawTable.REINFORCED) ? ModRegistry.REINFORCED_JAR.get()
                : ModRegistry.LIFE_SUPPORT_JAR.get();
        return Component.translatable("gui.headinajar.directory.jar", jar.getDescription());
    }

    static Component speciesName(String id) {
        EntityType<?> type = type(id);
        return type == null ? Component.literal(id) : type.getDescription();
    }

    /** Who filed it: a name, or the DNA Sequencer for a machine's read. */
    static Component filer(String by) {
        return by == null || by.isEmpty() ? Component.translatable("block.headinajar.dna_sequencer") : Component.literal(by);
    }

    // --- input ---------------------------------------------------------------------------------------------------

    boolean scrolled(double amount) {
        int before = scroll;
        scroll = Mth.clamp(scroll - (int) Math.signum(amount) * CELL, 0, maxScroll());
        return scroll != before || maxScroll() > 0;
    }

    boolean clicked(int x, int y, double mouseX, double mouseY) {
        if (mouseX >= x + BAR_X && mouseX < x + BAR_X + BAR_W && mouseY >= y + GRID_TOP && mouseY < y + GRID_BOTTOM && maxScroll() > 0) {
            dragging = true;
            drag(y, mouseY);
            return true;
        }
        return false;
    }

    void drag(int y, double mouseY) {
        if (!dragging) return;
        double f = (mouseY - (y + GRID_TOP) - THUMB_H / 2.0) / (GRID_BOTTOM - GRID_TOP - THUMB_H);
        scroll = Mth.clamp((int) Math.round(f * maxScroll()), 0, maxScroll());
    }

    void released() {
        dragging = false;
    }

    // --- the Sequencer with its two tabs -------------------------------------------------------------------------

    /**
     * The DNA Sequencer's screen with two tabs above the panel, "Sequencer" and "Staff Directory". In Directory mode the
     * grid takes the whole panel: no slots are drawn or hovered (the menu, its slots and indices are unchanged), the Auto
     * and A/C/G/T buttons are hidden and the letter keys pass. The tab cannot switch while the cursor carries an item.
     */
    public static class TabbedSequencer extends LabScreens.SequencerScreen {
        static final int TAB_W = 26, TAB_H = 24, TAB_GAP = 1;
        /** The tab last chosen, kept while the game runs. */
        private static boolean lastDirectory;
        private final DirectoryView view;
        private final List<AbstractWidget> sequencerWidgets = new ArrayList<>();
        private boolean directory;

        public TabbedSequencer(LabMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            view = new DirectoryView(Minecraft.getInstance().font);
            directory = lastDirectory;
        }

        @Override
        protected void init() {
            super.init();
            sequencerWidgets.clear();
            for (var child : children()) if (child instanceof AbstractWidget w) sequencerWidgets.add(w);
            show();
        }

        private void show() {
            for (AbstractWidget w : sequencerWidgets) w.visible = !directory;
        }

        public boolean directory() {
            return directory;
        }

        /** Which tab is under the mouse: 0 Sequencer, 1 Directory, -1 none. */
        private int tabAt(double mouseX, double mouseY) {
            if (mouseY < topPos - 22 || mouseY >= topPos) return -1;
            for (int i = 0; i < 2; i++) {
                int tx = leftPos + i * (TAB_W + TAB_GAP);
                if (mouseX >= tx && mouseX < tx + TAB_W) return i;
            }
            return -1;
        }

        private void tab(int i) {
            if ((i == 1) == directory) {
                // the tab already open: the click is heard, as a press of any button is
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1));
                return;
            }
            if (!menu.getCarried().isEmpty()) {
                ClientRefusals.local(com.coolfreeze23.headinajar.feedback.Refusals.line("lab.directory_carrying"));
                return;
            }
            directory = lastDirectory = i == 1;
            show();
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1));
        }

        private void tabs(GuiGraphics g) {
            for (int i = 0; i < 2; i++) {
                boolean selected = (i == 1) == directory;
                int tx = leftPos + i * (TAB_W + TAB_GAP), ty = topPos - 22;
                if (selected) g.blit(SHEET, tx, ty, tx == leftPos ? 52 : 26, 0, TAB_W, TAB_H + 1);
                else g.blit(SHEET, tx, ty, 0, 0, TAB_W, TAB_H);
                ItemStack icon = i == 0 ? new ItemStack(ModRegistry.DNA_SEQUENCER_ITEM.get()) : new ItemStack(Items.WRITABLE_BOOK);
                g.renderItem(icon, tx + 5, ty + (selected ? 5 : 6));
            }
        }

        @Override
        protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
            if (directory) {
                // the panel alone: no slot frames, no gauges, no arrow
                int x = leftPos, y = topPos;
                g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF000000);
                g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFFFFFFFF);
                g.fill(x + 3, y + 3, x + imageWidth - 1, y + imageHeight - 1, 0xFF555555);
                g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFFC6C6C6);
            } else {
                super.renderBg(g, partialTick, mouseX, mouseY);
            }
            tabs(g);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (!directory) {
                super.render(g, mouseX, mouseY, partialTick);
            } else {
                renderBackground(g, mouseX, mouseY, partialTick);
                hoveredSlot = null;
                view.render(g, leftPos, topPos, mouseX, mouseY);
                Cell c = view.at(leftPos, topPos, mouseX, mouseY);
                if (c != null) g.renderTooltip(font, view.tooltip(c), mouseX, mouseY);
            }
            int t = tabAt(mouseX, mouseY);
            if (t >= 0) g.renderTooltip(font, Component.translatable(t == 0 ? "gui.headinajar.sequencer.tab.sequencer" : "gui.headinajar.sequencer.tab.directory"), mouseX, mouseY);
        }

        @Override
        protected boolean isHovering(int x, int y, int w, int h, double mouseX, double mouseY) {
            return !directory && super.isHovering(x, y, w, h, mouseX, mouseY);
        }

        @Override
        protected void renderSlot(GuiGraphics g, net.minecraft.world.inventory.Slot slot) {
            if (!directory) super.renderSlot(g, slot);
        }

        /** A tab was pressed: its release is the tab's too (outside the panel, the game would throw a carried item). */
        private boolean tabPressed;

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int t = tabAt(mouseX, mouseY);
            if (t >= 0) {
                tab(t);
                tabPressed = true;
                return true;
            }
            if (!directory) return super.mouseClicked(mouseX, mouseY, button);
            view.clicked(leftPos, topPos, mouseX, mouseY);
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
            if (!directory) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
            view.drag(topPos, mouseY);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (tabPressed) {
                tabPressed = false;
                return true;
            }
            if (!directory) return super.mouseReleased(mouseX, mouseY, button);
            view.released();
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!directory) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            view.scrolled(scrollY);
            return true;
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            if (!directory) return super.keyPressed(key, scan, mods);
            // the letter keys are not calls here: only closing the screen is
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && shouldCloseOnEsc()
                    || minecraft.options.keyInventory.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(key, scan))) {
                onClose();
                return true;
            }
            return false;
        }
    }
}
