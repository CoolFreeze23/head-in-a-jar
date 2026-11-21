package com.coolfreeze23.headinajar.staff.work.jobs;

import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Sorting: the card's one Pick-Up emptied into its Drop-Offs, each stack to the Drop-Off whose filter names it, or,
 * failing that, to the one marked "Everything else" (0.7.0: one filter a Drop-Off; grades open more in a later release).
 * The walking and carrying are Hauling's. At Disengaged morale 1 item in 20 goes to a random other Drop-Off with room
 * ("Misfiled."); a worker with the {@code never_misfiles} quirk (the Librarian) never misfiles.
 */
public final class Sorting {
    /** One item in this many is misfiled at Disengaged morale. */
    static final int MISFILE_ODDS = 20;
    static final String NEVER = "never_misfiles";

    private Sorting() {
    }

    /**
     * The misfiled part of a stack just taken (each item 1 in 20 when Disengaged), taken out of {@code got}; empty for
     * none.
     */
    public static ItemStack misfile(Mob mob, ItemStack got) {
        WorkTable.Spec spec = WorkTable.of(mob);
        if (spec != null && spec.quirk(NEVER) || !Jobs.disengaged(mob)) return ItemStack.EMPTY;
        int k = misfiles(mob.getRandom(), got.getCount());
        if (k <= 0) return ItemStack.EMPTY;
        ItemStack wrong = got.copyWithCount(k);
        got.shrink(k);
        return wrong;
    }

    /** How many of {@code n} items go astray, each 1 in {@link #MISFILE_ODDS}. */
    public static int misfiles(RandomSource random, int n) {
        int k = 0;
        for (int i = 0; i < n; i++) if (random.nextInt(MISFILE_ODDS) == 0) k++;
        return k;
    }

    /** A random Drop-Off of the order that is not one of {@code right} and has room for the stack; null for none. */
    static Order.Stop elsewhere(ServerLevel level, Mob mob, Order o, ItemStack stack, List<Order.Stop> right) {
        List<Order.Stop> others = new ArrayList<>();
        for (Order.Stop s : o.dropOffs()) if (!right.contains(s) && Hauling.room(level, s, stack) > 0) others.add(s);
        return others.isEmpty() ? null : others.get(mob.getRandom().nextInt(others.size()));
    }
}
