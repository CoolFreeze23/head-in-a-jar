package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity;
import com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity;
import com.coolfreeze23.headinajar.block.HeadJobs;
import com.coolfreeze23.headinajar.block.MachineBlock;
import com.coolfreeze23.headinajar.block.TimeClockBlock;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.JobCardItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Grades;
import com.coolfreeze23.headinajar.staff.SiteState;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.StaffTable;
import com.coolfreeze23.headinajar.staff.Strikes;
import com.coolfreeze23.headinajar.registry.SecondRegistry;
import com.coolfreeze23.headinajar.staff.second.Secondees;
import com.coolfreeze23.headinajar.staff.second.Secondment;
import com.coolfreeze23.headinajar.staff.work.Detail;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.Shifts;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Strike (0.7.2): Interns never strike; a strike starts at dawn by low morale or by unpaid dawns, never in the first
 * week, never with {@code staff.strikes} or {@code staff.morale} off, never in the cool-down; the Board's honest
 * "unionise" walks out a site that already meets the test; the Rep's demands, a cake short of the back pay and a cake
 * with it, collapse after three loaded dawns, wages paid at a strike's dawn owed no more; a strike breaks no block and
 * hurts nobody, with no striker inside a block; strikers refuse cards, the Whistle, Let go, Recall and promotion, a
 * striking site hires nobody; an Employee Mount on the site faces the wall with its bonus unchanged.
 * <p>
 * {@code platform32}: 32 x 10 x 32, the floor's top at helper y 5 (mobs stand at y 6). {@code site34}: 34 x 18 x 34,
 * the grass top at helper y 4 (mobs stand at y 5). The clocks read the test time 12,000 (off shift: staff wait in line)
 * and the strike rules a test day ({@code Strikes.Record.dayOverride}) outside the first week. A config switch is
 * turned only for one synchronous call and put back in a {@code finally}, so tests alongside never see it.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class StrikeGameTests {
    private static final BlockPos CLOCK = new BlockPos(16, 6, 16);
    private static final long DAY = 20;

    private static ServerPlayer boss(GameTestHelper h, BlockPos at) {
        return TestPlayers.at(h, "picket", at);
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** A Time Clock facing south owned by {@code owner}, off shift, its strike rules reading day 20. */
    private static TimeClockBlockEntity clock(GameTestHelper h, BlockPos at, ServerPlayer owner) {
        h.setBlock(at, WorkRegistry.TIME_CLOCK.get().defaultBlockState().setValue(TimeClockBlock.FACING, Direction.SOUTH));
        TimeClockBlockEntity c = (TimeClockBlockEntity) h.getBlockEntity(at);
        c.timeOverride = 12000;
        c.setOwner(owner);
        c.registerSite();
        c.strike.dayOverride = DAY;
        return c;
    }

    /** {@code n} hires of a species at a grade round the clock (3 blocks out); they join it on their own. */
    private static <T extends Mob> List<T> hires(GameTestHelper h, ServerPlayer p, EntityType<T> type, int n, int grade) {
        List<T> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / Math.max(1, n);
            out.add(MomentsGameTests.staff(h, p, type, CLOCK.offset((int) Math.round(6 * Math.cos(a)), 0, (int) Math.round(6 * Math.sin(a))), grade));
        }
        return out;
    }

    private static void joined(GameTestHelper h, TimeClockBlockEntity c, List<? extends Mob> staff) {
        for (Mob m : staff) h.assertTrue(c.has(m.getUUID()), "setup: a " + m.getType().getDescription().getString() + " did not join the clock");
    }

    private static void file(Mob m, Consumer<StaffFile> change) {
        StaffFile f = Staff.of(m);
        change.accept(f);
        m.setData(ModRegistry.STAFF, f);
    }

    /** The clock's next tick is a dawn for it and for these staff; {@code each} sets each file first. */
    private static void dawn(GameTestHelper h, TimeClockBlockEntity c, List<? extends Mob> staff, Consumer<StaffFile> each) {
        long day = Staff.day(h.getLevel());
        for (Mob m : staff) file(m, f -> {
            f.lastDawn = day - 1;
            each.accept(f);
        });
        c.lastDawn = day - 1;
    }

    /** Unpaid at the coming dawn (nothing in the drawer, not paid for today). */
    private static void unpaid(GameTestHelper h, StaffFile f) {
        f.paidThrough = Staff.day(h.getLevel()) - 1;
    }

    /** Paid for today already (the dawn takes nothing and counts it paid). */
    private static void paid(GameTestHelper h, StaffFile f) {
        f.paidThrough = Staff.day(h.getLevel());
    }

    private static ItemStack wage(EntityType<?> type, int dawns) {
        StaffTable.Wage w = StaffTable.wage(type);
        return new ItemStack(w.item(), w.count() * dawns);
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static boolean noted(TimeClockBlockEntity c, String what) {
        String at = c.getBlockPos().toShortString() + " ";
        for (String s : Strikes.HISTORY) if (s.startsWith(at) && s.substring(at.length()).startsWith(what)) return true;
        return false;
    }

    /** A site on strike now (from the files, with no dawn): four Associate cows, all under 40. */
    private static void onStrike(GameTestHelper h, ServerPlayer p, java.util.function.BiConsumer<TimeClockBlockEntity, List<Mob>> then) {
        TimeClockBlockEntity c = clock(h, CLOCK, p);
        List<Mob> cows = new ArrayList<>(hires(h, p, EntityType.COW, 4, Grades.ASSOCIATE));
        h.runAfterDelay(30, () -> {
            joined(h, c, cows);
            for (Mob m : cows) file(m, f -> f.morale = 20);
            SiteState.afterDawn(c);
            h.assertTrue(c.strike.active(), "setup: no strike");
            then.accept(c, cows);
        });
    }

    // --- 9. Interns never strike ----------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void internsNeverStrike(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        TimeClockBlockEntity c = clock(h, CLOCK, p);
        List<Mob> interns = new ArrayList<>(hires(h, p, EntityType.COW, 4, Grades.INTERN));
        h.runAfterDelay(30, () -> {
            joined(h, c, interns);
            // as neglected as staff can be: unpaid two dawns running and under 15
            dawn(h, c, interns, f -> {
                f.morale = 5;
                f.career.unpaidRun = 3;
                unpaid(h, f);
            });
            h.runAfterDelay(3, () -> {
                h.assertTrue(!c.strike.active(), "a site of Interns struck");
                h.assertTrue(!Strikes.meetsTest(c), "Interns counted for the strike test");
                // two Associates join them: now the site strikes, and the Interns keep working
                file(interns.get(0), f -> f.career.grade = Grades.ASSOCIATE);
                file(interns.get(1), f -> f.career.grade = Grades.ASSOCIATE);
                SiteState.afterDawn(c);
                h.assertTrue(c.strike.active(), "two neglected Associates did not strike");
                h.assertTrue(c.strike.ring().size() == 2, "the ring holds " + c.strike.ring().size() + ", want the two Associates");
                for (Mob m : interns.subList(2, 4)) {
                    h.assertTrue(!c.strike.ring().contains(m.getUUID()) && !SiteState.striker.test(m), "an Intern walked out");
                    h.assertTrue(!"staff.headinajar.status.on_strike".equals(Workforce.data(m).status), "an Intern is on strike");
                }
                done(h, p);
            });
        });
    }

    // --- 11. a strike starts at dawn --------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 500)
    public static void aStrikeStartsAtDawnOnLowMorale(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        TimeClockBlockEntity c = clock(h, CLOCK, p);
        List<Mob> cows = new ArrayList<>(hires(h, p, EntityType.COW, 4, Grades.ASSOCIATE));
        h.runAfterDelay(30, () -> {
            joined(h, c, cows);
            // half of them under 40 after the dawn (paid: +10, a cow +2), the most tasks the Rep
            dawn(h, c, cows, f -> paid(h, f));
            file(cows.get(0), f -> f.morale = 10);
            file(cows.get(1), f -> f.morale = 15);
            file(cows.get(2), f -> f.morale = 90);
            file(cows.get(3), f -> {
                f.morale = 90;
                f.tasks = 200;
            });
            h.runAfterDelay(3, () -> {
                h.assertTrue(c.strike.active() && noted(c, "start dawn 4"), "half under 40 did not walk out at dawn");
                h.assertTrue(c.strike.rep().equals(cows.get(3).getUUID()), "the Rep is not the one with the most tasks");
                CompoundTag sync = new CompoundTag();
                c.writeSync(sync);
                h.assertTrue(sync.getBoolean("strike"), "the clock does not sync its strike");
                for (Mob m : cows) {
                    h.assertTrue(SiteState.striker.test(m), "a cow is not on strike");
                    h.assertTrue("staff.headinajar.status.on_strike".equals(Workforce.data(m).status), "its status is " + Workforce.data(m).status);
                    h.assertTrue(Staff.of(m).history.stream().anyMatch(e -> e.k().equals("strike")), "no history line");
                }
                h.assertTrue(SiteState.rep.test(cows.get(3)) && !SiteState.rep.test(cows.get(0)), "the Rep hook");
                // the ring: standable slots 3 to 5 blocks out, nobody inside a block
                h.runAfterDelay(300, () -> {
                    Vec3 centre = Vec3.atCenterOf(h.absolutePos(CLOCK));
                    for (Mob m : cows) {
                        double d = Math.hypot(m.getX() - centre.x, m.getZ() - centre.z);
                        BlockPos slot = Strikes.slotOf(m);
                        h.assertTrue(d >= 2.2 && d <= 5.9, "a striker stands " + String.format("%.1f", d) + " from the clock (slot "
                                + (slot == null ? "none" : slot.subtract(h.absolutePos(CLOCK)).toShortString()) + ", standing " + Strikes.standing(m)
                                + ", at " + m.blockPosition().subtract(h.absolutePos(CLOCK)).toShortString() + ", fails " + Workforce.data(m).pathFails
                                + ", state " + Workforce.data(m).state + ", ground " + m.onGround() + ", nav done " + m.getNavigation().isDone()
                                + ", last path " + (h.getLevel().getGameTime() - Workforce.data(m).lastPath) + " ago, on " + h.getLevel().getBlockState(m.blockPosition().below()).getBlock()
                                + ", in " + h.getLevel().getBlockState(m.blockPosition()).getBlock() + ", rep " + SiteState.rep.test(m) + ")");
                        h.assertTrue(h.getLevel().noCollision(m, m.getBoundingBox().deflate(0.01)), "a striker is inside a block");
                    }
                    done(h, p);
                });
            });
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void aStrikeStartsAtDawnWhenUnpaid(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        TimeClockBlockEntity c = clock(h, CLOCK, p);
        List<Mob> cows = new ArrayList<>(hires(h, p, EntityType.COW, 3, Grades.ASSOCIATE));
        h.runAfterDelay(30, () -> {
            joined(h, c, cows);
            // morale high; two go unpaid a second dawn running, the third is paid
            dawn(h, c, cows, f -> f.morale = 95);
            file(cows.get(0), f -> {
                f.career.unpaidRun = 1;
                unpaid(h, f);
            });
            file(cows.get(1), f -> {
                f.career.unpaidRun = 1;
                unpaid(h, f);
            });
            file(cows.get(2), f -> paid(h, f));
            h.runAfterDelay(3, () -> {
                h.assertTrue(Staff.of(cows.get(0)).career.unpaidRun == 2 && Staff.of(cows.get(0)).morale >= 40, "setup: the dawn did not count it unpaid");
                h.assertTrue(c.strike.active(), "two unpaid two dawns running did not walk out");
                h.assertTrue(c.strike.owed(cows.get(0).getUUID()) == 2 && c.strike.owed(cows.get(2).getUUID()) == 0,
                        "owed " + c.strike.owed(cows.get(0).getUUID()) + " and " + c.strike.owed(cows.get(2).getUUID()) + ", want 2 and 0");
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void halfAndTwoAtLeast(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        TimeClockBlockEntity c = clock(h, CLOCK, p);
        List<Mob> cows = new ArrayList<>(hires(h, p, EntityType.COW, 5, Grades.ASSOCIATE));
        h.runAfterDelay(30, () -> {
            joined(h, c, cows);
            for (Mob m : cows) file(m, f -> f.morale = 80);
            // two of five under 40 is less than half; one unpaid two dawns running is not two
            file(cows.get(0), f -> f.morale = 10);
            file(cows.get(1), f -> f.morale = 10);
            file(cows.get(2), f -> f.career.unpaidRun = 4);
            h.assertTrue(!Strikes.meetsTest(c), "two of five under 40 met the test");
            SiteState.afterDawn(c);
            h.assertTrue(!c.strike.active(), "a strike with two of five under 40");
            file(cows.get(2), f -> f.morale = 10);
            h.assertTrue(Strikes.meetsTest(c), "three of five under 40 did not meet the test");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void neverInTheFirstWeek(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        TimeClockBlockEntity c = clock(h, CLOCK, p);
        List<Mob> cows = new ArrayList<>(hires(h, p, EntityType.COW, 2, Grades.ASSOCIATE));
        h.runAfterDelay(30, () -> {
            joined(h, c, cows);
            for (Mob m : cows) file(m, f -> f.morale = 10);
            c.strike.dayOverride = 6;
            SiteState.afterDawn(c);
            h.assertTrue(!c.strike.active(), "a strike on day 6");
            c.strike.dayOverride = 7;
            SiteState.afterDawn(c);
            h.assertTrue(c.strike.active(), "no strike on day 7");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void staffStrikesOffMeansNoStrike(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        TimeClockBlockEntity c = clock(h, CLOCK, p);
        List<Mob> cows = new ArrayList<>(hires(h, p, EntityType.COW, 2, Grades.ASSOCIATE));
        h.runAfterDelay(30, () -> {
            joined(h, c, cows);
            for (Mob m : cows) file(m, f -> f.morale = 10);
            Config.STRIKES.set(false);
            try {
                SiteState.afterDawn(c);
            } finally {
                Config.STRIKES.set(true);
            }
            h.assertTrue(!c.strike.active(), "a strike with staff.strikes off");
            Config.MORALE.set(false);
            try {
                SiteState.afterDawn(c);
            } finally {
                Config.MORALE.set(true);
            }
            h.assertTrue(!c.strike.active(), "a strike with staff.morale off");
            SiteState.afterDawn(c);
            h.assertTrue(c.strike.active(), "no strike with both on");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 300)
    public static void coolDownHolds(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        onStrike(h, p, (c, cows) -> {
            // three loaded dawns of waiting: it collapses, everyone at 20
            for (int i = 0; i < Strikes.COLLAPSE_DAWNS; i++) {
                h.assertTrue(c.strike.active(), "it ended after " + i + " dawns");
                SiteState.afterDawn(c);
            }
            h.assertTrue(!c.strike.active() && noted(c, "collapsed"), "no collapse after three dawns");
            for (Mob m : cows) h.assertTrue(Staff.of(m).morale == Strikes.COLLAPSED_MORALE, "morale " + Staff.of(m).morale + " after the collapse");
            h.assertTrue(c.strike.cooldownUntil() == DAY + Strikes.COOLDOWN_DAYS, "cool-down until " + c.strike.cooldownUntil());
            for (Mob m : cows) file(m, f -> f.morale = 10);
            c.strike.dayOverride = DAY + 1;
            SiteState.afterDawn(c);
            c.strike.dayOverride = DAY + 2;
            SiteState.afterDawn(c);
            h.assertTrue(!c.strike.active(), "a new strike in the cool-down");
            c.strike.dayOverride = DAY + 3;
            SiteState.afterDawn(c);
            h.assertTrue(c.strike.active(), "no strike after the cool-down");
            // a strike's dawn is not a dawn of the test: the record survives a save
            CompoundTag saved = new CompoundTag();
            c.strike.save(saved);
            Strikes.Record r = new Strikes.Record();
            r.load(saved);
            h.assertTrue(r.active() && r.ring().size() == 4 && r.rep().equals(c.strike.rep()), "the record did not save");
            done(h, p);
        });
    }

    // --- 12. the Board --------------------------------------------------------------------------------------------------

    /** An Array of five cow heads at {@code at}, the gavel lifted on motion {@code motion} with {@code ayes}. */
    private static CerebralArrayBlockEntity board(GameTestHelper h, BlockPos at, int ayes, int motion) {
        h.setBlock(at, ModRegistry.CEREBRAL_ARRAY.get());
        CerebralArrayBlockEntity a = (CerebralArrayBlockEntity) h.getBlockEntity(at);
        for (int i = 0; i < 5; i++) {
            Mob cow = EntityType.COW.create(h.getLevel());
            a.items().setStackInSlot(i, JarredHeadItem.of(cow));
        }
        a.callVote(h.getLevel(), ayes, motion);
        return a;
    }

    private static final int UNION = 4, BROTH = 0;

    private static void boardCase(GameTestHelper h, int ayes, int motion, boolean qualifies, boolean walks, String what) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        TimeClockBlockEntity c = clock(h, CLOCK, p);
        List<Mob> cows = new ArrayList<>(hires(h, p, EntityType.COW, 2, Grades.ASSOCIATE));
        h.runAfterDelay(30, () -> {
            joined(h, c, cows);
            for (Mob m : cows) file(m, f -> f.morale = qualifies ? 10 : 80);
            board(h, CLOCK.east(10), ayes, motion);
            h.runAfterDelay(15, () -> {
                h.assertTrue(c.strike.active() == walks, what + ": strike " + c.strike.active());
                if (walks) h.assertTrue(noted(c, "start board " + ayes + " to " + (5 - ayes)), what + ": not the vote's own count");
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void theBoardWalksThemOut(GameTestHelper h) {
        boardCase(h, 3, UNION, true, true, "unionise carried 3 to 2");
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void aLostUnioniseWalksNobodyOut(GameTestHelper h) {
        boardCase(h, 2, UNION, true, false, "unionise lost 2 to 3");
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void anotherMotionWalksNobodyOut(GameTestHelper h) {
        boardCase(h, 4, BROTH, true, false, "warmer broth carried");
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void theBoardNeverWalksOutAContentSite(GameTestHelper h) {
        boardCase(h, 5, UNION, false, false, "unionise carried at a content site");
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void boardUnionisesOffKeepsItAJoke(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        TimeClockBlockEntity c = clock(h, CLOCK, p);
        List<Mob> cows = new ArrayList<>(hires(h, p, EntityType.COW, 2, Grades.ASSOCIATE));
        h.runAfterDelay(30, () -> {
            joined(h, c, cows);
            for (Mob m : cows) file(m, f -> f.morale = 10);
            int n;
            Config.BOARD_UNIONISES.set(false);
            try {
                n = Strikes.boardCarried(h.getLevel(), h.absolutePos(CLOCK.east(10)), 3, 2);
            } finally {
                Config.BOARD_UNIONISES.set(true);
            }
            h.assertTrue(n == 0 && !c.strike.active(), "a walk-out with staff.boardUnionises off");
            done(h, p);
        });
    }

    // --- 13. the end ----------------------------------------------------------------------------------------------------

    private static boolean use(ServerPlayer p, Mob mob, ItemStack held) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        PlayerInteractEvent.EntityInteract e = new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, mob);
        Strikes.interact(e);
        return e.isCanceled();
    }

    @GameTest(template = "platform32", timeoutTicks = 300)
    public static void theStrikeEndsByCake(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(16, 6, 20));
        onStrike(h, p, (c, cows) -> {
            // what they are owed is taken at the walk-out: two that had gone unpaid 3 and 9 dawns (capped at 7), the
            // others nothing; this strike collapses and a new one starts after the cool-down with those runs
            Mob a = cows.get(0), b = cows.get(1);
            file(a, f -> f.career.unpaidRun = 3);
            file(b, f -> f.career.unpaidRun = 9);
            for (int i = 0; i < Strikes.COLLAPSE_DAWNS; i++) SiteState.afterDawn(c);
            c.strike.dayOverride = DAY + 10;
            for (Mob m : cows) file(m, f -> f.morale = 20);
            SiteState.afterDawn(c);
            h.assertTrue(c.strike.active(), "setup: no second strike");
            Mob theRep = cows.stream().filter(m -> m.getUUID().equals(c.strike.rep())).findFirst().orElseThrow();
            h.assertTrue(c.strike.owed(a.getUUID()) == 3 && c.strike.owed(b.getUUID()) == Strikes.BACK_PAY_DAWNS,
                    "owed " + c.strike.owed(a.getUUID()) + " and " + c.strike.owed(b.getUUID()));
            // a strike's dawn: a unpaid at it (one more owed), b paid at it (no more owed)
            file(a, f -> f.career.unpaidRun = 4);
            file(b, f -> f.career.unpaidRun = 0);
            SiteState.afterDawn(c);
            h.assertTrue(c.strike.owed(a.getUUID()) == 4 && c.strike.owed(b.getUUID()) == Strikes.BACK_PAY_DAWNS,
                    "after a strike dawn owed " + c.strike.owed(a.getUUID()) + " and " + c.strike.owed(b.getUUID()) + ", want 4 and 7");
            int dawns = 4 + Strikes.BACK_PAY_DAWNS;
            ItemStack owed = wage(EntityType.COW, dawns);
            // the demands: one wage, the whole bill
            Component demands = Strikes.demands(c);
            h.assertTrue(key(demands).equals("message.headinajar.strike.demands_one"), "demands " + key(demands));
            h.assertTrue(demands.getString().contains(String.valueOf(owed.getCount())), "the demands do not say " + owed.getCount() + ": " + demands.getString());
            h.assertTrue(use(p, theRep, ItemStack.EMPTY), "an empty hand on the Rep did nothing");
            // a gift
            h.assertTrue(use(p, theRep, new ItemStack(Items.STICK)), "a stick on the Rep did nothing");
            h.assertValueEqual(Refusals.lastDelivered(p), "strike.gift", "the gift line");
            // a cake with nothing in the drawer
            h.assertTrue(use(p, theRep, new ItemStack(Items.CAKE)) && c.strike.active(), "the cake with no back pay");
            h.assertValueEqual(Refusals.lastDelivered(p), "strike.cake", "the pizza party line");
            // half of it
            c.drawer().insertItem(0, owed.copyWithCount(owed.getCount() / 2), false);
            h.assertTrue(use(p, theRep, new ItemStack(Items.CAKE)) && c.strike.active(), "the cake with half the back pay");
            h.assertValueEqual(Refusals.lastDelivered(p), "strike.short", "the short line");
            // all of it and one over: taken exactly
            c.drawer().insertItem(1, owed.copyWithCount(owed.getCount() - owed.getCount() / 2 + 1), false);
            h.assertTrue(use(p, theRep, new ItemStack(Items.CAKE)), "the cake with the back pay");
            h.assertTrue(!c.strike.active() && noted(c, "agreed"), "no agreement");
            int left = 0;
            for (int i = 0; i < TimeClockBlockEntity.DRAWER; i++) left += c.drawer().getStackInSlot(i).getCount();
            h.assertTrue(left == 1, left + " left in the drawer, want 1");
            for (Mob m : cows) {
                StaffFile f = Staff.of(m);
                h.assertTrue(f.morale == Strikes.AGREED_MORALE, "morale " + f.morale + " after the agreement");
                h.assertTrue(f.career.unpaidRun == 0 && !Shifts.unpaid(m, h.getLevel()), "still unpaid after the back pay");
                h.assertTrue(!SiteState.striker.test(m), "still on strike");
            }
            h.assertTrue(c.strike.cooldownUntil() == DAY + 10 + Strikes.COOLDOWN_DAYS, "the cool-down did not start");
            done(h, p);
        });
    }

    /** Whether the look last sent for this worker carries the picket sign or the rosette. */
    private static boolean signShown(Mob m) {
        int bits = com.coolfreeze23.headinajar.staff.work.WorkerLooks.sentBits(m);
        return bits >= 0 && (bits & (com.coolfreeze23.headinajar.staff.work.WorkerNet.PICKET | com.coolfreeze23.headinajar.staff.work.WorkerNet.REP)) != 0;
    }

    @GameTest(template = "platform32", timeoutTicks = 300)
    public static void theSignsComeDownWhenAStrikeEnds(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(16, 6, 20));
        onStrike(h, p, (c, cows) -> {
            for (Mob m : cows) h.assertTrue(signShown(m), "a striker's look went out without its sign");
            // the collapse: three loaded dawns, nobody paid
            for (int i = 0; i < Strikes.COLLAPSE_DAWNS; i++) SiteState.afterDawn(c);
            h.assertTrue(!c.strike.active(), "setup: the strike did not collapse");
            for (Mob m : cows) h.assertTrue(!signShown(m), "after the collapse a look still went out with a sign or rosette");
            // a second strike after the cool-down, ended by back pay and a cake
            c.strike.dayOverride = DAY + 10;
            for (Mob m : cows) file(m, f -> {
                f.morale = 20;
                f.career.unpaidRun = 2;
            });
            SiteState.afterDawn(c);
            h.assertTrue(c.strike.active(), "setup: no second strike");
            for (Mob m : cows) h.assertTrue(signShown(m), "a striker's look went out without its sign (second strike)");
            Mob theRep = cows.stream().filter(m -> m.getUUID().equals(c.strike.rep())).findFirst().orElseThrow();
            c.drawer().insertItem(0, wage(EntityType.COW, 2 * cows.size()), false);
            h.assertTrue(use(p, theRep, new ItemStack(Items.CAKE)) && !c.strike.active(), "the cake with the back pay did not end it");
            for (Mob m : cows) h.assertTrue(!signShown(m), "after the cake a look still went out with a sign or rosette");
            done(h, p);
        });
    }

    /**
     * The site unloads and loads again in the middle of a strike (its clock and its strikers saved and read back as new
     * objects, as a chunk reload makes them): the strikers stand in the ring again, say "On strike" on the Roster, and
     * the ring chants.
     */
    @GameTest(template = "platform32", timeoutTicks = 200 + Strikes.CHANT_TICKS * 2 + 200)
    public static void aStrikeGoesOnChantingAfterItsSiteLoadsAgain(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(16, 6, 24));
        onStrike(h, p, (c, cows) -> h.runAfterDelay(200, () -> {
            for (Mob m : cows) h.assertTrue(Strikes.standing(m), "setup: a striker never reached the ring");
            // one striker pushed off its slot (still in the ring), as a crowd or a gap in the ground leaves one
            Mob pushed = cows.get(0);
            BlockPos slot = Strikes.slotOf(pushed);
            Vec3 clock = Vec3.atBottomCenterOf(c.getBlockPos());
            Vec3 off = Vec3.atBottomCenterOf(slot).subtract(clock).normalize().scale(-1.5).add(Vec3.atBottomCenterOf(slot));
            pushed.setPos(off.x, pushed.getY(), off.z);
            var level = h.getLevel();
            var registries = level.registryAccess();
            // the clock read back from its save
            net.minecraft.nbt.CompoundTag clockTag = c.saveWithFullMetadata(registries);
            c.loadWithComponents(clockTag, registries);
            // each striker saved, removed and loaded again as a new entity (same UUID)
            List<Mob> back = new ArrayList<>();
            for (Mob m : cows) {
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                m.saveWithoutId(tag);
                tag.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString());
                m.discard();
                net.minecraft.world.entity.Entity e = EntityType.loadEntityRecursive(tag, level, x -> x);
                h.assertTrue(e instanceof Mob && level.addFreshEntity(e), "setup: a striker did not load again");
                back.add((Mob) e);
            }
            int chantsBefore = chants(c);
            // a few ticks later every striker stands in the ring again, the pushed one where it is
            h.runAfterDelay(5, () -> {
                for (Mob m : back) h.assertTrue(Strikes.standing(m), "a striker was not standing in the ring 5 ticks after the reload");
            });
            h.runAfterDelay(Strikes.CHANT_TICKS * 2, () -> {
                for (Mob m : back) {
                    h.assertTrue(Strikes.clockOf(m) != null, "a striker is off the ring after the reload");
                    h.assertTrue(Strikes.standing(m), "a striker never stood in the ring again after the reload");
                    h.assertValueEqual(com.coolfreeze23.headinajar.staff.work.Workforce.data(m).status, "staff.headinajar.status.on_strike",
                            "a striker's Roster status after the reload");
                }
                h.assertTrue(lastChantStanding(c) > 0 && chants(c) > chantsBefore, "no chant with anyone standing after the reload");
                done(h, p);
            });
        }));
    }

    private static int chants(TimeClockBlockEntity c) {
        String at = c.getBlockPos().toShortString() + " chant ";
        int n = 0;
        for (String s : Strikes.HISTORY) if (s.startsWith(at) && !s.endsWith(" 0")) n++;
        return n;
    }

    private static int lastChantStanding(TimeClockBlockEntity c) {
        String at = c.getBlockPos().toShortString() + " chant ";
        int last = -1;
        for (String s : Strikes.HISTORY) if (s.startsWith(at)) last = Integer.parseInt(s.substring(at.length()).trim());
        return last;
    }

    /**
     * During a strike: a Clipboard on a striker keeps it on the picket line; a Clipboard sending a worker to the striking
     * site says why it cannot go (and the worker stays where it was); an empty hand or a cake on a striker who is not the
     * Rep sends you to the Rep (the cake is not eaten); the Performance Review slip used on a worker says it is read.
     */
    @GameTest(template = "platform32", timeoutTicks = 300)
    public static void strikersAndTheirSiteAnswerEveryPress(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(16, 6, 20));
        Mob outsider = MomentsGameTests.staff(h, p, EntityType.PIG, new BlockPos(2, 6, 2), Grades.INTERN);
        onStrike(h, p, (c, cows) -> {
            // setup: the pig (an Intern, so it never walked out) taken off the site, as staff of no site would be
            var od = Workforce.data(outsider);
            if (od.clock != null) com.coolfreeze23.headinajar.staff.work.Sites.leave(h.getLevel(), od.clock, outsider.getUUID());
            od.clock = null;
            od.state = com.coolfreeze23.headinajar.staff.work.WorkerData.State.AWAITING;
            Mob s = cows.stream().filter(m -> !m.getUUID().equals(c.strike.rep())).findFirst().orElseThrow();
            ItemStack board = new ItemStack(WorkRegistry.CLIPBOARD.get());
            board.set(WorkRegistry.ORDER.get(), Order.EMPTY.withClock(c.site()));
            // the Clipboard on a striker: refused, still on the ring
            h.assertTrue(!com.coolfreeze23.headinajar.item.ClipboardItem.onWorker(p, board, s), "the Clipboard moved a striker");
            h.assertValueEqual(Refusals.lastDelivered(p), "strike.striker", "the Clipboard on a striker");
            h.assertTrue(Strikes.clockOf(s) == c, "a striker left the ring");
            // the Clipboard sending another worker to the striking site: the strike is the reason, and it keeps its own site
            var was = Workforce.data(outsider).clock;
            h.assertTrue(!com.coolfreeze23.headinajar.item.ClipboardItem.onWorker(p, board, outsider), "a worker joined a striking site");
            h.assertValueEqual(Refusals.lastDelivered(p), "strike.hire", "the Clipboard to a striking site");
            h.assertTrue(java.util.Objects.equals(Workforce.data(outsider).clock, was) && !c.has(outsider.getUUID()), "the worker lost its old site");
            // an empty hand and a cake on a striker who is not the Rep
            h.assertTrue(use(p, s, ItemStack.EMPTY), "an empty hand on a striker did nothing");
            h.assertValueEqual(Refusals.lastDelivered(p), "strike.not_rep", "an empty hand on a striker");
            ItemStack cake = new ItemStack(Items.CAKE);
            h.assertTrue(use(p, s, cake), "a cake on a striker did nothing");
            h.assertValueEqual(Refusals.lastDelivered(p), "strike.cake_rep", "a cake on a striker");
            h.assertTrue(p.getMainHandItem().is(Items.CAKE) && c.strike.active(), "the cake was eaten or the strike ended");
            // the Performance Review slip on a worker
            ItemStack slip = new ItemStack(WorkRegistry.PERFORMANCE_REVIEW.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, slip);
            slip.interactLivingEntity(p, outsider, InteractionHand.MAIN_HAND);
            h.assertValueEqual(Refusals.lastDelivered(p), "review.read", "the slip on a worker");
            done(h, p);
        });
    }

    // --- 15. strikers refuse work ---------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 300)
    public static void strikersRefuseWork(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(16, 6, 20));
        TimeClockBlockEntity c = clock(h, CLOCK, p);
        List<Mob> cows = new ArrayList<>(hires(h, p, EntityType.COW, 3, Grades.ASSOCIATE));
        Mob intern = MomentsGameTests.staff(h, p, EntityType.COW, CLOCK.west(6), Grades.INTERN);
        h.runAfterDelay(30, () -> {
            joined(h, c, cows);
            joined(h, c, List.of(intern));
            for (Mob m : cows) file(m, f -> f.morale = 10);
            SiteState.afterDawn(c);
            h.assertTrue(c.strike.active(), "setup: no strike");
            Mob s = cows.get(0);
            ItemStack card = new ItemStack(WorkRegistry.JOB_CARD.get());
            card.set(WorkRegistry.JOB_CARD_DATA.get(), new Order.Card(new Order(Optional.of(c.site()), "hauling", List.of(), List.of(), "x"), "test", 0));
            h.assertTrue(!JobCardItem.give(p, card.copy(), s), "a striker took a card");
            h.assertValueEqual(Refusals.lastDelivered(p), "strike.striker", "the card");
            h.assertTrue(!JobCardItem.give(p, card.copy(), intern), "a striking site took a card");
            h.assertValueEqual(Refusals.lastDelivered(p), "strike.cards", "a card at a striking site");
            Workforce.requestLetGo(p, s);
            h.assertValueEqual(Refusals.lastDelivered(p), "strike.let_go", "Let go");
            h.assertTrue(Workforce.data(s).state != WorkerData.State.NOTICE, "a striker was let go");
            // the saw on a striking Team Lead with a jar
            file(s, f -> f.career.grade = Grades.TEAM_LEAD);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
            SurgicalSawItem.saw(p, p.getMainHandItem(), s, InteractionHand.MAIN_HAND);
            h.assertValueEqual(Refusals.lastDelivered(p), "promotion.strike", "the promotion");
            h.assertTrue(s.isAlive() && !Hosts.isHost(s), "a striker was cut");
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            // new staff won't cross the picket line
            Mob newcomer = h.spawn(EntityType.COW, CLOCK.north(6));
            h.assertTrue(!Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), newcomer), "a hire onto a striking site");
            h.assertValueEqual(Refusals.lastDelivered(p), "strike.hire", "the hire");
            // the same line again a little later (a repeat within 10 ticks is one refusal)
            h.runAfterDelay(Refusals.DEBOUNCE + 2, () -> {
                Detail.whistleAt(p, s);
                h.assertValueEqual(Refusals.lastDelivered(p), "strike.striker", "the Whistle");
                h.assertTrue(Workforce.data(s).state != WorkerData.State.DETAIL, "a striker joined a detail");
                h.runAfterDelay(Refusals.DEBOUNCE + 2, () -> {
                    Shifts.recall(p, c, s);
                    h.assertValueEqual(Refusals.lastDelivered(p), "strike.striker", "Recall");
                    done(h, p);
                });
            });
        });
    }

    // --- 16. the Employee Mount ---------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void mountsUnchangedByStrikes(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(16, 6, 20));
        BlockPos machine = new BlockPos(24, 6, 24), mountAt = machine.east();
        h.setBlock(machine, ModRegistry.DNA_SEQUENCER.get());
        h.setBlock(mountAt, ModRegistry.EMPLOYEE_MOUNT.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        EmployeeMountBlockEntity mount = (EmployeeMountBlockEntity) h.getBlockEntity(mountAt);
        Villager v = h.spawn(EntityType.VILLAGER, new BlockPos(2, 6, 28));
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
        ItemStack head = JarredHeadItem.of(v);
        v.discard();
        mount.use(p, head);
        BlockPos at = h.absolutePos(machine);
        float before = HeadJobs.staff(h.getLevel(), at, "librarian");
        boolean paidBefore = mount.paid();
        long missedBefore = mount.daysMissed();
        onStrike(h, p, (c, cows) -> h.runAfterDelay(110, () -> {
            h.assertTrue(mount.solidarity(), "the mount shows no solidarity with the strike on its site");
            h.assertTrue(HeadJobs.staff(h.getLevel(), at, "librarian") == before, "the bonus changed: "
                    + HeadJobs.staff(h.getLevel(), at, "librarian") + ", was " + before);
            h.assertTrue(mount.paid() == paidBefore && mount.daysMissed() == missedBefore, "its pay changed");
            CompoundTag sync = new CompoundTag();
            mount.writeSync(sync);
            h.assertTrue(sync.getBoolean("Strike"), "its solidarity is not synced");
            for (int i = 0; i < Strikes.COLLAPSE_DAWNS; i++) SiteState.afterDawn(c);
            h.runAfterDelay(110, () -> {
                h.assertTrue(!mount.solidarity(), "still in solidarity after the strike");
                h.assertTrue(HeadJobs.staff(h.getLevel(), at, "librarian") == before, "the bonus changed after the strike");
                done(h, p);
            });
        }));
    }

    // --- 14. a strike never breaks a block or hurts anyone ------------------------------------------------------------

    /** Damage seen while a test watches (victim, attacker or projectile), once registered. */
    private static final List<UUID[]> HURT = new ArrayList<>();
    private static boolean listening;

    private static synchronized void listen() {
        if (listening) return;
        listening = true;
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent e) -> {
            var src = e.getSource();
            HURT.add(new UUID[]{e.getEntity().getUUID(), src.getEntity() == null ? null : src.getEntity().getUUID(),
                    src.getDirectEntity() == null ? null : src.getDirectEntity().getUUID()});
            if (HURT.size() > 4096) HURT.subList(0, 2048).clear();
        });
    }

    private static final BlockPos SITE = new BlockPos(17, 5, 17);

    @GameTest(template = "site34", timeoutTicks = 1400)
    public static void aStrikeNeverBreaksABlockOrHurtsAnyone(GameTestHelper h) {
        listen();
        h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(true, h.getLevel().getServer());
        ServerPlayer p = boss(h, SITE.east(3));
        h.setBlock(SITE, WorkRegistry.TIME_CLOCK.get().defaultBlockState().setValue(TimeClockBlock.FACING, Direction.SOUTH));
        TimeClockBlockEntity c = (TimeClockBlockEntity) h.getBlockEntity(SITE);
        c.timeOverride = 12000;
        c.setOwner(p);
        c.registerSite();
        c.strike.dayOverride = DAY;
        // their way in crosses farmland with wheat, turtle eggs, leaves and a door
        for (int x = 8; x <= 26; x++) {
            for (int z : new int[]{9, 25}) {
                h.setBlock(new BlockPos(x, 4, z), Blocks.FARMLAND);
                h.setBlock(new BlockPos(x, 5, z), Blocks.WHEAT);
            }
        }
        for (int z = 10; z <= 24; z += 3) h.setBlock(new BlockPos(9, 5, z), Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, 2));
        for (int z = 11; z <= 23; z += 4) h.setBlock(new BlockPos(25, 5, z), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        h.setBlock(new BlockPos(17, 5, 26), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(new BlockPos(17, 6, 26), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // eight risky strikers from all round the edge
        List<Mob> strikers = new ArrayList<>();
        EntityType<?>[] kinds = {EntityType.ZOMBIE, EntityType.SNOW_GOLEM, EntityType.GOAT, EntityType.LLAMA, EntityType.ENDERMAN,
                EntityType.PIGLIN, EntityType.CHICKEN};
        BlockPos[] from = {new BlockPos(5, 5, 5), new BlockPos(29, 5, 5), new BlockPos(5, 5, 29), new BlockPos(29, 5, 29),
                new BlockPos(17, 5, 4), new BlockPos(4, 5, 17), new BlockPos(30, 5, 17)};
        for (int i = 0; i < kinds.length; i++) {
            @SuppressWarnings("unchecked") EntityType<? extends Mob> t = (EntityType<? extends Mob>) kinds[i];
            Mob m = MomentsGameTests.staff(h, p, t, from[i], Grades.ASSOCIATE);
            if (m instanceof IronGolem g) g.setPlayerCreated(true);
            strikers.add(m);
        }
        // the zombie in its Hard Hat (Night Shift does not burn in one)
        strikers.get(0).setItemSlot(EquipmentSlot.HEAD, new ItemStack(WorkRegistry.HARD_HAT.get()));
        // the eighth: a ravager body with a zombie's head, seconded
        Mob ravager = h.spawn(EntityType.RAVAGER, new BlockPos(17, 5, 30));
        ravager.setHealth(1);
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), ravager, InteractionHand.MAIN_HAND);
        h.assertTrue(Hosts.isHost(ravager), "setup: no ravager host: " + Refusals.lastDelivered(p));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SecondRegistry.SECONDMENT_COLLAR.get()));
        Secondment.instrument(p, p, p.getMainHandItem(), ravager, InteractionHand.MAIN_HAND);
        Mob zombieHead = EntityType.ZOMBIE.create(h.getLevel());
        p.setItemInHand(InteractionHand.MAIN_HAND, JarredHeadItem.of(zombieHead));
        Secondment.instrument(p, p, p.getMainHandItem(), ravager, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(Secondees.seated(ravager) && Workforce.isWorker(ravager), "setup: the zombie head did not seat on the ravager: " + Refusals.lastDelivered(p));
        Secondees.data(ravager).canister = com.coolfreeze23.headinajar.staff.second.SecondeeData.CANISTER;
        ravager.setHealth(ravager.getMaxHealth());
        file(ravager, f -> f.career.grade = Grades.ASSOCIATE);
        strikers.add(ravager);
        // the employer, a villager and a tamed wolf stand where the ring's slots will be
        p.moveTo(h.absoluteVec(Vec3.atBottomCenterOf(SITE.south(3))));
        Villager villager = h.spawn(EntityType.VILLAGER, SITE.north(3));
        // a bystander, not staff: it stands in the slot (its own brain would open the test's door, which is no striker's doing)
        villager.setNoAi(true);
        Wolf wolf = h.spawn(EntityType.WOLF, SITE.east(3));
        wolf.tame(p);
        // a bystander too: a pet's own walk may tread on the eggs, which is no striker's doing
        wolf.setNoAi(true);
        Set<UUID> watched = new HashSet<>();
        for (Mob m : strikers) watched.add(m.getUUID());
        Set<UUID> bystanders = Set.of(p.getUUID(), villager.getUUID(), wolf.getUUID());
        h.runAfterDelay(80, () -> {
            joined(h, c, strikers);
            // the snapshot, once everyone is on the roster: every block in the site's 33 x 17 x 33 box
            BlockPos clock = h.absolutePos(SITE);
            Map<BlockPos, BlockState> before = new HashMap<>();
            for (BlockPos b : BlockPos.betweenClosed(clock.offset(-16, -8, -16), clock.offset(16, 8, 16))) before.put(b.immutable(), h.getLevel().getBlockState(b));
            int hurtFrom = HURT.size();
            for (Mob m : strikers) file(m, f -> f.morale = 10);
            SiteState.afterDawn(c);
            h.assertTrue(c.strike.active() && c.strike.ring().size() == strikers.size(), "setup: the strike took " + c.strike.ring().size());
            h.runAfterDelay(600, () -> {
                for (Mob m : strikers) {
                    h.assertTrue(m.isAlive(), "a " + m.getType().getDescription().getString() + " striker died");
                    h.assertTrue(h.getLevel().noCollision(m, m.getBoundingBox().deflate(0.01)), "a " + m.getType().getDescription().getString() + " stands inside a block");
                }
                h.assertTrue(noted(c, "chant"), "no chant in 600 ticks");
                for (int i = 0; i < Strikes.COLLAPSE_DAWNS; i++) SiteState.afterDawn(c);
                h.assertTrue(!c.strike.active(), "no collapse");
                h.runAfterDelay(200, () -> {
                    for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
                        BlockState now = h.getLevel().getBlockState(e.getKey());
                        // a growing crop is the farm's, not a striker's doing
                        if (now.getBlock() == Blocks.WHEAT && e.getValue().getBlock() == Blocks.WHEAT) continue;
                        // nor is grass dying under a block set on it
                        if (e.getValue().is(Blocks.GRASS_BLOCK) && now.is(Blocks.DIRT)) continue;
                        // nor is the clock's own lamp
                        if (e.getKey().equals(clock)) continue;
                        h.assertTrue(now.equals(e.getValue()), "a block changed at " + e.getKey().subtract(clock).toShortString() + ": " + e.getValue() + " -> " + now);
                    }
                    for (UUID[] hit : HURT.subList(Math.min(hurtFrom, HURT.size()), HURT.size())) {
                        boolean byStriker = hit[1] != null && watched.contains(hit[1]) || hit[2] != null && watched.contains(hit[2]);
                        h.assertTrue(!byStriker, "a striker hurt something");
                        boolean ours = watched.contains(hit[0]) || bystanders.contains(hit[0]);
                        // the secondee body's own rot is the one cost of a strike
                        h.assertTrue(!ours || hit[0].equals(ravager.getUUID()), "someone in the site was hurt");
                    }
                    done(h, p);
                });
            });
        });
    }

    // --- the chant ------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 900)
    public static void theRingChants(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(16, 6, 24));
        onStrike(h, p, (c, cows) -> {
            h.runAfterDelay(Strikes.CHANT_TICKS + 300, () -> {
                h.assertTrue(noted(c, "chant"), "no chant in a round and a half");
                for (Mob m : cows) h.assertTrue(Fx.count(Fx.CHANT, m.getId()) >= 1, "a striker never pumped to the chant");
                done(h, p);
            });
        });
    }
}
