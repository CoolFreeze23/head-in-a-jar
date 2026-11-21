package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.mind.MobMotor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A striker's body on the picket line (0.7.2), for {@code staff.Strikes}: walking out (off the job, off a detail, the
 * card kept), walking to its slot in the ring with the site's own rationed paths (vanilla navigation, which never digs),
 * and standing there facing the clock with its brain off. Nothing here thinks: the strike decides, this moves.
 */
public final class Picket {
    private Picket() {
    }

    /** It walks out: whatever it was doing ends (a job's trip, a detail), and it stops where it is. */
    public static void walkOut(Mob mob, WorkerData d, TimeClockBlockEntity c) {
        if (d.state == WorkerData.State.DETAIL || d.detail != null) {
            if (d.detail != null && mob.getServer() != null) {
                ServerPlayer lead = mob.getServer().getPlayerList().getPlayer(d.detail);
                if (lead != null) {
                    List<UUID> roll = new ArrayList<>(Detail.members(lead));
                    if (roll.remove(mob.getUUID())) Detail.save(lead, roll, Detail.mode(lead));
                }
            }
            Detail.sendHome(mob, d);
        }
        if (d.state == WorkerData.State.ON_JOB) Shifts.sendBack(mob, d, c);
        else {
            com.coolfreeze23.headinajar.staff.work.jobs.Jobs.end(mob, d);
            d.dropTask();
            if (c != null) c.take(mob.getUUID(), -1);
        }
        d.state = WorkerData.State.TO_CLOCK;
        d.status = Shifts.status("on_strike");
        WorkerPaths.stop(mob);
    }

    /**
     * One tick on the way to its slot: the body moves every tick, the path is asked for on think ticks only (rationed).
     * True when it is there, or has given up on the slot and stands where it stopped.
     */
    public static boolean toSlot(Mob mob, WorkerData d, BlockPos slot, AABB box) {
        MobMotor.tick(mob);
        Movers.tick(mob);
        if (Math.floorMod(mob.tickCount + mob.getId(), Workforce.thinkTicks()) != 0) return Shifts.near(mob, slot);
        return Shifts.walk(mob, d, slot, box);
    }

    /** It stands where it is, its brain off. */
    public static void stand(Mob mob) {
        if (!MobMotor.idle(mob) || Movers.moving(mob)) WorkerPaths.stop(mob);
    }

    /**
     * The order strikers take the ring's slots in: round the clock from its front, as the slots are laid, so each walks
     * out to the slot on its own side and nobody crosses the crowd at the clock.
     */
    public static <T extends Mob> void roundTheClock(List<T> strikers, TimeClockBlockEntity c) {
        BlockPos clock = c.getBlockPos();
        double front = Math.atan2(c.facing().getStepZ(), c.facing().getStepX());
        strikers.sort(java.util.Comparator.comparingDouble(m -> {
            double a = Math.atan2(m.getZ() - (clock.getZ() + 0.5), m.getX() - (clock.getX() + 0.5)) - front;
            return ((a % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
        }));
    }

    /** Whether it is within reach of its slot. */
    public static boolean at(Mob mob, BlockPos slot) {
        return Shifts.near(mob, slot);
    }

    /** It turns to face the clock (and stays so). */
    public static void face(Mob mob, TimeClockBlockEntity c) {
        Shifts.face(mob, c);
    }

    /** Back to work when the strike ends: to the clock, to punch in for its shift. */
    public static void backToWork(Mob mob, WorkerData d) {
        WorkerPaths.stop(mob);
        d.state = WorkerData.State.TO_CLOCK;
        d.status = Shifts.status("waiting");
        d.pathFails = 0;
        WorkerLooks.changed(mob);
    }

    /** The ring's radii round the clock, in the order they are filled. */
    public static final int[] RADII = {3, 4, 5};

    /**
     * {@code n} slots round the clock, spread evenly from the cell in front of it: as many as fit at radius 3, the rest at
     * 4, then 5. A slot is a standable cell (solid below, two cells clear; the cell itself, or one up or down) in the
     * site's box, never taken twice. Fewer than {@code n} come back when the ground has no more room: the rest stand
     * where they stop.
     */
    public static List<BlockPos> ring(TimeClockBlockEntity c, int n) {
        List<BlockPos> out = new ArrayList<>();
        net.minecraft.world.level.Level level = c.getLevel();
        if (level == null || n <= 0) return out;
        BlockPos clock = c.getBlockPos();
        java.util.Set<BlockPos> taken = new java.util.HashSet<>();
        double front = Math.atan2(c.facing().getStepZ(), c.facing().getStepX());
        for (int r : RADII) {
            int left = n - out.size();
            if (left <= 0) break;
            // each striker's angle on this circle; a blocked one is looked for along the circle, never past its share
            double share = 2 * Math.PI / left, step = 1.0 / r;
            int tries = Math.max(1, (int) Math.floor(share / 2 / step));
            for (int k = 0; k < left; k++) {
                double want = front + share * k;
                for (int j = 0; j <= 2 * tries; j++) {
                    double a = want + (j % 2 == 0 ? 1 : -1) * ((j + 1) / 2) * step;
                    BlockPos p = clock.offset((int) Math.round(r * Math.cos(a)), 0, (int) Math.round(r * Math.sin(a)));
                    if (!Workforce.inBox(clock, p)) continue;
                    BlockPos s = Shifts.standable(level, p);
                    if (s == null || !taken.add(s.immutable())) continue;
                    out.add(s.immutable());
                    break;
                }
            }
        }
        return out;
    }

    /** Whether a cell is one a creature can stand in: solid below, two cells clear of collision and fluid. */
    public static boolean standable(net.minecraft.world.level.Level level, BlockPos p) {
        if (!level.isLoaded(p)) return false;
        BlockPos up = p.above(), below = p.below();
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(up).getCollisionShape(level, up).isEmpty()
                && level.getFluidState(p).isEmpty() && level.getFluidState(up).isEmpty()
                && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }
}
