package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.item.ClipboardItem;
import com.coolfreeze23.headinajar.item.JobCardItem;
import com.coolfreeze23.headinajar.menu.ClipboardMenu;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import com.coolfreeze23.headinajar.staff.work.jobs.Farming;
import com.coolfreeze23.headinajar.staff.work.jobs.Jobs;
import com.coolfreeze23.headinajar.staff.work.jobs.Sorting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The Clipboard, Job Cards and the four jobs (0.7.0): marking and printing, giving cards and their refusals, Hauling,
 * Sorting, Farming and Security worked by real hired staff on a site in {@code platform32} (the floor's top is helper y
 * 5; the clock stands on it at the middle). Workers are set to the Redstone shift with the clock powered, so the tests
 * do not hang on the time of day.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class StaffJobGameTests {
    private static final BlockPos CLOCK = new BlockPos(16, 6, 22);
    private static final String SECURITY = "staff_security";

    // --- set-up -------------------------------------------------------------------------------------------------------

    private static ServerPlayer boss(GameTestHelper h) {
        return TestPlayers.at(h, "Boss", new BlockPos(1, 6, 1));
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** A Time Clock of {@code owner}'s at {@code at}, powered (the Redstone shift runs). */
    private static TimeClockBlockEntity clock(GameTestHelper h, ServerPlayer owner, BlockPos at) {
        h.setBlock(at, WorkRegistry.TIME_CLOCK.get());
        h.setBlock(at.below(), Blocks.REDSTONE_BLOCK);
        TimeClockBlockEntity c = (TimeClockBlockEntity) h.getBlockEntity(at);
        c.setOwner(owner);
        return c;
    }

    /** A creature hired through the Lanyard and joined to the clock. */
    private static <T extends Mob> T hire(GameTestHelper h, ServerPlayer p, TimeClockBlockEntity clock, EntityType<T> type, BlockPos at) {
        T mob = h.spawn(type, at);
        if (mob instanceof Enemy) mob.setHealth(mob.getMaxHealth() * 0.5f);
        boolean hired = Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), mob);
        h.assertTrue(hired, "setup: could not hire a " + type + ": " + Refusals.lastDelivered(p));
        h.assertTrue(clock.join(mob), "setup: the clock had no room");
        return mob;
    }

    private static Villager villager(GameTestHelper h, ServerPlayer p, TimeClockBlockEntity clock, BlockPos at, VillagerProfession job) {
        Villager v = h.spawn(EntityType.VILLAGER, at);
        if (job != null) v.setVillagerData(v.getVillagerData().setProfession(job));
        h.assertTrue(Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), v), "setup: villager not hired: " + Refusals.lastDelivered(p));
        clock.join(v);
        return v;
    }

    private static ChestBlockEntity chest(GameTestHelper h, BlockPos at, ItemStack... items) {
        h.setBlock(at, Blocks.CHEST);
        ChestBlockEntity c = (ChestBlockEntity) h.getBlockEntity(at);
        for (int i = 0; i < items.length; i++) c.setItem(i, items[i]);
        return c;
    }

    private static Order.Stop pickUp(GameTestHelper h, BlockPos at) {
        return new Order.Stop(h.absolutePos(at), Direction.UP, false, ItemStack.EMPTY, false);
    }

    private static Order.Stop dropOff(GameTestHelper h, BlockPos at, ItemStack filter, boolean everythingElse) {
        return new Order.Stop(h.absolutePos(at), Direction.UP, true, filter.isEmpty() ? ItemStack.EMPTY : filter.copyWithCount(1), everythingElse);
    }

    private static Order.Area area(GameTestHelper h, BlockPos a, BlockPos b) {
        return new Order.Area(h.absolutePos(a), h.absolutePos(b));
    }

    /** A printed card for the clock's site. */
    private static ItemStack card(TimeClockBlockEntity clock, String job, List<Order.Stop> stops, List<Order.Area> areas) {
        ItemStack card = new ItemStack(WorkRegistry.JOB_CARD.get());
        Order o = Order.EMPTY.withClock(clock.site()).withJob(job).withStops(stops).withAreas(areas);
        card.set(WorkRegistry.JOB_CARD_DATA.get(), new Order.Card(o, "Boss", 0));
        return card;
    }

    /** Gives the card by hand (the real path) and puts the worker on the job, on the Redstone shift. */
    private static WorkerData work(GameTestHelper h, ServerPlayer p, Mob mob, ItemStack card) {
        boolean given = JobCardItem.give(p, card, mob);
        h.assertTrue(given, "setup: the card was refused: " + Refusals.lastDelivered(p));
        WorkerData d = Workforce.data(mob);
        d.shift = WorkerData.Shift.REDSTONE;
        d.state = WorkerData.State.ON_JOB;
        return d;
    }

    private static int count(net.minecraft.world.Container c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    private static int carried(WorkerData d, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : d.carried) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static int tasks(Mob mob) {
        StaffFile f = Staff.of(mob);
        return f == null ? 0 : f.tasks;
    }

    private static boolean mark(GameTestHelper h, ServerPlayer p, ItemStack board, BlockPos rel, boolean sneak) {
        BlockPos pos = h.absolutePos(rel);
        p.setShiftKeyDown(sneak);
        try {
            return ClipboardItem.mark(p, h.getLevel(), board, pos, Direction.UP, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        } finally {
            p.setShiftKeyDown(false);
        }
    }

    // --- the Clipboard ------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32")
    public static void theClipboardBindsOnlyForTheTrusted(GameTestHelper h) {
        ServerPlayer owner = boss(h), stranger = TestPlayers.at(h, "Stranger", new BlockPos(2, 6, 2));
        TimeClockBlockEntity clock = clock(h, owner, CLOCK);
        ItemStack mine = new ItemStack(WorkRegistry.CLIPBOARD.get()), theirs = new ItemStack(WorkRegistry.CLIPBOARD.get());
        h.assertTrue(mark(h, owner, mine, CLOCK, false), "the owner could not bind");
        h.assertTrue(Order.of(mine).clock().orElse(null) != null && Order.of(mine).clock().get().equals(clock.site()), "the clipboard was not bound");
        h.assertFalse(mark(h, stranger, theirs, CLOCK, false), "a stranger bound someone else's clock");
        h.assertTrue("clock.untrusted".equals(Refusals.lastDelivered(stranger)), "wrong refusal: " + Refusals.lastDelivered(stranger));
        h.assertTrue(Order.of(theirs).clock().isEmpty(), "the stranger's clipboard was bound anyway");
        done(h, owner, stranger);
    }

    @GameTest(template = "platform32")
    public static void theClipboardMarksStopsAndAreas(GameTestHelper h) {
        ServerPlayer p = boss(h);
        ItemStack board = new ItemStack(WorkRegistry.CLIPBOARD.get());
        chest(h, new BlockPos(12, 6, 16));
        chest(h, new BlockPos(20, 6, 16));
        // unbound first
        h.assertFalse(mark(h, p, board, new BlockPos(12, 6, 16), false), "an unbound clipboard marked a stop");
        h.assertTrue("clipboard.unbound".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        clock(h, p, CLOCK);
        mark(h, p, board, CLOCK, false);
        h.assertTrue(mark(h, p, board, new BlockPos(12, 6, 16), false), "no Pick-Up");
        h.assertTrue(mark(h, p, board, new BlockPos(20, 6, 16), true), "no Drop-Off");
        Order o = Order.of(board);
        h.assertTrue(o.pickUps().size() == 1 && o.dropOffs().size() == 1, "stops: " + o.stops());
        // a field: two corners
        h.assertTrue(mark(h, p, board, new BlockPos(10, 5, 10), false), "no first corner");
        h.assertTrue(Order.of(board).corner().isPresent(), "the first corner was not kept");
        h.assertTrue(mark(h, p, board, new BlockPos(14, 5, 13), false), "no second corner");
        o = Order.of(board);
        h.assertTrue(o.areas().size() == 1 && o.areas().get(0).sizeX() == 5 && o.areas().get(0).sizeZ() == 4 && o.corner().isEmpty(), "area: " + o.areas());
        // too big
        mark(h, p, board, new BlockPos(0, 5, 8), false);
        h.assertFalse(mark(h, p, board, new BlockPos(17, 5, 10), false), "an area of 18 was marked");
        h.assertTrue("clipboard.too_big".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        mark(h, p, board, new BlockPos(0, 5, 8), false);
        h.assertTrue(Order.of(board).corner().isEmpty(), "the corner was not cleared by using it again");
        // a marked stop again: unmarked
        h.assertTrue(mark(h, p, board, new BlockPos(12, 6, 16), false), "unmarking failed");
        h.assertTrue(Order.of(board).pickUps().isEmpty(), "the Pick-Up is still marked");
        // outside the box: 17 out from the clock
        BlockPos far = CLOCK.offset(17, 0, 0);
        h.setBlock(far, Blocks.CHEST);
        h.assertFalse(mark(h, p, board, far, false), "a stop outside the site was marked");
        h.assertTrue("clipboard.outside".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        done(h, p);
    }

    /** A claim mod that refuses opening one chest. */
    public static final class Claim {
        BlockPos claimed;

        @SubscribeEvent
        public void open(PlayerInteractEvent.RightClickBlock e) {
            if (e.getPos().equals(claimed)) e.setCanceled(true);
        }
    }

    @GameTest(template = "platform32")
    public static void aClaimedChestCannotBeOrderedAbout(GameTestHelper h) {
        ServerPlayer p = boss(h);
        clock(h, p, CLOCK);
        ItemStack board = new ItemStack(WorkRegistry.CLIPBOARD.get());
        mark(h, p, board, CLOCK, false);
        chest(h, new BlockPos(12, 6, 16));
        Claim claim = new Claim();
        claim.claimed = h.absolutePos(new BlockPos(12, 6, 16));
        NeoForge.EVENT_BUS.register(claim);
        try {
            h.assertFalse(mark(h, p, board, new BlockPos(12, 6, 16), false), "a claimed chest was marked");
        } finally {
            NeoForge.EVENT_BUS.unregister(claim);
        }
        h.assertTrue("clipboard.not_yours".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        h.assertTrue(Order.of(board).stops().isEmpty(), "the stop was kept");
        done(h, p);
    }

    @GameTest(template = "platform32")
    public static void printingNeedsPaperAndWhatTheJobNeeds(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        ItemStack board = new ItemStack(WorkRegistry.CLIPBOARD.get());
        h.assertFalse(ClipboardItem.print(p, board), "an unbound order printed");
        h.assertTrue("clipboard.unbound".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        mark(h, p, board, CLOCK, false);
        h.assertFalse(ClipboardItem.print(p, board), "an empty order printed");
        h.assertTrue("clipboard.empty".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        chest(h, new BlockPos(12, 6, 16));
        mark(h, p, board, new BlockPos(12, 6, 16), false);
        h.assertFalse(ClipboardItem.print(p, board), "a haul with no Drop-Off printed");
        h.assertTrue("clipboard.needs_both".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        ClipboardItem.edit(board, o -> o.withJob(WorkTable.FARMING));
        h.assertFalse(ClipboardItem.print(p, board), "a farm with no field printed");
        h.assertTrue("clipboard.no_field".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        mark(h, p, board, new BlockPos(8, 5, 8), false);
        mark(h, p, board, new BlockPos(11, 5, 11), false);
        h.assertFalse(ClipboardItem.print(p, board), "a field with no Drop-Off printed");
        h.assertTrue("clipboard.field_drop_off".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        chest(h, new BlockPos(20, 6, 16));
        mark(h, p, board, new BlockPos(20, 6, 16), true);
        h.assertFalse(ClipboardItem.print(p, board), "a card printed with no paper");
        h.assertTrue("clipboard.no_paper".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        p.getInventory().add(new ItemStack(Items.PAPER, 2));
        ClipboardItem.edit(board, o -> o.withName("North field"));
        h.assertTrue(ClipboardItem.print(p, board), "a good order did not print: " + Refusals.lastDelivered(p));
        int paper = 0, cards = 0;
        ItemStack card = ItemStack.EMPTY;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.PAPER)) paper += s.getCount();
            if (s.is(WorkRegistry.JOB_CARD.get())) {
                cards++;
                card = s;
            }
        }
        h.assertTrue(paper == 1 && cards == 1, "paper " + paper + ", cards " + cards);
        Order.Card c = Order.card(card);
        h.assertTrue(c != null && c.order().job().equals(WorkTable.FARMING) && c.order().name().equals("North field")
                && c.order().clock().get().equals(clock.site()) && c.order().areas().size() == 1, "the card: " + c);
        // Security may patrol the whole site with nothing marked
        ItemStack guard = new ItemStack(WorkRegistry.CLIPBOARD.get());
        mark(h, p, guard, CLOCK, false);
        ClipboardItem.edit(guard, o -> o.withJob(WorkTable.SECURITY));
        h.assertTrue(ClipboardItem.print(p, guard), "a whole-site patrol did not print: " + Refusals.lastDelivered(p));
        done(h, p);
    }

    @GameTest(template = "platform32")
    public static void theOrderScreenSetsFiltersAndEverythingElse(GameTestHelper h) {
        ServerPlayer p = boss(h);
        clock(h, p, CLOCK);
        ItemStack board = new ItemStack(WorkRegistry.CLIPBOARD.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, board);
        mark(h, p, board, CLOCK, false);
        chest(h, new BlockPos(12, 6, 16));
        chest(h, new BlockPos(20, 6, 16));
        chest(h, new BlockPos(20, 6, 18));
        mark(h, p, board, new BlockPos(12, 6, 16), false);
        mark(h, p, board, new BlockPos(20, 6, 16), true);
        mark(h, p, board, new BlockPos(20, 6, 18), true);
        ClipboardMenu menu = new ClipboardMenu(1, p.getInventory(), InteractionHand.MAIN_HAND);
        h.assertTrue(menu.site() == ClipboardMenu.BOUND, "site state " + menu.site());
        h.assertFalse(menu.setFilter(0, new ItemStack(Items.COBBLESTONE)), "a Pick-Up took a filter");
        h.assertTrue(menu.setFilter(1, new ItemStack(Items.COBBLESTONE, 5)), "no filter set");
        h.assertTrue(menu.clickMenuButton(p, ClipboardMenu.EVERYTHING_ELSE + 1), "no Everything else");
        h.assertTrue(menu.clickMenuButton(p, ClipboardMenu.EVERYTHING_ELSE + 2), "no Everything else on the second");
        h.assertTrue(menu.clickMenuButton(p, ClipboardMenu.JOB + 1), "no job set");
        p.containerMenu = menu;
        ClipboardMenu.name(p, "Sorting\u00a7k room");
        p.containerMenu = p.inventoryMenu;
        Order o = Order.of(p.getMainHandItem());
        h.assertTrue(o.stops().get(1).filter().is(Items.COBBLESTONE) && o.stops().get(1).filter().getCount() == 1, "filter " + o.stops().get(1).filter());
        h.assertTrue(!o.stops().get(1).everythingElse() && o.stops().get(2).everythingElse(), "one Everything else an order: " + o.stops());
        h.assertTrue(o.job().equals(WorkTable.SORTING), "job " + o.job());
        h.assertTrue(o.name().equals("Sortingk room"), "name " + o.name());
        h.assertTrue(menu.clickMenuButton(p, ClipboardMenu.UNMARK_STOP), "no unmark");
        h.assertTrue(Order.of(p.getMainHandItem()).stops().size() == 2, "the stop was not unmarked");
        // the clock goes: the board says so
        h.setBlock(CLOCK, Blocks.AIR);
        h.assertTrue(ClipboardItem.siteState(p, Order.of(p.getMainHandItem())) == ClipboardMenu.CLOSED, "the closed site is not shown");
        h.assertFalse(mark(h, p, p.getMainHandItem(), new BlockPos(12, 6, 16), false), "a closed site's clipboard marked a stop");
        h.assertTrue("clipboard.closed".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        done(h, p);
    }

    @GameTest(template = "platform32")
    public static void theClipboardAssignsAndLetsGo(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        ItemStack board = new ItemStack(WorkRegistry.CLIPBOARD.get());
        Cow cow = h.spawn(EntityType.COW, new BlockPos(12, 6, 12));
        h.assertFalse(ClipboardItem.onWorker(p, board, cow), "a wild cow took an order");
        h.assertTrue("clipboard.not_staff".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        h.assertTrue(Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), cow), "setup: cow not hired");
        h.assertFalse(ClipboardItem.onWorker(p, board, cow), "an unbound clipboard assigned");
        h.assertTrue("clipboard.unbound".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        mark(h, p, board, CLOCK, false);
        h.assertTrue(ClipboardItem.onWorker(p, board, cow), "not assigned: " + Refusals.lastDelivered(p));
        WorkerData d = Workforce.data(cow);
        h.assertTrue(clock.has(cow.getUUID()) && clock.site().equals(d.clock) && d.state == WorkerData.State.TO_CLOCK, "assignment " + d.state);
        h.assertFalse(ClipboardItem.onWorker(p, board, cow), "assigned twice");
        h.assertTrue("clipboard.already_here".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        // Sneak + Use twice: Let go
        p.setShiftKeyDown(true);
        ClipboardItem.onWorker(p, board, cow);
        h.assertTrue(d.state != WorkerData.State.NOTICE, "let go on the first press");
        ClipboardItem.onWorker(p, board, cow);
        p.setShiftKeyDown(false);
        h.assertTrue(Workforce.data(cow) != null && Workforce.data(cow).state == WorkerData.State.NOTICE, "not let go on the second press");
        done(h, p);
    }

    // --- Job Cards ----------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32")
    public static void cardsAreRefusedWithTheirLines(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity here = clock(h, p, CLOCK);
        TimeClockBlockEntity there = clock(h, p, new BlockPos(28, 6, 28));
        chest(h, new BlockPos(12, 6, 16));
        chest(h, new BlockPos(20, 6, 16));
        List<Order.Stop> stops = List.of(pickUp(h, new BlockPos(12, 6, 16)), dropOff(h, new BlockPos(20, 6, 16), ItemStack.EMPTY, false));
        var zombie = hire(h, p, here, EntityType.HUSK, new BlockPos(14, 6, 14));
        Cat cat = hire(h, p, here, EntityType.CAT, new BlockPos(14, 6, 18));
        Cow cow = hire(h, p, here, EntityType.COW, new BlockPos(18, 6, 14));
        // another site's card
        h.assertFalse(JobCardItem.give(p, card(there, WorkTable.HAULING, stops, List.of()), zombie), "another site's card was taken");
        h.assertTrue("card.other_site".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        // what each cannot do
        h.assertFalse(JobCardItem.give(p, card(here, WorkTable.SORTING, stops, List.of()), zombie), "a zombie took Sorting");
        h.assertTrue("card.cannot.sorting_undead".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        h.assertFalse(JobCardItem.give(p, card(here, WorkTable.HAULING, stops, List.of()), cat), "a cat took a card");
        h.assertTrue("card.cannot.cat".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        h.assertFalse(JobCardItem.give(p, card(here, WorkTable.FARMING, stops, List.of()), cow), "a cow took Farming");
        h.assertTrue("card.cannot".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        // no hands in a pen (a fox can farm a field, but not shear)
        Sheep sheep = h.spawn(EntityType.SHEEP, new BlockPos(6, 6, 6));
        sheep.setNoAi(true);
        var fox = h.spawn(EntityType.FOX, new BlockPos(8, 6, 8));
        Order.Area pen = area(h, new BlockPos(4, 5, 4), new BlockPos(9, 5, 9));
        Object[] no = Jobs.refusal(fox, Order.card(card(here, WorkTable.FARMING, stops, List.of(pen))));
        h.assertTrue(no != null && "card.cannot.hands".equals(no[0]), "the fox's pen: " + (no == null ? null : no[0]));
        h.assertTrue(Jobs.refusal(fox, Order.card(card(here, WorkTable.FARMING, stops, List.of(area(h, new BlockPos(20, 5, 20), new BlockPos(24, 5, 24)))))) == null,
                "a fox was refused an empty field");
        // a swimmer with land stops
        var dolphin = h.spawn(EntityType.DOLPHIN, new BlockPos(24, 6, 8));
        no = Jobs.refusal(dolphin, Order.card(card(here, WorkTable.HAULING, stops, List.of())));
        h.assertTrue(no != null && "card.water".equals(no[0]), "the dolphin's land stops: " + (no == null ? null : no[0]));
        // a good card, then the site closes
        ItemStack ok = card(here, WorkTable.HAULING, stops, List.of());
        h.assertTrue(JobCardItem.give(p, ok, zombie), "a good card was refused: " + Refusals.lastDelivered(p));
        h.assertTrue(ok.isEmpty() && !Workforce.data(zombie).card.isEmpty(), "the card did not change hands");
        ItemStack late = card(there, WorkTable.HAULING, stops, List.of());
        h.setBlock(new BlockPos(28, 6, 28), Blocks.AIR);
        h.assertFalse(JobCardItem.give(p, late, zombie), "a closed site's card was taken");
        h.assertTrue("card.closed".equals(Refusals.lastDelivered(p)), "wrong refusal: " + Refusals.lastDelivered(p));
        done(h, p);
    }

    // --- Hauling ------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 800)
    public static void haulingMovesAStackChestToChest(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        ChestBlockEntity from = chest(h, new BlockPos(11, 6, 16), new ItemStack(Items.COBBLESTONE, 64));
        ChestBlockEntity to = chest(h, new BlockPos(21, 6, 16));
        Villager v = villager(h, p, clock, new BlockPos(16, 6, 13), null);
        work(h, p, v, card(clock, WorkTable.HAULING, List.of(pickUp(h, new BlockPos(11, 6, 16)), dropOff(h, new BlockPos(21, 6, 16), ItemStack.EMPTY, false)), List.of()));
        h.succeedWhen(() -> {
            h.assertTrue(count(to, Items.COBBLESTONE) == 64, "delivered " + count(to, Items.COBBLESTONE));
            h.assertTrue(count(from, Items.COBBLESTONE) == 0, "left " + count(from, Items.COBBLESTONE));
            h.assertTrue(tasks(v) >= 1, "no task on the file");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 1200)
    public static void haulingObeysFilters(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        ChestBlockEntity from = chest(h, new BlockPos(11, 6, 16), new ItemStack(Items.DIRT, 20), new ItemStack(Items.COBBLESTONE, 20));
        ChestBlockEntity stone = chest(h, new BlockPos(21, 6, 15));
        ChestBlockEntity any = chest(h, new BlockPos(21, 6, 18));
        Villager v = villager(h, p, clock, new BlockPos(16, 6, 13), null);
        work(h, p, v, card(clock, WorkTable.HAULING, List.of(pickUp(h, new BlockPos(11, 6, 16)),
                dropOff(h, new BlockPos(21, 6, 15), new ItemStack(Items.COBBLESTONE), false),
                dropOff(h, new BlockPos(21, 6, 18), ItemStack.EMPTY, false)), List.of()));
        h.succeedWhen(() -> {
            h.assertTrue(count(stone, Items.COBBLESTONE) == 20 && count(any, Items.DIRT) == 20, "stone " + count(stone, Items.COBBLESTONE) + ", any " + count(any, Items.DIRT));
            h.assertTrue(count(stone, Items.DIRT) == 0 && count(any, Items.COBBLESTONE) == 0, "a filter was ignored");
            h.assertTrue(count(from, Items.DIRT) + count(from, Items.COBBLESTONE) == 0, "items left");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 1000)
    public static void haulingWaitsAtAFullDropOff(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        ChestBlockEntity from = chest(h, new BlockPos(11, 6, 16), new ItemStack(Items.DIRT, 64));
        // room for 10 dirt only
        ItemStack[] full = new ItemStack[27];
        for (int i = 0; i < 27; i++) full[i] = new ItemStack(Items.STONE, 64);
        full[26] = new ItemStack(Items.DIRT, 54);
        ChestBlockEntity to = chest(h, new BlockPos(21, 6, 16), full);
        Villager v = villager(h, p, clock, new BlockPos(16, 6, 13), null);
        WorkerData d = work(h, p, v, card(clock, WorkTable.HAULING, List.of(pickUp(h, new BlockPos(11, 6, 16)), dropOff(h, new BlockPos(21, 6, 16), ItemStack.EMPTY, false)), List.of()));
        boolean[] filled = {false}, freed = {false};
        h.onEachTick(() -> {
            if (!filled[0] && carried(d, Items.DIRT) > 0) {
                h.assertTrue(carried(d, Items.DIRT) == 10, "took more than the room: " + carried(d, Items.DIRT));
                // someone fills the last of the room before it arrives
                to.setItem(26, new ItemStack(Items.DIRT, 64));
                filled[0] = true;
            }
            if (filled[0] && !freed[0] && "staff.headinajar.say.drop_off_full".equals(d.status) && d.heldUp) {
                h.assertTrue(carried(d, Items.DIRT) == 10, "it put the load down");
                h.assertTrue(count(from, Items.DIRT) == 54, "the pick-up lost items: " + count(from, Items.DIRT));
                to.setItem(0, ItemStack.EMPTY);
                freed[0] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(freed[0], "it never waited at the full drop-off");
            h.assertTrue(count(to, Items.DIRT) >= 74 && !d.carrying(), "the load did not go in after the room came back");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 800)
    public static void aCatOnTheChestPausesHauling(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        ChestBlockEntity from = chest(h, new BlockPos(11, 6, 16), new ItemStack(Items.DIRT, 64));
        ChestBlockEntity to = chest(h, new BlockPos(21, 6, 16));
        Cat cat = h.spawn(EntityType.CAT, new BlockPos(11, 7, 16));
        cat.setNoAi(true);
        cat.setInSittingPose(true);
        Villager v = villager(h, p, clock, new BlockPos(16, 6, 13), null);
        WorkerData d = work(h, p, v, card(clock, WorkTable.HAULING, List.of(pickUp(h, new BlockPos(11, 6, 16)), dropOff(h, new BlockPos(21, 6, 16), ItemStack.EMPTY, false)), List.of()));
        boolean[] paused = {false};
        h.onEachTick(() -> {
            if (!paused[0] && "staff.headinajar.say.cat_on_chest".equals(d.status)) {
                h.assertTrue(ChestBlock.isChestBlockedAt(h.getLevel(), h.absolutePos(new BlockPos(11, 6, 16))), "setup: the cat does not block the chest");
                h.assertTrue(count(from, Items.DIRT) == 64 && !d.carrying(), "it took from under the cat");
                paused[0] = true;
                cat.discard();
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(paused[0], "the cat never paused it");
            h.assertTrue(count(to, Items.DIRT) == 64, "not hauled after the cat left: " + count(to, Items.DIRT));
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 1000)
    public static void doubleChestsAndHoppersWork(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        BlockPos a = new BlockPos(11, 6, 16), b = new BlockPos(11, 6, 17);
        h.setBlock(a, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST).setValue(ChestBlock.TYPE, ChestType.LEFT));
        h.setBlock(b, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        ChestBlockEntity other = (ChestBlockEntity) h.getBlockEntity(b);
        other.setItem(26, new ItemStack(Items.IRON_INGOT, 30));
        h.setBlock(new BlockPos(21, 6, 16), Blocks.HOPPER);
        HopperBlockEntity hopper = (HopperBlockEntity) h.getBlockEntity(new BlockPos(21, 6, 16));
        Villager v = villager(h, p, clock, new BlockPos(16, 6, 13), null);
        // the Pick-Up marked on the half without the items
        work(h, p, v, card(clock, WorkTable.HAULING, List.of(pickUp(h, a), dropOff(h, new BlockPos(21, 6, 16), ItemStack.EMPTY, false)), List.of()));
        h.succeedWhen(() -> {
            h.assertTrue(count(hopper, Items.IRON_INGOT) == 30, "hopper has " + count(hopper, Items.IRON_INGOT));
            h.assertTrue(count(other, Items.IRON_INGOT) == 0, "the other half kept " + count(other, Items.IRON_INGOT));
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 1000)
    public static void theClickedFaceIsUsed(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        ChestBlockEntity from = chest(h, new BlockPos(11, 6, 16), new ItemStack(Items.IRON_ORE, 8), new ItemStack(Items.COAL, 8));
        h.setBlock(new BlockPos(21, 6, 16), Blocks.FURNACE);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) h.getBlockEntity(new BlockPos(21, 6, 16));
        Villager v = villager(h, p, clock, new BlockPos(16, 6, 13), null);
        // marked on its side: the fuel slot
        Order.Stop side = new Order.Stop(h.absolutePos(new BlockPos(21, 6, 16)), Direction.WEST, true, ItemStack.EMPTY, false);
        work(h, p, v, card(clock, WorkTable.HAULING, List.of(pickUp(h, new BlockPos(11, 6, 16)), side), List.of()));
        h.succeedWhen(() -> {
            h.assertTrue(furnace.getItem(1).is(Items.COAL), "no coal in the fuel slot: " + furnace.getItem(1));
            h.assertTrue(furnace.getItem(0).isEmpty(), "ore went in through the side: " + furnace.getItem(0));
            h.assertTrue(count(from, Items.IRON_ORE) == 8, "the ore was taken with nowhere to go");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 2400)
    public static void twoHaulersNeverDuplicateAnItem(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        ItemStack[] five = new ItemStack[5];
        for (int i = 0; i < 5; i++) five[i] = new ItemStack(Items.COBBLESTONE, 64);
        ChestBlockEntity from = chest(h, new BlockPos(11, 6, 16), five);
        ChestBlockEntity to = chest(h, new BlockPos(21, 6, 16));
        List<Order.Stop> stops = List.of(pickUp(h, new BlockPos(11, 6, 16)), dropOff(h, new BlockPos(21, 6, 16), ItemStack.EMPTY, false));
        Villager a = villager(h, p, clock, new BlockPos(16, 6, 13), null);
        Villager b = villager(h, p, clock, new BlockPos(16, 6, 19), null);
        WorkerData da = work(h, p, a, card(clock, WorkTable.HAULING, stops, List.of()));
        WorkerData db = work(h, p, b, card(clock, WorkTable.HAULING, stops, List.of()));
        h.onEachTick(() -> {
            int total = count(from, Items.COBBLESTONE) + count(to, Items.COBBLESTONE) + carried(da, Items.COBBLESTONE) + carried(db, Items.COBBLESTONE);
            if (total != 320) h.fail("cobblestone in the world: " + total + " (320 at the start)");
        });
        h.succeedWhen(() -> {
            h.assertTrue(count(to, Items.COBBLESTONE) == 320, "delivered " + count(to, Items.COBBLESTONE));
            h.assertTrue(tasks(a) >= 1 && tasks(b) >= 1, "both should have hauled: " + tasks(a) + ", " + tasks(b));
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    // --- Sorting ------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 1600)
    public static void sortingFiltersAndEverythingElse(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        ChestBlockEntity from = chest(h, new BlockPos(11, 6, 16), new ItemStack(Items.SAND, 10), new ItemStack(Items.COBBLESTONE, 10), new ItemStack(Items.DIRT, 10));
        ChestBlockEntity stone = chest(h, new BlockPos(21, 6, 14));
        ChestBlockEntity open = chest(h, new BlockPos(21, 6, 16));
        ChestBlockEntity rest = chest(h, new BlockPos(21, 6, 18));
        Villager v = villager(h, p, clock, new BlockPos(16, 6, 13), VillagerProfession.LIBRARIAN);
        work(h, p, v, card(clock, WorkTable.SORTING, List.of(pickUp(h, new BlockPos(11, 6, 16)),
                dropOff(h, new BlockPos(21, 6, 14), new ItemStack(Items.COBBLESTONE), false),
                dropOff(h, new BlockPos(21, 6, 16), new ItemStack(Items.SAND), false),
                dropOff(h, new BlockPos(21, 6, 18), ItemStack.EMPTY, true)), List.of()));
        h.succeedWhen(() -> {
            h.assertTrue(count(stone, Items.COBBLESTONE) == 10, "cobblestone " + count(stone, Items.COBBLESTONE));
            h.assertTrue(count(open, Items.SAND) == 10, "sand " + count(open, Items.SAND));
            h.assertTrue(count(rest, Items.DIRT) == 10, "everything else " + count(rest, Items.DIRT));
            h.assertTrue(count(from, Items.DIRT) + count(from, Items.SAND) + count(from, Items.COBBLESTONE) == 0, "left in the pick-up");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32")
    public static void disengagedMisfilesOneInTwenty(GameTestHelper h) {
        // the odds themselves, on fixed seeds: 400 items, 10 to 30 astray
        for (long seed = 1; seed <= 8; seed++) {
            int k = Sorting.misfiles(RandomSource.create(seed), 400);
            h.assertTrue(k >= 10 && k <= 30, "seed " + seed + ": " + k + " of 400 misfiled");
        }
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        Villager v = villager(h, p, clock, new BlockPos(16, 6, 13), null);
        StaffFile f = Staff.of(v);
        f.morale = 60;
        int content = 0;
        for (int i = 0; i < 400; i++) content += Sorting.misfile(v, new ItemStack(Items.DIRT)).getCount();
        h.assertTrue(content == 0, "a content worker misfiled " + content);
        f.morale = 20;
        f.lowDawns = 0;
        int disengaged = 0;
        for (int i = 0; i < 400; i++) disengaged += Sorting.misfile(v, new ItemStack(Items.DIRT)).getCount();
        h.assertTrue(disengaged > 0 && disengaged < 60, "a disengaged worker misfiled " + disengaged + " of 400");
        done(h, p);
    }

    @GameTest(template = "platform32")
    public static void aLibrarianNeverMisfiles(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        Villager v = villager(h, p, clock, new BlockPos(16, 6, 13), VillagerProfession.LIBRARIAN);
        Staff.of(v).morale = 20;
        int n = 0;
        for (int i = 0; i < 400; i++) n += Sorting.misfile(v, new ItemStack(Items.DIRT)).getCount();
        h.assertTrue(n == 0, "a librarian misfiled " + n);
        done(h, p);
    }

    // --- Farming ------------------------------------------------------------------------------------------------------

    /** A 3 by 3 field of wheat on wet farmland at x 8-10, z 8-10, every other one ripe. */
    private static void field(GameTestHelper h) {
        for (int x = 8; x <= 10; x++) {
            for (int z = 8; z <= 10; z++) {
                h.setBlock(new BlockPos(x, 5, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                boolean ripe = (x + z) % 2 == 0;
                h.setBlock(new BlockPos(x, 6, z), ((CropBlock) Blocks.WHEAT).getStateForAge(ripe ? 7 : 3));
            }
        }
    }

    @GameTest(template = "platform32", timeoutTicks = 1200)
    public static void farmingHarvestsRipeOnlyAndReplants(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        field(h);
        ChestBlockEntity to = chest(h, new BlockPos(12, 6, 12));
        Villager v = villager(h, p, clock, new BlockPos(13, 6, 9), VillagerProfession.FARMER);
        work(h, p, v, card(clock, WorkTable.FARMING, List.of(dropOff(h, new BlockPos(12, 6, 12), ItemStack.EMPTY, false)),
                List.of(area(h, new BlockPos(8, 5, 8), new BlockPos(10, 5, 10)))));
        h.succeedWhen(() -> {
            for (int x = 8; x <= 10; x++) {
                for (int z = 8; z <= 10; z++) {
                    var s = h.getBlockState(new BlockPos(x, 6, z));
                    h.assertTrue(s.is(Blocks.WHEAT), "no wheat at " + x + "," + z);
                    int age = s.getValue(CropBlock.AGE);
                    boolean ripe = (x + z) % 2 == 0;
                    // a young crop may grow by itself meanwhile, but is never harvested before it is ripe
                    h.assertTrue(ripe ? age < 7 : age >= 3, "at " + x + "," + z + " age " + age);
                    h.assertTrue(h.getBlockState(new BlockPos(x, 5, z)).is(Blocks.FARMLAND), "farmland trampled at " + x + "," + z);
                }
            }
            h.assertTrue(count(to, Items.WHEAT) == 5, "wheat delivered: " + count(to, Items.WHEAT));
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void farmingNeverTramples(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        h.setBlock(new BlockPos(8, 5, 8), Blocks.FARMLAND);
        Villager v = villager(h, p, clock, new BlockPos(8, 10, 8), null);
        v.fallDistance = 4;
        h.runAfterDelay(60, () -> {
            h.assertTrue(v.onGround(), "setup: the worker never landed");
            h.assertTrue(h.getBlockState(new BlockPos(8, 5, 8)).is(Blocks.FARMLAND), "a worker trampled the farmland");
            done(h, p);
        });
    }

    /** Four sheep standing still in a pen at x 4-9, z 20-25, shears in the Pick-Up, a Drop-Off for the wool. */
    private static WorkerData shearing(GameTestHelper h, ServerPlayer p, TimeClockBlockEntity clock, Villager v) {
        for (int i = 0; i < 4; i++) {
            Sheep s = h.spawn(EntityType.SHEEP, new BlockPos(5 + i, 6, 22));
            s.setNoAi(true);
        }
        chest(h, new BlockPos(11, 6, 20), new ItemStack(Items.SHEARS));
        chest(h, new BlockPos(11, 6, 24));
        // paid ahead: a dawn that falls during the test (the world's hour is whatever the run has reached) finds it paid,
        // so it never leaves the sheep to ask about its wage
        com.coolfreeze23.headinajar.staff.StaffFile f = com.coolfreeze23.headinajar.staff.Staff.of(v);
        f.paidThrough = com.coolfreeze23.headinajar.staff.Staff.day(h.getLevel()) + 10;
        v.setData(com.coolfreeze23.headinajar.registry.ModRegistry.STAFF, f);
        return work(h, p, v, card(clock, WorkTable.FARMING, List.of(pickUp(h, new BlockPos(11, 6, 20)), dropOff(h, new BlockPos(11, 6, 24), ItemStack.EMPTY, false)),
                List.of(area(h, new BlockPos(4, 5, 20), new BlockPos(9, 5, 25)))));
    }

    @GameTest(template = "platform32", timeoutTicks = 1600)
    public static void shearingWearsShearsAndToolsmithsHalf(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        // a villager works the day shift: the clock reads mid-morning, so the test never depends on the world's hour
        clock.timeOverride = 3000;
        Villager v = villager(h, p, clock, new BlockPos(12, 6, 22), null);
        WorkerData d = shearing(h, p, clock, v);
        h.succeedWhen(() -> {
            var sheep = h.getLevel().getEntitiesOfClass(Sheep.class, new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(3, 5, 19))).inflate(8));
            int shorn = 0;
            for (Sheep s : sheep) if (s.isSheared()) shorn++;
            h.assertTrue(shorn == 4, "shorn " + shorn);
            h.assertTrue(d.tool.is(Items.SHEARS) && d.tool.getDamageValue() == 4, "shears worn " + d.tool.getDamageValue());
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 1600)
    public static void aToolsmithWearsShearsHalfAsFast(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        // a villager works the day shift: the clock reads mid-morning, so the test never depends on the world's hour
        clock.timeOverride = 3000;
        Villager v = villager(h, p, clock, new BlockPos(12, 6, 22), VillagerProfession.TOOLSMITH);
        WorkerData d = shearing(h, p, clock, v);
        h.succeedWhen(() -> {
            var sheep = h.getLevel().getEntitiesOfClass(Sheep.class, new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(3, 5, 19))).inflate(8));
            int shorn = 0;
            for (Sheep s : sheep) if (s.isSheared()) shorn++;
            h.assertTrue(shorn == 4, "shorn " + shorn + " of " + sheep.size() + "; " + d.state + " " + d.status + ", tool " + d.tool + " worn " + d.tool.getDamageValue() + ", at " + v.blockPosition() + ", carrying " + d.carried);
            h.assertTrue(d.tool.is(Items.SHEARS) && d.tool.getDamageValue() == 2, "a toolsmith's shears worn " + d.tool.getDamageValue());
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 1600)
    public static void milkingTakesBuckets(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        for (int i = 0; i < 2; i++) {
            Cow c = h.spawn(EntityType.COW, new BlockPos(5 + 2 * i, 6, 22));
            c.setNoAi(true);
        }
        ChestBlockEntity from = chest(h, new BlockPos(11, 6, 20), new ItemStack(Items.BUCKET, 2));
        ChestBlockEntity to = chest(h, new BlockPos(11, 6, 24));
        Villager v = villager(h, p, clock, new BlockPos(12, 6, 22), null);
        work(h, p, v, card(clock, WorkTable.FARMING, List.of(pickUp(h, new BlockPos(11, 6, 20)), dropOff(h, new BlockPos(11, 6, 24), ItemStack.EMPTY, false)),
                List.of(area(h, new BlockPos(4, 5, 20), new BlockPos(9, 5, 25)))));
        h.succeedWhen(() -> {
            h.assertTrue(count(to, Items.MILK_BUCKET) == 2, "milk " + count(to, Items.MILK_BUCKET));
            h.assertTrue(count(from, Items.BUCKET) == 0, "buckets left in the pick-up: " + count(from, Items.BUCKET));
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32")
    public static void aBeeGrowsItsField(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        h.setBlock(new BlockPos(8, 5, 8), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(8, 6, 8), ((CropBlock) Blocks.WHEAT).getStateForAge(2));
        Order.Area field = area(h, new BlockPos(6, 5, 6), new BlockPos(10, 5, 10));
        h.assertTrue(Farming.grow(h.getLevel(), field, RandomSource.create(1), clock.getBlockPos()), "nothing grew");
        h.assertTrue(h.getBlockState(new BlockPos(8, 6, 8)).getValue(CropBlock.AGE) == 3, "the crop did not grow a stage");
        h.setBlock(new BlockPos(8, 6, 8), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        h.assertFalse(Farming.grow(h.getLevel(), field, RandomSource.create(1), clock.getBlockPos()), "a ripe crop grew");
        done(h, p);
    }

    // --- Security -----------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 800, batch = SECURITY)
    public static void securityKillsAZombieInTheBox(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        var guard = hire(h, p, clock, EntityType.VINDICATOR, new BlockPos(16, 6, 12));
        guard.setHealth(guard.getMaxHealth());
        var z = h.spawn(EntityType.HUSK, new BlockPos(20, 6, 19));
        z.setNoAi(true);
        work(h, p, guard, card(clock, WorkTable.SECURITY, List.of(), List.of()));
        int before = tasks(guard);
        h.succeedWhen(() -> {
            h.assertTrue(!z.isAlive(), "the zombie is still alive at " + z.getHealth());
            h.assertTrue(tasks(guard) > before, "seeing it off was not a task");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 800, batch = SECURITY)
    public static void securityNeverLeavesTheBox(GameTestHelper h) {
        ServerPlayer p = boss(h);
        // the clock near one edge: its box ends at x 20 (16 out)
        BlockPos at = new BlockPos(4, 6, 16);
        TimeClockBlockEntity clock = clock(h, p, at);
        var guard = hire(h, p, clock, EntityType.VINDICATOR, new BlockPos(17, 6, 16));
        var z = h.spawn(EntityType.HUSK, new BlockPos(25, 6, 16));
        z.setNoAi(true);
        WorkerData d = work(h, p, guard, card(clock, WorkTable.SECURITY, List.of(), List.of()));
        double edge = h.absolutePos(at).getX() + Workforce.radius() + 1;
        h.onEachTick(() -> {
            if (guard.getX() > edge + 0.3) h.fail("the guard left the box: x " + (guard.getX() - edge) + " past the edge");
        });
        // stage 1: 300 ticks with the husk outside; 2: the husk set down beside the guard, inside the box, until struck;
        // 3: the husk outside again: the guard lets it go with the line
        int[] stage = {1};
        long[] since = {0};
        // the line is the status for as long as nothing else is said: remember it was said once the husk is out
        boolean[] letGo = {false};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (stage[0] == 3 && "staff.headinajar.say.not_our_problem".equals(d.status)) letGo[0] = true;
            if (stage[0] == 1 && t >= 300) {
                h.assertTrue(z.getHealth() == z.getMaxHealth(), "it fought a husk outside the box");
                double x = Math.min(guard.getX() + 1.5, edge - 2);
                z.teleportTo(x, guard.getY(), guard.getZ());
                stage[0] = 2;
                since[0] = t;
            } else if (stage[0] == 2 && (z.getHealth() < z.getMaxHealth() || t - since[0] > 200)) {
                h.assertTrue(z.getHealth() < z.getMaxHealth(), "the guard never took on the husk that came in: " + d.status);
                h.assertTrue(z.isAlive(), "setup: the husk died before it could leave");
                Vec3 out = h.absoluteVec(new Vec3(27.5, 6, 16.5));
                z.teleportTo(out.x, out.y, out.z);
                stage[0] = 3;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(stage[0] == 3 && letGo[0], "not let go with the line yet: " + d.status);
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 500, batch = SECURITY)
    public static void securitySparesPlayersPetsVillagersAndStaff(GameTestHelper h) {
        ServerPlayer p = boss(h), other = TestPlayers.at(h, "Visitor", new BlockPos(18, 6, 16));
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        var guard = hire(h, p, clock, EntityType.VINDICATOR, new BlockPos(16, 6, 13));
        Villager villager = h.spawn(EntityType.VILLAGER, new BlockPos(14, 6, 16));
        villager.setNoAi(true);
        Wolf wolf = h.spawn(EntityType.WOLF, new BlockPos(16, 6, 19));
        wolf.tame(p);
        wolf.setNoAi(true);
        var colleague = hire(h, p, clock, EntityType.HUSK, new BlockPos(19, 6, 13));
        float colleagueHealth = colleague.getHealth();
        work(h, p, guard, card(clock, WorkTable.SECURITY, List.of(), List.of()));
        h.runAfterDelay(400, () -> {
            h.assertTrue(other.getHealth() == other.getMaxHealth(), "the guard hit a player");
            h.assertTrue(villager.getHealth() == villager.getMaxHealth(), "the guard hit a villager");
            h.assertTrue(wolf.getHealth() == wolf.getMaxHealth(), "the guard hit a pet");
            h.assertTrue(colleague.getHealth() == colleagueHealth, "the guard hit a colleague");
            done(h, p, other);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 600, batch = SECURITY)
    public static void securityArrowsComeFromThePickUp(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        ChestBlockEntity from = chest(h, new BlockPos(11, 6, 16), new ItemStack(Items.ARROW, 32));
        var guard = hire(h, p, clock, EntityType.PILLAGER, new BlockPos(16, 6, 13));
        WorkerData d = work(h, p, guard, card(clock, WorkTable.SECURITY, List.of(pickUp(h, new BlockPos(11, 6, 16))), List.of()));
        h.succeedWhen(() -> {
            h.assertTrue(carried(d, Items.ARROW) == 32 && count(from, Items.ARROW) == 0, "arrows carried " + carried(d, Items.ARROW));
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 1200, batch = SECURITY)
    public static void securityPatrolsAndCountsItsHours(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        var guard = hire(h, p, clock, EntityType.VINDICATOR, new BlockPos(16, 6, 13));
        work(h, p, guard, card(clock, WorkTable.SECURITY, List.of(), List.of(area(h, new BlockPos(10, 5, 10), new BlockPos(22, 5, 22)))));
        Vec3 start = guard.position();
        double[] far = {0};
        h.onEachTick(() -> far[0] = Math.max(far[0], guard.position().distanceTo(start)));
        h.succeedWhen(() -> {
            h.assertTrue(far[0] > 3, "it never patrolled: " + far[0]);
            h.assertTrue(tasks(guard) >= 1, "a thousand ticks of patrol is not a task yet");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    // --- carrying the vanilla way ---------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 1200)
    public static void aFoxCarriesInItsMouthAndNeverTwice(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        ChestBlockEntity from = chest(h, new BlockPos(12, 6, 16), new ItemStack(Items.COBBLESTONE, 3));
        ChestBlockEntity to = chest(h, new BlockPos(20, 6, 16));
        var fox = hire(h, p, clock, EntityType.FOX, new BlockPos(16, 6, 13));
        WorkerData d = work(h, p, fox, card(clock, WorkTable.HAULING, List.of(pickUp(h, new BlockPos(12, 6, 16)), dropOff(h, new BlockPos(20, 6, 16), ItemStack.EMPTY, false)), List.of()));
        boolean[] seen = {false};
        h.onEachTick(() -> {
            int total = count(from, Items.COBBLESTONE) + count(to, Items.COBBLESTONE) + carried(d, Items.COBBLESTONE);
            if (total != 3) h.fail("cobblestone in the world: " + total + " (3 at the start)");
            ItemStack mouth = fox.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            if (d.carrying() && mouth.is(Items.COBBLESTONE)) seen[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(seen[0], "the fox never carried in its mouth");
            h.assertTrue(count(to, Items.COBBLESTONE) == 3, "delivered " + count(to, Items.COBBLESTONE));
            h.assertTrue(fox.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty(), "the fox's mouth was not emptied");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform32")
    public static void letGoTakesTheCarryOutOfTheMouthOnce(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p, CLOCK);
        var fox = hire(h, p, clock, EntityType.FOX, new BlockPos(16, 6, 13));
        WorkerData d = Workforce.data(fox);
        d.carried.add(new ItemStack(Items.COBBLESTONE, 1));
        Jobs.showCarry(fox, d);
        h.assertTrue(fox.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).is(Items.COBBLESTONE), "setup: the carry is not in its mouth");
        Workforce.letGo(p, fox);
        h.assertTrue(fox.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty(), "the fox kept a copy in its mouth");
        int dropped = 0;
        for (var e : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, fox.getBoundingBox().inflate(4))) {
            if (e.getItem().is(Items.COBBLESTONE)) dropped += e.getItem().getCount();
        }
        h.assertTrue(dropped == 1, "set down " + dropped + " cobblestone (1 carried)");
        // an enderman carries a barrel while it carries anything
        var enderman = hire(h, p, clock, EntityType.ENDERMAN, new BlockPos(20, 6, 13));
        WorkerData e = Workforce.data(enderman);
        e.carried.add(new ItemStack(Items.DIRT, 4));
        Jobs.showCarry(enderman, e);
        h.assertTrue(enderman.getCarriedBlock() != null && enderman.getCarriedBlock().is(Blocks.BARREL), "no barrel: " + enderman.getCarriedBlock());
        e.carried.clear();
        Jobs.showCarry(enderman, e);
        h.assertTrue(enderman.getCarriedBlock() == null, "the barrel stayed: " + enderman.getCarriedBlock());
        done(h, p);
    }
}
