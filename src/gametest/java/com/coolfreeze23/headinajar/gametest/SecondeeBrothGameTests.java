package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlock;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.menu.TimeClockMenu;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.SecondRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.second.Broth;
import com.coolfreeze23.headinajar.staff.second.BrothTank;
import com.coolfreeze23.headinajar.staff.second.SecondeeData;
import com.coolfreeze23.headinajar.staff.second.Secondees;
import com.coolfreeze23.headinajar.staff.second.Secondment;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.Shifts;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 0.7.1, the collar's canister and the Time Clock's Broth tank: 500 mB last 60,000 ticks (plan 5.1 #9), punch-in tops
 * up from the tank, a dry secondee stands by and then walks back to its clock and tops up there, the 4,000 mB tank
 * tops eight secondees once each (and pipes fill it but never drain it), a bucket of broth pours in, a full tank and a
 * bucket of anything else refuse, the tank is saved, and the Broth tab's numbers (the menu's data).
 * <p>
 * Secondees are made the real way: the saw, the collar and a jarred head through {@code Secondment.instrument} from a
 * mock player's hand. A sheep head on a cow body: both are paid in wheat and work by day. {@code platform32}: the floor's
 * top at helper y 5; the clock stands at y 6 and reads its own test time.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class SecondeeBrothGameTests {
    private static final BlockPos CLOCK = new BlockPos(16, 6, 16);

    private static ServerPlayer boss(GameTestHelper h) {
        return TestPlayers.at(h, "broth", new BlockPos(2, 6, 2));
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static TimeClockBlockEntity clock(GameTestHelper h, ServerPlayer owner, long time) {
        h.setBlock(CLOCK, WorkRegistry.TIME_CLOCK.get().defaultBlockState().setValue(TimeClockBlock.FACING, Direction.SOUTH));
        TimeClockBlockEntity c = (TimeClockBlockEntity) h.getBlockEntity(CLOCK);
        c.timeOverride = time;
        c.setOwner(owner);
        c.registerSite();
        return c;
    }

    private static ItemStack card(TimeClockBlockEntity c) {
        ItemStack s = new ItemStack(WorkRegistry.JOB_CARD.get());
        s.set(WorkRegistry.JOB_CARD_DATA.get(), new Order.Card(new Order(Optional.of(c.site()), "hauling", List.of(), List.of(), "broth run"), "test", 0));
        return s;
    }

    private static InteractionResult use(ServerPlayer p, ItemStack stack, Mob host) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return Secondment.instrument(p, p, p.getMainHandItem(), host, InteractionHand.MAIN_HAND);
    }

    /** A sheep head seated on a collared cow host at {@code at}, by the saw, the collar and the head. */
    private static Mob secondee(GameTestHelper h, ServerPlayer p, BlockPos at) {
        Mob cow = h.spawn(EntityType.COW, at);
        cow.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), cow, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "the saw did not make a host: " + Refusals.lastDelivered(p));
        use(p, new ItemStack(SecondRegistry.SECONDMENT_COLLAR.get()), cow);
        h.assertTrue(Secondees.collared(cow), "the collar was not fitted: " + Refusals.lastDelivered(p));
        Mob sheep = h.spawn(EntityType.SHEEP, at.above(3));
        ItemStack head = JarredHeadItem.of(sheep);
        sheep.discard();
        use(p, head, cow);
        h.assertTrue(Secondees.seated(cow), "the head was not seated: " + Refusals.lastDelivered(p));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return cow;
    }

    private static SecondeeData data(Mob host) {
        return Secondees.data(host);
    }

    // --- the canister ---------------------------------------------------------------------------------------------

    /** Plan 5.1 #9: a full canister is 60,000 ticks of broth (exactly 499 mB in 59,880 ticks, the 500th in the next 120). */
    @GameTest(template = "platform32")
    public static void aCanisterLastsSixtyThousandTicks(GameTestHelper h) {
        ServerPlayer p = boss(h);
        Mob host = secondee(h, p, new BlockPos(6, 6, 6));
        SecondeeData d = data(host);
        h.assertTrue(d.canister == 0, "a new seat starts at " + d.canister + " mB (dry flag " + Broth.dry(d) + ")");
        d.canister = SecondeeData.CANISTER;
        long t0 = h.getLevel().getGameTime() + 7;
        int full = 499 * Broth.SIP_TICKS;
        for (int i = 0; i < full; i++) Broth.tick(host, d, t0 + i);
        h.assertTrue(d.canister == 1, "after " + full + " ticks the canister holds " + d.canister + " mB, not 1");
        h.assertTrue(!Broth.dry(d), "dry with 1 mB left");
        for (int i = full; i < 500 * Broth.SIP_TICKS; i++) Broth.tick(host, d, t0 + i);
        h.assertTrue(d.canister == 0, "after 60,000 ticks the canister holds " + d.canister + " mB");
        h.assertTrue(Broth.dry(d), "an empty canister is not marked dry");
        // the sight glass's four steps
        h.assertTrue(Broth.step(500) == 0 && Broth.step(334) == 0 && Broth.step(333) == 1 && Broth.step(167) == 1 && Broth.step(166) == 2
                && Broth.step(1) == 2 && Broth.step(0) == 3, "the sight-glass steps are wrong");
        done(h, p);
    }

    /** Plan 5.1 #9: punching in tops the canister up from the clock's Broth tank. */
    @GameTest(template = "platform32", timeoutTicks = 900)
    public static void punchInTopsUpFromTheTank(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, p, Shifts.DAY_START + 50);
        c.drawer().insertItem(0, new ItemStack(Items.WHEAT, 16), false);
        c.rack().setStackInSlot(0, card(c));
        c.broth().fill(new FluidStack(ModFluids.BROTH.source.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        Mob host = secondee(h, p, CLOCK.east(4));
        data(host).canister = 200;
        long start = h.getLevel().getGameTime();
        h.succeedWhen(() -> {
            h.assertTrue(c.punchedFor(host.getUUID(), Shifts.stamp(WorkerData.Shift.DAY, c)), "not punched in");
            int canister = data(host).canister, tank = c.broth().getFluidAmount();
            long sips = (h.getLevel().getGameTime() - start) / Broth.SIP_TICKS + 1;
            h.assertTrue(canister >= SecondeeData.CANISTER - 1, "punched in with " + canister + " mB in the canister");
            h.assertTrue(1000 - tank >= 300 && 1000 - tank <= 300 + sips, "the tank gave " + (1000 - tank) + " mB for a canister that had 200 and " + sips + " sips");
            p.getServer().getPlayerList().remove(p);
        });
    }

    /**
     * Plan 5.1 #9: a secondee on the job whose canister runs dry stops work and stands by where it is ("Out of broth:
     * standing by."), then walks back to its clock ("Out of broth: going back to the office."), tops up from the tank
     * there and goes back to work.
     */
    @GameTest(template = "platform32", timeoutTicks = 1600)
    public static void aDrySecondeeStandsByThenWalksHome(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, p, Shifts.DAY_START + 50);
        c.drawer().insertItem(0, new ItemStack(Items.WHEAT, 16), false);
        c.rack().setStackInSlot(0, card(c));
        c.broth().fill(new FluidStack(ModFluids.BROTH.source.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        Mob host = secondee(h, p, CLOCK.east(4));
        data(host).canister = SecondeeData.CANISTER;
        WorkerData w = Workforce.data(host);
        Vec3 clockAt = Vec3.atCenterOf(h.absolutePos(CLOCK));
        int[] step = {0};
        long[] at = {0};
        Vec3[] stood = {null};
        h.onEachTick(() -> {
            long now = h.getLevel().getGameTime();
            switch (step[0]) {
                case 0 -> {
                    if (w.state == WorkerData.State.ON_JOB) {
                        // on the job, eleven blocks from the clock: the broth runs out
                        Vec3 far = h.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(5, 6, 16)));
                        host.teleportTo(far.x, far.y, far.z);
                        data(host).canister = 0;
                        step[0] = 1;
                    }
                }
                case 1 -> {
                    if ("staff.headinajar.say.secondment.dry".equals(w.status)) {
                        h.assertTrue(w.state != WorkerData.State.ON_JOB, "a dry secondee is still on the job");
                        h.assertTrue(Broth.dry(data(host)), "not marked dry");
                        stood[0] = host.position();
                        at[0] = now;
                        step[0] = 2;
                    }
                }
                case 2 -> {
                    if ("staff.headinajar.say.secondment.dry_home".equals(w.status)) {
                        h.assertTrue(now - at[0] >= Broth.STAND_BY_TICKS - 15, "it stood by only " + (now - at[0]) + " ticks");
                        step[0] = 3;
                    } else {
                        h.assertTrue(host.position().distanceTo(stood[0]) < 0.5, "it walked off while standing by: " + host.position().distanceTo(stood[0]));
                    }
                }
                case 3 -> {
                    if (data(host).canister > 0) {
                        double off = host.position().subtract(clockAt).horizontalDistance();
                        h.assertTrue(off <= TimeClockBlockEntity.PUNCH_REACH + 0.2, "it topped up " + off + " from the clock");
                        h.assertTrue(data(host).canister == SecondeeData.CANISTER, "topped up to " + data(host).canister);
                        h.assertTrue(c.broth().getFluidAmount() <= 500, "the tank still holds " + c.broth().getFluidAmount());
                        step[0] = 4;
                    }
                }
                case 4 -> {
                    if (w.state == WorkerData.State.ON_JOB && !Broth.dry(data(host))) {
                        step[0] = 5;
                        done(h, p);
                    }
                }
                default -> {
                }
            }
        });
    }

    // --- the tank -------------------------------------------------------------------------------------------------

    /** The 4,000 mB tank, filled by a pipe, tops eight secondees once each at their punch-in; pipes never drain it. */
    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void theTankTopsEightSecondeesOnceEach(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, p, 23500);
        IFluidHandler pipe = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(CLOCK), Direction.UP);
        h.assertTrue(pipe != null, "the clock has no fluid handler");
        h.assertTrue(pipe.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 0,
                "a pipe filled the Broth tank with water");
        int in = pipe.fill(new FluidStack(ModFluids.BROTH.source.get(), 5000), IFluidHandler.FluidAction.EXECUTE);
        h.assertTrue(in == BrothTank.TANK && c.broth().getFluidAmount() == BrothTank.TANK, "a pipe put in " + in + " mB");
        h.assertTrue(pipe.drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty()
                        && pipe.drain(new FluidStack(ModFluids.BROTH.source.get(), 1000), IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                "a pipe drained the Broth tank");
        List<Mob> hosts = new ArrayList<>();
        for (int i = 0; i < 8; i++) hosts.add(secondee(h, p, new BlockPos(4 + i * 3, 6, 4)));
        for (Mob host : hosts) {
            c.join(host);
            Shifts.punchedIn(c, host);
        }
        for (Mob host : hosts) h.assertTrue(data(host).canister == SecondeeData.CANISTER, "a secondee topped up to " + data(host).canister);
        h.assertTrue(c.broth().getFluidAmount() == 0, "the tank holds " + c.broth().getFluidAmount() + " after eight top-ups");
        // a ninth punch finds the tank dry
        data(hosts.get(0)).canister = 0;
        Shifts.punchedIn(c, hosts.get(0));
        h.assertTrue(data(hosts.get(0)).canister == 0, "a dry tank gave " + data(hosts.get(0)).canister + " mB");
        done(h, p);
    }

    /** A Nutrient Broth Bucket used on the clock pours its 1,000 mB in and comes back an empty bucket. */
    @GameTest(template = "platform32")
    public static void aBucketOfBrothPoursIntoTheClock(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, p, -1);
        for (int i = 1; i <= 4; i++) {
            pour(h, p, new ItemStack(ModFluids.BROTH.bucket.get()));
            h.assertTrue(c.broth().getFluidAmount() == i * 1000, "after bucket " + i + " the tank holds " + c.broth().getFluidAmount());
            h.assertTrue(p.getMainHandItem().is(Items.BUCKET), "bucket " + i + " came back as " + p.getMainHandItem());
        }
        h.assertTrue(!(p.containerMenu instanceof TimeClockMenu), "the bucket opened the clock's screen");
        // the tank is saved with the clock
        var reg = h.getLevel().registryAccess();
        CompoundTag saved = c.saveWithFullMetadata(reg);
        h.setBlock(CLOCK, Blocks.AIR);
        h.setBlock(CLOCK, WorkRegistry.TIME_CLOCK.get().defaultBlockState());
        TimeClockBlockEntity fresh = (TimeClockBlockEntity) h.getBlockEntity(CLOCK);
        fresh.loadWithComponents(saved, reg);
        h.assertTrue(fresh.broth().getFluidAmount() == 4000 && BrothTank.isBroth(fresh.broth().getFluid()), "the reloaded tank holds " + fresh.broth().getFluid());
        // an old clock's record (no tank) loads empty
        saved.remove("Broth");
        fresh.loadWithComponents(saved, reg);
        h.assertTrue(fresh.broth().isEmpty(), "a clock saved without a tank loads with " + fresh.broth().getFluidAmount());
        done(h, p);
    }

    /** A full tank refuses the bucket (the line and the refusal), and a bucket of anything else is refused too. */
    @GameTest(template = "platform32")
    public static void aFullTankRefusesTheBucket(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, p, -1);
        c.broth().fill(new FluidStack(ModFluids.BROTH.source.get(), 3500), IFluidHandler.FluidAction.EXECUTE);
        pour(h, p, new ItemStack(ModFluids.BROTH.bucket.get()));
        h.assertTrue("clock.broth_full".equals(Refusals.lastDelivered(p)), "no refusal from a tank with 500 mB of room: " + Refusals.delivered(p));
        h.assertTrue(p.getMainHandItem().is(ModFluids.BROTH.bucket.get()) && c.broth().getFluidAmount() == 3500, "the refused bucket was poured anyway");
        pour(h, p, new ItemStack(Items.WATER_BUCKET));
        h.assertTrue("machine.wrong_fluid".equals(Refusals.lastDelivered(p)), "no refusal for water: " + Refusals.delivered(p));
        h.assertTrue(p.getMainHandItem().is(Items.WATER_BUCKET) && c.broth().getFluidAmount() == 3500, "the water went somewhere");
        h.assertTrue(h.getLevel().getFluidState(h.absolutePos(CLOCK.south())).isEmpty() && h.getLevel().getFluidState(h.absolutePos(CLOCK.above())).isEmpty(),
                "the water bucket was emptied beside the clock");
        // someone the owner does not trust may not pour
        ServerPlayer stranger = TestPlayers.at(h, "stranger", CLOCK.east(2));
        c.broth().drain(3500, IFluidHandler.FluidAction.EXECUTE);
        pour(h, stranger, new ItemStack(ModFluids.BROTH.bucket.get()));
        h.assertTrue("clock.untrusted".equals(Refusals.lastDelivered(stranger)) && c.broth().isEmpty(), "a stranger poured broth into the clock");
        done(h, p, stranger);
    }

    private static void pour(GameTestHelper h, ServerPlayer p, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = h.absolutePos(CLOCK);
        p.moveTo(Vec3.atBottomCenterOf(abs.south(2)));
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0, 0.5), Direction.SOUTH, abs, false));
    }

    // --- the tab --------------------------------------------------------------------------------------------------

    /** The Broth tab's numbers: the tank, each seated secondee's canister by roster place, nothing for other staff. */
    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void theBrothTabShowsTheTankAndEachCanister(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, p, 23500);
        c.broth().fill(new FluidStack(ModFluids.BROTH.source.get(), 2500), IFluidHandler.FluidAction.EXECUTE);
        Mob a = secondee(h, p, CLOCK.east(3));
        Mob cow = h.spawn(EntityType.COW, CLOCK.east(5));
        h.assertTrue(Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), cow), "the cow was not hired: " + Refusals.lastDelivered(p));
        Mob b = secondee(h, p, CLOCK.west(3));
        c.join(a);
        c.join(cow);
        c.join(b);
        data(a).canister = 320;
        data(b).canister = 0;
        TimeClockMenu menu = new TimeClockMenu(1, p.getInventory(), c);
        h.assertTrue(menu.clickMenuButton(p, TimeClockMenu.TAB + TimeClockMenu.BROTH) && menu.tab() == TimeClockMenu.BROTH, "the Broth tab did not open");
        h.assertTrue(menu.brothTank() == 2500, "the tab shows a tank of " + menu.brothTank());
        int pa = c.roster().indexOf(a.getUUID()), pc = c.roster().indexOf(cow.getUUID()), pb = c.roster().indexOf(b.getUUID());
        h.assertTrue(menu.canister(pa) == 320, "the first secondee shows " + menu.canister(pa));
        h.assertTrue(menu.canister(pb) == 0, "the dry secondee shows " + menu.canister(pb));
        h.assertTrue(menu.canister(pc) == -1, "a lanyarded cow shows a canister: " + menu.canister(pc));
        h.assertTrue(menu.canister(3) == -1 && menu.canister(-1) == -1 && menu.canister(TimeClockMenu.BROTH_ROWS) == -1, "empty places show canisters");
        // the numbers follow the clock
        data(a).canister = 499;
        c.broth().drain(500, IFluidHandler.FluidAction.EXECUTE);
        h.assertTrue(menu.canister(pa) == 499 && menu.brothTank() == 2000, "the tab did not follow: " + menu.canister(pa) + ", " + menu.brothTank());
        done(h, p);
    }
}
