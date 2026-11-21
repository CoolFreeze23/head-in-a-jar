package com.coolfreeze23.headinajar.staff.second;

import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.staff.work.Detail;
import com.coolfreeze23.headinajar.staff.work.Shifts;
import com.coolfreeze23.headinajar.staff.work.Sites;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerPaths;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The collar's canister: the head drinks 1 mB every 6 seconds of its 500 (50 minutes), tops up from its Time Clock's
 * Broth tank each time it punches in, and dry it stands by (or walks back to the office for more).
 * <p>
 * Dry, a secondee does nothing else ({@link #holds}, from the staff brain's think): on the job it drops its work and
 * stands where it ran out for {@link #STAND_BY_TICKS} ("Out of broth: standing by."), on a Whistle detail likewise and
 * then it is sent home; then it walks back to its clock ("Out of broth: going back to the office.") and tops up there
 * from the tank, and the site's day goes on as before (its punch for the shift still stands). With the tank dry it
 * stands at the clock until there is broth. A secondee with no clock looks for one as any worker does. Team sync does
 * not grow meanwhile: none of this is on the job.
 */
public final class Broth {
    /** One mB every this many ticks while a head is seated: 500 mB last 60,000 ticks. */
    public static final int SIP_TICKS = 120;
    /** How long a dry secondee stands by where it ran out before it walks back to its clock. */
    public static final int STAND_BY_TICKS = 100;
    /** Saved flag (broth's bits are 8-11): the canister is dry, and the head sags. */
    public static final int FLAG_DRY = 1 << 8;
    /** How near its clock a dry secondee must be to top up from the tank (blocks, horizontal: the punch's reach). */
    static final double AT_CLOCK = TimeClockBlockEntity.PUNCH_REACH;

    private Broth() {
    }

    static void register() {
    }

    // --- the canister ---------------------------------------------------------------------------------------------

    /**
     * Each server tick of a collared host: a seated head sips 1 mB every {@link #SIP_TICKS} (staggered by entity id, so
     * any 60,000 ticks in a row take exactly 500 mB); the dry flag follows the canister. Nothing drinks while
     * secondments are off on the server (the heads stand by as they are).
     */
    public static void tick(Mob host, SecondeeData d, long time) {
        if (!d.seated()) return;
        if (d.canister > 0 && Secondees.enabled() && Math.floorMod(time + host.getId(), (long) SIP_TICKS) == 0) {
            int before = step(d.canister);
            d.canister--;
            if (step(d.canister) != before) SecondeeSync.changed(host);
        }
        boolean dry = d.canister <= 0;
        if (dry != dry(d)) {
            d.flags = dry ? d.flags | FLAG_DRY : d.flags & ~FLAG_DRY;
            d.dryTicks = 0;
            SecondeeSync.changed(host);
        } else if (dry && d.dryTicks < Integer.MAX_VALUE) {
            d.dryTicks++;
        }
    }

    /** The canister ran dry (the saved flag; the head sags). */
    public static boolean dry(SecondeeData d) {
        return (d.flags & FLAG_DRY) != 0;
    }

    /** The canister's sight-glass step: 0 full, 1 two thirds, 2 one third, 3 empty. */
    public static int step(int mB) {
        if (mB <= 0) return 3;
        if (mB * 3 <= SecondeeData.CANISTER) return 2;
        if (mB * 3 <= SecondeeData.CANISTER * 2) return 1;
        return 0;
    }

    // --- the clock --------------------------------------------------------------------------------------------------

    /** The clock has punched a worker in ({@code Shifts.punchedIn}): a secondee's canister tops up from the tank. */
    public static void punchedIn(TimeClockBlockEntity clock, Mob mob) {
        SecondeeData d = Secondees.data(mob);
        if (d == null || !d.seated()) return;
        BrothTank.topUp(clock, mob, d);
    }

    // --- the brain --------------------------------------------------------------------------------------------------

    /**
     * The staff brain's first say for a seconded head with a dry canister ({@code WorkerBrain.think}): true when broth
     * has the think (it stands by, or walks home and tops up), false to let the brain think as usual. Nothing for any
     * other worker.
     */
    public static boolean holds(Mob mob, WorkerData w) {
        SecondeeData d = Secondees.data(mob);
        if (d == null || !d.seated() || d.canister > 0 || !(mob.level() instanceof ServerLevel level)) return false;
        switch (w.state) {
            case ON_JOB -> {
                // it stops work where it is: the job ends (what it carries it keeps), the posting is free again
                TimeClockBlockEntity c = w.clock == null || !Workforce.clockHere(mob, w) || !WorkerPaths.loaded(level, w.clock.pos())
                        ? null : Sites.clock(level, w.clock);
                Shifts.sendBack(mob, w, c);
                d.dryTicks = 0;
                standBy(mob, w);
                return true;
            }
            case DETAIL -> {
                if (w.detail == null) return false; // walking home from a detail already: the detail's own way
                if (d.dryTicks < STAND_BY_TICKS) {
                    standBy(mob, w);
                    return true;
                }
                Detail.sendHome(mob, w);
                sayOnce(mob, w, "secondment.dry_home");
                return true;
            }
            case TO_CLOCK, OFF_SHIFT -> {
                return home(mob, w, d, level);
            }
            default -> {
                // awaiting a clock, off site, on notice, on the shuttle: the site's day brings it to a clock
                return false;
            }
        }
    }

    /** Dry on its site: stands by a while, then walks to its clock and tops up there. False: the shifts' turn. */
    private static boolean home(Mob mob, WorkerData w, SecondeeData d, ServerLevel level) {
        if (w.clock == null || !Workforce.clockHere(mob, w) || !WorkerPaths.loaded(level, w.clock.pos())) return false;
        TimeClockBlockEntity c = Sites.clock(level, w.clock);
        if (c == null || !c.has(mob.getUUID()) || !Workforce.inBox(c.getBlockPos(), mob.blockPosition())) return false;
        // in the middle of its punch: the punch tops it up
        if (c.punch(mob.getUUID(), Shifts.stamp(w.shift, c)) == TimeClockBlockEntity.Punch.PUNCHING) return false;
        Vec3 at = Vec3.atCenterOf(c.getBlockPos());
        double dx = mob.getX() - at.x, dz = mob.getZ() - at.z;
        boolean there = Math.sqrt(dx * dx + dz * dz) <= AT_CLOCK && Math.abs(mob.getY() - c.getBlockPos().getY()) < 2.5;
        if (there) {
            if (BrothTank.topUp(c, mob, d) > 0) return false; // back to its day: the punch for this shift still stands
            // the tank is dry too: it waits at the clock (off shift it is in the line anyway)
            if (w.state == WorkerData.State.OFF_SHIFT) return false;
            standBy(mob, w);
            face(mob, at);
            return true;
        }
        if (w.state == WorkerData.State.OFF_SHIFT) return false; // the line is where its shift starts; no walk before
        if (d.dryTicks < STAND_BY_TICKS) {
            standBy(mob, w);
            return true;
        }
        // a swimmer comes to the water by the clock (it tops up from within reach of it)
        BlockPos spot = Shifts.swimmer(mob) ? Shifts.wetSpot(mob, c) : Shifts.lineSpot(c, Math.max(0, c.roster().indexOf(mob.getUUID())));
        if (spot == null) {
            standBy(mob, w);
            return true;
        }
        WorkerPaths.Result r = WorkerPaths.go(mob, w, spot, 1.0, c.box(), false);
        if (r == WorkerPaths.Result.FAILED && WorkerPaths.givenUp(w)) {
            // no way to the clock ("Can't get there. Moving on.", said by the paths): it stands by where it is
            standBy(mob, w);
            return true;
        }
        sayOnce(mob, w, "secondment.dry_home");
        return true;
    }

    /** Stands still where it is, with the dry line over it once. */
    private static void standBy(Mob mob, WorkerData w) {
        WorkerPaths.stop(mob);
        sayOnce(mob, w, "secondment.dry");
    }

    /** A stop line said once until its status changes (as the shifts do it). */
    private static void sayOnce(Mob mob, WorkerData w, String key) {
        if (("staff.headinajar.say." + key).equals(w.status)) return;
        Workforce.say(mob, key);
    }

    private static void face(Mob mob, Vec3 at) {
        float yaw = (float) (Mth.atan2(at.z - mob.getZ(), at.x - mob.getX()) * Mth.RAD_TO_DEG) - 90;
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
    }
}
