package com.coolfreeze23.headinajar.block;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * What pipes, hoppers and other automation see of a machine's slots. One rule for every machine:
 * <ul>
 * <li>an input slot is filled from the top and the sides, takes only what that slot takes, and can't be emptied by
 * automation (the machine uses it up);</li>
 * <li>an output slot is emptied from the bottom (and the sides), and nothing can be put into it;</li>
 * <li>a head slot (and anything else marked {@link Role#HAND}) is the player's alone: automation neither sees into it
 * nor takes from it.</li>
 * </ul>
 * A query with no side (Jade, a probe) sees inputs as inputs and outputs as outputs.
 */
public final class MachineItems implements IItemHandler {
    public enum Role {
        /** Filled by automation from the top and sides; never emptied by it. */
        INPUT,
        /** Emptied by automation from the bottom and sides; never filled by it. */
        OUTPUT,
        /** Player hands only (jarred heads, the Board Room Core). */
        HAND
    }

    private final ItemStackHandler items;
    private final Role[] roles;
    private final Direction side;

    private MachineItems(ItemStackHandler items, Role[] roles, Direction side) {
        this.items = items;
        this.roles = roles;
        this.side = side;
    }

    /** The view of {@code items} from {@code side} (null: no particular side). */
    public static IItemHandler sided(ItemStackHandler items, Role[] roles, Direction side) {
        return new MachineItems(items, roles, side);
    }

    /** {@code n} slots of one role. */
    public static Role[] all(int n, Role role) {
        Role[] out = new Role[n];
        java.util.Arrays.fill(out, role);
        return out;
    }

    private Role role(int slot) {
        return slot >= 0 && slot < roles.length ? roles[slot] : Role.HAND;
    }

    @Override
    public int getSlots() {
        return items.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return role(slot) == Role.HAND ? ItemStack.EMPTY : items.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (role(slot) != Role.INPUT || side == Direction.DOWN) return stack;
        return items.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (role(slot) != Role.OUTPUT || side == Direction.UP) return ItemStack.EMPTY;
        return items.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return items.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return role(slot) == Role.INPUT && side != Direction.DOWN && items.isItemValid(slot, stack);
    }

    /**
     * Loads saved slots into {@code items} keeping its own slot count: a machine saved with fewer slots (an older
     * version) gets its new slots empty instead of a handler too small for it; extra saved slots are dropped with a
     * warning.
     */
    public static void load(ItemStackHandler items, CompoundTag tag, HolderLookup.Provider registries) {
        int size = items.getSlots();
        items.deserializeNBT(registries, tag);
        int saved = items.getSlots();
        if (saved == size) return;
        ItemStack[] keep = new ItemStack[size];
        for (int i = 0; i < size; i++) keep[i] = i < saved ? items.getStackInSlot(i) : ItemStack.EMPTY;
        for (int i = size; i < saved; i++) {
            if (!items.getStackInSlot(i).isEmpty()) {
                com.coolfreeze23.headinajar.HeadInAJar.LOGGER.warn("A machine saved with {} slots now has {}; dropping {}", saved, size, items.getStackInSlot(i));
            }
        }
        items.setSize(size);
        for (int i = 0; i < size; i++) if (!keep[i].isEmpty()) items.setStackInSlot(i, keep[i]);
    }
}
