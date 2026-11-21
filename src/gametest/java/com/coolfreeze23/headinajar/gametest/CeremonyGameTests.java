package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlock;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.SecondRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Grades;
import com.coolfreeze23.headinajar.staff.Promotion;
import com.coolfreeze23.headinajar.staff.RosterPromotion;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.second.Secondees;
import com.coolfreeze23.headinajar.staff.second.Secondment;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The Roster's Promote for a seconded Team Lead (plan 5.1 #3): pressed within 8 blocks of the clock it walks there,
 * the ceremony plays at the clock, the collar unclips the head into the presser's hands with its file promoted, and the
 * body stays an empty collared host, half synced and handshaken; beyond 8 blocks, the "far" line and nothing else. The
 * press's other refusals (a living worker is promoted in person, below Team Lead, someone else's), and a walk called
 * off when the presser leaves, with nothing spent.
 * <p>
 * {@code platform32}: 32 x 10 x 32, the floor's top at helper y 5; the clock stands at y 6 and reads its own test time.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class CeremonyGameTests {
    private static final BlockPos CLOCK = new BlockPos(16, 6, 16);

    private static ServerPlayer boss(GameTestHelper h) {
        return TestPlayers.at(h, "board", CLOCK.south(2));
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static TimeClockBlockEntity clock(GameTestHelper h, ServerPlayer owner) {
        h.setBlock(CLOCK, WorkRegistry.TIME_CLOCK.get().defaultBlockState().setValue(TimeClockBlock.FACING, Direction.SOUTH));
        TimeClockBlockEntity c = (TimeClockBlockEntity) h.getBlockEntity(CLOCK);
        c.timeOverride = 1000;
        c.setOwner(owner);
        c.registerSite();
        return c;
    }

    /** A collared cow host with a zombie head seated on it by {@code p} (the saw's and the collar's own paths), on the clock at {@code grade}. */
    private static Mob secondee(GameTestHelper h, ServerPlayer p, TimeClockBlockEntity clock, BlockPos at, int grade) {
        Mob cow = h.spawn(EntityType.COW, at);
        cow.setHealth(1);
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        h.assertTrue(SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), cow, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "the saw makes a host of the cow");
        use(p, new ItemStack(SecondRegistry.SECONDMENT_COLLAR.get()), cow);
        use(p, zombieHead(h, p, at.west(3)), cow);
        h.assertTrue(Secondees.seated(cow), "the zombie head is seated");
        h.assertTrue(clock.join(cow), "the secondee is not on the clock");
        StaffFile f = Staff.of(cow);
        f.career.grade = grade;
        f.tasks = Grades.TASKS[grade];
        cow.setData(ModRegistry.STAFF, f);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return cow;
    }

    private static ItemStack zombieHead(GameTestHelper h, ServerPlayer p, BlockPos at) {
        Mob zombie = h.spawn(EntityType.ZOMBIE, at);
        zombie.setHealth(1);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), zombie, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(ModRegistry.JARRED_HEAD.get()) && JarredHeadItem.type(s) == EntityType.ZOMBIE) {
                p.getInventory().setItem(i, ItemStack.EMPTY);
                return s;
            }
        }
        throw new AssertionError("no jarred zombie head");
    }

    private static void use(ServerPlayer p, ItemStack stack, Mob host) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Secondment.instrument(p, p, p.getMainHandItem(), host, InteractionHand.MAIN_HAND);
    }

    private static ItemStack head(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (s.is(ModRegistry.JARRED_HEAD.get())) return s;
        return ItemStack.EMPTY;
    }

    private static List<String> history(Mob mob) {
        List<String> said = new ArrayList<>();
        for (String line : Promotion.HISTORY) if (line.contains(mob.getUUID().toString())) said.add(line);
        return said;
    }

    private static boolean saw(Mob mob, String what) {
        for (String line : history(mob)) if (line.endsWith(" " + what)) return true;
        return false;
    }

    /** Runs {@code then} on the first tick {@code ready} holds, checking every tick. */
    private static void when(GameTestHelper h, BooleanSupplier ready, Runnable then) {
        h.runAfterDelay(1, () -> {
            if (ready.getAsBoolean()) then.run();
            else when(h, ready, then);
        });
    }

    /** A press that refuses: true returned, the line named, no walk, no ceremony, the head still seated. */
    private static void refused(GameTestHelper h, ServerPlayer p, TimeClockBlockEntity clock, Mob mob, String reason) {
        h.assertTrue(Promotion.fromRoster(p, clock, mob), reason + ": the press did nothing");
        h.assertValueEqual(Refusals.lastDelivered(p), reason, "the refusal");
        h.assertTrue(RosterPromotion.walk(mob) == null, reason + ": it was sent to the clock");
        for (Promotion.Ceremony c : Promotion.running()) h.assertTrue(!c.candidate.equals(mob.getUUID()), reason + ": a ceremony started");
    }

    // --- plan 5.1 #3 ------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = RosterPromotion.WALK_LIMIT + Promotion.LENGTH + 60)
    public static void aSecondedTeamLeadIsPromotedFromTheRoster(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p);
        Mob cow = secondee(h, p, clock, CLOCK.east(4).south(3), Grades.TEAM_LEAD);
        int number = Staff.of(cow).number;
        h.assertTrue(Promotion.fromRoster(p, clock, cow), "the press did nothing");
        h.assertTrue(RosterPromotion.walk(cow) != null && saw(cow, "summoned"), "it was not sent to the clock: " + Refusals.lastDelivered(p));
        // a second press while it walks is the Board already in session
        Promotion.fromRoster(p, clock, cow);
        h.assertValueEqual(Refusals.lastDelivered(p), "promotion.busy", "a second press");
        when(h, () -> saw(cow, "start"), () -> {
            Vec3 foot = Vec3.atBottomCenterOf(h.absolutePos(CLOCK));
            h.assertTrue(cow.position().distanceTo(foot) <= RosterPromotion.ARRIVED + 1.5, "the ceremony began away from the clock: " + cow.position().distanceTo(foot));
            h.assertTrue(Secondees.seated(cow) && head(p).isEmpty(), "something was spent before the cut");
            when(h, () -> saw(cow, "finished") || saw(cow, "cancelled candidate") || saw(cow, "cancelled player"), () -> {
                List<String> said = history(cow);
                h.assertTrue(saw(cow, "commit") && saw(cow, "confetti") && saw(cow, "jingle") && saw(cow, "bell") && saw(cow, "done_line"),
                        "the timeline did not run at the clock: " + said);
                h.assertTrue(cow.isAlive() && !cow.isRemoved(), "the body is gone");
                h.assertFalse(Secondees.seated(cow), "the head is still on the collar");
                h.assertTrue(Secondees.collared(cow), "the collar came off with the head");
                h.assertTrue(Hosts.isHost(cow) && Hosts.data(cow).sync >= 50 && Hosts.data(cow).handshaken, "the body does not remember its training");
                h.assertTrue(!Workforce.isWorker(cow) && !clock.has(cow.getUUID()), "the body is still on the workforce");
                ItemStack back = head(p);
                h.assertTrue(!back.isEmpty() && JarredHeadItem.type(back) == EntityType.ZOMBIE, "the head did not come to the presser");
                StaffFile headFile = Staff.of(back);
                h.assertTrue(headFile != null && headFile.number == number && headFile.career.promotedDay >= 0 && Grades.of(headFile) == Grades.TEAM_LEAD,
                        "the head's file is not promoted: " + (headFile == null ? null : headFile.career.promotedDay));
                h.assertTrue(headFile.history.get(headFile.history.size() - 1).k().equals("promoted"), "the file's last line: " + headFile.history);
                StaffFile body = Staff.of(cow);
                h.assertTrue(body == null || body.career.link == number, "the body's file does not name the head");
                h.assertTrue(body == null || headFile.career.link == body.number, "the head's file does not name the body");
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform32")
    public static void aSecondedTeamLeadTooFarFromTheClockIsAskedCloser(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p);
        Mob cow = secondee(h, p, clock, CLOCK.east(10), Grades.TEAM_LEAD);
        h.assertTrue(cow.position().distanceTo(Vec3.atBottomCenterOf(h.absolutePos(CLOCK))) > Promotion.ROSTER_REACH, "the test cow is not beyond 8 blocks");
        refused(h, p, clock, cow, "promotion.far");
        h.assertTrue(Secondees.seated(cow) && head(p).isEmpty(), "something was spent");
        done(h, p);
    }

    @GameTest(template = "platform32")
    public static void theRosterPromoteRefusesWithItsLines(GameTestHelper h) {
        ServerPlayer p = boss(h);
        ServerPlayer other = TestPlayers.at(h, "other", CLOCK.south(3).west(2));
        TimeClockBlockEntity clock = clock(h, p);
        // below Team Lead
        Mob associate = secondee(h, p, clock, CLOCK.south(3).east(3), Grades.ASSOCIATE);
        refused(h, p, clock, associate, "promotion.below");
        Mob intern = secondee(h, p, clock, CLOCK.south(4).west(3), Grades.INTERN);
        refused(h, p, clock, intern, "promotion.intern");
        // a Team Lead pressed by someone who is not its employer (a trusted player may review, never promote)
        Mob lead = secondee(h, p, clock, CLOCK.south(5).east(1), Grades.TEAM_LEAD);
        refused(h, other, clock, lead, "promotion.others");
        // a living Team Lead (not seconded) is promoted in person, with the saw
        Mob living = MomentsGameTests.staff(h, p, EntityType.COW, CLOCK.south(4).east(5), Grades.TEAM_LEAD);
        clock.join(living);
        refused(h, p, clock, living, "promotion.in_person");
        done(h, p, other);
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void aWalkToTheClockIsCalledOffWhenThePresserLeaves(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p);
        Mob cow = secondee(h, p, clock, CLOCK.east(6), Grades.TEAM_LEAD);
        h.assertTrue(Promotion.fromRoster(p, clock, cow) && RosterPromotion.walk(cow) != null, "it was not sent: " + Refusals.lastDelivered(p));
        BlockPos far = h.absolutePos(CLOCK.west(14));
        p.teleportTo(far.getX() + 0.5, far.getY() + 40, far.getZ() + 0.5);
        when(h, () -> RosterPromotion.walk(cow) == null, () -> {
            h.assertTrue(saw(cow, "cancelled player"), "the walk was not called off for the presser: " + history(cow));
            h.assertTrue(!saw(cow, "start"), "a ceremony started");
            h.assertTrue(Secondees.seated(cow) && Workforce.isWorker(cow) && clock.has(cow.getUUID()) && head(p).isEmpty(), "something was spent");
            done(h, p);
        });
    }
}
