package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.ItemStackHandler;

/** A stack of copper discs and frog legs: burns meat into 20 FE/t and pushes it into neighbouring machines. */
public class GalvanicPileBlockEntity extends BlockEntity {
    public static final int CAPACITY = 50_000;
    public static final int RATE = 20;
    public static final int PUSH = 1_000;

    private final Buffer energy = new Buffer(CAPACITY, 0, PUSH, this::setChanged);
    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return fuelValue(stack) > 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int burning;

    public GalvanicPileBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PILE_BE.get(), pos, state);
    }

    /** FE one item of fuel gives. */
    public static int fuelValue(ItemStack stack) {
        if (stack.is(Items.ROTTEN_FLESH)) return 4_000;
        if (stack.is(Items.SPIDER_EYE)) return 6_000;
        if (stack.is(Tags.Items.FOODS_RAW_MEAT) || stack.is(Tags.Items.FOODS_RAW_FISH)) return 3_000;
        return 0;
    }

    public Buffer energy() {
        return energy;
    }

    public ItemStackHandler fuel() {
        return fuel;
    }

    public int burning() {
        return burning;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GalvanicPileBlockEntity pile) {
        if (pile.burning <= 0 && pile.energy.getEnergyStored() < CAPACITY) {
            ItemStack stack = pile.fuel.getStackInSlot(0);
            int value = fuelValue(stack);
            if (value > 0) {
                pile.fuel.extractItem(0, 1, false);
                pile.burning = value;
            }
        }
        if (pile.burning > 0 && pile.energy.getEnergyStored() < CAPACITY) {
            int made = Math.min(RATE, pile.burning);
            pile.burning -= made;
            pile.energy.set(pile.energy.getEnergyStored() + made);
        }
        boolean lit = pile.burning > 0;
        if (state.getValue(GalvanicPileBlock.LIT) != lit) level.setBlock(pos, state.setValue(GalvanicPileBlock.LIT, lit), 3);
        pushEnergy(level, pos, pile.energy);
    }

    /** Gives energy to the machines on every side. */
    public static void pushEnergy(Level level, BlockPos pos, Buffer energy) {
        for (Direction dir : Direction.values()) {
            if (energy.getEnergyStored() <= 0) return;
            var target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
            if (target == null || !target.canReceive()) continue;
            int sent = target.receiveEnergy(Math.min(PUSH, energy.getEnergyStored()), false);
            if (sent > 0) energy.set(energy.getEnergyStored() - sent);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.put("Fuel", fuel.serializeNBT(registries));
        tag.putInt("Burning", burning);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        fuel.deserializeNBT(registries, tag.getCompound("Fuel"));
        burning = tag.getInt("Burning");
    }
}
