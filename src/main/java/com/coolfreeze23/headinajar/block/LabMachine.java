package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.menu.LabMenu;
import com.coolfreeze23.headinajar.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The genetics lab's shared machine: an FE buffer, a few slots, a progress counter, and the gene library of whoever
 * last started it (so an unattended run still files its results with the right team).
 */
public abstract class LabMachine extends BlockEntity implements MenuProvider, HasItems {
    protected final Buffer energy;
    protected final ItemStackHandler items;
    protected int progress;
    protected String owner = "";
    private final LabMenu.Kind kind;

    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return i == 0 ? energy.getEnergyStored() / 10 : i == 1 ? progress : value(i);
        }

        @Override
        public void set(int i, int v) {
        }

        @Override
        public int getCount() {
            return kind.data;
        }
    };

    protected LabMachine(BlockEntityType<?> type, BlockPos pos, BlockState state, LabMenu.Kind kind, int capacity) {
        this(type, pos, state, kind, capacity, 0);
    }

    protected LabMachine(BlockEntityType<?> type, BlockPos pos, BlockState state, LabMenu.Kind kind, int capacity, int extract) {
        super(type, pos, state);
        this.kind = kind;
        this.energy = new Buffer(capacity, extract > 0 ? 0 : Math.max(1000, capacity / 20), extract, this::setChanged);
        this.items = new ItemStackHandler(kind.slots.length) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return valid(slot, stack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
    }

    /** Synced number {@code i} (2 and up). */
    protected int value(int i) {
        return 0;
    }

    protected abstract boolean valid(int slot, ItemStack stack);

    /** A menu button. */
    public boolean button(ServerPlayer player, int id) {
        return false;
    }

    public Buffer energy() {
        return energy;
    }

    @Override
    public ItemStackHandler items() {
        return items;
    }

    public int progress() {
        return progress;
    }

    /** Spends FE; false (and nothing spent) if there is not enough. */
    protected boolean spend(int fe) {
        if (energy.getEnergyStored() < fe) return false;
        energy.set(energy.getEnergyStored() - fe);
        return true;
    }

    /** Puts a result into an output slot; false if it does not fit. */
    protected boolean output(int slot, ItemStack stack, boolean simulate) {
        return items.insertItem(slot, stack, simulate).isEmpty();
    }

    /** Output slots refuse player inserts through {@link #valid}; this bypasses that check. */
    protected boolean place(int slot, ItemStack stack, boolean simulate) {
        ItemStack there = items.getStackInSlot(slot);
        if (there.isEmpty()) {
            if (!simulate) items.setStackInSlot(slot, stack.copy());
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(there, stack) || there.getCount() + stack.getCount() > there.getMaxStackSize()) return false;
        if (!simulate) there.grow(stack.getCount());
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Progress", progress);
        tag.putString("Owner", owner);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        items.deserializeNBT(registries, tag.getCompound("Items"));
        progress = tag.getInt("Progress");
        owner = tag.getString("Owner");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.headinajar." + kind.id());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        if (player instanceof ServerPlayer server) Network.syncArchive(server);
        return new LabMenu(kind, id, inventory, items, data, worldPosition);
    }
}
