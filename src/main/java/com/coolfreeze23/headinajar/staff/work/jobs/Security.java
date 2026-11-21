package com.coolfreeze23.headinajar.staff.work.jobs;

import com.coolfreeze23.headinajar.staff.work.Combat;
import com.coolfreeze23.headinajar.staff.work.FriendOrFoe;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import com.coolfreeze23.headinajar.staff.work.WorkerPaths;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Security. Patrols random points in its area or the whole site box (a new point each time it arrives); each think it
 * looks for a hostile inside the box within 16 blocks of itself that the company's friend-or-foe rule allows, nearest
 * first, line of sight for the nearest three; then fights with its species' attack ({@code Combat.fight}). It never
 * paths out of the box: a target that leaves is dropped ("Off site. Not our problem."). Ranged staff take arrows from
 * the card's Pick-Up into their carry ({@code Combat} shoots from it). A task is one hostile seen off (killed or driven
 * out of the site) or 1,000 ticks of patrol.
 */
final class Security {
    /** How far it looks for trouble. */
    static final double SIGHT = 16;
    /** A patrol task is this many ticks. */
    static final int PATROL_TASK = 1000;
    /** Line of sight is checked for this many of the nearest. */
    static final int LOOKS = 3;

    /** Who each guard is fighting (not saved: a reload starts the patrol again). */
    private static final Map<Mob, UUID> TARGET = new WeakHashMap<>();

    private Security() {
    }

    /** The guard's task: where it is walking and how long it has patrolled. */
    static final class Patrol {
        final Order.Card card;
        BlockPos point;
        final Jobs.Walk walk = new Jobs.Walk();
        int patrolled;
        long lastThink;
        long noArrows;
        Order.Stop fetchAt;
        BlockPos stand;

        Patrol(Order.Card card) {
            this.card = card;
        }
    }

    static void forget(Mob mob) {
        TARGET.remove(mob);
    }

    static void think(ServerLevel level, Mob mob, WorkerData d, WorkTable.Spec spec, Order.Card card) {
        Patrol p = d.task instanceof Patrol patrol && patrol.card.equals(card) ? patrol : null;
        if (p == null) {
            p = new Patrol(card);
            p.lastThink = level.getGameTime();
            d.task = p;
            TARGET.remove(mob);
        }
        long now = level.getGameTime();
        int since = (int) Mth.clamp(now - p.lastThink, 0, 200);
        p.lastThink = now;
        AABB box = Jobs.box(d);
        UUID owner = d.employer;
        LivingEntity target = target(level, mob, owner, box);
        if (target != null) {
            Jobs.working(d);
            if (!Combat.fight(mob, d, target, box)) mob.getLookControl().setLookAt(target, 30, 30);
            return;
        }
        // ranged staff keep arrows in their carry
        if (ranged(spec) && arrows(d) == 0 && !card.order().pickUps().isEmpty()) {
            if (now >= p.noArrows && fetchArrows(level, mob, d, spec, p, box)) return;
        }
        float pace = Jobs.pace(mob, spec, WorkTable.SECURITY);
        Order.Area area = card.order().areas().isEmpty() ? null : card.order().areas().get(0);
        AABB beat = area == null ? box : area.box().intersect(box);
        if (p.point == null || Jobs.arrived(mob, p.point) || p.walk.gaveUp) {
            p.point = patrolPoint(level, mob, beat, box);
            p.walk.reset();
        }
        if (p.point != null) Jobs.walk(mob, d, p.walk, p.point, p.point, pace * 0.8, box);
        // a line just said (no arrows, a target let go) stays its status while it shows
        boolean fresh = d.status != null && d.status.startsWith("staff.headinajar.say.") && now - d.lastSaid < Workforce.SAY_SHOW;
        if (!fresh) Jobs.working(d);
        p.patrolled += since;
        while (p.patrolled >= PATROL_TASK) {
            p.patrolled -= PATROL_TASK;
            Jobs.taskDone(mob);
        }
    }

    /**
     * The hostile it is after: the one it was fighting while it is alive, inside the box and still fair game (one that
     * dies or leaves counts as seen off; one that leaves gets the line), else the nearest allowed hostile within 16
     * inside the box that one of its nearest three it can see. Null: none.
     */
    private static LivingEntity target(ServerLevel level, Mob mob, UUID owner, AABB box) {
        UUID was = TARGET.get(mob);
        if (was != null) {
            Entity e = level.getEntity(was);
            if (e instanceof LivingEntity t && t.isAlive() && box.contains(t.position()) && FriendOrFoe.mayTarget(owner, level, t)) return t;
            TARGET.remove(mob);
            if (e instanceof LivingEntity t && t.isAlive() && !box.contains(t.position())) Workforce.say(mob, "not_our_problem");
            if (!(e instanceof LivingEntity t) || !t.isAlive() || !box.contains(t.position())) Jobs.taskDone(mob);
            WorkerPaths.stop(mob);
        }
        AABB look = mob.getBoundingBox().inflate(SIGHT).intersect(box);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, look,
                e -> e != mob && e.distanceToSqr(mob) <= SIGHT * SIGHT && box.contains(e.position()) && FriendOrFoe.mayTarget(owner, level, e));
        if (near.isEmpty()) return null;
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mob)));
        for (int i = 0; i < Math.min(LOOKS, near.size()); i++) {
            LivingEntity e = near.get(i);
            if (mob.hasLineOfSight(e)) {
                TARGET.put(mob, e.getUUID());
                return e;
            }
        }
        return null;
    }

    private static boolean ranged(WorkTable.Spec spec) {
        return "bow".equals(spec.attack()) || "crossbow".equals(spec.attack());
    }

    private static int arrows(WorkerData d) {
        int n = 0;
        for (ItemStack s : d.carried) if (s.is(ItemTags.ARROWS)) n += s.getCount();
        return n;
    }

    /** Walks to the card's Pick-Up and takes arrows into its carry; false when there is nothing to fetch (it says so). */
    private static boolean fetchArrows(ServerLevel level, Mob mob, WorkerData d, WorkTable.Spec spec, Patrol p, AABB box) {
        long now = level.getGameTime();
        if (p.fetchAt == null) {
            p.fetchAt = p.card.order().pickUps().get(0);
            p.stand = null;
            p.walk.reset();
        }
        Order.Stop stop = p.fetchAt;
        if (!level.isLoaded(stop.pos())) {
            p.fetchAt = null;
            p.noArrows = now + Farming.ASK_AGAIN;
            Jobs.held(mob, d, "cant_get_there");
            return false;
        }
        if (p.stand == null) p.stand = Jobs.standBy(level, mob, stop.pos(), box);
        if (!Jobs.walk(mob, d, p.walk, stop.pos(), p.stand, Jobs.pace(mob, spec, WorkTable.SECURITY), box)) {
            if (p.walk.gaveUp) {
                p.fetchAt = null;
                p.walk.reset();
                p.noArrows = now + Farming.ASK_AGAIN;
                Jobs.held(mob, d, "cant_get_there");
                return false;
            }
            return true;
        }
        p.fetchAt = null;
        IItemHandler from = Jobs.handler(level, stop);
        ItemStack got = ItemStack.EMPTY;
        if (from != null && !Jobs.catOn(level, stop.pos())) {
            for (int slot = 0; slot < from.getSlots() && got.isEmpty(); slot++) {
                ItemStack seen = from.extractItem(slot, 64, true);
                if (seen.isEmpty() || !seen.is(ItemTags.ARROWS)) continue;
                got = from.extractItem(slot, Math.min(seen.getCount(), spec.carryLimit(seen.getMaxStackSize()) > 0 ? spec.carryLimit(seen.getMaxStackSize()) : 64), false);
            }
        }
        if (!got.isEmpty()) got = com.coolfreeze23.headinajar.staff.work.Quirks.taken(mob, d, got);
        if (got.isEmpty()) {
            p.noArrows = now + Farming.ASK_AGAIN;
            Jobs.held(mob, d, "no_arrows");
            return false;
        }
        Farming.carry(mob, d, got);
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        WorkerLooks.changed(mob);
        Jobs.working(d);
        return true;
    }

    /**
     * A point to patrol to inside {@code within} (an area, or the box): the movers' random point
     * ({@code Movers.randomPoint}, near the worker by its species' own picker), or, when that finds none inside an area
     * away from it, a standable spot anywhere in the area ({@link #anywhere}).
     */
    static BlockPos patrolPoint(ServerLevel level, Mob mob, AABB within, AABB box) {
        BlockPos p = com.coolfreeze23.headinajar.staff.work.Movers.randomPoint(mob, within);
        return p != null ? p : anywhere(level, mob, within, box);
    }

    /**
     * A random spot to walk to inside {@code within} (and the site box): somewhere a creature can stand, loaded, found by
     * looking down each of up to 10 random columns. Null if none was found this time.
     */
    static BlockPos anywhere(ServerLevel level, Mob mob, AABB within, AABB box) {
        if (within == null || within.getXsize() <= 0 || within.getZsize() <= 0) return null;
        var r = mob.getRandom();
        for (int i = 0; i < 10; i++) {
            int x = Mth.floor(within.minX + r.nextDouble() * within.getXsize());
            int z = Mth.floor(within.minZ + r.nextDouble() * within.getZsize());
            int top = Mth.floor(within.maxY) - 1, bottom = Mth.floor(within.minY);
            for (int y = top; y >= bottom; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (!level.isLoaded(p)) break;
                if (!box.contains(Vec3.atCenterOf(p))) continue;
                if (Jobs.standable(level, p)) return p;
            }
        }
        return null;
    }
}
