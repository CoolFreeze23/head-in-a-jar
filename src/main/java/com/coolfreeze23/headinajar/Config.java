package com.coolfreeze23.headinajar;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static {
        B.push("jar");
    }

    public static final ModConfigSpec.IntValue MAX_CHARGE = B
            .comment("Energy (FE) a Life Support Jar holds.")
            .defineInRange("maxCharge", 100_000, 1_000, 10_000_000);
    public static final ModConfigSpec.IntValue IDLE_DRAIN = B
            .comment("FE per tick a jar spends keeping its head alive while the player is the jar (0 = never).")
            .defineInRange("idleDrain", 2, 0, 10_000);
    public static final ModConfigSpec.IntValue TELEKINESIS_RANGE = B
            .comment("How far away Telekinesis can grab things, in blocks.")
            .defineInRange("telekinesisRange", 16, 4, 64);
    public static final ModConfigSpec.DoubleValue TELEKINESIS_MAX_HARDNESS = B
            .comment("Hardest block Telekinesis can lift (obsidian is 50).")
            .defineInRange("telekinesisMaxHardness", 50.0, 0.0, 1000.0);

    static {
        B.pop().push("booth");
    }

    public static final ModConfigSpec.IntValue BOOTH_ENERGY = B
            .comment("FE one separation costs. The chair won't start unless its buffer holds all of it.")
            .defineInRange("energyPerOp", 40_000, 0, 1_000_000);
    public static final ModConfigSpec.BooleanValue BOTCH_ENABLED = B
            .comment("A worn blade can botch the cut and leave your view upside down for a while.")
            .define("botchEnabled", true);

    static {
        B.pop().push("link");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> GOGGLE_RANGES = B
            .comment("Neuro-link range in blocks for Relay Goggles Mk I, II and III.")
            .defineList("goggleRanges", List.of(48, 160, 640), () -> 48, o -> o instanceof Integer i && i > 0);
    public static final ModConfigSpec.ConfigValue<String> PORTAL_RULE = B
            .comment("A linked body without its head at a portal: 'block' stops it, 'break' lets it through and the link breaks.")
            .define("portalRule", "block", o -> "block".equals(o) || "break".equals(o));
    public static final ModConfigSpec.BooleanValue ALLOW_MANUAL_UNLINK = B
            .comment("The Swap key may leave the body behind at any signal strength.")
            .define("allowManualUnlink", true);

    static {
        B.pop().push("life");
    }

    public static final ModConfigSpec.ConfigValue<String> LINK_LOSS_INVENTORY = B
            .comment("When a linked body dies: 'corpse' keeps your things on the corpse, 'keep' leaves them with you, 'vanilla' drops them.")
            .define("linkLossInventory", "corpse", o -> "corpse".equals(o) || "keep".equals(o) || "vanilla".equals(o));
    public static final ModConfigSpec.IntValue SALINE_DRAIN_SECONDS = B
            .comment("Seconds per mB of Cranial Saline a head uses while out in the world (docked and carried heads use none).")
            .defineInRange("salineDrainSeconds", 12, 1, 3600);
    public static final ModConfigSpec.BooleanValue PROTECT_OFFLINE = B
            .comment("Idle bodies, corpses and placed heads of offline players can't be hurt.")
            .define("protectOfflineBodies", true);

    static {
        B.pop();
        B.push("genetics");
    }

    public static final ModConfigSpec.BooleanValue PVP_TURRETS = B
            .comment("Turret Pedestals may shoot players who are not on the owner's team.")
            .define("pvpTurrets", false);
    public static final ModConfigSpec.BooleanValue KEEP_GENES = B
            .comment("Spliced genes stay in your body when you die and respawn.")
            .define("keepGenesOnDeath", true);

    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    // --- client ---------------------------------------------------------------------------------
    private static final ModConfigSpec.Builder C = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue PHOTOSENSITIVE = C
            .comment("No flashing, tearing or static bursts in the link HUD. Signal effects are shown as text instead.")
            .define("photosensitive", false);
    public static final ModConfigSpec.IntValue STATIC_INTENSITY = C
            .comment("How strong the link's static and scanlines are, in percent.")
            .defineInRange("staticIntensity", 100, 0, 100);
    public static final ModConfigSpec.BooleanValue HANDSHAKE = C
            .comment("Play the Neural Handshake timing game when first mounting a host (off: a flat +5% sync).")
            .define("handshake", true);
    public static final ModConfigSpec.BooleanValue DOLORES_VOICE = C
            .comment("DOLORES, the Separation Chair, speaks her lines aloud (off: her lines are shown as text only).")
            .define("doloresVoice", true);
    public static final ModConfigSpec.IntValue SCREEN_SHAKE = C
            .comment("How much the mod's effects may move the camera (shake, kicks, field-of-view pulls), in percent. The photosensitive option turns them all off.")
            .defineInRange("screenShake", 100, 0, 100);
    public static final ModConfigSpec.BooleanValue MEMOS = C
            .comment("DOLORES's memos: a line in chat, with a beep, the first time something new happens (off: none are shown).")
            .define("memos", true);
    public static final ModConfigSpec.BooleanValue HUD_NUMBERS = C
            .comment("Show the numbers beside the jar's bars all the time (off: only while low, or for a few seconds after they change).")
            .define("hudNumbers", false);
    public static final ModConfigSpec CLIENT_SPEC = C.build();

    public static int goggleRange(int tier) {
        if (!SPEC.isLoaded()) return new int[]{48, 160, 640}[Math.max(0, Math.min(2, tier - 1))];
        List<? extends Integer> ranges = GOGGLE_RANGES.get();
        return ranges.isEmpty() ? 48 : ranges.get(Math.max(0, Math.min(ranges.size() - 1, tier - 1)));
    }

    public static <T> T get(ModConfigSpec.ConfigValue<T> value, T fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    private Config() {
    }
}
