package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.anim.SeekingController;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which clip an animated mecha (the Hopper, the Bulwark) plays, and at which tick, chosen every frame from the mecha's
 * synced state alone (and what this client measures of its motion): never a one-shot trigger that a player arriving
 * later would miss. The clips' names and lengths are the frame's ({@link MechaRig}). Six controllers, in the model's
 * order: {@code main} (the whole machine), {@code cockpit} (board and boot over it), {@code unboard}, {@code arm_l} and
 * {@code arm_r} (the arm verbs), {@code hit} (the flinch). Later ones win on the bones they key.
 */
public final class HopperAnimation {
    /** A clip at a tick, entered with a blend of so many ticks. */
    public record Choice(MechaRig.Clip clip, double tick, int blend) {
    }

    /** The Hopper's build clip (its length is the build's). */
    public static final MechaRig.Clip BUILD = MechaRig.HOPPER.clip("build");
    /** Getting out the ordinary way: the boarding clip run backwards (clamps, hoop up, hoop down), on the frame after the fact. */
    public static final int UNBOARD = 22;

    /** Board, then boot: the cockpit's two clips run one after the other from the boarding tick. */
    public static final int BOOT_START = 22, BOOT_END = BOOT_START + 40;
    /** The boot's relay clicks, one per fitted module, from this boot tick, 3 apart. */
    public static final int RELAY_FROM = 26, RELAY_EVERY = 3;

    /** What an arm does with its module. */
    public enum Verb {
        SPIN(8, 10, 14), RECOIL(0, 20, 0), SUSTAIN(8, 40, 14), STRIKE(0, 18, 0), GUARD(10, 20, 12);

        /** The start clip's length (0: the loop is the whole action), the loop's, and the end clip's (0: none). */
        public final int start, loop, end;

        Verb(int start, int loop, int end) {
            this.start = start;
            this.loop = loop;
            this.end = end;
        }

        MechaRig.Clip startClip(int side) {
            return MechaRig.Clip.of(name().toLowerCase() + suffix(side), start, false);
        }

        MechaRig.Clip loopClip(int side) {
            return start == 0 ? MechaRig.Clip.of(name().toLowerCase() + suffix(side), loop, true) : MechaRig.Clip.of(name().toLowerCase() + "_loop" + suffix(side), loop, true);
        }

        MechaRig.Clip endClip(int side) {
            return MechaRig.Clip.of(name().toLowerCase() + "_end" + suffix(side), end, false);
        }
    }

    private static final Set<MechaModule> SPIN = EnumSet.of(MechaModule.DRILL, MechaModule.CHAINSAW, MechaModule.HARVESTER),
            // a gun kicks; a beam or stream is held braced (the lance's kick is its ignition); a jab strikes
            RECOIL = EnumSet.of(MechaModule.RIVET_GUN, MechaModule.ARC_CANNON),
            SUSTAIN = EnumSet.of(MechaModule.FLAMETHROWER, MechaModule.TRACTOR_BEAM, MechaModule.WELDING_TORCH, MechaModule.PLASMA_LANCE),
            GUARD = EnumSet.of(MechaModule.RIOT_SHIELD, MechaModule.MANIPULATOR_HAND);

    /** The verb an arm module is worked with (fists, hooks, scoops and the syringe strike). */
    public static Verb verb(MechaModule module) {
        if (module == null) return null;
        if (SPIN.contains(module)) return Verb.SPIN;
        if (RECOIL.contains(module)) return Verb.RECOIL;
        if (SUSTAIN.contains(module)) return Verb.SUSTAIN;
        if (GUARD.contains(module)) return Verb.GUARD;
        return Verb.STRIKE;
    }

    static String suffix(int side) {
        return side == 0 ? "_l" : "_r";
    }

    private HopperAnimation() {
    }

    /** The walk is the limp (one leg dragged): a flat battery, or a machine below a quarter of its health. */
    public static boolean limps(MechaEntity m) {
        return m.battery() <= 0 || m.getHealth() < m.getMaxHealth() * 0.25f;
    }

    public static boolean piloted(MechaEntity m) {
        return m.getFirstPassenger() instanceof Player;
    }

    static MechaRig rig(MechaEntity m) {
        MechaRig rig = MechaRig.of(m.frame());
        return rig != null ? rig : MechaRig.HOPPER;
    }

    // --- choosing ---------------------------------------------------------------------------------------------------

    /**
     * A machine nobody is in and nothing is happening to: no build, no eject or getting out still playing, not hurt,
     * venting or dying. It shows the parked pose and runs nothing.
     */
    public static boolean still(MechaEntity m, double now) {
        if (piloted(m) || m.isDeadOrDying() || m.hurtTime > 0 || m.vent() > 0 || Siege.rising(m, now)) return false;
        MechaRig rig = rig(m);
        long built = m.builtAt(), ejected = m.ejectedAt(), boarded = m.boardedAt();
        if (built >= 0 && now - built < rig.clip("build").length()) return false;
        if (ejected >= 0 && now - ejected < rig.clip("eject").length() + 30) return false;
        if (m.liftAt() >= 0 && now - m.liftAt() / 4 < 40) return false;
        return !(boarded < -1 && now + boarded < rig.clip("shutdown").length() + UNBOARD + 10);
    }

    /** The idle tic's place in its cycle: every 250-300 ticks, fixed per machine so every client shows it at once. */
    static double ticPhase(MechaEntity m, double now) {
        int cycle = 250 + Math.floorMod(m.getId(), 50);
        return ((now + m.getId() * 37L) % cycle + cycle) % cycle;
    }

    private static Choice at(MechaRig rig, String clip, double tick, int blend) {
        return new Choice(rig.clip(clip), tick, blend);
    }

    /** The whole machine's clip at {@code now} (game time plus partial tick). */
    public static Choice main(MechaEntity m, HopperView v, double now) {
        MechaRig rig = rig(m);
        double frac = now - Math.floor(now);
        if (m.isDeadOrDying()) {
            // wrecked in Siege: it stands up out of it, quickly, then collapses
            int rise = Siege.riseBeforeCollapse(m);
            double t = m.deathTime + frac;
            if (t < rise) return at(rig, "siege_out", t * Siege.OUT_TICKS / rise, 0);
            return at(rig, "destroyed", t - rise, rise > 0 ? 2 : 0);
        }
        // a parked machine shows the parked pose, settled, and nothing moves
        if (still(m, now)) return at(rig, "parked", 0, 0);
        boolean piloted = piloted(m);
        long built = m.builtAt();
        if (built >= 0 && !piloted && now - built < rig.clip("build").length()) return at(rig, "build", now - built, 0);
        // the pilot gone mid-Siege, the machine stands up out of it by itself
        if (!piloted && Siege.rising(m, now)) return siege(m, rig, now);
        long ejected = m.ejectedAt();
        if (ejected >= 0 && now - ejected < rig.clip("eject").length()) return at(rig, "eject", now - ejected, 0);
        long boarded = m.boardedAt();
        if (!piloted) {
            Choice lift = lift(m, rig, v, now);
            if (lift != null) return lift;
            if (boarded < -1 && now + boarded < rig.clip("shutdown").length()) return at(rig, "shutdown", now + boarded, 3);
            // after an eject or a shutdown the bar comes down through the blend
            return at(rig, "parked", now, ejected >= 0 && now - ejected < rig.clip("eject").length() + 20 ? 10 : 5);
        }
        // the Colossus kneels into Siege, holds it, stands up, or shrugs at a refusal: over everything else, the vent too
        Choice siege = siege(m, rig, now);
        if (siege != null) return siege;
        if (m.vent() > 0) return at(rig, "vent", MechaLogic.VENT_TICKS - m.vent() + frac, 3);
        // the Executive's jingle: the lapels tugged, a nod, the vials in turn (it stands still for it)
        double jingle = Executive.jingle(m, now);
        if (jingle >= 0) return at(rig, "drive_jingle", jingle, 3);
        // the Chimera's Graft: the arm that hit comes up to its face, the gulp and the swell; or the recoil of a refusal
        Choice graft = Chimera.clip(m, rig, now);
        if (graft != null) return graft;
        if (rig.wheels != null) return wheeled(m, rig, v, now, boarded);
        Choice lift = lift(m, rig, v, now);
        if (lift != null) return lift;
        Choice line = dragline(m, rig, v, now);
        if (line != null) return line;
        if (rig.climbs) {
            // on a wall: the clamps squeeze as it takes to it, then the climb from its measured rate, or holding on
            Legs.Climb c = v.climb;
            if (c.face != null) {
                double t = now - c.since;
                if (t < rig.clip("cling_on").length()) return at(rig, "cling_on", t, 2);
                return Math.abs(c.rate) > 0.05 ? at(rig, "climb", c.phase, 3) : at(rig, "cling", now, 4);
            }
            // over the top (or let go): it lands as the model turns back upright
            if (c.leftAt >= 0 && m.onGround() && now - c.leftAt < rig.clip("land").length()) return at(rig, "land", now - c.leftAt, 2);
        }
        if (rig.flies()) {
            Choice flight = flight(m, rig, v, now);
            if (flight != null) return flight;
        }
        if (rig.verb == MechaRig.Verb.POGO) {
            if (m.pogoAt() >= 0) {
                double t = now - m.pogoAt();
                int crouch = rig.clip("pogo_crouch").length();
                return t < crouch ? at(rig, "pogo_crouch", t, 2) : at(rig, "pogo_hold", t - crouch, 0);
            }
            long leap = m.leapAt(), landed = m.landedAt();
            if (leap >= 0 && landed < leap && now - leap < 200) return at(rig, "pogo_leap", now - leap, 0);
            if (landed >= 0 && now - landed < rig.clip("pogo_land").length()) return at(rig, "pogo_land", now - landed, 1);
        }
        if (v.airTicks > 3) return at(rig, "fall", now, 3);
        if (rig.has("jump") && v.jumpAt >= 0 && now - v.jumpAt < rig.clip("jump").length() && !m.onGround()) return at(rig, "jump", now - v.jumpAt, 1);
        // the landing's weight is the server's (synced), so everyone, a late arrival too, sees the same landing
        long land = m.landAt();
        if (land >= 0) {
            MechaRig.Clip clip = rig.clip((land & 1) == 1 ? "land_heavy" : "land");
            double t = now - land / 2;
            if (t >= 0 && t < clip.length()) return new Choice(clip, t, 1);
        }
        if (v.speed > HopperView.WALKING) {
            if (limps(m)) return at(rig, "limp", v.limpPhase(), 4);
            // backing, on a frame with its own backing gait
            if (v.forward < -HopperView.WALKING && rig.has("walk_back")) return at(rig, "walk_back", v.backPhase, 4);
            return new Choice(rig.walk(false), v.walkPhase(), rig.walkBlend);
        }
        if (Math.abs(v.turn) > 4) return at(rig, v.turn > 0 ? "turn_r" : "turn_l", now, 3);
        if (boarded >= 0 && now - boarded < BOOT_START + rig.clip("boot").length()) {
            // standing up from the parked sag while the cockpit boots (any movement switches to the walk)
            return now - boarded < BOOT_START ? at(rig, "parked", now, 0) : at(rig, "boot_legs", now - boarded - BOOT_START, 3);
        }
        if (!v.far) {
            double tic = ticPhase(m, now);
            if (tic < rig.clip("idle_tic").length()) return at(rig, ticClip(m, rig, now), tic, 3);
        }
        return at(rig, "idle", now, 5);
    }

    /**
     * The idle tic's clip: a frame with a mirrored tic ({@code idle_tic_r}, the Chimera's other forearm) plays it every
     * other time, the same on every client.
     */
    static String ticClip(MechaEntity m, MechaRig rig, double now) {
        if (!rig.has("idle_tic_r")) return "idle_tic";
        int cycle = 250 + Math.floorMod(m.getId(), 50);
        long n = (long) Math.floor((now + m.getId() * 37L) / cycle);
        return Math.floorMod(n + m.getId(), 2) == 1 ? "idle_tic_r" : "idle_tic";
    }

    /**
     * A frame on wheels (the Wheelhouse): Rush Hour from its synced moment (the wind-up, the boost, the wind-down, the
     * refusal), the landing, then the drive's clips from what this client measures ({@link Wheels#drive}), the engine's
     * start while it boots standing, the tic, idle. Turning is the {@code steer} controller's and code's.
     */
    static Choice wheeled(MechaEntity m, MechaRig rig, HopperView v, double now, long boarded) {
        Wheels.State w = v.wheels;
        long rush = m.rushAt();
        boolean rushing = false;
        if (rush >= 0) {
            double t = now - rush / 4;
            if ((rush & 3) == Wheelhouse.REFUSED) {
                if (t < Wheelhouse.REFUSED_TICKS) return at(rig, "rush_refused", t, 1);
            } else if (t < Wheelhouse.WINDUP) {
                return at(rig, "rush_windup", t, 1);
            } else if (t < Wheelhouse.WINDUP + Wheelhouse.BOOST_TICKS) {
                // the boost: its loop at the measured speed, or by time while it stands
                rushing = true;
                if (w.speed() <= Wheels.MOVING) return at(rig, "rush", now, 3);
            } else if (t < Wheelhouse.WINDUP + Wheelhouse.BOOST_TICKS + Wheelhouse.END_TICKS) {
                return at(rig, "rush_end", t - Wheelhouse.WINDUP - Wheelhouse.BOOST_TICKS, 2);
            }
        }
        long land = m.landAt();
        if (land >= 0) {
            MechaRig.Clip clip = rig.clip((land & 1) == 1 ? "land_heavy" : "land");
            double t = now - land / 2;
            if (t >= 0 && t < clip.length()) return new Choice(clip, t, 1);
        }
        Wheels.Drive drive = Wheels.drive(rig.wheels, w, now, m.battery() <= 0, rushing, rig::has);
        if (drive != null) return at(rig, drive.clip(), drive.tick(), drive.blend());
        if (boarded >= 0 && now - boarded < BOOT_END) {
            return now - boarded < BOOT_START ? at(rig, "parked", now, 0) : at(rig, rig.name("boot_legs"), now - boarded - BOOT_START, 3);
        }
        if (!v.far) {
            double tic = ticPhase(m, now);
            if (tic < rig.clip("idle_tic").length()) return at(rig, "idle_tic", tic, 3);
        }
        return at(rig, "idle", now, 5);
    }

    /**
     * Turning on wheels: on the spot the turn clips, sliding (the pilot sneaking at speed) the drift; otherwise nothing,
     * and the code steers the wheels from the turn ({@link Wheels}).
     */
    public static Choice steer(MechaEntity m, HopperView v, double now) {
        MechaRig rig = rig(m);
        if (rig.wheels == null || !piloted(m) || m.isDeadOrDying() || m.vent() > 0) return null;
        Wheels.State w = v.wheels;
        int spot = Wheels.onTheSpot(w);
        if (spot != 0) return at(rig, spot > 0 ? "turn_r" : "turn_l", now, 4);
        if (drifting(m, w)) return at(rig, w.turn > 0 ? "drift_r" : "drift_l", now, 4);
        return null;
    }

    /** A handbrake drift: the pilot sneaking while it turns at speed (the rear slides out). */
    public static boolean drifting(MechaEntity m, Wheels.State w) {
        return m.getFirstPassenger() instanceof Player p && p.isShiftKeyDown() && w.speed() > 0.3 && Math.abs(w.turn) > 1.5f;
    }

    /**
     * Heavy Lifting (the Bulwark): the grab, the refused grab, the throw and the set-down from the synced lift moment,
     * then, while something is held, the hold standing or the carrying walk. Null when the machine lifts nothing.
     */
    static Choice lift(MechaEntity m, MechaRig rig, HopperView v, double now) {
        if (rig.verb != MechaRig.Verb.LIFT) return null;
        long lift = m.liftAt();
        if (lift >= 0) {
            int kind = (int) (lift & 3);
            double t = now - lift / 4;
            switch (kind) {
                case Bulwark.GRAB -> {
                    MechaRig.Clip grab = rig.grab();
                    if (t < grab.length()) return new Choice(grab, t, 2);
                }
                case Bulwark.REFUSED -> {
                    if (t < rig.clip("grab_refused").length()) return at(rig, "grab_refused", t, 2);
                }
                case Bulwark.THROW -> {
                    MechaRig.Clip thrown = rig.throwing();
                    if (t < thrown.length()) return new Choice(thrown, t, 0);
                }
                default -> {
                    // set down: the grab's lift run backwards, from the hold to the ground
                    if (t < Bulwark.SET_TICKS) return new Choice(rig.grab(), Bulwark.GRAB_HELD - t, 0);
                }
            }
        }
        if (m.heldId() < 0) return null;
        if (v.speed > HopperView.WALKING) return new Choice(rig.walk(true), v.walkPhase(), 4);
        return new Choice(rig.hold(), now, 4);
    }

    /** Siege (the Colossus) from its synced moment: in, held, out, or the refused shrug. Null when none is under way. */
    static Choice siege(MechaEntity m, MechaRig rig, double now) {
        if (rig.verb != MechaRig.Verb.SIEGE) return null;
        double t = Siege.since(m, now);
        if (t < 0) return null;
        return switch (Siege.kind(m)) {
            case Siege.ENTER -> t < Siege.IN_TICKS ? at(rig, "siege_in", t, 3) : at(rig, "siege", t - Siege.IN_TICKS, 0);
            case Siege.LEAVE -> t < Siege.OUT_TICKS ? at(rig, "siege_out", t, 0) : null;
            case Siege.REFUSED -> t < Siege.REFUSED_TICKS ? at(rig, "siege_refused", t, 2) : null;
            default -> null;
        };
    }

    /**
     * The Dragline (the Skitter), from its synced moment: the refusal, the fire (the line out from its tick 9), the cut;
     * then, with the line out and the machine off the ground and off any wall, winding in, paying out or hanging and
     * swinging under the anchor. Null otherwise (on the ground the line simply trails).
     */
    static Choice dragline(MechaEntity m, MechaRig rig, HopperView v, double now) {
        if (rig.verb != MechaRig.Verb.DRAGLINE) return null;
        long line = m.lineAt();
        if (line >= 0) {
            double t = now - line / 4;
            switch ((int) (line & 3)) {
                case Skitter.REFUSED -> {
                    if (t < Skitter.REFUSED_TICKS) return at(rig, "dragline_refused", t, 1);
                }
                case Skitter.FIRED -> {
                    if (t < Skitter.FIRE_TICKS) return at(rig, "dragline_fire", t, 1);
                }
                default -> {
                    if (t < Skitter.CUT_TICKS) return at(rig, "dragline_cut", t, 1);
                }
            }
        }
        // while a pad can still stand on something the machine keeps its ground stance and is simply dragged
        if (m.lineLength() == 0 || m.onGround() || v.climb.face != null || Legs.canStand(m)) return null;
        return switch (m.lineMode()) {
            case Skitter.REEL -> at(rig, "dragline_reel", now, 3);
            case Skitter.RAPPEL -> at(rig, "dragline_rappel", now, 3);
            case Skitter.HANG -> at(rig, "dragline_swing", now, 4);
            default -> null;
        };
    }

    /**
     * The cage stays shut in the air and on a wall: a machine that climbs opens it for getting out only when it stands
     * on the ground (the Golden Parachute's eject is the one clip that throws it open anywhere).
     */
    public static boolean cageShut(MechaEntity m) {
        return rig(m).climbs && (!m.onGround() || m.view.climb.face != null);
    }

    /**
     * A flier (the Kite) in the air, and its Talon Lift: the talons' grab, refusal and drop from the synced talon moment,
     * the takeoff from the synced crouch, then by flight state: the stall when the battery goes flat aloft, carrying,
     * the flare about six ticks before touching down, the wingbeat while the pilot holds the climb or it rises (a hover
     * when it barely moves), a glide coming down fast, else the fall. Null on the ground (the walker's clips then).
     */
    static Choice flight(MechaEntity m, MechaRig rig, HopperView v, double now) {
        long lift = m.liftAt();
        if (lift >= 0) {
            double t = now - lift / 4;
            switch ((int) (lift & 3)) {
                case Kite.GRAB -> {
                    if (t < Kite.GRAB_TICKS) return at(rig, "talon_grab", t, 2);
                }
                case Kite.REFUSED -> {
                    if (t < Kite.REFUSED_TICKS) return at(rig, "talon_refused", t, 2);
                }
                case Kite.DROP -> {
                    if (t < Kite.DROP_TICKS) return at(rig, "talon_drop", t, 1);
                }
                default -> {
                }
            }
        }
        long take = m.takeoffAt();
        if (take >= 0 && now - take >= 0 && now - take < Kite.TAKEOFF_TICKS) return at(rig, "takeoff", now - take, 2);
        if (m.onGround()) return null;
        if (m.battery() <= 0 && v.stallAt >= 0) {
            double t = now - v.stallAt;
            int stall = rig.clip("stall").length();
            return t < stall ? at(rig, "stall", t, 3) : at(rig, "stall_loop", t - stall, 0);
        }
        if (m.heldId() >= 0) return at(rig, "talon_carry", v.carryPhase, 4);
        if (v.flareAt >= 0 && now - v.flareAt < rig.clip("flare").length()) return at(rig, "flare", now - v.flareAt, 3);
        if (m.climbing() || v.vy > 0.02) {
            return m.climbing() && v.speed < 0.1 ? at(rig, "hover", now, 4) : at(rig, "flap", v.flapPhase, 4);
        }
        if (v.vy < 0 && v.speed > 0.3) return at(rig, "glide", now, 6);
        return at(rig, "fall", now, 4);
    }

    /** The tail's roll into a turn (the {@code bank} controller): while the body turns faster than 1.5 degrees a tick. */
    public static Choice bank(MechaEntity m, double now) {
        MechaRig rig = rig(m);
        if (!rig.flies() || m.onGround() || m.isDeadOrDying() || Math.abs(m.view.turn) <= 1.5f) {
            m.view.bankAt = -1;
            return null;
        }
        int side = m.view.turn > 0 ? 1 : -1;
        if (m.view.bankAt < 0 || m.view.bankSide != side) {
            m.view.bankAt = now;
            m.view.bankSide = side;
        }
        return at(rig, side > 0 ? "bank_r" : "bank_l", now - m.view.bankAt, 2);
    }

    /** Getting out the ordinary way: the hoop opens and closes again over the empty collar (the jar is already out). */
    public static Choice unboard(MechaEntity m, double now) {
        long boarded = m.boardedAt(), ejected = m.ejectedAt();
        if (piloted(m) || boarded >= -1 || m.isDeadOrDying() || cageShut(m)) return null;
        MechaRig rig = rig(m);
        if (ejected >= 0 && now - ejected < rig.clip("eject").length() + 30) return null;
        double t = now + boarded;
        if (t < 0 || t >= UNBOARD) return null;
        MechaRig.Clip board = rig.clip("board");
        return new Choice(board, board.length() - t, 0);
    }

    /** Board, then the boot, over live controls; nothing after. */
    public static Choice cockpit(MechaEntity m, double now) {
        long boarded = m.boardedAt();
        if (!piloted(m) || boarded < 0 || m.isDeadOrDying()) return null;
        MechaRig rig = rig(m);
        double t = now - boarded;
        if (t < BOOT_START) return at(rig, "board", t, 0);
        // far off, the boot's lamps and sensor sweep are not worth drawing
        if (t < BOOT_START + rig.clip("boot").length() && !m.view.far) return at(rig, "boot", t - BOOT_START, 0);
        return null;
    }

    /** The guard's jolt when a blow lands on it (every frame's {@code guard_hit} clip, 0.4 s). */
    static final int GUARD_HIT = 8;

    /** One arm: a dry fire, its module's verb while held (a guard jolts when it takes a blow), the verb's end once let go. */
    public static Choice arm(MechaEntity m, HopperView v, int side, double now) {
        if (m.isDeadOrDying() || !piloted(m)) return null;
        // both fists are on the load (or reaching for it): the arms' own verbs wait
        if (Bulwark.handsBusy(m, now)) return null;
        // the Colossus's forearms are planted in Siege (entering, held, leaving): no arm verb lifts them
        if (Siege.rooted(m)) return null;
        MechaRig rig = rig(m);
        // a frame with one tool has no right arm's clips
        if (!rig.has("dry_fire" + suffix(side))) return null;
        long dry = m.dryAt();
        MechaRig.Clip dryClip = rig.clip("dry_fire" + suffix(side));
        if (dry >= 0 && (dry & 1) == side && now - dry / 2 < dryClip.length()) return new Choice(dryClip, now - dry / 2, 1);
        boolean held = (m.armsShown() & (1 << side)) != 0;
        Verb verb = verb(m.arm(side));
        if (held && verb != null) {
            if (v.pressAt[side] < 0) {
                v.pressAt[side] = now;
                v.verb[side] = verb;
            }
            v.releaseAt[side] = -1;
            double t = now - v.pressAt[side];
            // a blow taken on the raised guard: the arm jolts back with it (the hips take the weight in the flinch)
            if (verb == Verb.GUARD && m.hurtTime > 0 && t > verb.start && 10 - m.hurtTime < GUARD_HIT) {
                return new Choice(MechaRig.Clip.of("guard_hit" + suffix(side), GUARD_HIT, false), 10 - m.hurtTime + (now - Math.floor(now)), 1);
            }
            if (verb.start > 0 && t < verb.start) return new Choice(verb.startClip(side), t, 2);
            return new Choice(verb.loopClip(side), verb.start > 0 ? t - verb.start : t, 0);
        }
        if (v.pressAt[side] >= 0) {
            v.pressAt[side] = -1;
            v.releaseAt[side] = now;
        }
        Verb was = v.verb[side];
        if (was != null && was.end > 0 && v.releaseAt[side] >= 0 && now - v.releaseAt[side] < was.end) {
            return new Choice(was.endClip(side), now - v.releaseAt[side], 2);
        }
        return null;
    }

    /** The flinch, from the hurt time every client is sent; on a Wheelhouse, the buffer's ram first. */
    public static Choice hit(MechaEntity m, double now) {
        if (m.isDeadOrDying()) return null;
        MechaRig rig = rig(m);
        long ram = m.ramAt();
        if (ram >= 0 && rig.has("rush_ram") && now - ram < rig.clip("rush_ram").length()) return at(rig, "rush_ram", now - ram, 0);
        if (m.hurtTime <= 0) return null;
        return at(rig(m), "hurt", 10 - m.hurtTime + (now - Math.floor(now)), 0);
    }

    // --- the controllers ------------------------------------------------------------------------------------------

    /** Registers the controllers (client and server; only the client ever runs them). */
    public static void register(MechaEntity m, AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(controller(m, "main", HopperAnimation::mainState, true));
        // a frame on wheels turns on the spot and drifts by clip (the code steers it otherwise)
        if (rig(m).wheels != null) controllers.add(controller(m, "steer", s -> pick(s, steer(s.getAnimatable(), s.getAnimatable().view, now(s))), true));
        controllers.add(controller(m, "cockpit", s -> pick(s, cockpit(s.getAnimatable(), now(s))), true));
        // the boarding clip backwards when the pilot gets out; no keyframe effects (they would all fire at once backwards)
        controllers.add(controller(m, "unboard", s -> pick(s, unboard(s.getAnimatable(), now(s))), false));
        // a flier's tail rolls into its turns over the flight clips
        if (MechaRig.of(m.frame()) != null && MechaRig.of(m.frame()).flies()) {
            controllers.add(controller(m, "bank", s -> pick(s, bank(s.getAnimatable(), now(s))), false));
        }
        controllers.add(controller(m, "arm_l", s -> pick(s, arm(s.getAnimatable(), s.getAnimatable().view, 0, now(s))), true));
        controllers.add(controller(m, "arm_r", s -> pick(s, arm(s.getAnimatable(), s.getAnimatable().view, 1, now(s))), true));
        controllers.add(controller(m, "hit", s -> pick(s, hit(s.getAnimatable(), now(s))), false));
    }

    private static SeekingController<MechaEntity> controller(MechaEntity m, String name,
                                                             software.bernie.geckolib.animation.AnimationController.AnimationStateHandler<MechaEntity> handler,
                                                             boolean effects) {
        SeekingController<MechaEntity> c = new SeekingController<>(m, name, handler);
        if (effects) {
            c.setSoundKeyframeHandler(event -> com.coolfreeze23.headinajar.client.HopperKeyframes.sound(event));
            c.setParticleKeyframeHandler(event -> com.coolfreeze23.headinajar.client.HopperKeyframes.particle(event));
            c.setCustomInstructionKeyframeHandler(event -> com.coolfreeze23.headinajar.client.HopperKeyframes.instruction(event));
        }
        return c;
    }

    private static double now(AnimationState<MechaEntity> state) {
        return state.getAnimatable().level().getGameTime() + state.getPartialTick();
    }

    private static PlayState mainState(AnimationState<MechaEntity> state) {
        MechaEntity m = state.getAnimatable();
        double now = now(state);
        m.view.update(m, now);
        m.view.far = com.coolfreeze23.headinajar.client.HopperClient.far(m);
        return pick(state, main(m, m.view, now));
    }

    @SuppressWarnings("unchecked")
    private static PlayState pick(AnimationState<MechaEntity> state, Choice choice) {
        SeekingController<MechaEntity> c = (SeekingController<MechaEntity>) state.getController();
        if (choice == null) return c.none();
        return c.show(choice.clip().name(), choice.clip().raw(), choice.clip().at(choice.tick()), choice.blend());
    }
}
