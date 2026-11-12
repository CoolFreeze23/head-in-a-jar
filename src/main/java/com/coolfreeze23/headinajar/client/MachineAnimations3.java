package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.ChargingDockBlock;
import com.coolfreeze23.headinajar.machine.KitController;
import com.coolfreeze23.headinajar.machine.KitController.Choice;
import com.coolfreeze23.headinajar.machine.MachineKit;
import software.bernie.geckolib.animation.AnimatableManager;

/** The third batch's clips and when they play (the rules of {@link MachineAnimations}). */
public final class MachineAnimations3 {
    private MachineAnimations3() {
    }

    // --- Charging Dock -------------------------------------------------------------------------------------------

    /** The switch's clip from one state to another (OPEN, SHUT, LOW, FULL) and its length; null: none (it ends in place). */
    private static final String[][] DOCK_CLIP = {
            // to:  OPEN          SHUT          LOW              FULL
            {null, "occupy", "occupy_low", "full"},          // from OPEN
            {"vacate", null, "brownout", "full"},            // from SHUT
            {"vacate_low", "brownout_out", null, "brownout_out"}, // from LOW
            {null, "topup", "brownout", null}};              // from FULL (its clip already ends open)
    /** A late joiner's pose for each state: the end of a clip that ends there. */
    private static final String[] DOCK_REST = {"vacate", "occupy", "occupy_low", "full"};

    private static int dockLength(String clip) {
        return switch (clip) {
            case "occupy", "vacate", "topup" -> 12;
            case "full" -> 14;
            case "brownout" -> 16;
            case "occupy_low" -> 18;
            default -> 10;
        };
    }

    public static void dock(ChargingDockBlock.Entity dock, AnimatableManager.ControllerRegistrar controllers) {
        // the knife switch: one clip per change of state, played from the moment it is seen; a refusal hops it; then held
        MachineAnimations.add(controllers, new KitController<>(dock, "switch", (d, now, k) -> {
            MachineKit kit = d.kit();
            int state = d.switchState();
            double r = kit.since(MachineKit.EV_REFUSE, now);
            if (r >= 0 && r < 8) return new Choice(state == ChargingDockBlock.Entity.OPEN ? "refuse" : "refuse_on", r);
            if (k.lastState != state) {
                // a late joiner sees the pose at rest; a change seen plays its clip from now
                if (k.lastState < 0 || DOCK_CLIP[k.lastState][state] == null) {
                    k.mark = now - 1000;
                    k.lastBit = null;
                } else {
                    k.mark = now;
                    // FALSE: a change seen (the kit's toggle test reads it as moving until the clip's length has passed)
                    k.lastBit = Boolean.FALSE;
                    k.released = k.lastState;
                }
                k.lastState = state;
            }
            double t = now - k.mark;
            String clip = k.lastBit != null ? DOCK_CLIP[(int) k.released][state] : DOCK_REST[state];
            return new Choice(clip, Math.min(t, dockLength(clip)));
        }));
        // charging: the sparks and the meter's ticks
        MachineAnimations.add(controllers, new KitController<>(dock, "charging", (d, now, k) ->
                d.switchState() == ChargingDockBlock.Entity.SHUT ? new Choice("charging", now % 40) : null, "charging"));
    }

    // --- Tesla Node -------------------------------------------------------------------------------------------

    public static void tesla(com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode node, AnimatableManager.ControllerRegistrar controllers) {
        // the ring's float: refuse > perk > link or unlink > stop > send (send_low in a brown-out) > idle
        MachineAnimations.add(controllers, new KitController<>(node, "ring", (n, now, k) -> {
            MachineKit kit = n.kit();
            double r = kit.since(MachineKit.EV_REFUSE, now);
            if (r >= 0 && r < 8) return new Choice("refuse", r);
            double perk = kit.since(MachineKit.EV_OWN, now);
            if (perk >= 0 && perk < 36) return new Choice("perk", perk);
            int linked = n.target() != null ? 1 : 0;
            if (k.lastState != linked) {
                // a change seen plays its clip from now; a late joiner sees it done
                k.mark = k.lastState < 0 ? now - 1000 : now;
                k.lastState = linked;
            }
            double l = now - k.mark;
            if (linked == 1 && l < 24) return new Choice("link", l);
            if (linked == 0 && l < 16) return new Choice("unlink", l);
            if (n.sending()) {
                k.lastBit = Boolean.TRUE;
                return new Choice(kit.rate() > 0 && kit.rate() < 4 ? "send_low" : "send", now % 40);
            }
            if (k.lastBit == Boolean.TRUE) {
                k.lastBit = Boolean.FALSE;
                k.released = now;
            }
            if (!Double.isNaN(k.released) && now - k.released < 12) return new Choice("stop", now - k.released);
            return linked == 1 ? new Choice("idle", now % 80) : null;
        }, "send", "send_low", "idle"));
    }

    public static boolean awake(com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode node, double now) {
        MachineKit kit = node.kit();
        return node.target() != null || node.source() != null || node.spinSpeed != 0 || node.aimVel != 0
                || kit.since(MachineKit.EV_REFUSE, now) < 12 || kit.since(MachineKit.EV_OWN, now) < 36;
    }

    // --- Injection Rig ------------------------------------------------------------------------------------------

    /** Ticks since the shot began (-1 none), and the cancel's moment in the shot's ticks (NaN none). */
    static double shot(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig rig, double now) {
        double t = rig.kit().since(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig.EV_INJECT, now);
        return t >= 0 && t < 200 ? t : -1;
    }

    static double cancelledAt(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig rig, double now) {
        double t = shot(rig, now), c = rig.kit().since(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig.EV_CANCEL, now);
        return t >= 0 && c >= 0 && c <= t ? t - c : Double.NaN;
    }

    /** The result the server answered (0 accepted, 1 rejected, 2 mutated); accepted until it arrives. */
    static int result(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig rig, double now) {
        double t = shot(rig, now), r = rig.kit().since(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig.EV_RESULT, now);
        return t >= 0 && r >= 0 && r <= t ? rig.kit().eventParam(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig.EV_RESULT) : 0;
    }

    public static void rig(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig rig, AnimatableManager.ControllerRegistrar controllers) {
        // the syringe and plunger: the shot, then the tail by its result, or the cancel; at rest primed while loaded
        MachineAnimations.add(controllers, new KitController<>(rig, "main", (r, now, k) -> {
            double t = shot(r, now), tc = cancelledAt(r, now);
            if (!Double.isNaN(tc)) {
                double c = t - tc;
                if (c < 14) return new Choice("cancel", c);
            } else if (t >= 0 && t < 30) {
                return new Choice("inject", t);
            } else if (t >= 30 && t < 47) {
                return new Choice(switch (result(r, now)) {
                    case 1 -> "rejected";
                    case 2 -> "mutated";
                    default -> "accepted";
                }, t - 30);
            }
            return KitController.toggle(k, r.loaded(), now, "prime", 8, "unprime", 6);
        }));
        // a refusal on its own controller (during a shot it is the lamp and the sound only)
        MachineAnimations.add(controllers, new KitController<>(rig, "refuse", (r, now, k) ->
                shot(r, now) >= 0 && shot(r, now) < 47 ? null : MachineAnimations.refusal(r.kit(), now, "refuse")));
    }

    public static boolean awake(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig rig, double now) {
        double t = shot(rig, now);
        return t >= 0 && t < 47 || rig.loaded() || rig.kit().since(com.coolfreeze23.headinajar.machine.MachineKit.EV_REFUSE, now) < 8
                || MachineAnimations.toggling(rig, "main", now, 8);
    }

    // --- DNA Sequencer ------------------------------------------------------------------------------------------

    static final String BASES = "acgt";

    /** Ticks into a player's called read (the tally), -1 none; its length (a thirteenth slot when every call was right). */
    static double tally(com.coolfreeze23.headinajar.block.LabMachines.Sequencer s, double now) {
        double t = s.kit().since(com.coolfreeze23.headinajar.block.LabMachines.Sequencer.EV_TALLY, now);
        return t >= 0 && t < 140 ? t : -1;
    }

    static boolean fullTally(com.coolfreeze23.headinajar.block.LabMachines.Sequencer s) {
        return s.kit().eventParam(com.coolfreeze23.headinajar.block.LabMachines.Sequencer.EV_TALLY) == 0xFFF;
    }

    /** Ticks since the read's end was rung (the tally's at +84, the auto-read's at +2), -1 none. */
    static double ending(com.coolfreeze23.headinajar.block.LabMachines.Sequencer s, double now) {
        double t = tally(s, now);
        if (t >= 84) return t - 84;
        double f = s.kit().since(MachineKit.EV_FINISH, now);
        return f >= 2 && f < 60 ? f - 2 : -1;
    }

    /** A key struck by a player calling in the screen: ticks since (-1 none), its base and index from the event. */
    static double keyBeat(com.coolfreeze23.headinajar.block.LabMachines.Sequencer s, double now) {
        double t = s.kit().since(com.coolfreeze23.headinajar.block.LabMachines.Sequencer.EV_BEAT, now);
        return t >= 0 && t < 16 && tally(s, now) < 0 ? t : -1;
    }

    /** The beat being struck now by the auto-read (ticks into it, -1 none). */
    static double beat(com.coolfreeze23.headinajar.block.LabMachines.Sequencer s, double now) {
        double t = now - s.beatAt;
        return s.beat >= 0 && s.kit().running() && t >= 0 && t < 16 ? t : -1;
    }

    public static void sequencer(com.coolfreeze23.headinajar.block.LabMachines.Sequencer seq, AnimatableManager.ControllerRegistrar controllers) {
        // the keys: the beat's key pressed; a refusal ripples them
        MachineAnimations.add(controllers, new KitController<>(seq, "keys", (s, now, k) -> {
            Choice r = MachineAnimations.refusal(s.kit(), now, "refuse");
            if (r != null) return r;
            double kb = keyBeat(s, now);
            if (kb >= 0 && kb < 8) return new Choice("press_" + BASES.charAt(s.kit().eventParam(com.coolfreeze23.headinajar.block.LabMachines.Sequencer.EV_BEAT) & 3), kb);
            double b = beat(s, now);
            if (b >= 0 && b < 8) return new Choice("press_" + BASES.charAt(s.shownBases()[s.beat] & 3), b);
            return null;
        }));
        // the crank and the call flag: a beat rings once; a called read tallies; the fraction loads and unloads
        MachineAnimations.add(controllers, new KitController<>(seq, "work", (s, now, k) -> {
            double t = tally(s, now);
            if (t >= 0 && t < (fullTally(s) ? 110 : 96)) return new Choice(fullTally(s) ? "tally_full" : "tally", t);
            double kb = keyBeat(s, now);
            if (kb >= 0) return new Choice("ring", kb);
            double b = beat(s, now);
            if (b >= 0) return new Choice("ring", b);
            double f = s.kit().since(MachineKit.EV_FINISH, now);
            if (f >= 0 && f < 6) return new Choice("unload", f);
            return KitController.toggle(k, s.loaded(), now, "load", 6, "unload", 6);
        }));
        // the receipt torn off at the end
        MachineAnimations.add(controllers, new KitController<>(seq, "receipt", (s, now, k) -> {
            double e = ending(s, now);
            return e >= 2 && e < 12 ? new Choice("tear", e - 2) : null;
        }));
        // the cash drawer: the bell at the end; out for a screen
        MachineAnimations.add(controllers, new KitController<>(seq, "drawer", (s, now, k) -> {
            double e = ending(s, now);
            boolean full = tally(s, now) >= 0 && fullTally(s);
            if (e >= 0 && e < (full ? 44 : 36)) return new Choice(s.kit().open() ? (full ? "ding2_open" : "ding_open") : (full ? "ding2" : "ding"), e);
            return KitController.toggle(k, s.kit().open(), now, "drawer_open", 10, "drawer_close", 10);
        }));
    }

    public static boolean awake(com.coolfreeze23.headinajar.block.LabMachines.Sequencer s, double now) {
        return s.kit().running() || s.loaded() || tally(s, now) >= 0 || ending(s, now) >= 0 || s.kit().open()
                || s.kit().since(MachineKit.EV_REFUSE, now) < 8 || MachineAnimations.toggling(s, "drawer", now, 10)
                || MachineAnimations.toggling(s, "work", now, 6);
    }

    // --- Cerebral Array ----------------------------------------------------------------------------------------

    /** Ticks since the gavel lifted for a vote (it strikes at 10), -1 none. */
    static double vote(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity a, double now) {
        double t = a.kit().since(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity.EV_VOTE, now);
        return t >= 0 && t < 60 ? t : -1;
    }

    static int ayes(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity a) {
        return a.kit().eventParam(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity.EV_VOTE) & 15;
    }

    static int nays(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity a) {
        return a.kit().eventParam(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity.EV_VOTE) >> 4 & 15;
    }

    public static void array(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity array, AnimatableManager.ControllerRegistrar controllers) {
        // the gavel and the cards: a vote (two more bangs when the nays have it); a refusal taps the gavel twice
        MachineAnimations.add(controllers, new KitController<>(array, "vote", (a, now, k) -> {
            double t = vote(a, now);
            if (t >= 0) return new Choice(nays(a) > ayes(a) ? "vote_anyway" : "vote", t);
            return MachineAnimations.refusal(a.kit(), now, "refuse");
        }));
    }

    public static boolean awake(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity a, double now) {
        return vote(a, now) >= 0 || a.kit().since(MachineKit.EV_REFUSE, now) < 8 || sitting(a);
    }

    /** The board sits: fed, and at least one seat taken. */
    static boolean sitting(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity a) {
        if (!a.fed()) return false;
        for (int i = 0; i < com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity.HEAD_SLOTS; i++) if (!a.items().getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    /**
     * The sitting board's gavel, 0 (down) to 1 (lifted): it rises over 5 ticks, holds 4 and drops in 3 (-1: the clips have it), after gaps of
     * 40 to 80 ticks drawn from the machine's position and the round's number (the same for every player); nothing
     * during a vote or a refusal.
     */
    public static double gavelLift(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity a, double time) {
        double now = MachineAnimations.now(a);
        if (!sitting(a) || vote(a, now) >= 0 || a.kit().since(MachineKit.EV_REFUSE, now) < 8) return -1;
        long seed = a.getBlockPos().asLong();
        double shifted = time + Math.floorMod(seed, 480L);
        long round = (long) Math.floor(shifted / 480);
        double in = shifted - round * 480;
        java.util.Random r = new java.util.Random(seed * 31 + round);
        double at = 0;
        for (int i = 0; i < 12; i++) {
            at += 40 + r.nextInt(41);
            if (at > 480 - 12) break;
            double t = in - at;
            if (t >= 0 && t < 12) {
                if (t < 5) return Math.sin(Math.PI / 2 * t / 5);
                if (t < 9) return 1;
                double d = (t - 9) / 3;
                return 1 - d * d;
            }
        }
        return 0;
    }

    // --- Employee Mount ----------------------------------------------------------------------------------------

    /** A paid employee's work tic: its period by trade, its phase by place (so a row of mounts is not in step). */
    static int stampPeriod(com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity m) {
        return switch (m.profession()) {
            case "armorer", "weaponsmith", "toolsmith" -> 200;
            case "librarian", "cartographer" -> 160;
            default -> 120;
        };
    }

    static double stamp(com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity m, double now) {
        if (m.head().isEmpty() || !m.paid() || "nitwit".equals(m.profession())) return -1;
        int period = stampPeriod(m);
        long phase = Math.floorMod(m.getBlockPos().asLong() * 31, period);
        return Math.floorMod((long) now - phase, period) + (now - Math.floor(now));
    }

    static boolean smith(com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity m) {
        return switch (m.profession()) {
            case "armorer", "weaponsmith", "toolsmith" -> true;
            default -> false;
        };
    }

    public static void mount(com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity mount, AnimatableManager.ControllerRegistrar controllers) {
        // the handset: a complaint; payday's cheer; a refusal's hops
        MachineAnimations.add(controllers, new KitController<>(mount, "phone", (m, now, k) -> {
            Choice r = MachineAnimations.refusal(m.kit(), now, "refuse");
            if (r != null) return r;
            double c = m.kit().since(com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity.EV_COMPLAINT, now);
            if (c >= 0 && c < 68) return new Choice("complaint", c);
            double p = m.kit().since(com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity.EV_PAYDAY, now);
            boolean back = m.kit().eventParam(com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity.EV_PAYDAY) >= 3;
            if (p >= 0 && p < (back ? 70 : 30)) return new Choice(back ? "payday_back" : "payday", p);
            return null;
        }));
        // the stamp: a paid employee's work, at every tic (three strikes for the smiths)
        MachineAnimations.add(controllers, new KitController<>(mount, "stamp", (m, now, k) -> {
            double t = stamp(m, now);
            boolean three = smith(m);
            return t >= 0 && t < (three ? 34 : 14) ? new Choice(three ? "stamp_3" : "stamp", t) : null;
        }));
    }

    public static boolean awake(com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity m, double now) {
        double s = stamp(m, now);
        return s >= 0 && s < 34 || m.kit().since(com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity.EV_COMPLAINT, now) < 68 || m.kit().since(com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity.EV_PAYDAY, now) < 70
                || m.kit().since(MachineKit.EV_REFUSE, now) < 8;
    }

    public static boolean awake(ChargingDockBlock.Entity dock, double now) {
        return dock.switchState() != ChargingDockBlock.Entity.OPEN || dock.needleVel != 0 || dock.needle != 0
                || dock.kit().since(MachineKit.EV_REFUSE, now) < 8 || MachineAnimations.toggling(dock, "switch", now, 18);
    }
}
