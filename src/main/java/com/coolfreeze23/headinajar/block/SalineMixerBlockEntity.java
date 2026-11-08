package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.menu.SalineMixerMenu;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Mixes Cranial Saline: 1000 mB water + sugar + kelp + redstone + 2,000 FE over 10 s gives 1000 mB saline.
 * Also bottles saline (250 mB per glass bottle). Water goes in from any side; saline comes out.
 */
public class SalineMixerBlockEntity extends BlockEntity implements MenuProvider, HasItems {
    public static final int SLOT_SUGAR = 0, SLOT_KELP = 1, SLOT_REDSTONE = 2, SLOT_BOTTLE_IN = 3, SLOT_BOTTLE_OUT = 4;
    public static final int TANK = 4000;
    public static final int BATCH_TICKS = 200;
    public static final int FE_PER_TICK = 10;

    private final Buffer energy = new Buffer(20_000, 1_000, 0, this::setChanged);
    private final FluidTank water = new FluidTank(TANK, f -> f.getFluid().isSame(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final FluidTank saline = new FluidTank(TANK, f -> f.getFluid().isSame(ModFluids.SALINE.source.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final ItemStackHandler items = new ItemStackHandler(5) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_SUGAR -> stack.is(Items.SUGAR);
                case SLOT_KELP -> stack.is(Items.KELP) || stack.is(Items.DRIED_KELP);
                case SLOT_REDSTONE -> stack.is(Items.REDSTONE);
                case SLOT_BOTTLE_IN -> stack.is(Items.GLASS_BOTTLE);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** What pipes see: water in, saline out. */
    private final IFluidHandler pipes = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? water.getFluid() : saline.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return TANK;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 ? water.isFluidValid(stack) : saline.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return water.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return saline.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return saline.drain(maxDrain, action);
        }
    };
    private int progress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> energy.getEnergyStored() / 10;
                case 1 -> water.getFluidAmount();
                case 2 -> saline.getFluidAmount();
                case 3 -> progress;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public SalineMixerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MIXER_BE.get(), pos, state);
    }

    public Buffer energy() {
        return energy;
    }

    public ItemStackHandler items() {
        return items;
    }

    public IFluidHandler pipes() {
        return pipes;
    }

    public FluidTank water() {
        return water;
    }

    public FluidTank saline() {
        return saline;
    }

    private boolean canMix() {
        return water.getFluidAmount() >= 1000 && saline.getSpace() >= 1000 && energy.getEnergyStored() >= FE_PER_TICK
                && !items.getStackInSlot(SLOT_SUGAR).isEmpty() && !items.getStackInSlot(SLOT_KELP).isEmpty() && !items.getStackInSlot(SLOT_REDSTONE).isEmpty();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SalineMixerBlockEntity mixer) {
        if (mixer.canMix()) {
            mixer.energy.set(mixer.energy.getEnergyStored() - FE_PER_TICK);
            if (++mixer.progress >= BATCH_TICKS) {
                mixer.progress = 0;
                mixer.water.drain(1000, IFluidHandler.FluidAction.EXECUTE);
                for (int slot : new int[]{SLOT_SUGAR, SLOT_KELP, SLOT_REDSTONE}) mixer.items.extractItem(slot, 1, false);
                mixer.saline.fill(new FluidStack(ModFluids.SALINE.source.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
            }
        } else if (mixer.progress > 0) {
            mixer.progress = 0;
        }
        // bottling
        ItemStack in = mixer.items.getStackInSlot(SLOT_BOTTLE_IN);
        ItemStack out = mixer.items.getStackInSlot(SLOT_BOTTLE_OUT);
        if (!in.isEmpty() && mixer.saline.getFluidAmount() >= 250 && (out.isEmpty() || out.is(ModRegistry.SALINE_BOTTLE.get()) && out.getCount() < out.getMaxStackSize())) {
            mixer.saline.drain(250, IFluidHandler.FluidAction.EXECUTE);
            mixer.items.extractItem(SLOT_BOTTLE_IN, 1, false);
            if (out.isEmpty()) mixer.items.setStackInSlot(SLOT_BOTTLE_OUT, new ItemStack(ModRegistry.SALINE_BOTTLE.get()));
            else out.grow(1);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.put("Items", items.serializeNBT(registries));
        tag.put("Water", water.writeToNBT(registries, new CompoundTag()));
        tag.put("Saline", saline.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        items.deserializeNBT(registries, tag.getCompound("Items"));
        water.readFromNBT(registries, tag.getCompound("Water"));
        saline.readFromNBT(registries, tag.getCompound("Saline"));
        progress = tag.getInt("Progress");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.headinajar.saline_mixer");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SalineMixerMenu(id, inventory, items, data, worldPosition);
    }
}
