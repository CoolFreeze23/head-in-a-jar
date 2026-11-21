package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.item.HardHatItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.Band;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffBlocks;
import com.coolfreeze23.headinajar.staff.StaffCards;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.StaffShake;
import com.coolfreeze23.headinajar.staff.StaffTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A worker's day at its site: awaiting a clock, walking to it, the queue at the punch, taking a card from the rack, the
 * loose line by the clock off shift, finding its way back from off site, and the shift's end. The clock's side (the
 * punch itself, the dawn's pay, the roster) is {@link TimeClockBlockEntity}'s.
 * <p>
 * Shifts read the Overworld's time of day in every dimension: Day 1,000-11,000, Night 13,000-23,000 (both end before
 * the sun burns), Redstone while the clock is powered. A worker with no card, off shift or awaiting does no pathing at
 * all; one in line stands facing the clock with its brain off until its shift starts.
 */
public final class Shifts {
    public static final int DAY_START = 1000, DAY_END = 11000, NIGHT_START = 13000, NIGHT_END = 23000;
    /** Ticks after its shift's start a worker counts as late (the bell rings for it). */
    public static final int LATE_TICKS = 600;
    /** A worker carrying something when its shift ends has this long to finish the trip. */
    public static final int CARRY_GRACE = 600;
    /** A worker off site within this far of its site's box walks back. */
    public static final int WALK_BACK = 24;
    /** A worker with no card looks at the rack again this often. */
    public static final int RACK_LOOK_TICKS = 40;
    /** Within this far of its clock a worker joins the queue at the punch. */
    static final double QUEUE_AT = 4.5;
    /** The Cleric's blessing at the punch: 2 hearts. */
    static final float CLERIC_HEAL = 4;
    /** "Pleasant to work with" (and the Nitwit): morale at dawn. */
    static final int PLEASANT = 2;

    /**
     * The line off shift, in the clock's own terms (blocks to its right, blocks out in front of it): every spot within 2
     * blocks of the clock (the awning). The cell straight in front is the punch's and stays clear.
     */
    private static final int[][] LINE = {{0, 2}, {-1, 1}, {1, 1}, {-2, 0}, {2, 0}, {-1, -1}, {1, -1}, {0, -2}, {-1, 0}, {1, 0}};

    /** Where each worker last asked to walk (its failed tries count for that spot only). */
    private static final Map<Mob, BlockPos> TARGET = new WeakHashMap<>();
    /** Thinks spent on the last stretch to a spot (by the move control, once the path is within reach). */
    private static final Map<Mob, Integer> STRETCH = new WeakHashMap<>();
    private static final int STRETCH_THINKS = 4;

    private Shifts() {
    }

    // --- the time -----------------------------------------------------------------------------------------------------

    /** The Overworld's time of day (0-23,999) as this clock reads it. */
    public static long time(TimeClockBlockEntity c) {
        if (c.timeOverride >= 0) return c.timeOverride % 24000L;
        Level level = c.getLevel();
        ServerLevel ow = level != null && level.getServer() != null ? level.getServer().overworld() : null;
        return Math.floorMod(ow != null ? ow.getDayTime() : level == null ? 0 : level.getDayTime(), 24000L);
    }

    /** Whether a shift is running at this clock now. */
    public static boolean on(WorkerData.Shift shift, TimeClockBlockEntity c) {
        long t = time(c);
        return switch (shift) {
            case DAY -> t >= DAY_START && t < DAY_END;
            case NIGHT -> t >= NIGHT_START && t < NIGHT_END;
            case REDSTONE -> c.powered();
        };
    }

    /** One shift told from the next (a punch is good for one): the day and the shift, or when the power came on. */
    public static long stamp(WorkerData.Shift shift, TimeClockBlockEntity c) {
        long day = c.getLevel() == null ? 0 : Staff.day(c.getLevel());
        return switch (shift) {
            case DAY -> day * 4 + 1;
            case NIGHT -> day * 4 + 2;
            case REDSTONE -> -1 - c.poweredAt();
        };
    }

    /** Ticks since the shift started (late after {@link #LATE_TICKS}). */
    public static long sinceStart(WorkerData.Shift shift, TimeClockBlockEntity c) {
        return switch (shift) {
            case DAY -> time(c) - DAY_START;
            case NIGHT -> time(c) - NIGHT_START;
            case REDSTONE -> c.getLevel() == null ? 0 : c.getLevel().getGameTime() - c.poweredAt();
        };
    }

    /** Whether it burns by day (its kind's quirk) and wears no Hard Hat: no Day shift for it. */
    public static boolean dayRefused(Mob mob) {
        WorkTable.Spec spec = WorkTable.of(mob);
        return spec != null && spec.quirk("burns") && !HardHatItem.wears(mob);
    }

    // --- the think ----------------------------------------------------------------------------------------------------

    /** A think for a worker that is not on the job, on a detail or on notice. */
    static void think(Mob mob, WorkerData d) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        switch (d.state) {
            case AWAITING -> awaiting(mob, d, level);
            case OFF_SITE -> offSite(mob, d, level);
            case TO_CLOCK -> toClock(mob, d, level);
            case OFF_SHIFT -> offShift(mob, d, level);
            default -> {
            }
        }
    }

    private static void awaiting(Mob mob, WorkerData d, ServerLevel level) {
        if (d.clock != null) {
            set(mob, d, WorkerData.State.TO_CLOCK, "waiting");
            return;
        }
        if (join(mob, d, level)) return;
        // the last thing it said stays its status (a stop line), else it is awaiting assignment
        if (d.status == null || !d.status.startsWith("staff.headinajar.say.")) d.status = status("awaiting");
    }

    /** Joins the nearest clock of its employer's with room whose site it stands in. */
    static boolean join(Mob mob, WorkerData d, ServerLevel level) {
        for (SiteIndex.Site s : SiteIndex.get(level).around(mob.blockPosition(), d.employer)) {
            TimeClockBlockEntity c = Sites.clock(level, net.minecraft.core.GlobalPos.of(level.dimension(), s.pos()));
            if (c == null || !d.employer.equals(c.owner())) continue;
            if (c.has(mob.getUUID()) || c.room() > 0) {
                WorkerPaths.stop(mob);
                return c.join(mob);
            }
        }
        return false;
    }

    private static void offSite(Mob mob, WorkerData d, ServerLevel level) {
        if (d.clock == null) {
            if (join(mob, d, level)) return;
            standAndSay(mob, d, "off_site");
            return;
        }
        if (!Workforce.clockHere(mob, d)) {
            standAndSay(mob, d, "off_site");
            return;
        }
        if (!level.isLoaded(d.clock.pos())) {
            standAndSay(mob, d, "wheres_my_clock");
            return;
        }
        if (!(level.getBlockEntity(d.clock.pos()) instanceof TimeClockBlockEntity c) || !c.has(mob.getUUID())) {
            d.clock = null;
            d.dropTask();
            return;
        }
        BlockPos at = mob.blockPosition();
        if (Workforce.inBox(c.getBlockPos(), at)) {
            set(mob, d, WorkerData.State.TO_CLOCK, "waiting");
            return;
        }
        if (outside(c.getBlockPos(), mob.position()) > WALK_BACK) {
            standAndSay(mob, d, "off_site");
            return;
        }
        d.status = status("off_site");
        if (walk(mob, d, c.front(), c.box().inflate(WALK_BACK + 2)) && !Workforce.inBox(c.getBlockPos(), mob.blockPosition())) {
            standAndSay(mob, d, "off_site");
        }
    }

    private static void toClock(Mob mob, WorkerData d, ServerLevel level) {
        TimeClockBlockEntity c = clockOrLeave(mob, d, level);
        if (c == null) return;
        catchUpDawn(c, mob);
        if (!on(d.shift, c)) {
            if (toLine(mob, d, c)) {
                set(mob, d, WorkerData.State.OFF_SHIFT, "off_shift");
                face(mob, c);
            } else {
                d.status = status("waiting");
            }
            return;
        }
        if (d.shift == WorkerData.Shift.DAY && dayRefused(mob)) {
            sayOnce(mob, d, "night_only");
            toLine(mob, d, c);
            return;
        }
        UUID id = mob.getUUID();
        long stamp = stamp(d.shift, c);
        switch (c.punch(id, stamp)) {
            case DONE -> afterPunch(mob, d, c, level);
            case PUNCHING -> {
                WorkerPaths.stop(mob);
                face(mob, c);
                d.status = status("waiting");
            }
            case QUEUED -> {
                d.status = status("waiting");
                if (walk(mob, d, queueSpot(c, c.queuePlace(id)), c.box())) face(mob, c);
            }
            case NONE -> {
                d.status = status("waiting");
                if (horizontal(mob.position(), Vec3.atCenterOf(c.getBlockPos())) <= QUEUE_AT) {
                    c.queuePunch(id, stamp, sinceStart(d.shift, c) > LATE_TICKS);
                    if (walk(mob, d, queueSpot(c, c.queuePlace(id)), c.box())) face(mob, c);
                } else {
                    walk(mob, d, queueSpot(c, Math.max(0, c.queuePlace(id))), c.box());
                }
            }
        }
    }

    /** Punched in: a card (its own first, then the rack's), or the line with the reason it is not working. */
    private static void afterPunch(Mob mob, WorkerData d, TimeClockBlockEntity c, ServerLevel level) {
        WorkTable.Spec spec = WorkTable.of(mob);
        if (spec != null && !spec.card()) {
            // works with no card (a cat, a witch, a bee): on duty in the line
            toLine(mob, d, c);
            d.status = status("on_job");
            return;
        }
        if (unpaid(mob, level) && !payLate(c, mob)) {
            sayOnce(mob, d, "unpaid");
            toLine(mob, d, c);
            return;
        }
        if (!d.card.isEmpty() && Order.card(d.card) != null) {
            startJob(mob, d, -1);
            return;
        }
        if (c.mayLook(mob.getUUID(), RACK_LOOK_TICKS)) {
            int slot = pick(mob, c);
            if (slot >= 0) {
                c.take(mob.getUUID(), slot);
                startJob(mob, d, slot);
                return;
            }
            sayOnce(mob, d, "no_card");
        }
        toLine(mob, d, c);
    }

    /**
     * The rack posting this worker takes: top to bottom, the first card it can do among those with the fewest staff on
     * them (a card is a posting: it stays in the rack, and many can work it). -1: none it can do.
     */
    public static int pick(Mob mob, TimeClockBlockEntity c) {
        int best = -1, fewest = Integer.MAX_VALUE;
        for (int i = 0; i < TimeClockBlockEntity.RACK; i++) {
            Order.Card card = Order.card(c.rack().getStackInSlot(i));
            if (card == null || com.coolfreeze23.headinajar.staff.work.jobs.Jobs.refusal(mob, card) != null) continue;
            int n = c.takers(i);
            if (n < fewest) {
                fewest = n;
                best = i;
            }
        }
        return best;
    }

    private static void startJob(Mob mob, WorkerData d, int slot) {
        d.dropTask();
        d.rackSlot = slot;
        d.state = WorkerData.State.ON_JOB;
        d.status = status("on_job");
        TARGET.remove(mob);
        WorkerLooks.changed(mob);
    }

    private static void offShift(Mob mob, WorkerData d, ServerLevel level) {
        TimeClockBlockEntity c = clockOrLeave(mob, d, level);
        if (c == null) return;
        catchUpDawn(c, mob);
        if (on(d.shift, c)) set(mob, d, WorkerData.State.TO_CLOCK, "waiting");
        // otherwise: brain off, in line, facing the clock; no paths
    }

    /** On the job: whether its shift still runs (false: it has been sent back to the clock, or is off site). */
    static boolean stillOn(Mob mob, WorkerData d) {
        if (!(mob.level() instanceof ServerLevel level)) return true;
        if (d.clock == null || !Workforce.clockHere(mob, d)) {
            leaveSite(mob, d, null);
            return false;
        }
        if (!level.isLoaded(d.clock.pos())) return true; // its card reads as gone; the job stands it down
        if (!(level.getBlockEntity(d.clock.pos()) instanceof TimeClockBlockEntity c) || !c.has(mob.getUUID())) {
                siteClosed(mob, d);
            return false;
        }
        if (!Workforce.inBox(c.getBlockPos(), mob.blockPosition())) {
                leaveSite(mob, d, c);
            return false;
        }
        boolean on = on(d.shift, c) && !unpaid(mob, level) && !(d.shift == WorkerData.Shift.DAY && dayRefused(mob));
        if (on) {
            c.stillOn(mob.getUUID());
            return true;
        }
        // the shift is over: a trip in hand is finished first, for a while
        if (com.coolfreeze23.headinajar.staff.work.jobs.Jobs.busy(mob, d) && level.getGameTime() - c.overSince(mob.getUUID()) < CARRY_GRACE) return true;
        sendBack(mob, d, c);
        return false;
    }

    /** Off the job and back to the clock (the shift's end, a Recall). A personal card stays in its hand. */
    public static void sendBack(Mob mob, WorkerData d, TimeClockBlockEntity c) {
        if (c != null) {
            c.take(mob.getUUID(), -1);
            c.stillOn(mob.getUUID());
        }
        com.coolfreeze23.headinajar.staff.work.jobs.Jobs.end(mob, d);
        d.dropTask();
        WorkerPaths.stop(mob);
        set(mob, d, WorkerData.State.TO_CLOCK, "waiting");
    }

    private static void leaveSite(Mob mob, WorkerData d, TimeClockBlockEntity c) {
        if (c != null) c.take(mob.getUUID(), -1);
        com.coolfreeze23.headinajar.staff.work.jobs.Jobs.end(mob, d);
        d.dropTask();
        set(mob, d, WorkerData.State.OFF_SITE, "off_site");
    }

    /** Its clock is gone: off site with no clock, to join one within 16 by itself. */
    public static void siteClosed(Mob mob, WorkerData d) {
        d.clock = null;
        com.coolfreeze23.headinajar.staff.work.jobs.Jobs.end(mob, d);
        d.dropTask();
        WorkerPaths.stop(mob);
        set(mob, d, WorkerData.State.OFF_SITE, "off_site");
    }

    /**
     * Its clock, when it stands in the clock's site and the clock is there; else it is set off site (a clock in another
     * dimension, gone, or the worker outside the box) or told its clock is out of reach (not loaded), and null.
     */
    private static TimeClockBlockEntity clockOrLeave(Mob mob, WorkerData d, ServerLevel level) {
        if (d.clock == null || !Workforce.clockHere(mob, d)) {
            set(mob, d, WorkerData.State.OFF_SITE, "off_site");
            return null;
        }
        if (!level.isLoaded(d.clock.pos())) {
            standAndSay(mob, d, "wheres_my_clock");
            return null;
        }
        if (!(level.getBlockEntity(d.clock.pos()) instanceof TimeClockBlockEntity c) || !c.has(mob.getUUID())) {
            siteClosed(mob, d);
            return null;
        }
        if (!Workforce.inBox(c.getBlockPos(), mob.blockPosition())) {
            set(mob, d, WorkerData.State.OFF_SITE, "off_site");
            return null;
        }
        return c;
    }

    // --- the line and the queue ---------------------------------------------------------------------------------------

    /** Walks to its spot in the line; true when it is there (or as near as it will get). */
    private static boolean toLine(Mob mob, WorkerData d, TimeClockBlockEntity c) {
        boolean there = walk(mob, d, lineSpot(c, Math.max(0, c.roster().indexOf(mob.getUUID()))), c.box());
        if (there) face(mob, c);
        return there;
    }

    /** A worker's spot in the line by its place on the roster: the first standable one from its own on. */
    public static BlockPos lineSpot(TimeClockBlockEntity c, int index) {
        Level level = c.getLevel();
        BlockPos clock = c.getBlockPos();
        Direction out = c.facing(), right = out.getClockWise();
        for (int k = 0; k < LINE.length; k++) {
            int[] o = LINE[(index + k) % LINE.length];
            BlockPos p = clock.relative(right, o[0]).relative(out, o[1]);
            BlockPos s = standable(level, p);
            if (s != null) return s;
        }
        return c.front();
    }

    /** Where a worker stands to punch in: the head of the queue's spot, in front of the clock. */
    public static BlockPos punchSpot(TimeClockBlockEntity c) {
        return queueSpot(c, 0);
    }

    /** Where the {@code place}th in the queue waits (0: at the punch; the punching one keeps the front). */
    static BlockPos queueSpot(TimeClockBlockEntity c, int place) {
        BlockPos p = c.front().relative(c.facing(), Math.max(0, place));
        BlockPos s = Workforce.inBox(c.getBlockPos(), p) ? standable(c.getLevel(), p) : null;
        return s != null ? s : lineSpot(c, place);
    }

    /** {@code p} or the cell above or below it, wherever a creature could stand; null for none (or not loaded). */
    static BlockPos standable(Level level, BlockPos p) {
        if (level == null) return null;
        for (int dy : new int[]{0, -1, 1}) {
            BlockPos q = p.above(dy);
            if (!level.isLoaded(q)) continue;
            if (level.getBlockState(q).getCollisionShape(level, q).isEmpty() && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()
                    && !level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty() && level.getFluidState(q).isEmpty()) {
                return q;
            }
        }
        return null;
    }

    /**
     * Walks towards a spot (rationed paths, every node inside {@code box}); true when it is there, or when it has given
     * up on that spot ("Can't get there. Moving on.") and stands where it is.
     */
    static boolean walk(Mob mob, WorkerData d, BlockPos spot, AABB box) {
        TARGET.put(mob, spot);
        if (near(mob, spot)) {
            STRETCH.remove(mob);
            if (!mob.getNavigation().isDone() || Movers.moving(mob)) WorkerPaths.stop(mob);
            return true;
        }
        if (WorkerPaths.arrived(mob, spot)) {
            // the path has brought it within reach of the spot: the last stretch straight, by its own move control (a
            // walker's), a few thinks at most; then it is as near as it will get (a colleague may be standing there)
            String family = Movers.family(mob);
            int tries = STRETCH.merge(mob, 1, Integer::sum);
            if (tries > STRETCH_THINKS || !(Movers.WALK.equals(family) || Movers.CLIMB.equals(family) || Movers.LAVA.equals(family))) {
                STRETCH.remove(mob);
                WorkerPaths.stop(mob);
                return true;
            }
            mob.getMoveControl().setWantedPosition(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, Movers.walk(mob));
            return false;
        }
        STRETCH.remove(mob);
        WorkerPaths.Result r = WorkerPaths.go(mob, d, spot, 1.0, box, false);
        // given up on that spot ("Can't get there. Moving on.", said by the paths): it stands where it is
        return r == WorkerPaths.Result.FAILED && WorkerPaths.givenUp(d);
    }

    static boolean near(Mob mob, BlockPos spot) {
        double dx = mob.getX() - (spot.getX() + 0.5), dz = mob.getZ() - (spot.getZ() + 0.5);
        return dx * dx + dz * dz <= 0.8 * 0.8 && Math.abs(mob.getY() - spot.getY()) < 1.5;
    }

    /** Turns it to face the clock (it stays so: nothing turns it while it stands). */
    static void face(Mob mob, TimeClockBlockEntity c) {
        Vec3 at = Vec3.atCenterOf(c.getBlockPos());
        float yaw = (float) (Mth.atan2(at.z - mob.getZ(), at.x - mob.getX()) * Mth.RAD_TO_DEG) - 90;
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        mob.yRotO = yaw;
    }

    /** How far a point is outside a site's box (0 inside). */
    static double outside(BlockPos clock, Vec3 p) {
        int r = Workforce.radius();
        double dx = Math.max(0, Math.abs(p.x - (clock.getX() + 0.5)) - r - 0.5);
        double dz = Math.max(0, Math.abs(p.z - (clock.getZ() + 0.5)) - r - 0.5);
        double dy = Math.max(0, Math.abs(p.y - clock.getY()) - Workforce.SITE_HEIGHT);
        return Math.sqrt(dx * dx + dz * dz + dy * dy);
    }

    // --- pay ------------------------------------------------------------------------------------------------------------

    /** Whether it has not been paid today (a species that takes no wage is always paid). */
    public static boolean unpaid(Mob mob, Level level) {
        StaffFile f = Staff.of(mob);
        return f != null && f.staff() && f.paidThrough < Staff.day(level) && !StaffTable.wage(mob.getType()).none();
    }

    /**
     * One dawn for a worker on this clock's roster (the same rules as a head on a plinth: one dawn however many days it
     * was away): its wage from the Pay Drawer, its morale up or down, the unpaid memo to its employer; "pleasant to work
     * with" (and the Nitwit) +2; villager staff restock their trades.
     */
    public static void dawn(TimeClockBlockEntity c, Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        WorkerData d = Workforce.data(mob);
        StaffFile f = Staff.of(mob);
        if (d == null || f == null || !f.staff() || d.state == WorkerData.State.NOTICE) return;
        long day = Staff.day(level);
        if (f.lastDawn >= day) return;
        f.lastDawn = day;
        StaffTable.Wage wage = StaffTable.wage(mob.getType());
        boolean paid = f.paidThrough >= day || wage.none() || c.takeWage(wage);
        if (paid) {
            f.paidThrough = Math.max(f.paidThrough, day);
            StaffBlocks.adjust(f, StaffBlocks.PAID);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 5, 0.25, 0.2, 0.25, 0);
        } else {
            StaffBlocks.adjust(f, StaffBlocks.UNPAID);
            level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 6, 0.2, 0.1, 0.2, 0.01);
            ServerPlayer employer = level.getServer().getPlayerList().getPlayer(d.employer);
            if (employer != null) com.coolfreeze23.headinajar.clarity.Memos.send(employer, com.coolfreeze23.headinajar.clarity.Memos.Memo.UNPAID_DAWN, Staff.name(f));
        }
        WorkTable.Spec spec = WorkTable.of(mob);
        if (spec != null && (spec.quirk("pleasant") || spec.quirk("nitwit"))) StaffBlocks.adjust(f, PLEASANT);
        f.lowDawns = f.morale < Band.DISENGAGED_AT ? f.lowDawns + 1 : 0;
        mob.setData(ModRegistry.STAFF, f);
        if (mob instanceof Villager v) v.restock();
    }

    /** A worker that missed its clock's dawn (not loaded then) has it now, if its clock has had today's. */
    static void catchUpDawn(TimeClockBlockEntity c, Mob mob) {
        StaffFile f = Staff.of(mob);
        if (f != null && f.staff() && c.getLevel() != null && f.lastDawn < Staff.day(c.getLevel()) && c.lastDawn >= Staff.day(c.getLevel())) dawn(c, mob);
    }

    /** An unpaid worker at the clock takes today's wage if it is in the drawer now. */
    static boolean payLate(TimeClockBlockEntity c, Mob mob) {
        StaffFile f = Staff.of(mob);
        if (f == null || !(mob.level() instanceof ServerLevel level)) return false;
        if (!c.takeWage(StaffTable.wage(mob.getType()))) return false;
        f.paidThrough = Staff.day(level);
        mob.setData(ModRegistry.STAFF, f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 5, 0.25, 0.2, 0.25, 0);
        return true;
    }

    // --- the punch ------------------------------------------------------------------------------------------------------

    /** The clock has punched it in: the advancement (its first punch), and a Cleric on site heals it 2 hearts. */
    public static void punchedIn(TimeClockBlockEntity c, Mob mob) {
        WorkerData d = Workforce.data(mob);
        if (d == null || !(mob.level() instanceof ServerLevel level)) return;
        if (!d.has(WorkerData.FLAG_PUNCHED)) {
            d.set(WorkerData.FLAG_PUNCHED);
            Advance.grant(level.getServer().getPlayerList().getPlayer(d.employer), "clocking_in");
        }
        if (mob.getHealth() < mob.getMaxHealth() && clericOnSite(c, level)) {
            mob.heal(CLERIC_HEAL);
            level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 3, 0.3, 0.2, 0.3, 0);
        }
    }

    private static boolean clericOnSite(TimeClockBlockEntity c, ServerLevel level) {
        for (UUID id : c.roster()) {
            Entity e = level.getEntity(id);
            if (!(e instanceof Mob m) || !m.isAlive()) continue;
            WorkerData d = Workforce.data(m);
            WorkTable.Spec spec = WorkTable.of(m);
            if (d != null && spec != null && spec.quirk("cleric") && d.state != WorkerData.State.OFF_SITE && d.state != WorkerData.State.DETAIL
                    && Workforce.inBox(c.getBlockPos(), m.blockPosition())) return true;
        }
        return false;
    }

    // --- orders from the clock's screen ---------------------------------------------------------------------------------

    /** Recall (the Roster): drop the card (a personal one back into the rack) and come back to the clock. */
    public static void recall(ServerPlayer player, TimeClockBlockEntity c, Mob mob) {
        WorkerData d = Workforce.data(mob);
        if (d == null) return;
        if (d.state == WorkerData.State.DETAIL) {
            Refusals.deny(player, StaffShake.at(mob), "clock.recall_detail", Workforce.name(mob));
            return;
        }
        if (d.state == WorkerData.State.NOTICE) {
            Refusals.deny(player, StaffShake.at(mob), "staff.already_let_go", Workforce.name(mob));
            return;
        }
        if (!d.card.isEmpty()) {
            net.minecraft.world.item.ItemStack card = d.card;
            d.card = net.minecraft.world.item.ItemStack.EMPTY;
            if (!c.returnCard(card)) Workforce.give(player, card);
        }
        sendBack(mob, d, c);
        if (!Workforce.inBox(c.getBlockPos(), mob.blockPosition())) set(mob, d, WorkerData.State.OFF_SITE, "off_site");
        player.displayClientMessage(Component.translatable("message.headinajar.clock.recalled", Workforce.name(mob)), true);
        mob.level().playSound(null, c.getBlockPos(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 1.1f);
    }

    /** The Shifts tab: a worker's shift; undead to Day without a Hard Hat is refused (Night Shift does not do days). */
    public static void setShift(ServerPlayer player, TimeClockBlockEntity c, Mob mob, WorkerData.Shift shift) {
        WorkerData d = Workforce.data(mob);
        if (d == null || d.shift == shift) return;
        if (shift == WorkerData.Shift.DAY && dayRefused(mob)) {
            Refusals.deny(player, c, "shift.no_hard_hat");
            return;
        }
        d.shift = shift;
        c.unpunch(mob.getUUID());
        mob.level().playSound(null, c.getBlockPos(), net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.4f);
    }

    /**
     * A new given name typed on the Roster for the worker with this entity id: only from a clock's screen the player has
     * open, for a worker on that clock's roster, by its employer or someone they trust. It is the file's given name,
     * never a name tag; a blank keeps the old one.
     */
    public static void rename(ServerPlayer player, int entityId, String name) {
        if (!(player.containerMenu instanceof com.coolfreeze23.headinajar.menu.TimeClockMenu menu) || menu.clock() == null) return;
        TimeClockBlockEntity c = menu.clock();
        Entity e = player.level().getEntity(entityId);
        if (!(e instanceof Mob mob) || !Workforce.isWorker(mob) || !c.has(mob.getUUID())) return;
        if (!Workforce.mayOrder(mob, player) || !c.trusts(player)) {
            Refusals.deny(player, c, "clock.untrusted", c.ownerName());
            return;
        }
        StaffFile f = Staff.of(mob);
        if (f == null) return;
        String clean = StaffCards.clean(name);
        if (!clean.isEmpty() && !clean.equals(f.customName)) {
            f.customName = clean;
            f.note("renamed", Staff.day(player.level()), player.getGameProfile().getName());
            mob.setData(ModRegistry.STAFF, f);
            WorkerLooks.changed(mob);
        }
        player.displayClientMessage(Component.translatable("screen.headinajar.file.renamed", Staff.name(f)), true);
    }

    // --- small things ---------------------------------------------------------------------------------------------------

    private static void set(Mob mob, WorkerData d, WorkerData.State state, String status) {
        boolean changed = d.state != state;
        d.state = state;
        d.status = status(status);
        if (changed) {
            TARGET.remove(mob);
            d.pathFails = 0;
            WorkerLooks.changed(mob);
        }
    }

    static String status(String key) {
        return "staff.headinajar.status." + key;
    }

    /** A stop line, said once until its status changes (so a line of idle staff is not a choir). */
    private static void sayOnce(Mob mob, WorkerData d, String key) {
        if (("staff.headinajar.say." + key).equals(d.status)) return;
        Workforce.say(mob, key);
    }

    private static void standAndSay(Mob mob, WorkerData d, String key) {
        if (!mob.getNavigation().isDone()) WorkerPaths.stop(mob);
        if (d.state != WorkerData.State.OFF_SITE && !"wheres_my_clock".equals(key)) set(mob, d, WorkerData.State.OFF_SITE, "off_site");
        sayOnce(mob, d, key);
    }

    static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
