package com.coolfreeze23.headinajar.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;

/** A machine menu: the machine's slots at the given positions, synced numbers, and the player inventory under them. */
public class MachineMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockPos pos;
    private final int machineSlots;

    public MachineMenu(MenuType<?> type, int id, Inventory inventory, IItemHandler handler, ContainerData data, BlockPos pos, int[][] slotXY) {
        this(type, id, inventory, handler, data, pos, slotXY, 84);
    }

    /** {@code invY}: where the player's inventory starts (84 on a standard 166-high screen). */
    public MachineMenu(MenuType<?> type, int id, Inventory inventory, IItemHandler handler, ContainerData data, BlockPos pos, int[][] slotXY, int invY) {
        super(type, id);
        this.data = data;
        this.pos = pos;
        this.machineSlots = slotXY.length;
        for (int i = 0; i < slotXY.length; i++) addSlot(new MachineSlot(handler, i, slotXY[i][0], slotXY[i][1]));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, invY + 58));
        addDataSlots(data);
    }

    public int data(int index) {
        return data.get(index);
    }

    /** Why a machine is paused, as synced in its status number: 0 running or idle, then these (lang {@code gui.headinajar.stall.<id>}). */
    public static final String[] STALLS = {"", "power", "output", "input", "unreadable"};
    public static final int RUNNING = 0, NO_POWER = 1, OUTPUT_BLOCKED = 2, NO_INPUT = 3, UNREADABLE = 4;

    /** The synced number holding the machine's stall reason (see {@link #STALLS}), or -1 when it has none. */
    public int statusIndex() {
        return -1;
    }

    /**
     * A machine slot whose changes reach the machine. Vanilla's shift-click merge changes a stack in place and then calls
     * {@link #setChanged()}, which for a plain item-handler slot tells nobody, so the block entity was never marked to
     * be saved. Here it goes through the handler, which marks it.
     */
    public static class MachineSlot extends SlotItemHandler {
        public MachineSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public void setChanged() {
            if (getItemHandler() instanceof IItemHandlerModifiable modifiable) modifiable.setStackInSlot(getContainerSlot(), modifiable.getStackInSlot(getContainerSlot()));
        }
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // only into slots that take it: vanilla's merge would also top up an output slot holding the same item
            boolean moved = false;
            for (int i = 0; i < machineSlots && !stack.isEmpty(); i++) {
                if (slots.get(i).mayPlace(stack) && moveItemStackTo(stack, i, i + 1, false)) moved = true;
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    /** Closed (by the player, by walking away, by leaving): a kit machine's hatch or drawer may shut. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide && player.level().getBlockEntity(pos) instanceof com.coolfreeze23.headinajar.machine.KitMachine machine) {
            machine.kit().stopOpen(player);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getCenter()) < 64 && player.level().getBlockEntity(pos) != null;
    }
}
