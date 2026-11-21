package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity;
import com.coolfreeze23.headinajar.block.ChairQuiet;
import com.coolfreeze23.headinajar.block.DoloresVoice;
import com.coolfreeze23.headinajar.block.HeadJobs;
import com.coolfreeze23.headinajar.block.SeparationChairBlock;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.menu.SeparationChairMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Grades;
import com.coolfreeze23.headinajar.staff.Promotion;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The moments of 0.7.2: the Promotion to the Board (the saw's staff branch, row by row of its order, so a failure names
 * the case), the ceremony against the Separation Chair (never both at once), the ceremony cancelled cleanly, the
 * candidate unhurtable, and the Cerebral Array's promoted rows with the 0.5.4 table pinned.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class MomentsGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7), CHAIR = new BlockPos(2, 2, 2);
    private static final int CEREMONY = Promotion.LENGTH + 10;

    private static ServerPlayer player(GameTestHelper h) {
        return TestPlayers.at(h, "board", FLOOR.south(2));
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** Hires a creature with the Staff Lanyard (a hostile worn down first) and gives it a grade (a test's setup). */
    static <T extends Mob> T staff(GameTestHelper h, ServerPlayer boss, EntityType<T> type, BlockPos at, int grade) {
        T mob = h.spawn(type, at);
        if (mob instanceof Enemy) mob.setHealth(mob.getMaxHealth() * 0.4f);
        boolean hired = Workforce.useLanyard(boss, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), mob);
        h.assertTrue(hired, "the lanyard did not hire the " + type.getDescription().getString() + ": " + Refusals.lastDelivered(boss));
        StaffFile f = Staff.of(mob);
        f.career.grade = grade;
        f.tasks = Grades.TASKS[grade];
        mob.setData(ModRegistry.STAFF, f);
        mob.setHealth(mob.getMaxHealth());
        return mob;
    }

    /** The saw in the main hand and (if {@code jar}) a Life Support Jar in the off hand. */
    private static ItemStack arm(ServerPlayer p, boolean jar) {
        ItemStack saw = new ItemStack(ModRegistry.SURGICAL_SAW.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, saw);
        p.setItemInHand(InteractionHand.OFF_HAND, jar ? new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()) : ItemStack.EMPTY);
        return saw;
    }

    private static InteractionResult saw(ServerPlayer p, Mob mob) {
        return SurgicalSawItem.saw(p, p.getMainHandItem(), mob, InteractionHand.MAIN_HAND);
    }

    private static int heads(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(ModRegistry.JARRED_HEAD.get())) n += s.getCount();
        return n;
    }

    private static ItemStack firstHead(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (s.is(ModRegistry.JARRED_HEAD.get())) return s;
        return ItemStack.EMPTY;
    }

    /** A refusal: the line named, and nothing touched (health, jar, saw, no ceremony, not a host). */
    private static void refused(GameTestHelper h, ServerPlayer p, Mob mob, String reason, boolean jar) {
        float health = mob.getHealth();
        ItemStack saw = p.getMainHandItem();
        InteractionResult r = saw(p, mob);
        h.assertTrue(r == InteractionResult.FAIL, reason + ": the saw answered " + r);
        h.assertValueEqual(Refusals.lastDelivered(p), reason, "the refusal");
        h.assertTrue(mob.isAlive() && mob.getHealth() == health, reason + ": the creature was touched");
        h.assertTrue(!Hosts.isHost(mob), reason + ": it became a host");
        h.assertTrue(saw.getDamageValue() == 0, reason + ": the saw was used");
        h.assertTrue(p.getOffhandItem().is(ModRegistry.LIFE_SUPPORT_JAR.get()) == jar, reason + ": the jar was spent");
        for (Promotion.Ceremony c : Promotion.running()) h.assertTrue(!c.candidate.equals(mob.getUUID()), reason + ": a ceremony started");
    }

    // --- 1. the waiver is exact ---------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = CEREMONY + 40)
    public static void theWaiverIsExactOwnTeamLeadIsPromoted(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = staff(h, p, EntityType.COW, FLOOR, Grades.TEAM_LEAD);
        ItemStack saw = arm(p, true);
        int number = Staff.of(cow).number;
        h.assertTrue(cow.getHealth() == cow.getMaxHealth(), "the cow is at full health");
        h.assertTrue(saw(p, cow) == InteractionResult.CONSUME, "the saw did not start the ceremony: " + Refusals.lastDelivered(p));
        long start = h.getLevel().getGameTime();
        h.runAfterDelay(Promotion.COMMIT - 2, () -> {
            h.assertTrue(Workforce.isWorker(cow) && heads(p) == 0 && p.getOffhandItem().is(ModRegistry.LIFE_SUPPORT_JAR.get()), "nothing is spent before the cut");
        });
        h.runAfterDelay(CEREMONY, () -> {
            h.assertTrue(cow.isAlive() && !cow.isRemoved(), "the body is gone");
            h.assertTrue(Hosts.isHost(cow), "the body is not a host");
            h.assertTrue(Hosts.data(cow).sync >= 50 && Hosts.data(cow).handshaken, "the body does not remember its training");
            h.assertTrue(!Workforce.isWorker(cow), "the body is still on the workforce");
            h.assertTrue(heads(p) == 1, "no head in hand");
            StaffFile head = Staff.of(firstHead(p));
            h.assertTrue(head != null && head.number == number && head.career.promotedDay >= 0, "the head's file is not promoted: " + (head == null ? null : head.career.promotedDay));
            h.assertTrue(JarredHeadItem.type(firstHead(p)) == EntityType.COW, "the head is not the cow's");
            // the file, name and number went to the Board with the head: the body is a plain host with no file
            h.assertTrue(Staff.of(cow) == null && !cow.hasData(ModRegistry.STAFF), "the body still carries a staff file");
            h.assertTrue(!p.getOffhandItem().is(ModRegistry.LIFE_SUPPORT_JAR.get()), "the jar was not spent");
            h.assertTrue(saw.getDamageValue() == 1, "the saw was not used once");
            boolean lanyard = false;
            for (ItemStack s : p.getInventory().items) lanyard |= s.is(WorkRegistry.STAFF_LANYARD.get());
            h.assertTrue(lanyard, "the lanyard did not come back");
            // the timeline: the commit at 120, the bell at 131, the closing line at 145
            List<String> said = new ArrayList<>();
            for (String line : Promotion.HISTORY) if (line.contains(cow.getUUID().toString())) said.add(line);
            h.assertTrue(said.stream().anyMatch(l -> l.startsWith((start + Promotion.COMMIT) + " ") && l.endsWith("commit")), "the cut was not at tick 120: " + said);
            h.assertTrue(said.stream().anyMatch(l -> l.startsWith((start + Promotion.BELL) + " ") && l.endsWith("bell")), "the bell was not at 131: " + said);
            h.assertTrue(said.stream().anyMatch(l -> l.endsWith("finished")), "it never finished: " + said);
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void theWaiverIsExactTeamLeadWithoutAJar(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = staff(h, p, EntityType.COW, FLOOR, Grades.TEAM_LEAD);
        arm(p, false);
        refused(h, p, cow, "promotion.no_jar", false);
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theWaiverIsExactSeniorAssociateIsNeverCut(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = staff(h, p, EntityType.COW, FLOOR, Grades.SENIOR);
        arm(p, true);
        refused(h, p, cow, "promotion.below", true);
        cow.setHealth(cow.getMaxHealth() * 0.4f);
        refused(h, p, cow, "promotion.below", true);
        arm(p, false);
        refused(h, p, cow, "staff.saw", false);
        cow.setHealth(cow.getMaxHealth());
        refused(h, p, cow, "staff.saw", false);
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theWaiverIsExactInterns(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = staff(h, p, EntityType.COW, FLOOR, Grades.INTERN);
        arm(p, true);
        refused(h, p, cow, "promotion.intern", true);
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theWaiverIsExactSomeoneElsesTeamLead(GameTestHelper h) {
        ServerPlayer boss = player(h), other = TestPlayers.at(h, "other", FLOOR.east(2));
        Mob cow = staff(h, boss, EntityType.COW, FLOOR, Grades.TEAM_LEAD);
        arm(other, true);
        refused(h, other, cow, "promotion.others", true);
        // trusted players may review, never promote
        com.coolfreeze23.headinajar.form.HeadRegistry.get(boss).setTrust(boss.getUUID(), other.getUUID(), true);
        refused(h, other, cow, "promotion.others", true);
        done(h, boss, other);
    }

    @GameTest(template = "platform")
    public static void theWaiverIsExactANameTag(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = staff(h, p, EntityType.COW, FLOOR, Grades.TEAM_LEAD);
        cow.setCustomName(Component.literal("Daisy"));
        arm(p, true);
        refused(h, p, cow, "saw.named", true);
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theWaiverIsExactNotAHostWithAForgedFile(GameTestHelper h) {
        ServerPlayer p = player(h);
        // an armour stand is not a Mob: the saw never reaches staff code (its own line, as always)
        var stand = h.spawn(EntityType.ARMOR_STAND, FLOOR.east(3));
        StaffFile forged = new StaffFile();
        forged.number = 9999;
        forged.career.grade = Grades.TEAM_LEAD;
        stand.setData(ModRegistry.STAFF, forged);
        arm(p, true);
        InteractionResult r = ModRegistry.SURGICAL_SAW.get().interactLivingEntity(p.getMainHandItem(), p, stand, InteractionHand.MAIN_HAND);
        h.assertTrue(r == InteractionResult.FAIL && "saw.furniture".equals(Refusals.lastDelivered(p)), "the stand: " + r + " " + Refusals.lastDelivered(p));
        // a boss with a forged file (no worker record): the table's own line
        Mob guardian = h.spawn(EntityType.ELDER_GUARDIAN, FLOOR.west(3));
        guardian.setData(ModRegistry.STAFF, forged.copy());
        guardian.setHealth(1);
        if (guardian.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES)) {
            h.assertTrue(saw(p, guardian) == InteractionResult.FAIL && "saw.boss".equals(Refusals.lastDelivered(p)), "the boss: " + Refusals.lastDelivered(p));
        }
        // a player: the saw passes, as it always did
        ServerPlayer target = TestPlayers.at(h, "target", FLOOR.north(2));
        r = ModRegistry.SURGICAL_SAW.get().interactLivingEntity(p.getMainHandItem(), p, target, InteractionHand.MAIN_HAND);
        h.assertTrue(r == InteractionResult.PASS, "a player: " + r);
        done(h, p, target);
    }

    @GameTest(template = "platform")
    public static void theWaiverIsExactOrdinaryCutsDoNotChange(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob lively = h.spawn(EntityType.COW, FLOOR);
        arm(p, true);
        h.assertTrue(saw(p, lively) == InteractionResult.FAIL && "saw.too_lively".equals(Refusals.lastDelivered(p)), "a lively cow: " + Refusals.lastDelivered(p));
        Mob weak = h.spawn(EntityType.COW, FLOOR.east(2));
        weak.setHealth(weak.getMaxHealth() * 0.5f);
        h.assertTrue(saw(p, weak) == InteractionResult.CONSUME, "a weakened cow was not cut: " + Refusals.lastDelivered(p));
        h.assertTrue(weak.isDeadOrDying(), "the jar cut did not kill it");
        h.assertTrue(heads(p) == 1, "no head kept");
        StaffFile f = Staff.of(firstHead(p));
        h.assertTrue(f != null && f.career.promotedDay == -1, "an ordinary head says promoted");
        h.assertTrue(Promotion.running().stream().noneMatch(c -> c.candidate.equals(weak.getUUID())), "an ordinary cut started a ceremony");
        done(h, p);
    }

    // --- 2. a creature that is all head goes whole ----------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = CEREMONY + 40)
    public static void aHeadOnlyTeamLeadGoesWhole(GameTestHelper h) {
        ServerPlayer p = player(h);
        net.minecraft.world.entity.monster.Slime slime = staff(h, p, EntityType.SLIME, FLOOR, Grades.TEAM_LEAD);
        slime.setSize(1, true);
        arm(p, true);
        h.assertTrue(saw(p, slime) == InteractionResult.CONSUME, "the slime's ceremony did not start: " + Refusals.lastDelivered(p));
        h.runAfterDelay(CEREMONY, () -> {
            h.assertTrue(slime.isRemoved(), "a body was left behind");
            h.assertTrue(heads(p) == 1, "no head");
            List<net.minecraft.world.entity.monster.Slime> left = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class, slime.getBoundingBox().inflate(4));
            h.assertTrue(left.isEmpty(), "the slime split");
            done(h, p);
        });
    }

    // --- 4. the ceremony never overlaps an operation --------------------------------------------------------------------

    private static SeparationChairBlockEntity chair(GameTestHelper h) {
        h.setBlock(CHAIR, ModRegistry.SEPARATION_CHAIR.get().defaultBlockState().setValue(SeparationChairBlock.FACING, Direction.SOUTH));
        SeparationChairBlockEntity chair = (SeparationChairBlockEntity) h.getBlockEntity(CHAIR);
        chair.items().setStackInSlot(0, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        chair.items().setStackInSlot(1, new ItemStack(ModRegistry.RELAY_GOGGLES.get()));
        chair.items().setStackInSlot(2, new ItemStack(ModRegistry.COLLAR_CAM.get()));
        chair.items().setStackInSlot(3, new ItemStack(ModRegistry.SALINE_BOTTLE.get(), 4));
        chair.energy().set(50_000);
        return chair;
    }

    private static ServerPlayer patient(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(CHAIR));
        player.moveTo(v.x, v.y, v.z, 0, 0);
        SeatEntity seat = SeatEntity.at(h.getLevel(), h.absolutePos(CHAIR), Direction.SOUTH);
        h.assertTrue(player.startRiding(seat, true), "could not sit");
        return player;
    }

    private static boolean sign(SeparationChairBlockEntity chair, ServerPlayer p) {
        return ((SeparationChairMenu) chair.createMenu(1, p.getInventory(), p)).clickMenuButton(p, SeparationChairMenu.BUTTON_SIGN);
    }

    // its own batch: it needs no other test's running chair within 32 blocks of its cow (which tests stand that near
    // depends only on how many tests there are)
    @GameTest(template = "platform", timeoutTicks = 120, batch = "ceremony_chair_alone")
    public static void theCeremonyNeverOverlapsAnOperationRunningChairRefuses(GameTestHelper h) {
        ServerPlayer p = player(h), patient = patient(h);
        SeparationChairBlockEntity chair = chair(h);
        Mob cow = staff(h, p, EntityType.COW, FLOOR, Grades.TEAM_LEAD);
        h.assertTrue(sign(chair, patient), "the chair did not start");
        h.runAfterDelay(2, () -> {
            arm(p, true);
            refused(h, p, cow, "promotion.operation", true);
            // a chair 33 blocks off does not count (marked as running, as a running chair marks itself each tick)
            chair.abort(patient, "abort");
            ChairQuiet.mark(h.getLevel(), h.absolutePos(CHAIR), false);
            BlockPos far = h.absolutePos(FLOOR).offset(33, 0, 0);
            ChairQuiet.mark(h.getLevel(), far, true);
            h.assertTrue(saw(p, cow) == InteractionResult.CONSUME, "a chair at 33 blocks stopped it: " + Refusals.lastDelivered(p));
            ChairQuiet.mark(h.getLevel(), far, false);
            Promotion.running().forEach(c -> {
                if (c.candidate.equals(cow.getUUID())) c.cancelled = "test";
            });
            done(h, p, patient);
        });
    }

    /** Whether a DOLORES history line ("time x, y, z line") was said within this test's structure (16 blocks of its floor). */
    private static boolean near(GameTestHelper h, String line) {
        String[] xyz = line.substring(line.indexOf(' ') + 1, line.lastIndexOf(' ')).split(",\\s*");
        BlockPos at = new BlockPos(Integer.parseInt(xyz[0].trim()), Integer.parseInt(xyz[1].trim()), Integer.parseInt(xyz[2].trim()));
        return at.closerThan(h.absolutePos(FLOOR), 16);
    }

    /** A chair signed {@code at} ticks into a ceremony 6 blocks off: before the cut it is cancelled, after it goes silent. */
    private static void chairAt(GameTestHelper h, int at) {
        ServerPlayer p = player(h), patient = patient(h);
        SeparationChairBlockEntity chair = chair(h);
        Mob cow = staff(h, p, EntityType.COW, FLOOR, Grades.TEAM_LEAD);
        arm(p, true);
        h.assertTrue(saw(p, cow) == InteractionResult.CONSUME, "the ceremony did not start: " + Refusals.lastDelivered(p));
        long start = h.getLevel().getGameTime();
        Promotion.Ceremony c = Promotion.running().stream().filter(x -> x.candidate.equals(cow.getUUID())).findFirst().orElseThrow();
        h.runAfterDelay(at, () -> {
            h.assertTrue(sign(chair, patient), "the chair did not start at ceremony tick " + at);
            long signed = h.getLevel().getGameTime();
            h.runAfterDelay(CEREMONY - at, () -> {
                if (at < Promotion.COMMIT) {
                    h.assertTrue("operation".equals(c.cancelled), "not cancelled by the chair at " + at + ": " + c.cancelled);
                    h.assertTrue(heads(p) == 0 && p.getOffhandItem().is(ModRegistry.LIFE_SUPPORT_JAR.get()) && p.getMainHandItem().getDamageValue() == 0,
                            "something was spent at " + at);
                    h.assertTrue(Workforce.isWorker(cow) && !Hosts.isHost(cow), "the candidate was cut at " + at);
                } else {
                    h.assertTrue(c.committed && c.silenced, "not silenced after the cut at " + at);
                    h.assertTrue(heads(p) == 1 && Hosts.isHost(cow), "the result did not stand at " + at);
                }
                // from the chair's start on: no ceremony sound, voice line or Fx
                for (String line : Promotion.HISTORY) {
                    if (!line.contains(cow.getUUID().toString())) continue;
                    long t = Long.parseLong(line.substring(0, line.indexOf(' ')));
                    boolean loud = line.endsWith("confetti") || line.endsWith("jingle") || line.endsWith("bell") || line.endsWith("done_line");
                    h.assertTrue(!(loud && t >= signed), "the ceremony was heard after the chair started: " + line);
                }
                for (Fx.Sent s : Fx.sent()) {
                    if (s.gameTime() >= signed && s.fx().kind() == Fx.CONFETTI && s.fx().entityA() == cow.getId()) h.fail("confetti after the chair started");
                }
                for (String line : DoloresVoice.HISTORY) {
                    long t = Long.parseLong(line.substring(0, line.indexOf(' ')));
                    // only this test's ceremony: other tests running alongside promote their own candidates
                    if (t >= signed && line.contains("promotion_") && near(h, line)) h.fail("DOLORES spoke for the Board after the chair started: " + line);
                }
                // the chair's own operation goes on to its length, as if nothing were happening
                h.assertTrue(chair.running() || chair.finishAt() >= 0, "the chair stopped");
                chair.abort(patient, "abort");
                done(h, p, patient);
            });
        });
    }

    @GameTest(template = "platform", timeoutTicks = CEREMONY + 40)
    public static void theCeremonyNeverOverlapsAnOperationChairAt10(GameTestHelper h) {
        chairAt(h, 10);
    }

    @GameTest(template = "platform", timeoutTicks = CEREMONY + 40)
    public static void theCeremonyNeverOverlapsAnOperationChairAt119(GameTestHelper h) {
        chairAt(h, 119);
    }

    @GameTest(template = "platform", timeoutTicks = CEREMONY + 40)
    public static void theCeremonyNeverOverlapsAnOperationChairAt121(GameTestHelper h) {
        chairAt(h, 121);
    }

    @GameTest(template = "platform", timeoutTicks = CEREMONY + 40)
    public static void theCeremonyNeverOverlapsAnOperationChairAt150(GameTestHelper h) {
        chairAt(h, 150);
    }

    @GameTest(template = "platform", timeoutTicks = 300)
    public static void theCeremonyNeverOverlapsAnOperationTheChairKeepsItsTime(GameTestHelper h) {
        // a chair signed 10 ticks into a ceremony runs its whole operation, to the tick
        ServerPlayer p = player(h), patient = patient(h);
        SeparationChairBlockEntity chair = chair(h);
        Mob cow = staff(h, p, EntityType.COW, FLOOR, Grades.TEAM_LEAD);
        arm(p, true);
        h.assertTrue(saw(p, cow) == InteractionResult.CONSUME, "the ceremony did not start");
        h.runAfterDelay(10, () -> {
            h.assertTrue(sign(chair, patient), "the chair did not start");
            long start = chair.opStart();
            h.runAfterDelay(com.coolfreeze23.headinajar.block.ChairTimeline.LENGTH + 20, () -> {
                h.assertTrue(chair.finishAt() >= 0, "the chair never finished");
                // the chair finishes on the tick after its last, as every operation does (240 ticks, then the stand-up)
                h.assertValueEqual(chair.finishAt() - start, (long) com.coolfreeze23.headinajar.block.ChairTimeline.LENGTH + 1, "the operation's length");
                done(h, p, patient);
            });
        });
    }

    // --- 5. cancelled cleanly -------------------------------------------------------------------------------------------

    private static void cancelledBy(GameTestHelper h, String why, java.util.function.BiConsumer<ServerPlayer, Mob> what) {
        ServerPlayer p = player(h);
        Mob cow = staff(h, p, EntityType.COW, FLOOR, Grades.TEAM_LEAD);
        arm(p, true);
        h.assertTrue(saw(p, cow) == InteractionResult.CONSUME, "the ceremony did not start");
        Promotion.Ceremony c = Promotion.running().stream().filter(x -> x.candidate.equals(cow.getUUID())).findFirst().orElseThrow();
        h.runAfterDelay(40, () -> what.accept(p, cow));
        h.runAfterDelay(CEREMONY, () -> {
            h.assertValueEqual(c.cancelled, why, "why it stopped");
            h.assertTrue(heads(p) == 0 && p.getOffhandItem().is(ModRegistry.LIFE_SUPPORT_JAR.get()) && p.getMainHandItem().getDamageValue() == 0, "something was spent");
            h.assertTrue(!Hosts.isHost(cow), "it was cut");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = CEREMONY + 40)
    public static void theCeremonyIsCancelledCleanlyCandidateRemoved(GameTestHelper h) {
        cancelledBy(h, "candidate", (p, cow) -> cow.discard());
    }

    @GameTest(template = "platform", timeoutTicks = CEREMONY + 40)
    public static void theCeremonyIsCancelledCleanlyPlayerWalksAway(GameTestHelper h) {
        cancelledBy(h, "player", (p, cow) -> p.teleportTo(cow.getX() + 17.5, cow.getY(), cow.getZ()));
    }

    @GameTest(template = "platform", timeoutTicks = CEREMONY + 40)
    public static void theCeremonyIsCancelledCleanlyStrike(GameTestHelper h) {
        cancelledBy(h, "strike", (p, cow) -> {
            Promotion.Ceremony c = Promotion.running().stream().filter(x -> x.candidate.equals(cow.getUUID())).findFirst().orElseThrow();
            // a strike at the candidate's site: by its clock, or (a worker with no clock yet) by hand
            if (c.clock != null) Promotion.strikeStarted(h.getLevel(), c.clock);
            else Promotion.cancel(c, "strike");
        });
    }

    // --- 6. the candidate cannot be hurt --------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void theCandidateCannotBeHurtDuringIt(GameTestHelper h) {
        ServerPlayer p = player(h), other = TestPlayers.at(h, "brute", FLOOR.east(2));
        Mob cow = staff(h, p, EntityType.COW, FLOOR, Grades.TEAM_LEAD);
        arm(p, true);
        h.assertTrue(saw(p, cow) == InteractionResult.CONSUME, "the ceremony did not start");
        h.runAfterDelay(20, () -> {
            float health = cow.getHealth();
            cow.hurt(cow.damageSources().playerAttack(other), 6);
            cow.hurt(cow.damageSources().generic(), 6);
            h.assertTrue(cow.getHealth() == health, "the candidate was hurt");
            Promotion.running().forEach(c -> {
                if (c.candidate.equals(cow.getUUID())) c.cancelled = "test";
            });
            done(h, p, other);
        });
    }

    // --- 7. the Array's promoted rows -----------------------------------------------------------------------------------

    private static final EntityType<?>[] ROWS = {EntityType.COW, EntityType.ZOMBIE, EntityType.VILLAGER, EntityType.WITCH, EntityType.ENDERMAN,
            EntityType.WITHER_SKELETON, EntityType.EVOKER};
    /** 0.5.4's table, head by head, pinned. */
    private static final int[] TABLE = {10, 20, 40, 60, 80, 100, 120};

    @SuppressWarnings("unchecked")
    private static ItemStack head(GameTestHelper h, EntityType<?> type, boolean promoted) {
        Mob mob = (Mob) h.spawn((EntityType<? extends Mob>) type, FLOOR.above(3));
        ItemStack stack = JarredHeadItem.of(mob, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        mob.discard();
        StaffFile f = Staff.fileFor(h.getLevel(), type, null);
        if (promoted) f.career.promotedDay = 3;
        Staff.write(stack, f);
        return stack;
    }

    private static CerebralArrayBlockEntity array(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CEREBRAL_ARRAY.get());
        return (CerebralArrayBlockEntity) h.getBlockEntity(FLOOR);
    }

    private static void clear(CerebralArrayBlockEntity a) {
        for (int i = 0; i < CerebralArrayBlockEntity.HEAD_SLOTS; i++) a.items().setStackInSlot(i, ItemStack.EMPTY);
    }

    @GameTest(template = "platform")
    public static void arrayPromotedRows(GameTestHelper h) {
        CerebralArrayBlockEntity a = array(h);
        int[] expect = {15, 30, 60, 90, 120, 150, 180};
        for (int i = 0; i < ROWS.length; i++) {
            clear(a);
            a.items().setStackInSlot(0, head(h, ROWS[i], true));
            h.assertValueEqual(a.potential(), expect[i], "a promoted " + ROWS[i].getDescription().getString());
        }
        // three promoted cows at 15, a fourth at the plain 10
        clear(a);
        for (int i = 0; i < 4; i++) a.items().setStackInSlot(i, head(h, EntityType.COW, true));
        h.assertValueEqual(a.potential(), 15 * 3 + 10, "four promoted cows");
        // nine promoted evokers: 3 x 180 + 6 x 120 = 1,260, at a meeting 1,386
        clear(a);
        for (int i = 0; i < 9; i++) a.items().setStackInSlot(i, head(h, EntityType.EVOKER, true));
        h.assertValueEqual(a.potential(), Math.round((3 * 180 + 6 * 120) * 1.1f), "nine promoted evokers");
        h.assertValueEqual(Math.round((3 * 180 + 6 * 120) * 1.1f), 1386, "the book's number");
        // cap 0, or bonus 100: today's numbers
        int cap = Config.PROMOTED_ARRAY_CAP.get(), bonus = Config.PROMOTED_ARRAY_BONUS.get();
        try {
            Config.PROMOTED_ARRAY_CAP.set(0);
            h.assertValueEqual(a.potential(), Math.round(9 * 120 * 1.1f), "cap 0");
            Config.PROMOTED_ARRAY_CAP.set(cap);
            Config.PROMOTED_ARRAY_BONUS.set(100);
            h.assertValueEqual(a.potential(), Math.round(9 * 120 * 1.1f), "bonus 100");
        } finally {
            Config.PROMOTED_ARRAY_CAP.set(cap);
            Config.PROMOTED_ARRAY_BONUS.set(bonus);
        }
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void arrayTableUnchanged(GameTestHelper h) {
        CerebralArrayBlockEntity a = array(h);
        for (int i = 0; i < ROWS.length; i++) {
            h.assertValueEqual(HeadJobs.arrayOutput(ROWS[i]), TABLE[i], "the table for " + ROWS[i].getDescription().getString());
            clear(a);
            a.items().setStackInSlot(0, head(h, ROWS[i], false));
            h.assertValueEqual(a.potential(), TABLE[i], "an unpromoted " + ROWS[i].getDescription().getString());
        }
        // a meeting of five, none promoted: +10% as in 0.5.4
        clear(a);
        for (int i = 0; i < 5; i++) a.items().setStackInSlot(i, head(h, EntityType.ZOMBIE, false));
        h.assertValueEqual(a.potential(), Math.round(100 * 1.1f), "five zombies");
        // the Dragon Head and an unfiled old head (no file at all) make what they always made
        clear(a);
        Mob cow = h.spawn(EntityType.COW, FLOOR.above(3));
        ItemStack old = JarredHeadItem.of(cow);
        cow.discard();
        a.items().setStackInSlot(0, old);
        a.items().setStackInSlot(1, new ItemStack(Items.DRAGON_HEAD));
        h.assertValueEqual(a.potential(), 10, "an old head and a dragon head without the core");
        h.succeed();
    }
}
