package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.PowerBlockEntities;
import com.coolfreeze23.headinajar.machine.KitController;
import com.coolfreeze23.headinajar.machine.KitController.Choice;
import com.coolfreeze23.headinajar.machine.MachineKit;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimatableManager;

/** The fourth batch's clips and code motion (the rules of {@link MachineAnimations}). */
public final class MachineAnimations4 {
    private MachineAnimations4() {
    }

    static double eio(double x) {
        x = Mth.clamp(x, 0, 1);
        return -(Math.cos(Math.PI * x) - 1) / 2;
    }

    // --- Relay Masts and the Signal Jammer ------------------------------------------------------------------------

    public static void station(PowerBlockEntities.Station station, AnimatableManager.ControllerRegistrar controllers) {
        // the Mk II's lamp stack rises over the dish while it carries a link (a late joiner sees it where it is)
        MachineAnimations.add(controllers, new KitController<>(station, "stage", (s, now, k) ->
                s.getBlockState().is(com.coolfreeze23.headinajar.registry.ModRegistry.RELAY_MAST_MK2.get())
                        ? KitController.toggle(k, s.carrying(), now, "stage_up", 10, "stage_down", 10) : null));
    }

    /**
     * The dish's yaw (degrees, world-relative) at {@code now}: on a change of aim, a 9 degree wind-up the wrong way over 5
     * ticks, a tick's hold, the swing at about 6 degrees a tick to 2 past, a 4 tick settle (the model's aim_turn).
     */
    public static float dishYaw(PowerBlockEntities.Station s, double now) {
        if (Float.isNaN(s.yaw)) return 0;
        double t = now - s.turnAt;
        double d = s.yawTo - s.yawFrom, sign = Math.signum(d);
        double swing = Math.max(4, Math.abs(d) / 6);
        double yaw;
        if (t < 0 || d == 0) yaw = s.yawTo;
        else if (t < 5) yaw = s.yawFrom - 9 * sign * eio(t / 5);
        else if (t < 6) yaw = s.yawFrom - 9 * sign;
        else if (t < 6 + swing) yaw = s.yawFrom - 9 * sign + (d + 11 * sign) * eio((t - 6) / swing);
        else if (t < 10 + swing) yaw = s.yawTo + 2 * sign * (1 - eio((t - 6 - swing) / 4));
        else yaw = s.yawTo;
        s.yaw = (float) yaw;
        return (float) yaw;
    }

    /** The dish's droop (degrees face-down): 20 over 16 ticks unpowered with a bounce; back up with an overshoot. */
    public static float dishDroop(PowerBlockEntities.Station s, double now) {
        return droop(s, now) + flinch(s, now);
    }

    /** A lightning strike near a mast in the rain: 18 degrees face-up in 2 ticks, creeping back over 30. */
    static float flinch(PowerBlockEntities.Station s, double now) {
        double t = s.kit().since(PowerBlockEntities.Station.EV_FLINCH, now);
        if (t < 0 || t >= 32) return 0;
        return (float) (t < 2 ? -18 * t / 2 : -18 * (1 - eio((t - 2) / 30)));
    }

    static float droop(PowerBlockEntities.Station s, double now) {
        double t = now - s.poweredAt;
        if (!s.powered()) {
            if (t < 16) return (float) (20 * (t / 16) * (t / 16));
            if (t < 20) return (float) (20 - 2 * Math.sin(Math.PI * (t - 16) / 4));
            return 20;
        }
        if (t < 0 || t >= 16) return 0;
        if (t < 12) return (float) (20 - 22 * eio(t / 12));
        return (float) (-2 * (1 - eio((t - 12) / 4)));
    }

    // --- Turret Pedestal -------------------------------------------------------------------------------------------

    static final String[] SHOT_CLIP = {"fire", "charge", "lunge", "pulse", "lob", "fire", null};
    static final int[] SHOT_LENGTH = {10, 20, 12, 10, 12, 10, 0};

    public static void turret(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity turret, AnimatableManager.ControllerRegistrar controllers) {
        // the barrel's kick for each kind of shot; a refusal's rattle
        MachineAnimations.add(controllers, new KitController<>(turret, "recoil", (t, now, k) -> {
            Choice r = MachineAnimations.refusal(t.kit(), now, "refuse");
            if (r != null) return r;
            double s = t.kit().since(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity.EV_SHOT, now);
            int kind = Mth.clamp(t.kit().eventParam(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity.EV_SHOT), 0, SHOT_CLIP.length - 1);
            return s >= 0 && SHOT_CLIP[kind] != null && s < SHOT_LENGTH[kind] ? new Choice(SHOT_CLIP[kind], s) : null;
        }));
    }

    /** The zombie's headbutt (pitch down 20 in 3 ticks, held 1, back over 6) and the evoker's nod, on top of the aim. */
    public static float turretPitchExtra(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity t, double now) {
        double s = t.kit().since(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity.EV_SHOT, now);
        int kind = t.kit().eventParam(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity.EV_SHOT);
        if (kind == com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity.LUNGE && s >= 0 && s < 10) return (float) (s < 3 ? -20 * s / 3 : s < 4 ? -20 : -20 * (1 - eio((s - 4) / 6)));
        if (kind == com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity.NOD && s >= 0 && s < 12) return (float) (-8 * Math.sin(Math.PI * s / 6));
        double r = t.kit().since(MachineKit.EV_REFUSE, now);
        return 0;
    }

    /** The refusal's "no": the yaw shaken 15 degrees, twice over 8 ticks. */
    public static float turretYawExtra(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity t, double now) {
        double r = t.kit().since(MachineKit.EV_REFUSE, now);
        return r >= 0 && r < 8 ? (float) (15 * Math.sin(2 * Math.PI * r / 4)) : 0;
    }

    public static boolean awake(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity t, double now) {
        // a mounted head is always on watch (and its jar is drawn in the cradle's frame, which only the moving model gives)
        return !t.head().isEmpty() || t.kit().since(MachineKit.EV_REFUSE, now) < 8;
    }

    // --- Body Locker (the Cloakroom) ---------------------------------------------------------------------------------

    static final String[] LOCKER_REFUSE = {"refuse_empty", "refuse_full", "refuse_ticket"};

    public static void locker(com.coolfreeze23.headinajar.block.BodyLockerBlockEntity locker, AnimatableManager.ControllerRegistrar controllers) {
        // the shutter and the ticket: check-in rolls it down, check-out (the body already out) rolls it up; a refusal by state
        MachineAnimations.add(controllers, new KitController<>(locker, "shutter", (l, now, k) -> {
            MachineKit kit = l.kit();
            double r = kit.since(MachineKit.EV_REFUSE, now);
            if (r >= 0 && r < 8) return new Choice(LOCKER_REFUSE[Mth.clamp(kit.eventParam(MachineKit.EV_REFUSE), 0, 2)], r);
            double in = kit.since(com.coolfreeze23.headinajar.block.BodyLockerBlockEntity.EV_CHECKIN, now), out = kit.since(com.coolfreeze23.headinajar.block.BodyLockerBlockEntity.EV_CHECKOUT, now);
            if (in >= 0 && in < 30 && (out < 0 || in < out)) return new Choice("checkin", in);
            if (out >= 0 && out < 24) return new Choice("checkout", out);
            return new Choice(l.shownStored() != 0 ? "held_full" : "held_empty", 0);
        }));
    }

    /** The portrait shows from the check-in's tick 18 while something is held, and until the check-out's tick 18. */
    public static boolean portrait(com.coolfreeze23.headinajar.block.BodyLockerBlockEntity l, double now) {
        double in = l.kit().since(com.coolfreeze23.headinajar.block.BodyLockerBlockEntity.EV_CHECKIN, now), out = l.kit().since(com.coolfreeze23.headinajar.block.BodyLockerBlockEntity.EV_CHECKOUT, now);
        if (out >= 0 && out < 18 && (in < 0 || out < in)) return true;
        return l.shownStored() != 0 && !(in >= 0 && in < 18);
    }

    public static boolean awake(com.coolfreeze23.headinajar.block.BodyLockerBlockEntity l, double now) {
        MachineKit kit = l.kit();
        return kit.since(com.coolfreeze23.headinajar.block.BodyLockerBlockEntity.EV_CHECKIN, now) < 30 || kit.since(com.coolfreeze23.headinajar.block.BodyLockerBlockEntity.EV_CHECKOUT, now) < 24 || kit.since(MachineKit.EV_REFUSE, now) < 8
                || l.shownStored() != 0;
    }

    public static boolean awake(PowerBlockEntities.Station s, double now) {
        if (!s.mast()) return s.speed != 0;
        // a still dish (turned, or drooped) is posed by code and bakes; the Mk II's raised stage is a held clip
        boolean mk2 = s.getBlockState().is(com.coolfreeze23.headinajar.registry.ModRegistry.RELAY_MAST_MK2.get());
        // the Mk II's lamp glows through a render layer (not baked), so a powered Mk II is drawn by its renderer
        return mk2 && (s.carrying() || s.powered()) || now - s.turnAt < 60 || s.kit().since(PowerBlockEntities.Station.EV_FLINCH, now) < 32 || now - s.poweredAt < 20
                || MachineAnimations.toggling(s, "stage", now, 10) || s.kit().since(MachineKit.EV_REFUSE, now) < 8;
    }
}
