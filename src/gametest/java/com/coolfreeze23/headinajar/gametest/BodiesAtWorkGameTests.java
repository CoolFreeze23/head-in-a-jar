package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.SafetyPlacardBlock;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.host.HeadlessChicken;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.mind.MobMotor;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 0.6.3, Bodies at work: the headless chicken's run (driven by the mod's MobMotor while its own mind is off) and Safety
 * Placard notice 12. The Treadmill's tests are TreadmillGameTests, the tricks' TrickGameTests.
 * <p>
 * {@code platform32}: 32 x 10 x 32, dirt under a grass floor; the floor's top is helper y 5, a chicken stands at y 6.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class BodiesAtWorkGameTests {
    private static final BlockPos CENTRE = new BlockPos(16, 6, 16);
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(v.x, v.y, v.z, 0, 0);
        return player;
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** A chicken sawn without a jar, the way a player does it. */
    private static Chicken chicken(GameTestHelper h, BlockPos at, ServerPlayer sawyer) {
        Chicken c = h.spawn(EntityType.CHICKEN, at);
        c.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), c, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "the saw failed on a chicken");
        return c;
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    @GameTest(template = "platform32", timeoutTicks = 6200)
    public static void theHeadlessChickenNeverLeavesItsCircle(GameTestHelper h) {
        // a pond (grass turned to water) and a pit three deep, both well inside 12 blocks of the spot
        for (int x = 8; x <= 10; x++) for (int z = 8; z <= 10; z++) h.setBlock(new BlockPos(x, 5, z), Blocks.WATER);
        for (int x = 22; x <= 24; x++) for (int z = 15; z <= 17; z++) for (int y = 3; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        ServerPlayer p = player(h, new BlockPos(2, 6, 2));
        Chicken c = chicken(h, CENTRE, p);
        BlockPos anchor = HeadlessChicken.anchor(c);
        h.assertTrue(anchor != null && anchor.equals(h.absolutePos(CENTRE)), "the saw did not anchor the run where it cut: " + anchor);
        Vec3 home = Vec3.atBottomCenterOf(anchor);
        double floorY = h.absolutePos(CENTRE).getY();
        double[] far = {0}, moved = {0};
        Vec3[] last = {c.position()};
        String[] broke = {null};
        h.onEachTick(() -> {
            if (broke[0] != null || !c.isAlive()) return;
            double d = flat(c.position(), home);
            far[0] = Math.max(far[0], d);
            moved[0] += flat(c.position(), last[0]);
            last[0] = c.position();
            if (d > HeadlessChicken.RADIUS) broke[0] = "it ran " + d + " blocks from its spot";
            else if (c.isInWater()) broke[0] = "it ran into the pond at " + c.blockPosition();
            else if (c.getY() < floorY - 0.5) broke[0] = "it went down the pit at " + c.blockPosition();
        });
        h.runAfterDelay(6000, () -> {
            h.assertTrue(broke[0] == null, broke[0] == null ? "" : broke[0]);
            h.assertTrue(HeadlessChicken.running(c), "the chicken stopped running");
            // it really runs: well round its circle, and a long way over five minutes
            h.assertTrue(far[0] > 3, "it never got more than " + far[0] + " from its spot");
            h.assertTrue(moved[0] > 200, "it ran only " + moved[0] + " blocks in five minutes");
            Hosts.data(c).freshness = 100;
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void theChickenPausesAndHops(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 6, 2));
        Chicken c = chicken(h, CENTRE, p);
        int[] hops = {0}, still = {0}, longestStill = {0};
        boolean[] wasOnGround = {true};
        Vec3[] last = {c.position()};
        h.onEachTick(() -> {
            if (wasOnGround[0] && !c.onGround() && c.getDeltaMovement().y > 0) hops[0]++;
            wasOnGround[0] = c.onGround();
            double step = flat(c.position(), last[0]);
            last[0] = c.position();
            still[0] = step < 0.01 ? still[0] + 1 : 0;
            longestStill[0] = Math.max(longestStill[0], still[0]);
        });
        h.runAfterDelay(300, () -> {
            // a hop every 10 to 16 ticks while it runs; a breath of a second every six
            h.assertTrue(hops[0] >= 12, "only " + hops[0] + " hops in 15 seconds");
            h.assertTrue(longestStill[0] >= 12, "it never stopped for breath (longest still " + longestStill[0] + " ticks)");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 300)
    public static void ridingStopsTheRunAndItStartsTwoSecondsAfter(GameTestHelper h) {
        ServerPlayer p = player(h, CENTRE.east(2));
        Chicken c = chicken(h, CENTRE, p);
        h.runAfterDelay(40, () -> {
            h.assertTrue(HeadlessChicken.running(c), "a fresh headless chicken is not running");
            FormActions.detach(p, 3);
            h.assertTrue(FormActions.mountHost(p, c), "setup: could not ride the chicken");
        });
        h.runAfterDelay(60, () -> {
            h.assertFalse(HeadlessChicken.running(c), "a ridden chicken still runs on its own");
            p.stopRiding();
        });
        h.runAfterDelay(60 + HeadlessChicken.SETTLE - 10, () -> h.assertFalse(HeadlessChicken.running(c), "it ran off before two seconds were up"));
        h.runAfterDelay(60 + HeadlessChicken.SETTLE + 10, () -> {
            h.assertTrue(HeadlessChicken.running(c), "it did not start again two seconds after the ride");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void aShovedChickenRunsBackInside(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 6, 2));
        Chicken c = chicken(h, new BlockPos(14, 6, 16), p);
        Vec3 home = Vec3.atBottomCenterOf(HeadlessChicken.anchor(c));
        h.runAfterDelay(20, () -> {
            // knocked to the platform's edge, 16 blocks out
            Vec3 edge = h.absoluteVec(new Vec3(30.5, 6, 16.5));
            c.teleportTo(edge.x, edge.y, edge.z);
            h.assertTrue(flat(c.position(), home) > HeadlessChicken.RADIUS, "setup: not outside the circle");
        });
        h.runAfterDelay(240, () -> {
            h.assertTrue(flat(c.position(), home) <= HeadlessChicken.RADIUS, "still " + flat(c.position(), home) + " from its spot after 11 seconds");
            done(h, p);
        });
    }

    /** Knocked out past a pond that lies straight between it and its spot: it goes round, it does not wait at the water. */
    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void aShovedChickenFindsItsWayRoundAPond(GameTestHelper h) {
        // a pond 9 wide right across the straight way home: every leg of 8 or 6 blocks straight back ends in it
        for (int x = 12; x <= 20; x++) for (int z = 21; z <= 25; z++) h.setBlock(new BlockPos(x, 5, z), Blocks.WATER);
        ServerPlayer p = player(h, new BlockPos(2, 6, 2));
        Chicken c = chicken(h, CENTRE, p);
        Vec3 home = Vec3.atBottomCenterOf(HeadlessChicken.anchor(c));
        boolean[] wet = {false};
        h.runAfterDelay(20, () -> {
            Vec3 out = h.absoluteVec(new Vec3(16.5, 6, 30.5));
            c.teleportTo(out.x, out.y, out.z);
            h.assertTrue(flat(c.position(), home) > HeadlessChicken.RADIUS, "setup: not outside the circle");
        });
        h.onEachTick(() -> wet[0] |= c.isInWater());
        h.runAfterDelay(260, () -> {
            h.assertFalse(wet[0], "it walked into the pond");
            h.assertTrue(flat(c.position(), home) <= HeadlessChicken.RADIUS, "still " + flat(c.position(), home) + " from its spot after 12 seconds");
            done(h, p);
        });
    }

    /** In a batch of its own: the switch is the server's, and the other chickens' tests run beside each other. */
    @GameTest(template = "platform32", timeoutTicks = 200, batch = "headless_chicken_switch")
    public static void noRunWithTheConfigOff(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 6, 2));
        Chicken c = chicken(h, CENTRE, p);
        Config.HEADLESS_CHICKEN.set(false);
        Vec3 start = c.position();
        h.runAfterDelay(120, () -> {
            try {
                h.assertFalse(HeadlessChicken.running(c), "a chicken ran with bodies.headlessChicken off");
                h.assertTrue(flat(c.position(), start) < 0.3, "it moved with the run off");
            } finally {
                Config.HEADLESS_CHICKEN.set(true);
            }
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void aRunningChickenStillLays(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 6, 2));
        Chicken c = chicken(h, CENTRE, p);
        h.runAfterDelay(40, () -> c.eggTime = 5);
        h.runAfterDelay(100, () -> {
            h.assertTrue(HeadlessChicken.running(c), "setup: not running");
            boolean egg = !h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(CENTRE)).inflate(16),
                    e -> e.getItem().is(Items.EGG)).isEmpty();
            h.assertTrue(egg, "a running chicken laid no egg");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void anOldChickenRunsFromWhereItStands(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 6, 2));
        Chicken c = chicken(h, CENTRE, p);
        // a chicken host from before 0.6.3 has no spot saved
        Hosts.data(c).runAnchor = HostData.NO_ANCHOR;
        BlockPos stand = h.absolutePos(new BlockPos(10, 6, 20));
        c.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        h.runAfterDelay(40, () -> {
            h.assertTrue(HeadlessChicken.running(c), "an old chicken host is not running");
            h.assertTrue(stand.equals(HeadlessChicken.anchor(c)), "its spot is " + HeadlessChicken.anchor(c) + ", not where it stood");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void otherHostsStayStillAndTheMotorStopsCleanly(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        var cow = h.spawn(EntityType.COW, FLOOR);
        cow.setHealth(1);
        SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), cow, InteractionHand.MAIN_HAND);
        Vec3 start = cow.position();
        h.runAfterDelay(60, () -> {
            h.assertTrue(cow.position().distanceTo(start) < 0.2, "a cow host walked off on its own");
            h.assertFalse(HeadlessChicken.running(cow), "a cow ran like a chicken");
            MobMotor.halt(cow);
            h.assertTrue(cow.zza == 0 && cow.xxa == 0 && MobMotor.idle(cow), "the motor left walking input behind");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void placardCyclesThroughTheNewNotice(GameTestHelper h) {
        h.assertValueEqual(SafetyPlacardBlock.next(11), 12, "after notice 11");
        h.assertValueEqual(SafetyPlacardBlock.next(12), 13, "after notice 12 (13 from 0.7.0)");
        // a real use on a placed placard showing 11 turns it to 12
        BlockPos wall = FLOOR.north(), at = FLOOR;
        h.setBlock(wall, Blocks.STONE);
        h.setBlock(at, ModRegistry.SAFETY_PLACARD.get().defaultBlockState().setValue(SafetyPlacardBlock.FACING, Direction.SOUTH)
                .setValue(SafetyPlacardBlock.NOTICE, 11));
        ServerPlayer p = player(h, FLOOR.south(2));
        BlockPos abs = h.absolutePos(at);
        h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), p, new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false));
        h.assertValueEqual(h.getLevel().getBlockState(abs).getValue(SafetyPlacardBlock.NOTICE), 12, "the placard's notice after a use");
        done(h, p);
    }
}
