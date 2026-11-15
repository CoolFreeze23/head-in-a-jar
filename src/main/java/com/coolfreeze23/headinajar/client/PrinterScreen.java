package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity;
import com.coolfreeze23.headinajar.block.PrintCatalogue;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.menu.BodyPrinterMenu;
import com.coolfreeze23.headinajar.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Body Printer's screen: the shared machine panel, plus the Template slot (a ghost DNA Fraction while empty, a
 * locked look where specimen printing is not licensed), the template's species and size in the title row, and under the
 * slots what the print takes: today's own-body line with the Template slot empty, the scaled one with a template, or
 * why that template will not print.
 */
public class PrinterScreen extends MachineScreen<BodyPrinterMenu> {
    private static final ResourceLocation GHOST = HeadInAJar.id("textures/gui/template_ghost.png");
    /** The need box: under the slots and the gauge, two rows above the inventory. */
    private static final int NEED_X = 20, NEED_Y = 60, NEED_W = 148;

    public PrinterScreen(BodyPrinterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        energy(250_000);
        gauge(72, 17, 12, 40, 1, 4000, 0xFF5FC8C0, "gui.headinajar.saline");
        arrow(92, 30, 2, 1200);
        noInventoryLabel();
        button(120, 26, 48, 20, "gui.headinajar.printer.print", 0);
    }

    private Slot templateSlot() {
        return menu.slots.get(BodyPrinterBlockEntity.TEMPLATE);
    }

    private boolean locked() {
        return menu.data(BodyPrinterMenu.LOCKED) != 0;
    }

    /** The template's species, or null (no template, or a species this client does not have). */
    private EntityType<?> species() {
        int id = menu.data(BodyPrinterMenu.SPECIES);
        if (id < 0 || id >= BuiltInRegistries.ENTITY_TYPE.size() || !templateSlot().hasItem()) return null;
        return BuiltInRegistries.ENTITY_TYPE.byId(id);
    }

    private PrintCatalogue.Size size() {
        return PrintCatalogue.Size.ofCode(menu.data(BodyPrinterMenu.SIZE));
    }

    /** "Cow (medium)", or the species alone when it has no class. */
    private Component speciesLabel(EntityType<?> type) {
        PrintCatalogue.Size size = size();
        return size == null ? type.getDescription() : Component.translatable("gui.headinajar.printer.species", type.getDescription(), Component.translatable(size.key()));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        Slot slot = templateSlot();
        if (slot.hasItem()) return;
        int x = leftPos + slot.x, y = topPos + slot.y;
        if (locked()) {
            // locked: the frame filled in dark, struck through in the refusal red
            g.fill(x, y, x + 16, y + 16, 0xFF555555);
            for (int i = 1; i < 15; i++) {
                g.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFF902020);
                g.fill(x + 15 - i, y + i, x + 16 - i, y + i + 1, 0xFF902020);
            }
        } else {
            g.blit(GHOST, x, y, 0, 0, 16, 16, 16, 16);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // the title row: the template's species in place of the machine's name; a pause reason as the shared screen says it
        EntityType<?> type = species();
        Component name = type == null ? title : speciesLabel(type);
        int status = menu.data(menu.statusIndex());
        Component why = status > 0 && status < MachineMenu.STALLS.length ? Component.translatable("gui.headinajar.stall." + MachineMenu.STALLS[status]) : null;
        int whyX = why == null ? 0 : imageWidth - 7 - font.width(why);
        if (why != null && titleLabelX + font.width(name) + 6 > whyX) {
            g.drawString(font, why, titleLabelX, titleLabelY, 0x902020, false);
        } else {
            g.drawString(font, name, titleLabelX, titleLabelY, 0x404040, false);
            if (why != null) g.drawString(font, why, whyX, titleLabelY, 0x902020, false);
        }
        Component need;
        int color = 0x404040;
        int reason = menu.data(BodyPrinterMenu.REASON);
        if (!templateSlot().hasItem()) {
            need = Component.translatable("gui.headinajar.printer.need");
        } else if (reason > 0 && reason < PrintCatalogue.REASONS.length) {
            need = Refusals.line(PrintCatalogue.REASONS[reason], reasonArgs(reason, type));
            color = 0x902020;
        } else {
            PrintCatalogue.Size size = size();
            int[] n = new int[4];
            for (int i = 0; i < 4; i++) n[i] = size == null ? BodyPrinterBlockEntity.NEED[i] : size.of(BodyPrinterBlockEntity.NEED[i]);
            need = Component.translatable(n[2] == 1 ? "gui.headinajar.printer.need_scaled_one" : "gui.headinajar.printer.need_scaled", n[0], n[1], n[2], n[3]);
        }
        int y = NEED_Y;
        for (FormattedCharSequence part : font.split(need, NEED_W)) {
            g.drawString(font, part, NEED_X, y, color, false);
            y += font.lineHeight + 1;
        }
    }

    private Object[] reasonArgs(int reason, EntityType<?> type) {
        String key = PrintCatalogue.REASONS[reason];
        if (key.equals("printer.poor")) return new Object[]{menu.data(BodyPrinterMenu.QUALITY)};
        if (key.equals("printer.all_head")) return new Object[]{type == null ? Component.empty() : type.getDescription()};
        return new Object[0];
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int x, int y) {
        Slot slot = templateSlot();
        if (hoveredSlot != slot || !menu.getCarried().isEmpty()) {
            super.renderTooltip(g, x, y);
            return;
        }
        List<Component> lines = new ArrayList<>();
        if (!slot.hasItem()) {
            lines.add(Component.translatable(locked() ? "gui.headinajar.printer.locked" : "gui.headinajar.printer.template"));
        } else {
            EntityType<?> type = species();
            if (type != null) lines.add(speciesLabel(type));
            if (type == EntityType.VILLAGER) lines.add(Component.translatable("gui.headinajar.printer.villager").withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.addAll(getTooltipFromContainerItem(slot.getItem()));
        }
        g.renderComponentTooltip(font, lines, x, y);
    }

    /** Something that is not a template, put on the Template slot: it will not go in, and the printer says why. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // the slot under the click itself (the hovered slot is only known after the next frame is drawn)
        Slot slot = templateSlot();
        ItemStack carried = menu.getCarried();
        if (isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY) && !carried.isEmpty() && !slot.mayPlace(carried)) {
            ClientRefusals.local(Refusals.line(locked() ? "printer.not_licensed" : "printer.not_fraction"));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
