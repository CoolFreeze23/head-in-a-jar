package com.coolfreeze23.headinajar;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_CHARGE = B
            .comment("Charge a life-support jar holds.")
            .defineInRange("maxCharge", 1000, 100, 100000);
    public static final ModConfigSpec.IntValue IDLE_DRAIN_INTERVAL = B
            .comment("A jar loses 1 charge every this many ticks just keeping its head alive (0 = never).")
            .defineInRange("idleDrainInterval", 40, 0, 72000);
    public static final ModConfigSpec.IntValue TELEKINESIS_RANGE = B
            .comment("How far away Telekinesis can grab things, in blocks.")
            .defineInRange("telekinesisRange", 16, 4, 64);
    public static final ModConfigSpec.DoubleValue TELEKINESIS_MAX_HARDNESS = B
            .comment("Hardest block Telekinesis can lift (obsidian is 50).")
            .defineInRange("telekinesisMaxHardness", 50.0, 0.0, 1000.0);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {
    }
}
