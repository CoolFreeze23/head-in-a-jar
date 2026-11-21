package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.Movers;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerPaths;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.7.0, how workers get about (staff/work/Movers): one of each movement family's species reaches a stop 16 blocks away
 * inside its site's box, and the blocks a second each one walks, flies, hops, swims or paddles at (the work table's
 * {@code walk} numbers are tuned from these: about 1.5 to 2.5, horses a quarter more; nobody sprints).
 * <p>
 * {@code site34}: 34 x 18 x 34, dirt under a grass floor; the grass top is helper y 5 (a mob stands at y 5), a clock at
 * (17, 5, 17) has its whole 33 x 33 box inside. The stops are chests at x 25, the workers start at x 9.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class StaffMoverGameTests {
    static final BlockPos CLOCK = new BlockPos(17, 5, 17);
    static final int FROM_X = 9, TO_X = 25;
    /** Blocks a second the walk numbers aim at. */
    static final double SLOWEST = 1.5, FASTEST = 2.5, HORSE = 1.25;

    static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        p.moveTo(v.x, v.y, v.z, 0, 0);
        return p;
    }

    static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** The box of a site whose clock stands at {@code clock} (helper coordinates). */
    static AABB box(GameTestHelper h, BlockPos clock) {
        return Workforce.box(h.absolutePos(clock));
    }

    /** A creature hired by {@code p} through the Lanyard (a hostile worn down to below half first), kept from burning. */
    static <T extends Mob> T hired(GameTestHelper h, ServerPlayer p, EntityType<T> type, BlockPos at) {
        T mob = h.spawn(type, at);
        if (mob instanceof Slime slime) slime.setSize(2, true);
        if (mob instanceof IronGolem golem) golem.setPlayerCreated(true);
        if (mob instanceof Enemy) mob.setHealth(mob.getMaxHealth() * 0.4f);
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, -1, 0, false, false));
        ItemStack lanyard = new ItemStack(WorkRegistry.STAFF_LANYARD.get());
        h.assertTrue(Workforce.useLanyard(p, lanyard, mob), "the " + type.getDescription().getString() + " was refused: " + Refusals.lastDelivered(p));
        return mob;
    }

    static void fill(GameTestHelper h, BlockPos a, BlockPos b, Block block) {
        for (BlockPos p : BlockPos.betweenClosed(a, b)) h.setBlock(p, block);
    }

    /** One worker on its way to one stop, and when it got there. */
    static final class Trip {
        final Mob mob;
        final BlockPos stop;
        Vec3 start;
        long started = -1;
        long took = -1;
        Vec3 end;

        Trip(Mob mob, BlockPos stop, long ignored) {
            this.mob = mob;
            this.stop = stop;
        }

        double bps() {
            return took <= 0 ? 0 : Math.sqrt(Math.pow(end.x - start.x, 2) + Math.pow(end.z - start.z, 2)) / took * 20.0;
        }

        String name() {
            return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
        }
    }

    /**
     * Asks for each trip every tick (as a job asks every think; the ration holds it to one plan a 40 ticks) until every
     * worker is at its stop, then (when {@code timed}) checks each one's blocks a second against the aim.
     */
    static void travel(GameTestHelper h, AABB box, List<Trip> trips, boolean timed, ServerPlayer... players) {
        // a piglin measured in the Overworld would zombify on the way (a quirk with tests of its own)
        for (Trip t : trips) if (t.mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin p) p.setImmuneToZombification(true);
        h.onEachTick(() -> {
            for (Trip t : trips) {
                if (t.took >= 0) continue;
                if (t.started >= 0 && WorkerPaths.arrived(t.mob, t.stop)) {
                    t.took = h.getTick() - t.started;
                    t.end = t.mob.position();
                    HeadInAJar.LOGGER.info("Movers: {} ({}) reached its stop {} blocks away in {} ticks: {} blocks a second",
                            t.name(), Movers.family(t.mob), String.format("%.1f", t.start.distanceTo(Vec3.atCenterOf(t.stop))), t.took,
                            String.format("%.2f", t.bps()));
                    continue;
                }
                WorkerData d = Workforce.data(t.mob);
                if (d == null) continue;
                WorkerPaths.Result r = WorkerPaths.go(t.mob, d, t.stop, 1.0, box, false);
                // timed from its first plan (a mob just put down plans nothing until it has landed)
                if (r == WorkerPaths.Result.WALKING && t.started < 0) {
                    t.started = h.getTick();
                    t.start = t.mob.position();
                }
            }
        });
        h.succeedWhen(() -> {
            for (Trip t : trips) {
                h.assertTrue(t.took >= 0, t.name() + " (" + Movers.family(t.mob) + ") has not reached its stop: at "
                        + h.relativeVec(t.mob.position()) + ", the stop at " + h.relativePos(t.stop) + ", status "
                        + (Workforce.data(t.mob) == null ? "no longer staff" : Workforce.data(t.mob).status));
            }
            if (timed) {
                for (Trip t : trips) {
                    double lo = SLOWEST, hi = FASTEST;
                    if (t.mob instanceof Horse) {
                        lo *= HORSE;
                        hi *= HORSE;
                    }
                    double v = t.bps();
                    h.assertTrue(v >= lo - 0.05 && v <= hi + 0.05, String.format("%s goes %.2f blocks a second (aim %.2f to %.2f)", t.name(), v, lo, hi));
                }
            }
            for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    /** A chest for a stop at helper {@code at}; returns its absolute position. */
    static BlockPos stop(GameTestHelper h, BlockPos at) {
        h.setBlock(at, Blocks.CHEST);
        return h.absolutePos(at);
    }

    static void one(GameTestHelper h, EntityType<? extends Mob> type, BlockPos from, BlockPos to, boolean timed) {
        ServerPlayer p = player(h, new BlockPos(2, 5, 2));
        Mob mob = hired(h, p, type, from);
        BlockPos stop = stop(h, to);
        travel(h, box(h, CLOCK), List.of(new Trip(mob, stop, h.getTick())), timed, p);
    }

    /** Several species side by side, each with its own lane and stop. */
    static void lanes(GameTestHelper h, List<EntityType<? extends Mob>> types, boolean timed) {
        ServerPlayer p = player(h, new BlockPos(2, 5, 2));
        List<Trip> trips = new ArrayList<>();
        int z = 3;
        for (EntityType<? extends Mob> type : types) {
            Mob mob = hired(h, p, type, new BlockPos(FROM_X, 5, z));
            trips.add(new Trip(mob, stop(h, new BlockPos(TO_X, 5, z)), h.getTick()));
            z += 4;
        }
        travel(h, box(h, CLOCK), trips, timed, p);
    }

    // --- one of each family reaches a stop 16 blocks away ----------------------------------------------------------

    @GameTest(template = "site34", timeoutTicks = 400)
    public static void walkReachesAStop(GameTestHelper h) {
        one(h, EntityType.ZOMBIE, new BlockPos(FROM_X, 5, 17), new BlockPos(TO_X, 5, 17), true);
    }

    @GameTest(template = "site34", timeoutTicks = 500)
    public static void walkersKeepToAboutTwoBlocksASecond(GameTestHelper h) {
        lanes(h, List.of(EntityType.VILLAGER, EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.HORSE,
                EntityType.IRON_GOLEM, EntityType.SKELETON, EntityType.PIGLIN), true);
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void climbReachesAHighStop(GameTestHelper h) {
        // a 3 x 3 platform four blocks high; the stop is its top middle block
        fill(h, new BlockPos(24, 5, 16), new BlockPos(26, 8, 18), Blocks.STONE);
        ServerPlayer p = player(h, new BlockPos(2, 5, 2));
        Mob spider = hired(h, p, EntityType.SPIDER, new BlockPos(FROM_X, 5, 17));
        BlockPos stop = h.absolutePos(new BlockPos(25, 8, 17));
        travel(h, box(h, CLOCK), List.of(new Trip(spider, stop, h.getTick())), false, p);
    }

    @GameTest(template = "site34", timeoutTicks = 500)
    public static void climbersWalkTheFlatToo(GameTestHelper h) {
        lanes(h, List.of(EntityType.SPIDER, EntityType.CAVE_SPIDER), true);
    }

    @GameTest(template = "site34", timeoutTicks = 500)
    public static void fliersReachAStop(GameTestHelper h) {
        lanes(h, List.of(EntityType.ALLAY, EntityType.BEE, EntityType.PARROT), true);
    }

    @GameTest(template = "site34", timeoutTicks = 500)
    public static void freeFliersReachAStop(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 5, 2));
        List<Trip> trips = new ArrayList<>();
        Mob ghast = hired(h, p, EntityType.GHAST, new BlockPos(FROM_X, 5, 17));
        trips.add(new Trip(ghast, stop(h, new BlockPos(TO_X, 5, 17)), h.getTick()));
        Mob blaze = hired(h, p, EntityType.BLAZE, new BlockPos(FROM_X, 5, 5));
        trips.add(new Trip(blaze, stop(h, new BlockPos(TO_X, 5, 5)), h.getTick()));
        Mob phantom = hired(h, p, EntityType.PHANTOM, new BlockPos(FROM_X, 7, 29));
        trips.add(new Trip(phantom, stop(h, new BlockPos(TO_X, 5, 29)), h.getTick()));
        travel(h, box(h, CLOCK), trips, true, p);
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void aFreeFlierGoesOverAWall(GameTestHelper h) {
        // a wall four high across the blaze's way: the straight line is blocked, up and over is not
        fill(h, new BlockPos(17, 5, 3), new BlockPos(17, 8, 11), Blocks.STONE);
        ServerPlayer p = player(h, new BlockPos(2, 5, 2));
        Mob blaze = hired(h, p, EntityType.BLAZE, new BlockPos(FROM_X, 5, 7));
        travel(h, box(h, CLOCK), List.of(new Trip(blaze, stop(h, new BlockPos(TO_X, 5, 7)), h.getTick())), false, p);
    }

    @GameTest(template = "site34", timeoutTicks = 800)
    public static void hopReachesAStop(GameTestHelper h) {
        lanes(h, List.of(EntityType.SLIME, EntityType.MAGMA_CUBE), true);
    }

    /** A channel of {@code fluid}, two deep, along x from 8 to 26 at lane {@code z}, with a chest at its end. */
    static BlockPos channel(GameTestHelper h, int z, Block fluid) {
        fill(h, new BlockPos(7, 2, z - 2), new BlockPos(27, 4, z + 2), Blocks.STONE);
        fill(h, new BlockPos(8, 3, z - 1), new BlockPos(26, 4, z + 1), fluid);
        return stop(h, new BlockPos(27, 4, z));
    }

    static void swimmers(GameTestHelper h, List<EntityType<? extends Mob>> types, Block fluid, int y, boolean timed) {
        ServerPlayer p = player(h, new BlockPos(2, 5, 2));
        List<Trip> trips = new ArrayList<>();
        int z = 4;
        for (EntityType<? extends Mob> type : types) {
            BlockPos stop = channel(h, z, fluid);
            Mob mob = hired(h, p, type, new BlockPos(FROM_X, y, z));
            trips.add(new Trip(mob, stop, h.getTick()));
            z += 6;
        }
        travel(h, box(h, CLOCK), trips, timed, p);
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void waterReachesAStop(GameTestHelper h) {
        swimmers(h, List.of(EntityType.DOLPHIN, EntityType.TURTLE, EntityType.AXOLOTL), Blocks.WATER, 3, true);
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void squidReachesAStop(GameTestHelper h) {
        swimmers(h, List.of(EntityType.SQUID, EntityType.GLOW_SQUID), Blocks.WATER, 3, true);
    }

    @GameTest(template = "site34", timeoutTicks = 300)
    public static void teleportReachesAStop(GameTestHelper h) {
        one(h, EntityType.ENDERMAN, new BlockPos(FROM_X, 5, 17), new BlockPos(TO_X, 5, 17), false);
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void lavaReachesAStop(GameTestHelper h) {
        swimmers(h, List.of(EntityType.STRIDER), Blocks.LAVA, 4, true);
    }

    // --- the gate at hire ------------------------------------------------------------------------------------------

    /** Every species the Lanyard would hire has a family that fits the creature (else it would ship half working). */
    @GameTest(template = "site34", timeoutTicks = 40)
    public static void everyHireableSpeciesCanMove(GameTestHelper h) {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            WorkTable.Spec spec = WorkTable.of(type);
            if (spec == null || spec.refusal() != null) continue;
            Entity e = type.create(h.getLevel());
            if (!(e instanceof Mob mob)) {
                if (e != null) e.discard();
                continue;
            }
            checked++;
            if (!Movers.FAMILIES.contains(spec.move()) || !Movers.canMove(mob, spec)) {
                wrong.add(BuiltInRegistries.ENTITY_TYPE.getKey(type) + " (" + spec.move() + ")");
            }
            mob.discard();
        }
        h.assertTrue(checked > 40, "only " + checked + " species have a work row");
        h.assertTrue(wrong.isEmpty(), "these would be refused at hire, their family does not fit them: " + wrong);
        h.succeed();
    }

    /** A family that does not fit the creature, or one the mod does not know, is refused ({@code lanyard.unknown}). */
    @GameTest(template = "site34", timeoutTicks = 40)
    public static void aFamilyThatCannotMoveItIsRefused(GameTestHelper h) {
        Mob cow = h.spawn(EntityType.COW, new BlockPos(9, 5, 9));
        WorkTable.Spec spec = WorkTable.of(cow);
        Map<String, Boolean> expect = new LinkedHashMap<>();
        expect.put(Movers.WALK, true);
        expect.put(Movers.HOP, false);
        expect.put(Movers.SQUID, false);
        expect.put(Movers.FLY, false);
        expect.put(Movers.CLIMB, false);
        expect.put("burrow", false);
        for (var e : expect.entrySet()) {
            WorkTable.Spec s = new WorkTable.Spec(spec.hire(), spec.jobs(), spec.cannot(), spec.shift(), spec.carry(), spec.carryItems(),
                    spec.hands(), spec.walk(), e.getKey(), spec.attack(), spec.melee(), spec.quirks(), spec.card(), spec.water(), Map.of());
            h.assertValueEqual(Movers.canMove(cow, s), e.getValue(), "a cow moving by " + e.getKey());
        }
        h.succeed();
    }

    // --- random points ---------------------------------------------------------------------------------------------

    /** Patrol points and the Let go walk: inside the box, loaded, for a walker and for the movers that pick their own. */
    @GameTest(template = "site34", timeoutTicks = 60)
    public static void randomPointsStayInTheBox(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 5, 2));
        channel(h, 29, Blocks.WATER);
        AABB small = new AABB(h.absolutePos(new BlockPos(5, -3, 5))).minmax(new AABB(h.absolutePos(new BlockPos(29, 13, 31))));
        Map<Mob, Integer> found = new LinkedHashMap<>();
        for (Mob mob : List.of(hired(h, p, EntityType.ZOMBIE, new BlockPos(12, 5, 12)), hired(h, p, EntityType.GHAST, new BlockPos(17, 6, 17)),
                hired(h, p, EntityType.SLIME, new BlockPos(22, 5, 12)), hired(h, p, EntityType.SQUID, new BlockPos(15, 3, 29)))) {
            int n = 0;
            for (int i = 0; i < 30; i++) {
                BlockPos at = Movers.randomPoint(mob, small);
                if (at == null) continue;
                n++;
                h.assertTrue(small.contains(Vec3.atCenterOf(at)), mob.getType().getDescription().getString() + " picked " + h.relativePos(at) + ", outside the box");
                h.assertTrue(WorkerPaths.loaded(h.getLevel(), at), "a point in a chunk that is not loaded");
            }
            found.put(mob, n);
        }
        for (var e : found.entrySet()) h.assertTrue(e.getValue() >= 5, e.getKey().getType().getDescription().getString() + " found only " + e.getValue() + " points in 30 tries");
        // away from someone: the Let go walk
        Mob walker = found.keySet().iterator().next();
        Vec3 from = p.position();
        int away = 0;
        for (int i = 0; i < 20; i++) {
            BlockPos at = Movers.randomPoint(walker, null, from, 12);
            if (at != null) {
                away++;
                double d = Vec3.atCenterOf(at).distanceTo(from);
                h.assertTrue(d >= 11 || d > walker.position().distanceTo(from) + 1, "a point only " + d + " away, nearer than it stood");
            }
        }
        h.assertTrue(away > 0, "no point away from the player");
        done(h, p);
    }

}
