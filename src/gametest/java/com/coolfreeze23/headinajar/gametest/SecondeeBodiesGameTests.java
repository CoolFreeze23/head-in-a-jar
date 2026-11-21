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
/**
 * 0.7.1, bodies that had trouble reaching their clock (seen in the client): a squid body punches in from the water by its
 * clock, and says so at a clock with no water within reach; horse bodies join the site they stand in like any other.
 * {@code platform32}: the floor's top at helper y 5; the clock stands at y 6 facing north and reads its own test time.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class SecondeeBodiesGameTests {
    private static final BlockPos CLOCK = new BlockPos(16, 6, 16);

    private static ServerPlayer boss(GameTestHelper h) {
        return TestPlayers.at(h, "bodies", new BlockPos(2, 6, 2));
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static TimeClockBlockEntity clock(GameTestHelper h, ServerPlayer owner) {
        h.setBlock(CLOCK, WorkRegistry.TIME_CLOCK.get().defaultBlockState().setValue(TimeClockBlock.FACING, Direction.NORTH));
        TimeClockBlockEntity c = (TimeClockBlockEntity) h.getBlockEntity(CLOCK);
        c.timeOverride = Shifts.DAY_START + 50;
        c.setOwner(owner);
        c.registerSite();
        return c;
    }

    private static InteractionResult use(ServerPlayer p, ItemStack stack, Mob host) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return Secondment.instrument(p, p, p.getMainHandItem(), host, InteractionHand.MAIN_HAND);
    }

    /** A {@code head} head seated on a collared host of {@code body} at {@code at}, its canister full. */
    private static Mob secondee(GameTestHelper h, ServerPlayer p, EntityType<? extends Mob> body, EntityType<? extends Mob> headType, BlockPos at) {
        Mob host = h.spawn(body, at);
        host.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), host, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "the saw did not make a host: " + Refusals.lastDelivered(p));
        use(p, new ItemStack(SecondRegistry.SECONDMENT_COLLAR.get()), host);
        Mob donor = h.spawn(headType, new BlockPos(2, 6, 28));
        ItemStack head = JarredHeadItem.of(donor);
        donor.discard();
        use(p, head, host);
        h.assertTrue(Secondees.seated(host), "the head was not seated on the " + body.getDescription().getString() + ": " + Refusals.lastDelivered(p));
        Secondees.data(host).canister = SecondeeData.CANISTER;
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return host;
    }

    /**
     * A pool north of the clock, two deep, reaching the clock's front cell only through a notch one block wide (as in
     * the client: the squid must punch from the open water within reach, not from the notch it cannot swim into).
     */
    private static void pool(GameTestHelper h) {
        for (BlockPos q : BlockPos.betweenClosed(new BlockPos(11, 4, 6), new BlockPos(21, 5, 14))) h.setBlock(q, Blocks.WATER);
        h.setBlock(new BlockPos(16, 5, 15), Blocks.WATER);
    }

    @GameTest(template = "platform32", timeoutTicks = 900)
    public static void aSquidBodyPunchesInFromTheWaterByItsClock(GameTestHelper h) {
        ServerPlayer p = boss(h);
        pool(h);
        TimeClockBlockEntity c = clock(h, p);
        Mob squid = secondee(h, p, EntityType.SQUID, EntityType.SHEEP, new BlockPos(16, 4, 8));
        WorkerData d = Workforce.data(squid);
        h.assertTrue(c.join(squid), "setup: the clock had no room");
        h.succeedWhen(() -> {
            h.assertTrue(c.punchedFor(squid.getUUID(), Shifts.stamp(d.shift, c)),
                    "the squid has not punched in: " + d.state + ", " + d.status + ", at " + h.relativeVec(squid.position()));
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void aSquidBodyAtADryClockSaysSo(GameTestHelper h) {
        ServerPlayer p = boss(h);
        // a pool well away from the clock: no water within its reach
        for (BlockPos q : BlockPos.betweenClosed(new BlockPos(2, 4, 4), new BlockPos(8, 5, 10))) h.setBlock(q, Blocks.WATER);
        TimeClockBlockEntity c = clock(h, p);
        Mob squid = secondee(h, p, EntityType.SQUID, EntityType.SHEEP, new BlockPos(5, 4, 7));
        WorkerData d = Workforce.data(squid);
        h.assertTrue(c.join(squid), "setup: the clock had no room");
        h.succeedWhen(() -> {
            h.assertValueEqual(d.status, "staff.headinajar.say.secondment.clock_on_land", "the squid's status at a dry clock");
            done(h, p);
        });
    }

    /** Four horse bodies with villager heads, set down in the site's box: each joins the clock (2 of 4 did not, once). */
    @GameTest(template = "platform32", timeoutTicks = 600)
    public static void horseBodiesJoinTheSiteTheyStandIn(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity c = clock(h, p);
        List<Mob> horses = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) horses.add(secondee(h, p, EntityType.HORSE, EntityType.VILLAGER, new BlockPos(8 + 5 * i, 6, 24)));
        h.succeedWhen(() -> {
            for (Mob m : horses) {
                WorkerData d = Workforce.data(m);
                var horse = (net.minecraft.world.entity.animal.horse.AbstractHorse) m;
                h.assertTrue(c.has(m.getUUID()), "a horse body did not join: " + (d == null ? "no worker data" : d.state + ", " + d.status)
                        + ", eating " + horse.isEating() + ", standing " + horse.isStanding() + ", at " + h.relativeVec(m.position()));
            }
            done(h, p);
        });
    }
}
