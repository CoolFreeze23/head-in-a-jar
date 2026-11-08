package com.coolfreeze23.headinajar.block;

import net.neoforged.neoforge.energy.EnergyStorage;

/** An FE buffer that tells its block entity when it changes, and that the block itself can drain or fill directly. */
public class Buffer extends EnergyStorage {
    private final Runnable onChange;

    public Buffer(int capacity, int maxReceive, int maxExtract, Runnable onChange) {
        super(capacity, maxReceive, maxExtract);
        this.onChange = onChange;
    }

    public void set(int value) {
        int v = Math.max(0, Math.min(capacity, value));
        if (v != energy) {
            energy = v;
            onChange.run();
        }
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        int got = super.receiveEnergy(toReceive, simulate);
        if (got > 0 && !simulate) onChange.run();
        return got;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        int out = super.extractEnergy(toExtract, simulate);
        if (out > 0 && !simulate) onChange.run();
        return out;
    }
}
