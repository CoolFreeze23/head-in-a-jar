package com.coolfreeze23.headinajar.mecha;

import java.util.Locale;

/**
 * A mecha module: which slot it fits, its tier, mass (kg), standing power drain (FE/t) and heat (HU/t), and what its
 * one tuning slider (0-100%, default 50) trades. Active effects live in {@link MechaLogic}.
 */
public enum MechaModule {
    // legs
    HYDRAULIC_LEGS(Slot.LEGS, 2, 60, 0, 0, "stride"),
    SPRING_HEELS(Slot.LEGS, 2, 20, 0, 0, "tension"),
    ANCHOR_CLAMPS(Slot.LEGS, 2, 30, 0, 0, null),
    AQUA_FINS(Slot.LEGS, 2, 15, 0, 0, null),
    SPRINT_SERVOS(Slot.LEGS, 2, 25, 0, 0, "boost"),
    STILTS(Slot.LEGS, 2, 35, 0, 0, null),
    ARACHNID_LEGS(Slot.LEGS, 3, 90, 0, 0, null),
    HOVER_SKIRT(Slot.LEGS, 3, 50, 30, 2, "altitude"),
    LAVA_TREADS(Slot.LEGS, 3, 40, 0, 0, null),
    // back
    JUMP_JETS(Slot.BACK, 2, 45, 0, 0, "thrust"),
    ROCKET_PACK(Slot.BACK, 3, 110, 0, 0, "thrust"),
    GLIDER_WINGS(Slot.BACK, 2, 40, 0, 0, null),
    CARGO_HOLD(Slot.BACK, 2, 80, 0, 0, null),
    PASSENGER_POD(Slot.BACK, 2, 120, 0, 0, null),
    RADIATOR_FINS(Slot.BACK, 2, 30, 0, 0, null),
    BATTERY_RACK(Slot.BACK, 2, 70, 0, 0, null),
    ENDER_SATCHEL(Slot.BACK, 3, 15, 0, 0, null),
    SMOKE_MORTAR(Slot.BACK, 3, 35, 0, 0, null),
    MISSILE_POD(Slot.BACK, 4, 90, 0, 0, "warhead"),
    // core
    GALVANIC_STACK(Slot.CORE, 2, 25, 0, 0, null),
    COMBUSTION_KERNEL(Slot.CORE, 2, 60, 0, 0, null),
    BIO_REACTOR_CORE(Slot.CORE, 2, 50, 0, 0, null),
    CAPACITOR_BANK(Slot.CORE, 3, 50, 0, 0, null),
    FUSION_BOTTLE(Slot.CORE, 4, 140, 0, 0, "output"),
    // torso
    ARMOR_PLATING(Slot.TORSO, 2, 60, 0, 0, "thickness"),
    THORN_PLATING(Slot.TORSO, 2, 50, 0, 0, null),
    FIRE_SUPPRESSION(Slot.TORSO, 2, 25, 0, 0, null),
    LIFE_SUPPORT_RECYCLER(Slot.TORSO, 2, 20, 10, 0, null),
    SIGNAL_BOOSTER(Slot.TORSO, 2, 15, 20, 0, null),
    SOLAR_SKIN(Slot.TORSO, 2, 20, 0, 0, null),
    COOLANT_LOOP(Slot.TORSO, 2, 45, 0, 0, null),
    REACTIVE_ARMOUR(Slot.TORSO, 3, 70, 0, 0, null),
    ENERGY_SHIELD(Slot.TORSO, 3, 40, 40, 4, "strength"),
    MEDICAL_INJECTOR(Slot.TORSO, 3, 30, 0, 0, "threshold"),
    STEALTH_FIELD(Slot.TORSO, 4, 30, 200, 8, null),
    TESLA_COIL(Slot.TORSO, 3, 60, 0, 0, "voltage"),
    WORKBENCH(Slot.TORSO, 2, 10, 0, 0, null),
    BIO_INTEGRATION_PORT(Slot.TORSO, 3, 40, 0, 0, null),
    // arms
    DRILL(Slot.ARM, 2, 70, 0, 0, "torque"),
    CHAINSAW(Slot.ARM, 2, 60, 0, 0, null),
    HYDRAULIC_FIST(Slot.ARM, 2, 90, 0, 0, "force"),
    GRAPPLE_HOOK(Slot.ARM, 2, 30, 0, 0, null),
    TRACTOR_BEAM(Slot.ARM, 3, 50, 0, 0, null),
    RIVET_GUN(Slot.ARM, 2, 45, 0, 0, null),
    RIOT_SHIELD(Slot.ARM, 2, 80, 0, 0, null),
    MANIPULATOR_HAND(Slot.ARM, 2, 30, 0, 0, null),
    EXCAVATOR_SCOOP(Slot.ARM, 2, 80, 0, 0, null),
    FLAMETHROWER(Slot.ARM, 3, 50, 0, 0, "spread"),
    ARC_CANNON(Slot.ARM, 3, 85, 0, 0, "charge"),
    HEMO_SYRINGE(Slot.ARM, 2, 25, 0, 0, null),
    HARVESTER(Slot.ARM, 2, 10, 0, 0, null),
    WELDING_TORCH(Slot.ARM, 3, 30, 0, 0, null),
    PLASMA_LANCE(Slot.ARM, 4, 120, 0, 0, null),
    // sensors
    NIGHT_OPTICS(Slot.SENSOR, 2, 5, 2, 0, null),
    BIO_SCANNER(Slot.SENSOR, 2, 5, 1, 0, null),
    THERMAL_IMAGING(Slot.SENSOR, 3, 8, 10, 1, null),
    ORE_SCANNER(Slot.SENSOR, 3, 12, 0, 0, "radius"),
    TARGET_LOCK(Slot.SENSOR, 2, 5, 2, 0, null);

    /** Modules the pilot fires with the utility key (R; G picks which). */
    public static final java.util.List<MechaModule> UTILITIES = java.util.List.of(ENDER_SATCHEL, SMOKE_MORTAR, MISSILE_POD, WORKBENCH, ORE_SCANNER);

    public enum Slot { LEGS, BACK, CORE, TORSO, ARM, SENSOR }

    public final Slot slot;
    public final int tier, mass, idlePower;
    public final float idleHeat;
    /** Translation key suffix of the slider, or null for none. */
    public final String slider;

    MechaModule(Slot slot, int tier, int mass, int idlePower, float idleHeat, String slider) {
        this.slot = slot;
        this.tier = tier;
        this.mass = mass;
        this.idlePower = idlePower;
        this.idleHeat = idleHeat;
        this.slider = slider;
    }

    public String id() {
        return "mecha_" + name().toLowerCase(Locale.ROOT);
    }
}
