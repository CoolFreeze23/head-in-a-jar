package com.coolfreeze23.headinajar.machine;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The Noggin Lamp every machine wears, as a blockstate (the lens is a baked part chosen by this property, so a lamp
 * change costs one chunk re-mesh and nothing per frame). Levels (a tank, a gauge) are blockstates too, 0 to 4.
 */
public enum MachineLamp implements StringRepresentable {
    /** No power, nothing to do. */
    OFF,
    /** Ready: stocked or charged, not working. */
    READY,
    /** Waiting on something the player can fix: an output full, an input missing mid-job. */
    WAIT,
    /** Working (the lens pulses). */
    WORK,
    /** Working on short power: the brown-out (a dimmer pulse). */
    LOW,
    /** A refusal, for a moment (steady red). */
    FAULT;

    public static final EnumProperty<MachineLamp> LAMP = EnumProperty.create("lamp", MachineLamp.class);

    /** A level 0-4 (tank, gauge). */
    public static IntegerProperty level(String name) {
        return IntegerProperty.create(name, 0, 4);
    }

    /** The step 0-4 of {@code amount} in {@code capacity}: 0 empty, then n for more than (n-1)/4 of it. */
    public static int step(int amount, int capacity) {
        if (amount <= 0) return 0;
        return Math.max(1, Math.min(4, (int) Math.ceil(amount * 4.0 / capacity)));
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
