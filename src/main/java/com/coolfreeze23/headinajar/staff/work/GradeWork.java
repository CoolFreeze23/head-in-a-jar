package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.staff.Grades;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * What a grade does at work (0.7.2): its work speed ({@link Grades#SPEED}), a Team Lead's aura (staff within
 * {@link Grades#AURA_RANGE} blocks of a Team Lead of their own site's roster work {@link Grades#AURA} times faster; one
 * Team Lead's worth, never stacked), the carry of a Senior Associate and a Team Lead ({@link Grades#CARRY}), and the
 * worker's reminders while a review is due ("Still waiting on that review." at most once every {@link #NAG_TICKS} ticks
 * of work, and the employer's first memo).
 * <p>
 * Cost: the aura reads each site's Team Leads from its roster at most once every {@link #LEADS_TICKS} ticks (a map
 * lookup a roster member), and then at most {@link #MOST_LEADS} distances a think; nothing scans for entities.
 */
public final class GradeWork {
    /** Ticks of work between two reminders while a review is due. */
    public static final int NAG_TICKS = 6000;
    /** A site's Team Leads are read again after this many ticks. */
    static final int LEADS_TICKS = 100;
    /** At most this many Team Leads are measured for one worker's aura. */
    static final int MOST_LEADS = 8;

    private record Leads(long at, List<Mob> mobs) {
    }

    /** Each site's Team Leads, by clock (dimension and position). */
    private static final Map<GlobalPos, Leads> LEADS = new HashMap<>();
    /** Each worker's ticks of work since its last reminder, while due. */
    private static final Map<Mob, Integer> WORKED = new WeakHashMap<>();
    /** Workers whose employer has been offered the first-review memo this session. */
    private static final Map<Mob, Boolean> MEMO = new WeakHashMap<>();

    private GradeWork() {
    }

    /** The work speed a worker's grade and its site's Team Leads give it (1 for an Intern with no Team Lead near). */
    public static float speed(Mob mob) {
        float s = Grades.SPEED[Grades.of(Staff.of(mob))];
        return aura(mob) ? s * Grades.AURA : s;
    }

    /** Whether a Team Lead of its own site (not itself) is within {@link Grades#AURA_RANGE} blocks. */
    public static boolean aura(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        WorkerData d = Workforce.data(mob);
        if (d == null || d.clock == null || !d.clock.dimension().equals(level.dimension())) return false;
        int measured = 0;
        double r2 = Grades.AURA_RANGE * Grades.AURA_RANGE;
        for (Mob lead : leads(level, d.clock)) {
            if (lead == mob) continue;
            if (measured++ >= MOST_LEADS) break;
            if (lead.isAlive() && lead.level() == level && lead.distanceToSqr(mob) <= r2) return true;
        }
        return false;
    }

    /** The Team Leads on a site's roster, as last read (again after {@link #LEADS_TICKS}); none if the clock is not loaded. */
    static List<Mob> leads(ServerLevel level, GlobalPos site) {
        long now = level.getGameTime();
        Leads l = LEADS.get(site);
        if (l != null && now - l.at >= 0 && now - l.at < LEADS_TICKS) return l.mobs;
        List<Mob> out = new ArrayList<>();
        if (level.isLoaded(site.pos()) && level.getBlockEntity(site.pos()) instanceof TimeClockBlockEntity clock) {
            for (UUID id : clock.roster()) {
                Entity e = level.getEntity(id);
                if (e instanceof Mob m && Grades.of(Staff.of(m)) == Grades.TEAM_LEAD) out.add(m);
            }
        }
        if (LEADS.size() > 1024) LEADS.clear();
        LEADS.put(site, new Leads(now, List.copyOf(out)));
        return out;
    }

    /** Forgets a site's Team Leads (a review just changed a grade there): read again on the next think. */
    public static void forget(GlobalPos site) {
        if (site != null) LEADS.remove(site);
    }

    /** The carry's multiplier for a worker's grade (1 below Senior Associate). */
    public static float carry(Mob mob) {
        return Grades.CARRY[Grades.of(Staff.of(mob))];
    }

    /**
     * A think of work (a worker on the job with its card): while a review is due, the employer's first memo, and every
     * {@link #NAG_TICKS} ticks of work the worker's reminder over its head.
     */
    public static void worked(Mob mob, int ticks) {
        StaffFile f = Staff.of(mob);
        if (!Grades.due(f)) {
            WORKED.remove(mob);
            return;
        }
        if (MEMO.putIfAbsent(mob, true) == null && f.employer != null && mob.level().getServer() != null) {
            ServerPlayer boss = mob.level().getServer().getPlayerList().getPlayer(f.employer);
            if (boss != null) Memos.send(boss, Memos.Memo.REVIEW_DUE, Workforce.name(mob));
            else MEMO.remove(mob);
        }
        int n = WORKED.merge(mob, ticks, Integer::sum);
        if (n >= NAG_TICKS) {
            WORKED.put(mob, 0);
            Workforce.say(mob, "review_due");
        }
    }

    /** Tests: a worker's ticks of work towards its next reminder. */
    public static int worked(Mob mob) {
        return WORKED.getOrDefault(mob, 0);
    }
}
