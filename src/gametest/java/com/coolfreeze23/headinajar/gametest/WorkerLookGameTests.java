package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Badged;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import com.coolfreeze23.headinajar.staff.work.WorkerNet;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What others see of a worker (0.7.0): the look's colour, bits and name as the server sends them on hire, carry, a hat, a card and Let go. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class WorkerLookGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** A creature hired through the Lanyard's own path by {@code boss}. */
    private static <T extends Mob> T hired(GameTestHelper h, ServerPlayer boss, EntityType<T> type) {
        T mob = h.spawn(type, FLOOR.above());
        mob.setHealth(mob.getMaxHealth() * 0.25f);
        ItemStack lanyard = new ItemStack(WorkRegistry.STAFF_LANYARD.get());
        boss.setItemInHand(InteractionHand.MAIN_HAND, lanyard);
        h.assertTrue(Workforce.useLanyard(boss, lanyard, mob), "the " + type.getDescription().getString() + " is hired");
        return mob;
    }

    private static boolean has(Mob mob, int bit) {
        return (WorkerLooks.bits(mob) & bit) != 0;
    }

    @GameTest(template = "platform")
    public static void aHireDropsTheLoopForTenTicks(GameTestHelper h) {
        ServerPlayer boss = TestPlayers.at(h, "Boss", FLOOR.east(2));
        Mob zombie = hired(h, boss, EntityType.ZOMBIE);
        h.assertTrue(has(zombie, WorkerNet.HIRED), "just hired: the loop drops");
        h.assertTrue(WorkerLooks.colour(zombie) == Badged.TEAL, "no team: the badge is company teal");
        WorkerNet.Look look = WorkerLooks.look(zombie, boss);
        h.assertTrue(look.entity() == zombie.getId() && look.colour() == Badged.TEAL, "the payload carries the id and colour");
        h.assertFalse(look.name().getString().isEmpty(), "the employer is sent its given name");
        h.runAfterDelay(WorkerLooks.HIRE_TICKS + 1, () -> {
            h.assertFalse(has(zombie, WorkerNet.HIRED), "the drop is over after ten ticks");
            done(h, boss);
        });
    }

    @GameTest(template = "platform")
    public static void aStrangerIsNotSentTheName(GameTestHelper h) {
        ServerPlayer boss = TestPlayers.at(h, "Boss", FLOOR.east(2));
        ServerPlayer stranger = TestPlayers.at(h, "Nosy", FLOOR.west(2));
        Mob cow = hired(h, boss, EntityType.COW);
        h.assertTrue(WorkerLooks.look(cow, stranger).name().getString().isEmpty(), "a stranger sees no name");
        h.assertTrue(WorkerLooks.look(cow, stranger).colour() == Badged.TEAL, "but sees the badge");
        cow.setCustomName(net.minecraft.network.chat.Component.literal("Daisy"));
        h.assertTrue(WorkerLooks.look(cow, boss).name().getString().isEmpty(), "a name tag shows itself: no nameplate is sent");
        done(h, boss, stranger);
    }

    @GameTest(template = "platform")
    public static void carryingAndAHardHatShow(GameTestHelper h) {
        ServerPlayer boss = TestPlayers.at(h, "Boss", FLOOR.east(2));
        Mob cow = hired(h, boss, EntityType.COW);
        WorkerData d = Workforce.data(cow);
        h.assertFalse(has(cow, WorkerNet.CARRYING), "carrying nothing yet");
        d.carried.add(new ItemStack(Items.WHEAT, 16));
        WorkerLooks.changed(cow);
        h.assertTrue(has(cow, WorkerNet.CARRYING), "carrying: the crate shows");
        cow.setItemSlot(EquipmentSlot.HEAD, new ItemStack(WorkRegistry.HARD_HAT.get()));
        h.assertTrue(has(cow, WorkerNet.HAT), "a Hard Hat on");
        d.carried.clear();
        WorkerLooks.changed(cow);
        h.assertFalse(has(cow, WorkerNet.CARRYING), "set down: no crate");
        h.assertTrue(has(cow, WorkerNet.HAT), "the hat stays on");
        done(h, boss);
    }

    @GameTest(template = "platform")
    public static void aCardIsReadForTwentyTicks(GameTestHelper h) {
        ServerPlayer boss = TestPlayers.at(h, "Boss", FLOOR.east(2));
        Mob cow = hired(h, boss, EntityType.COW);
        WorkerLooks.reading(cow);
        h.assertTrue(has(cow, WorkerNet.READING), "reading the card");
        h.runAfterDelay(WorkerLooks.READ_TICKS + 1, () -> {
            h.assertFalse(has(cow, WorkerNet.READING), "read");
            done(h, boss);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 300)
    public static void letGoPopsTheBadgeThenTheLoopComesOff(GameTestHelper h) {
        ServerPlayer boss = TestPlayers.at(h, "Boss", FLOOR.east(2));
        Mob cow = hired(h, boss, EntityType.COW);
        Workforce.data(cow).carried.add(new ItemStack(Items.WHEAT));
        Workforce.letGo(boss, cow);
        h.assertTrue(has(cow, WorkerNet.NOTICE), "serving notice: the badge pops off");
        h.assertFalse(has(cow, WorkerNet.CARRYING), "the carry is set down");
        h.assertTrue(WorkerLooks.colour(cow) >= 0, "still staff until the notice ends");
        Workforce.release(cow);
        h.assertTrue(WorkerLooks.colour(cow) == -1 && WorkerLooks.bits(cow) == 0, "released: no look (the loop comes off)");
        h.assertTrue(WorkerLooks.look(cow, boss).colour() == -1, "the payload says so");
        done(h, boss);
    }
}
