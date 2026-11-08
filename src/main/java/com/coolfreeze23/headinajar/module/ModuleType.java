package com.coolfreeze23.headinajar.module;

import java.util.Locale;

/** What a jar module does. Powered modules go offline when the jar's charge runs out. */
public enum ModuleType {
    HOP_SPRINGS(true),
    PROPELLER(true),
    MANIPULATOR_ARM(true),
    TELEKINESIS(true),
    DRILL_BIT(false),
    ARMOR_PLATING(false),
    HEAT_SHIELD(true),
    NIGHT_LENS(false),
    MAGNET(false),
    WATER_JET(true),
    SUCTION_CUPS(true),
    SPIDER_LEGS(true),
    SPEAKER_GRILLE(false),
    SALINE_RECYCLER(true),
    SHOCK_LID(true),
    SELF_RIGHTING_GYRO(false),
    HUB_ANTENNA(true),
    LIFE_SIGN_MONITOR(false),
    HAT_MOUNT(false),
    CORTEX_COPROCESSOR(true),
    LACRIMAL_LASER(true),
    BODY_RECALL_BEACON(true);

    public final boolean powered;

    ModuleType(boolean powered) {
        this.powered = powered;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
