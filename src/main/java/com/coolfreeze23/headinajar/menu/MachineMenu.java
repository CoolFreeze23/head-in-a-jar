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
import net.neoforged.neoforge.items.SlotItemHandler;

/** A machine menu: the machine's slots at the given positions, synced numbers, and the player inventory under them. */
public class MachineMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockPos pos;
    private final int machineSlots;

    public MachineMenu(MenuType<?> type, int id, Inventory inventory, IItemHandler handler, ContainerData data, BlockPos pos, int[][] slotXY) {
        super(type, id);
        this.data = data;
        this.pos = pos;
        this.machineSlots = slotXY.length;
        for (int i = 0; i < slotXY.length; i++) addSlot(new SlotItemHandler(handler, i, slotXY[i][0], slotXY[i][1]));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        addDataSlots(data);
    }

    public int data(int index) {
        return data.get(index);
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
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getCenter()) < 64 && player.level().getBlockEntity(pos) != null;
    }
}
