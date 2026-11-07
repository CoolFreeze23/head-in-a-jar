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
    WATER_JET(true);

    public final boolean powered;

    ModuleType(boolean powered) {
        this.powered = powered;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
