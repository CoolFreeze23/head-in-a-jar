package com.coolfreeze23.headinajar.staff;

import net.minecraft.network.chat.Component;

/**
 * The grades staff climb by tasks: Intern, Associate at 50, Senior Associate at 300, Team Lead at 1,000. A grade takes
 * effect at a Performance Review, never by itself: until then the worker is "Review due" and works at its old grade.
 * Each grade's work speed, Sorting filters a stop, carry and wage are read from here.
 */
public final class Grades {
    public static final int INTERN = 0, ASSOCIATE = 1, SENIOR = 2, TEAM_LEAD = 3;
    /** Tasks each grade needs. */
    public static final int[] TASKS = {0, 50, 300, 1000};
    /** Work speed (a multiplier on the time a task takes is its inverse). */
    public static final float[] SPEED = {1.0f, 1.1f, 1.2f, 1.3f};
    /** Sorting filters a stop on a card printed from 0.7.2. */
    public static final int[] FILTERS = {1, 3, 6, 9};
    /** Carry, as a multiplier on its species' stacks (Senior Associates and Team Leads carry half again). */
    public static final float[] CARRY = {1.0f, 1.0f, 1.5f, 1.5f};
    /** Wage, in wages a dawn (a Team Lead takes two). */
    public static final int[] WAGE = {1, 1, 1, 2};
    /** A Team Lead makes staff within this many blocks work this much faster (one Team Lead's worth, never stacked). */
    public static final double AURA_RANGE = 8;
    public static final float AURA = 1.1f;

    private Grades() {
    }

    /** The grade on a file (0 Intern for none). */
    public static int of(StaffFile f) {
        return f == null || f.career == null ? INTERN : Math.max(INTERN, Math.min(TEAM_LEAD, f.career.grade));
    }

    /** The highest grade these tasks earn (a review may skip grades). */
    public static int earned(int tasks) {
        int g = INTERN;
        for (int i = 1; i < TASKS.length; i++) if (tasks >= TASKS[i]) g = i;
        return g;
    }

    /** Whether a review would raise its grade. */
    public static boolean due(StaffFile f) {
        return f != null && earned(f.tasks) > of(f);
    }

    public static Component name(int grade) {
        return Component.translatable("staff.headinajar.grade." + Math.max(INTERN, Math.min(TEAM_LEAD, grade)));
    }

    public static Component name(StaffFile f) {
        return name(of(f));
    }

    /** A worker's wage a dawn: its species' (a secondee's head's), twice over for a Team Lead. */
    public static StaffTable.Wage wage(net.minecraft.world.entity.Mob mob) {
        StaffTable.Wage base = StaffTable.wage(com.coolfreeze23.headinajar.staff.second.Secondees.payType(mob));
        int times = WAGE[of(Staff.of(mob))];
        return base.none() || times == 1 ? base : new StaffTable.Wage(base.item(), base.tag(), base.count() * times);
    }
}
