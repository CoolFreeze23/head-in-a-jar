package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlock;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.menu.TimeClockMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffBlocks;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.StaffTable;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.Shifts;
import com.coolfreeze23.headinajar.staff.work.SiteIndex;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 0.7.0, the Time Clock and the worker's day at its site: clock spacing, the roster's cap, joining a clock within 16,
 * dawn pay from the drawer (paid and unpaid), the hopper into the drawer, shifts by day, night and redstone, the punch
 * (and its bell when late), cards as postings worked by the fewest takers, the line within 2 blocks of the clock,
 * the untrusted refusal, and a broken clock. Workers are hired through the real Staff Lanyard.
 * <p>
 * {@code platform32}: 32 x 10 x 32, the floor's top at helper y 5; the clock stands at y 6. Each clock reads its own test
 * time ({@code timeOverride}), so these never move the world's clock under other tests.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class TimeClockGameTests {
    private static final BlockPos CLOCK = new BlockPos(16, 6, 16);

    private static ServerPlayer boss(GameTestHelper h) {
        return TestPlayers.at(h, "boss", new BlockPos(2, 6, 2));
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** A Time Clock facing south owned by {@code owner}, at {@code at}, reading the test time {@code time}. */
    private static TimeClockBlockEntity clock(GameTestHelper h, BlockPos at, ServerPlayer owner, long time) {
        h.setBlock(at, WorkRegistry.TIME_CLOCK.get().defaultBlockState().setValue(TimeClockBlock.FACING, Direction.SOUTH));
        TimeClockBlockEntity c = (TimeClockBlockEntity) h.getBlockEntity(at);
        c.timeOverride = time;
        c.setOwner(owner);
        c.registerSite();
        return c;
    }

    /** Hires a creature through the Staff Lanyard (a hostile worn down to 40% first). */
    private static <T extends Mob> T hire(GameTestHelper h, ServerPlayer boss, EntityType<T> type, BlockPos at) {
        T mob = h.spawn(type, at);
        if (mob instanceof Enemy) mob.setHealth(mob.getMaxHealth() * 0.4f);
        boolean hired = Workforce.useLanyard(boss, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), mob);
        h.assertTrue(hired, "the lanyard did not hire the " + type.getDescription().getString() + ": " + Refusals.lastDelivered(boss));
        return mob;
    }

    private static WorkerData data(Mob mob) {
        return Workforce.data(mob);
    }

    /** A Job Card printed for this clock's site. */
    private static ItemStack card(TimeClockBlockEntity c, String name) {
        ItemStack s = new ItemStack(WorkRegistry.JOB_CARD.get());
        s.set(WorkRegistry.JOB_CARD_DATA.get(), new Order.Card(new Order(Optional.of(c.site()), "hauling", List.of(), List.of(), name), "test", 0));
        return s;
    }

    private static ItemStack wage(EntityType<?> type) {
        StaffTable.Wage w = StaffTable.wage(type);
        return new ItemStack(w.item(), w.count());
    }

    // --- the clock --------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32")
    public static void clocksNeedEightBlocksBetween(GameTestHelper h) {
        ServerPlayer p = boss(h);
        int spacing = Config.get(Config.CLOCK_SPACING, 8);
        BlockPos first = new BlockPos(3, 6, 16);
        clock(h, first, p, -1);
        // the real placement, from the player's hand, onto the floor
        BlockPos near = first.east(spacing), far = first.east(spacing + 1);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WorkRegistry.TIME_CLOCK_ITEM.get(), 2));
        for (BlockPos at : new BlockPos[]{near, far}) {
            BlockPos floor = h.absolutePos(at.below());
            UseOnContext ctx = new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
            p.getMainHandItem().useOn(ctx);
        }
        h.assertTrue(!h.getBlockState(near).is(WorkRegistry.TIME_CLOCK.get()), "a clock " + spacing + " blocks from another was placed");
        h.assertTrue(Refusals.delivered(p).contains("clock.too_close"), "no refusal for the close clock: " + Refusals.delivered(p));
        h.assertTrue(h.getBlockState(far).is(WorkRegistry.TIME_CLOCK.get()), "a clock " + (spacing + 1) + " blocks away was refused");
        SiteIndex sites = SiteIndex.get(h.getLevel());
        h.assertTrue(sites.at(h.absolutePos(far)) != null, "the placed clock is not in the site register");
        // breaking a clock strikes it from the register
        h.setBlock(far, Blocks.AIR);
        h.assertTrue(sites.at(h.absolutePos(far)) == null, "a broken clock stays in the site register");
        done(h, p);
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void aNinthWorkerIsRefused(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, 12000);
        int max = Config.get(Config.MAX_PER_CLOCK, 8);
        List<Mob> cows = new ArrayList<>();
        for (int i = 0; i <= max; i++) cows.add(hire(h, p, EntityType.COW, new BlockPos(6 + (i % 5) * 4, 6, 8 + (i / 5) * 4)));
        h.succeedWhen(() -> {
            h.assertTrue(c.roster().size() == max, "roster " + c.roster().size() + " of " + max);
            long left = cows.stream().filter(m -> data(m).clock == null && data(m).state == WorkerData.State.AWAITING).count();
            h.assertTrue(left == 1, left + " cows left awaiting, not 1");
            p.getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void staffWithinSixteenJoinANewClock(GameTestHelper h) {
        ServerPlayer p = boss(h);
        Mob near = hire(h, p, EntityType.COW, new BlockPos(12, 6, 16));
        Mob far = hire(h, p, EntityType.COW, new BlockPos(29, 6, 16));
        h.runAfterDelay(30, () -> {
            h.assertTrue(data(near).state == WorkerData.State.AWAITING && data(near).clock == null, "a cow joined no clock at all");
            TimeClockBlockEntity c = clock(h, new BlockPos(2, 6, 16), p, 12000);
            h.runAfterDelay(40, () -> {
                h.assertTrue(c.has(near.getUUID()) && data(near).clock != null, "a cow 10 blocks from the new clock did not join it");
                h.assertTrue(!c.has(far.getUUID()) && data(far).clock == null, "a cow 27 blocks away joined");
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void dawnPaysFromTheDrawer(GameTestHelper h) {
        dawn(h, true);
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void anUnpaidDawnCounts(GameTestHelper h) {
        dawn(h, false);
    }

    private static void dawn(GameTestHelper h, boolean pay) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, 12000);
        Mob cow = hire(h, p, EntityType.COW, CLOCK.east(3));
        h.runAfterDelay(25, () -> {
            h.assertTrue(c.has(cow.getUUID()), "the cow did not join the clock");
            long day = Staff.day(h.getLevel());
            StaffFile f = Staff.of(cow);
            f.lastDawn = day - 1;
            f.paidThrough = day - 1;
            f.morale = 50;
            if (pay) c.drawer().insertItem(0, wage(EntityType.COW), false);
            c.lastDawn = day - 1;
            h.runAfterDelay(3, () -> {
                StaffFile g = Staff.of(cow);
                h.assertTrue(g.lastDawn == day, "no dawn ran for the cow");
                // a cow is pleasant to work with: +2 on top
                int want = 50 + (pay ? StaffBlocks.PAID : StaffBlocks.UNPAID) + 2;
                h.assertTrue(g.morale == want, "morale " + g.morale + ", want " + want);
                if (pay) {
                    h.assertTrue(g.paidThrough == day, "paid but not marked paid");
                    h.assertTrue(c.drawer().getStackInSlot(0).isEmpty(), "the wage is still in the drawer");
                } else {
                    h.assertTrue(g.paidThrough == day - 1 && Shifts.unpaid(cow, h.getLevel()), "unpaid but marked paid");
                    // a second tick is not a second dawn
                    h.assertTrue(c.lastDawn == day, "the clock did not note its dawn");
                }
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void aHopperFillsTheDrawer(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, -1);
        h.setBlock(CLOCK.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) h.getBlockEntity(CLOCK.above());
        hopper.setItem(0, new ItemStack(Items.WHEAT, 3));
        var below = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(CLOCK), Direction.DOWN);
        h.assertTrue(below != null, "the clock has no item handler");
        h.succeedWhen(() -> {
            int n = 0;
            for (int i = 0; i < TimeClockBlockEntity.DRAWER; i++) if (c.drawer().getStackInSlot(i).is(Items.WHEAT)) n += c.drawer().getStackInSlot(i).getCount();
            h.assertTrue(n == 3, n + " wheat in the drawer");
            h.assertTrue(below.extractItem(0, 1, true).isEmpty(), "automation can take wages out of the drawer");
            p.getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32")
    public static void anUntrustedPlayerIsRefused(GameTestHelper h) {
        ServerPlayer p = boss(h);
        ServerPlayer stranger = TestPlayers.at(h, "stranger", CLOCK.east(2));
        TimeClockBlockEntity c = clock(h, CLOCK, p, -1);
        p.moveTo(Vec3.atBottomCenterOf(h.absolutePos(CLOCK.west(2))));
        c.use(stranger);
        h.assertTrue(!(stranger.containerMenu instanceof TimeClockMenu), "a stranger opened the clock");
        h.assertTrue(Refusals.delivered(stranger).contains("clock.untrusted"), "no refusal for the stranger: " + Refusals.delivered(stranger));
        // the owner may (the screen itself needs a real client's channels: the menu's own check stands in)
        h.assertTrue(c.trusts(p) && !c.trusts(stranger), "trust is the wrong way round");
        h.assertTrue(new TimeClockMenu(1, p.getInventory(), c).stillValid(p), "the owner's menu is not valid");
        h.assertTrue(!new TimeClockMenu(2, stranger.getInventory(), c).stillValid(stranger), "a stranger's menu is valid");
        done(h, p, stranger);
    }

    @GameTest(template = "platform32", timeoutTicks = 300)
    public static void aBrokenClockSendsStaffOffSite(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, 12000);
        Mob cow = hire(h, p, EntityType.COW, CLOCK.east(3));
        h.runAfterDelay(25, () -> {
            h.assertTrue(c.has(cow.getUUID()), "the cow did not join");
            c.drawer().insertItem(0, new ItemStack(Items.WHEAT, 5), false);
            c.rack().setStackInSlot(0, card(c, "posted"));
            h.getLevel().destroyBlock(h.absolutePos(CLOCK), false);
            h.assertTrue(data(cow).state == WorkerData.State.OFF_SITE && data(cow).clock == null, "the cow is " + data(cow).state + " with clock " + data(cow).clock);
            AABB around = new AABB(h.absolutePos(CLOCK)).inflate(2);
            List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, around);
            h.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.WHEAT) && e.getItem().getCount() == 5), "the drawer's wheat did not fall out");
            h.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(WorkRegistry.JOB_CARD.get())), "the rack's card did not fall out");
            // a new clock within 16: the cow joins it by itself
            TimeClockBlockEntity next = clock(h, CLOCK.west(10), p, 12000);
            h.succeedWhen(() -> {
                h.assertTrue(next.has(cow.getUUID()) && data(cow).clock != null && data(cow).clock.equals(next.site()), "the cow did not join the new clock");
                p.getServer().getPlayerList().remove(p);
            });
        });
    }

    // --- shifts and the punch ---------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 900)
    public static void shiftsStartAndEnd(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, 12000);
        Mob cow = hire(h, p, EntityType.COW, CLOCK.east(4));
        WorkerData d = data(cow);
        int[] step = {0};
        h.onEachTick(() -> {
            switch (step[0]) {
                case 0 -> {
                    // evening: to the line, off shift
                    if (d.state == WorkerData.State.OFF_SHIFT) {
                        c.timeOverride = Shifts.DAY_START + 50;
                        step[0] = 1;
                    }
                }
                case 1 -> {
                    // morning: punched in, and with nothing on the rack it says so
                    if (c.punchedFor(cow.getUUID(), Shifts.stamp(WorkerData.Shift.DAY, c)) && "staff.headinajar.say.no_card".equals(d.status)) {
                        c.rack().setStackInSlot(2, card(c, "posted"));
                        step[0] = 2;
                    }
                }
                case 2 -> {
                    if (d.state == WorkerData.State.ON_JOB && d.rackSlot == 2 && c.takers(2) == 1) {
                        c.timeOverride = Shifts.DAY_END;
                        step[0] = 3;
                    }
                }
                case 3 -> {
                    // the shift's end: back to the line, the posting free again
                    if (d.state == WorkerData.State.OFF_SHIFT && c.takers(2) == 0) {
                        h.assertTrue(Vec3.atCenterOf(h.absolutePos(CLOCK)).subtract(cow.position()).horizontalDistance() <= 2.6,
                                "the cow stands " + Vec3.atCenterOf(h.absolutePos(CLOCK)).subtract(cow.position()).horizontalDistance() + " from the clock");
                        step[0] = 4;
                        done(h, p);
                    }
                }
                default -> {
                }
            }
        });
    }

    /**
     * Three staff of one clock with nothing on the rack: the first says "Nothing on the rack for me." and the other two
     * keep quiet while it shows (the line is still their status); staff of another clock still say it.
     */
    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void oneClockSaysAStopLineWithOneVoice(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, Shifts.DAY_START + 100);
        TimeClockBlockEntity other = clock(h, CLOCK.west(12), p, Shifts.DAY_START + 100);
        Mob a = hire(h, p, EntityType.COW, CLOCK.east(3));
        Mob b = hire(h, p, EntityType.COW, CLOCK.east(4));
        Mob d = hire(h, p, EntityType.COW, CLOCK.east(5));
        Mob e = hire(h, p, EntityType.COW, CLOCK.west(13));
        net.minecraft.core.GlobalPos here = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), c.getBlockPos());
        data(a).clock = here;
        data(b).clock = here;
        data(d).clock = here;
        data(e).clock = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), other.getBlockPos());
        h.assertTrue(Workforce.say(a, "no_card"), "the first did not say it");
        h.assertTrue(!Workforce.say(b, "no_card") && !Workforce.say(d, "no_card"), "a second voice from the same clock");
        h.assertTrue("staff.headinajar.say.no_card".equals(data(b).status), "the quiet one's status is not the line: " + data(b).status);
        h.assertTrue(Workforce.say(e, "no_card"), "another clock's staff were silenced too");
        done(h, p);
    }

    /** A worker hired 3 blocks off walks up to its spot in front of the clock before its punch begins. */
    @GameTest(template = "platform32", timeoutTicks = 600)
    public static void staffStepUpToTheSlotToPunch(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, Shifts.DAY_START + 100);
        Mob cow = hire(h, p, EntityType.COW, CLOCK.east(3));
        Vec3 slot = Vec3.atBottomCenterOf(h.absolutePos(CLOCK.south()));
        double[] off = {-1};
        h.onEachTick(() -> {
            if (off[0] < 0 && c.kit().since(TimeClockBlockEntity.EV_PUNCH, h.getLevel().getGameTime()) == 0) {
                off[0] = Math.hypot(cow.getX() - slot.x, cow.getZ() - slot.z);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(off[0] >= 0, "its punch never began");
            h.assertTrue(off[0] <= TimeClockBlockEntity.AT_SLOT, "it punched " + off[0] + " blocks from its spot at the slot");
            h.assertTrue(c.punchedFor(cow.getUUID(), Shifts.stamp(WorkerData.Shift.DAY, c)), "not punched in");
            p.getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 600)
    public static void aLateWorkerRingsTheBell(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, Shifts.DAY_START + Shifts.LATE_TICKS + 100);
        Mob cow = hire(h, p, EntityType.COW, CLOCK.east(3));
        boolean[] punch = {false}, ding = {false};
        h.onEachTick(() -> {
            long now = h.getLevel().getGameTime();
            if (c.kit().since(TimeClockBlockEntity.EV_PUNCH, now) == 0) punch[0] = true;
            if (c.kit().since(TimeClockBlockEntity.EV_DING, now) == 0) ding[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(c.punchedFor(cow.getUUID(), Shifts.stamp(WorkerData.Shift.DAY, c)), "not punched in");
            h.assertTrue(punch[0] && ding[0], "punch " + punch[0] + ", bell " + ding[0]);
            p.getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 900)
    public static void redstoneShiftRunsWhilePowered(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, Shifts.DAY_START + 100);
        Mob cow = hire(h, p, EntityType.COW, CLOCK.east(3));
        WorkerData d = data(cow);
        d.shift = WorkerData.Shift.REDSTONE;
        int[] step = {0};
        h.onEachTick(() -> {
            switch (step[0]) {
                case 0 -> {
                    // day time, but no power: off shift
                    if (d.state == WorkerData.State.OFF_SHIFT) {
                        h.setBlock(CLOCK.west(), Blocks.REDSTONE_BLOCK);
                        step[0] = 1;
                    }
                }
                case 1 -> {
                    if (c.powered() && c.punchedFor(cow.getUUID(), Shifts.stamp(WorkerData.Shift.REDSTONE, c))) {
                        h.setBlock(CLOCK.west(), Blocks.AIR);
                        step[0] = 2;
                    }
                }
                case 2 -> {
                    if (!c.powered() && d.state == WorkerData.State.OFF_SHIFT) {
                        step[0] = 3;
                        done(h, p);
                    }
                }
                default -> {
                }
            }
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 900)
    public static void aLanyardedZombiePunchesInAtDuskAndTakesACard(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, 12000);
        c.rack().setStackInSlot(0, card(c, "night haul"));
        Mob zombie = h.spawn(EntityType.ZOMBIE, CLOCK.east(5));
        // the test world may be in daylight: a Hard Hat keeps it from burning (vanilla spares any helmet)
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(WorkRegistry.HARD_HAT.get()));
        zombie.setHealth(zombie.getMaxHealth() * 0.4f);
        h.assertTrue(Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), zombie), "the zombie was not hired: " + Refusals.lastDelivered(p));
        WorkerData d = data(zombie);
        h.assertTrue(d.shift == WorkerData.Shift.NIGHT, "a zombie starts on " + d.shift);
        boolean[] dusk = {false};
        h.onEachTick(() -> {
            if (!dusk[0] && d.state == WorkerData.State.OFF_SHIFT) {
                c.timeOverride = Shifts.NIGHT_START;
                dusk[0] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(dusk[0], "it never stood in the line before dusk (" + d.state + ")");
            h.assertTrue(d.has(WorkerData.FLAG_PUNCHED), "it never punched in");
            h.assertTrue(d.state == WorkerData.State.ON_JOB && d.rackSlot == 0 && c.takers(0) == 1, "state " + d.state + ", slot " + d.rackSlot);
            p.getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 900)
    public static void cardsGoToTheFewestTakers(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, Shifts.DAY_START + 50);
        c.rack().setStackInSlot(0, card(c, "first"));
        c.rack().setStackInSlot(3, card(c, "second"));
        List<Mob> cows = new ArrayList<>();
        for (int i = 0; i < 3; i++) cows.add(hire(h, p, EntityType.COW, CLOCK.east(2 + i).south(1)));
        h.succeedWhen(() -> {
            long on = cows.stream().filter(m -> data(m).state == WorkerData.State.ON_JOB).count();
            h.assertTrue(on == 3, on + " of 3 on the job");
            // one card serves many: three workers on two postings, two on the first, one on the second
            h.assertTrue(c.takers(0) == 2 && c.takers(3) == 1, "takers " + c.takers(0) + " and " + c.takers(3));
            p.getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void theLineStandsUnderTheAwning(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, 23200);
        Vec3 at = Vec3.atCenterOf(h.absolutePos(CLOCK));
        for (int i = 0; i < 8; i++) {
            BlockPos s = Shifts.lineSpot(c, i);
            double dist = Vec3.atBottomCenterOf(s).subtract(at).horizontalDistance();
            h.assertTrue(dist <= 2.0 + 1e-6, "line spot " + i + " is " + dist + " from the clock");
            h.assertTrue(!s.equals(c.front()), "line spot " + i + " is the punch's cell");
        }
        // between 23,000 and the next shift a night worker stands there
        Mob zombie = h.spawn(EntityType.ZOMBIE, CLOCK.east(4));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(WorkRegistry.HARD_HAT.get()));
        zombie.setHealth(zombie.getMaxHealth() * 0.4f);
        h.assertTrue(Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), zombie), "the zombie was not hired");
        h.succeedWhen(() -> {
            h.assertTrue(data(zombie).state == WorkerData.State.OFF_SHIFT, "state " + data(zombie).state);
            double dist = zombie.position().subtract(at).horizontalDistance();
            h.assertTrue(dist <= 2.5, "it stands " + dist + " from the clock");
            p.getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32")
    public static void undeadToDayNeedsAHardHat(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, 12000);
        Mob zombie = h.spawn(EntityType.ZOMBIE, CLOCK.east(3));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.setHealth(zombie.getMaxHealth() * 0.4f);
        h.assertTrue(Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), zombie), "the zombie was not hired");
        c.join(zombie);
        Shifts.setShift(p, c, zombie, WorkerData.Shift.DAY);
        h.assertTrue(data(zombie).shift == WorkerData.Shift.NIGHT, "an undead in an iron helmet was put on Day");
        h.assertTrue(Refusals.delivered(p).contains("shift.no_hard_hat"), "no refusal: " + Refusals.delivered(p));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(WorkRegistry.HARD_HAT.get()));
        Shifts.setShift(p, c, zombie, WorkerData.Shift.DAY);
        h.assertTrue(data(zombie).shift == WorkerData.Shift.DAY, "a hard-hatted undead was refused Day");
        done(h, p);
    }

    @GameTest(template = "platform32", timeoutTicks = 600)
    public static void aClericHealsStaffAsTheyPunchIn(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, Shifts.DAY_START + 50);
        net.minecraft.world.entity.npc.Villager cleric = h.spawn(EntityType.VILLAGER, CLOCK.west(3));
        cleric.setVillagerData(cleric.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.CLERIC));
        h.assertTrue(Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), cleric), "the cleric was not hired: " + Refusals.lastDelivered(p));
        Mob cow = hire(h, p, EntityType.COW, CLOCK.east(3));
        cow.setHealth(4);
        h.succeedWhen(() -> {
            h.assertTrue(c.punchedFor(cow.getUUID(), Shifts.stamp(WorkerData.Shift.DAY, c)), "the cow has not punched in");
            h.assertTrue(cow.getHealth() >= 8 - 1e-3, "the cow's health is " + cow.getHealth() + " after punching in beside a Cleric");
            p.getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void screenOrdersGoToTheWorkerByUuid(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, CLOCK, p, 12000);
        Mob a = hire(h, p, EntityType.COW, CLOCK.east(3));
        Mob b = hire(h, p, EntityType.COW, CLOCK.west(3));
        h.runAfterDelay(25, () -> {
            h.assertTrue(c.has(a.getUUID()) && c.has(b.getUUID()), "the cows did not join");
            TimeClockMenu menu = new TimeClockMenu(1, p.getInventory(), c);
            // the roster's order does not matter: the order names its worker
            menu.order(p, com.coolfreeze23.headinajar.staff.work.ClockNet.SHIFT, b.getUUID(), WorkerData.Shift.NIGHT.ordinal());
            h.assertTrue(data(b).shift == WorkerData.Shift.NIGHT && data(a).shift == WorkerData.Shift.DAY,
                    "shifts " + data(a).shift + " and " + data(b).shift);
            var rows = com.coolfreeze23.headinajar.staff.work.ClockNet.rows(1, c).rows();
            h.assertTrue(rows.size() == 2 && rows.stream().anyMatch(r -> r.id().equals(b.getUUID()) && r.shift() == WorkerData.Shift.NIGHT.ordinal()),
                    "the rows do not show the change: " + rows);
            done(h, p);
        });
    }
}
