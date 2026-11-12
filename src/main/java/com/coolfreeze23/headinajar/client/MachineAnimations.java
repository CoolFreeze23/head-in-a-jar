package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.GalvanicPileBlockEntity;
import com.coolfreeze23.headinajar.block.LabMachines;
import com.coolfreeze23.headinajar.block.SalineMixerBlockEntity;
import com.coolfreeze23.headinajar.machine.KitController;
import com.coolfreeze23.headinajar.machine.KitController.Choice;
import com.coolfreeze23.headinajar.machine.MachineKit;
import com.coolfreeze23.headinajar.machine.MachineLamp;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;

/**
 * What each kit machine's clips are and when they play: one chooser per controller, reading only synced state (the
 * kit's clock, its open bit, block events seen, the machine's own few fields), so every player sees the same tick.
 */
public final class MachineAnimations {
    private MachineAnimations() {
    }

    /** Game time plus the partial tick (the clock every clip is read against). */
    public static double now(BlockEntity machine) {
        Level level = machine.getLevel();
        if (level == null) return 0;
        return level.getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    static <T extends BlockEntity & GeoAnimatable> KitController<T> add(AnimatableManager.ControllerRegistrar controllers, KitController<T> c) {
        c.setSoundKeyframeHandler(MachineKeyframes::sound);
        c.setParticleKeyframeHandler(MachineKeyframes::particle);
        controllers.add(c);
        return c;
    }

    /** The refusal's 8-tick rattle, on a controller of its own registered last (so it wins while it plays). */
    static Choice refusal(MachineKit kit, double now, String clip) {
        double t = kit.since(MachineKit.EV_REFUSE, now);
        return t >= 0 && t < 8 ? new Choice(clip, t) : null;
    }

    // --- Galvanic Pile ---------------------------------------------------------------------------------------

    public static void pile(GalvanicPileBlockEntity pile, AnimatableManager.ControllerRegistrar controllers) {
        // the handle: a wind-back at each new piece, the two quarter turns of the work loop, the unwind when spent
        add(controllers, new KitController<>(pile, "handle", (p, now, c) -> {
            MachineKit kit = p.kit();
            double t = kit.elapsed(now);
            if (kit.running()) return t < 24 ? new Choice("refuel", t) : new Choice("work", t % 50);
            double f = kit.since(MachineKit.EV_FINISH, now);
            return f >= 0 && f < 10 ? new Choice("finish", f) : null;
        }, "work"));
        add(controllers, new KitController<>(pile, "refuse", (p, now, c) -> refusal(p.kit(), now, "refuse")));
    }

    public static boolean awake(GalvanicPileBlockEntity pile, double now) {
        MachineKit kit = pile.kit();
        return kit.running() || pile.squeeze > 0.001f || kit.since(MachineKit.EV_FINISH, now) < 10
                || kit.since(MachineKit.EV_REFUSE, now) < 8 || kit.since(MachineKit.EV_PLOP, now) < 8;
    }

    // --- Saline Mixer ------------------------------------------------------------------------------------------

    public static void mixer(SalineMixerBlockEntity mixer, AnimatableManager.ControllerRegistrar controllers) {
        // the caps: the doses while a batch runs (seeked on the batch's own length), else up while a screen is open
        add(controllers, new KitController<>(mixer, "caps", (m, now, c) -> {
            MachineKit kit = m.kit();
            if (kit.running()) {
                c.lastBit = null;
                return new Choice("batch", Math.min(200, Math.max(0, kit.elapsed(now) * 200 / Math.max(1, m.batchTicks()))));
            }
            return KitController.toggle(c, kit.open(), now, "open", 6, "close", 7);
        }));
        // the tap: pulled once a loop while bottles fill; the loop in hand is finished when the bottling stops
        add(controllers, new KitController<>(mixer, "tap", (m, now, c) -> {
            if (m.bottling()) {
                if (c.lastBit != Boolean.TRUE) c.mark = now;
                c.lastBit = true;
                return new Choice("bottle", (now - c.mark) % 10);
            }
            if (c.lastBit == Boolean.TRUE) {
                c.lastBit = false;
                c.mark = now - ((now - c.mark) % 10);
            }
            double t = now - c.mark;
            return c.lastBit != null && t < 10 ? new Choice("bottle", t) : null;
        }, "bottle"));
        add(controllers, new KitController<>(mixer, "flag", (m, now, c) -> KitController.toggle(c, m.hasOutput(), now, "flag_up", 6, "flag_down", 6)));
        add(controllers, new KitController<>(mixer, "refuse", (m, now, c) -> m.kit().running() ? null
                : refusal(m.kit(), now, "refuse_" + "skr".charAt(Math.floorMod(m.kit().eventParam(MachineKit.EV_REFUSE), 3)))));
    }

    public static boolean awake(SalineMixerBlockEntity mixer, double now) {
        MachineKit kit = mixer.kit();
        return kit.running() || kit.open() || mixer.bottling() || mixer.hasOutput() || mixer.paddleSpeed > 0
                || kit.since(MachineKit.EV_REFUSE, now) < 8 || kit.since(MachineKit.EV_FINISH, now) < 10
                || toggling(mixer, "caps", now, 7) || toggling(mixer, "flag", now, 6);
    }

    // --- Centrifuge ------------------------------------------------------------------------------------------

    public static void centrifuge(LabMachines.Centrifuge c, AnimatableManager.ControllerRegistrar controllers) {
        add(controllers, new KitController<>(c, "drawer", (m, now, k) -> KitController.toggle(k, m.kit().open(), now, "drawer_open", 5, "drawer_close", 6)));
        add(controllers, new KitController<>(c, "flag", (m, now, k) -> KitController.toggle(k, m.hasOutput(), now, "flag_up", 6, "flag_down", 6)));
        add(controllers, new KitController<>(c, "refuse", (m, now, k) -> refusal(m.kit(), now, m.kit().open() ? "refuse_open" : "refuse")));
    }

    public static boolean awake(LabMachines.Centrifuge c, double now) {
        MachineKit kit = c.kit();
        return c.speed > 0 || c.swing != 0 || kit.running() || kit.open() || c.hasOutput()
                || kit.since(MachineKit.EV_REFUSE, now) < 8 || toggling(c, "drawer", now, 6) || toggling(c, "flag", now, 6);
    }

    /** The Mixer moves nothing and plays no clip: its caps and flag rest where their state holds them. */
    public static boolean still(SalineMixerBlockEntity mixer, double now) {
        MachineKit kit = mixer.kit();
        return !kit.running() && !mixer.bottling() && mixer.paddleSpeed == 0 && kit.since(MachineKit.EV_REFUSE, now) >= 8
                && settled(mixer, "caps", kit.open(), now, 7) && settled(mixer, "flag", mixer.hasOutput(), now, 6) && tapResting(mixer, now);
    }

    /** The Centrifuge's rotor is at rest and no clip plays. */
    public static boolean still(LabMachines.Centrifuge c, double now) {
        MachineKit kit = c.kit();
        return c.speed == 0 && c.swing == 0 && !kit.running() && kit.since(MachineKit.EV_REFUSE, now) >= 8
                && settled(c, "drawer", kit.open(), now, 6) && settled(c, "flag", c.hasOutput(), now, 6);
    }

    /** A toggle controller has seen the state {@code bit} and finished playing its change (or, never run, the state is rest). */
    private static boolean settled(GeoAnimatable machine, String name, boolean bit, double now, int length) {
        AnimationController<?> c = machine.getAnimatableInstanceCache().getManagerForId(0).getAnimationControllers().get(name);
        if (!(c instanceof KitController<?> k) || k.lastBit == null) return !bit;
        return k.lastBit == bit && now - k.mark >= length;
    }

    private static boolean tapResting(SalineMixerBlockEntity mixer, double now) {
        AnimationController<?> c = mixer.getAnimatableInstanceCache().getManagerForId(0).getAnimationControllers().get("tap");
        return !(c instanceof KitController<?> k) || k.lastBit == null || !k.lastBit && now - k.mark >= 10;
    }

    /** A toggle controller of this machine is still moving back to rest. */
    static boolean toggling(GeoAnimatable machine, String name, double now, int length) {
        var manager = machine.getAnimatableInstanceCache().getManagerForId(0);
        AnimationController<?> c = manager.getAnimationControllers().get(name);
        return c instanceof KitController<?> k && KitController.toggleAwake(k, now, length);
    }

    /** The Pile's lamp says it is working (the twitch and the spark only then). */
    static boolean working(BlockEntity machine) {
        var state = machine.getBlockState();
        return state.hasProperty(MachineLamp.LAMP) && state.getValue(MachineLamp.LAMP) == MachineLamp.WORK;
    }

    // --- Life Support Cradle ----------------------------------------------------------------------------------

    /** Breathing needs a docked head and power, and waits for the dock clip to finish. */
    static boolean breathing(com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity c, double now) {
        return c.owner() != null && c.powered() && now - c.dockedAt() >= 24;
    }

    public static void cradle(com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity cradle, AnimatableManager.ControllerRegistrar controllers) {
        // the hose and the blanket: laid on at a dock, put back at an undock (the Compact jar's valve sits lower)
        add(controllers, new KitController<>(cradle, "dock", (c, now, k) -> {
            boolean compact = c.owner() != null ? c.compact() : c.lastCompact();
            KitController.Choice ch = KitController.toggle(k, c.owner() != null, now, compact ? "dock_c" : "dock", 24, compact ? "undock_c" : "undock", 20);
            // a dock seen happening plays from the server's dock time; a late arrival sees it done
            return ch;
        }));
        // the bellows breathes on the world's clock while a head is docked and powered; it finishes its breath when that stops
        add(controllers, new KitController<>(cradle, "breathe", (c, now, k) -> {
            double t = now % 60;
            if (breathing(c, now)) {
                k.lastBit = true;
                return new KitController.Choice("breathe", t);
            }
            if (k.lastBit == Boolean.TRUE && t < 54) return new KitController.Choice("breathe", t);
            k.lastBit = false;
            return null;
        }, "breathe"));
        add(controllers, new KitController<>(cradle, "refuse", (c, now, k) -> refusal(c.kit(), now, "refuse")));
    }

    public static boolean awake(com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity c, double now) {
        return c.owner() != null || c.kit().since(MachineKit.EV_REFUSE, now) < 8 || toggling(c, "dock", now, 24) || toggling(c, "breathe", now, 60);
    }

    // --- Body Printer ------------------------------------------------------------------------------------------

    /** The print's tick for the picture: the clock, held at 1,177 (the body in the open doorway) while it waits in the vat. */
    public static double printTick(com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity p, double now) {
        double t = p.kit().elapsed(now);
        if (t < 0) return -1;
        return p.waits() ? Math.min(t, 1177) : Math.min(t, 1200);
    }

    public static void printer(com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity printer, AnimatableManager.ControllerRegistrar controllers) {
        // the bar and the carriage: the print on its clock, else the head goes to the middle when all four materials are in
        add(controllers, new KitController<>(printer, "work", (p, now, k) -> {
            double t = printTick(p, now);
            if (t >= 0) {
                k.lastBit = null;
                return new KitController.Choice("print", t);
            }
            return KitController.toggle(k, p.materials() == 15, now, "loaded", 10, "unloaded", 10);
        }));
        // the door: opens for the delivery, stays open while the body stands in it, then shuts; else it cracks for a screen
        add(controllers, new KitController<>(printer, "door", (p, now, k) -> {
            double t = printTick(p, now);
            if (t >= 1160) return new KitController.Choice("deliver", t - 1160);
            if (t >= 0) {
                k.lastBit = null;
                k.released = Double.NaN;
                return null;
            }
            double since = p.kit().since(MachineKit.EV_FINISH, now);
            if (p.held() || since >= 0 && since < 2) {
                k.released = now;
                k.lastBit = null;
                return new KitController.Choice("deliver", 40);
            }
            // only a door that was held open shuts; a printer first seen idle starts closed
            if (!Double.isNaN(k.released) && now - k.released < 20) return new KitController.Choice("shut", now - k.released);
            k.released = Double.NaN;
            return KitController.toggle(k, p.kit().open(), now, "open", 6, "close", 6);
        }));
        // the bar's rotation: a refusal (a kick, or a bob while printing), and the sag of a stall
        add(controllers, new KitController<>(printer, "refuse", (p, now, k) -> {
            MachineKit kit = p.kit();
            double r = kit.since(MachineKit.EV_REFUSE, now);
            if (r >= 0 && r < 8) return new KitController.Choice(kit.running() ? "refuse_busy" : "refuse", r);
            double t = printTick(p, now);
            boolean stalled = kit.running() && !kit.advancing() && !p.waits() && t >= 160 && t < 1120;
            return KitController.toggle(k, stalled, now, "stall", 16, "unstall", 10);
        }));
    }

    public static boolean awake(com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity p, double now) {
        MachineKit kit = p.kit();
        return kit.running() || p.held() || kit.open() || p.materials() == 15 || kit.since(MachineKit.EV_REFUSE, now) < 8
                || kit.since(MachineKit.EV_FINISH, now) < 60 || toggling(p, "work", now, 10) || toggling(p, "door", now, 20) || toggling(p, "refuse", now, 16);
    }

    // --- Gene Splicer ------------------------------------------------------------------------------------------

    public static void splicer(LabMachines.Splicer splicer, AnimatableManager.ControllerRegistrar controllers) {
        // the needle, the wheel, the cassette: start, the chased work loop by the strand's band, the finish's hiccups or a stop
        add(controllers, new KitController<>(splicer, "main", (s, now, k) -> {
            MachineKit kit = s.kit();
            double e = kit.elapsed(now);
            if (kit.running()) {
                if (e < 10) return new KitController.Choice("start", e);
                float partial = (float) (now - Math.floor(now));
                double phase = s.phasePrev + (s.phase - s.phasePrev) * partial;
                return new KitController.Choice("work_" + s.band(), phase % 80);
            }
            double f = kit.since(MachineKit.EV_FINISH, now);
            int band = Math.floorMod(kit.eventParam(MachineKit.EV_FINISH), 3);
            if (f >= 0 && f < 60) return new KitController.Choice("finish_" + band, f);
            double c = kit.since(MachineKit.EV_OWN, now);
            if (c >= 0 && c < 26) return new KitController.Choice("stop", c);
            // a cassette waiting: the finish's last frame (the cassette up)
            if (s.hasOutput()) return new KitController.Choice("finish_" + s.band(), 60);
            return null;
        }));
        // the spools lift and turn when a gene is picked onto them (after the main controller: theirs wins)
        for (int i = 0; i < 3; i++) {
            final int n = i;
            add(controllers, new KitController<>(splicer, "pick_" + (i + 1), (s, now, k) -> {
                double t = now - s.pickedAt[n];
                return t >= 0 && t < 10 ? new KitController.Choice("pick_" + (n + 1), t) : null;
            }));
        }
        add(controllers, new KitController<>(splicer, "drawer", (s, now, k) -> KitController.toggle(k, s.kit().open(), now, "drawer_open", 5, "drawer_close", 6)));
        add(controllers, new KitController<>(splicer, "flag", (s, now, k) -> KitController.toggle(k, s.hasOutput(), now, "flag_up", 6, "flag_down", 6)));
        add(controllers, new KitController<>(splicer, "refuse", (s, now, k) -> refusal(s.kit(), now, "refuse")));
    }

    public static boolean awake(LabMachines.Splicer s, double now) {
        MachineKit kit = s.kit();
        return kit.running() || s.speed > 0 || kit.open() || s.hasOutput() || kit.since(MachineKit.EV_REFUSE, now) < 8
                || kit.since(MachineKit.EV_FINISH, now) < 60 || kit.since(MachineKit.EV_OWN, now) < 26
                || now - Math.max(s.pickedAt[0], Math.max(s.pickedAt[1], s.pickedAt[2])) < 10
                || toggling(s, "drawer", now, 6) || toggling(s, "flag", now, 6);
    }
}
