package com.coolfreeze23.headinajar.mecha;

import java.util.Locale;

/**
 * A mecha chassis: size, toughness, carrying capacity, battery, heat, how many modules of each kind it takes, and what it
 * moves with when that is built in (treads, spider legs, wings) instead of a Legs module.
 */
public enum MechaFrame {
    //          w     h     hp   armor load  dry  speed  battery     heatCap dissip  legs back core torso arm sensor
    HOPPER(1.2f, 2.5f, 60, 6, 350, 150, 0.13, 100_000, 400, 3, new int[]{1, 1, 1, 2, 2, 1}, Model.HOPPER, 1f, Locomotion.LEGS, 0, 0),
    BULWARK(1.6f, 3.1f, 140, 12, 800, 320, 0.11, 300_000, 800, 5, new int[]{1, 2, 1, 3, 2, 2}, Model.BULWARK, 1f, Locomotion.LEGS, 0, 0),
    /** grown more than built: three Gene Ports take serums (never rejected); Bio-Gel in its veins heals it */
    CHIMERA(1.6f, 2.8f, 120, 8, 700, 220, 0.12, 200_000, 700, 6, new int[]{1, 1, 1, 3, 2, 1}, Model.HOPPER, 1f, Locomotion.LEGS, 3, 0),
    /** a car: fast on built ground, slow off-road, no jumping, two passenger seats built in */
    WHEELHOUSE(2.2f, 1.5f, 90, 10, 1000, 300, 0.24, 300_000, 600, 4, new int[]{0, 2, 1, 2, 1, 1}, Model.WHEELHOUSE, 1f, Locomotion.TREADS, 0, 2),
    /** a quad spider: climbs walls natively, one mandible arm */
    SKITTER(2.0f, 1.6f, 100, 10, 600, 260, 0.12, 200_000, 600, 4, new int[]{0, 1, 1, 2, 1, 2}, Model.SKITTER, 1f, Locomotion.SPIDER, 0, 0),
    /** a heavy biped with two shoulder hardpoints; too big for a nether portal */
    COLOSSUS(2.4f, 4.4f, 300, 18, 1800, 700, 0.09, 1_000_000, 1600, 8, new int[]{1, 2, 2, 5, 4, 2}, Model.BULWARK, 1f, Locomotion.LEGS, 0, 0),
    /** an ornithopter: flapping wings built into the back */
    KITE(1.8f, 2.0f, 70, 6, 280, 120, 0.12, 250_000, 500, 6, new int[]{1, 0, 1, 1, 2, 1}, Model.KITE, 1f, Locomotion.WINGS, 0, 0),
    /** Colossus Mk II: four Gene Ports */
    EXECUTIVE(2.4f, 4.4f, 360, 20, 2000, 750, 0.10, 2_000_000, 2000, 10, new int[]{1, 2, 2, 5, 4, 2}, Model.BULWARK, 1f, Locomotion.LEGS, 4, 0);

    /** The code-defined model a frame is drawn with. */
    public enum Model { HOPPER, BULWARK, WHEELHOUSE, SKITTER, KITE }

    /** What moves the frame: a Legs module, or something built in. */
    public enum Locomotion { LEGS, TREADS, SPIDER, WINGS }

    /** Kite flight speed (blocks per tick). */
    public static final double FLIGHT_SPEED = 0.6;

    public final float width, height;
    public final int health, armor, load, dryMass;
    public final double speed;
    public final int battery, heatCap, dissipation;
    public final Model model;
    /** Drawn at this size against its code-defined model; animated frames (every frame with a rig) are drawn true size. */
    public final float renderScale;
    public final Locomotion locomotion;
    private final int genePorts, seats;
    private final int[] slots;

    MechaFrame(float width, float height, int health, int armor, int load, int dryMass, double speed, int battery, int heatCap,
               int dissipation, int[] slots, Model model, float renderScale, Locomotion locomotion, int genePorts, int seats) {
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
        this.slots = slots;
        this.model = model;
        this.renderScale = renderScale;
        this.locomotion = locomotion;
        this.genePorts = genePorts;
        this.seats = seats;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Serums the frame's own Gene Ports hold (a Bio-Integration Port module adds one more). */
    public int genePorts() {
        return genePorts;
    }

    /** Passenger seats built into the frame. */
    public int seats() {
        return seats;
    }

    /** Idle drain in FE/t, by size. */
    public float idleDrain() {
        return switch (this) {
            case HOPPER, KITE -> 2;
            case CHIMERA, SKITTER, WHEELHOUSE -> 3;
            case BULWARK -> 5;
            case COLOSSUS -> 8;
            case EXECUTIVE -> 10;
        };
    }

    /** Arm slots past the first two are shoulder hardpoints. */
    public boolean hasShoulders() {
        return slots(MechaModule.Slot.ARM) > 2;
    }

    public int slots(MechaModule.Slot slot) {
        return slots[slot.ordinal()];
    }

    /**
     * Drawn and animated by GeckoLib from its own model (geo/entity/mecha/&lt;id&gt;): sockets for modules, a seat for the
     * real jar, clips chosen from synced state ({@link MechaRig} holds what differs between them). The other frames keep
     * their code-defined model until each moves over.
     */
    public boolean animated() {
        return MechaRig.of(this) != null;
    }

    /** Total module slots, in the fixed order LEGS, BACK, CORE, TORSO, ARM (left first), SENSOR. */
    public int totalSlots() {
        int n = 0;
        for (int s : slots) n += s;
        return n;
    }

    /** The most module slots any frame has (the Gantry's size). */
    public static int maxSlots() {
        int n = 0;
        for (MechaFrame f : values()) n = Math.max(n, f.totalSlots());
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
