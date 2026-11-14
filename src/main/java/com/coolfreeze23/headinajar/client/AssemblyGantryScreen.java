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
        imageHeight = 184;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        add(x + 6, y + 74, 52, 14, "gui.headinajar.gantry.assemble", AssemblyGantryMenu.ASSEMBLE);
        add(x + 59, y + 74, 66, 14, "gui.headinajar.gantry.disassemble", AssemblyGantryMenu.DISASSEMBLE);
        add(x + 126, y + 74, 44, 14, "gui.headinajar.gantry.repair", AssemblyGantryMenu.REPAIR);
        add(x + 117, y + 55, 13, 14, "<", AssemblyGantryMenu.PREV);
        add(x + 130, y + 55, 13, 14, ">", AssemblyGantryMenu.NEXT);
        add(x + 145, y + 55, 13, 14, "-", AssemblyGantryMenu.MINUS);
        add(x + 158, y + 55, 13, 14, "+", AssemblyGantryMenu.PLUS);
    }

    private void add(int x, int y, int w, int h, String key, int id) {
        addRenderableWidget(Button.builder(Component.translatable(key), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
                .bounds(x, y, w, h).build());
    }

    private MechaFrame frame() {
        return menu.getSlot(AssemblyGantryBlockEntity.SLOT_FRAME).getItem().getItem() instanceof MechaFrameItem f ? f.frame() : null;
    }

    /** The charge the energy gauge shows: a drop (a repair's top-up, an assembly) runs down over about two seconds. */
    private float shownEnergy = -1;
    private float dropPerTick;
    private long lastGaugeTick = -1;

    private void drawEnergy(GuiGraphics g) {
        int target = menu.data(0) * 10;
        long tick = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        if (shownEnergy < 0 || target > shownEnergy) {
            shownEnergy = target;
            dropPerTick = 0;
        } else if (target < shownEnergy) {
            if (dropPerTick == 0) dropPerTick = Math.max(1, (shownEnergy - target) / 40f);
            if (tick != lastGaugeTick) shownEnergy = Math.max(target, shownEnergy - dropPerTick * Math.max(1, tick - lastGaugeTick));
            if (shownEnergy <= target) dropPerTick = 0;
        }
        lastGaugeTick = tick;
        // over the kit's gauge (8, 17, 6 x 56): the same colours, the eased level
        int gx = leftPos + 8, gy = topPos + 17, h = 56;
        int filled = (int) Math.min(h, shownEnergy * h / 100_000f);
        g.fill(gx, gy, gx + 6, gy + h, 0xFF26323A);
        g.fill(gx, gy + h - filled, gx + 6, gy + h, 0xFF3FD8F0);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        drawEnergy(g);
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

    /** The mouse is on the mass line of a loadout over the lock (its tooltip is drawn after the labels). */
    private boolean lockedTip;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (lockedTip) g.renderComponentTooltip(font, java.util.List.of(Component.translatable("gui.headinajar.gantry.locked_tip",
                Math.round(com.coolfreeze23.headinajar.mecha.MechaLogic.LOAD_LOCK * 100))), mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        MechaFrame frame = frame();
        lockedTip = false;
        if (frame == null) {
            g.drawString(font, Component.translatable("gui.headinajar.gantry.need_frame"), 8, 91, 0x804040, false);
            return;
        }
        int mass = frame.dryMass, power = Math.round(frame.idleDrain());
        for (int i = 0; i < frame.totalSlots(); i++) {
            ItemStack stack = menu.getSlot(AssemblyGantryBlockEntity.FIRST_MODULE + i).getItem();
            if (stack.getItem() instanceof MechaModuleItem m) {
                mass += m.type().mass;
                power += m.type().idlePower;
            }
        }
        float load = mass / (float) frame.load;
        // over the load lock the machine is built but cannot move: the line says so, and its tooltip says what to do
        boolean locked = load > com.coolfreeze23.headinajar.mecha.MechaLogic.LOAD_LOCK;
        Component massLine = Component.translatable(locked ? "gui.headinajar.gantry.mass_locked" : "gui.headinajar.gantry.mass", mass, frame.load);
        g.drawString(font, massLine, 8, 91, load > 1 ? 0xC03030 : 0x404040, false);
        lockedTip = locked && mouseX - leftPos >= 8 && mouseX - leftPos < 8 + font.width(massLine) && mouseY - topPos >= 90 && mouseY - topPos < 100;
        int selected = menu.data(1);
        ItemStack sel = selected < frame.totalSlots() ? menu.getSlot(AssemblyGantryBlockEntity.FIRST_MODULE + selected).getItem() : ItemStack.EMPTY;
        String kind = selected < frame.totalSlots() ? frame.slotAt(selected).name().toLowerCase(Locale.ROOT) : "torso";
        Component slotLine = sel.getItem() instanceof MechaModuleItem m && m.type().slider != null
                ? Component.translatable("gui.headinajar.gantry.slider", Component.translatable("mecha_slider.headinajar." + m.type().slider), MechaModuleItem.slider(sel))
                : Component.translatable("mecha_slot.headinajar." + kind);
        g.drawString(font, slotLine, 96, 91, 0x404040, false);
        g.drawString(font, Component.translatable("gui.headinajar.gantry.power", power, frame.heatCap), 112, 6, 0x606060, false);
    }
}
