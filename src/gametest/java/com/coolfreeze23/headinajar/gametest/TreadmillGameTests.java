package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.GalvanicPileBlockEntity;
import com.coolfreeze23.headinajar.block.PowerBlockEntities;
import com.coolfreeze23.headinajar.block.TreadmillBlock;
import com.coolfreeze23.headinajar.block.TreadmillBlockEntity;
import com.coolfreeze23.headinajar.block.TreadmillRate;
import com.coolfreeze23.headinajar.block.Treadmills;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.host.HostControl;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 0.6.3, the Corporate Wellness Treadmill: what each body makes, where it goes, how a walker is held, who is told. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class TreadmillGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);
    private static final String BATCH = "treadmill";

    // the company's table and its extra rows for the other bodies: FE/t, or the refusal
    private static final Map<String, Object> TABLE = new HashMap<>();

    static {
        rows(2, "silverfish", "endermite");
        rows(4, "skeleton", "stray", "bogged", "turtle", "wither_skeleton", "skeleton_horse");
        rows(6, "zombie", "husk", "drowned", "zombified_piglin", "cat", "armadillo", "zombie_villager", "zombie_horse", "snow_golem");
        rows(8, "pig", "sheep", "fox", "creeper", "cave_spider", "frog", "rabbit", "ocelot");
        rows(10, "cow", "mooshroom", "villager", "goat", "wolf", "witch", "pillager", "vindicator", "evoker", "piglin",
                "wandering_trader", "piglin_brute", "illusioner");
        rows(12, "chicken", "panda");
        rows(14, "llama", "spider", "trader_llama");
        rows(16, "donkey", "enderman");
        rows(18, "mule", "polar_bear");
        rows(20, "horse", "sniffer");
        rows(24, "camel");
        rows("flier", "bat", "phantom", "parrot", "allay", "vex", "blaze", "breeze", "bee");
        rows("ghast", "ghast");
        rows("swimmer", "cod", "salmon", "tropical_fish", "pufferfish", "squid", "glow_squid", "dolphin", "axolotl", "guardian",
                "elder_guardian", "tadpole");
        rows("strider", "strider");
        rows("heavy", "iron_golem", "ravager", "warden", "hoglin", "zoglin");
        rows("bounce", "slime", "magma_cube");
    }

    private static void rows(Object what, String... names) {
        for (String n : names) TABLE.put("minecraft:" + n, what);
    }

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(v.x, v.y, v.z, 0, 0);
        return player;
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** A belt with its foot at {@code foot} (relative), the head toward {@code facing}. */
    private static TreadmillBlockEntity belt(GameTestHelper h, BlockPos foot, Direction facing) {
        BlockState state = ModRegistry.TREADMILL.get().defaultBlockState().setValue(TreadmillBlock.FACING, facing);
        h.setBlock(foot, state);
        h.setBlock(foot.relative(facing), state.setValue(TreadmillBlock.PART, TreadmillBlock.Part.HEAD));
        return (TreadmillBlockEntity) h.getBlockEntity(foot);
    }

    private static TreadmillBlockEntity belt(GameTestHelper h, BlockPos foot) {
        return belt(h, foot, Direction.NORTH);
    }

    /** A host made the way the saw does, standing on the belt's seam, clocked in. */
    private static <T extends Mob> T walker(GameTestHelper h, TreadmillBlockEntity belt, EntityType<T> type, ServerPlayer sawyer) {
        Vec3 seam = belt.seam();
        T mob = h.spawn(type, h.relativeVec(seam));
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "the saw failed on " + type);
        mob.setHealth(mob.getMaxHealth());
        mob.moveTo(seam.x, seam.y, seam.z, 0, 0);
        belt.check();
        h.assertTrue(belt.walker() == mob, "the belt did not clock in the " + type);
        return mob;
    }

    /** A body of any species marked a host directly (the saw's rules about who are not what is tested). */
    private static Mob bareHost(GameTestHelper h, TreadmillBlockEntity belt, EntityType<?> type) {
        Vec3 seam = belt.seam();
        Entity e = type.create(h.getLevel());
        if (!(e instanceof Mob mob)) throw new GameTestAssertException(type + " is not a mob");
        mob.moveTo(seam.x, seam.y, seam.z, 0, 0);
        mob.setPersistenceRequired();
        h.getLevel().addFreshEntity(mob);
        HostData data = Hosts.data(mob);
        data.host = true;
        data.freshness = 100;
        return mob;
    }

    private static boolean earned(GameTestHelper h, ServerPlayer p, String id) {
        AdvancementHolder holder = h.getLevel().getServer().getAdvancements().get(HeadInAJar.id(id));
        if (holder == null) throw new GameTestAssertException("no advancement " + id);
        return p.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static void near(GameTestHelper h, double got, double want, double slack, String what) {
        h.assertTrue(Math.abs(got - want) <= slack, what + ": " + got + ", wanted " + want + " +- " + slack);
    }

    // --- FE ------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 260)
    public static void treadmillCowMakesTenBesideAPileMakingTwenty(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        TreadmillBlockEntity belt = belt(h, new BlockPos(3, 2, 4));
        walker(h, belt, EntityType.COW, p);
        h.setBlock(new BlockPos(11, 2, 7), ModRegistry.GALVANIC_PILE.get());
        GalvanicPileBlockEntity pile = (GalvanicPileBlockEntity) h.getBlockEntity(new BlockPos(11, 2, 7));
        pile.fuel().insertItem(0, new ItemStack(Items.ROTTEN_FLESH, 2), false);
        h.runAfterDelay(200, () -> {
            near(h, belt.energy().getEnergyStored(), 2_000, 10, "a cow's 200 ticks");
            near(h, pile.energy().getEnergyStored(), 4_000, 20, "a Pile's 200 ticks");
            done(h, p);
        });
    }

    /** Every row of the data map and the default for a land body with no row, one walker each on the real belt. */
    @GameTest(template = "platform", batch = BATCH)
    public static void everyTreadmillRateInTheTable(GameTestHelper h) {
        TreadmillBlockEntity belt = belt(h, FLOOR);
        // the data map holds exactly the table (the new arrivals' rows, there only when the supplier is, are tested with them)
        Set<String> rows = new HashSet<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) if (TreadmillRate.of(type) != null) rows.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        rows.removeAll(com.coolfreeze23.headinajar.supplier.Supplier.NEW_ARRIVALS);
        h.assertTrue(rows.equals(TABLE.keySet()), "the data map's rows differ from the table: " + rows.size() + " vs " + TABLE.size());
        Map<String, Object> table = new HashMap<>(TABLE);
        // the default: a land body with no row (no vanilla walker is left without one; a shulker stands in for a modded host)
        table.put("minecraft:shulker", 8);
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Object> row : table.entrySet()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(row.getKey()));
            Mob mob = bareHost(h, belt, type);
            belt.check();
            if (row.getValue() instanceof Integer fe) {
                if (belt.walker() != mob || Math.abs(TreadmillBlockEntity.rate(mob) - fe) > 1e-9) wrong.add(row.getKey() + " " + TreadmillBlockEntity.rate(mob));
            } else if (belt.walker() != null || !row.getValue().equals(TreadmillBlockEntity.verdict(mob).refuse())) {
                wrong.add(row.getKey() + " " + TreadmillBlockEntity.verdict(mob));
            }
            mob.discard();
            belt.check();
        }
        h.assertTrue(wrong.isEmpty(), "wrong on the belt: " + wrong);
        h.assertTrue(belt.walker() == null, "the last body is still clocked in");
        h.succeed();
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 160)
    public static void camelOnTheBeltOutearnsThePile(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        TreadmillBlockEntity belt = belt(h, new BlockPos(3, 2, 4));
        walker(h, belt, EntityType.CAMEL, p);
        h.setBlock(new BlockPos(11, 2, 7), ModRegistry.GALVANIC_PILE.get());
        GalvanicPileBlockEntity pile = (GalvanicPileBlockEntity) h.getBlockEntity(new BlockPos(11, 2, 7));
        pile.fuel().insertItem(0, new ItemStack(Items.ROTTEN_FLESH, 1), false);
        h.runAfterDelay(100, () -> {
            near(h, belt.energy().getEnergyStored(), 2_400, 24, "a camel's 100 ticks");
            near(h, pile.energy().getEnergyStored(), 2_000, 20, "a Pile's 100 ticks");
            h.assertTrue(belt.energy().getEnergyStored() > pile.energy().getEnergyStored(), "the camel did not out-earn the Pile");
            done(h, p);
        });
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 160)
    public static void lacedBodyIsTiredAtHalf(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        TreadmillBlockEntity belt = belt(h, FLOOR);
        Mob cow = walker(h, belt, EntityType.COW, p);
        Hosts.data(cow).laced = true;
        near(h, TreadmillBlockEntity.rate(cow), 5, 1e-9, "a laced cow's rate");
        h.runAfterDelay(100, () -> {
            near(h, belt.energy().getEnergyStored(), 500, 5, "a laced cow's 100 ticks");
            belt.report(p);   // the status and the Tired line (seen in the client pass)
            done(h, p);
        });
    }

    /** Ten cows at full sync on ten belts: 110 FE/t between them (11 each, made whole by the carry). */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 260)
    public static void fullSyncAddsTenPercent(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(7, 2, 13));
        List<TreadmillBlockEntity> belts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            TreadmillBlockEntity belt = belt(h, new BlockPos(1 + (i % 7) * 2, 2, i < 7 ? 4 : 10));
            Mob cow = walker(h, belt, EntityType.COW, p);
            Hosts.data(cow).sync = 100;
            belts.add(belt);
        }
        h.runAfterDelay(200, () -> {
            double sum = belts.stream().mapToInt(b -> b.energy().getEnergyStored()).sum();
            near(h, sum, 22_000, 110, "ten synced cows' 200 ticks");
            near(h, belts.get(0).making(), 11, 1e-9, "a synced cow's FE/t");
            done(h, p);
        });
    }

    @GameTest(template = "platform", batch = "treadmill_config", timeoutTicks = 160)
    public static void outputConfigScalesTheBelt(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        TreadmillBlockEntity belt = belt(h, new BlockPos(3, 2, 4));
        Mob cow = walker(h, belt, EntityType.COW, p);
        int was = Config.TREADMILL_OUTPUT.get();
        Config.TREADMILL_OUTPUT.set(250);
        try {
            near(h, TreadmillBlockEntity.rate(cow), 25, 1e-9, "a cow at 250 %");
        } catch (RuntimeException e) {
            Config.TREADMILL_OUTPUT.set(was);
            throw e;
        }
        h.runAfterDelay(100, () -> {
            int got = belt.energy().getEnergyStored();
            Config.TREADMILL_OUTPUT.set(0);
            double off = TreadmillBlockEntity.rate(cow);
            Config.TREADMILL_OUTPUT.set(was);
            near(h, got, 2_500, 25, "a cow's 100 ticks at 250 %");
            near(h, off, 0, 0, "a cow at 0 %");
            done(h, p);
        });
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 80)
    public static void beltHoldsTenThousand(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        TreadmillBlockEntity belt = belt(h, new BlockPos(3, 2, 4));
        walker(h, belt, EntityType.CAMEL, p);
        IEnergyStorage cap = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(new BlockPos(3, 2, 4)), Direction.EAST);
        h.assertTrue(cap != null && cap.getMaxEnergyStored() == 10_000, "the foot has no 10 kFE buffer");
        h.assertTrue(!cap.canReceive() && cap.receiveEnergy(500, false) == 0, "the belt takes FE in");
        h.assertTrue(h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(new BlockPos(3, 2, 3)), Direction.EAST) == null,
                "the head has a buffer of its own");
        belt.energy().set(9_990);
        h.runAfterDelay(20, () -> {
            h.assertValueEqual(belt.energy().getEnergyStored(), 10_000, "a full belt");
            h.assertValueEqual(cap.extractEnergy(5_000, true), 1_000, "what a tick can take out");
            done(h, p);
        });
    }

    // --- rot -----------------------------------------------------------------------------------------------------

    private static void rot(GameTestHelper h, boolean embalmed, boolean laced, double want) {
        ServerPlayer p = player(h, FLOOR);
        TreadmillBlockEntity belt = belt(h, new BlockPos(3, 2, 4));
        Mob cow = walker(h, belt, EntityType.COW, p);
        HostData data = Hosts.data(cow);
        data.freshness = 100;
        if (embalmed) data.embalmedUntil = h.getLevel().getGameTime() + 100_000;
        data.laced = laced;
        h.assertTrue(Treadmills.isWalker(cow), "the cow is not clocked in");
        h.runAfterDelay(1_200, () -> {
            near(h, data.freshness, want, 0.02, "freshness after a minute on the belt");
            done(h, p);
        });
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 1_260)
    public static void beltRotsTwiceAsFast(GameTestHelper h) {
        rot(h, false, false, 96);
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 1_260)
    public static void embalmedOnTheBeltRotsAtItsUsualRate(GameTestHelper h) {
        rot(h, true, false, 98);
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 1_260)
    public static void lacedBodyNeverRotsOnTheBelt(GameTestHelper h) {
        rot(h, false, true, 100);
    }

    // --- where the FE goes ---------------------------------------------------------------------------------------

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 160)
    public static void beltPushesFromItsNearEndOnly(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(7, 2, 12));
        TreadmillBlockEntity belt = belt(h, FLOOR);
        h.setBlock(FLOOR.east(), ModRegistry.LEYDEN_JAR.get());
        h.setBlock(FLOOR.north().west(), ModRegistry.LEYDEN_JAR.get());
        var atFoot = (PowerBlockEntities.LeydenJar) h.getBlockEntity(FLOOR.east());
        var atHead = (PowerBlockEntities.LeydenJar) h.getBlockEntity(FLOOR.north().west());
        walker(h, belt, EntityType.COW, p);
        h.runAfterDelay(100, () -> {
            h.assertTrue(atFoot.energy().getEnergyStored() >= 900, "the jar at the foot got " + atFoot.energy().getEnergyStored());
            h.assertValueEqual(atHead.energy().getEnergyStored(), 0, "the jar at the head");
            h.assertTrue(belt.energy().getEnergyStored() < 50, "the belt kept what it made: " + belt.energy().getEnergyStored());
            done(h, p);
        });
    }

    // --- refusals ------------------------------------------------------------------------------------------------

    @GameTest(template = "platform", batch = BATCH)
    public static void beltRefusesEachKindWithItsLine(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(7, 2, 12));
        TreadmillBlockEntity belt = belt(h, FLOOR);
        Map<EntityType<?>, String> kinds = new java.util.LinkedHashMap<>();
        kinds.put(EntityType.BAT, "flier");
        kinds.put(EntityType.GHAST, "ghast");
        kinds.put(EntityType.SQUID, "swimmer");
        kinds.put(EntityType.STRIDER, "strider");
        kinds.put(EntityType.IRON_GOLEM, "heavy");
        kinds.put(EntityType.SLIME, "bounce");
        for (Map.Entry<EntityType<?>, String> k : kinds.entrySet()) {
            Mob mob = bareHost(h, belt, k.getKey());
            belt.report(p);
            h.assertTrue(belt.walker() == null, "the belt clocked in a " + k.getKey());
            h.assertValueEqual(Refusals.lastDelivered(p), "treadmill." + k.getValue(), "the line for a " + k.getKey());
            mob.discard();
        }
        // a Utility Shell (a host by make) runs on charge
        Vec3 seam = belt.seam();
        Mob lunk = h.spawn(ModRegistry.LUNK.get(), h.relativeVec(seam));
        lunk.moveTo(seam.x, seam.y, seam.z, 0, 0);
        belt.report(p);
        h.assertTrue(belt.walker() == null, "the belt clocked in a Lunk");
        h.assertValueEqual(Refusals.lastDelivered(p), "treadmill.shell", "the line for a Lunk");
        lunk.discard();
        // a living cow, head and all: the belt never makes anything of it
        Mob cow = h.spawn(EntityType.COW, h.relativeVec(seam));
        cow.moveTo(seam.x, seam.y, seam.z, 0, 0);
        belt.report(p);
        h.assertTrue(belt.walker() == null, "the belt clocked in a living cow");
        h.assertValueEqual(Refusals.lastDelivered(p), "treadmill.living", "the line for a living cow");
        cow.discard();
        // nobody: idle (a status, not a refusal)
        belt.report(p);
        h.assertValueEqual(Refusals.lastDelivered(p), "treadmill.living", "an idle belt refused");
        done(h, p);
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 120)
    public static void noWalkerNoFe(GameTestHelper h) {
        TreadmillBlockEntity belt = belt(h, FLOOR);
        Vec3 seam = belt.seam();
        Mob cow = h.spawn(EntityType.COW, h.relativeVec(seam));
        cow.moveTo(seam.x, seam.y, seam.z, 0, 0);
        h.runAfterDelay(100, () -> {
            h.assertValueEqual(belt.energy().getEnergyStored(), 0, "FE from an empty (or living-cow) belt");
            h.assertFalse(h.getBlockState(FLOOR).getValue(TreadmillBlock.RUNNING), "an empty belt runs");
            h.assertFalse(Treadmills.isWalker(cow), "a living cow clocked in");
            h.succeed();
        });
    }

    // --- placing, breaking, holding ------------------------------------------------------------------------------

    @GameTest(template = "platform", batch = BATCH)
    public static void beltPlacesLikeABedOrRefuses(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.south(2)));
        p.moveTo(at.x, at.y, at.z, 180, 30);   // facing north
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.TREADMILL_ITEM.get()));
        BlockPos below = h.absolutePos(FLOOR.below());
        InteractionResult r = p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
        h.assertTrue(r.consumesAction(), "placing failed: " + r);
        BlockState foot = h.getBlockState(FLOOR), head = h.getBlockState(FLOOR.north());
        h.assertTrue(foot.is(ModRegistry.TREADMILL.get()) && foot.getValue(TreadmillBlock.PART) == TreadmillBlock.Part.FOOT
                && foot.getValue(TreadmillBlock.FACING) == Direction.NORTH, "the foot is not where clicked, facing away: " + foot);
        h.assertTrue(head.is(ModRegistry.TREADMILL.get()) && head.getValue(TreadmillBlock.PART) == TreadmillBlock.Part.HEAD, "no head: " + head);
        h.assertTrue(p.getUUID().equals(((TreadmillBlockEntity) h.getBlockEntity(FLOOR)).owner()), "the placer does not own it");
        // no room for the head: refused, out loud
        h.setBlock(FLOOR.east(2).north(), Blocks.STONE);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.TREADMILL_ITEM.get()));
        BlockPos below2 = h.absolutePos(FLOOR.east(2).below());
        InteractionResult r2 = p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below2).add(0, 0.5, 0), Direction.UP, below2, false)));
        h.assertFalse(r2.consumesAction(), "placed with no room");
        h.assertTrue(h.getBlockState(FLOOR.east(2)).isAir(), "half a belt was placed");
        h.assertValueEqual(Refusals.lastDelivered(p), "treadmill.no_room", "the no-room line");
        done(h, p);
    }

    /** Breaking either half takes both and drops one belt; the body on it stays standing where it was. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 60)
    public static void brokenBeltLeavesTheBodyStanding(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(7, 2, 13));
        TreadmillBlockEntity belt = belt(h, new BlockPos(3, 2, 5));
        Mob cow = walker(h, belt, EntityType.COW, p);
        belt(h, new BlockPos(11, 2, 5));
        h.runAfterDelay(5, () -> {
            h.getLevel().destroyBlock(h.absolutePos(new BlockPos(3, 2, 4)), true);   // the head
            h.getLevel().destroyBlock(h.absolutePos(new BlockPos(11, 2, 5)), true);  // the other's foot
        });
        h.runAfterDelay(30, () -> {
            for (BlockPos c : List.of(new BlockPos(3, 2, 5), new BlockPos(3, 2, 4), new BlockPos(11, 2, 5), new BlockPos(11, 2, 4))) {
                h.assertTrue(h.getBlockState(c).isAir(), "half a belt left at " + c);
            }
            int left = 0, right = 0;
            for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(16))) {
                if (!item.getItem().is(ModRegistry.TREADMILL_ITEM.get())) continue;
                if (item.getX() < h.absolutePos(FLOOR).getX()) left += item.getItem().getCount();
                else right += item.getItem().getCount();
            }
            h.assertValueEqual(left, 1, "belts dropped breaking the head");
            h.assertValueEqual(right, 1, "belts dropped breaking the foot");
            h.assertTrue(cow.isAlive() && Hosts.isHost(cow) && !Treadmills.isWalker(cow), "the cow did not step off and stand");
            near(h, cow.getX(), belt.seam().x, 0.05, "where the cow stands");
            done(h, p);
        });
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 80)
    public static void aBodyRottingAwayOnTheBeltFillsTheHopperBelow(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(7, 2, 13));
        TreadmillBlockEntity belt = belt(h, FLOOR);
        h.setBlock(FLOOR.below(), Blocks.HOPPER);
        Mob cow = walker(h, belt, EntityType.COW, p);
        Hosts.data(cow).freshness = 0.0001f;
        h.runAfterDelay(50, () -> {
            h.assertFalse(cow.isAlive(), "the cow did not rot away");
            HopperBlockEntity hopper = (HopperBlockEntity) h.getBlockEntity(FLOOR.below());
            int got = 0;
            for (int i = 0; i < hopper.getContainerSize(); i++) got += hopper.getItem(i).getCount();
            h.assertTrue(got > 0, "the hopper under the belt took nothing");
            int lying = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(FLOOR)).inflate(3)).size();
            h.assertValueEqual(lying, 0, "loot left lying on the belt");
            done(h, p);
        });
    }

    /** An unridden walker is put back at the seam facing the head; knocked further than a block, it steps off. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 60)
    public static void anUnriddenWalkerIsHeldAtTheSeam(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(7, 2, 13));
        TreadmillBlockEntity belt = belt(h, FLOOR);
        Mob cow = walker(h, belt, EntityType.COW, p);
        Vec3 seam = belt.seam();
        cow.setPos(seam.x + 0.3, seam.y, seam.z + 0.4);
        cow.setYRot(30);
        h.runAfterDelay(2, () -> {
            near(h, cow.getX(), seam.x, 0.02, "held x");
            near(h, cow.getZ(), seam.z, 0.02, "held z");
            near(h, net.minecraft.util.Mth.wrapDegrees(cow.getYRot() - Direction.NORTH.toYRot()), 0, 1, "facing the head");
            h.assertTrue(Treadmills.isWalker(cow), "a nudged walker was let go");
            cow.setPos(seam.x + 1.5, seam.y, seam.z);
        });
        h.runAfterDelay(5, () -> {
            h.assertFalse(Treadmills.isWalker(cow), "a walker knocked off is still clocked in");
            near(h, cow.getX(), seam.x + 1.5, 0.05, "it stands where it landed");
            done(h, p);
        });
    }

    /** Wide bodies: a horse held at the seam clear of the hood; a camel with walls beside the seam is never put into them. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 140)
    public static void wideBodiesKeepClearOfTheHoodAndTheWalls(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(7, 2, 13));
        TreadmillBlockEntity open = belt(h, new BlockPos(3, 2, 5));
        Mob horse = walker(h, open, EntityType.HORSE, p);
        // walls three high beside the camel's head cell (it could never walk in between them)
        for (int y = 2; y <= 4; y++) {
            h.setBlock(new BlockPos(9, y, 4), Blocks.STONE);
            h.setBlock(new BlockPos(11, y, 4), Blocks.STONE);
        }
        TreadmillBlockEntity walled = belt(h, new BlockPos(10, 2, 5));
        Mob camel = walker(h, walled, EntityType.CAMEL, p);
        Vec3 stood = h.absoluteVec(new Vec3(10.5, 2 + TreadmillBlock.DECK, 5.9));
        camel.setPos(stood.x, stood.y, stood.z);
        double hood = h.absoluteVec(new Vec3(0, 0, 4 + 6 / 16.0)).z;
        h.runAfterDelay(2, () -> {
            h.assertTrue(horse.position().subtract(open.standPoint(horse)).horizontalDistance() < 0.02, "the horse is not at its stand point");
            h.assertTrue(horse.getZ() - horse.getBbWidth() / 2 >= hood - 1e-6, "the horse reaches into the hood");
            h.assertTrue(camel.position().subtract(stood).horizontalDistance() < 0.02, "the camel was moved into the walls");
        });
        h.runAfterDelay(120, () -> {
            for (Mob m : List.of(horse, camel)) {
                h.assertTrue(m.isAlive() && m.getHealth() >= m.getMaxHealth() - 1e-3, m.getType() + " was hurt on the belt: " + m.getHealth());
                h.assertTrue(Treadmills.isWalker(m), m.getType() + " stepped off");
            }
            h.assertTrue(horse.position().subtract(open.standPoint(horse)).horizontalDistance() < 0.02, "the horse slid");
            h.assertTrue(camel.position().subtract(stood).horizontalDistance() < 0.02, "the camel slid");
            near(h, walled.making(), 24, 1e-9, "the walled camel's FE/t");
            done(h, p);
        });
    }

    // --- a jar on the belt ---------------------------------------------------------------------------------------

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 320)
    public static void jarOnTheBeltMakesFeAndSyncs(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(7, 2, 13));
        TreadmillBlockEntity belt = belt(h, FLOOR);
        Vec3 seam = belt.seam();
        Mob cow = walker(h, belt, EntityType.COW, p);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, cow), "mount failed");
        cow.moveTo(seam.x, seam.y, seam.z, 0, 0);
        belt.check();
        h.assertTrue(belt.walker() == cow, "a ridden host on the belt is not walking it");
        float sync0 = Hosts.data(cow).sync;
        h.runAfterDelay(240, () -> {
            near(h, belt.energy().getEnergyStored(), 2_400, 30, "a ridden cow's 240 ticks");
            h.assertTrue(Hosts.data(cow).sync >= sync0 + 0.98f, "the ride did not sync: " + sync0 + " -> " + Hosts.data(cow).sync);
            p.stopRiding();
        });
        h.runAfterDelay(250, () -> {
            h.assertTrue(belt.walker() == cow && Treadmills.isWalker(cow), "the body stopped when its jar got off");
            h.assertTrue(cow.position().subtract(belt.standPoint(cow)).horizontalDistance() < 0.02, "the body is not held once its jar is off");
            done(h, p);
        });
    }

    /** On a running belt the rider's forward input walks the body in place; back still moves it. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 60)
    public static void aRiddenHostCannotWalkOffForwards(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(7, 2, 13));
        TreadmillBlockEntity belt = belt(h, FLOOR);
        Mob cow = walker(h, belt, EntityType.COW, p);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, cow), "mount failed");
        h.assertFalse(Treadmills.holdsForward(cow), "a belt that has not started holds the body");
        h.runAfterDelay(3, () -> {
            h.assertTrue(h.getBlockState(FLOOR).getValue(TreadmillBlock.RUNNING) && h.getBlockState(FLOOR.north()).getValue(TreadmillBlock.RUNNING),
                    "the belt does not run on both halves");
            h.assertTrue(Treadmills.holdsForward(cow), "a running belt does not hold the body");
            p.zza = 1;
            h.assertTrue(HostControl.riddenInput(cow, p).z == 0, "forward walked off a running belt");
            p.zza = -1;
            h.assertTrue(HostControl.riddenInput(cow, p).z < 0, "back is held too");
            p.zza = 0;
            done(h, p);
        });
    }

    /**
     * A ridden body pushed forward along an idle belt, from behind its foot, for three seconds: it stops at the console
     * end, still on the deck (the drawn hood and console used to be a stair it walked up and over). A ridden body steps
     * a whole block (a camel one and a half): {@code move} is the game's own collision and step-up.
     */
    private static void rideIntoTheConsole(GameTestHelper h, EntityType<? extends Mob> type) {
        ServerPlayer p = player(h, new BlockPos(3, 2, 12));
        belt(h, FLOOR);
        Mob body = h.spawn(type, new BlockPos(7, 2, 11));
        body.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), body, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "the saw failed on " + type);
        Vec3 start = h.absoluteVec(new Vec3(7.5, 2, 11.5));
        body.moveTo(start.x, start.y, start.z, 180, 0);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, body), "mount failed");
        h.assertTrue(body.maxUpStep() >= 1.0f, "setup: a ridden " + type + " steps " + body.maxUpStep());
        double wall = h.absoluteVec(new Vec3(7, 2, 6 + 6 / 16.0)).z;   // the console end's wall, on the head cell's far side
        double floor = h.absoluteVec(new Vec3(7, 2, 7)).y;
        double[] highest = {floor};
        h.onEachTick(() -> {
            body.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, -0.2, -0.3));
            highest[0] = Math.max(highest[0], body.getY());
        });
        h.runAfterDelay(60, () -> {
            double front = body.getBoundingBox().minZ;
            h.assertTrue(front >= wall - 1e-3, "the " + type + " went past the console: its front at " + front + ", the wall at " + wall);
            h.assertTrue(highest[0] <= floor + TreadmillBlock.DECK + 0.01, "the " + type + " climbed to " + (highest[0] - floor) + " above the floor");
            h.assertTrue(body.getZ() < start.z - 2, "the " + type + " never got onto the belt: " + body.position());
            done(h, p);
        });
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void aRiddenHorseStopsAtTheConsole(GameTestHelper h) {
        rideIntoTheConsole(h, EntityType.HORSE);
    }

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 100)
    public static void aRiddenCamelStopsAtTheConsole(GameTestHelper h) {
        rideIntoTheConsole(h, EntityType.CAMEL);
    }

    /** The Wellness Meter: 0, 1-6, 7-12, 13-18, 19+ FE/t; on the head, the same as the foot. */
    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 60)
    public static void theMeterReadsTheBand(GameTestHelper h) {
        h.assertValueEqual(TreadmillBlockEntity.dial(0), 0, "nothing");
        h.assertValueEqual(TreadmillBlockEntity.dial(2), 1, "a silverfish");
        h.assertValueEqual(TreadmillBlockEntity.dial(6.6), 2, "a synced zombie");
        h.assertValueEqual(TreadmillBlockEntity.dial(12), 2, "a chicken");
        h.assertValueEqual(TreadmillBlockEntity.dial(14), 3, "a spider");
        h.assertValueEqual(TreadmillBlockEntity.dial(26.4), 4, "a synced camel");
        ServerPlayer p = player(h, new BlockPos(7, 2, 13));
        TreadmillBlockEntity belt = belt(h, FLOOR);
        walker(h, belt, EntityType.CAMEL, p);
        h.runAfterDelay(3, () -> {
            h.assertValueEqual(h.getBlockState(FLOOR.north()).getValue(TreadmillBlock.DIAL), 4, "the head's meter for a camel");
            h.assertValueEqual(h.getBlockState(FLOOR).getValue(TreadmillBlock.DIAL), 4, "the foot's meter for a camel");
            done(h, p);
        });
    }

    // --- who is told -----------------------------------------------------------------------------------------------

    @GameTest(template = "platform", batch = BATCH, timeoutTicks = 80)
    public static void corporateWellnessOnTheFirstPush(GameTestHelper h) {
        ServerPlayer owner = TestPlayers.at(h, "Owner", new BlockPos(7, 2, 13));
        TreadmillBlockEntity belt = belt(h, FLOOR);
        belt.setOwner(owner.getUUID());
        walker(h, belt, EntityType.COW, owner);
        h.runAfterDelay(10, () -> h.assertFalse(earned(h, owner, "corporate_wellness"), "earned with no machine to power"));
        h.runAfterDelay(12, () -> h.setBlock(FLOOR.east(), ModRegistry.LEYDEN_JAR.get()));
        h.runAfterDelay(40, () -> {
            h.assertTrue(earned(h, owner, "corporate_wellness"), "the first push earned nothing");
            h.assertTrue(Memos.told(owner, Memos.Memo.TREADMILL_POWERED) > 0, "no memo for the first push");
            done(h, owner);
        });
    }

    /** Its own batch: the sum is of every running belt within 16, and a neighbouring test's belts would count. */
    @GameTest(template = "platform", batch = "treadmill_cardio", timeoutTicks = 300)
    public static void cardioDepartmentAtOneHundred(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "Cardio", new BlockPos(7, 2, 13));
        List<TreadmillBlockEntity> belts = new ArrayList<>();
        for (int i = 0; i < 10; i++) belts.add(belt(h, new BlockPos(1 + (i % 7) * 2, 2, i < 7 ? 4 : 10)));
        // nine cows: 90 FE/t
        for (int i = 0; i < 9; i++) walker(h, belts.get(i), EntityType.COW, p);
        h.runAfterDelay(110, () -> {
            h.assertFalse(earned(h, p, "cardio_department"), "earned at 90 FE/t");
            walker(h, belts.get(9), EntityType.COW, p);
        });
        h.runAfterDelay(230, () -> {
            h.assertTrue(earned(h, p, "cardio_department"), "100 FE/t within 16 earned nothing");
            done(h, p);
        });
    }
}
