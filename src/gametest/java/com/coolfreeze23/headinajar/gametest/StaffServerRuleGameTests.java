package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.Movers;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerBrain;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerPaths;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.coolfreeze23.headinajar.gametest.StaffMoverGameTests.CLOCK;
import static com.coolfreeze23.headinajar.gametest.StaffMoverGameTests.box;
import static com.coolfreeze23.headinajar.gametest.StaffMoverGameTests.fill;
import static com.coolfreeze23.headinajar.gametest.StaffMoverGameTests.hired;
import static com.coolfreeze23.headinajar.gametest.StaffMoverGameTests.player;
import static com.coolfreeze23.headinajar.gametest.StaffMoverGameTests.stop;

/**
 * 0.7.0, the server rules that keep staff cheap: thinking staggered by id, paths rationed (one a 40 ticks, one a think
 * in combat, a failed stop tried twice more then skipped), paths inside the site box, no chunk ever loaded by a think, a
 * stop in a chunk that is not there skipped unread, the mandatory break stopping every brain, and idle brains asking
 * for nothing. Tests that read the brain's own counters (which count every worker in the world) run in batches of
 * their own.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class StaffServerRuleGameTests {

    /** The brain's think count (every worker in the world), read straight from its counter. */
    static long thinks() {
        try {
            Field f = WorkerBrain.class.getDeclaredField("thinks");
            f.setAccessible(true);
            return f.getLong(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Workers left from tests that have finished (their structures gone): out of the way of a count. */
    static void clearLeftovers(GameTestHelper h, List<? extends Mob> keep) {
        for (Mob m : h.getLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Mob.class), Workforce::isWorker)) {
            if (!keep.contains(m)) m.discard();
        }
    }

    /**
     * Its status is "Can't get there. Moving on." (a null status fails the test, it does not crash the server). Read
     * right after the ask that gave up: the site's own think rewrites the status of an idle worker on its next think.
     */
    static void saidCantGetThere(GameTestHelper h, WorkerData d) {
        saidCantGetThere(h, d.status);
    }

    static void saidCantGetThere(GameTestHelper h, String status) {
        h.assertTrue("staff.headinajar.say.cant_get_there".equals(status), "its status is " + status + ", not \"Can't get there\"");
    }

    /** The status a worker had at the moment it gave up on its stop (null until it has). */
    static String giveUpLine(WorkerData d, boolean wasGivenUp, String before) {
        return before != null || wasGivenUp || !WorkerPaths.givenUp(d) ? before : d.status;
    }

    /** Counts a worker's path requests: each one moves its {@code lastPath} on. */
    static final class Requests {
        final Map<Mob, Long> last = new HashMap<>();
        final Map<Mob, Integer> count = new HashMap<>();

        void watch(Mob mob) {
            last.put(mob, Workforce.data(mob).lastPath);
            count.put(mob, 0);
        }

        void tick() {
            for (Mob m : last.keySet()) {
                WorkerData d = Workforce.data(m);
                if (d == null) continue;
                if (d.lastPath != last.get(m)) {
                    last.put(m, d.lastPath);
                    count.merge(m, 1, Integer::sum);
                }
            }
        }

        int of(Mob m) {
            return count.getOrDefault(m, 0);
        }
    }

    // --- thinking ---------------------------------------------------------------------------------------------------

    /** Over 100 ticks each of 24 workers thinks exactly on its own ticks (10 times), never more than ceil(24/10) a tick. */
    @GameTest(template = "site34", timeoutTicks = 200, batch = "staff_think")
    public static void thinkingIsStaggered(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 5, 2));
        List<Mob> cows = new ArrayList<>();
        for (int i = 0; i < 24; i++) cows.add(hired(h, p, EntityType.COW, new BlockPos(5 + (i % 6) * 4, 5, 5 + (i / 6) * 4)));
        clearLeftovers(h, cows);
        int tt = Workforce.thinkTicks();
        int[] seen = {0, 0, 0};
        long[] before = {thinks()};
        int[] most = {0};
        List<String> wrong = new ArrayList<>();
        h.onEachTick(() -> {
            long now = thinks();
            int measured = (int) (now - before[0]);
            before[0] = now;
            if (seen[0]++ == 0 || seen[1] >= 100) return;
            seen[1]++;
            int predicted = 0;
            for (Mob c : cows) if (Math.floorMod(c.tickCount + c.getId(), tt) == 0) predicted++;
            most[0] = Math.max(most[0], measured);
            seen[2] += measured;
            if (measured != predicted && wrong.size() < 5) wrong.add("tick " + seen[1] + ": " + measured + " thinks, " + predicted + " due");
        });
        h.succeedWhen(() -> {
            h.assertTrue(seen[1] >= 100, "counting");
            h.assertTrue(wrong.isEmpty(), "thinks off their own ticks: " + wrong);
            h.assertValueEqual(seen[2], 24 * 100 / tt, "thinks in 100 ticks");
            h.assertTrue(most[0] <= (24 + tt - 1) / tt, most[0] + " thinks in one tick");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    /** {@code /headoffice staff pause}: no worker thinks, plans a path or moves, whatever it was doing. */
    @GameTest(template = "site34", timeoutTicks = 260, batch = "staff_pause")
    public static void pauseStopsEveryBrain(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 5, 2));
        AABB box = box(h, CLOCK);
        BlockPos squidStop = StaffMoverGameTests.channel(h, 28, Blocks.WATER);
        List<Mob> staff = new ArrayList<>();
        List<BlockPos> stops = new ArrayList<>();
        int z = 4;
        for (EntityType<? extends Mob> type : List.of(EntityType.ZOMBIE, EntityType.COW, EntityType.SPIDER, EntityType.ALLAY, EntityType.SLIME)) {
            staff.add(hired(h, p, type, new BlockPos(4, 5, z)));
            stops.add(stop(h, new BlockPos(30, 5, z)));
            z += 4;
        }
        staff.add(hired(h, p, EntityType.GHAST, new BlockPos(6, 7, 22)));
        stops.add(stop(h, new BlockPos(30, 5, 22)));
        staff.add(hired(h, p, EntityType.SQUID, new BlockPos(9, 3, 28)));
        stops.add(squidStop);
        clearLeftovers(h, staff);
        Requests requests = new Requests();
        Map<Mob, Vec3> held = new HashMap<>();
        long[] thinksAt = {0};
        boolean[] wasPaused = {Workforce.paused};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t < 20) {
                // on their way first
                for (int i = 0; i < staff.size(); i++) WorkerPaths.go(staff.get(i), Workforce.data(staff.get(i)), stops.get(i), 1.0, box, false);
                return;
            }
            if (t == 20) {
                Workforce.paused = true;
                for (Mob m : staff) requests.watch(m);
                return;
            }
            // the break: whoever asks, nobody plans (the asks a job would make are refused by the halted brain anyway)
            if (t == 60) {
                thinksAt[0] = thinks();
                for (Mob m : staff) held.put(m, m.position());
            }
            requests.tick();
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() >= 220, "paused");
            try {
                h.assertValueEqual(thinks() - thinksAt[0], 0L, "thinks during the break");
                for (Mob m : staff) {
                    String name = m.getType().getDescription().getString();
                    h.assertValueEqual(requests.of(m), 0, "paths planned by the " + name + " during the break");
                    h.assertTrue(m.position().distanceTo(held.get(m)) < 0.3, "the " + name + " moved " + m.position().distanceTo(held.get(m)) + " during the break");
                }
            } finally {
                Workforce.paused = wasPaused[0];
            }
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    // --- paths ------------------------------------------------------------------------------------------------------

    /**
     * Asked every tick, a worker plans one path a 40 ticks (one a think when it is urgent); a stop it cannot reach is
     * tried twice more, then skipped with "Can't get there. Moving on." and not tried again for a while.
     */
    @GameTest(template = "site34", timeoutTicks = 300, batch = "staff_rules")
    public static void pathsAreRationed(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 5, 2));
        AABB box = box(h, CLOCK);
        Mob calm = hired(h, p, EntityType.ZOMBIE, new BlockPos(8, 5, 8));
        Mob fighting = hired(h, p, EntityType.ZOMBIE, new BlockPos(8, 5, 16));
        Mob stuck = hired(h, p, EntityType.ZOMBIE, new BlockPos(8, 5, 26));
        BlockPos a1 = stop(h, new BlockPos(28, 5, 6)), a2 = stop(h, new BlockPos(28, 5, 10));
        BlockPos b1 = stop(h, new BlockPos(28, 5, 14)), b2 = stop(h, new BlockPos(28, 5, 18));
        // a chest sealed in stone: no way to it
        fill(h, new BlockPos(26, 5, 24), new BlockPos(30, 7, 28), Blocks.STONE);
        BlockPos sealed = stop(h, new BlockPos(28, 6, 26));
        String[] said = {null};
        Requests requests = new Requests();
        requests.watch(calm);
        requests.watch(fighting);
        requests.watch(stuck);
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t > 250) return;
            // a new stop every tick: the ration, not "still on its way", is what holds the plans back
            WorkerPaths.go(calm, Workforce.data(calm), t % 2 == 0 ? a1 : a2, 1.0, box, false);
            WorkerPaths.go(fighting, Workforce.data(fighting), t % 2 == 0 ? b1 : b2, 1.0, box, true);
            WorkerData sd = Workforce.data(stuck);
            boolean was = WorkerPaths.givenUp(sd);
            WorkerPaths.go(stuck, sd, sealed, 1.0, box, false);
            said[0] = giveUpLine(sd, was, said[0]);
            requests.tick();
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 250, "asking");
            int tt = Workforce.thinkTicks();
            h.assertTrue(requests.of(calm) <= 250 / WorkerPaths.PATH_TICKS + 1 && requests.of(calm) >= 250 / WorkerPaths.PATH_TICKS - 1,
                    requests.of(calm) + " plans in 250 ticks, one a " + WorkerPaths.PATH_TICKS + " allowed");
            h.assertTrue(requests.of(fighting) <= 250 / tt + 1 && requests.of(fighting) >= 250 / tt - 2,
                    requests.of(fighting) + " urgent plans in 250 ticks, one a think (" + tt + ") allowed");
            h.assertValueEqual(requests.of(stuck), 1 + WorkerPaths.RETRIES, "tries at a stop with no way to it");
            WorkerData d = Workforce.data(stuck);
            h.assertTrue(WorkerPaths.givenUp(d), "it has not given up on the sealed stop");
            saidCantGetThere(h, said[0]);
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    /** A stop reachable only by going round outside the box is "Can't get there", and the worker never leaves the box. */
    @GameTest(template = "site34", timeoutTicks = 300, batch = "staff_rules")
    public static void pathsStayInTheBox(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(30, 5, 2));
        AABB box = box(h, CLOCK);
        // a wall across the whole box (x 1 to 33); the only way round is column 0, just outside it
        fill(h, new BlockPos(1, 5, 20), new BlockPos(33, 7, 20), Blocks.STONE);
        Mob zombie = hired(h, p, EntityType.ZOMBIE, new BlockPos(3, 5, 16));
        BlockPos chest = stop(h, new BlockPos(3, 5, 24));
        WorkerData d = Workforce.data(zombie);
        boolean[] left = {false};
        String[] said = {null};
        String[] control = {null};
        h.onEachTick(() -> {
            if (h.getTick() < 5) return;
            if (control[0] == null) {
                // there is a way, round the end of the wall (once it has landed: nothing plans in the air)
                Path round = zombie.getNavigation().createPath(chest.north(), 0);
                boolean outside = false;
                if (round != null) for (int i = 0; i < round.getNodeCount(); i++) {
                    outside |= !box.contains(round.getNode(i).x + 0.5, round.getNode(i).y + 0.1, round.getNode(i).z + 0.5);
                }
                control[0] = round == null || !round.canReach() ? "no way round the wall at all: the test proves nothing"
                        : !outside ? "the way round stays inside the box: the test proves nothing" : "";
                zombie.getNavigation().stop();
            }
            if (!WorkerPaths.givenUp(d)) {
                WorkerPaths.go(zombie, d, chest, 1.0, box, false);
                said[0] = giveUpLine(d, false, said[0]);
            }
            left[0] |= !box.contains(zombie.position());
        });
        h.succeedWhen(() -> {
            h.assertTrue("".equals(control[0]), control[0] == null ? "planning the way round" : control[0]);
            h.assertFalse(left[0], "the zombie left its box");
            h.assertTrue(WorkerPaths.givenUp(d), "it has not given up yet");
            saidCantGetThere(h, said[0]);
            h.assertFalse(WorkerPaths.arrived(zombie, chest), "it got there");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    /**
     * Fix 15: a stop in a chunk that is not there is skipped unread ("Can't get there"), and nothing loads it. (A GameTest
     * cannot unload a chunk under a test, so the stop is put where no chunk is loaded: the guard itself is what is tested.
     * The real unloaded case is the client check.)
     */
    @GameTest(template = "site34", timeoutTicks = 100, batch = "staff_rules")
    public static void aStopInAnUnloadedChunkIsSkipped(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 5, 2));
        ServerLevel level = h.getLevel();
        Mob zombie = hired(h, p, EntityType.ZOMBIE, new BlockPos(9, 5, 9));
        Mob ghast = hired(h, p, EntityType.GHAST, new BlockPos(20, 6, 20));
        BlockPos far = h.absolutePos(new BlockPos(9, 5, 9)).offset(100_000, 0, 0);
        h.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "the far chunk is loaded already");
        int[] loads = {0};
        Consumer<ChunkEvent.Load> counter = e -> loads[0]++;
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ChunkEvent.Load.class, counter);
        WorkerBrain.THINKING.set(true);
        try {
            for (Mob m : List.of(zombie, ghast)) {
                WorkerData d = Workforce.data(m);
                h.assertValueEqual(WorkerPaths.go(m, d, far, 1.0, null, false), WorkerPaths.Result.FAILED, "a stop in no chunk");
                h.assertTrue(WorkerPaths.givenUp(d), "it did not skip the stop at once");
                saidCantGetThere(h, d);
                // the far stop's box: no point found, nothing read
                AABB there = new AABB(far).inflate(16);
                h.assertTrue(Movers.randomPoint(m, there) == null, "a point found in a chunk that is not there");
            }
            h.assertFalse(WorkerPaths.loaded(level, far), "the guard says the far chunk is there");
        } finally {
            WorkerBrain.THINKING.set(false);
            NeoForge.EVENT_BUS.unregister(counter);
        }
        h.assertValueEqual(loads[0], 0, "chunks loaded");
        h.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "the far chunk was loaded");
        StaffMoverGameTests.done(h, p);
    }

    /** Staff with nothing to do (no clock, no card) plan no paths at all. */
    @GameTest(template = "site34", timeoutTicks = 460, batch = "staff_rules")
    public static void idleBrainsRequestNoPaths(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(17, 5, 21));
        StaffMoverGameTests.channel(h, 13, Blocks.WATER);
        List<Mob> idle = List.of(hired(h, p, EntityType.ZOMBIE, new BlockPos(15, 5, 17)), hired(h, p, EntityType.COW, new BlockPos(19, 5, 17)),
                hired(h, p, EntityType.GHAST, new BlockPos(17, 8, 19)), hired(h, p, EntityType.SLIME, new BlockPos(17, 5, 15)),
                hired(h, p, EntityType.SQUID, new BlockPos(17, 3, 13)));
        Requests requests = new Requests();
        for (Mob m : idle) requests.watch(m);
        h.onEachTick(requests::tick);
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() >= 400, "watching");
            for (Mob m : idle) h.assertValueEqual(requests.of(m), 0, "paths planned by an idle " + m.getType().getDescription().getString());
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    // --- chunks -----------------------------------------------------------------------------------------------------

    /**
     * A full site of 8 at work for 2,400 ticks (two haul lines, a sorting line and its return, a wheat field, a patrol)
     * loads no chunk while any worker thinks. Two more workers patrol by the movers' own random points and now and then
     * are sent at a stop far away in no chunk, inside a think.
     */
    @GameTest(template = "site34", timeoutTicks = 2460, batch = "staff_site")
    public static void noChunkLoadsWhileThinking(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(17, 5, 21));
        List<Mob> staff = fullSite(h, p);
        AABB box = box(h, CLOCK);
        Mob walker = hired(h, p, EntityType.ZOMBIE, new BlockPos(12, 5, 20));
        Mob flier = hired(h, p, EntityType.GHAST, new BlockPos(22, 8, 12));
        BlockPos far = h.absolutePos(CLOCK).offset(0, 0, 90_000);
        int[] loads = {0};
        Consumer<ChunkEvent.Load> counter = e -> {
            if (WorkerBrain.THINKING.get()) loads[0]++;
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ChunkEvent.Load.class, counter);
        Map<Mob, BlockPos> going = new HashMap<>();
        h.onEachTick(() -> {
            long t = h.getTick();
            for (Mob m : List.of(walker, flier)) {
                if ((t + m.getId()) % 10 != 0) continue;
                WorkerData d = Workforce.data(m);
                if (d == null) continue;
                WorkerBrain.THINKING.set(true);
                try {
                    BlockPos to = going.get(m);
                    if (to == null || WorkerPaths.arrived(m, to) || WorkerPaths.givenUp(d)) {
                        to = t % 400 < 10 ? far : Movers.randomPoint(m, box);
                        if (to != null) going.put(m, to);
                    }
                    if (to != null) WorkerPaths.go(m, d, to, 1.0, box, false);
                } finally {
                    WorkerBrain.THINKING.set(false);
                }
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() >= 2400, "working");
            NeoForge.EVENT_BUS.unregister(counter);
            h.assertValueEqual(loads[0], 0, "chunks loaded while staff thought");
            for (Mob m : staff) h.assertTrue(Workforce.present(m), "a worker of the site is gone");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    /**
     * A full site of 8 villagers on a redstone shift (the clock powered, so the time of day does not matter): a clock at
     * {@link StaffMoverGameTests#CLOCK} with eight cards in its rack (cobblestone hauled A to B and back, a sorting line
     * and its return haul, two farmers on a ripe wheat field, two guards on a patrol of the whole site).
     */
    static List<Mob> fullSite(GameTestHelper h, ServerPlayer p) {
        ServerLevel level = h.getLevel();
        h.setBlock(CLOCK, WorkRegistry.TIME_CLOCK.get());
        h.setBlock(CLOCK.east(), Blocks.REDSTONE_BLOCK);
        TimeClockBlockEntity clock = (TimeClockBlockEntity) h.getBlockEntity(CLOCK);
        clock.setOwner(p);
        GlobalPos site = GlobalPos.of(level.dimension(), h.absolutePos(CLOCK));
        BlockPos a = stop(h, new BlockPos(4, 5, 4)), b = stop(h, new BlockPos(4, 5, 30));
        BlockPos sortIn = stop(h, new BlockPos(30, 5, 4)), cobble = stop(h, new BlockPos(30, 5, 8)), rest = stop(h, new BlockPos(30, 5, 12));
        stock(h, a, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64));
        stock(h, sortIn, new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.DIRT, 32), new ItemStack(Items.SAND, 32), new ItemStack(Items.COBBLESTONE, 32));
        // a ripe wheat field, 6 x 6, by water
        for (BlockPos f : BlockPos.betweenClosed(new BlockPos(21, 4, 21), new BlockPos(26, 4, 26))) {
            h.setBlock(f, Blocks.FARMLAND);
            h.setBlock(f.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        }
        h.setBlock(new BlockPos(27, 4, 23), Blocks.WATER);
        List<Order> orders = List.of(
                haul(site, a, b), haul(site, b, a),
                new Order(Optional.of(site), WorkTable.SORTING, List.of(pick(sortIn), drop(cobble, new ItemStack(Items.COBBLESTONE), false), drop(rest, ItemStack.EMPTY, true)),
                        List.of(), "Sorting"),
                new Order(Optional.of(site), WorkTable.HAULING, List.of(pick(cobble), pick(rest), drop(sortIn, ItemStack.EMPTY, false)), List.of(), "Returns"),
                field(site, h, WorkTable.FARMING, new BlockPos(21, 5, 21), new BlockPos(26, 5, 26)),
                field(site, h, WorkTable.FARMING, new BlockPos(21, 5, 21), new BlockPos(26, 5, 26)),
                field(site, h, WorkTable.SECURITY, new BlockPos(2, 5, 2), new BlockPos(32, 5, 32)),
                field(site, h, WorkTable.SECURITY, new BlockPos(2, 5, 2), new BlockPos(32, 5, 32)));
        for (int i = 0; i < orders.size(); i++) {
            ItemStack card = new ItemStack(WorkRegistry.JOB_CARD.get());
            card.set(WorkRegistry.JOB_CARD_DATA.get(), new Order.Card(orders.get(i), p.getGameProfile().getName(), 0));
            clock.rack().setStackInSlot(i, card);
        }
        List<Mob> staff = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Mob v = hired(h, p, EntityType.VILLAGER, new BlockPos(13 + (i % 4) * 2, 5, 13 + (i / 4) * 6));
            Workforce.data(v).shift = WorkerData.Shift.REDSTONE;
            h.assertTrue(clock.join(v), "the clock had no room");
            staff.add(v);
        }
        return staff;
    }

    static Order haul(GlobalPos site, BlockPos from, BlockPos to) {
        return new Order(Optional.of(site), WorkTable.HAULING, List.of(pick(from), drop(to, ItemStack.EMPTY, false)), List.of(), "Haul");
    }

    static Order field(GlobalPos site, GameTestHelper h, String job, BlockPos a, BlockPos b) {
        return new Order(Optional.of(site), job, List.of(), List.of(new Order.Area(h.absolutePos(a), h.absolutePos(b))), job);
    }

    static Order.Stop pick(BlockPos at) {
        return new Order.Stop(at, Direction.UP, false, ItemStack.EMPTY, false);
    }

    static Order.Stop drop(BlockPos at, ItemStack filter, boolean everythingElse) {
        return new Order.Stop(at, Direction.UP, true, filter, everythingElse);
    }

    static void stock(GameTestHelper h, BlockPos abs, ItemStack... stacks) {
        if (h.getLevel().getBlockEntity(abs) instanceof ChestBlockEntity chest) {
            for (int i = 0; i < stacks.length; i++) chest.setItem(i, stacks[i]);
        }
    }
}
