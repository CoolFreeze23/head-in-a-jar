package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.item.MechaFrameItem;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * The Gantry: frame and plating on the left, module slots labelled by kind, live totals (mass against the frame's load
 * rating, standing power drain, heat capacity), the selected slot's slider, and Assemble / Disassemble / Repair.
 */
public class AssemblyGantryScreen extends MachineScreen<AssemblyGantryMenu> {
    public AssemblyGantryScreen(AssemblyGantryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        energy(100_000);
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        add(x + 44, y + 56, 40, 14, "gui.headinajar.gantry.assemble", AssemblyGantryMenu.ASSEMBLE);
        add(x + 86, y + 56, 44, 14, "gui.headinajar.gantry.disassemble", AssemblyGantryMenu.DISASSEMBLE);
        add(x + 132, y + 56, 38, 14, "gui.headinajar.gantry.repair", AssemblyGantryMenu.REPAIR);
        add(x + 135, y + 37, 9, 14, "<", AssemblyGantryMenu.PREV);
        add(x + 144, y + 37, 9, 14, ">", AssemblyGantryMenu.NEXT);
        add(x + 153, y + 37, 9, 14, "-", AssemblyGantryMenu.MINUS);
        add(x + 162, y + 37, 9, 14, "+", AssemblyGantryMenu.PLUS);
    }

    private void add(int x, int y, int w, int h, String key, int id) {
        addRenderableWidget(Button.builder(Component.translatable(key), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
                .bounds(x, y, w, h).build());
    }

    private MechaFrame frame() {
        return menu.getSlot(AssemblyGantryBlockEntity.SLOT_FRAME).getItem().getItem() instanceof MechaFrameItem f ? f.frame() : null;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        MechaFrame frame = frame();
        int selected = menu.data(1);
        for (int i = 0; i < AssemblyGantryBlockEntity.MODULE_SLOTS; i++) {
            Slot slot = menu.getSlot(AssemblyGantryBlockEntity.FIRST_MODULE + i);
            int sx = leftPos + slot.x, sy = topPos + slot.y;
            if (frame == null || i >= frame.totalSlots()) {
                g.fill(sx, sy, sx + 16, sy + 16, 0xC0555555);
            } else {
                int tint = switch (frame.slotAt(i)) {
                    case LEGS -> 0x4060C060;
                    case BACK -> 0x406080E0;
                    case CORE -> 0x40E0C040;
                    case TORSO -> 0x40A0A0A0;
                    case ARM -> 0x40E06060;
                    case SENSOR -> 0x40C060E0;
                };
                g.fill(sx, sy, sx + 16, sy + 16, tint);
                if (i == selected) g.renderOutline(sx - 1, sy - 1, 18, 18, 0xFFFFFF60);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        MechaFrame frame = frame();
        if (frame == null) {
            g.drawString(font, Component.translatable("gui.headinajar.gantry.need_frame"), 8, 73, 0x804040, false);
            return;
        }
        int mass = frame.dryMass, power = frame == MechaFrame.BULWARK ? 5 : frame == MechaFrame.CHIMERA ? 3 : 2;
        for (int i = 0; i < frame.totalSlots(); i++) {
            ItemStack stack = menu.getSlot(AssemblyGantryBlockEntity.FIRST_MODULE + i).getItem();
            if (stack.getItem() instanceof MechaModuleItem m) {
                mass += m.type().mass;
                power += m.type().idlePower;
            }
        }
        float load = mass / (float) frame.load;
        g.drawString(font, Component.translatable("gui.headinajar.gantry.mass", mass, frame.load), 22, 73, load > 1 ? 0xC03030 : 0x404040, false);
        int selected = menu.data(1);
        ItemStack sel = selected < frame.totalSlots() ? menu.getSlot(AssemblyGantryBlockEntity.FIRST_MODULE + selected).getItem() : ItemStack.EMPTY;
        String kind = selected < frame.totalSlots() ? frame.slotAt(selected).name().toLowerCase(Locale.ROOT) : "torso";
        Component slotLine = sel.getItem() instanceof MechaModuleItem m && m.type().slider != null
                ? Component.translatable("gui.headinajar.gantry.slider", Component.translatable("mecha_slider.headinajar." + m.type().slider), MechaModuleItem.slider(sel))
                : Component.translatable("mecha_slot.headinajar." + kind);
        g.drawString(font, slotLine, 104, 73, 0x404040, false);
        g.drawString(font, Component.translatable("gui.headinajar.gantry.power", power, frame.heatCap), 112, 6, 0x606060, false);
    }
}
