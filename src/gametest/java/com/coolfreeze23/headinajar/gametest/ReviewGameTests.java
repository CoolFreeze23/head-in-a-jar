package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.item.ClipboardItem;
import com.coolfreeze23.headinajar.item.JobCardItem;
import com.coolfreeze23.headinajar.item.PerformanceReviewItem;
import com.coolfreeze23.headinajar.menu.ClipboardMenu;
import com.coolfreeze23.headinajar.menu.TimeClockMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Grades;
import com.coolfreeze23.headinajar.staff.Reviews;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.StaffTable;
import com.coolfreeze23.headinajar.staff.work.CardFilters;
import com.coolfreeze23.headinajar.staff.work.ClockNet;
import com.coolfreeze23.headinajar.staff.work.GradeWork;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Grades and the Performance Review (0.7.2): the Review press from the Roster (grades by tasks, skipping grades, the four
 * ratings with the Performance Plan first, +5 morale, once a day, no paper, untrusted, the empty review), what grades do
 * at work (speed, the Team Lead's aura over its own site and never stacked, a Senior Associate's carry, a Team Lead's
 * wage, the review-due reminder), Sorting filters by grade on cards printed from 0.7.2 with older cards read as they
 * always were, and a staff file saved by 0.7.1 loading as an Intern with its tasks.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReviewGameTests {
    private static final BlockPos CLOCK = new BlockPos(16, 6, 22);

    private static ServerPlayer boss(GameTestHelper h) {
        return TestPlayers.at(h, "Reviewer", new BlockPos(16, 6, 19));
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** A Time Clock of {@code owner}'s, powered (the Redstone shift runs). */
    private static TimeClockBlockEntity clock(GameTestHelper h, ServerPlayer owner, BlockPos at) {
        h.setBlock(at, WorkRegistry.TIME_CLOCK.get());
        h.setBlock(at.below(), Blocks.REDSTONE_BLOCK);
        TimeClockBlockEntity c = (TimeClockBlockEntity) h.getBlockEntity(at);
        c.setOwner(owner);
        return c;
    }

    /** A creature hired with the Lanyard, joined to the clock, on the payroll at 50, with these tasks and this grade. */
    private static <T extends Mob> T hire(GameTestHelper h, ServerPlayer p, TimeClockBlockEntity clock, EntityType<T> type, BlockPos at,
                                          int tasks, int grade) {
        T mob = h.spawn(type, at);
        if (mob instanceof Enemy) mob.setHealth(mob.getMaxHealth() * 0.5f);
        h.assertTrue(Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), mob), "setup: not hired: " + Refusals.lastDelivered(p));
        h.assertTrue(clock.join(mob), "setup: the clock had no room");
        StaffFile f = Staff.of(mob);
        f.morale = 50;
        f.tasks = tasks;
        f.career.grade = grade;
        f.career.tasksAtReview = Math.min(tasks, Grades.TASKS[grade]);
        mob.setData(ModRegistry.STAFF, f);
        return mob;
    }

    /** Paper in the pockets, clear of the hotbar (the main hand stays as the test sets it). */
    private static void givePaper(ServerPlayer p, int n) {
        ItemStack s = p.getInventory().getItem(9);
        p.getInventory().setItem(9, new ItemStack(Items.PAPER, n + (s.is(Items.PAPER) ? s.getCount() : 0)));
    }

    private static int paper(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(Items.PAPER)) n += s.getCount();
        return n;
    }

    private static ItemStack slip(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (s.is(WorkRegistry.PERFORMANCE_REVIEW.get())) return s;
        return ItemStack.EMPTY;
    }

    /** The Review pressed on the Roster, through the clock's menu (the real path from the screen's order). */
    private static void pressReview(ServerPlayer p, TimeClockBlockEntity clock, Mob mob) {
        TimeClockMenu menu = new TimeClockMenu(1, p.getInventory(), clock);
        menu.order(p, ClockNet.REVIEW, mob.getUUID(), 0);
    }

    // --- 8. reviews grade and rate ------------------------------------------------------------------------------------

    @GameTest(template = "platform32")
    public static void aReviewSkipsGradesAndPrintsTheSlip(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        Mob cow = hire(h, p, clock, EntityType.COW, new BlockPos(14, 6, 20), 412, Grades.INTERN);
        givePaper(p, 3);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(Grades.due(Staff.of(cow)), "412 tasks and an Intern: not due");
        pressReview(p, clock, cow);
        StaffFile f = Staff.of(cow);
        h.assertValueEqual(f.career.grade, Grades.SENIOR, "the grade (an Intern with 412 tasks skips to Senior Associate)");
        h.assertValueEqual(paper(p), 2, "paper left");
        h.assertValueEqual(f.morale, 50 + Reviews.MORALE, "morale");
        h.assertValueEqual(f.career.tasksAtReview, 412, "tasks at review");
        h.assertValueEqual(f.career.reviewedDay, Staff.day(h.getLevel()), "the review's day");
        h.assertTrue(f.history.stream().anyMatch(e -> e.k().equals("reviewed")), "no history line");
        h.assertFalse(Grades.due(f), "still due after the review");
        ItemStack held = p.getMainHandItem();
        h.assertTrue(held.is(WorkRegistry.PERFORMANCE_REVIEW.get()), "the slip is not in the hand: " + held);
        PerformanceReviewItem.Slip s = held.get(WorkRegistry.REVIEW.get());
        h.assertTrue(s != null, "the slip says nothing");
        h.assertValueEqual(s.tasks(), 412, "the slip's tasks");
        h.assertValueEqual(s.rating(), PerformanceReviewItem.MEETS, "the rating (412 tasks, Content)");
        h.assertValueEqual(s.grade(), Grades.SENIOR, "the slip's grade");
        h.assertTrue(s.raised(), "the slip does not say the grade was raised");
        h.assertValueEqual(s.number(), f.number, "the slip's staff number");
        h.assertValueEqual(s.reviewer(), p.getGameProfile().getName(), "the reviewer");
        done(h, p);
    }

    @GameTest(template = "platform32")
    public static void aReviewOnceADayWithPaperByTheTrusted(GameTestHelper h) {
        ServerPlayer p = boss(h), stranger = TestPlayers.at(h, "Stranger", new BlockPos(18, 6, 19));
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        Mob cow = hire(h, p, clock, EntityType.COW, new BlockPos(14, 6, 20), 60, Grades.INTERN);
        // no paper
        pressReview(p, clock, cow);
        h.assertValueEqual(Refusals.lastDelivered(p), "review.paper", "no paper");
        h.assertValueEqual(Grades.of(Staff.of(cow)), Grades.INTERN, "graded with no paper");
        // someone else's worker
        givePaper(stranger, 1);
        h.assertFalse(Reviews.review(stranger, clock, cow), "a stranger reviewed someone else's worker");
        h.assertValueEqual(Refusals.lastDelivered(stranger), "review.untrusted", "untrusted");
        h.assertValueEqual(paper(stranger), 1, "the stranger's paper was taken");
        // the review, then the same day again
        givePaper(p, 2);
        pressReview(p, clock, cow);
        h.assertValueEqual(Grades.of(Staff.of(cow)), Grades.ASSOCIATE, "the first review");
        int morale = Staff.of(cow).morale;
        pressReview(p, clock, cow);
        h.assertValueEqual(Refusals.lastDelivered(p), "review.today", "twice in a day");
        h.assertValueEqual(paper(p), 1, "the second review took paper");
        h.assertValueEqual(Staff.of(cow).morale, morale, "the second review gave morale");
        // a worker that is not here: the Roster's own refusal
        UUID gone = UUID.randomUUID();
        clock.roster().add(gone);
        new TimeClockMenu(1, p.getInventory(), clock).order(p, ClockNet.REVIEW, gone, 0);
        h.assertValueEqual(Refusals.lastDelivered(p), "review.away", "a worker not here");
        clock.roster().remove(gone);
        done(h, p, stranger);
    }

    @GameTest(template = "platform32")
    public static void theEmptyReviewStillPrints(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        Mob cow = hire(h, p, clock, EntityType.COW, new BlockPos(14, 6, 20), 0, Grades.INTERN);
        givePaper(p, 1);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        pressReview(p, clock, cow);
        ItemStack slip = slip(p);
        h.assertTrue(!slip.isEmpty(), "no slip printed: " + Refusals.lastDelivered(p));
        PerformanceReviewItem.Slip s = slip.get(WorkRegistry.REVIEW.get());
        h.assertTrue(s.empty(), "not the empty review");
        h.assertValueEqual(s.rating(), PerformanceReviewItem.NEEDS, "the empty review's rating");
        h.assertFalse(s.raised(), "the empty review raised a grade");
        h.assertValueEqual(Staff.of(cow).morale, 50 + Reviews.MORALE, "+5 morale whatever it says");
        h.assertTrue(p.getMainHandItem().is(Items.STICK), "the stick in the hand was replaced");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void gradesAndRatingsByTheTable(GameTestHelper h) {
        int[][] grades = {{0, 0}, {49, 0}, {50, 1}, {299, 1}, {300, 2}, {999, 2}, {1000, 3}, {5000, 3}};
        for (int[] g : grades) h.assertValueEqual(Grades.earned(g[0]), g[1], g[0] + " tasks");
        // the Performance Plan first, whatever else it did
        h.assertValueEqual(Reviews.rating(file(200, 80, 1)), PerformanceReviewItem.PLAN, "a grievance with 200 tasks, Engaged");
        h.assertValueEqual(Reviews.rating(file(100, 80, 0)), PerformanceReviewItem.EXCEEDS, "100 tasks, Engaged");
        h.assertValueEqual(Reviews.rating(file(100, 60, 0)), PerformanceReviewItem.MEETS, "100 tasks, Content");
        h.assertValueEqual(Reviews.rating(file(99, 80, 0)), PerformanceReviewItem.MEETS, "99 tasks, Engaged");
        h.assertValueEqual(Reviews.rating(file(25, 50, 0)), PerformanceReviewItem.MEETS, "25 tasks");
        h.assertValueEqual(Reviews.rating(file(24, 80, 0)), PerformanceReviewItem.NEEDS, "24 tasks");
        h.assertValueEqual(Reviews.rating(file(0, 50, 0)), PerformanceReviewItem.NEEDS, "nothing");
        h.succeed();
    }

    /** A file with tasks since its last review, morale and grievances since. */
    private static StaffFile file(int since, int morale, int grievances) {
        StaffFile f = new StaffFile();
        f.number = 1;
        f.morale = morale;
        f.tasks = 1000 + since;
        f.career.tasksAtReview = 1000;
        f.career.grievancesSinceReview = grievances;
        return f;
    }

    @GameTest(template = "platform32")
    public static void aTeamLeadsReviewSendsTheMemo(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        Mob cow = hire(h, p, clock, EntityType.COW, new BlockPos(14, 6, 20), 1000, Grades.SENIOR);
        givePaper(p, 1);
        pressReview(p, clock, cow);
        h.assertValueEqual(Grades.of(Staff.of(cow)), Grades.TEAM_LEAD, "the grade");
        h.assertTrue(Memos.told(p, Memos.Memo.TEAM_LEAD) > 0, "no Team Lead memo");
        done(h, p);
    }

    // --- 9. grades change the work --------------------------------------------------------------------------------------

    @GameTest(template = "platform32")
    public static void gradesSpeedTheWorkAndATeamLeadsAuraDoesNotStack(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        Mob intern = hire(h, p, clock, EntityType.COW, new BlockPos(4, 6, 4), 0, Grades.INTERN);
        Mob[] graded = new Mob[4];
        for (int g = 0; g < 4; g++) graded[g] = hire(h, p, clock, EntityType.COW, new BlockPos(28, 6, 4 + g * 6), Grades.TASKS[g], g);
        GradeWork.forget(clock.site());
        // the grade's own speed (the Team Lead at the far end is beyond everyone's 8 blocks but its neighbour's)
        h.assertTrue(Math.abs(GradeWork.speed(intern) - 1.0f) < 1e-4, "an Intern alone: " + GradeWork.speed(intern));
        h.assertTrue(Math.abs(GradeWork.speed(graded[1]) - 1.1f) < 1e-4, "an Associate: " + GradeWork.speed(graded[1]));
        h.assertTrue(Math.abs(GradeWork.speed(graded[3]) - 1.3f) < 1e-4, "a Team Lead alone: " + GradeWork.speed(graded[3]));
        // the aura: within 8 blocks of a Team Lead of the same site, 10% faster; the Senior 6 blocks away
        h.assertTrue(Math.abs(GradeWork.speed(graded[2]) - 1.2f * 1.1f) < 1e-4, "a Senior beside a Team Lead: " + GradeWork.speed(graded[2]));
        // a second Team Lead beside the Intern: one Team Lead's worth, never two
        Mob lead2 = hire(h, p, clock, EntityType.COW, new BlockPos(6, 6, 4), 1000, Grades.TEAM_LEAD);
        Mob lead3 = hire(h, p, clock, EntityType.COW, new BlockPos(4, 6, 6), 1000, Grades.TEAM_LEAD);
        GradeWork.forget(clock.site());
        h.assertTrue(Math.abs(GradeWork.speed(intern) - 1.1f) < 1e-4, "an Intern by two Team Leads: " + GradeWork.speed(intern));
        h.assertTrue(Math.abs(GradeWork.speed(lead2) - 1.3f * 1.1f) < 1e-4, "a Team Lead by another: " + GradeWork.speed(lead2));
        // another site's Team Lead gives nothing
        ServerPlayer other = TestPlayers.at(h, "Other", new BlockPos(2, 6, 28));
        TimeClockBlockEntity theirs = clock(h, other, new BlockPos(4, 6, 28));
        Mob visitor = hire(h, other, theirs, EntityType.COW, new BlockPos(5, 6, 5), 1000, Grades.TEAM_LEAD);
        lead2.discard();
        lead3.discard();
        GradeWork.forget(clock.site());
        GradeWork.forget(theirs.site());
        h.assertTrue(Math.abs(GradeWork.speed(intern) - 1.0f) < 1e-4, "another site's Team Lead sped an Intern: " + GradeWork.speed(intern));
        h.assertTrue(visitor.isAlive(), "setup");
        // a Team Lead's wage is two of its species'
        StaffTable.Wage base = StaffTable.wage(EntityType.COW);
        h.assertValueEqual(Grades.wage(graded[3]).count(), base.count() * 2, "a Team Lead's wage");
        h.assertValueEqual(Grades.wage(graded[2]).count(), base.count(), "a Senior's wage");
        done(h, p, other);
    }

    @GameTest(template = "platform32", timeoutTicks = 1200)
    public static void aSeniorAssociateCarriesHalfAgain(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        BlockPos fromAt = new BlockPos(11, 6, 16), toAt = new BlockPos(21, 6, 16);
        h.setBlock(fromAt, Blocks.CHEST);
        ChestBlockEntity from = (ChestBlockEntity) h.getBlockEntity(fromAt);
        for (int i = 0; i < 12; i++) from.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        h.setBlock(toAt, Blocks.CHEST);
        Villager v = hire(h, p, clock, EntityType.VILLAGER, new BlockPos(16, 6, 13), Grades.TASKS[Grades.SENIOR], Grades.SENIOR);
        WorkTable.Spec spec = WorkTable.of(v);
        int carry = Math.max(1, spec.carry());
        int expect = (int) Math.floor(carry * 1.5f * 64);
        if (spec.carryItems() > 0) expect = Math.min(expect, (int) Math.floor(spec.carryItems() * 1.5f));
        expect = Math.min(expect, Math.min(WorkerData.MAX_CARRIED, (int) Math.ceil(carry * 1.5f)) * 64);
        ItemStack card = new ItemStack(WorkRegistry.JOB_CARD.get());
        Order o = Order.EMPTY.withClock(clock.site()).withJob(WorkTable.HAULING).withStops(List.of(
                new Order.Stop(h.absolutePos(fromAt), Direction.UP, false, ItemStack.EMPTY, false),
                new Order.Stop(h.absolutePos(toAt), Direction.UP, true, ItemStack.EMPTY, false)));
        card.set(WorkRegistry.JOB_CARD_DATA.get(), new Order.Card(o, "Boss", 0));
        h.assertTrue(JobCardItem.give(p, card, v), "setup: the card was refused: " + Refusals.lastDelivered(p));
        WorkerData d = Workforce.data(v);
        d.shift = WorkerData.Shift.REDSTONE;
        d.state = WorkerData.State.ON_JOB;
        int[] most = {0};
        int want = expect;
        h.succeedWhen(() -> {
            int now = 0;
            for (ItemStack s : d.carried) now += s.getCount();
            most[0] = Math.max(most[0], now);
            h.assertTrue(most[0] <= want, "carried " + most[0] + ", more than " + want);
            h.assertTrue(most[0] == want, "carried at most " + most[0] + " of " + want);
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32")
    public static void aReviewDueIsRemindedEverySixThousandTicksOfWork(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        Mob cow = hire(h, p, clock, EntityType.COW, new BlockPos(14, 6, 20), 60, Grades.INTERN);
        WorkerData d = Workforce.data(cow);
        d.status = null;
        GradeWork.worked(cow, GradeWork.NAG_TICKS - 10);
        h.assertTrue(d.status == null || !d.status.endsWith("review_due"), "reminded before 6,000 ticks of work");
        h.assertTrue(Memos.told(p, Memos.Memo.REVIEW_DUE) > 0, "no first memo to the employer");
        GradeWork.worked(cow, 10);
        h.assertValueEqual(d.status, "staff.headinajar.say.review_due", "the reminder");
        h.assertValueEqual(GradeWork.worked(cow), 0, "the count did not start again");
        // not due: nothing counts
        Staff.of(cow).career.grade = Grades.ASSOCIATE;
        GradeWork.worked(cow, 100);
        h.assertValueEqual(GradeWork.worked(cow), 0, "counted while not due");
        done(h, p);
    }

    // --- 10. old cards keep working -------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void oldCardsKeepWorking(GameTestHelper h) {
        BlockPos a = h.absolutePos(new BlockPos(1, 2, 1)), b = h.absolutePos(new BlockPos(3, 2, 1)), c = h.absolutePos(new BlockPos(5, 2, 1));
        Order o = Order.EMPTY.withJob(WorkTable.SORTING).withStops(List.of(
                new Order.Stop(c, Direction.UP, false, ItemStack.EMPTY, false),
                new Order.Stop(a, Direction.UP, true, new ItemStack(Items.SAND), false),
                new Order.Stop(b, Direction.UP, true, ItemStack.EMPTY, true)));
        ItemStack[] kinds = {new ItemStack(Items.SAND), new ItemStack(Items.COBBLESTONE), new ItemStack(Items.DIRT), new ItemStack(Items.GRAVEL)};
        // a card printed before 0.7.2 (no filters component): exactly as 0.7.0 read it, whoever works it
        for (ItemStack k : kinds) {
            for (int allowance : new int[]{1, 3, 9}) {
                h.assertTrue(CardFilters.destinations(o, k, true, null, allowance).equals(o.destinations(k, true)), "an old card read differently: " + k);
            }
        }
        // a 0.7.2 card: the Drop-Off at a also names cobblestone and dirt; an Intern reads its first filter only
        CardFilters f = CardFilters.NONE.withExtra(a, 0, new ItemStack(Items.COBBLESTONE)).withExtra(a, 1, new ItemStack(Items.DIRT)).stamped();
        h.assertTrue(f.printed(), "not printed");
        h.assertValueEqual(CardFilters.destinations(o, kinds[0], true, f, Grades.FILTERS[Grades.INTERN]).get(0).pos(), a, "an Intern's sand");
        h.assertValueEqual(CardFilters.destinations(o, kinds[1], true, f, Grades.FILTERS[Grades.INTERN]).get(0).pos(), b, "an Intern's cobblestone");
        h.assertValueEqual(CardFilters.destinations(o, kinds[1], true, f, Grades.FILTERS[Grades.ASSOCIATE]).get(0).pos(), a, "an Associate's cobblestone");
        h.assertValueEqual(CardFilters.destinations(o, kinds[2], true, f, Grades.FILTERS[Grades.ASSOCIATE]).get(0).pos(), a, "an Associate's dirt");
        h.assertValueEqual(CardFilters.destinations(o, kinds[3], true, f, Grades.FILTERS[Grades.TEAM_LEAD]).get(0).pos(), b, "gravel to everything else");
        h.assertValueEqual(f.count(o.stopAt(a)), 3, "filters on the Drop-Off");
        h.assertValueEqual(CardFilters.gradeFor(3), Grades.ASSOCIATE, "3 filters");
        h.assertValueEqual(CardFilters.gradeFor(4), Grades.SENIOR, "4 filters");
        h.assertValueEqual(CardFilters.gradeFor(9), Grades.TEAM_LEAD, "9 filters");
        // Hauling never reads them
        h.assertTrue(CardFilters.destinations(o, kinds[1], false, f, 9).equals(o.destinations(kinds[1], false)), "Hauling read the extras");
        h.succeed();
    }

    @GameTest(template = "platform32")
    public static void printRefusesFiltersNobodyCanRead(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        Mob cow = hire(h, p, clock, EntityType.COW, new BlockPos(14, 6, 20), 0, Grades.INTERN);
        BlockPos from = h.absolutePos(new BlockPos(11, 6, 16)), to = h.absolutePos(new BlockPos(21, 6, 16));
        h.setBlock(new BlockPos(11, 6, 16), Blocks.CHEST);
        h.setBlock(new BlockPos(21, 6, 16), Blocks.CHEST);
        ItemStack board = new ItemStack(WorkRegistry.CLIPBOARD.get());
        board.set(WorkRegistry.ORDER.get(), Order.EMPTY.withClock(clock.site()).withJob(WorkTable.SORTING).withStops(List.of(
                new Order.Stop(from, Direction.UP, false, ItemStack.EMPTY, false),
                new Order.Stop(to, Direction.UP, true, new ItemStack(Items.SAND), false))));
        CardFilters four = CardFilters.NONE;
        ItemStack[] extra = {new ItemStack(Items.COBBLESTONE), new ItemStack(Items.DIRT), new ItemStack(Items.GRAVEL)};
        for (int k = 0; k < extra.length; k++) four = four.withExtra(to, k, extra[k]);
        CardFilters.put(board, four);
        givePaper(p, 2);
        // four filters on a Drop-Off need a Senior Associate; the site has an Intern
        h.assertFalse(ClipboardItem.print(p, board), "printed a card nobody on the site can read");
        h.assertValueEqual(Refusals.lastDelivered(p), "print.grade", "the refusal");
        h.assertValueEqual(paper(p), 2, "the refused print took paper");
        // a Senior Associate on the roster: printed, by grade, with the extras
        Staff.of(cow).career.grade = Grades.SENIOR;
        h.assertTrue(ClipboardItem.print(p, board), "not printed with a Senior on the roster: " + Refusals.lastDelivered(p));
        ItemStack card = ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().items) if (s.is(WorkRegistry.JOB_CARD.get())) card = s;
        CardFilters printed = CardFilters.of(card);
        h.assertTrue(printed != null && printed.printed(), "the card carries no print version");
        h.assertValueEqual(printed.count(Order.card(card).order().stopAt(to)), 4, "the card's filters on the Drop-Off");
        // a one-filter Sorting card always prints, and so does Hauling (and they carry the print version too)
        CardFilters.put(board, CardFilters.NONE);
        Staff.of(cow).career.grade = Grades.INTERN;
        h.assertTrue(ClipboardItem.print(p, board), "a one-filter card was refused: " + Refusals.lastDelivered(p));
        done(h, p);
    }

    @GameTest(template = "platform32")
    public static void extraFiltersAreForSortingDropOffs(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        BlockPos from = h.absolutePos(new BlockPos(11, 6, 16)), to = h.absolutePos(new BlockPos(21, 6, 16));
        ItemStack board = new ItemStack(WorkRegistry.CLIPBOARD.get());
        board.set(WorkRegistry.ORDER.get(), Order.EMPTY.withClock(clock.site()).withJob(WorkTable.HAULING).withStops(List.of(
                new Order.Stop(from, Direction.UP, false, ItemStack.EMPTY, false),
                new Order.Stop(to, Direction.UP, true, ItemStack.EMPTY, false))));
        p.setItemInHand(InteractionHand.MAIN_HAND, board);
        ClipboardMenu menu = new ClipboardMenu(1, p.getInventory(), InteractionHand.MAIN_HAND);
        menu.clickMenuButton(p, ClipboardMenu.MORE + 1);
        h.assertValueEqual(Refusals.lastDelivered(p), "clipboard.more_sorting", "extras on Hauling");
        h.assertValueEqual(menu.picked(), -1, "Hauling's Drop-Off picked");
        ClipboardItem.edit(p.getMainHandItem(), o -> o.withJob(WorkTable.SORTING));
        menu.clickMenuButton(p, ClipboardMenu.MORE + 0);
        h.assertValueEqual(Refusals.lastDelivered(p), "clipboard.more_drop_off", "extras on a Pick-Up");
        menu.clickMenuButton(p, ClipboardMenu.MORE + 1);
        h.assertValueEqual(menu.picked(), 1, "the Drop-Off was not picked");
        h.assertTrue(menu.setExtra(0, new ItemStack(Items.DIRT)), "no extra set");
        h.assertValueEqual(CardFilters.onBoard(p.getMainHandItem()).count(Order.of(p.getMainHandItem()).stopAt(to)), 1, "the extra on the board");
        // unmarking the Drop-Off takes its extras with it
        ClipboardItem.edit(p.getMainHandItem(), o -> o.withStops(List.of(o.stops().get(0))));
        h.assertTrue(CardFilters.onBoard(p.getMainHandItem()).stops().isEmpty(), "the extras outlived their Drop-Off");
        done(h, p);
    }

    // --- 18. a staff file from 0.7.1 ------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aFileFrom071LoadsAsAnInternWithItsTasks(GameTestHelper h) {
        // a file exactly as 0.7.1 saved it (no career field), pinned
        CompoundTag old = new CompoundTag();
        old.putInt("number", 412);
        old.putString("species", "minecraft:cow");
        old.putInt("nameIndex", 3);
        old.putString("employerName", "Steve");
        old.putLong("joined", 2);
        ListTag history = new ListTag();
        CompoundTag hired = new CompoundTag();
        hired.putString("k", "hired");
        hired.putLong("d", 2);
        hired.putString("a", "Steve");
        history.add(hired);
        old.put("history", history);
        old.putInt("morale", 64);
        old.putLong("paidThrough", 40);
        old.putLong("lastDawn", 40);
        old.putInt("tasks", 300);
        StaffFile f = StaffFile.load(old);
        h.assertTrue(f != null, "the 0.7.1 file did not load");
        h.assertValueEqual(Grades.of(f), Grades.INTERN, "its grade");
        h.assertValueEqual(f.tasks, 300, "its tasks");
        h.assertValueEqual(f.career.reviewedDay, -1L, "never reviewed");
        h.assertValueEqual(f.career.promotedDay, -1L, "never promoted");
        h.assertFalse(f.career.grievance, "a grievance from nowhere");
        h.assertTrue(Grades.due(f), "300 tasks and no review: not due");
        h.assertValueEqual(Grades.earned(f.tasks), Grades.SENIOR, "what its review would make it");
        h.assertValueEqual(f.morale, 64, "its morale");
        // saved again untouched, it is the same file (no career field written)
        CompoundTag again = f.save();
        h.assertFalse(again.contains("career"), "a career field written for an untouched file");
        h.assertTrue(again.equals(old), "the file changed on a round trip: " + again + " vs " + old);
        // on a creature
        Mob cow = h.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        cow.setData(ModRegistry.STAFF, f);
        h.assertValueEqual(Grades.of(Staff.of(cow)), Grades.INTERN, "on the cow");
        h.succeed();
    }
}
