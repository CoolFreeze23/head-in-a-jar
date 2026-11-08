package com.coolfreeze23.headinajar.mecha;

import java.util.Locale;

/** A mecha chassis: size, toughness, carrying capacity, battery, heat, and how many modules of each kind it takes. */
public enum MechaFrame {
    //       w     h     hp   armor load dry  speed  battery     heatCap dissip legs back core torso arm sensor
    HOPPER(1.2f, 2.5f, 60, 6, 350, 150, 0.13, 400_000, 400, 3, 1, 1, 1, 2, 2, 1),
    BULWARK(1.6f, 3.1f, 140, 12, 800, 320, 0.11, 1_200_000, 800, 5, 1, 2, 1, 3, 2, 2);

    public final float width, height;
    public final int health, armor, load, dryMass;
    public final double speed;
    public final int battery, heatCap, dissipation;
    private final int[] slots;

    MechaFrame(float width, float height, int health, int armor, int load, int dryMass, double speed, int battery, int heatCap,
               int dissipation, int legs, int back, int core, int torso, int arms, int sensors) {
        this.width = width;
        this.height = height;
        this.health = health;
        this.armor = armor;
        this.load = load;
        this.dryMass = dryMass;
        this.speed = speed;
        this.battery = battery;
        this.heatCap = heatCap;
        this.dissipation = dissipation;
        this.slots = new int[]{legs, back, core, torso, arms, sensors};
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int slots(MechaModule.Slot slot) {
        return slots[slot.ordinal()];
    }

    /** Total module slots, in the fixed order LEGS, BACK, CORE, TORSO, ARM (left first), SENSOR. */
    public int totalSlots() {
        int n = 0;
        for (int s : slots) n += s;
        return n;
    }

    /** The slot kind at a flat index. */
    public MechaModule.Slot slotAt(int index) {
        for (MechaModule.Slot slot : MechaModule.Slot.values()) {
            if (index < slots(slot)) return slot;
            index -= slots(slot);
        }
        return null;
    }

    /** First flat index of a slot kind. */
    public int firstIndex(MechaModule.Slot slot) {
        int n = 0;
        for (MechaModule.Slot s : MechaModule.Slot.values()) {
            if (s == slot) return n;
            n += slots(s);
        }
        return -1;
    }
}
