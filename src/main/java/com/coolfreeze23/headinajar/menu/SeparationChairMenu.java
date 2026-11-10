package com.coolfreeze23.headinajar.menu;

import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** The chair's console: four input slots, the checklist and Form 27-B. */
public class SeparationChairMenu extends AbstractContainerMenu {
    public static final int BUTTON_SIGN = 0;
    public static final int[][] SLOT_XY = {{26, 26}, {26, 48}, {48, 26}, {48, 48}};

    private final SeparationChairBlockEntity chair;
    private final ContainerData data;
    private final BlockPos pos;

    /** Client side. */
    public SeparationChairMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, inventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof SeparationChairBlockEntity c ? c : null,
                new SimpleContainerData(3));
    }

    public SeparationChairMenu(int id, Inventory inventory, SeparationChairBlockEntity chair, ContainerData data) {
        super(ModRegistry.CHAIR_MENU.get(), id);
        this.chair = chair;
        this.data = data;
        this.pos = chair == null ? BlockPos.ZERO : chair.getBlockPos();
        ItemStackHandler items = chair == null ? new ItemStackHandler(4) : chair.items();
        for (int i = 0; i < 4; i++) addSlot(new PartSlot(items, i, SLOT_XY[i][0], SLOT_XY[i][1]));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new net.minecraft.world.inventory.Slot(inventory, col + row * 9 + 9, 8 + col * 18, 98 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new net.minecraft.world.inventory.Slot(inventory, col, 8 + col * 18, 156));
        addDataSlots(data);
    }

    /** A part slot: locked from the signature to the end of the operation, so nothing leaves before the cut uses it. */
    private class PartSlot extends SlotItemHandler {
        PartSlot(ItemStackHandler items, int index, int x, int y) {
            super(items, index, x, y);
        }

        private boolean locked() {
            return chair != null && chair.running();
        }

        @Override
        public boolean mayPickup(Player player) {
            return !locked() && super.mayPickup(player);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !locked() && super.mayPlace(stack);
        }
    }

    public SeparationChairBlockEntity chair() {
        return chair;
    }

    public int energy() {
        return data.get(0) * 10;
    }

    public int opTick() {
        return data.get(1);
    }

    public int wear() {
        return data.get(2);
    }

    /** Whether the viewer is the one sitting in this chair. */
    public boolean viewerSeated(Player player) {
        return player.getVehicle() instanceof com.coolfreeze23.headinajar.entity.SeatEntity seat && seat.chairPos().equals(pos);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return id == BUTTON_SIGN && player instanceof ServerPlayer server && chair != null && chair.sign(server);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 4) {
            if (chair != null && chair.running()) return ItemStack.EMPTY;
            if (!moveItemStackTo(stack, 4, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < 4 && !moved; i++) {
                if (SeparationChairBlockEntity.validFor(i, stack)) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return chair != null && !chair.isRemoved() && player.distanceToSqr(pos.getCenter()) < 64;
    }
}
