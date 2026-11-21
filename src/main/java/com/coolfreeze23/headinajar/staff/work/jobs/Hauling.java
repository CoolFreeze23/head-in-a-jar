package com.coolfreeze23.headinajar.staff.work.jobs;

import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.Quirks;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Hauling (and Sorting, which is Hauling from one Pick-Up with misfiles: {@link Sorting}). Each trip: the next Pick-Up in
 * turn; at it, read through its stored face, take by simulated extraction the first stack some Drop-Off on the card
 * accepts and has simulated room for, up to the carry size; carry it to that Drop-Off and insert it; what is left over
 * tries the next Drop-Off that takes it, else it waits holding it ("Drop-off full. Waiting.") and tries again every 40
 * ticks. A chest with a cat on it: it waits with the cat line. One trip in flight per worker; two haulers on one card
 * each check again at the container, so neither duplicates nor loses an item.
 */
final class Hauling {
    private Hauling() {
    }

    /** A trip in flight (the worker's {@code task}). */
    static final class Trip {
        final Order.Card card;
        /** The Pick-Up last visited (an index into the card's Pick-Ups). */
        int pick = -1;
        /** Where it is walking, and where it stands for it. */
        Order.Stop target;
        BlockPos stand;
        final Jobs.Walk walk = new Jobs.Walk();
        /** Where each carried stack goes (parallel to the worker's carry), and whether it is a misfile. */
        final List<Order.Stop> dest = new ArrayList<>();
        final List<Boolean> misfiled = new ArrayList<>();
        /** The game time its current action ends (0: not started). */
        long readyAt;
        /** Waiting until (a full Drop-Off, empty Pick-Ups, a cat). */
        long retryAt;
        /** Pick-Ups found with nothing to take, in a row. */
        int empty;

        Trip(Order.Card card) {
            this.card = card;
        }

        void aim(Order.Stop s) {
            target = s;
            stand = null;
            walk.reset();
            readyAt = 0;
        }
    }

    static void think(ServerLevel level, Mob mob, WorkerData d, WorkTable.Spec spec, Order.Card card, boolean sorting) {
        String job = sorting ? WorkTable.SORTING : WorkTable.HAULING;
        Trip t = d.task instanceof Trip trip && trip.card.equals(card) ? trip : null;
        if (t == null) {
            t = new Trip(card);
            d.task = t;
            // a load carried over from before (another card, a reload): where it goes on this card
            for (ItemStack s : d.carried) {
                List<Order.Stop> to = card.order().destinations(s, sorting);
                t.dest.add(to.isEmpty() ? null : to.get(0));
                t.misfiled.add(false);
            }
        }
        long now = level.getGameTime();
        if (now < t.retryAt) return;
        float pace = Jobs.pace(mob, spec, job);
        AABB box = Jobs.box(d);
        if (d.carrying()) deliver(level, mob, d, t, pace, box, sorting);
        else fetch(level, mob, d, spec, t, pace, box, sorting);
    }

    // --- to the Pick-Up and back with a load ------------------------------------------------------------------------

    private static void fetch(ServerLevel level, Mob mob, WorkerData d, WorkTable.Spec spec, Trip t, float pace, AABB box, boolean sorting) {
        List<Order.Stop> picks = t.card.order().pickUps();
        if (picks.isEmpty()) return;
        long now = level.getGameTime();
        if (t.target == null || t.target.dropOff()) {
            t.pick = (t.pick + 1) % picks.size();
            t.aim(picks.get(t.pick));
        }
        Order.Stop stop = t.target;
        if (!level.isLoaded(stop.pos())) {
            skip(mob, d, t);
            return;
        }
        if (t.stand == null) t.stand = Jobs.standBy(level, mob, stop.pos(), box);
        if (!Jobs.walk(mob, d, t.walk, stop.pos(), t.stand, pace, box)) {
            if (t.walk.gaveUp) skip(mob, d, t);
            return;
        }
        if (t.readyAt == 0) {
            t.readyAt = now + Jobs.actionTicks(pace);
            mob.getLookControl().setLookAt(stop.pos().getX() + 0.5, stop.pos().getY() + 0.5, stop.pos().getZ() + 0.5);
            return;
        }
        if (now < t.readyAt) return;
        t.readyAt = 0;
        if (Jobs.catOn(level, stop.pos())) {
            Jobs.held(mob, d, "cat_on_chest");
            t.retryAt = now + Jobs.RETRY_TICKS;
            return;
        }
        IItemHandler from = Jobs.handler(level, stop);
        if (from == null) {
            skip(mob, d, t);
            return;
        }
        if (take(level, mob, d, spec, t, from, sorting)) {
            t.empty = 0;
            Jobs.working(d);
            mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            WorkerLooks.changed(mob);
            if (d.carrying()) t.aim(t.dest.get(0));
            else t.target = null;
            return;
        }
        // nothing to take here: the next Pick-Up; all of them empty, a rest
        t.target = null;
        if (++t.empty >= picks.size()) {
            t.empty = 0;
            t.retryAt = now + Jobs.RETRY_TICKS;
            Jobs.working(d);
            d.status = "staff.headinajar.say.pick_up_empty";
        }
    }

    /** A stop it could not reach (or not see: its chunk is not loaded): "Can't get there. Moving on." */
    private static void skip(Mob mob, WorkerData d, Trip t) {
        Jobs.held(mob, d, "cant_get_there");
        t.target = null;
        t.walk.reset();
    }

    /**
     * Takes from a Pick-Up: stack by stack, the first that some Drop-Off on the card accepts and has room for (both
     * simulated), as much as the room and the carry allow, until the carry is full. Returns whether it took anything.
     */
    static boolean take(ServerLevel level, Mob mob, WorkerData d, WorkTable.Spec spec, Trip t, IItemHandler from, boolean sorting) {
        int stacks = Math.max(1, Math.min(WorkerData.MAX_CARRIED, spec.carry()));
        int budget = spec.carryItems() > 0 ? spec.carryItems() : Integer.MAX_VALUE;
        boolean took = false;
        for (int slot = 0; slot < from.getSlots() && d.carried.size() < stacks && budget > 0; slot++) {
            ItemStack seen = from.extractItem(slot, 64, true);
            if (seen.isEmpty()) continue;
            Order o = t.card.order();
            List<Order.Stop> to = o.destinations(seen, sorting);
            int room = 0;
            Order.Stop first = null;
            for (Order.Stop s : to) {
                int r = room(level, s, seen);
                if (r > 0 && first == null) first = s;
                room += r;
            }
            if (first == null) continue;
            int n = Math.min(Math.min(seen.getCount(), room), Math.min(budget, spec.carryLimit(seen.getMaxStackSize())));
            if (n <= 0) continue;
            ItemStack got = from.extractItem(slot, n, false);
            if (got.isEmpty()) continue;
            got = Quirks.taken(mob, d, got);
            took = true;
            if (got.isEmpty()) continue;
            budget -= got.getCount();
            ItemStack wrong = sorting ? Sorting.misfile(mob, got) : ItemStack.EMPTY;
            if (!got.isEmpty()) {
                d.carried.add(got);
                t.dest.add(first);
                t.misfiled.add(false);
            }
            if (!wrong.isEmpty()) {
                Order.Stop elsewhere = Sorting.elsewhere(level, mob, o, wrong, to);
                if (elsewhere == null) {
                    // nowhere wrong to put it: it goes where it belongs after all
                    if (!got.isEmpty() && ItemStack.isSameItemSameComponents(got, wrong)) got.grow(wrong.getCount());
                    else {
                        d.carried.add(wrong);
                        t.dest.add(first);
                        t.misfiled.add(false);
                    }
                } else {
                    d.carried.add(wrong);
                    t.dest.add(elsewhere);
                    t.misfiled.add(true);
                }
            }
        }
        return took;
    }

    /** How many of a stack a Drop-Off has room for now (simulated; 0 if its chunk is not loaded or it is gone). */
    static int room(ServerLevel level, Order.Stop s, ItemStack stack) {
        IItemHandler h = Jobs.handler(level, s);
        if (h == null) return 0;
        ItemStack rest = ItemHandlerHelper.insertItemStacked(h, stack.copy(), true);
        return stack.getCount() - rest.getCount();
    }

    private static void deliver(ServerLevel level, Mob mob, WorkerData d, Trip t, float pace, AABB box, boolean sorting) {
        long now = level.getGameTime();
        // keep the bookkeeping beside the carry
        while (t.dest.size() < d.carried.size()) {
            List<Order.Stop> to = t.card.order().destinations(d.carried.get(t.dest.size()), sorting);
            t.dest.add(to.isEmpty() ? null : to.get(0));
            t.misfiled.add(false);
        }
        ItemStack load = d.carried.get(0);
        Order.Stop dest = t.dest.get(0);
        if (load.isEmpty() || dest == null) {
            // nothing here takes it: set down where it stands
            if (!load.isEmpty()) mob.spawnAtLocation(load);
            pop(d, t);
            WorkerLooks.changed(mob);
            return;
        }
        if (t.target != dest) t.aim(dest);
        if (!level.isLoaded(dest.pos())) {
            if (!reroute(level, t, load, dest, sorting)) {
                Jobs.held(mob, d, "cant_get_there");
                t.retryAt = now + Jobs.RETRY_TICKS;
            }
            return;
        }
        if (t.stand == null) t.stand = Jobs.standBy(level, mob, dest.pos(), box);
        if (!Jobs.walk(mob, d, t.walk, dest.pos(), t.stand, pace, box)) {
            if (t.walk.gaveUp) {
                t.walk.reset();
                if (!reroute(level, t, load, dest, sorting)) {
                    Jobs.held(mob, d, "cant_get_there");
                    t.retryAt = now + Jobs.RETRY_TICKS;
                }
            }
            return;
        }
        if (t.readyAt == 0) {
            t.readyAt = now + Jobs.actionTicks(pace);
            mob.getLookControl().setLookAt(dest.pos().getX() + 0.5, dest.pos().getY() + 0.5, dest.pos().getZ() + 0.5);
            return;
        }
        if (now < t.readyAt) return;
        t.readyAt = 0;
        if (Jobs.catOn(level, dest.pos())) {
            Jobs.held(mob, d, "cat_on_chest");
            t.retryAt = now + Jobs.RETRY_TICKS;
            return;
        }
        IItemHandler into = Jobs.handler(level, dest);
        if (into == null) {
            if (!reroute(level, t, load, dest, sorting)) {
                Jobs.held(mob, d, "cant_get_there");
                t.retryAt = now + Jobs.RETRY_TICKS;
            }
            return;
        }
        boolean wrong = t.misfiled.get(0);
        ItemStack rest = ItemHandlerHelper.insertItemStacked(into, load.copy(), false);
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (rest.getCount() < load.getCount() && wrong) com.coolfreeze23.headinajar.staff.work.Workforce.say(mob, "misfiled");
        if (rest.isEmpty()) {
            pop(d, t);
            if (!d.carrying()) {
                // the trip is done
                Jobs.taskDone(mob);
                t.target = null;
                WorkerLooks.changed(mob);
            }
            if (!wrong) Jobs.working(d);
            return;
        }
        d.carried.set(0, rest);
        if (!reroute(level, t, rest, dest, sorting)) {
            Jobs.held(mob, d, "drop_off_full");
            t.retryAt = now + Jobs.RETRY_TICKS;
        }
    }

    /** The first carried stack is done with (delivered, or set down). */
    private static void pop(WorkerData d, Trip t) {
        if (!d.carried.isEmpty()) d.carried.remove(0);
        if (!t.dest.isEmpty()) t.dest.remove(0);
        if (!t.misfiled.isEmpty()) t.misfiled.remove(0);
    }

    /** Another Drop-Off on the card that takes this stack and has room; false when there is none (it waits). */
    private static boolean reroute(ServerLevel level, Trip t, ItemStack load, Order.Stop not, boolean sorting) {
        for (Order.Stop s : t.card.order().destinations(load, sorting)) {
            if (s.equals(not)) continue;
            if (room(level, s, load) > 0) {
                t.dest.set(0, s);
                t.misfiled.set(0, false);
                t.aim(s);
                return true;
            }
        }
        return false;
    }
}
