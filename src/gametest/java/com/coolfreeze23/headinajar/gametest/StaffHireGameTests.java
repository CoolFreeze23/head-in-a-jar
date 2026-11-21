package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerIndex;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Hiring with the Staff Lanyard (0.7.0): who may be hired, the book's refusals in its order, the headcount cap, the
 * server switches, and what a hire does to a creature's own life. {@code platform}: 15 x 6 x 15, the floor's top at
 * helper y 1. Hires go through the real use ({@code Workforce.useLanyard}); a refusal is read from what the player was
 * told.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class StaffHireGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    static ServerPlayer player(GameTestHelper h) {
        return TestPlayers.at(h, "hire", FLOOR.south(3));
    }

    static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    static boolean lanyard(ServerPlayer p, Entity target) {
        return Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), target);
    }

    /** The Lanyard on {@code target} is refused with {@code key}, and nothing is hired. */
    static void refused(GameTestHelper h, ServerPlayer p, Entity target, String key) {
        h.assertFalse(lanyard(p, target), "a " + target.getType() + " was hired");
        h.assertFalse(Workforce.isWorker(target), "a refused " + target.getType() + " is staff");
        h.assertValueEqual(Refusals.lastDelivered(p), key, "the answer for a " + target.getType());
    }

    static <T extends Mob> T quiet(GameTestHelper h, EntityType<T> type, BlockPos at) {
        T mob = h.spawn(type, at);
        mob.setNoAi(true);
        if (mob instanceof Zombie) mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return mob;
    }

    @GameTest(template = "platform", timeoutTicks = 460)
    public static void aCowIsHiredAndStopsItsVanillaLife(GameTestHelper h) {
        ServerPlayer p = player(h);
        Cow cow = h.spawn(EntityType.COW, FLOOR);
        h.assertTrue(lanyard(p, cow), "the cow was not hired");
        WorkerData d = Workforce.data(cow);
        h.assertTrue(d != null && d.state == WorkerData.State.AWAITING && p.getUUID().equals(d.employer), "the cow's record");
        StaffFile f = Staff.of(cow);
        h.assertTrue(f != null && p.getUUID().equals(f.employer) && f.staff(), "the cow's file: employed and on the payroll");
        h.assertTrue(cow.isPersistenceRequired(), "a hired cow may despawn");
        Vec3 at = cow.position();
        // no clock within 16: it stands where it was hired, awaiting assignment, for 20 seconds
        h.runAfterDelay(400, () -> {
            h.assertTrue(cow.position().distanceTo(at) < 1.0, "the cow wandered " + cow.position().distanceTo(at) + " blocks");
            h.assertValueEqual(Workforce.data(cow).state, WorkerData.State.AWAITING, "the cow's state");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void aZombieBelowHalfIsHired(GameTestHelper h) {
        ServerPlayer p = player(h);
        Zombie zombie = quiet(h, EntityType.ZOMBIE, FLOOR);
        zombie.setTarget(p);
        zombie.setHealth(zombie.getMaxHealth() * 0.5f);
        h.assertTrue(lanyard(p, zombie), "a zombie at half its health was not hired");
        h.assertTrue(zombie.getTarget() == null && !zombie.isAggressive(), "a hired zombie still wants the player");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aZombieAboveHalfIsNotReady(GameTestHelper h) {
        ServerPlayer p = player(h);
        Zombie zombie = quiet(h, EntityType.ZOMBIE, FLOOR);
        zombie.setHealth(zombie.getMaxHealth() * 0.5f + 1);
        refused(h, p, zombie, "lanyard.not_ready");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theBookRefusesWhoItRefuses(GameTestHelper h) {
        ServerPlayer p = player(h);
        Cow calf = quiet(h, EntityType.COW, FLOOR.east(2));
        calf.setAge(-24000);
        refused(h, p, calf, "lanyard.baby");
        refused(h, p, quiet(h, EntityType.IRON_GOLEM, FLOOR.west(3)), "lanyard.village_golem");
        refused(h, p, quiet(h, EntityType.CREEPER, FLOOR.north(3)), "lanyard.creeper");
        refused(h, p, quiet(h, EntityType.COD, FLOOR.north(3).east(3)), "lanyard.no_legs");
        refused(h, p, quiet(h, EntityType.TADPOLE, FLOOR.north(3).west(3)), "lanyard.no_legs");
        refused(h, p, quiet(h, EntityType.BAT, FLOOR.east(4)), "lanyard.no_legs");
        refused(h, p, quiet(h, EntityType.VEX, FLOOR.west(5)), "lanyard.vex");
        refused(h, p, quiet(h, EntityType.SHULKER, FLOOR.north(5)), "lanyard.shulker");
        refused(h, p, quiet(h, EntityType.WARDEN, FLOOR.north(5).east(4)), "lanyard.overqualified");
        refused(h, p, quiet(h, EntityType.ELDER_GUARDIAN, FLOOR.north(5).west(4)), "lanyard.overqualified");
        refused(h, p, quiet(h, EntityType.WITHER, FLOOR.south(5).east(5)), "lanyard.overqualified");
        // someone else's pet
        ServerPlayer other = TestPlayers.at(h, "owner", FLOOR.south(5));
        Wolf wolf = quiet(h, EntityType.WOLF, FLOOR.east(5));
        wolf.tame(other);
        refused(h, p, wolf, "lanyard.poach");
        // a host (its head is off)
        Cow host = quiet(h, EntityType.COW, FLOOR.west(1).north(1));
        Hosts.data(host).host = true;
        refused(h, p, host, "lanyard.host");
        // another player
        refused(h, p, other, "lanyard.player");
        // a golem someone built is hired; your own pet too
        IronGolem built = quiet(h, EntityType.IRON_GOLEM, FLOOR.south(1).west(5));
        built.setPlayerCreated(true);
        h.assertTrue(lanyard(p, built), "a built iron golem was refused: " + Refusals.lastDelivered(p));
        Wolf mine = quiet(h, EntityType.WOLF, FLOOR.south(1).east(3));
        mine.tame(p);
        h.assertTrue(lanyard(p, mine), "your own wolf was refused: " + Refusals.lastDelivered(p));
        // someone else's staff: refused, and it stays yours
        h.assertFalse(lanyard(other, built), "someone else's golem was poached");
        h.assertValueEqual(Refusals.lastDelivered(other), "lanyard.poach", "the answer for poaching");
        h.assertTrue(p.getUUID().equals(Workforce.employer(built)), "the golem changed employer");
        done(h, p, other);
    }

    @GameTest(template = "platform")
    public static void theTwentyFifthIsRefused(GameTestHelper h) {
        ServerPlayer p = player(h);
        WorkerIndex index = WorkerIndex.get(h.getLevel().getServer());
        UUID[] fake = new UUID[23];
        for (int i = 0; i < fake.length; i++) {
            fake[i] = UUID.randomUUID();
            index.seen(p.getUUID(), fake[i], 9000 + i, "minecraft:cow", WorkerIndex.at(p));
        }
        try {
            h.assertTrue(lanyard(p, quiet(h, EntityType.COW, FLOOR)), "the 24th was refused");
            refused(h, p, quiet(h, EntityType.COW, FLOOR.east(2)), "lanyard.headcount");
        } finally {
            for (UUID id : fake) index.remove(p.getUUID(), id);
        }
        done(h, p);
    }

    /** In a batch of its own: the switch is the server's. */
    @GameTest(template = "platform", batch = "staff_switches")
    public static void staffingOffRefuses(GameTestHelper h) {
        ServerPlayer p = player(h);
        Config.STAFF_ENABLED.set(false);
        try {
            refused(h, p, quiet(h, EntityType.COW, FLOOR), "lanyard.disabled");
        } finally {
            Config.STAFF_ENABLED.set(true);
        }
        done(h, p);
    }

    /** In a batch of its own: the switch is the server's. */
    @GameTest(template = "platform", batch = "staff_switches_hostiles")
    public static void hostilesOffRefusesHostilesOnly(GameTestHelper h) {
        ServerPlayer p = player(h);
        Config.HIRE_HOSTILES.set(false);
        try {
            Zombie zombie = quiet(h, EntityType.ZOMBIE, FLOOR);
            zombie.setHealth(zombie.getMaxHealth() * 0.5f);
            refused(h, p, zombie, "lanyard.no_hostiles");
            h.assertTrue(lanyard(p, quiet(h, EntityType.COW, FLOOR.east(2))), "a cow was refused with hostiles off");
        } finally {
            Config.HIRE_HOSTILES.set(true);
        }
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aHireIsFiledAndStamped(GameTestHelper h) {
        ServerPlayer p = player(h);
        Cow cow = quiet(h, EntityType.COW, FLOOR);
        lanyard(p, cow);
        StaffFile f = Staff.of(cow);
        h.assertTrue(f != null && !f.history.isEmpty() && f.history.get(f.history.size() - 1).k().equals("hired"), "the file's last line is not the hire");
        h.assertTrue(WorkerIndex.get(h.getLevel().getServer()).of(p.getUUID()).stream().anyMatch(e -> e.id.equals(cow.getUUID())), "not in the index");
        h.assertTrue(com.coolfreeze23.headinajar.genetics.Directory.get(h.getLevel().getServer())
                        .book(com.coolfreeze23.headinajar.genetics.GeneArchive.key(p)).has(com.coolfreeze23.headinajar.genetics.Directory.Stamp.HIRED, "minecraft:cow"),
                "the Directory's hired stamp");
        done(h, p);
    }
}
