package com.coolfreeze23.headinajar.mecha;

import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * What one animated frame brings to the shared animated-mecha code, as data: its clips (name, length, loop), the walk's
 * design speeds (the playback divisors), the build's seat ticks, the bones its effects read, its lamp caps, its
 * locators, how far its drawing reaches past the hitbox, its signature verb on the drive key, and whether it has its
 * own sound set ({@code mecha.<frame>.<name>}, falling back to the shared {@code mecha.<name>}). A new frame is a new
 * entry here plus its model, sheet and clip file; the choosing, drawing and effects code is shared.
 */
public final class MechaRig {
    /** A clip of the model: its name, length in ticks, and whether it loops. */
    public record Clip(String name, int length, boolean loop, software.bernie.geckolib.animation.RawAnimation raw) {
        public static Clip of(String name, int length, boolean loop) {
            return new Clip(name, length, loop, loop ? software.bernie.geckolib.animation.RawAnimation.begin().thenLoop(name)
                    : software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold(name));
        }

        /** A tick inside the clip: wrapped for a loop, held just short of the end otherwise. */
        public double at(double tick) {
            if (loop) return ((tick % length) + length) % length;
            return Math.max(0, Math.min(length - 0.01, tick));
        }
    }

    /** What the drive key does in the cockpit. */
    public enum Verb { POGO, LIFT, SIEGE, JINGLE, RUSH, GRAFT, DRAGLINE, TALON }

    /** Where an effect locator rides: its bone and its place in the model (Blockbench pixels, the machine facing north). */
    public record Locator(String bone, Vec3 px) {
    }

    public final String id;
    public final Verb verb;
    /** Blocks a tick the walk and the limp are authored at: measured speed over these is the playback rate. */
    public final double walkDesign, limpDesign;
    /** The build's seat tick for each flat slot (the module clunks home then). */
    public final int[] seatTicks;
    /** Bones whose world matrices the effects read, and the lamp caps drawn again through the glow mask. */
    public final String[] tracked;
    public final Set<String> lamps;
    /** How far the drawing reaches past the hitbox: sideways and upward (blocks). */
    public final double cullSide, cullUp;
    /** Getting out the ordinary way: the sounds of the boarding clip run backwards (clip tick, sound, pitch). */
    public final Object[][] unboardSounds;
    /** This frame has its own sound set ({@code mecha.<id>.<name>}). */
    public final boolean ownSounds;
    private final Map<String, Clip> clips = new HashMap<>();
    public final Map<String, Locator> locators = new HashMap<>();
    /** Whose own sounds a name is looked up in first, in order ({@code mecha.<set>.<name>}), before the shared set. */
    public String[] soundSets;
    /**
     * A bone carrying the frame's own hat (the Chimera's hard hat on its strut), and how many of its first cubes are not
     * the hat (the strut): the hat is not drawn while the jar wears the Hat Mount's helmet, which sits where it would.
     */
    public String hatBone;
    /**
     * Where the third-person camera turns about: this share of the frame's height up its centre line; NaN keeps the
     * pilot's eye (the Hopper's framing, the reference).
     */
    public double pivot = 2 / 3.0;
    public int hatKeeps;
    /** Ticks the walk is blended in over from standing: longer where the walk carries the hips above the rest pose. */
    public int walkBlend = 4;
    /**
     * The pilot's first-person eye, blocks above the jar's base (where the pilot's feet are): the eyes of the head in the
     * jar are 0.35 above it. NaN: the player's own eye height (1.62 above the jar's base), as the Hopper, the Bulwark and the
     * Wheelhouse keep. Only the first-person camera moves (client); aim and reach stay at the player's eye.
     */
    public double firstPersonEye = Double.NaN;

    private MechaRig(String id, Verb verb, double walkDesign, double limpDesign, int[] seatTicks, String[] tracked, Set<String> lamps,
                     double cullSide, double cullUp, Object[][] unboardSounds, boolean ownSounds, String clipTable, Object[][] locators) {
        this.id = id;
        this.verb = verb;
        this.walkDesign = walkDesign;
        this.limpDesign = limpDesign;
        this.seatTicks = seatTicks;
        this.tracked = tracked;
        this.lamps = lamps;
        this.cullSide = cullSide;
        this.cullUp = cullUp;
        this.unboardSounds = unboardSounds;
        this.ownSounds = ownSounds;
        this.soundSets = ownSounds ? new String[]{id} : new String[0];
        // "name length[L]" pairs: L marks a loop
        for (String entry : clipTable.trim().split("\\s*,\\s*")) {
            String[] p = entry.split(" ");
            boolean loop = p[1].endsWith("L");
            String name = p[0];
            int length = Integer.parseInt(loop ? p[1].substring(0, p[1].length() - 1) : p[1]);
            clips.put(name, Clip.of(name, length, loop));
        }
        for (Object[] l : locators) this.locators.put((String) l[0], new Locator((String) l[1], new Vec3((double) l[2], (double) l[3], (double) l[4])));
    }

    /** A clip by name; a name the frame lacks is a programming error. */
    public Clip clip(String name) {
        Clip c = clips.get(name);
        if (c == null) throw new IllegalArgumentException(id + " has no clip " + name);
        return c;
    }

    public boolean has(String clip) {
        return clips.containsKey(clip);
    }

    /** The clip each role plays on this frame: its walk, and for a lifting frame its grab, hold, carrying walk and throw. */
    private final Map<String, String> roles = new HashMap<>();

    private MechaRig roles(String... pairs) {
        for (int i = 0; i < pairs.length; i += 2) roles.put(pairs[i], pairs[i + 1]);
        return this;
    }

    private Clip role(String role) {
        return clip(roles.getOrDefault(role, role));
    }

    /** The walk clip (carrying a load or not). */
    public Clip walk(boolean carrying) {
        return role(carrying ? "carry_walk" : "walk");
    }

    public Clip grab() {
        return role("grab");
    }

    public Clip throwing() {
        return role("throw");
    }

    public Clip hold() {
        return role("hold");
    }

    /** A wheeled rig's shared motion (null for a legged frame): {@link Wheels}. */
    public Wheels.Spec wheels;
    /** The pilot's look on the frame: the bone that takes its yaw and the limit, the bone that takes its pitch and its range. */
    public String lookYawBone = "sensor", lookPitchBone = "sensor";
    public float lookYawLimit = 60, lookPitchMin = -30, lookPitchMax = 25;
    /** Where passengers sit (model pixels, x left -, z front -), in boarding order; none: behind the cockpit. */
    public double[][] seats = new double[0][];

    private MechaRig wheels(Wheels.Spec spec) {
        this.wheels = spec;
        return this;
    }

    private MechaRig look(String yawBone, float yawLimit, String pitchBone, float pitchMin, float pitchMax) {
        lookYawBone = yawBone;
        lookYawLimit = yawLimit;
        lookPitchBone = pitchBone;
        lookPitchMin = pitchMin;
        lookPitchMax = pitchMax;
        return this;
    }

    private MechaRig seats(double[]... places) {
        seats = places;
        return this;
    }

    /** A multi-leg rig's legs (null for a biped or a wheeled frame): {@link Legs}. */
    public Legs.Spec legs;
    /** It walks up walls: the model is turned onto the wall it climbs ({@link Legs.Climb}). */
    public boolean climbs;
    /** Clip sounds this frame has no take of, by the shared or another frame's event each borrows ({@code bulwark.grip_clack}). */
    public final Map<String, String> borrowed = new HashMap<>();

    /** Blocks a tick its backing gait and its climb were authored at (0: none). */
    public double backDesign, climbDesign;

    private MechaRig gaits(double back, double climb) {
        backDesign = back;
        climbDesign = climb;
        return this;
    }

    private MechaRig legs(Legs.Spec spec, boolean climbs) {
        this.legs = spec;
        this.climbs = climbs;
        return this;
    }

    private MechaRig borrow(String... pairs) {
        for (int i = 0; i < pairs.length; i += 2) borrowed.put(pairs[i], pairs[i + 1]);
        return this;
    }

    /** The clip a role plays on this frame (a wheeled frame's "walk" is its drive, its "boot_legs" the engine's start). */
    public String name(String role) {
        return roles.getOrDefault(role, role);
    }

    /** The eyes of the head in a wide jar, blocks above the jar's base (its neck 0.095 up, the eyes 4 px above that). */
    public static final double JAR_EYES = 0.35;

    /** The walk phase wraps at a whole number of every walk's, drive's and limp's cycles (12 to 32 ticks, 14, 20, 22 and 26). */
    public static final double PHASE_WRAP = 1441440;

    public static final MechaRig HOPPER = new MechaRig("hopper", Verb.POGO, 0.2808, 0.0842,
            new int[]{65, 76, 87, 98, 109, 120, 131, 142},
            new String[]{"foot_l", "foot_r", "vent_l", "vent_r", "restraint", "jar_anchor", "socket_arm_l", "socket_arm_r", "torso"},
            Set.of("lamp_core", "lamp_l", "lamp_r", "lamp_sensor"), 0.75, 0.9,
            new Object[][]{{1, "bar_unlatch", 1f}, {6, "servo", 1.1f}, {18, "bar_latch", 1f}}, false,
            "parked 80L, board 22, boot 40, boot_legs 40, shutdown 20, idle 60L, walk 12L, limp 24L, limp_l 12L, limp_r 12L, turn_l 12L, turn_r 12L, jump 10,"
                    + " fall 16L, land 12, land_heavy 20, pogo_crouch 30, pogo_hold 10L, pogo_leap 12, pogo_land 24, vent 100, hurt 8, eject 30,"
                    + " destroyed 40, build 200, idle_tic 20, dry_fire_l 18, dry_fire_r 18",
            new Object[][]{
                    {"fx_step_l", "foot_l", -7.0, -6.0, -2.0}, {"fx_step_r", "foot_r", 7.0, -6.0, -2.0},
                    {"fx_vent_l", "vent_l", -14.0, 22.0, 4.0}, {"fx_vent_r", "vent_r", 14.0, 22.0, 4.0},
                    {"fx_latch", "restraint", 0.0, 41.7, -8.5}, {"fx_jar_exit", "torso", 0.0, 44.0, 0.0},
                    {"fx_hull", "torso", 0.0, 28.5, 0.0}, {"fx_core", "torso", 0.0, 23.0, -13.0},
                    {"fx_wrist_l", "socket_arm_l", -12.5, 13.0, -2.0}, {"fx_wrist_r", "socket_arm_r", 12.5, 13.0, -2.0}});

    /**
     * The heavy biped: the heel-and-toe walk ({@code walk_c}) and the overhead carry. Its clips were authored at 0.2376
     * blocks a tick (walk) and 0.0713 (limp). It is drawn up to 2.6 blocks over its hitbox: a load held overhead.
     */
    public static final MechaRig BULWARK = new MechaRig("bulwark", Verb.LIFT, 0.2376, 0.0713,
            new int[]{65, 73, 80, 87, 94, 101, 108, 115, 122, 129, 136},
            new String[]{"foot_l", "foot_r", "vent_l", "vent_r", "clamp_l", "jar_anchor", "socket_arm_l", "socket_arm_r", "torso", "sensor"},
            Set.of("lamp_core", "lamp_tail"), 1.3, 2.6,
            new Object[][]{{1, "clamp_release", 1f}, {6, "servo", 1f}, {18, "clamp_close", 1f}}, true,
            "parked 80L, board 22, boot 40, boot_legs 40, shutdown 24, idle 60L, idle_tic 40, walk_c 16L, limp 32L, limp_l 16L, limp_r 16L, turn_l 16L,"
                    + " turn_r 16L, jump 12, fall 16L, land 16, land_heavy 24, grab_high 24, hold_high 40L, carry_walk_high 16L, throw_high 19,"
                    + " grab_refused 26, vent 100, hurt 10, eject 30, destroyed 40, build 200, dry_fire_l 18, dry_fire_r 18",
            new Object[][]{
                    {"fx_step_l", "foot_l", -10.0, -9.0, -1.0}, {"fx_step_r", "foot_r", 10.0, -9.0, -1.0},
                    {"fx_vent_l", "vent_l", -17.0, 27.0, 5.5}, {"fx_vent_r", "vent_r", 17.0, 27.0, 5.5},
                    {"fx_jar_exit", "torso", 0.0, 54.0, 0.0}, {"fx_hull", "torso", 0.0, 38.5, 0.0},
                    {"fx_core", "sensor", 0.0, 23.0, -18.5}, {"fx_lamp_tail", "torso", 0.0, 32.5, 13.0},
                    {"fx_jet_l", "torso", -5.0, 22.0, 13.0}, {"fx_jet_r", "torso", 5.0, 22.0, 13.0},
                    {"fx_wrist_l", "socket_arm_l", -17.5, 21.0, -5.0}, {"fx_wrist_r", "socket_arm_r", 17.5, 21.0, -5.0}})
            .roles("walk", "walk_c", "grab", "grab_high", "hold", "hold_high", "carry_walk", "carry_walk_high", "throw", "throw_high");

    /**
     * The gatehouse: two towers on long legs, the jar in the slot between them; Siege on the drive key. Its clips were
     * authored at 0.1944 blocks a tick (walk) and 0.0583 (limp). It borrows the Bulwark's sounds where it has none of its own.
     */
    public static final MechaRig COLOSSUS = new MechaRig("colossus", Verb.SIEGE, 0.1944, 0.0583,
            new int[]{127, 135, 141, 147, 153, 159, 165, 171, 177, 183, 189, 195, 201, 207, 213, 219},
            new String[]{"foot_l", "foot_r", "vent_l", "vent_r", "clamp_l", "jar_anchor", "socket_arm_l", "socket_arm_r", "torso", "sensor",
                    "shin_l", "shin_r", "socket_arm_2", "socket_arm_3"},
            Set.of("lamp_core", "lamp_tail"), 1.4, 1.8,
            new Object[][]{{1, "clamp_release", 1f}, {6, "servo", 1f}, {18, "clamp_close", 1f}}, true,
            "parked 80L, board 22, boot 40, boot_legs 40, shutdown 24, idle 60L, idle_tic 48, walk_c 26L, limp 32L, limp_l 26L, limp_r 26L, turn_l 16L, turn_r 16L,"
                    + " jump 12, fall 16L, land 16, land_heavy 24, dry_fire_l 18, dry_fire_r 18, vent 100, hurt 10, eject 30, destroyed 40,"
                    + " build 320, siege_in 40, siege 60L, siege_out 40, siege_refused 26",
            new Object[][]{
                    {"fx_step_l", "foot_l", -14.0, -7.0, -1.0}, {"fx_step_r", "foot_r", 14.0, -7.0, -1.0},
                    {"fx_spade_l", "shin_l", -14.0, 17.0, -13.0}, {"fx_spade_r", "shin_r", 14.0, 17.0, -13.0},
                    {"fx_vent_l", "vent_l", -14.5, 66.0, 8.0}, {"fx_vent_r", "vent_r", 14.5, 66.0, 8.0},
                    {"fx_jar_exit", "torso", 0.0, 73.7, 0.0}, {"fx_hull", "torso", 0.0, 57.7, 0.0},
                    {"fx_core", "sensor", 0.0, 46.5, -18.5}, {"fx_lamp_tail", "torso", 0.0, 58.0, 6.5},
                    {"fx_jet_l", "torso", -7.0, 49.0, 10.0}, {"fx_jet_r", "torso", 7.0, 49.0, 10.0},
                    {"fx_wrist_l", "socket_arm_l", -23.0, 46.0, -5.0}, {"fx_wrist_r", "socket_arm_r", 23.0, 46.0, -5.0},
                    {"fx_shoulder_l", "socket_arm_2", -14.5, 83.0, -3.0}, {"fx_shoulder_r", "socket_arm_3", 14.5, 83.0, -3.0}})
            .roles("walk", "walk_c");

    /**
     * The board's company car: a dark suit on narrow legs, the jar under a canopy with a sunroof, four Gene Port vials on
     * the shoulders; the premium jingle on the drive key. Clips authored at 0.216 blocks a tick (walk) and 0.0648 (limp).
     */
    public static final MechaRig EXECUTIVE = new MechaRig("executive", Verb.JINGLE, 0.216, 0.0648,
            new int[]{47, 55, 60, 65, 70, 77, 82, 87, 92, 97, 104, 109, 114, 119, 126, 131},
            new String[]{"foot_l", "foot_r", "vent_l", "vent_r", "clamp_l", "jar_anchor", "socket_arm_l", "socket_arm_r", "torso", "sensor",
                    "gene_port_0", "gene_port_1", "gene_port_2", "gene_port_3"},
            Set.of("lamp_core", "lamp_tail"), 1.2, 1.8,
            new Object[][]{{1, "clamp_release", 1f}, {6, "servo", 1f}, {18, "clamp_close", 1f}}, true,
            "parked 80L, board 22, boot 42, boot_legs 40, shutdown 24, idle 60L, idle_tic 30, walk_c 22L, limp 32L, limp_l 22L, limp_r 22L, turn_l 16L, turn_r 16L,"
                    + " jump 12, fall 16L, land 16, land_heavy 24, dry_fire_l 18, dry_fire_r 18, vent 100, hurt 10, eject 30, destroyed 40,"
                    + " build 240, drive_jingle 30",
            new Object[][]{
                    {"fx_step_l", "foot_l", -10.0, -4.0, -1.0}, {"fx_step_r", "foot_r", 10.0, -4.0, -1.0},
                    {"fx_vent_l", "vent_l", -7.0, 53.0, 10.0}, {"fx_vent_r", "vent_r", 7.0, 53.0, 10.0},
                    {"fx_jar_exit", "torso", 0.0, 79.7, 0.0}, {"fx_hull", "torso", 0.0, 57.7, 0.0},
                    {"fx_core", "sensor", 0.0, 50.5, -12.0}, {"fx_lamp_tail", "torso", 0.0, 78.0, 13.0},
                    {"fx_jet_l", "torso", -6.0, 46.0, 9.0}, {"fx_jet_r", "torso", 6.0, 46.0, 9.0},
                    {"fx_wrist_l", "socket_arm_l", -21.0, 41.0, -4.0}, {"fx_wrist_r", "socket_arm_r", 21.0, 41.0, -4.0},
                    {"fx_gene_0", "gene_port_0", -23.5, 72.0, 0.0}, {"fx_gene_1", "gene_port_1", -18.5, 72.0, 0.0},
                    {"fx_gene_2", "gene_port_2", 18.5, 72.0, 0.0}, {"fx_gene_3", "gene_port_3", 23.5, 72.0, 0.0}})
            .roles("walk", "walk_c");

    static {
        COLOSSUS.soundSets = new String[]{"colossus", "bulwark"};
        EXECUTIVE.soundSets = new String[]{"executive", "bulwark"};
        // walk_c carries the hips 4 px (Colossus) and 2 px (Executive) over the rest pose: gathered over 10 ticks, not popped
        COLOSSUS.roles("walk", "walk_c");
        EXECUTIVE.roles("walk", "walk_c");
        COLOSSUS.walkBlend = 10;
        // the pilot is the head in the jar: in first person it looks out from the jar's eyes (between the towers; under
        // the Executive's valance), not from a standing player's eye height a block above the lid
        COLOSSUS.firstPersonEye = JAR_EYES;
        EXECUTIVE.firstPersonEye = JAR_EYES;
        EXECUTIVE.walkBlend = 10;
    }

    /**
     * The site tug, the first frame on the wheeled skeleton: four big wheels (7 px tyres: 130.96 degrees a block rolled),
     * four-wheel steering (fronts to 30, the rear a third of it the other way), Rush Hour on the drive key. Its drive was
     * authored at 0.5184 blocks a tick (built ground), reverse at 0.2074, Rush Hour at 0.8294, the limp at 0.1555. The
     * turret takes the look's yaw (to 100 degrees), the tool boom on the bumper its pitch. Two staff sit on the bench.
     */
    public static final MechaRig WHEELHOUSE = new MechaRig("wheelhouse", Verb.RUSH, 0.5184, 0.1555,
            new int[]{65, 73, 81, 89, 97, 105, 113},
            new String[]{"strut_fl", "strut_fr", "strut_rl", "strut_rr", "exhaust_l", "exhaust_r", "vent_l", "vent_r", "buffer",
                    "jar_anchor", "socket_arm_l", "body", "lamp_core", "lamp_head_fl", "lamp_head_fr", "lamp_tail"},
            Set.of("lamp_core", "lamp_head_fl", "lamp_head_fr", "lamp_tail", "lamp_brake"), 0.4, 2.1,
            new Object[][]{{1, "catch_release", 1f}, {6, "servo", 1f}, {18, "ring_latch", 1f}}, true,
            "parked 80L, board 22, boot 40, boot_engine 40, shutdown 20, idle 40L, idle_tic 40, drive_start 12, drive 20L, reverse 20L,"
                    + " brake 16, steer_l 7, steer_r 7, turn_l 12L, turn_r 12L, drift_l 8L, drift_r 8L, rush_windup 12, rush 10L, rush_ram 14,"
                    + " rush_end 24, rush_refused 34, bump 12, land 16, land_heavy 24, spin_l 8, spin_loop_l 10L, spin_end_l 14, recoil_l 20,"
                    + " sustain_l 8, sustain_loop_l 40L, sustain_end_l 14, strike_l 18, guard_l 10, guard_loop_l 20L, guard_hit_l 8,"
                    + " guard_end_l 12, dry_fire_l 18, hurt 10, limp 32L, vent 100, eject 30, destroyed 40, build 200",
            new Object[][]{
                    {"fx_contact_fl", "strut_fl", -14.5, 0.0, -13.0}, {"fx_contact_fr", "strut_fr", 14.5, 0.0, -13.0},
                    {"fx_contact_rl", "strut_rl", -14.5, 0.0, 11.0}, {"fx_contact_rr", "strut_rr", 14.5, 0.0, 11.0},
                    {"fx_head_l", "lamp_head_fl", -15.0, 20.0, -21.5}, {"fx_head_r", "lamp_head_fr", 15.0, 20.0, -21.5},
                    {"fx_tail_l", "lamp_tail", -15.0, 20.0, 19.5}, {"fx_tail_r", "lamp_tail", 15.0, 20.0, 19.5},
                    {"fx_exhaust_l", "exhaust_l", -9.0, 7.0, 22.5}, {"fx_exhaust_r", "exhaust_r", 9.0, 7.0, 22.5},
                    {"fx_vent_l", "vent_l", -12.5, 12.0, -1.5}, {"fx_vent_r", "vent_r", 12.5, 12.0, -1.5},
                    {"fx_buffer", "buffer", 0.0, 7.5, -25.5}, {"fx_wrist_l", "socket_arm_l", -14.0, 11.0, -31.0},
                    {"fx_jar_exit", "body", 0.0, 40.0, 9.5}, {"fx_hull", "body", 0.0, 24.5, 9.5}, {"fx_core", "lamp_core", 0.0, 23.0, 20.5},
                    {"fx_seat_l", "body", -5.0, 18.0, -2.5}, {"fx_seat_r", "body", 5.0, 18.0, -2.5}})
            .roles("walk", "drive", "boot_legs", "boot_engine")
            .wheels(new Wheels.Spec(new double[]{-14.5, 14.5, -14.5, 14.5}, new double[]{-13, -13, 11, 11}, 130.96, 150,
                    0.5184, 0.2074, 0.1555, 0.8294, 30, 0.3f, 4, 1.5f, 4, 1, 2, null, "drive_start", 12, 16, 12))
            .look("sensor", 100, "arm_l", -10, 30)
            .seats(new double[]{-5, 18, -2.5}, new double[]{5, 18, -2.5});

    /**
     * The lab result that was issued a hard hat: a specimen cage on the light-biped skeleton, a growth through its bars,
     * an eye on a stalk, three gill vials (its Gene Ports), a hard hat over the jar's lid; the Graft on the drive key. Its
     * walk was authored at 0.259 blocks a tick (7 ticks a step) and its limp at 0.0778. The idle tic twitches either
     * forearm ({@code idle_tic_r} is the left one's mirror). It has no Pogo: its drive key is the Graft's.
     */
    public static final MechaRig CHIMERA = new MechaRig("chimera", Verb.GRAFT, 0.259, 0.0778,
            new int[]{107, 115, 122, 129, 136, 143, 150, 157, 164},
            new String[]{"foot_l", "foot_r", "vent_l", "vent_r", "restraint", "jar_anchor", "socket_arm_l", "socket_arm_r", "torso", "sensor",
                    "growth", "gene_port_0", "gene_port_1", "gene_port_2", "shin_l", "shin_r"},
            Set.of("lamp_core", "lamp_l", "lamp_r", "lamp_sensor"), 0.8, 0.7,
            new Object[][]{{1, "bar_unlatch", 1f}, {6, "servo", 1.1f}, {18, "bar_latch", 1f}}, true,
            "parked 80L, board 22, boot 42, boot_legs 40, shutdown 20, idle 60L, idle_tic 30, idle_tic_r 30, walk 14L, limp 24L, limp_l 14L, limp_r 14L, turn_l 12L,"
                    + " turn_r 12L, jump 10, fall 16L, land 12, land_heavy 20, vent 100, hurt 8, eject 30, destroyed 40, build 220,"
                    + " dry_fire_l 18, dry_fire_r 18, graft_l 40, graft_r 40, graft_refused 26",
            new Object[][]{
                    {"fx_step_l", "foot_l", -8.0, -8.0, -2.0}, {"fx_step_r", "foot_r", 8.0, -8.0, -2.0},
                    {"fx_knee_l", "shin_l", -8.0, 7.0, 2.5}, {"fx_knee_r", "shin_r", 8.0, 7.0, 2.5},
                    {"fx_vent_l", "vent_l", -15.0, 25.0, -1.0}, {"fx_vent_r", "vent_r", 15.0, 25.0, -1.0},
                    {"fx_latch", "restraint", 0.0, 50.72, 0.0}, {"fx_jar_exit", "torso", 0.0, 50.72, 0.0},
                    {"fx_hull", "torso", 0.0, 32.72, 0.0}, {"fx_core", "torso", 0.0, 29.0, -11.0},
                    {"fx_graft", "growth", 0.0, 25.0, -12.0},
                    {"fx_gene_0", "gene_port_0", -5.5, 32.0, 12.5}, {"fx_gene_1", "gene_port_1", 0.0, 32.0, 12.5},
                    {"fx_gene_2", "gene_port_2", 5.5, 32.0, 12.5},
                    {"fx_wrist_l", "socket_arm_l", -14.0, 13.0, -2.0}, {"fx_wrist_r", "socket_arm_r", 14.0, 13.0, -2.0}});

    static {
        // the Hopper's camera turns about its pilot's eye, as it always has
        HOPPER.pivot = Double.NaN;
        // the hard hat is the restraint's cubes after its strut
        CHIMERA.hatBone = "restraint";
        CHIMERA.hatKeeps = 1;
        // the pilot sees from the eyes of the head in the jar
        CHIMERA.firstPersonEye = JAR_EYES;
    }

    /**
     * Facilities' steeplejack, the first frame on the multi-leg skeleton: four legs at the pod's corners (hips at +-9.5
     * px, femur 12, tibia 20, the body's pivot 13 up), solved for flat ground by its art script and again in code on
     * uneven ground; it walks up walls (the model turned onto the wall about its near foot line). The jar rides on top
     * in a four-post cage. One mandible arm, the line gun's Dragline on the drive key. Its trot was authored at 0.2592
     * blocks a tick, backing at 0.1037, the climb at 0.2 (the climbing rate), the limp at 0.0778. No sound set of its
     * own: each clip sound borrows the nearest take.
     */
    public static final MechaRig SKITTER = new MechaRig("skitter", Verb.DRAGLINE, 0.2592, 0.0778,
            new int[]{65, 73, 81, 89, 97, 105, 113},
            new String[]{"root", "body", "shin_fl", "shin_fr", "shin_rl", "shin_rr", "vent_l", "vent_r", "restraint", "jar_anchor",
                    "socket_arm_l", "forearm_l", "sensor", "lamp_core", "lamp_tail", "launcher"},
            Set.of("lamp_core", "lamp_tail"), 1.3, 1.4,
            new Object[][]{{1, "clamp_release", 1f}, {4, "cage_lift", 1f}, {18, "cage_drop", 1f}}, false,
            "parked 80L, board 22, boot 40, boot_legs 40, shutdown 24, idle 60L, idle_tic 40, walk 8L, walk_back 20L, turn_l 12L,"
                    + " turn_r 12L, climb 8L, cling_on 8, cling 40L, jump 12, fall 16L, land 14, land_heavy 20, dragline_fire 18,"
                    + " dragline_reel 10L, dragline_swing 30L, dragline_rappel 16L, dragline_cut 16, dragline_refused 30, spin_l 8,"
                    + " spin_loop_l 10L, spin_end_l 14, recoil_l 20, sustain_l 8, sustain_loop_l 40L, sustain_end_l 14, strike_l 18,"
                    + " guard_l 10, guard_loop_l 20L, guard_hit_l 8, guard_end_l 12, dry_fire_l 18, hurt 10, limp 16L, vent 100,"
                    + " eject 30, destroyed 40, build 200",
            new Object[][]{
                    {"fx_foot_fl", "shin_fl", -9.5, 13.0, -41.5}, {"fx_foot_fr", "shin_fr", 9.5, 13.0, -41.5},
                    {"fx_foot_rl", "shin_rl", -9.5, 13.0, -22.5}, {"fx_foot_rr", "shin_rr", 9.5, 13.0, -22.5},
                    {"fx_vent_l", "vent_l", -12.0, 12.0, 3.5}, {"fx_vent_r", "vent_r", 12.0, 12.0, 3.5},
                    {"fx_tail_l", "lamp_tail", -7.0, 13.0, 18.5}, {"fx_tail_r", "lamp_tail", 7.0, 13.0, 18.5},
                    {"fx_line", "launcher", 11.5, 19.5, -18.5}, {"fx_anchor", "body", 0.0, 18.0, 14.0},
                    {"fx_jar_exit", "body", 0.0, 36.0, 0.0}, {"fx_hull", "body", 0.0, 20.5, 0.0}, {"fx_core", "lamp_core", 0.0, 13.5, -16.0},
                    {"fx_eye_l", "lamp_core", -2.5, 13.5, -16.0}, {"fx_eye_r", "lamp_core", 2.5, 13.5, -16.0},
                    {"fx_wrist_l", "socket_arm_l", 0.0, 9.5, -25.0}, {"fx_jaw", "forearm_l", 0.0, 9.5, -28.5}})
            .legs(new Legs.Spec(new String[]{"fl", "fr", "rl", "rr"}, new double[]{-9.5, 9.5, -9.5, 9.5}, new double[]{-9.5, -9.5, 9.5, 9.5},
                    13, 12, 20, 8, 18), true)

            .gaits(0.1037, 0.2)
            .look("sensor", 70, "sensor", -30, 25)
            .borrow("clamp_release", "bulwark.clamp_release", "cage_lift", "bar_unlatch", "cage_drop", "bar_latch", "cage_blow", "bar_blow",
                    "cage_clank", "bar_clank", "clamp_clack", "bulwark.grip_clack", "jaw_snap", "bulwark.grip_clack", "reel_tick", "relay_tick",
                    "belly_settle", "servo_settle", "leg_tick", "servo_tick", "foot_tap", "tap", "leg_curl", "servo_sag",
                    "line_charge", "skitter.line_charge", "reel_payout", "skitter.reel", "reel_wind", "skitter.reel", "reel_freewheel", "skitter.reel",
                    "reel_brake", "servo_catch", "line_creak", "skitter.line_creak", "line_cough", "skitter.line_cough",
                    "line_fire", "skitter.line_fire", "line_cut", "skitter.line_cut");

    /**
     * The ornithopter on bird legs: the jar slung under the body in a lantern whose floor is a lift. Its clips were
     * authored at 0.2592 blocks a tick (the walk) and 0.0778 (the limp). Drawn with the wings spread about 4.8 blocks
     * wide and, at the top of the beat, 3.8 tall. In the air its clips are chosen by flight state (takeoff, flap,
     * hover, glide, flare, stall, fall) and the drive key works the Talon Lift.
     */
    public static final MechaRig KITE = new MechaRig("kite", Verb.TALON, 0.2592, 0.0778,
            // the build clip's own seat keys (build:seat:<slot>), as every frame's are
            new int[]{65, 73, 81, 89, 97, 105},
            new String[]{"foot_l", "foot_r", "restraint", "jar_anchor", "socket_arm_l", "socket_arm_r", "torso", "sensor", "hips",
                    "wing_l", "wing_r", "wingtip_l", "wingtip_r", "tail"},
            Set.of("lamp_core", "lamp_nav_l", "lamp_nav_r"), 1.5, 1.8,
            new Object[][]{{1, "floor_release", 1f}, {7, "cable_winch", 1f}, {20, "floor_latch", 1f}}, true,
            "parked 80L, board 22, boot 40, boot_legs 30, shutdown 20, idle 60L, idle_tic 36, walk 16L, turn_l 16L, turn_r 16L,"
                    + " takeoff 16, flap 16L, hover 12L, glide 40L, bank_l 8, bank_r 8, flare 14, land 18, land_heavy 22, stall 34,"
                    + " stall_loop 16L, fall 16L, talon_grab 16, talon_carry 20L, talon_drop 12, talon_refused 30, hurt 10, limp 24L, limp_l 16L, limp_r 16L,"
                    + " vent 100, eject 30, destroyed 40, build 200, dry_fire_l 18, dry_fire_r 18",
            new Object[][]{
                    {"fx_step_l", "foot_l", -12.5, -3.0, -2.0}, {"fx_step_r", "foot_r", 12.5, -3.0, -2.0},
                    {"fx_tip_l", "wingtip_l", -36.5, 32.0, 0.0}, {"fx_tip_r", "wingtip_r", 36.5, 32.0, 0.0},
                    {"fx_wing_l", "wing_l", -15.5, 31.0, 3.0}, {"fx_wing_r", "wing_r", 15.5, 31.0, 3.0},
                    {"fx_core", "sensor", 0.0, 31.0, -17.0}, {"fx_tail", "tail", 0.0, 28.0, 18.5},
                    {"fx_jar_exit", "torso", 0.0, 9.0, 16.0}, {"fx_hull", "torso", 0.0, 9.0, 0.0}, {"fx_cargo", "hips", 0.0, -6.0, -3.0},
                    {"fx_wrist_l", "socket_arm_l", -16.0, 16.0, -3.0}, {"fx_wrist_r", "socket_arm_r", 16.0, 16.0, -3.0}})
            .eyeInside("torso", "hips", "sensor", "lamp_core", "wing_l", "wing_r", "tail", "restraint");

    static {
        // clip sounds with no take of their own: the Chimera's boot ends on the shared "ready" ding, the Kite's talons
        // carrying a load strain the Bulwark's hydraulics
        CHIMERA.borrow("boot_chime", "ding");
        KITE.borrow("hydraulic_strain", "bulwark.hydraulic_strain");
    }

    /** Bones whose own cubes the pilot's first-person eye sits inside (not drawn for that eye). */
    public Set<String> eyeInside = Set.of();

    private MechaRig eyeInside(String... bones) {
        eyeInside = Set.of(bones);
        return this;
    }

    /** Whether this frame flies on its own wings (its clips are chosen by flight state in the air). */
    public boolean flies() {
        return verb == Verb.TALON;
    }

    /** The rig of an animated frame, or null for a frame still drawn by its code-defined model. */
    public static MechaRig of(MechaFrame frame) {
        return switch (frame) {
            case HOPPER -> HOPPER;
            case BULWARK -> BULWARK;
            case COLOSSUS -> COLOSSUS;
            case EXECUTIVE -> EXECUTIVE;
            case WHEELHOUSE -> WHEELHOUSE;
            case CHIMERA -> CHIMERA;
            case SKITTER -> SKITTER;
            case KITE -> KITE;
            default -> null;
        };
    }
}
