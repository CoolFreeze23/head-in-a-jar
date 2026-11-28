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
    public static final ModConfigSpec.BooleanValue PHASE_THROUGH_WALLS = B
            .comment("The Phase gene may dash through walls (false refuses it: \"This server keeps its walls.\").")
            .define("phaseThroughWalls", true);
    public static final ModConfigSpec.BooleanValue BULLDOZE_BREAKS_BLOCKS = B
            .comment("The Bulldoze gene breaks leaves, crops, flowers and cobwebs it walks into (false: it only shoves).")
            .define("bulldozeBreaksBlocks", true);
    public static final ModConfigSpec.BooleanValue SPECIMEN_PRINTING = B
            .comment("The Body Printer's Template slot prints hosts from DNA Fractions (false keeps the slot locked).")
            .define("specimenPrinting", true);

    static {
        B.pop().push("saw");
    }

    public static final ModConfigSpec.BooleanValue ALLOW_BABIES = B
            .comment("Babies may be sawn (true brings back the behaviour before 0.6.0).")
            .define("allowBabies", false);

    static {
        B.pop().push("staff");
    }

    public static final ModConfigSpec.BooleanValue MORALE = B
            .comment("Staff morale moves with their pay (false: morale never moves and everyone shows Content; wages are still taken).")
            .define("morale", true);
    public static final ModConfigSpec.BooleanValue COMPLAINTS = B
            .comment("Disengaged heads on plinths complain over their jars every two minutes (false: they sulk in silence).")
            .define("complaints", true);
    public static final ModConfigSpec.BooleanValue STAFF_ENABLED = B
            .comment("Living staff can be hired with a Staff Lanyard (false: the Lanyard refuses, Time Clocks only keep time, every staff brain is off).")
            .define("enabled", true);
    public static final ModConfigSpec.BooleanValue HIRE_HOSTILES = B
            .comment("Hostile creatures can be hired, at half their health or less (false: only passive and neutral ones).")
            .define("hireHostiles", true);
    public static final ModConfigSpec.IntValue MAX_PER_PLAYER = B
            .comment("Most living staff one player may employ.")
            .defineInRange("maxPerPlayer", 24, 0, 1000);
    public static final ModConfigSpec.IntValue MAX_PER_CLOCK = B
            .comment("Most staff on one Time Clock's roster.")
            .defineInRange("maxPerClock", 8, 1, 64);
    public static final ModConfigSpec.IntValue THINK_TICKS = B
            .comment("Ticks between a worker's thinks (staggered by entity id). Higher is cheaper and slower.")
            .defineInRange("thinkTicks", 10, 5, 40);
    public static final ModConfigSpec.IntValue CLOCK_SPACING = B
            .comment("Blocks needed between two Time Clocks (on the level, at any height).")
            .defineInRange("clockSpacing", 8, 1, 64);
    public static final ModConfigSpec.IntValue SITE_RADIUS = B
            .comment("How far a site reaches out from its Time Clock on the level (it reaches 8 up and 8 down). Never larger than 16.")
            .defineInRange("siteRadius", 16, 8, 16);
    public static final ModConfigSpec.IntValue DETAIL_SIZE = B
            .comment("Most staff on one player's detail (the Supervisor's Whistle).")
            .defineInRange("detailSize", 6, 0, 16);
    public static final ModConfigSpec.BooleanValue DETAIL_SHUTTLE = B
            .comment("A detail left far from home takes the company shuttle back to its Time Clock (false: it stays where it was left, on Hold).")
            .define("detailShuttle", true);
    public static final ModConfigSpec.BooleanValue SECONDMENT = B
            .comment("A Secondment Collar can be fitted to a host and a jarred head seated on it (false: the collar refuses; heads already seated stand by where they are).")
            .define("secondment", true);
    public static final ModConfigSpec.BooleanValue PROMOTION = B
            .comment("A Team Lead of your own can be promoted to the Board with the Surgical Saw and an empty jar (false: the saw refuses; reviews and grades go on).")
            .define("promotion", true);
    public static final ModConfigSpec.IntValue PROMOTED_ARRAY_CAP = B
            .comment("Promoted heads per Cerebral Array that make the promoted rate; any more make their species' normal rate.")
            .defineInRange("promotedArrayCap", 3, 0, 9);
    public static final ModConfigSpec.IntValue PROMOTED_ARRAY_BONUS = B
            .comment("What a promoted head makes at a Cerebral Array, in percent of its species' rate (100: no bonus).")
            .defineInRange("promotedArrayBonus", 150, 100, 300);
    public static final ModConfigSpec.BooleanValue STRIKES = B
            .comment("Neglected sites go on strike at dawn (never in a world's first week; false: no strikes, ever).")
            .define("strikes", true);
    public static final ModConfigSpec.BooleanValue BOARD_UNIONISES = B
            .comment("When the Board really carries a motion to unionise, a site near it that already meets the strike test walks out at once (false: the motion is only words).")
            .define("boardUnionises", true);

    static {
        B.pop().push("heads");
    }

    public static final ModConfigSpec.BooleanValue DESK_JOBS = B
            .comment("Fed heads on plinths do their desk jobs (false keeps titles, bells and lunch, but no desk jobs).")
            .define("deskJobs", true);
    public static final ModConfigSpec.BooleanValue LOOKOUTS = B
            .comment("A head with no attack fits a Turret Pedestal as a Lookout (false brings back the \"nothing to shoot with\" refusal).")
            .define("lookouts", true);
    public static final ModConfigSpec.BooleanValue WARDEN_DARKNESS = B
            .comment("A warden head with a Grievance shrieks and gives Darkness to every player within 16 blocks.")
            .define("wardenDarkness", true);
    public static final ModConfigSpec.BooleanValue LIVING_LIBRARY = B
            .comment("Placed and station heads give one blood sample a day to a sampling tool or a Leech Jar (false: tools do nothing on heads).")
            .define("livingLibrary", true);

    static {
        B.pop().push("bodies");
    }

    public static final ModConfigSpec.IntValue TREADMILL_OUTPUT = B
            .comment("What a Corporate Wellness Treadmill makes, in percent of its species' rate (100: a cow makes 10 FE/t).")
            .defineInRange("treadmillOutput", 100, 0, 1000);
    public static final ModConfigSpec.BooleanValue HEADLESS_CHICKEN = B
            .comment("A chicken host nobody rides runs in circles round where it was cut, never more than 12 blocks out (false: it stands still like any host).")
            .define("headlessChicken", true);

    static {
        B.pop().push("combat");
    }

    public static final ModConfigSpec.BooleanValue LANCE_UNSEATS_HOSTS = B
            .comment("A lance's unseating hit (a charged spear, or any weapon tagged minecraft:spears that knocks a rider off) takes a jar off its host body, Rover or Spider Walker (false: hosts hold, as mechas, sockets and the Passenger Pod do).")
            .define("lanceUnseatsHosts", true);

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
    public static final ModConfigSpec.BooleanValue LANYARDS = C
            .comment("Show the staff badge on heads and stations that work for someone (off: no badges are drawn for you).")
            .define("lanyards", true);
    public static final ModConfigSpec.BooleanValue GENE_QUIRKS = C
            .comment("Hear other players' and hosts' gene quirks: the moos, oinks, croaks, sneezes and sobs (off: only your own).")
            .define("geneQuirks", true);
    public static final ModConfigSpec.BooleanValue STAFF_CHALK = C
            .comment("Show the Clipboard's chalk outlines round marked stops and areas while you hold one.")
            .define("staffChalk", true);
    public static final ModConfigSpec.BooleanValue STAFF_NAMEPLATES = C
            .comment("Show a worker's given name over it when you look at it from close by (off: no names; their stop lines still show).")
            .define("staffNameplates", true);
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
