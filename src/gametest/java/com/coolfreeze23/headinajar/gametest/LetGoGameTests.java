package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.mixin.MobAccessor;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.work.LetGo;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/**
 * Let go (0.7.0): two presses within 3 seconds, the lanyard, Form 45-S and the Hard Hat back, the file's
 * "restructuring", the notice period's walk away with its head down, Security seeing them out, and afterwards an
 * ordinary creature again (it may despawn as it could before). {@code platform}: 15 x 6 x 15, the floor's top at helper
 * y 1; {@code platform32}: 32 x 10 x 32, the floor's top at helper y 5.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class LetGoGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** Hired through the Lanyard, in a Hard Hat against the test's sun. */
    static <T extends Mob> T hired(GameTestHelper h, ServerPlayer p, EntityType<T> type, BlockPos at) {
        T mob = h.spawn(type, at);
        if (mob instanceof Zombie) mob.setHealth(mob.getMaxHealth() / 2);
        h.assertTrue(Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), mob), "not hired: " + Refusals.lastDelivered(p));
        if (mob instanceof Zombie) mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(WorkRegistry.HARD_HAT.get()));
        return mob;
    }

    static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void letGoNeedsTwoPressesInThreeSeconds(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "letgo", FLOOR.south(3));
        Cow cow = hired(h, p, EntityType.COW, FLOOR);
        Workforce.requestLetGo(p, cow);
        h.assertTrue(Workforce.data(cow).state != WorkerData.State.NOTICE, "one press let it go");
        h.runAfterDelay(Workforce.CONFIRM_TICKS + 5, () -> {
            // too late: this press asks again
            Workforce.requestLetGo(p, cow);
            h.assertTrue(Workforce.data(cow).state != WorkerData.State.NOTICE, "a second press after 3 seconds let it go");
            h.runAfterDelay(20, () -> {
                Workforce.requestLetGo(p, cow);
                h.assertValueEqual(Workforce.data(cow).state, WorkerData.State.NOTICE, "two presses within 3 seconds");
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform")
    public static void letGoReturnsTheLanyardTheFormAndTheHat(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "letgo", FLOOR.south(3));
        Zombie zombie = hired(h, p, EntityType.ZOMBIE, FLOOR);
        WorkerData d = Workforce.data(zombie);
        d.carried.add(new ItemStack(net.minecraft.world.item.Items.WHEAT, 5));
        Workforce.letGo(p, zombie);
        h.assertValueEqual(count(p, WorkRegistry.STAFF_LANYARD.get()), 1, "lanyards back");
        h.assertValueEqual(count(p, WorkRegistry.SEPARATION_NOTICE.get()), 1, "Form 45-S");
        h.assertValueEqual(count(p, WorkRegistry.HARD_HAT.get()), 1, "the Hard Hat back");
        h.assertTrue(zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "the hat is still on its head");
        h.assertTrue(d.carried.isEmpty(), "the carry was not set down");
        h.assertFalse(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, zombie.getBoundingBox().inflate(2),
                e -> e.getItem().is(net.minecraft.world.item.Items.WHEAT)).isEmpty(), "the carry is not at its feet");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theFileSaysRestructuring(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "letgo", FLOOR.south(3));
        Cow cow = hired(h, p, EntityType.COW, FLOOR);
        Workforce.letGo(p, cow);
        StaffFile f = Staff.of(cow);
        h.assertTrue(f != null && f.left != null && LetGo.REASON.equals(f.left.why()), "the file's reason for leaving");
        h.assertValueEqual(f.history.get(f.history.size() - 1).a(), LetGo.REASON, "the last history line");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void onlyTheEmployerLetsGo(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "letgo", FLOOR.south(3));
        ServerPlayer other = TestPlayers.at(h, "stranger", FLOOR.south(4));
        Cow cow = hired(h, p, EntityType.COW, FLOOR);
        Workforce.requestLetGo(other, cow);
        Workforce.requestLetGo(other, cow);
        h.assertValueEqual(Refusals.lastDelivered(other), "staff.let_go_not_yours", "a stranger's answer");
        h.assertTrue(Workforce.data(cow).state != WorkerData.State.NOTICE, "a stranger let it go");
        done(h, p, other);
    }

    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void aFiredZombieWalksTwelveAwayAndCanDespawnAgain(GameTestHelper h) {
        // the player at the platform's south edge, so the walk away (north) stays on the platform
        BlockPos floor = new BlockPos(16, 6, 25);
        ServerPlayer p = TestPlayers.at(h, "letgo", floor.south(3));
        Zombie zombie = h.spawn(EntityType.ZOMBIE, floor);
        ((MobAccessor) zombie).headinajar$setPersistent(false);
        zombie.setHealth(zombie.getMaxHealth() / 2);
        h.assertTrue(Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), zombie), "not hired");
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
        h.assertTrue(zombie.isPersistenceRequired(), "a worker may despawn");
        Workforce.letGo(p, zombie);
        h.runAfterDelay(Workforce.NOTICE_TICKS / 2, () -> h.assertTrue(zombie.getXRot() >= LetGo.DROOP - 1, "its head is not down"));
        h.runAfterDelay(Workforce.NOTICE_TICKS + 5, () -> {
            h.assertFalse(Workforce.isWorker(zombie), "still staff after the notice");
            h.assertFalse(zombie.isPersistenceRequired(), "the persistence it had before was not put back");
            double away = zombie.position().distanceTo(p.position());
            h.assertTrue(away >= 8, "it walked only to " + String.format("%.1f", away) + " blocks from the player");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 120)
    public static void securitySeesThemOut(GameTestHelper h) {
        BlockPos floor = new BlockPos(16, 6, 16);
        ServerPlayer p = TestPlayers.at(h, "letgo", floor.south(4));
        h.setBlock(floor.north(4), WorkRegistry.TIME_CLOCK.get());
        TimeClockBlockEntity clock = (TimeClockBlockEntity) h.getBlockEntity(floor.north(4));
        clock.setOwner(p);
        Zombie guard = hired(h, p, EntityType.ZOMBIE, floor.east(3));
        Cow leaver = hired(h, p, EntityType.COW, floor.west(3));
        clock.join(guard);
        clock.join(leaver);
        GlobalPos site = clock.site();
        ItemStack card = new ItemStack(WorkRegistry.JOB_CARD.get());
        card.set(WorkRegistry.JOB_CARD_DATA.get(), new Order.Card(new Order(Optional.of(site), WorkTable.SECURITY, java.util.List.of(), java.util.List.of(), ""), "", 0));
        Workforce.data(guard).card = card;
        Workforce.letGo(p, leaver);
        h.assertTrue(LetGo.isEscort(guard), "the Security colleague is not seeing it out");
        h.runAfterDelay(60, () -> {
            h.assertTrue(guard.distanceTo(leaver) < 6, "the escort is " + String.format("%.1f", guard.distanceTo(leaver)) + " blocks off");
            done(h, p);
        });
    }
}
