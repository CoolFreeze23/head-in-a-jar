package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.DeskJobs;
import com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.block.MachineBlock;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.Band;
import com.coolfreeze23.headinajar.staff.Grades;
import com.coolfreeze23.headinajar.staff.Grievances;
import com.coolfreeze23.headinajar.staff.SiteState;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffBlocks;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import com.coolfreeze23.headinajar.staff.work.WorkerNet;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Chapter 3's Grievance (Form 66-G), 0.7.2: filed at the second dawn running under 15 by a site worker, a plinth head
 * and an Employee Mount's villager (three days unpaid); a site worker stands facing the wall and does no work; it ends
 * once morale is back to 40 (a cake does it, Let Them Eat Cake); nothing is lost. And old saves: a 0.7.1 staff file and
 * a 0.7.1 mount load as they were.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class GrievanceGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h) {
        return TestPlayers.at(h, "hr", FLOOR.south(3));
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static boolean noted(StaffFile f, String key) {
        for (StaffFile.Entry e : f.history) if (e.k().equals(key)) return true;
        return false;
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void grievanceStartsAndEndsForASiteWorker(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = MomentsGameTests.staff(h, p, EntityType.COW, FLOOR, Grades.ASSOCIATE);
        // a wall two blocks to its east, the nearest solid face
        for (int y = 2; y <= 3; y++) h.setBlock(FLOOR.east(2).atY(y), Blocks.STONE);
        StaffFile f = Staff.of(cow);
        int tasks = f.tasks, grade = f.career.grade;
        f.morale = 10;
        f.lowDawns = 1;
        Grievances.dawn(cow, f);
        h.assertTrue(!Staff.of(cow).career.grievance, "a grievance at the first dawn under 15");
        f.lowDawns = 2;
        Grievances.dawn(cow, f);
        StaffFile g = Staff.of(cow);
        h.assertTrue(g.career.grievance && g.career.grievancesSinceReview == 1 && noted(g, "grievance"), "no Form 66-G at the second dawn");
        h.assertTrue(Band.of(g) == Band.GRIEVANCE, "the band: " + Band.of(g));
        h.assertTrue((WorkerLooks.bits(cow) & WorkerNet.GRIEVANCE) != 0, "the look does not show it");
        // it is held: no work, facing the wall
        h.assertTrue(SiteState.held(cow, Workforce.data(cow)), "the brain was not held");
        h.assertTrue(Math.abs(Mth.wrapDegrees(cow.getYRot() - Direction.EAST.toYRot())) < 1, "not facing the wall: " + cow.getYRot());
        // still Disengaged by number (30): still a grievance
        g.morale = 30;
        cow.setData(ModRegistry.STAFF, g);
        h.assertTrue(SiteState.held(cow, Workforce.data(cow)) && Band.of(Staff.of(cow)) == Band.GRIEVANCE, "it ended under 40");
        // a cake: +40, the grievance ends, Let Them Eat Cake; the cake is eaten; a second the same day is refused
        ItemStack cake = new ItemStack(Items.CAKE, 2);
        Grievances.give(p, cow, Staff.of(cow), cake);
        StaffFile after = Staff.of(cow);
        h.assertTrue(!after.career.grievance && after.morale == 70 && noted(after, "grievance_withdrawn"), "the cake did not end it: " + after.morale);
        h.assertTrue(cake.getCount() == 1, "the cake was not eaten");
        h.assertTrue(!SiteState.held(cow, Workforce.data(cow)), "still held after it ended");
        Grievances.give(p, cow, Staff.of(cow), cake);
        h.assertValueEqual(Refusals.lastDelivered(p), "staff.no_cake", "a second cake");
        h.assertTrue(cake.getCount() == 1, "the second cake was eaten");
        // nothing lost
        h.assertTrue(after.tasks == tasks && after.career.grade == grade && Workforce.isWorker(cow), "something was lost");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void grievanceEndsWhenMoraleIsBackWithoutCake(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = MomentsGameTests.staff(h, p, EntityType.COW, FLOOR, Grades.INTERN);
        StaffFile f = Staff.of(cow);
        f.morale = 5;
        f.lowDawns = 2;
        Grievances.dawn(cow, f);
        h.assertTrue(Staff.of(cow).career.grievance, "no grievance");
        StaffFile g = Staff.of(cow);
        g.morale = 45;
        cow.setData(ModRegistry.STAFF, g);
        h.assertTrue(!SiteState.held(cow, Workforce.data(cow)), "held at 45");
        h.assertTrue(!Staff.of(cow).career.grievance, "not withdrawn at 45");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void grievanceStartsAndEndsForAPlinthHead(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = h.spawn(EntityType.COW, FLOOR.above(3));
        ItemStack item = JarredHeadItem.of(cow);
        cow.discard();
        h.setBlock(FLOOR, ModRegistry.JARRED_HEAD_BLOCK.get());
        JarredHeadBlockEntity head = (JarredHeadBlockEntity) h.getBlockEntity(FLOOR);
        CustomData data = item.get(DataComponents.CUSTOM_DATA);
        head.load(data == null ? new CompoundTag() : data.copyTag());
        StaffFile f = Staff.fileFor(h.getLevel(), EntityType.COW, null);
        Staff.employ(f, p);
        f.morale = 8;
        f.lowDawns = 2;
        Grievances.dawn(head, f);
        head.setStaff(f);
        StaffBlocks.show(head, f);
        h.assertTrue(head.staff().career.grievance && noted(head.staff(), "grievance"), "no Form 66-G on the plinth");
        h.assertTrue(head.band() == Band.GRIEVANCE, "the plinth's band: " + head.band());
        // a cake on it (the plinth's own cake rule): +40 brings it to 48, the grievance ends
        StaffFile g = head.staff();
        StaffBlocks.adjust(g, DeskJobs.CAKE);
        Grievances.check(head, g, p);
        head.setStaff(g);
        h.assertTrue(!head.staff().career.grievance && noted(head.staff(), "grievance_withdrawn"), "the plinth's grievance did not end");
        done(h, p);
    }

    private static EmployeeMountBlockEntity mount(GameTestHelper h, ServerPlayer p) {
        h.setBlock(FLOOR, ModRegistry.DNA_SEQUENCER.get());
        BlockPos at = FLOOR.east();
        h.setBlock(at, ModRegistry.EMPLOYEE_MOUNT.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        EmployeeMountBlockEntity m = (EmployeeMountBlockEntity) h.getBlockEntity(at);
        Villager v = h.spawn(EntityType.VILLAGER, FLOOR.above(3));
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
        ItemStack head = JarredHeadItem.of(v);
        v.discard();
        m.use(p, head);
        return m;
    }

    /** Moves the mount's pay record back (as a save would hold it). */
    private static void paidThrough(GameTestHelper h, EmployeeMountBlockEntity m, long day) {
        CompoundTag tag = m.saveWithoutMetadata(h.getLevel().registryAccess());
        tag.putLong("PaidThrough", day);
        m.loadWithComponents(tag, h.getLevel().registryAccess());
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void grievanceOnAMountIsBookkeepingOnly(GameTestHelper h) {
        ServerPlayer p = player(h);
        EmployeeMountBlockEntity m = mount(h, p);
        long today = Staff.day(h.getLevel());
        paidThrough(h, m, today - 3);
        h.assertTrue(m.band() == Band.GRIEVANCE, "three days unpaid: " + m.band());
        float bonus = com.coolfreeze23.headinajar.block.HeadJobs.staff(h.getLevel(), h.absolutePos(FLOOR), "librarian");
        m.grievanceBookkeeping();
        h.assertTrue(m.grievanceFiled(), "not filed");
        StaffFile f = Staff.of(m.head());
        h.assertTrue(f != null && noted(f, "grievance"), "no history line on the head's file");
        m.grievanceBookkeeping();
        h.assertTrue(f.career.grievancesSinceReview == 1 && Staff.of(m.head()).career.grievancesSinceReview == 1, "filed twice");
        h.assertTrue(com.coolfreeze23.headinajar.block.HeadJobs.staff(h.getLevel(), h.absolutePos(FLOOR), "librarian") == bonus, "the bonus changed");
        // paid again: withdrawn
        paidThrough(h, m, today);
        m.grievanceBookkeeping();
        h.assertTrue(!m.grievanceFiled() && noted(Staff.of(m.head()), "grievance_withdrawn"), "not withdrawn when paid");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void oldWorldGradesAndMounts(GameTestHelper h) throws Exception {
        // a staff file as 0.7.1 saved it (no career): an Intern with its tasks, never reviewed, no grievance, not promoted
        CompoundTag old = TagParser.parseTag("{number:412,species:\"minecraft:cow\",nameIndex:3,joined:2L,morale:55,paidThrough:9L,"
                + "lowDawns:0,lastDawn:9L,tasks:318,history:[{k:\"hired\",d:2L,a:\"Steve\"}]}");
        StaffFile f = StaffFile.load(old);
        h.assertTrue(f != null && f.tasks == 318 && Grades.of(f) == Grades.INTERN && f.career.reviewedDay == -1
                && !f.career.grievance && f.career.promotedDay == -1 && f.career.cakeDay == -1, "the 0.7.1 file did not load as an Intern");
        h.assertTrue(Grades.due(f), "300 tasks and no review: a review is due");
        // saved again untouched, it is the same record (nothing new written for the defaults)
        h.assertValueEqual(f.save(), StaffFile.CODEC.encodeStart(NbtOps.INSTANCE, StaffFile.load(old)).result().orElseThrow(), "the round trip");
        h.assertTrue(!f.save().contains("career"), "a default career was written");
        // a 0.7.1 mount (no Grievance key), paid today: Content, nothing filed
        ServerPlayer p = player(h);
        EmployeeMountBlockEntity m = mount(h, p);
        CompoundTag tag = m.saveWithoutMetadata(h.getLevel().registryAccess());
        tag.remove("Grievance");
        tag.putLong("PaidThrough", Staff.day(h.getLevel()));
        tag.remove("PaidStreak");
        m.loadWithComponents(tag, h.getLevel().registryAccess());
        h.assertTrue(m.band() == Band.CONTENT && !m.grievanceFiled(), "an old paid mount: " + m.band());
        done(h, p);
    }
}
