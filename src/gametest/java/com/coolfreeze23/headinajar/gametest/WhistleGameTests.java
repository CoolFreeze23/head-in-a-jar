package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.Detail;
import com.coolfreeze23.headinajar.staff.work.Shuttle;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The Supervisor's Whistle (0.7.0): a detail of six and no more; Follow keeps four blocks and Hold holds; standing down
 * walks home or takes the shuttle; the catch-up shuttle; portals, five minutes away, logging out, dying; pets stay and
 * fighters fight; the shuttle never loads a chunk or makes two of anyone; the pets' gifts; a jar's Speaker Grille.
 * <p>
 * {@code platform32}: 32 x 10 x 32, a grass floor whose top is helper y 5; creatures stand at y 6. Staff are hired with
 * the Lanyard (a flyer, whose movement family is not driven yet, straight through {@code Workforce.hire}) and put on
 * the detail with the Whistle's own Use (the EntityInteract event).
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class WhistleGameTests {
    private static final int Y = 6;

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        return SawTableGameTests.player(h, at);
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        SawTableGameTests.done(h, players);
    }

    private static BlockPos at(int x, int z) {
        return new BlockPos(x, Y, z);
    }

    /** A creature hired by {@code p} with a Lanyard (hostiles worn down to half first). */
    private static <T extends Mob> T staff(GameTestHelper h, ServerPlayer p, EntityType<T> type, BlockPos at) {
        T mob = h.spawn(type, at);
        if (mob instanceof Enemy) mob.setHealth(mob.getMaxHealth() * 0.5f - 0.5f);
        if (!Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), mob)) Workforce.hire(p, mob);
        h.assertTrue(Workforce.isWorker(mob), "setup: " + type.getDescriptionId() + " was not hired");
        return mob;
    }

    /** Use with the Whistle on a creature, as the click reaches the server. */
    private static InteractionResult whistleOn(ServerPlayer p, Entity e) {
        ItemStack whistle = new ItemStack(WorkRegistry.SUPERVISORS_WHISTLE.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, whistle);
        p.getCooldowns().removeCooldown(whistle.getItem());
        return CommonHooks.onInteractEntity(p, e, InteractionHand.MAIN_HAND);
    }

    /** Use with the Whistle in the air, as the click reaches the server. */
    private static void blow(ServerPlayer p, boolean sneak) {
        ItemStack whistle = new ItemStack(WorkRegistry.SUPERVISORS_WHISTLE.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, whistle);
        p.getCooldowns().removeCooldown(whistle.getItem());
        p.setShiftKeyDown(sneak);
        p.gameMode.useItem(p, p.serverLevel(), whistle, InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false);
    }

    private static void join(GameTestHelper h, ServerPlayer p, Mob... mobs) {
        for (Mob mob : mobs) {
            whistleOn(p, mob);
            h.assertTrue(Detail.on(mob, p), "setup: " + mob.getType().getDescriptionId() + " did not join the detail");
        }
    }

    private static TimeClockBlockEntity clock(GameTestHelper h, ServerPlayer p, BlockPos at) {
        h.setBlock(at, WorkRegistry.TIME_CLOCK.get());
        TimeClockBlockEntity clock = (TimeClockBlockEntity) h.getBlockEntity(at);
        clock.setOwner(p);
        return clock;
    }

    private static void moveTo(GameTestHelper h, Entity e, BlockPos at) {
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        e.moveTo(v.x, v.y, v.z, e.getYRot(), e.getXRot());
    }

    private static int count(ServerLevel level, UUID id) {
        int n = 0;
        for (Entity e : level.getAllEntities()) if (e.getUUID().equals(id) && !e.isRemoved()) n++;
        return n;
    }

    // --- the detail -------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void aDetailTakesSixAndNoMore(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 4));
        Cow[] cows = new Cow[7];
        for (int i = 0; i < 7; i++) cows[i] = staff(h, p, EntityType.COW, at(4 + i * 3, 12));
        for (int i = 0; i < 6; i++) {
            h.assertTrue(whistleOn(p, cows[i]) == InteractionResult.SUCCESS, "the whistle's use did not answer");
            h.assertTrue(Detail.on(cows[i], p), "staff no. " + (i + 1) + " did not join");
        }
        whistleOn(p, cows[6]);
        h.assertFalse(Detail.on(cows[6], p), "a seventh joined a detail of six");
        h.assertTrue("whistle.full".equals(Refusals.lastDelivered(p)), "the seventh was not refused as full: " + Refusals.lastDelivered(p));
        h.assertTrue(Detail.members(p).size() == 6, "the roll holds " + Detail.members(p).size());
        // again on one of them: it leaves (no site: it awaits one), and the seventh fits
        whistleOn(p, cows[0]);
        h.assertFalse(Detail.on(cows[0], p), "the second use did not take it off the detail");
        h.assertTrue(Workforce.data(cows[0]).state == WorkerData.State.AWAITING, "off the detail with no site, it is " + Workforce.data(cows[0]).state);
        whistleOn(p, cows[6]);
        h.assertTrue(Detail.on(cows[6], p), "the seventh did not join once there was room");
        // a creature not on staff, and someone else's staff
        Cow stray = h.spawn(EntityType.COW, at(20, 20));
        whistleOn(p, stray);
        h.assertTrue("whistle.not_staff".equals(Refusals.lastDelivered(p)), "a stray cow was not refused: " + Refusals.lastDelivered(p));
        ServerPlayer q = player(h, at(26, 4));
        Cow theirs = staff(h, q, EntityType.COW, at(26, 20));
        whistleOn(p, theirs);
        h.assertTrue("staff.reports_to".equals(Refusals.lastDelivered(p)), "someone else's staff was not refused: " + Refusals.lastDelivered(p));
        h.assertFalse(Detail.on(theirs, p), "someone else's staff joined");
        done(h, p, q);
    }

    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void followKeepsFourBlocks(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        Husk husk = staff(h, p, EntityType.HUSK, at(16, 26));
        join(h, p, husk);
        long[] close = {-1};
        h.onEachTick(() -> {
            double dist = husk.distanceTo(p);
            if (close[0] < 0 && dist <= Detail.FOLLOW_NEAR + 0.5) close[0] = h.getTick();
            if (close[0] < 0) return;
            if (h.getTick() > close[0] + 20 && dist <= Detail.FOLLOW_NEAR && !husk.getNavigation().isDone()) {
                h.fail("it is still walking " + String.format("%.1f", dist) + " from the player");
            }
            if (dist > 6.5) h.fail("having caught up, it fell " + String.format("%.1f", dist) + " behind a player standing still");
            if (h.getTick() > close[0] + 80) done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 500)
    public static void holdHolds(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        Husk husk = staff(h, p, EntityType.HUSK, at(16, 9));
        join(h, p, husk);
        blow(p, false);
        h.assertTrue(Detail.mode(p) == Detail.Mode.HOLD, "one use in the air did not set Hold");
        Vec3 was = husk.position();
        moveTo(h, p, at(16, 26));
        h.runAfterDelay(120, () -> {
            h.assertTrue(husk.position().distanceTo(was) < 0.5, "on Hold it moved " + String.format("%.2f", husk.position().distanceTo(was)));
            blow(p, false);
            h.assertTrue(Detail.mode(p) == Detail.Mode.FOLLOW, "a second use did not set Follow");
            h.succeedWhen(() -> {
                h.assertTrue(husk.distanceTo(p) <= 6.5, "on Follow it has not come: " + String.format("%.1f", husk.distanceTo(p)));
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 900)
    public static void standDownWalksHomeOrShuttles(GameTestHelper h) {
        ServerPlayer p = player(h, at(28, 24));
        TimeClockBlockEntity clock = clock(h, p, at(2, 2));
        // one 37 blocks from its clock (outside the site's box, within 48): it walks
        Husk near = staff(h, p, EntityType.HUSK, at(28, 28));
        clock.join(near);
        // one whose clock is far off in a chunk nobody has loaded: the shuttle
        Husk far = staff(h, p, EntityType.HUSK, at(24, 28));
        BlockPos away = h.absolutePos(at(2, 2)).offset(30000, 0, 30000);
        Workforce.data(far).clock = GlobalPos.of(h.getLevel().dimension(), away);
        join(h, p, near, far);
        UUID farId = far.getUUID();
        blow(p, true);
        h.assertTrue(Detail.members(p).isEmpty(), "Sneak + Use left " + Detail.members(p).size() + " on the detail");
        h.assertTrue(far.isRemoved() && Shuttle.aboard(h.getLevel().getServer(), farId), "the far one did not take the shuttle");
        h.assertFalse(h.getLevel().hasChunk(away.getX() >> 4, away.getZ() >> 4), "the shuttle loaded the far clock's chunk");
        h.succeedWhen(() -> {
            WorkerData d = Workforce.data(near);
            h.assertTrue(!near.isRemoved() && d.state == WorkerData.State.TO_CLOCK, "the near one has not walked into its site: " + d.state
                    + " at " + near.blockPosition());
            h.assertTrue(Workforce.inBox(clock.getBlockPos(), near.blockPosition()), "it is the site's again outside the box");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 300)
    public static void theCatchUpShuttleArrivesInFiveSeconds(GameTestHelper h) {
        ServerPlayer p = player(h, at(26, 26));
        Husk husk = staff(h, p, EntityType.HUSK, at(1, 1));
        join(h, p, husk);
        UUID id = husk.getUUID();
        ServerLevel level = h.getLevel();
        long[] gone = {-1}, back = {-1};
        h.onEachTick(() -> {
            Entity e = level.getEntity(id);
            if (gone[0] < 0 && e == null) gone[0] = level.getGameTime();
            if (gone[0] >= 0 && back[0] < 0 && e != null) {
                back[0] = level.getGameTime();
                long trip = back[0] - gone[0];
                h.assertTrue(trip >= Shuttle.CATCH_UP_TICKS - 2 && trip <= Shuttle.CATCH_UP_TICKS + 2, "the trip took " + trip + " ticks");
                h.assertTrue(e != husk && husk.isRemoved(), "the old one is still in the world");
                h.assertTrue(e.distanceTo(p) <= 4.5, "set down " + String.format("%.1f", e.distanceTo(p)) + " from the player");
                h.assertTrue(Detail.on(e, p), "it came back off the detail");
                h.assertTrue(count(level, id) == 1, "there are " + count(level, id) + " of it");
                done(h, p);
            }
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 300)
    public static void aDetailNeverFollowsThroughAPortal(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        Husk husk = staff(h, p, EntityType.HUSK, at(16, 12));
        join(h, p, husk);
        // a member never changes dimension
        var travel = NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(husk, Level.NETHER));
        h.assertTrue(travel.isCanceled(), "a member was let through a portal");
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "setup: no Nether");
        Vec3 was = husk.position();
        p.teleportTo(nether, 0.5, 70, 0.5, 0, 0);
        h.assertTrue(Detail.mode(p) == Detail.Mode.HOLD, "going through a portal did not set Hold");
        h.runAfterDelay(80, () -> {
            h.assertTrue(husk.level() == h.getLevel() && !husk.isRemoved(), "it followed into the Nether");
            h.assertTrue(husk.position().distanceTo(was) < 0.5, "it moved without its player");
            h.assertTrue("staff.headinajar.say.portal".equals(Workforce.data(husk).status), "it did not say it waits at the portal: "
                    + Workforce.data(husk).status);
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 300)
    public static void fiveMinutesAwaySendsThemHome(GameTestHelper h) {
        ServerPlayer p = player(h, at(29, 29));
        TimeClockBlockEntity clock = clock(h, p, at(2, 2));
        Husk husk = staff(h, p, EntityType.HUSK, at(4, 4));
        clock.join(husk);
        join(h, p, husk);
        blow(p, false);
        h.runAfterDelay(40, () -> {
            h.assertTrue(Detail.away(husk) >= 30, "40 ticks more than 32 away counted " + Detail.away(husk));
            // the rest of the five minutes
            Detail.setAway(husk, Detail.AWAY_TICKS - 20);
        });
        h.runAfterDelay(80, () -> {
            h.assertTrue(Detail.members(p).isEmpty(), "after five minutes away it is still on the detail");
            h.assertTrue(Workforce.data(husk).state == WorkerData.State.TO_CLOCK, "it is not back at its site: " + Workforce.data(husk).state);
            h.assertTrue(Memos.told(p, Memos.Memo.DETAIL_HOME) > 0, "DOLORES did not say the detail went home");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 300)
    public static void logOutHolds(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        Husk husk = staff(h, p, EntityType.HUSK, at(16, 24));
        join(h, p, husk);
        h.getLevel().getServer().getPlayerList().remove(p);
        h.assertTrue(Detail.mode(p) == Detail.Mode.HOLD, "logging out did not set Hold");
        Vec3 was = husk.position();
        h.runAfterDelay(100, () -> {
            h.assertTrue(husk.position().distanceTo(was) < 0.5, "with its player logged out it moved");
            h.assertTrue(Workforce.data(husk).state == WorkerData.State.DETAIL && p.getUUID().equals(Workforce.data(husk).detail),
                    "it left the detail when its player logged out");
            h.succeed();
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void whenYouDieTheDetailGoesHome(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        TimeClockBlockEntity clock = clock(h, p, at(2, 2));
        Husk husk = staff(h, p, EntityType.HUSK, at(8, 8));
        clock.join(husk);
        join(h, p, husk);
        p.kill();
        h.assertTrue(Detail.members(p).isEmpty(), "the detail outlived its supervisor");
        h.assertTrue(Memos.told(p, Memos.Memo.DETAIL_HOME) > 0, "DOLORES did not say the detail went home");
        h.succeedWhen(() -> {
            h.assertTrue(Workforce.data(husk).state == WorkerData.State.TO_CLOCK, "it is not back at its site: " + Workforce.data(husk).state);
            done(h, p);
        });
    }

    // --- fighting ---------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void petsDoNotFight(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        Husk husk = staff(h, p, EntityType.HUSK, at(14, 10));
        Cat cat = staff(h, p, EntityType.CAT, at(16, 10));
        Wolf wolf = staff(h, p, EntityType.WOLF, at(18, 10));
        join(h, p, husk, cat, wolf);
        Husk target = h.spawn(EntityType.HUSK, at(16, 22));
        target.setNoAi(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WorkRegistry.SUPERVISORS_WHISTLE.get()));
        h.assertFalse(CommonHooks.onPlayerAttackTarget(p, target), "an attack with the whistle struck the target itself");
        h.assertTrue(target.getUUID().equals(Detail.target(husk)), "the fighter was not sent");
        h.assertTrue(Detail.target(cat) == null && Detail.target(wolf) == null, "a pet was sent");
        long now = h.getLevel().getGameTime();
        h.assertTrue(now - Workforce.data(cat).lastSaid < 5 && now - Workforce.data(wolf).lastSaid < 5, "a pet did not say it stays");
        h.succeedWhen(() -> {
            h.assertTrue(target.getHealth() < target.getMaxHealth(), "the fighter has not struck the target");
            h.assertTrue(cat.distanceTo(p) < 7 && wolf.distanceTo(p) < 7, "a pet went off after the target");
            h.assertTrue(cat.distanceTo(target) > 5 && wolf.distanceTo(target) > 5, "a pet closed on the target");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 500)
    public static void fightersAttackWhatYouPointAt(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        Husk husk = staff(h, p, EntityType.HUSK, at(14, 9));
        Spider spider = staff(h, p, EntityType.SPIDER, at(18, 9));
        join(h, p, husk, spider);
        // a villager is not fair game, nor is thin air
        Villager villager = h.spawn(EntityType.VILLAGER, at(4, 20));
        villager.setNoAi(true);
        Detail.point(p, villager.getId());
        h.assertTrue("whistle.friendly".equals(Refusals.lastDelivered(p)), "pointing at a villager was not refused: " + Refusals.lastDelivered(p));
        Detail.point(p, -1);
        h.assertTrue("whistle.nothing".equals(Refusals.lastDelivered(p)), "pointing at nothing was not refused: " + Refusals.lastDelivered(p));
        h.assertTrue(Detail.target(husk) == null && Detail.target(spider) == null, "a refused point sent someone");
        Husk target = h.spawn(EntityType.HUSK, at(16, 24));
        target.setNoAi(true);
        Detail.point(p, target.getId());
        h.succeedWhen(() -> {
            h.assertTrue(!target.isAlive(), "the target is still standing: " + target.getHealth());
            h.assertTrue(villager.isAlive() && villager.getHealth() == villager.getMaxHealth(), "the villager was hurt");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void fightersAnswerWhatHurtsYou(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        Husk husk = staff(h, p, EntityType.HUSK, at(14, 9));
        Cat cat = staff(h, p, EntityType.CAT, at(18, 9));
        join(h, p, husk, cat);
        Husk attacker = h.spawn(EntityType.HUSK, at(16, 16));
        attacker.setNoAi(true);
        NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(p, new DamageContainer(p.damageSources().mobAttack(attacker), 2)));
        h.assertTrue(attacker.getUUID().equals(Detail.target(husk)), "the fighter did not answer who hurt its player");
        h.assertTrue(Detail.target(cat) == null, "the pet answered");
        done(h, p);
    }

    // --- the shuttle ------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void theShuttleHomeNeverLoadsAChunk(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        Cow cow = staff(h, p, EntityType.COW, at(16, 10));
        BlockPos far = h.absolutePos(at(16, 10)).offset(-30000, 0, 30000);
        ServerLevel level = h.getLevel();
        h.assertFalse(level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "setup: the far chunk is loaded");
        Workforce.data(cow).clock = GlobalPos.of(level.dimension(), far);
        join(h, p, cow);
        UUID id = cow.getUUID();
        whistleOn(p, cow);
        h.assertTrue(cow.isRemoved() && Shuttle.aboard(level.getServer(), id), "it did not take the shuttle home");
        h.runAfterDelay(60, () -> {
            h.assertFalse(level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "the shuttle loaded its clock's chunk");
            h.assertTrue(Shuttle.aboard(level.getServer(), id), "the record was lost while its clock is not loaded");
            h.assertTrue(count(level, id) == 0, "it is in the world and in the shuttle");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 600)
    public static void aShuttledMemberIsNeverDuplicated(GameTestHelper h) {
        ServerPlayer p = player(h, at(24, 24));
        TimeClockBlockEntity clock = clock(h, p, at(2, 2));
        // walled in 37 blocks from its clock: no way home on foot, so the shuttle takes it
        for (int x = 27; x <= 29; x++) for (int z = 27; z <= 29; z++) for (int y = Y; y <= Y + 2; y++) {
            if (x != 28 || z != 28 || y == Y + 2) h.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
        }
        Husk husk = staff(h, p, EntityType.HUSK, at(28, 28));
        clock.join(husk);
        join(h, p, husk);
        UUID id = husk.getUUID();
        whistleOn(p, husk);
        ServerLevel level = h.getLevel();
        boolean[] again = {false};
        long[] at = {-1};
        h.onEachTick(() -> {
            Entity e = level.getEntity(id);
            if (!husk.isRemoved() || e == null) return;
            h.assertTrue(count(level, id) == 1, "there are " + count(level, id) + " of it");
            if (!again[0]) {
                h.assertTrue(Workforce.data(e).state == WorkerData.State.TO_CLOCK, "set down beside its clock as " + Workforce.data(e).state);
                h.assertTrue(e.distanceToSqr(Vec3.atCenterOf(clock.getBlockPos())) < 9, "not set down beside its clock");
                h.assertTrue(clock.arrivals().isEmpty(), "its record is still in the arrivals");
                // the same record again while it stands there: never set down twice
                CompoundTag copy = new CompoundTag();
                e.saveAsPassenger(copy);
                clock.arrive(copy);
                again[0] = true;
                at[0] = level.getGameTime();
            } else if (level.getGameTime() > at[0] + 10) {
                h.assertTrue(clock.arrivals().isEmpty(), "a record of someone in the world waits in the arrivals");
                done(h, p);
            }
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void aRecordWaitsForItsClockAndIsNeverLost(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        TimeClockBlockEntity clock = clock(h, p, at(4, 4));
        Husk husk = staff(h, p, EntityType.HUSK, at(20, 20));
        clock.join(husk);
        Cow cow = staff(h, p, EntityType.COW, at(24, 20));
        ServerLevel level = h.getLevel();
        // records held while their clocks were not loaded: one whose clock is there now, one whose clock was broken
        UUID huskId = husk.getUUID(), cowId = cow.getUUID();
        CompoundTag a = new CompoundTag(), b = new CompoundTag();
        husk.saveAsPassenger(a);
        husk.discard();
        BlockPos gone = h.absolutePos(at(26, 8));
        Workforce.data(cow).clock = GlobalPos.of(level.dimension(), gone);
        cow.saveAsPassenger(b);
        cow.discard();
        Shuttle.data(level.getServer()).hold(clock.site(), a);
        Shuttle.data(level.getServer()).hold(GlobalPos.of(level.dimension(), gone), b);
        h.succeedWhen(() -> {
            Entity back = level.getEntity(huskId), other = level.getEntity(cowId);
            h.assertTrue(back != null && Workforce.data(back).state == WorkerData.State.TO_CLOCK, "the held record did not reach its clock");
            h.assertTrue(back.distanceToSqr(Vec3.atCenterOf(clock.getBlockPos())) < 9, "not set down beside its clock");
            h.assertTrue(other != null && Workforce.data(other).state == WorkerData.State.AWAITING && Workforce.data(other).clock == null,
                    "the one whose clock is gone was not set down where it stood, awaiting a new one");
            h.assertFalse(Shuttle.aboard(level.getServer(), huskId) || Shuttle.aboard(level.getServer(), cowId), "a record is still held");
            h.assertTrue(count(level, huskId) == 1 && count(level, cowId) == 1, "two of someone");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void aConvertedMemberKeepsItsPlace(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        Cow first = staff(h, p, EntityType.COW, at(14, 9));
        Husk husk = staff(h, p, EntityType.HUSK, at(18, 9));
        join(h, p, first, husk);
        // as vanilla turns a husk into a zombie under water: a new creature with a new UUID
        var zombie = husk.convertTo(EntityType.ZOMBIE, true);
        net.neoforged.neoforge.event.EventHooks.onLivingConvert(husk, zombie);
        h.assertTrue(zombie != null && Workforce.isWorker(zombie), "setup: the zombie is not on staff");
        h.assertTrue(Detail.members(p).equals(List.of(first.getUUID(), zombie.getUUID())), "the roll did not follow the conversion: " + Detail.members(p));
        h.assertTrue(Detail.on(zombie, p), "the converted member is off the detail");
        done(h, p);
    }

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void recallShuttlesTheFarHome(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        TimeClockBlockEntity clock = clock(h, p, at(4, 4));
        Husk near = staff(h, p, EntityType.HUSK, at(8, 8));
        clock.join(near);
        Husk far = staff(h, p, EntityType.HUSK, at(20, 20));
        BlockPos away = h.absolutePos(at(4, 4)).offset(30000, 0, 30000);
        Workforce.data(far).clock = GlobalPos.of(h.getLevel().dimension(), away);
        join(h, p, near, far);
        UUID farId = far.getUUID();
        h.assertTrue(com.coolfreeze23.headinajar.command.StaffCommands.recall(near), "the near one was not recalled");
        h.assertTrue(com.coolfreeze23.headinajar.command.StaffCommands.recall(far), "the far one was not recalled");
        h.assertTrue(Detail.members(p).isEmpty(), "recalled staff are still on the detail: " + Detail.members(p));
        h.assertTrue(!near.isRemoved() && Workforce.data(near).state == WorkerData.State.TO_CLOCK, "the near one did not walk back");
        h.assertTrue(far.isRemoved() && Shuttle.aboard(h.getLevel().getServer(), farId), "the far one did not take the shuttle");
        done(h, p);
    }

    // --- the pets ---------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void aParrotRidesYourShoulder(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        // vanilla seats a shoulder rider only on a player standing on the ground (a test player is not ticked)
        p.setOnGround(true);
        Parrot parrot = staff(h, p, EntityType.PARROT, at(16, 7));
        join(h, p, parrot);
        UUID id = parrot.getUUID();
        ServerLevel level = h.getLevel();
        long[] landed = {-1};
        h.onEachTick(() -> {
            if (landed[0] < 0) {
                if (!parrot.isRemoved()) return;
                h.assertTrue(Detail.onShoulder(level.getServer(), id), "it left the world but is not on the shoulder");
                h.assertTrue(Detail.members(p).contains(id), "on the shoulder it left the roll");
                landed[0] = level.getGameTime();
            } else if (level.getGameTime() == landed[0] + 30) {
                blow(p, true);
                Entity back = level.getEntity(id);
                h.assertTrue(back != null && !Detail.onShoulder(level.getServer(), id), "standing down did not take it off the shoulder");
                WorkerData d = Workforce.data(back);
                h.assertTrue(d != null, "off the shoulder it is not on staff");
            } else if (level.getGameTime() > landed[0] + 50) {
                WorkerData d = Workforce.data(level.getEntity(id));
                h.assertTrue(d != null && d.detail == null && d.state == WorkerData.State.AWAITING, "stood down it is still " + (d == null ? null : d.state));
                done(h, p);
            }
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void aFoxBringsYouWhatItFinds(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        Fox fox = staff(h, p, EntityType.FOX, at(16, 9));
        join(h, p, fox);
        Vec3 drop = h.absoluteVec(new Vec3(21.5, Y + 0.2, 12.5));
        ItemEntity found = new ItemEntity(h.getLevel(), drop.x, drop.y, drop.z, new ItemStack(Items.DIAMOND));
        found.setNoPickUpDelay();
        h.getLevel().addFreshEntity(found);
        h.succeedWhen(() -> {
            List<ItemEntity> brought = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3),
                    ie -> ie.getItem().is(Items.DIAMOND) && ie.getOwner() == fox);
            h.assertFalse(brought.isEmpty(), "the fox has not brought the diamond (in its mouth: " + fox.getMainHandItem() + ")");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void aWolfSitsByYourBody(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        Wolf wolf = staff(h, p, EntityType.WOLF, at(16, 20));
        join(h, p, wolf);
        FormActions.detach(p, 3);
        HeadlessBody body = (HeadlessBody) h.getLevel().getEntity(FormActions.state(p).body());
        h.assertTrue(body != null, "setup: no body");
        moveTo(h, p, at(16, 18));
        h.succeedWhen(() -> {
            h.assertTrue(wolf.distanceTo(body) <= 2.6, "the wolf is " + String.format("%.1f", wolf.distanceTo(body)) + " from the body");
            h.assertTrue(wolf.isInSittingPose(), "the wolf is not sitting by the body");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 300)
    public static void aCatKeepsCreepersOff(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        Cat cat = staff(h, p, EntityType.CAT, at(16, 8));
        join(h, p, cat);
        Creeper creeper = h.spawn(EntityType.CREEPER, at(16, 11));
        h.succeedWhen(() -> {
            h.assertTrue(creeper.isAlive() && creeper.getSwelling(1) == 0, "the creeper went off");
            h.assertTrue(creeper.distanceTo(p) > 8, "the creeper is " + String.format("%.1f", creeper.distanceTo(p)) + " from the player");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void awayDayForSixOutOfTheirSite(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        BlockPos far = h.absolutePos(at(16, 6)).offset(30000, 0, -30000);
        for (int i = 0; i < 6; i++) {
            Cow cow = staff(h, p, EntityType.COW, at(10 + i * 2, 10));
            Workforce.data(cow).clock = GlobalPos.of(h.getLevel().dimension(), far);
            join(h, p, cow);
        }
        h.succeedWhen(() -> {
            h.assertTrue(SawTableGameTests.earned(h, p, "away_day"), "six out of their site did not earn Away Day");
            done(h, p);
        });
    }

    // --- a jar ------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void aJarWhistlesOnlyThroughASpeakerGrille(GameTestHelper h) {
        ServerPlayer p = player(h, at(16, 6));
        Husk husk = staff(h, p, EntityType.HUSK, at(16, 10));
        join(h, p, husk);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.state(p).form() == Form.JAR, "setup: not a jar");
        blow(p, false);
        h.assertTrue("whistle.jar".equals(Refusals.lastDelivered(p)), "a bare jar whistled: " + Refusals.lastDelivered(p));
        h.assertTrue(Detail.mode(p) == Detail.Mode.FOLLOW, "a bare jar's whistle changed the detail");
        whistleOn(p, husk);
        h.assertTrue(Detail.on(husk, p), "a bare jar's whistle on its staff did something");
        FormActions.state(p).setModule(0, new ItemStack(ModRegistry.module(ModuleType.SPEAKER_GRILLE)));
        blow(p, false);
        h.assertTrue(Detail.mode(p) == Detail.Mode.HOLD, "the grille did not whistle Hold");
        whistleOn(p, husk);
        h.assertFalse(Detail.on(husk, p), "the grille's whistle on its staff did not stand it down");
        done(h, p);
    }
}
