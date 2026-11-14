package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Which of its own clips a fitted module plays, at which tick, and whether its working lights are on, chosen every
 * frame from what every client already has: the arm buttons (the synced arm byte), the battery, heat and vent, the hit
 * time, the module's last one-off action ({@link MechaEntity#actAt}), the machine's motion as this client measures it
 * ({@link HopperView}), and whether anyone is aboard. Never a one-shot trigger a late arrival would miss. Pure
 * arithmetic: the drawing is the client's ({@code client/ModuleLayer}).
 * <p>
 * A machine drawn as parked, or beyond {@link #NEAR} blocks, shows every module at rest: its {@code idle} clip where it
 * has one (a folded shield, a closed hatch, a flame out), else its model as built.
 */
public final class ModuleMotion {
    /** Module motion is drawn within this many blocks of the camera; farther, modules hold their rest pose. */
    public static final double NEAR = 32;
    /** The grapple's line runs out at this many blocks a tick and reels in at {@link #REEL}. */
    public static final double LINE_OUT = 4, REEL = 3;
    /** Ticks into the grapple's deploy clip when the hook leaves the launcher (the clip hides it from then on). */
    public static final double HOOK_OFF = 3;

    /** A clip at a tick (null: the rest pose), and whether the module's working lights ({@code _firing}) are on. */
    public record Pose(String clip, double tick, boolean lit) {
        public static final Pose BUILT = new Pose(null, 0, false);
    }

    /** What this client remembers per machine: arm presses, each slot's on/off moments, the grapple's line. */
    public static final class State {
        final double[] lastPress = new double[8], releaseAt = new double[8];
        final boolean[] held = new boolean[8];
        final boolean[] seen = new boolean[MechaFrame.maxSlots()], on = new boolean[MechaFrame.maxSlots()];
        final double[] flipAt = new double[MechaFrame.maxSlots()];
        int passengers = -1;
        double podAt = -1e9;
        /** The grapple per arm: where the line is cast to (null: nothing in reach) and how far. */
        public final Vec3[] grappleTo = new Vec3[8];
        final double[] grappleDistance = new double[8];

        State() {
            java.util.Arrays.fill(lastPress, -1e9);
            java.util.Arrays.fill(releaseAt, -1e9);
        }
    }

    private ModuleMotion() {
    }

    /** The rest pose: the module's idle clip where it has one (a flame out, a shield folded), else as built. */
    public static Pose rest(MechaModule module) {
        Pose p = REST[module.ordinal()];
        if (p == null) REST[module.ordinal()] = p = ModuleArt.clips(module).containsKey("idle") ? new Pose("idle", 0, false) : Pose.BUILT;
        return p;
    }

    private static final Pose[] REST = new Pose[MechaModule.values().length];

    private static Pose at(String clip, double tick, boolean lit) {
        return new Pose(clip, tick, lit);
    }

    private static double len(MechaModule module, String clip) {
        ModuleArt.Clip c = ModuleArt.clips(module).get(clip);
        return c == null ? 0 : c.length();
    }

    /**
     * The pose of the module in flat slot {@code slot} at {@code now} (game time plus partial tick). {@code still}: the
     * machine is drawn as parked or beyond {@link #NEAR} blocks.
     */
    public static Pose pose(MechaEntity m, int slot, MechaModule module, double now, boolean still) {
        if (module == null || !ModuleArt.has(module)) return Pose.BUILT;
        if (still || m.isDeadOrDying()) return rest(module);
        State s = m.view.modules;
        MechaFrame frame = m.frame();
        boolean piloted = m.getFirstPassenger() instanceof Player;
        boolean powered = piloted && m.battery() > 0;
        HopperView v = m.view;
        return switch (module.slot) {
            case ARM -> arm(m, s, slot - frame.firstIndex(MechaModule.Slot.ARM), module, now);
            case LEGS -> legs(m, s, slot, module, now, piloted, powered, v);
            case BACK -> back(m, s, slot, module, now, powered, v);
            case CORE -> powered ? at("sustain", now, false) : rest(module);
            case TORSO -> torso(m, s, slot, module, now, piloted, powered);
            case SENSOR -> sensor(m, s, slot, module, now, powered);
        };
    }

    // --- arms ---------------------------------------------------------------------------------------------------

    /** The arm button's state for this side, kept per machine: the last press (never forgotten) and the release. */
    private static boolean held(MechaEntity m, State s, int side, double now) {
        boolean held = (m.armsShown() & (1 << side)) != 0 && m.getFirstPassenger() instanceof Player;
        if (held && !s.held[side]) {
            s.lastPress[side] = now;
            if (m.arm(side) == MechaModule.GRAPPLE_HOOK) cast(m, s, side);
        }
        if (!held && s.held[side]) s.releaseAt[side] = now;
        s.held[side] = held;
        return held;
    }

    private static Pose arm(MechaEntity m, State s, int side, MechaModule module, double now) {
        boolean held = held(m, s, side, now);
        double t = now - s.lastPress[side];
        return switch (module) {
            // the drill's bit is spun by the renderer from its working speed, coasting down after
            case DRILL -> rest(module);
            case CHAINSAW, HARVESTER -> held ? at("spin", t, false) : rest(module);
            case TRACTOR_BEAM -> held ? at("sustain", t, true) : rest(module);
            case FLAMETHROWER, WELDING_TORCH -> held ? at("sustain", t, true) : rest(module);
            case RIVET_GUN -> repeat(module, "recoil", s, side, held, now);
            case EXCAVATOR_SCOOP -> repeat(module, "strike", s, side, held, now);
            case HYDRAULIC_FIST, HEMO_SYRINGE -> {
                // the ram (the needle) goes out two ticks into its clip: started so it lands on the arm's contact
                double into = t - (ArmAim.STRIKE_CONTACT - 2);
                yield t >= 0 && into < len(module, "strike") ? at("strike", Math.max(0, into), false) : rest(module);
            }
            case ARC_CANNON -> {
                // the rails charge (lit, breathing) from the press; the kick a tick before the arm's shot; then the rails
                // breathe again while it is held
                double into = t - (ArmAim.RECOIL_CONTACT - 1), kick = len(module, "recoil");
                if (t >= 0 && into < 0) yield at("sustain", t, true);
                if (into >= 0 && into < kick) yield at("recoil", into, true);
                yield held ? at("sustain", into - kick, true) : rest(module);
            }
            case PLASMA_LANCE -> {
                double thrust = len(module, "strike");
                if (!held) yield t < thrust && now - s.releaseAt[side] < thrust ? at("strike", t, true) : rest(module);
                yield t < thrust ? at("strike", t, true) : at("sustain", t - thrust, true);
            }
            case MANIPULATOR_HAND -> {
                double grip = len(module, "strike");
                if (t < grip) yield at("strike", t, false);
                yield held ? at("guard", t - grip, false) : rest(module);
            }
            case RIOT_SHIELD -> {
                // up while held (it swings up to face front, then turns across), folded again on release
                double up = len(module, "deploy");
                if (held) yield t < up ? at("deploy", t, false) : at("guard", t - up, false);
                double since = now - s.releaseAt[side];
                yield since < up && s.releaseAt[side] > s.lastPress[side] ? at("deploy", up - since, false) : rest(module);
            }
            case GRAPPLE_HOOK -> grapple(s, side, module, now);
            default -> rest(module);
        };
    }

    /** A short clip repeated while the button is held; the round in progress finishes after it is let go. */
    private static Pose repeat(MechaModule module, String clip, State s, int side, boolean held, double now) {
        double length = len(module, clip);
        double start = s.lastPress[side];
        if (length <= 0 || start < -1e8) return rest(module);
        double t = now - start;
        if (held) return at(clip, t % length, false);
        double round = start + Math.floor(Math.max(0, s.releaseAt[side] - start) / length) * length;
        double into = now - round;
        return into >= 0 && into < length ? at(clip, into, false) : rest(module);
    }

    /** Where the grapple's line is cast to, worked out on this client from the pilot's look at the press. */
    private static void cast(MechaEntity m, State s, int side) {
        s.grappleTo[side] = null;
        if (!(m.getFirstPassenger() instanceof Player pilot)) return;
        Entity target = MechaLogic.lookEntity(pilot, m, 32);
        Vec3 to = null;
        if (target != null) to = target.getBoundingBox().getCenter();
        else {
            var hit = MechaLogic.lookBlock(pilot, 32);
            if (hit.getType() != HitResult.Type.MISS) to = hit.getLocation();
        }
        s.grappleTo[side] = to;
        s.grappleDistance[side] = to == null ? 0 : to.distanceTo(pilot.getEyePosition());
    }

    /**
     * The grapple: the deploy clip runs the hook out of the launcher and hides it ({@link #HOOK_OFF}); the line and the
     * flying hook are drawn by the client from then, out to the target and back; the retract clip brings it home.
     * With nothing in reach the hook comes straight back.
     */
    public static Pose grapple(State s, int side, MechaModule module, double now) {
        double t = now - s.lastPress[side];
        if (t < 0 || s.lastPress[side] < -1e8) return rest(module);
        if (t < HOOK_OFF) return at("deploy", t, false);
        double flight = line(s, side);
        if (t < HOOK_OFF + flight) return at("deploy", len(module, "deploy") - 0.01, false);
        double back = t - HOOK_OFF - flight;
        return back < len(module, "retract") ? at("retract", back, false) : rest(module);
    }

    /** Ticks the grapple's line is out: run out, two ticks on the target, reeled in. Zero with nothing in reach. */
    static double line(State s, int side) {
        if (s.grappleTo[side] == null) return 0;
        double d = s.grappleDistance[side];
        return Math.max(1, d / LINE_OUT) + 2 + Math.max(2, d / REEL);
    }

    /**
     * Where the flying hook is, as a share of the way from the launcher to the target (0 at the launcher, 1 on the
     * target), or -1 while the line is not out.
     */
    public static double hookOut(MechaEntity m, int side, double now) {
        State s = m.view.modules;
        if (s.grappleTo[side] == null) return -1;
        double t = now - s.lastPress[side] - HOOK_OFF;
        double d = s.grappleDistance[side], out = Math.max(1, d / LINE_OUT), reel = Math.max(2, d / REEL);
        if (t < 0 || t >= out + 2 + reel) return -1;
        if (t < out) return t / out;
        if (t < out + 2) return 1;
        return 1 - (t - out - 2) / reel;
    }

    // --- the rest -----------------------------------------------------------------------------------------------

    /**
     * A module that comes out while {@code on} and goes away again: {@code deploy} from the moment it turned on (then
     * {@code loop}, or the deploy's last frame), {@code retract} from the moment it turned off (or the deploy run
     * backwards), then rest. A machine first seen with it already on shows it out, without the deploy.
     */
    private static Pose toggle(MechaModule module, State s, int slot, boolean on, double now, String loop, double loopTick, boolean lit) {
        if (!s.seen[slot]) {
            s.seen[slot] = true;
            s.on[slot] = on;
            s.flipAt[slot] = -1e9;
        } else if (s.on[slot] != on) {
            s.on[slot] = on;
            s.flipAt[slot] = now;
        }
        double t = now - s.flipAt[slot];
        double deploy = len(module, "deploy");
        if (on) {
            if (t < deploy) return at("deploy", t, lit);
            if (loop != null) return at(loop, loopTick, lit);
            return deploy > 0 ? at("deploy", deploy - 0.01, lit) : rest(module);
        }
        double retract = len(module, "retract");
        if (retract > 0 && t < retract) return at("retract", t, false);
        if (retract <= 0 && t < deploy) return at("deploy", deploy - t, false);
        return rest(module);
    }

    /** A one-off clip from a moment: playing within its length, else rest. */
    private static Pose once(MechaModule module, String clip, double t) {
        return t >= 0 && t < len(module, clip) ? at(clip, t, false) : rest(module);
    }

    /** Ticks since this module's last one-off action, or a large number. */
    private static double sinceAct(MechaEntity m, MechaModule module, double now) {
        long act = m.actAt();
        if (act < 0 || (act & 63) != module.ordinal()) return 1e9;
        return now - (act >> 6);
    }

    /** Out for {@code hold} ticks after the action (deploy, held open, retract). */
    private static Pose opened(MechaModule module, double t, double hold) {
        double deploy = len(module, "deploy"), retract = len(module, "retract");
        if (t < 0 || t > hold + Math.max(retract, deploy)) return rest(module);
        if (t < deploy) return at("deploy", t, false);
        if (t < hold) return at("deploy", deploy - 0.01, false);
        double back = t - hold;
        if (retract > 0) return back < retract ? at("retract", back, false) : rest(module);
        return back < deploy ? at("deploy", deploy - back, false) : rest(module);
    }

    /** The frame's step length in ticks (half its walk cycle), for dressings that move once a step. */
    private static double step(MechaEntity m) {
        MechaRig rig = MechaRig.of(m.frame());
        return rig == null ? 12 : rig.walk(m.heldId() >= 0).length() / 2.0;
    }

    private static boolean walking(HopperView v) {
        return v.speed > HopperView.WALKING;
    }

    private static Pose legs(MechaEntity m, State s, int slot, MechaModule module, double now, boolean piloted, boolean powered, HopperView v) {
        double phase = v.walkPhase();
        return switch (module) {
            // once a step, the ram strokes and the damper rides its rod
            case HYDRAULIC_LEGS -> walking(v) ? at("pump", (phase % step(m)) / step(m) * len(module, "pump"), false) : rest(module);
            case SPRING_HEELS -> once(module, "burst", now - v.jumpAt);
            case ANCHOR_CLAMPS -> toggle(module, s, slot, piloted && m.getFirstPassenger().isShiftKeyDown(), now, null, 0, false);
            case AQUA_FINS -> piloted ? at("idle", now * (m.isInWater() ? 2 : 1), false) : rest(module);
            case SPRINT_SERVOS, LAVA_TREADS -> walking(v) ? at("spin", phase, false) : rest(module);
            case STILTS -> toggle(module, s, slot, piloted, now, null, 0, false);
            case ARACHNID_LEGS -> toggle(module, s, slot, piloted, now, walking(v) || m.horizontalCollision ? "sustain" : null,
                    (phase % step(m)) / step(m) * len(module, "sustain"), false);
            case HOVER_SKIRT -> toggle(module, s, slot, powered, now, "spin", now, powered);
            default -> rest(module);
        };
    }

    private static Pose back(MechaEntity m, State s, int slot, MechaModule module, double now, boolean powered, HopperView v) {
        boolean rising = !m.onGround() && m.getY() > m.yo + 0.01;
        return switch (module) {
            // the jets' pods and pilot lamps are worked by the renderer from the burst event
            case JUMP_JETS -> rest(module);
            case ROCKET_PACK -> {
                double t = now - v.jumpAt;
                if (t >= 0 && t < len(module, "burst")) yield at("burst", t, false);
                yield rising && v.jumpAt >= 0 ? at("sustain", now, false) : rest(module);
            }
            case GLIDER_WINGS -> toggle(module, s, slot, v.airTicks > 6 && m.getY() < m.yo - 0.01, now, null, 0, false);
            case CARGO_HOLD -> opened(module, sinceAct(m, module, now), 30);
            case ENDER_SATCHEL -> opened(module, sinceAct(m, module, now), 40);
            case PASSENGER_POD -> {
                // the canopy swings up when someone climbs in or out, and shuts again
                int count = m.getPassengers().size();
                if (s.passengers >= 0 && count != s.passengers) s.podAt = now;
                s.passengers = count;
                yield opened(module, now - s.podAt, 20);
            }
            case RADIATOR_FINS -> toggle(module, s, slot, m.vent() > 0 || m.heat() > m.frame().heatCap * 0.6f, now, "sustain",
                    now, false);
            case BATTERY_RACK -> once(module, "deploy", now - m.boardedAt() - HopperAnimation.RELAY_FROM);
            case SMOKE_MORTAR -> once(module, "recoil", sinceAct(m, module, now));
            case MISSILE_POD -> {
                // the hatch flips open, the salvo kicks, it stays open a moment and shuts
                double t = sinceAct(m, module, now), deploy = len(module, "deploy"), kick = len(module, "recoil");
                if (t < 0 || t > 46) yield rest(module);
                if (t < deploy) yield at("deploy", t, false);
                if (t < deploy + kick) yield at("recoil", t - deploy, false);
                if (t < 40) yield at("deploy", deploy - 0.01, false);
                yield at("deploy", Math.max(0, deploy - (t - 40)), false);
            }
            default -> rest(module);
        };
    }

    private static Pose torso(MechaEntity m, State s, int slot, MechaModule module, double now, boolean piloted, boolean powered) {
        long hit = m.hitAt();
        double sinceHit = hit < 0 ? 1e9 : now - hit / 2;
        return switch (module) {
            case ARMOR_PLATING, THORN_PLATING -> once(module, "hit", sinceHit);
            // a tile is blown off, and stays off a while before the next one is shown in its place
            case REACTIVE_ARMOUR -> sinceHit >= 0 && sinceHit < 40 ? at("hit", Math.min(sinceHit, len(module, "hit") - 0.01), false) : rest(module);
            case ENERGY_SHIELD, TESLA_COIL -> powered ? at("sustain", now, true) : rest(module);
            case SIGNAL_BOOSTER, LIFE_SUPPORT_RECYCLER, COOLANT_LOOP, STEALTH_FIELD, BIO_INTEGRATION_PORT -> powered ? at("sustain", now, false) : rest(module);
            case SOLAR_SKIN -> m.level().isDay() && m.level().canSeeSky(m.blockPosition().above((int) m.getBbHeight())) ? at("sustain", now, false) : rest(module);
            case FIRE_SUPPRESSION, MEDICAL_INJECTOR -> once(module, "burst", sinceAct(m, module, now));
            case WORKBENCH -> opened(module, sinceAct(m, module, now), 40);
            default -> rest(module);
        };
    }

    private static Pose sensor(MechaEntity m, State s, int slot, MechaModule module, double now, boolean powered) {
        return switch (module) {
            case NIGHT_OPTICS, THERMAL_IMAGING -> toggle(module, s, slot, powered, now, null, 0, false);
            case BIO_SCANNER, TARGET_LOCK -> powered ? at("sustain", now, false) : rest(module);
            // the scan runs for five seconds after the utility key fires it
            case ORE_SCANNER -> sinceAct(m, module, now) < 100 ? at("spin", now, false) : rest(module);
            default -> rest(module);
        };
    }

    /** The clips each module may be asked for here (for the tests: every one must be in the module's model). */
    public static final Map<MechaModule, String[]> ASKED = Map.ofEntries(
            Map.entry(MechaModule.CHAINSAW, new String[]{"spin"}), Map.entry(MechaModule.HARVESTER, new String[]{"spin"}),
            Map.entry(MechaModule.TRACTOR_BEAM, new String[]{"sustain"}), Map.entry(MechaModule.FLAMETHROWER, new String[]{"sustain", "idle"}),
            Map.entry(MechaModule.WELDING_TORCH, new String[]{"sustain", "idle"}), Map.entry(MechaModule.RIVET_GUN, new String[]{"recoil"}),
            Map.entry(MechaModule.EXCAVATOR_SCOOP, new String[]{"strike"}), Map.entry(MechaModule.HYDRAULIC_FIST, new String[]{"strike"}),
            Map.entry(MechaModule.HEMO_SYRINGE, new String[]{"strike"}), Map.entry(MechaModule.ARC_CANNON, new String[]{"recoil", "sustain"}),
            Map.entry(MechaModule.PLASMA_LANCE, new String[]{"strike", "sustain"}), Map.entry(MechaModule.MANIPULATOR_HAND, new String[]{"strike", "guard"}),
            Map.entry(MechaModule.RIOT_SHIELD, new String[]{"deploy", "guard", "idle"}), Map.entry(MechaModule.GRAPPLE_HOOK, new String[]{"deploy", "retract"}),
            Map.entry(MechaModule.HYDRAULIC_LEGS, new String[]{"pump"}), Map.entry(MechaModule.SPRING_HEELS, new String[]{"burst"}),
            Map.entry(MechaModule.ANCHOR_CLAMPS, new String[]{"deploy", "retract"}), Map.entry(MechaModule.AQUA_FINS, new String[]{"idle"}),
            Map.entry(MechaModule.SPRINT_SERVOS, new String[]{"spin"}), Map.entry(MechaModule.LAVA_TREADS, new String[]{"spin"}),
            Map.entry(MechaModule.STILTS, new String[]{"deploy", "retract"}), Map.entry(MechaModule.ARACHNID_LEGS, new String[]{"deploy", "sustain"}),
            Map.entry(MechaModule.HOVER_SKIRT, new String[]{"deploy", "spin"}), Map.entry(MechaModule.ROCKET_PACK, new String[]{"burst", "sustain"}),
            Map.entry(MechaModule.GLIDER_WINGS, new String[]{"deploy", "retract"}), Map.entry(MechaModule.CARGO_HOLD, new String[]{"deploy", "retract"}),
            Map.entry(MechaModule.ENDER_SATCHEL, new String[]{"deploy", "retract"}), Map.entry(MechaModule.WORKBENCH, new String[]{"deploy", "retract"}),
            Map.entry(MechaModule.PASSENGER_POD, new String[]{"deploy", "retract"}), Map.entry(MechaModule.RADIATOR_FINS, new String[]{"deploy", "sustain"}),
            Map.entry(MechaModule.BATTERY_RACK, new String[]{"deploy"}), Map.entry(MechaModule.SMOKE_MORTAR, new String[]{"recoil"}),
            Map.entry(MechaModule.MISSILE_POD, new String[]{"deploy", "recoil", "idle"}), Map.entry(MechaModule.ARMOR_PLATING, new String[]{"hit"}),
            Map.entry(MechaModule.THORN_PLATING, new String[]{"hit"}), Map.entry(MechaModule.REACTIVE_ARMOUR, new String[]{"hit"}),
            Map.entry(MechaModule.ENERGY_SHIELD, new String[]{"sustain"}), Map.entry(MechaModule.TESLA_COIL, new String[]{"sustain", "idle"}),
            Map.entry(MechaModule.SIGNAL_BOOSTER, new String[]{"sustain", "idle"}), Map.entry(MechaModule.LIFE_SUPPORT_RECYCLER, new String[]{"sustain"}),
            Map.entry(MechaModule.COOLANT_LOOP, new String[]{"sustain"}), Map.entry(MechaModule.STEALTH_FIELD, new String[]{"sustain"}),
            Map.entry(MechaModule.BIO_INTEGRATION_PORT, new String[]{"sustain"}), Map.entry(MechaModule.SOLAR_SKIN, new String[]{"sustain", "idle"}),
            Map.entry(MechaModule.FIRE_SUPPRESSION, new String[]{"burst"}), Map.entry(MechaModule.MEDICAL_INJECTOR, new String[]{"burst"}),
            Map.entry(MechaModule.NIGHT_OPTICS, new String[]{"deploy", "idle"}), Map.entry(MechaModule.THERMAL_IMAGING, new String[]{"deploy", "idle"}),
            Map.entry(MechaModule.BIO_SCANNER, new String[]{"sustain"}), Map.entry(MechaModule.TARGET_LOCK, new String[]{"sustain"}),
            Map.entry(MechaModule.ORE_SCANNER, new String[]{"spin"}), Map.entry(MechaModule.GALVANIC_STACK, new String[]{"sustain"}),
            Map.entry(MechaModule.COMBUSTION_KERNEL, new String[]{"sustain"}), Map.entry(MechaModule.BIO_REACTOR_CORE, new String[]{"sustain"}),
            Map.entry(MechaModule.CAPACITOR_BANK, new String[]{"sustain"}), Map.entry(MechaModule.FUSION_BOTTLE, new String[]{"sustain"}));
}
