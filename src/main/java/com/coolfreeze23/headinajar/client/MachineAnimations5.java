package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.LeechJarBlockEntity;
import com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity;
import com.coolfreeze23.headinajar.machine.KitController;
import com.coolfreeze23.headinajar.machine.KitController.Choice;
import com.coolfreeze23.headinajar.machine.MachineKit;
import software.bernie.geckolib.animation.AnimatableManager;

/** The fifth batch's clips and when they play (the rules of {@link MachineAnimations}). */
public final class MachineAnimations5 {
    private MachineAnimations5() {
    }

    // --- Neural Socket -----------------------------------------------------------------------------------------

    static final int CLAMP = 12, RELEASE = 12, REFUSE = 8;

    public static void socket(NeuralSocketBlockEntity socket, AnimatableManager.ControllerRegistrar controllers) {
        // the clamps follow the occupied bit (a late joiner sees them shut or open); a refusal hops or chatters them
        MachineAnimations.add(controllers, new KitController<>(socket, "claws", (s, now, k) -> {
            Choice held = KitController.toggle(k, s.occupied(), now, "clamp", CLAMP, "release", RELEASE);
            double r = s.kit().since(MachineKit.EV_REFUSE, now);
            if (r >= 0 && r < REFUSE) return new Choice(s.kit().eventParam(MachineKit.EV_REFUSE) == 1 ? "refuse_on" : "refuse", r);
            return held;
        }));
    }

    public static boolean awake(NeuralSocketBlockEntity s, double now) {
        return now - s.changedAt < Math.max(CLAMP, RELEASE) + 6 || s.kit().since(MachineKit.EV_REFUSE, now) < REFUSE;
    }

    // --- Leech Jar -----------------------------------------------------------------------------------------------

    static final int WAKE = 10, PRESS = 24, SETTLE = 12, SULK = 10, SAMPLE = 20, EMPTY = 24;

    public static void leech(LeechJarBlockEntity jar, AnimatableManager.ControllerRegistrar controllers) {
        // the leech: sulks while the jar is full; wakes and presses against the glass while a creature is beside it;
        // settles back onto the water when it goes
        MachineAnimations.add(controllers, new KitController<>(jar, "leech", (j, now, k) -> {
            if (j.full()) return new Choice("sulk", Math.min(SULK, now - j.fullAt));
            double t = now - j.wakeAt;
            if (j.side() >= 0) return t < WAKE ? new Choice("wake", t) : new Choice("press", (t - WAKE) % PRESS);
            return t < SETTLE ? new Choice("settle", t) : null;
        }, "press"));
        // the sampler: the nozzle pops and the crank turns once for a sample, twice when it is emptied
        MachineAnimations.add(controllers, new KitController<>(jar, "sampler", (j, now, k) -> {
            double feed = j.kit().since(LeechJarBlockEntity.EV_FEED, now), empty = j.kit().since(LeechJarBlockEntity.EV_EMPTY, now);
            if (empty >= 0 && empty < EMPTY && (feed < 0 || empty <= feed)) return new Choice("empty", empty);
            if (feed >= 0 && feed < SAMPLE) return new Choice("sample", feed);
            return null;
        }));
    }

    public static boolean awake(LeechJarBlockEntity j, double now) {
        MachineKit kit = j.kit();
        return j.side() >= 0 || now - j.wakeAt < SETTLE || j.full() && now - j.fullAt < SULK || j.carrier != j.carrierPrev
                || kit.since(LeechJarBlockEntity.EV_FEED, now) < SAMPLE || kit.since(LeechJarBlockEntity.EV_EMPTY, now) < EMPTY;
    }
}
