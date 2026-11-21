package com.coolfreeze23.headinajar.staff.work.jobs;

import com.coolfreeze23.headinajar.staff.Band;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.Quirks;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import com.coolfreeze23.headinajar.staff.work.WorkerPaths;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The four jobs (Hauling, Sorting, Farming, Security): a worker on the job does its card's next step.
 * <p>
 * Every container is read through {@code Capabilities.ItemHandler.BLOCK} at the face its stop was marked on, only after
 * {@code level.isLoaded} (a stop in a chunk that is not loaded is skipped: "Can't get there"), and every take is
 * simulated first, so two workers on one card never duplicate or lose an item. "Arrive" is within 2.5 blocks of the
 * stop's centre; an action takes 10 ticks divided by the worker's aptitude for the job and its morale's speed. A quirk
 * may hold a worker up before each step ({@code Quirks.holds}) and has its say over each take ({@code Quirks.taken}).
 * A finished task (a trip, 8 crops, 4 animals, a hostile seen off, 1,000 ticks of patrol) adds 1 to the file's
 * {@code tasks}.
 */
public final class Jobs {
    /** "Arrived": within this many blocks of a stop's centre. */
    public static final double ARRIVE = 2.5;
    /** Base ticks an action takes (taking, putting, harvesting one crop, shearing one sheep). */
    public static final int ACTION_TICKS = 10;
    /** Ticks a held-up worker waits before trying again (a full Drop-Off, an empty Pick-Up, a cat). */
    public static final int RETRY_TICKS = 40;
    /** The carry a fox or an allay shows in its mouth or hand (the very stack put there), by worker. */
    private static final java.util.Map<Mob, ItemStack> SHOWN = new java.util.WeakHashMap<>();
    /** The card each worker last read (a new one is read: held up to the face, or sniffed). */
    private static final java.util.Map<Mob, Order.Card> READ = new java.util.WeakHashMap<>();

    private Jobs() {
    }

    /** A think for a worker on the job. */
    public static void think(Mob mob, WorkerData d) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        Order.Card card = Order.working(level, d);
        if (card == null || d.clock == null || !Workforce.clockHere(mob, d)) {
            // the card is gone (taken out of the rack, the site closed): back to the clock
            end(mob, d);
            d.dropTask();
            d.state = WorkerData.State.TO_CLOCK;
            d.status = "staff.headinajar.status.waiting";
            READ.remove(mob);
            showCarry(mob, d);
            return;
        }
        if (!card.equals(READ.get(mob))) read(mob, card);
        // 0.7.2: a review due is remembered while it works
        com.coolfreeze23.headinajar.staff.work.GradeWork.worked(mob, Workforce.thinkTicks());
        if (Quirks.holds(mob, d)) return;
        WorkTable.Spec spec = WorkTable.of(mob);
        if (spec == null) return;
        try {
            switch (card.order().job()) {
                case WorkTable.HAULING -> Hauling.think(level, mob, d, spec, card, false);
                case WorkTable.SORTING -> Hauling.think(level, mob, d, spec, card, true);
                case WorkTable.FARMING -> Farming.think(level, mob, d, spec, card);
                case WorkTable.SECURITY -> Security.think(level, mob, d, spec, card);
                default -> {
                }
            }
        } finally {
            // whatever the step did to the carry, others see it
            showCarry(mob, d);
        }
    }

    /** A worker takes a card (from the rack, or by hand): it reads it. */
    public static void read(Mob mob, Order.Card card) {
        READ.put(mob, card);
        WorkerLooks.reading(mob);
    }

    // --- the carry, shown -------------------------------------------------------------------------------------------

    /** Whether this kind carries the vanilla way: a fox in its mouth, an allay in its hand (an enderman: a barrel). */
    static boolean handCarrier(Mob mob) {
        return mob instanceof net.minecraft.world.entity.animal.Fox || mob instanceof net.minecraft.world.entity.animal.allay.Allay;
    }

    /**
     * Shows the carry as others should see it, and tells them ({@code WorkerLooks.changed}, which sends only a real
     * change): a fox or an allay holds a copy of the first carried stack in its mouth or hand, an enderman holds a
     * barrel while it carries anything (the crate is the look layer's for everyone else). Anything the creature held
     * before it was put to work is set down at its feet when the carry first takes its place. The rules' death
     * handling empties a hand that matches the carry, so it is dropped once.
     */
    public static void showCarry(Mob mob, WorkerData d) {
        ItemStack first = ItemStack.EMPTY;
        for (ItemStack s : d.carried) {
            if (!s.isEmpty()) {
                first = s;
                break;
            }
        }
        if (mob instanceof net.minecraft.world.entity.monster.EnderMan enderman) {
            net.minecraft.world.level.block.state.BlockState barrel = net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState();
            if (!first.isEmpty() && enderman.getCarriedBlock() == null) enderman.setCarriedBlock(barrel);
            else if (first.isEmpty() && barrel.equals(enderman.getCarriedBlock())) enderman.setCarriedBlock(null);
        } else if (handCarrier(mob)) {
            net.minecraft.world.entity.EquipmentSlot hand = net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            ItemStack held = mob.getItemBySlot(hand);
            ItemStack shown = SHOWN.get(mob);
            // after a reload the hand still holds the copy: it is ours again
            if (shown == null && !held.isEmpty() && !first.isEmpty() && ItemStack.isSameItemSameComponents(held, first)) shown = held;
            boolean ours = shown != null && held == shown;
            if (first.isEmpty()) {
                if (ours) mob.setItemSlot(hand, ItemStack.EMPTY);
                SHOWN.remove(mob);
            } else if (!ours || held.getCount() != first.getCount() || !ItemStack.isSameItemSameComponents(held, first)) {
                if (!ours && !held.isEmpty()) mob.spawnAtLocation(held.copy());
                ItemStack copy = first.copy();
                mob.setItemSlot(hand, copy);
                SHOWN.put(mob, copy);
            }
        }
        WorkerLooks.changed(mob);
    }

    /**
     * Takes the shown carry out of a fox's mouth, an allay's hand or an enderman's arms before the carry itself is set
     * down (Let go): call it while {@code d.carried} still holds the load, so the copy is never left behind.
     */
    public static void hideCarry(Mob mob, WorkerData d) {
        if (mob instanceof net.minecraft.world.entity.monster.EnderMan enderman) {
            if (net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState().equals(enderman.getCarriedBlock())) enderman.setCarriedBlock(null);
        } else if (handCarrier(mob)) {
            ItemStack held = mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            boolean ours = held == SHOWN.get(mob);
            for (ItemStack s : d.carried) if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, s)) ours = true;
            if (ours && !held.isEmpty()) mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        SHOWN.remove(mob);
        READ.remove(mob);
    }

    /**
     * Why this worker cannot work this card (a refusal key under {@code refusal.headinajar.} and its arguments), or
     * null: it can. Asked when a card is given by hand and when idle staff look at the rack. The species' {@code cannot}
     * key for a job it cannot do (or the plain "A %s can't do %s."); {@code card.cannot.hands} for Farming a pen with
     * sheep or cows without hands; {@code card.water} for a swimmer whose stops are not in or touching water.
     */
    public static Object[] refusal(Mob mob, Order.Card card) {
        if (card == null) return new Object[]{"card.blank"};
        WorkTable.Spec spec = WorkTable.of(mob);
        String job = card.order().job();
        Component species = mob.getType().getDescription(), jobName = Component.translatable("staff.headinajar.job." + job);
        if (spec == null) return new Object[]{"card.cannot", species, jobName};
        if (!spec.card() || !spec.can(job)) {
            String key = spec.cannotKey(job);
            return key.equals("card.cannot") ? new Object[]{key, species, jobName} : new Object[]{key};
        }
        if (job.equals(WorkTable.FARMING) && !spec.hands() && spec.carry() > 0 && penned(mob.level(), card.order())) {
            return new Object[]{"card.cannot.hands"};
        }
        if (spec.water() && !wet(mob.level(), card.order())) return new Object[]{"card.water"};
        return null;
    }

    /** Whether any of an order's areas holds sheep or cows (only areas whose chunks are loaded are looked in). */
    static boolean penned(net.minecraft.world.level.Level level, Order o) {
        for (Order.Area a : o.areas()) {
            if (!level.isLoaded(a.a()) || !level.isLoaded(a.b())) continue;
            if (!level.getEntitiesOfClass(Animal.class, a.box(), e -> (e instanceof Sheep || e instanceof Cow) && !e.isBaby()).isEmpty()) return true;
        }
        return false;
    }

    /** Whether every stop is in or touching water (a stop whose chunk is not loaded is taken on trust). */
    static boolean wet(net.minecraft.world.level.Level level, Order o) {
        for (Order.Stop s : o.stops()) {
            if (!level.isLoaded(s.pos())) continue;
            boolean wet = level.getFluidState(s.pos()).is(FluidTags.WATER);
            for (Direction dir : Direction.values()) {
                if (wet) break;
                BlockPos n = s.pos().relative(dir);
                wet = level.isLoaded(n) && level.getFluidState(n).is(FluidTags.WATER);
            }
            if (!wet) return false;
        }
        return true;
    }

    /**
     * Ends a worker's task (a new card, a recall, a new site, its shift's end): its reservations go; what it carries it
     * keeps (the next card's Drop-Offs take it, or it is set down). The rack posting is the site's to drop.
     */
    public static void end(Mob mob, WorkerData d) {
        Farming.release(mob);
        Security.forget(mob);
        d.task = null;
        d.heldUp = false;
        d.pathFails = 0;
    }

    /** Whether a worker is in the middle of a trip it should finish before its shift ends (it carries a load). */
    public static boolean busy(Mob mob, WorkerData d) {
        return d.state == WorkerData.State.ON_JOB && d.carrying();
    }

    // --- shared by the jobs -----------------------------------------------------------------------------------------

    /** Morale's speed: Disengaged and Grievance work at half speed. */
    static float morale(Mob mob) {
        StaffFile f = Staff.of(mob);
        Band b = Band.of(f);
        return b == Band.DISENGAGED || b == Band.GRIEVANCE ? 0.5f : 1f;
    }

    /** Whether morale is Disengaged or worse (Sorting's misfiles). */
    static boolean disengaged(Mob mob) {
        Band b = Band.of(Staff.of(mob));
        return b == Band.DISENGAGED || b == Band.GRIEVANCE;
    }

    /** How fast it does a job: its aptitude times its morale's speed, and (0.7.2) its grade's and a Team Lead's near by. */
    static float pace(Mob mob, WorkTable.Spec spec, String job) {
        float a = spec.speed(job);
        return Math.max(0.1f, (a <= 0 ? 1 : a) * morale(mob) * com.coolfreeze23.headinajar.staff.work.GradeWork.speed(mob));
    }

    /** Ticks an action takes at this pace. */
    static int actionTicks(float pace) {
        return Math.max(1, Math.round(ACTION_TICKS / pace));
    }

    /** Whether the worker has arrived at a block (within {@link #ARRIVE} of its centre). */
    static boolean arrived(Mob mob, BlockPos pos) {
        Vec3 c = Vec3.atCenterOf(pos);
        Vec3 at = mob.position().add(0, Math.min(1, mob.getBbHeight() / 2), 0);
        return at.distanceToSqr(c) <= ARRIVE * ARRIVE;
    }

    /** The site box of the worker's clock. */
    static AABB box(WorkerData d) {
        return Workforce.box(d.clock.pos());
    }

    /**
     * A spot beside a block where the worker can stand (the block's own space if it has none to bump into), the
     * nearest to the worker, inside the box; null when there is none. Reads only loaded blocks.
     */
    static BlockPos standBy(ServerLevel level, Mob mob, BlockPos pos, AABB box) {
        if (!level.isLoaded(pos)) return null;
        if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && standable(level, pos)) return pos;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos n = pos.relative(dir).above(dy);
                if (!level.isLoaded(n) || !box.contains(Vec3.atCenterOf(n)) || !standable(level, n)) continue;
                double dist = mob.distanceToSqr(Vec3.atBottomCenterOf(n));
                if (dist < bestD) {
                    bestD = dist;
                    best = n;
                }
                break;
            }
        }
        if (best == null && standable(level, pos.above())) best = pos.above();
        return best;
    }

    /** Room for a creature to stand: nothing to bump into here and above, something to stand on below. */
    static boolean standable(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
    }

    /** A stop's item handler at its marked face, if its chunk is loaded and it is still a container; else null. */
    static IItemHandler handler(ServerLevel level, Order.Stop stop) {
        if (!level.isLoaded(stop.pos())) return null;
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, stop.pos(), stop.face());
        return h != null ? h : level.getCapability(Capabilities.ItemHandler.BLOCK, stop.pos(), null);
    }

    /** Whether a cat sits on this chest (vanilla will not open a chest with a cat on it, so staff wait too). */
    static boolean catOn(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return false;
        if (!ChestBlock.isChestBlockedAt(level, pos) && !otherHalfBlocked(level, pos, state)) return false;
        return catAbove(level, pos) || otherHalfCat(level, pos, state);
    }

    private static boolean otherHalfBlocked(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(ChestBlock.TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) return false;
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        return level.isLoaded(other) && ChestBlock.isChestBlockedAt(level, other);
    }

    private static boolean otherHalfCat(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(ChestBlock.TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) return false;
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        return level.isLoaded(other) && catAbove(level, other);
    }

    private static boolean catAbove(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(net.minecraft.world.entity.animal.Cat.class,
                new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1),
                net.minecraft.world.entity.animal.Cat::isInSittingPose).isEmpty();
    }

    /** One more finished task on the worker's file. */
    static void taskDone(Mob mob) {
        StaffFile f = Staff.of(mob);
        if (f != null) f.tasks++;
    }

    /** Its status on the Roster while it works with nothing wrong. */
    static void working(WorkerData d) {
        d.heldUp = false;
        d.status = "staff.headinajar.status.on_job";
    }

    /** A stop line (and the lamp's amber while it lasts). */
    static void held(Mob mob, WorkerData d, String key, Object... args) {
        d.heldUp = true;
        Workforce.say(mob, key, args);
    }

    /**
     * Walks towards a target, by the movers' contract: {@code WorkerPaths.go} every think until it has arrived (within
     * {@link #ARRIVE} of the target's centre, or at the spot it stands at); FAILED with {@code givenUp} means the stop
     * is skipped this trip ({@link Walk#gaveUp}; the movers say "Can't get there. Moving on."). Rationing, being on the
     * way already, a walk that came up short and a stuck walk are the movers' to tell.
     */
    static boolean walk(Mob mob, WorkerData d, Walk w, BlockPos target, BlockPos stand, double speed, AABB box) {
        if (arrived(mob, target) || stand != null && WorkerPaths.arrived(mob, stand)) return true;
        if (stand == null) {
            w.gaveUp = true;
            return false;
        }
        WorkerPaths.Result r = WorkerPaths.go(mob, d, stand, speed, box, false);
        if (r == WorkerPaths.Result.FAILED && WorkerPaths.givenUp(d)) {
            w.gaveUp = true;
            WorkerPaths.stop(mob);
        }
        return false;
    }

    /** A walk's bookkeeping, kept on a job's task. */
    static final class Walk {
        boolean gaveUp;

        void reset() {
            gaveUp = false;
        }
    }
}
