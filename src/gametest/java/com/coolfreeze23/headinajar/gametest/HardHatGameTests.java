package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.item.HardHatItem;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.WorkRules;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;

/**
 * The Hard Hat (0.7.0): what puts it on and what is refused, the sun and the clock's awning, falling blocks, and a hat
 * that never wears out. {@code platform}: 15 x 6 x 15, the floor's top at helper y 1.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class HardHatGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ItemStack hat() {
        return new ItemStack(WorkRegistry.HARD_HAT.get());
    }

    /** Hired, with nothing on its head (the rule tests' hire puts a hat on zombies: taken off here). */
    private static <T extends Mob> T hire(GameTestHelper h, ServerPlayer p, EntityType<T> type, BlockPos at) {
        T mob = StaffRuleGameTests.hire(h, p, type, at);
        mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        return mob;
    }

    /** Vanilla's own question, asked as zombies, skeletons and phantoms ask it each tick. */
    private static boolean burnsNow(Mob mob) {
        try {
            Method m = Mob.class.getDeclaredMethod("isSunBurnTick");
            m.setAccessible(true);
            return (boolean) m.invoke(mob);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @GameTest(template = "platform")
    public static void anIronHelmetIsNotAHardHat(GameTestHelper h) {
        ServerPlayer p = StaffRuleGameTests.player(h, new BlockPos(5, 2, 7));
        Zombie z = hire(h, p, EntityType.ZOMBIE, FLOOR);
        Cow wild = h.spawn(EntityType.COW, new BlockPos(10, 2, 10));
        // an iron helmet offered to staff: refused, kept in hand
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HELMET));
        p.interactOn(z, InteractionHand.MAIN_HAND);
        h.assertTrue(z.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "staff took an iron helmet");
        h.assertTrue(p.getMainHandItem().is(Items.IRON_HELMET), "the iron helmet left the hand");
        h.assertTrue("hard_hat.helmet".equals(Refusals.lastDelivered(p)), "no Policy 4.1: " + Refusals.delivered(p));
        // a Hard Hat offered to a creature that is not staff: refused
        p.setItemInHand(InteractionHand.MAIN_HAND, hat());
        p.interactOn(wild, InteractionHand.MAIN_HAND);
        h.assertTrue(wild.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && p.getMainHandItem().is(WorkRegistry.HARD_HAT.get()), "a wild cow took a Hard Hat");
        h.assertTrue("hard_hat.not_staff".equals(Refusals.lastDelivered(p)), "no refusal for a hat on a wild cow: " + Refusals.delivered(p));
        // the zombie's own iron helmet comes to whoever issues the hat; the thanks are said once
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        p.interactOn(z, InteractionHand.MAIN_HAND);
        h.assertTrue(HardHatItem.wears(z), "the Hard Hat did not go on the zombie");
        h.assertTrue(p.getInventory().countItem(WorkRegistry.HARD_HAT.get()) == 0, "the Hard Hat stayed with the player");
        h.assertTrue(p.getInventory().contains(new ItemStack(Items.IRON_HELMET)), "the zombie's helmet did not come back");
        h.assertTrue(Workforce.data(z).has(WorkerData.FLAG_HAT_THANKED), "Health and Safety did not thank anyone");
        // a second hat on the same head: refused
        p.setItemInHand(InteractionHand.MAIN_HAND, hat());
        p.interactOn(z, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getMainHandItem().is(WorkRegistry.HARD_HAT.get()) && "hard_hat.already".equals(Refusals.lastDelivered(p)), "a second hat went on");
        // a player wears it as a helmet (and so on a jar's Hat Mount)
        Equipable e = Equipable.get(hat());
        h.assertTrue(e != null && e.getEquipmentSlot() == EquipmentSlot.HEAD, "the Hard Hat is not worn on the head");
        StaffRuleGameTests.done(h, p);
    }

    @GameTest(template = "platform")
    public static void aHardHatStopsBurningEvenForAPhantom(GameTestHelper h) {
        ServerPlayer p = StaffRuleGameTests.player(h, new BlockPos(3, 2, 3));
        Phantom ph = hire(h, p, EntityType.PHANTOM, new BlockPos(7, 4, 7));
        ph.setNoGravity(true);
        h.assertTrue(!WorkRules.sheltered(ph), "a hatless staff phantom away from its clock is sheltered");
        ph.setItemSlot(EquipmentSlot.HEAD, hat());
        h.assertTrue(WorkRules.sheltered(ph), "a staff phantom in a Hard Hat is not sheltered");
        for (int i = 0; i < 200; i++) if (burnsNow(ph)) h.fail("a staff phantom in a Hard Hat burns");
        // a wild phantom in a Hard Hat is not sheltered (the hat is a staff rule)
        Phantom wild = h.spawn(EntityType.PHANTOM, new BlockPos(3, 4, 10));
        wild.setNoAi(true);
        wild.setItemSlot(EquipmentSlot.HEAD, hat());
        h.assertTrue(!WorkRules.sheltered(wild), "a wild phantom is sheltered by a hat");
        StaffRuleGameTests.done(h, p);
    }

    @GameTest(template = "platform")
    public static void undeadUnderTheClocksAwningDoNotBurn(GameTestHelper h) {
        ServerPlayer p = StaffRuleGameTests.player(h, new BlockPos(3, 2, 3));
        BlockPos at = new BlockPos(7, 2, 3);
        h.setBlock(at, WorkRegistry.TIME_CLOCK.get());
        TimeClockBlockEntity clock = (TimeClockBlockEntity) h.getBlockEntity(at);
        clock.setOwner(p);
        Skeleton near = hire(h, p, EntityType.SKELETON, new BlockPos(8, 2, 4));
        Skeleton far = hire(h, p, EntityType.SKELETON, new BlockPos(7, 2, 10));
        h.assertTrue(clock.join(near) && clock.join(far), "the clock would not take them");
        h.assertTrue(WorkRules.sheltered(near), "a skeleton a block from its clock is not under the awning");
        for (int i = 0; i < 200; i++) if (burnsNow(near)) h.fail("a skeleton under its clock's awning burns");
        h.assertTrue(!WorkRules.sheltered(far), "a skeleton 7 blocks from its clock is under the awning");
        StaffRuleGameTests.done(h, p);
    }

    @GameTest(template = "platform")
    public static void fallingBlocksSpareHattedStaffAndHalvePlayers(GameTestHelper h) {
        ServerPlayer boss = StaffRuleGameTests.player(h, new BlockPos(3, 2, 3));
        Zombie z = hire(h, boss, EntityType.ZOMBIE, FLOOR);
        z.setItemSlot(EquipmentSlot.HEAD, hat());
        float before = z.getHealth();
        z.hurt(h.getLevel().damageSources().fallingBlock(null), 6);
        z.hurt(h.getLevel().damageSources().anvil(null), 6);
        h.assertTrue(z.getHealth() >= before, "a falling block hurt staff in a Hard Hat");
        // a wild zombie in one is not spared: the hat is a staff rule
        Zombie wild = h.spawn(EntityType.ZOMBIE, new BlockPos(10, 2, 10));
        wild.setItemSlot(EquipmentSlot.HEAD, hat());
        float w = wild.getHealth();
        wild.hurt(h.getLevel().damageSources().fallingBlock(null), 6);
        h.assertTrue(wild.getHealth() < w, "a wild zombie in a Hard Hat is spared by falling blocks");
        // two players, the same armour (1): a Hard Hat against a leather helmet
        ServerPlayer hatted = StaffRuleGameTests.player(h, new BlockPos(3, 2, 10));
        ServerPlayer leather = StaffRuleGameTests.player(h, new BlockPos(10, 2, 3));
        hatted.setItemSlot(EquipmentSlot.HEAD, hat());
        leather.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float hb = hatted.getHealth(), lb = leather.getHealth();
        hatted.hurt(h.getLevel().damageSources().fallingBlock(null), 12);
        leather.hurt(h.getLevel().damageSources().fallingBlock(null), 12);
        float hl = hb - hatted.getHealth(), ll = lb - leather.getHealth();
        h.assertTrue(ll > 0 && Math.abs(hl / ll - WorkRules.PLAYER_HAT) < 0.05, "a Hard Hat took " + hl + " against a helmet's " + ll);
        StaffRuleGameTests.done(h, boss, hatted, leather);
    }

    @GameTest(template = "platform")
    public static void theHardHatNeverWears(GameTestHelper h) {
        ItemStack hat = hat();
        h.assertTrue(!hat.isDamageableItem() && hat.getMaxDamage() == 0, "the Hard Hat can wear out");
        ServerPlayer p = StaffRuleGameTests.player(h, new BlockPos(3, 2, 3));
        p.setItemSlot(EquipmentSlot.HEAD, hat);
        for (int i = 0; i < 20; i++) {
            p.invulnerableTime = 0;
            p.setHealth(p.getMaxHealth());
            p.hurt(h.getLevel().damageSources().fallingBlock(null), 4);
        }
        ItemStack worn = p.getItemBySlot(EquipmentSlot.HEAD);
        h.assertTrue(worn.is(WorkRegistry.HARD_HAT.get()) && worn.getDamageValue() == 0, "the Hard Hat wore under falling blocks");
        StaffRuleGameTests.done(h, p);
    }
}
