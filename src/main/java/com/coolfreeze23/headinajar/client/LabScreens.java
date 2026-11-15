package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.LabMachines;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneArchive;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.menu.LabMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Screens for the genetics lab. */
public final class LabScreens {
    private LabScreens() {
    }

    public static void register(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.LAB_MENUS.get(LabMenu.Kind.CENTRIFUGE).get(), (LabMenu menu, Inventory inv, Component title) ->
                new MachineScreen<>(menu, inv, title).energy(20_000).arrow(72, 35, 1, LabMachines.Centrifuge.TICKS));
        event.register(ModRegistry.LAB_MENUS.get(LabMenu.Kind.INJECTION_RIG).get(), (LabMenu menu, Inventory inv, Component title) ->
                new MachineScreen<>(menu, inv, title).energy(20_000)
                        .line(22, 56, "gui.headinajar.rig.how")
                        .button(104, 28, 56, 20, "gui.headinajar.rig.inject", LabMachines.InjectionRig.INJECT));
        // the Sequencer with its Staff Directory tab (DirectoryView)
        event.register(ModRegistry.LAB_MENUS.get(LabMenu.Kind.DNA_SEQUENCER).get(), DirectoryView.TabbedSequencer::new);
        event.register(ModRegistry.LAB_MENUS.get(LabMenu.Kind.CEREBRAL_ARRAY).get(), (LabMenu menu, Inventory inv, Component title) -> {
            var screen = new MachineScreen<>(menu, inv, title).energy(500_000)
                    .gauge(104, 17, 12, 52, 2, com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity.TANK, 0xFFC89A48, "gui.headinajar.broth")
                    .line(120, 18, "gui.headinajar.array.output", 1)
                    .line(120, 30, "gui.headinajar.array.heads", 3);
            screen.hideInventoryLabel();
            return screen;
        });
        event.register(ModRegistry.LAB_MENUS.get(LabMenu.Kind.GENE_SPLICER).get(), SplicerScreen::new);
    }

    static String geneName(Gene g) {
        return Component.translatable("gene.headinajar." + g.id()).getString();
    }

    // --- DNA Sequencer --------------------------------------------------------------------------------

    /**
     * Auto-read, or call the bases yourself: the strand scrolls past as twelve letters; press A, C, G or T for each in
     * turn. The calls go to the server in one go when the twelfth is in.
     */
    public static class SequencerScreen extends MachineScreen<LabMenu> {
        private static final String LETTERS = "ACGT";
        private static final int[] COLOURS = {0xFF55C060, 0xFF3F9BE4, 0xFFE0B030, 0xFFE05050};
        private final List<Integer> calls = new ArrayList<>();
        private int seenSeed = -1;

        public SequencerScreen(LabMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            energy(40_000);
            arrow(46, 35, 1, LabMachines.Sequencer.TICKS);
            inventoryLabelY = 10_000;
        }

        @Override
        protected void init() {
            super.init();
            addRenderableWidget(Button.builder(Component.translatable("gui.headinajar.sequencer.auto"),
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LabMachines.Sequencer.AUTO)).bounds(leftPos + 22, topPos + 56, 40, 18).build());
            for (int i = 0; i < 4; i++) {
                int base = i;
                addRenderableWidget(Button.builder(Component.literal(String.valueOf(LETTERS.charAt(i))), b -> call(base))
                        .bounds(leftPos + 80 + i * 22, topPos + 56, 20, 18).build());
            }
        }

        private void call(int base) {
            var stack = menu.slots.get(0).getItem();
            // decided here, so said here (the server never hears of a half-called strand)
            if (stack.isEmpty()) {
                ClientRefusals.local(com.coolfreeze23.headinajar.feedback.Refusals.line("lab.sequencer.empty", Component.translatable("item.headinajar.dna_fraction")));
                return;
            }
            if (GeneticsItems.species(stack) == null) {
                ClientRefusals.local(com.coolfreeze23.headinajar.feedback.Refusals.line("lab.sequencer.unreadable"));
                return;
            }
            calls.add(base);
            if (calls.size() < LabMachines.Sequencer.BASES) {
                // each key rings on the register for everyone watching (the twelfth is the read itself)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LabMachines.Sequencer.KEY + (calls.size() - 1) * 4 + base);
            }
            if (calls.size() == LabMachines.Sequencer.BASES) {
                int[] c = calls.stream().mapToInt(Integer::intValue).toArray();
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LabMachines.Sequencer.CALL + LabMachines.Sequencer.encode(c));
                calls.clear();
            }
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            int i = "ACGT".indexOf(Character.toUpperCase((char) key));
            if (i >= 0 && key < 128) {
                call(i);
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            int seed = menu.data(2);
            if (seed != seenSeed) {
                seenSeed = seed;
                calls.clear();
            }
            var stack = menu.slots.get(0).getItem();
            EntityType<?> species = GeneticsItems.species(stack);
            if (species == null) {
                g.drawString(font, Component.translatable("gui.headinajar.sequencer.insert"), 80, 20, 0x404040, false);
                return;
            }
            // the strand: called bases solid, the next one blinking, the rest dim
            int[] bases = LabMachines.Sequencer.bases(seed);
            for (int i = 0; i < bases.length; i++) {
                int x = 80 + i * 7, y = 20;
                boolean done = i < calls.size(), next = i == calls.size();
                int colour = done ? (calls.get(i) == bases[i] ? COLOURS[bases[i]] : 0xFF202020) : next && (System.currentTimeMillis() / 300) % 2 == 0 ? 0xFFFFFFFF : COLOURS[bases[i]] & 0x80FFFFFF;
                g.drawString(font, String.valueOf(LETTERS.charAt(bases[i])), x, y, colour, false);
            }
            List<Gene> genes = com.coolfreeze23.headinajar.genetics.Samples.genes(stack);
            Map<Gene, Float> lib = GeneArchive.clientLibrary;
            int y = 32;
            if (genes.isEmpty()) g.drawString(font, Component.translatable("gui.headinajar.sequencer.nothing"), 80, y, 0x804040, false);
            for (Gene gene : genes) {
                g.drawString(font, geneName(gene) + " " + Math.round(lib.getOrDefault(gene, 0f)) + "%", 80, y, 0x204060, false);
                y += 9;
                if (y > 48) break;
            }
            if (menu.data(3) == 1) g.drawString(font, Component.translatable("gui.headinajar.sequencer.reading"), 44, 48, 0x206020, false);
        }
    }

    // --- Gene Splicer ---------------------------------------------------------------------------------

    /**
     * Three strand positions. Each row has a department chip (pressed: the next department with a gene at Draft or
     * better; right-clicked: the one before), then "<" and ">" through that department's usable genes (and empty). Every
     * pick is today's button id ({@code PICK + slot * 100 + ordinal}), so the server is unchanged. A hovered chip names
     * its department in the title strip; a hovered gene name, and the stability, have tooltips.
     */
    public static class SplicerScreen extends MachineScreen<LabMenu> {
        /** The department chips' sheet: 15 chips of 12 x 12 at (16 * i, 0), the Portuguese marks a row lower. */
        private static final net.minecraft.resources.ResourceLocation CHIPS = com.coolfreeze23.headinajar.HeadInAJar.id("textures/gui/lab_chips.png");
        /** The chips' order: the fourteen species departments, then The Board, the Temp Agency and General Staff. */
        public static final List<String> DEPARTMENTS;

        static {
            List<String> d = new ArrayList<>(com.coolfreeze23.headinajar.staff.Staff.DEPARTMENTS);
            d.add(com.coolfreeze23.headinajar.staff.Staff.BOARD);
            d.add(com.coolfreeze23.headinajar.staff.Staff.TEMPS);
            d.add(com.coolfreeze23.headinajar.staff.Staff.GENERAL);
            DEPARTMENTS = List.copyOf(d);
        }

        private static final int ROW_Y = 16, ROW_H = 12, CHIP_X = 20, NAME_X = 47, NAME_W = 63;
        /** Each row's department (an index into {@link #DEPARTMENTS}), or -1 until the row has one. */
        private final int[] department = {-1, -1, -1};
        /** The department the title strip names, and until when (a press shows it for three seconds). */
        private int shown = -1;
        private long shownUntil;

        public SplicerScreen(LabMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            energy(40_000);
            arrow(116, 60, 1, LabMachines.Splicer.TICKS);
            inventoryLabelY = 10_000;
        }

        /** The genes at Draft (30%) or better in the team's library, in gene order. */
        public static List<Gene> usable() {
            List<Gene> out = new ArrayList<>();
            for (Gene g : Gene.values()) if (GeneArchive.potency(GeneArchive.clientLibrary.getOrDefault(g, 0f)) > 0) out.add(g);
            return out;
        }

        /** The usable genes of one department, in gene order. */
        public static List<Gene> usable(int department) {
            List<Gene> out = new ArrayList<>();
            String id = DEPARTMENTS.get(department);
            for (Gene g : usable()) if (g.department().equals(id)) out.add(g);
            return out;
        }

        public static int departmentOf(Gene gene) {
            return Math.max(0, DEPARTMENTS.indexOf(gene.department()));
        }

        /** The department {@code step} away from {@code from} (1 next, -1 back) that has a usable gene; -1 for none. */
        public static int stepDepartment(int from, int step) {
            int n = DEPARTMENTS.size();
            for (int i = 1; i <= n; i++) {
                int d = Math.floorMod((from < 0 ? (step > 0 ? -1 : 0) : from) + step * i, n);
                if (!usable(d).isEmpty()) return d;
            }
            return -1;
        }

        private Gene at(int slot) {
            int v = menu.data(2 + slot);
            return v <= 0 || v > Gene.values().length ? null : Gene.values()[v - 1];
        }

        /** The row's department: its gene's, else the one the chip last chose, else the first that has a usable gene. */
        private int departmentAt(int slot) {
            Gene gene = at(slot);
            if (gene != null) department[slot] = departmentOf(gene);
            if (department[slot] < 0) department[slot] = stepDepartment(-1, 1);
            return department[slot];
        }

        private void pick(int slot, Gene gene) {
            int ordinal = gene == null ? LabMachines.Splicer.CLEAR : gene.ordinal();
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LabMachines.Splicer.PICK + slot * 100 + ordinal);
        }

        private boolean emptyLibrary() {
            if (!usable().isEmpty()) return false;
            ClientRefusals.local(com.coolfreeze23.headinajar.feedback.Refusals.line("lab.splicer.empty_library",
                    Component.translatable("block.headinajar.dna_sequencer")));
            return true;
        }

        /** "<" and ">": the next usable gene of the row's department, then empty, round again. */
        private void cycle(int slot, int dir) {
            if (emptyLibrary()) return;
            int d = departmentAt(slot);
            List<Gene> genes = d < 0 ? List.of() : usable(d);
            if (genes.isEmpty()) {
                // the row's department has nothing usable now: the chip's next department, as a press would
                chip(slot, 1);
                return;
            }
            Gene now = at(slot);
            int index = now == null ? -1 : genes.indexOf(now);
            int next = Math.floorMod(index + 1 + dir, genes.size() + 1) - 1;
            pick(slot, next < 0 ? null : genes.get(next));
        }

        /** The chip: the next (or previous) department with a usable gene, and its first gene. */
        private void chip(int slot, int step) {
            if (emptyLibrary()) return;
            int d = stepDepartment(departmentAt(slot), step);
            if (d < 0) return;
            department[slot] = d;
            shown = d;
            shownUntil = System.currentTimeMillis() + 3000;
            pick(slot, usable(d).get(0));
        }

        @Override
        protected void init() {
            super.init();
            for (int i = 0; i < 3; i++) {
                int slot = i, y = topPos + ROW_Y + i * ROW_H;
                addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(slot, -1)).bounds(leftPos + 33, y, 12, 12).build());
                addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(slot, 1)).bounds(leftPos + 112, y, 12, 12).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("gui.headinajar.splicer.splice"),
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LabMachines.Splicer.SPLICE)).bounds(leftPos + 128, topPos + 16, 40, 18).build());
        }

        /** The row whose chip is under the mouse, or -1. */
        private int chipAt(double mouseX, double mouseY) {
            for (int i = 0; i < 3; i++) {
                int x = leftPos + CHIP_X, y = topPos + ROW_Y + i * ROW_H;
                if (mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12) return i;
            }
            return -1;
        }

        private int nameAt(double mouseX, double mouseY) {
            for (int i = 0; i < 3; i++) {
                int x = leftPos + NAME_X - 1, y = topPos + ROW_Y + i * ROW_H;
                if (mouseX >= x && mouseX < x + NAME_W + 2 && mouseY >= y && mouseY < y + 12) return i;
            }
            return -1;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int row = chipAt(mouseX, mouseY);
            if (row >= 0 && (button == 0 || button == 1)) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1));
                chip(row, button == 0 ? 1 : -1);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
            super.renderBg(g, partialTick, mouseX, mouseY);
            boolean pt = minecraft.getLanguageManager().getSelected().startsWith("pt");
            int hovered = chipAt(mouseX, mouseY);
            for (int i = 0; i < 3; i++) {
                int d = departmentAt(i);
                int x = leftPos + CHIP_X, y = topPos + ROW_Y + i * ROW_H;
                if (d < 0) {
                    // no department yet (nothing read to a Draft): an empty chip, framed like a slot
                    g.fill(x, y, x + 12, y + 12, 0xFF373737);
                    g.fill(x + 1, y + 1, x + 12, y + 12, 0xFFFFFFFF);
                    g.fill(x + 1, y + 1, x + 11, y + 11, 0xFF8B8B8B);
                } else {
                    g.blit(CHIPS, x, y, 16 * d, pt ? 16 : 0, 12, 12, 256, 32);
                }
                if (hovered == i) {
                    g.fill(x - 1, y - 1, x + 13, y, 0xFFFFFFFF);
                    g.fill(x - 1, y + 12, x + 13, y + 13, 0xFFFFFFFF);
                    g.fill(x - 1, y, x, y + 12, 0xFFFFFFFF);
                    g.fill(x + 12, y, x + 13, y + 12, 0xFFFFFFFF);
                }
            }
        }

        /** The department the title strip should name now (a hovered chip's, or the last one pressed), or -1. */
        private int stripDepartment(int mouseX, int mouseY) {
            int row = chipAt(mouseX, mouseY);
            if (row >= 0) return departmentAt(row);
            return System.currentTimeMillis() < shownUntil ? shown : -1;
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            int strip = stripDepartment(mouseX, mouseY);
            if (strip >= 0) {
                String id = DEPARTMENTS.get(strip);
                Component line = Component.translatable("gui.headinajar.splicer.chip", com.coolfreeze23.headinajar.staff.Staff.departmentName(id),
                        Component.translatable("department.headinajar." + id + ".line"));
                g.drawString(font, clipAtWord(font, line.getString(), imageWidth - 2 * titleLabelX), titleLabelX, titleLabelY, 0x404040, false);
            } else {
                super.renderLabels(g, mouseX, mouseY);
            }
            // gene names, coloured by potency band: Complete dark blue, Working blue, Draft amber; clipped to the row
            for (int i = 0; i < 3; i++) {
                Gene gene = at(i);
                float potency = gene == null ? 0 : GeneArchive.potency(GeneArchive.clientLibrary.getOrDefault(gene, 0f));
                int colour = gene == null ? 0x808080 : potency >= 1 ? 0x203878 : potency >= 0.75f ? 0x3870B0 : 0xA06010;
                String name = gene == null ? "-" : geneName(gene);
                String clipped = font.plainSubstrByWidth(name, NAME_W);
                if (!clipped.equals(name)) clipped = font.plainSubstrByWidth(name, NAME_W - font.width(".")) + ".";
                g.drawString(font, clipped, NAME_X, ROW_Y + 2 + i * ROW_H, colour, false);
            }
            int stability = menu.data(5), mutation = menu.data(6), load = menu.data(7);
            int colour = stability >= 60 ? 0x207020 : stability >= 30 ? 0x806010 : 0x902020;
            g.drawString(font, Component.translatable("gui.headinajar.splicer.stability", stability), 127, 36, colour, false);
            g.drawString(font, Component.translatable("gui.headinajar.splicer.mutation", mutation), 127, 44, colour, false);
            // the load against the lightest thing that can take it: a body (3), a host (6) or a Chimera's ports (8)
            var fits = com.coolfreeze23.headinajar.genetics.Genetics.Target.lightestFor(load);
            Component line = fits == null
                    ? Component.translatable("gui.headinajar.splicer.load_heavy", load, LabMachines.Splicer.MAX_LOAD)
                    : Component.translatable("gui.headinajar.splicer.load_fits", load, fits.loadCap,
                            Component.translatable("gui.headinajar.splicer.target." + fits.name().toLowerCase(java.util.Locale.ROOT)));
            g.drawString(font, line, 34, 51, fits == null ? 0x902020 : 0x404040, false);
        }

        @Override
        protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
            super.renderTooltip(g, mouseX, mouseY);
            if (!menu.getCarried().isEmpty()) return;
            int row = chipAt(mouseX, mouseY);
            if (row >= 0) {
                int d = departmentAt(row);
                // under the chip's row, wrapped: the title strip above names the department while the chip is pointed at
                if (d >= 0) g.renderTooltip(font, font.split(Component.translatable("gui.headinajar.splicer.chip_hint",
                        com.coolfreeze23.headinajar.staff.Staff.departmentName(DEPARTMENTS.get(d))), 170),
                        mouseX, topPos + ROW_Y + (row + 1) * ROW_H + 14);
                return;
            }
            int name = nameAt(mouseX, mouseY);
            if (name >= 0 && at(name) != null) {
                g.renderComponentTooltip(font, geneTooltip(font, at(name)), mouseX, mouseY);
                return;
            }
            int sx = leftPos + 127, sy = topPos + 35;
            if (mouseX >= sx && mouseX < sx + 45 && mouseY >= sy && mouseY < sy + 9) {
                g.renderComponentTooltip(font, pairsTooltip(font, strand()), mouseX, mouseY);
            }
        }

        private List<Gene> strand() {
            List<Gene> out = new ArrayList<>();
            for (int i = 0; i < 3; i++) if (at(i) != null) out.add(at(i));
            return out;
        }

        /** A line cut to {@code width} at a word, with "..." when anything was left out (never half a word). */
        static String clipAtWord(net.minecraft.client.gui.Font font, String text, int width) {
            if (font.width(text) <= width) return text;
            String room = font.plainSubstrByWidth(text, width - font.width("..."));
            int space = room.lastIndexOf(' ');
            if (space > 0) room = room.substring(0, space);
            return room.replaceAll("[ ,.:;]+$", "") + "...";
        }

        /** The gene's card: full name, department, load and stability, where it fits, active or not, what it does, its cost. */
        public static List<Component> geneTooltip(net.minecraft.client.gui.Font font, Gene gene) {
            List<Component> out = new ArrayList<>();
            out.add(Component.translatable("gene.headinajar." + gene.id()).withStyle(net.minecraft.ChatFormatting.WHITE));
            out.add(Component.translatable("gui.headinajar.splicer.tip.department",
                    com.coolfreeze23.headinajar.staff.Staff.departmentName(gene.department())).withStyle(net.minecraft.ChatFormatting.GRAY));
            out.add(Component.translatable("gui.headinajar.splicer.tip.numbers", gene.load, gene.stability).withStyle(net.minecraft.ChatFormatting.GRAY));
            List<Component> where = new ArrayList<>();
            for (var t : new com.coolfreeze23.headinajar.genetics.Genetics.Target[]{com.coolfreeze23.headinajar.genetics.Genetics.Target.BODY,
                    com.coolfreeze23.headinajar.genetics.Genetics.Target.HOST, com.coolfreeze23.headinajar.genetics.Genetics.Target.MECHA}) {
                if (gene.load <= t.loadCap) where.add(Component.translatable("gui.headinajar.splicer.target." + t.name().toLowerCase(java.util.Locale.ROOT)));
            }
            net.minecraft.network.chat.MutableComponent list = Component.empty();
            for (int i = 0; i < where.size(); i++) {
                if (i > 0) list.append(", ");
                list.append(where.get(i));
            }
            out.add(Component.translatable("gui.headinajar.splicer.tip.fits", list).withStyle(net.minecraft.ChatFormatting.GRAY));
            if (gene.active) out.add(Component.translatable("gui.headinajar.splicer.tip.active").withStyle(net.minecraft.ChatFormatting.AQUA));
            for (String part : new String[]{".desc", ".cost"}) {
                String key = "gene.headinajar." + gene.id() + part;
                if (!net.minecraft.locale.Language.getInstance().has(key)) continue;
                for (var line : font.getSplitter().splitLines(Component.translatable(key), 200, net.minecraft.network.chat.Style.EMPTY)) {
                    out.add(Component.literal(line.getString()).withStyle(part.equals(".cost") ? net.minecraft.ChatFormatting.DARK_GRAY : net.minecraft.ChatFormatting.GRAY));
                }
            }
            return out;
        }

        /** The Stab tooltip: the strand's named pairs, each with what the Splicer says about it. */
        public static List<Component> pairsTooltip(net.minecraft.client.gui.Font font, List<Gene> strand) {
            List<Component> out = new ArrayList<>();
            List<Gene[]> good = Gene.pairsIn(strand, true), bad = Gene.pairsIn(strand, false);
            if (good.isEmpty() && bad.isEmpty()) {
                out.add(Component.translatable("gui.headinajar.splicer.pairs.none").withStyle(net.minecraft.ChatFormatting.GRAY));
                return out;
            }
            out.add(Component.translatable("gui.headinajar.splicer.pairs").withStyle(net.minecraft.ChatFormatting.WHITE));
            for (boolean synergy : new boolean[]{true, false}) {
                for (Gene[] pair : synergy ? good : bad) {
                    String key = Gene.pairKey(pair, synergy);
                    boolean said = net.minecraft.locale.Language.getInstance().has(key + ".comment");
                    Component row = said
                            ? Component.translatable("gui.headinajar.splicer.pair." + (synergy ? "synergy" : "conflict"), Component.translatable(key),
                            Component.translatable(key + ".comment"))
                            : Component.translatable("gui.headinajar.splicer.pair." + (synergy ? "synergy" : "conflict") + "_plain", Component.translatable(key));
                    for (var line : font.getSplitter().splitLines(row, 220, net.minecraft.network.chat.Style.EMPTY)) {
                        out.add(Component.literal(line.getString()).withStyle(synergy ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
                    }
                }
            }
            return out;
        }
    }
}
