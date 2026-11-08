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
        event.register(ModRegistry.LAB_MENUS.get(LabMenu.Kind.DNA_SEQUENCER).get(), SequencerScreen::new);
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
            if (menu.slots.get(0).getItem().isEmpty()) return;
            calls.add(base);
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
            List<Gene> genes = Gene.of(BuiltInRegistries.ENTITY_TYPE.getKey(species).toString());
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

    /** Three strand positions with arrows to cycle through every gene the library holds at Draft or better. */
    public static class SplicerScreen extends MachineScreen<LabMenu> {
        public SplicerScreen(LabMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            energy(40_000);
            arrow(116, 60, 1, LabMachines.Splicer.TICKS);
            inventoryLabelY = 10_000;
        }

        private List<Gene> usable() {
            List<Gene> out = new ArrayList<>();
            for (Gene g : Gene.values()) if (GeneArchive.potency(GeneArchive.clientLibrary.getOrDefault(g, 0f)) > 0) out.add(g);
            return out;
        }

        private Gene at(int slot) {
            int v = menu.data(2 + slot);
            return v <= 0 || v > Gene.values().length ? null : Gene.values()[v - 1];
        }

        private void cycle(int slot, int dir) {
            List<Gene> usable = usable();
            if (usable.isEmpty()) return;
            Gene now = at(slot);
            // the cycle includes "empty" at position -1
            int index = now == null ? -1 : usable.indexOf(now);
            int next = Math.floorMod(index + 1 + dir, usable.size() + 1) - 1;
            int ordinal = next < 0 ? LabMachines.Splicer.CLEAR : usable.get(next).ordinal();
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LabMachines.Splicer.PICK + slot * 100 + ordinal);
        }

        @Override
        protected void init() {
            super.init();
            for (int i = 0; i < 3; i++) {
                int slot = i, y = topPos + 16 + i * 12;
                addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(slot, -1)).bounds(leftPos + 20, y, 12, 12).build());
                addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(slot, 1)).bounds(leftPos + 112, y, 12, 12).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("gui.headinajar.splicer.splice"),
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LabMachines.Splicer.SPLICE)).bounds(leftPos + 128, topPos + 16, 40, 18).build());
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            // gene names, coloured by potency band: Complete dark blue, Working blue, Draft amber
            for (int i = 0; i < 3; i++) {
                Gene gene = at(i);
                float potency = gene == null ? 0 : GeneArchive.potency(GeneArchive.clientLibrary.getOrDefault(gene, 0f));
                int colour = gene == null ? 0x808080 : potency >= 1 ? 0x203878 : potency >= 0.75f ? 0x3870B0 : 0xA06010;
                g.drawString(font, font.plainSubstrByWidth(gene == null ? "-" : geneName(gene), 76), 34, 18 + i * 12, colour, false);
            }
            int stability = menu.data(5), mutation = menu.data(6), load = menu.data(7);
            int colour = stability >= 60 ? 0x207020 : stability >= 30 ? 0x806010 : 0x902020;
            g.drawString(font, Component.translatable("gui.headinajar.splicer.stability", stability), 127, 36, colour, false);
            g.drawString(font, Component.translatable("gui.headinajar.splicer.mutation", mutation), 127, 44, colour, false);
            g.drawString(font, Component.translatable("gui.headinajar.splicer.load", load, LabMachines.Splicer.MAX_LOAD), 34, 51,
                    load > LabMachines.Splicer.MAX_LOAD ? 0x902020 : 0x404040, false);
        }
    }
}
