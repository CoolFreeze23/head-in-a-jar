package com.coolfreeze23.headinajar.genetics;

/**
 * Every number of the 0.6.2 genes in one place, so a balance pass is one file. Times are in ticks. Where a gene's
 * number is "scaled" it works at 70% in bodies and hosts and 100% in mecha ({@link GeneEffects#scale}); the ones
 * marked "unscaled" are thresholds that must be whole to work at all.
 */
public final class GeneNumbers {
    private GeneNumbers() {
    }

    // --- Livestock ---------------------------------------------------------------------------------------------------
    /** Ruminant Gut: food fills this much more (at least 1). */
    public static final float RUMINANT_FOOD = 0.25f;
    /** Ruminant Gut: a graze is 1 hunger, at most once every this many ticks (grass turns to dirt). */
    public static final int RUMINANT_GRAZE_TICKS = 60;
    /** Ruminant Gut: wheat eaten as an instant bite gives this much hunger. */
    public static final int RUMINANT_WHEAT_FOOD = 1;
    /** Ruminant Gut: a host standing on grass grazes back this much freshness (percent) every so many ticks: holds off rot. */
    public static final float RUMINANT_HOST_GRAZE = 2f;
    public static final int RUMINANT_HOST_TICKS = 1200;
    /** Stew Glands: a small flower is 1 hunger; its stew effect lasts this many times vanilla's (saturation flowers: 1x). */
    public static final int STEW_FLOWER_FOOD = 1;
    public static final int STEW_FLOWER_TIMES = 3;
    /** Stew Glands: one flower effect at a time: this many ticks between flowers. */
    public static final int STEW_FLOWER_COOLDOWN = 600;
    /** Stew Glands: an empty bowl in the air makes mushroom stew, once every this many ticks. */
    public static final int STEW_BOWL_COOLDOWN = 6000;
    /** Stew Glands' cost: mushroom and suspicious stew fill this share of their food. */
    public static final float STEW_STEW_SHARE = 0.5f;
    /** Swine Appetite: rotten flesh fills this much more. */
    public static final int SWINE_ROTTEN_EXTRA = 2;
    /** Fleece: grows back over this many ticks after shearing; shears give 1 to 3 wool. */
    public static final int FLEECE_REGROW = 6000;
    public static final int FLEECE_WOOL_MIN = 1, FLEECE_WOOL_MAX = 3;
    /**
     * Fleece's cost: fire burns this much harder while fleeced, and lasts this share longer (the burning-time
     * attribute: 8 s of fire becomes 10).
     */
    public static final float FLEECE_FIRE_HARDER = 1.25f;
    public static final double FLEECE_FIRE_LONGER = 0.25;
    /** Mountain Hooves: step height added and a safe fall this many blocks more (both unscaled: a full block, 7 blocks), off while sneaking. */
    public static final double HOOVES_STEP = 0.4;
    public static final double HOOVES_SAFE_FALL = 4;

    // --- Field Office ------------------------------------------------------------------------------------------------
    /** Bamboo Diet: bamboo and sugar cane are an instant bite of this much hunger; a cake slice fills this much more. */
    public static final int BAMBOO_FOOD = 1;
    public static final int BAMBOO_CAKE_EXTRA = 2;
    /** Snow Pounce (active): reach to the block looked at, splash radius, damage (scaled), knockback, Slowness IV time. */
    public static final double POUNCE_REACH = 8;
    public static final double POUNCE_SPLASH = 1.5;
    public static final float POUNCE_DAMAGE = 3;
    public static final double POUNCE_KNOCKBACK = 0.6;
    public static final int POUNCE_SLOW_TICKS = 20, POUNCE_SLOW_LEVEL = 3;
    /** Snow Pounce: cooldown, and its cost by day under open sky. */
    public static final int POUNCE_COOLDOWN = 80, POUNCE_COOLDOWN_DAY = 160;
    /** Pollinator: crops and saplings within this box get a growth tick as you pass, each at most once a minute. */
    public static final int POLLINATOR_RADIUS = 2, POLLINATOR_EVERY = 15, POLLINATOR_PLANT_TICKS = 1200;
    /** Pollinator: honey bottles fill this much more; its cost, this much slower in rain. */
    public static final int POLLINATOR_HONEY_EXTRA = 4;
    public static final double POLLINATOR_RAIN_SLOW = -0.2;
    /** Arctic Coat: Slowness lasts this share; swimming this much faster; in hot places Slowness I and this much more hunger. */
    public static final float ARCTIC_SLOW_SHARE = 0.5f;
    public static final double ARCTIC_SWIM = 0.3;
    public static final float ARCTIC_HOT_EXHAUSTION = 0.5f;
    /** Roll Up: sneak still this long to curl; damage taken while curled (60% less); a scute every so often. */
    public static final int ROLL_UP_STILL = 20;
    public static final float ROLL_UP_DAMAGE = 0.4f;
    public static final int ROLL_UP_SCUTE_TICKS = 24000;
    /** Roll Up's cost: hurt while standing still, you curl anyway for this long. */
    public static final int ROLL_UP_FLINCH = 40;
    /** Keen Nose: creatures within this many blocks leave a trail; brushable blocks within this many puff; checked every so often. */
    public static final int NOSE_CREATURES = 24, NOSE_BLOCKS = 12, NOSE_EVERY = 40;
    /** Keen Nose: sneak still this long on grass, moss or dirt: one time in this many, a seed; once per this many ticks. */
    public static final int NOSE_DIG_STILL = 100, NOSE_DIG_ODDS = 5, NOSE_DIG_COOLDOWN = 12000;
    /** Mimicry: a hostile out of sight within this many blocks is imitated, once every so often. */
    public static final int MIMICRY_RANGE = 32, MIMICRY_EVERY = 160;
    /** Mimicry's cost: cookies give Poison II for this long and this much damage. */
    public static final int MIMICRY_COOKIE_POISON = 200;
    public static final float MIMICRY_COOKIE_DAMAGE = 4;
    /** Tongue (active): reach, the biggest creature pulled (a chicken's width and height), cooldown. */
    public static final double TONGUE_REACH = 8;
    public static final double TONGUE_MAX_WIDTH = 0.4, TONGUE_MAX_HEIGHT = 0.7;
    public static final int TONGUE_COOLDOWN = 40;
    /** Tadpole Tail: swimming this much faster. */
    public static final double TADPOLE_SWIM = 0.3;
    /** Puffer: a hostile this near, or a melee hit, puffs you for this long; this much bigger (own modifier); poison on touch. */
    public static final double PUFFER_NEAR = 2;
    public static final int PUFFER_TICKS = 60, PUFFER_BETWEEN = 160, PUFFER_POISON = 80;
    public static final double PUFFER_SCALE = 0.3, PUFFER_SLOW = -0.3;

    // --- Logistics ---------------------------------------------------------------------------------------------------
    /** Spit Gland (active): reach, damage, how long a creature spat at loses interest, then how long it cannot be calmed. */
    public static final double SPIT_REACH = 12;
    public static final float SPIT_DAMAGE = 1;
    public static final int SPIT_CALM = 80, SPIT_IMMUNE = 240, SPIT_COOLDOWN = 40;
    /** Spit Gland + Venom Sac (Spitting Cobra): the spit poisons for this long. */
    public static final int SPIT_COBRA_POISON = 60;
    /** Spit Gland's cost "Personal Space": a player a block in front for this long is spat at, once per this many ticks. */
    public static final int SPIT_PERSONAL_TICKS = 100, SPIT_PERSONAL_EVERY = 1200;
    /** Pearl Digestion: pearls fly this much faster (half as far again is about x1.22), cooldown this share. */
    public static final double PEARL_SPEED = 1.22;
    public static final float PEARL_COOLDOWN_SHARE = 0.5f;
    /** Phase (active): dash this far through at most this many solid blocks; cooldown; a host's cost, a mecha's share of max health. */
    public static final double PHASE_REACH = 5;
    public static final int PHASE_WALL = 2, PHASE_COOLDOWN = 120;
    public static final float PHASE_HOST_COST = 2, PHASE_MECHA_SHARE = 0.03f;
    /** Levitation Bolt (active): reach, damage, Levitation trimmed to this long after the hit, cooldown, mecha heat share. */
    public static final double BOLT_REACH = 24;
    public static final float BOLT_DAMAGE = 4;
    public static final int BOLT_LEVITATION = 60, BOLT_COOLDOWN = 60;
    public static final float BOLT_HEAT_SHARE = 0.10f;
    /** Fetch: items matching the off hand within this many blocks drift to you; a note block played in the last 30 s wins. */
    public static final int FETCH_RANGE = 12, FETCH_EVERY = 8, FETCH_NOTE_TICKS = 600;
    public static final double FETCH_SPEED = 0.2;
    /** Fetch's cost: near a playing jukebox you dance for this long instead of attacking, once per this many ticks. */
    public static final int FETCH_DANCE = 60, FETCH_DANCE_EVERY = 1200;

    // --- Sales ---------------------------------------------------------------------------------------------------------
    /** Dusk Draught: invisible at dusk for this long; at night, the first targeting in this many ticks gives this long. */
    public static final int DUSK_INVISIBLE = 1200, DUSK_NIGHT_EVERY = 2400, DUSK_NIGHT_INVISIBLE = 160;

    // --- Night Shift -------------------------------------------------------------------------------------------------
    /** Sun-Baked: hits make players hungry for this long, creatures weak (Weakness I) for this long; 30 s in water, Slowness I a minute. */
    public static final int SUN_HUNGER = 100, SUN_WEAKNESS = 60, SUN_WATER = 600, SUN_WATER_SLOW = 1200;
    /** Rehabilitated: the cured gossip's reputation, scaled (125 at full); hit a villager and it is gone for this long. */
    public static final int REHAB_REPUTATION = 125, REHAB_FORGET = 24000;
    /** Rehabilitated's cost: a golden apple gives Nausea for this long. */
    public static final int REHAB_NAUSEA = 100;
    /** Gilded Rot: gold swords and axes do this much more to undead. */
    public static final float GILDED_DAMAGE = 3;
    /** Rampage: each melee hit within this long of the last adds 1, up to this many. */
    public static final int RAMPAGE_WINDOW = 60;
    public static final float RAMPAGE_MAX = 3, RAMPAGE_TAKEN = 1.15f;
    /** Bowstring Arms: a bow draws fully in 14 ticks instead of 20; arrows do 1 more at impact. */
    public static final int BOWSTRING_SKIP = 6;
    public static final float BOWSTRING_DAMAGE = 1;
    /** Rime Arrows: Slowness II for this long; the cost: fire burns this much harder. */
    public static final int RIME_SLOW = 100;
    public static final float RIME_FIRE_HARDER = 1.2f;
    /** Spore Arrows: Poison for this long; the cost: Poison on you lasts this much longer. */
    public static final int SPORE_POISON = 80;
    public static final float SPORE_POISON_LONGER = 1.5f;
    /** Undertow Stride: water movement efficiency 1 (unscaled: land speed on the seabed); the cost, swimming up half as fast. */
    public static final double UNDERTOW_EFFICIENCY = 1;
    public static final double UNDERTOW_RISE = 0.5;
    /** Snow Trail: snowballs do this much (this much to blazes) and slow for this long; you melt half a heart every so often. */
    public static final float SNOW_BALL = 2, SNOW_BALL_BLAZE = 3;
    public static final int SNOW_BALL_SLOW = 40, SNOW_MELT_EVERY = 60;
    public static final float SNOW_MELT = 1;

    // --- Accounts ----------------------------------------------------------------------------------------------------
    /** Gold Standard: golden carrots and apples give this much more; gold in sight within this many blocks slows you. */
    public static final float GOLD_FOOD = 1.5f;
    public static final int GOLD_SIGHT = 8, GOLD_EVERY = 10;
    public static final double GOLD_SLOW = -0.1;
    /** Brute Strength: axes do this much more. */
    public static final float BRUTE_AXE = 3;

    // --- Legal -------------------------------------------------------------------------------------------------------
    /** Breaking and Entering: axes do this much more. */
    public static final float BREAKING_AXE = 1;
    /** Steady Aim: crossbows load 40% faster; bolts fly this much faster (and so hit about a quarter harder); bows draw 20% slower. */
    public static final float STEADY_LOAD = 0.4f;
    public static final double STEADY_BOLT = 1.25;
    public static final float STEADY_BOW_SLOW = 0.2f;
    /** Fang Line (active): reach, fangs in the line, cooldown, mecha heat share. Damage is vanilla's 6 (hosts x0.7). */
    public static final double FANG_REACH = 12;
    public static final int FANG_COUNT = 8, FANG_COOLDOWN = 80;
    public static final float FANG_HEAT_SHARE = 0.15f;
    /** Fang Line's cost: blue sheep within this many blocks turn red. */
    public static final int FANG_SHEEP = 16;
    /** Potion Tolerance: potions you drink last this much longer; harmful ones splashed on you this share. */
    public static final float POTION_LONGER = 1.5f, POTION_SPLASH_SHARE = 0.5f;
    /** Bulldoze: knockback you give, and knockback on you. */
    public static final float BULLDOZE_KNOCK_OUT = 1.5f, BULLDOZE_KNOCK_IN = 0.67f;

    // --- Facilities --------------------------------------------------------------------------------------------------
    /** Weeping Glands: a Ghast Tear every this many ticks worn; a sob every few minutes; the cockpit fogs for this long. */
    public static final int WEEP_TEAR = 24000, WEEP_SOB = 4800, WEEP_FOG = 60;
    /** Stone Lungs: stone, deepslate and cobblestone mine this much faster; one in this many lets out a silverfish. */
    public static final float STONE_MINE = 1.2f;
    public static final int STONE_SILVERFISH_ODDS = 100;
}
