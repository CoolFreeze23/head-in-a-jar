package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity;
import com.coolfreeze23.headinajar.block.DeskIndex;
import com.coolfreeze23.headinajar.block.DeskJobs;
import com.coolfreeze23.headinajar.block.HeadDonor;
import com.coolfreeze23.headinajar.block.HeadSpeak;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.block.LeechJarBlockEntity;
import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Warnings;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Heads at work (0.6.1): lunch, the bell, desk jobs, morale's work, Lookouts and the comparator, the Living Library. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeadsAtWorkGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        return SawTableGameTests.player(h, at);
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        SawTableGameTests.done(h, players);
    }

    private static ItemStack jarredHead(GameTestHelper h, EntityType<? extends Mob> type, VillagerProfession job) {
        Mob mob = h.spawn(type, FLOOR.above(4));
        if (mob instanceof Villager v && job != null) v.setVillagerData(v.getVillagerData().setProfession(job));
        ItemStack stack = JarredHeadItem.of(mob);
        mob.discard();
        return stack;
    }

    private static JarredHeadBlockEntity plinth(GameTestHelper h, BlockPos at, EntityType<? extends Mob> type) {
        return plinth(h, at, jarredHead(h, type, null));
    }

    private static JarredHeadBlockEntity plinth(GameTestHelper h, BlockPos at, ItemStack head) {
        h.setBlock(at, ModRegistry.JARRED_HEAD_BLOCK.get());
        JarredHeadBlockEntity be = (JarredHeadBlockEntity) h.getBlockEntity(at);
        CustomData data = head.get(DataComponents.CUSTOM_DATA);
        be.load(data == null ? new CompoundTag() : data.copyTag());
        return be;
    }

    /** A head fed by {@code p} with a real bucket (so it is hired). */
    private static JarredHeadBlockEntity fed(GameTestHelper h, ServerPlayer p, BlockPos at, EntityType<? extends Mob> type) {
        JarredHeadBlockEntity head = plinth(h, at, type);
        useOn(h, p, head, new ItemStack(ModFluids.BROTH.bucket.get()));
        return head;
    }

    private static void useOn(GameTestHelper h, ServerPlayer p, JarredHeadBlockEntity head, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = head.getBlockPos();
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        p.gameMode.useItemOn(p, h.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
    }

    private static String key(Component c) {
        return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : null;
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static int dropped(GameTestHelper h, BlockPos near, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(near)).inflate(3))) {
            if (e.getItem().is(item)) n += e.getItem().getCount();
        }
        return n;
    }

    // --- lunch --------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 300)
    public static void aTrophyNeverWorksItsDesk(GameTestHelper h) {
        JarredHeadBlockEntity cow = plinth(h, FLOOR, EntityType.COW);
        h.runAfterDelay(200, () -> {
            h.assertTrue(!cow.fed() && DeskIndex.all(h.getLevel(), "cow").stream().noneMatch(cow.getBlockPos()::equals), "an unfed cow head is in the desk index");
            h.assertTrue(cow.said() == null, "an unfed, unrung head said something");
            h.assertTrue(!DeskJobs.cowNear(h.getLevel(), cow.getBlockPos()), "an unfed cow head makes broth last");
            h.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void aBucketFillsTheBowlAndHires(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        JarredHeadBlockEntity cow = plinth(h, FLOOR, EntityType.COW);
        h.assertTrue(cow.staff() == null || !cow.staff().staff(), "the head was staff before lunch");
        useOn(h, p, cow, new ItemStack(ModFluids.BROTH.bucket.get()));
        h.assertValueEqual(cow.bowl, JarredHeadBlockEntity.BOWL, "the bowl after a bucket");
        h.assertTrue(p.getMainHandItem().is(Items.BUCKET), "no empty bucket back: " + p.getMainHandItem());
        StaffFile f = cow.staff();
        h.assertTrue(f != null && f.staff() && p.getUUID().equals(f.employer), "lunch did not hire the head with its feeder as employer");
        h.assertValueEqual(f.history.get(f.history.size() - 1).k(), "lunch", "the history line");
        h.assertTrue(SawTableGameTests.earned(h, p, "lunch_is_served"), "Lunch Is Served was not earned");
        h.assertTrue(DeskIndex.all(h.getLevel(), "cow").contains(cow.getBlockPos()), "a fed cow head is not in the desk index");
        useOn(h, p, cow, new ItemStack(ModFluids.BROTH.bucket.get()));
        h.assertValueEqual(Refusals.lastDelivered(p), "desk.bowl_full", "a second bucket");
        useOn(h, p, cow, new ItemStack(Items.WATER_BUCKET));
        h.assertValueEqual(Refusals.lastDelivered(p), "desk.wrong_bucket", "a water bucket");
        h.assertValueEqual(cow.bowl, JarredHeadBlockEntity.BOWL, "the bowl after the refusals");
        // a bucket never tops up a part-full bowl: it waits until the bowl is down to a quarter
        cow.setBowl(600);
        useOn(h, p, cow, new ItemStack(ModFluids.BROTH.bucket.get()));
        h.assertValueEqual(cow.bowl, 600, "a bucket went into a bowl still 600 mB full");
        h.assertTrue(p.getMainHandItem().is(ModFluids.BROTH.bucket.get()), "the bucket was spent on a part-full bowl");
        cow.setBowl(DeskJobs.REFILL_AT);
        useOn(h, p, cow, new ItemStack(ModFluids.BROTH.bucket.get()));
        h.assertValueEqual(cow.bowl, JarredHeadBlockEntity.BOWL, "a bucket into a bowl down to a quarter");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void pipesFeedOnlyStaff(GameTestHelper h) {
        JarredHeadBlockEntity cow = plinth(h, FLOOR, EntityType.COW);
        var level = h.getLevel();
        BlockPos pos = cow.getBlockPos();
        FluidStack broth = new FluidStack(ModFluids.BROTH.source.get(), 500);
        IFluidHandler side = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.NORTH);
        h.assertTrue(side != null && side.fill(broth, IFluidHandler.FluidAction.EXECUTE) == 0, "a pipe fed a trophy");
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP) == null, "the bowl takes pipes from the top");
        ServerPlayer p = player(h, FLOOR.south(2));
        StaffFile f = Staff.create(level.getServer(), level, EntityType.COW);
        f.morale = 50;
        Staff.employ(f, p);
        cow.setStaff(f);
        h.assertValueEqual(side.fill(broth, IFluidHandler.FluidAction.EXECUTE), 500, "a pipe into a staff head's bowl");
        h.assertValueEqual(side.fill(new FluidStack(Blocks.WATER.defaultBlockState().getFluidState().getType(), 100), IFluidHandler.FluidAction.EXECUTE), 0, "water by pipe");
        h.assertValueEqual(cow.bowl, 500, "the bowl");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aBucketIsThreeDaysOfLunch(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        JarredHeadBlockEntity sheep = fed(h, p, FLOOR, EntityType.SHEEP);
        int passes = 72_000 / DeskJobs.PASS;
        for (int i = 0; i < passes - 1; i++) DeskJobs.runPass(h.getLevel(), sheep);
        h.assertValueEqual(sheep.bowl, 1, "the bowl one pass before three days");
        DeskJobs.runPass(h.getLevel(), sheep);
        h.assertValueEqual(sheep.bowl, 0, "the bowl after three days");
        h.assertTrue(!sheep.fed() && sheep.lunch, "an emptied bowl: still on lunch, no longer fed");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theBowlIsKeptInTheItem(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        JarredHeadBlockEntity cow = fed(h, p, FLOOR, EntityType.COW);
        cow.setBowl(500);
        cow.libraryDay = 3;
        ItemStack item = cow.toItem();
        JarredHeadBlockEntity again = plinth(h, FLOOR.east(2), item);
        h.assertValueEqual(again.bowl, 500, "the bowl after breaking and placing");
        h.assertValueEqual(again.libraryDay, 3L, "the day it last gave blood");
        h.assertTrue(again.staff() != null && again.staff().staff(), "the file");
        done(h, p);
    }

    // --- the order of things used on a head ---------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aGlassBottleOnASquidIsInkNotAWrongWage(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        JarredHeadBlockEntity squid = fed(h, p, FLOOR, EntityType.SQUID);
        useOn(h, p, squid, new ItemStack(Items.GLASS_BOTTLE));
        h.assertValueEqual(count(p, Items.INK_SAC), 1, "ink sacs");
        h.assertTrue(p.getMainHandItem().is(Items.GLASS_BOTTLE), "the bottle was not kept");
        h.assertTrue(!"staff.wrong_wage".equals(Refusals.lastDelivered(p)), "the bottle was taken for the witch's wage");
        useOn(h, p, squid, new ItemStack(Items.GLASS_BOTTLE));
        h.assertValueEqual(Refusals.lastDelivered(p), "desk.too_soon", "a second bottle the same day");
        h.assertValueEqual(count(p, Items.INK_SAC), 1, "ink sacs after the second bottle");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aDeskItemOnAnUnfedHeadSaysWhy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        JarredHeadBlockEntity sheep = plinth(h, FLOOR, EntityType.SHEEP);
        useOn(h, p, sheep, new ItemStack(Items.SHEARS));
        h.assertValueEqual(Refusals.lastDelivered(p), "desk.unfed", "shears on an unfed sheep head");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theSheepGivesWoolOnceADayAndTakesDye(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        JarredHeadBlockEntity sheep = fed(h, p, FLOOR, EntityType.SHEEP);
        useOn(h, p, sheep, new ItemStack(Items.SHEARS));
        h.assertTrue(sheep.record().getBoolean("Sheared"), "not shorn");
        h.assertTrue(dropped(h, FLOOR, Items.WHITE_WOOL) == 1, "no white wool");
        useOn(h, p, sheep, new ItemStack(Items.SHEARS));
        h.assertValueEqual(Refusals.lastDelivered(p), "desk.too_soon", "a second shearing");
        useOn(h, p, sheep, new ItemStack(Items.BLUE_DYE));
        h.assertValueEqual((int) sheep.record().getByte("Color"), DyeColor.BLUE.getId(), "the colour");
        h.assertTrue(sheep.getUpdateTag(h.getLevel().registryAccess()).getCompound("Look").getByte("Color") == DyeColor.BLUE.getId(), "clients are not told the colour");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theMooshroomGivesStewOnceADay(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        JarredHeadBlockEntity moo = fed(h, p, FLOOR, EntityType.MOOSHROOM);
        useOn(h, p, moo, new ItemStack(Items.BOWL));
        h.assertTrue(p.getMainHandItem().is(Items.MUSHROOM_STEW) || count(p, Items.MUSHROOM_STEW) == 1, "no stew");
        useOn(h, p, moo, new ItemStack(Items.BOWL));
        h.assertValueEqual(Refusals.lastDelivered(p), "desk.too_soon", "a second bowl");
        useOn(h, p, moo, new ItemStack(Items.SHEARS));
        h.assertTrue(DeskJobs.said().get(DeskJobs.said().size() - 1).endsWith("mooshroom.shears"), "shears said nothing");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aFedVillagerReadsItsBestOfferAndStillTrades(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        JarredHeadBlockEntity v = plinth(h, FLOOR, jarredHead(h, EntityType.VILLAGER, VillagerProfession.FARMER));
        useOn(h, p, v, new ItemStack(ModFluids.BROTH.bucket.get()));
        h.assertTrue(v.staff() != null && v.staff().staff(), "lunch did not hire the villager head");
        useOn(h, p, v, new ItemStack(Items.EMERALD));
        h.assertTrue(p.containerMenu instanceof MerchantMenu, "the trading screen did not open");
        String said = key(v.said());
        h.assertTrue("desk.headinajar.villager.best_offer".equals(said) || "desk.headinajar.villager.best_offer2".equals(said)
                || "desk.headinajar.villager.no_offer".equals(said), "the best offer was not read: " + said);
        p.closeContainer();
        done(h, p);
    }

    // --- the bell -------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 400)
    public static void aRingSaysALineAndNeverTheSameTwice(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        JarredHeadBlockEntity cow = plinth(h, FLOOR, EntityType.COW);
        int[] last = {0};
        for (int i = 0; i < 10; i++) {
            final int n = i;
            h.runAfterDelay(1 + 21L * i, () -> {
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                useOn(h, p, cow, ItemStack.EMPTY);
                h.assertTrue(cow.lastBell > 0, "ring " + n + " said nothing");
                h.assertTrue(cow.lastBell != last[0], "ring " + n + " said line " + cow.lastBell + " twice running");
                h.assertTrue(key(cow.said()) != null && key(cow.said()).startsWith("bell.headinajar.minecraft.cow."), "the line: " + key(cow.said()));
                last[0] = cow.lastBell;
            });
        }
        h.runAfterDelay(1 + 21L * 9 + 5, () -> {
            long at = cow.saidAt();
            useOn(h, p, cow, ItemStack.EMPTY);
            h.assertValueEqual(cow.saidAt(), at, "a ring within a second was not ignored");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void aBigHeadInABigJarFirstSaysItIsASnugFit(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        // a ghast in an Executive Jar, a warden in a Reinforced one: the first ring, ever, is the snug fit; then bell lines
        Mob ghastMob = h.spawn(EntityType.GHAST, FLOOR.above(4));
        JarredHeadBlockEntity ghast = plinth(h, FLOOR, JarredHeadItem.of(ghastMob, new ItemStack(ModRegistry.EXECUTIVE_JAR.get())));
        ghastMob.discard();
        Mob wardenMob = h.spawn(EntityType.WARDEN, FLOOR.above(4));
        JarredHeadBlockEntity warden = plinth(h, FLOOR.east(2), JarredHeadItem.of(wardenMob, new ItemStack(ModRegistry.REINFORCED_JAR.get())));
        wardenMob.discard();
        // a cow is no big head, whatever its jar
        Mob cowMob = h.spawn(EntityType.COW, FLOOR.above(4));
        JarredHeadBlockEntity cow = plinth(h, FLOOR.west(2), JarredHeadItem.of(cowMob, new ItemStack(ModRegistry.EXECUTIVE_JAR.get())));
        cowMob.discard();
        for (JarredHeadBlockEntity big : new JarredHeadBlockEntity[]{ghast, warden}) {
            useOn(h, p, big, ItemStack.EMPTY);
            h.assertValueEqual(key(big.saidSecond()), HeadSpeak.SNUG, "the first ring of a big head");
            h.assertTrue(key(big.said()).startsWith("staff.headinajar.noise."), "the snug fit's noise: " + key(big.said()));
            h.assertTrue(big.snug, "the snug fit not kept");
            big.rungAt = Long.MIN_VALUE / 2;
            useOn(h, p, big, ItemStack.EMPTY);
            h.assertTrue(key(big.said()).startsWith("bell.headinajar."), "the second ring was not a bell line: " + key(big.said()));
        }
        // it stays said when the head is broken and set down again (the item keeps its desk)
        JarredHeadBlockEntity again = plinth(h, FLOOR.north(2), ghast.toItem());
        h.assertTrue(again.snug, "the snug fit said again after a move");
        useOn(h, p, cow, ItemStack.EMPTY);
        h.assertTrue(key(cow.said()).startsWith("bell.headinajar.minecraft.cow."), "a cow in a big jar: " + key(cow.said()));
        h.assertTrue(!HeadSpeak.snug(EntityType.GHAST, new CompoundTag(), ModRegistry.LIFE_SUPPORT_JAR.getId().toString()), "a ghast in a small jar is snug");
        CompoundTag big = new CompoundTag();
        big.putInt("Size", 3);
        h.assertTrue(HeadSpeak.snug(EntityType.SLIME, big, ModRegistry.EXECUTIVE_JAR.getId().toString()), "a size-4 slime in an Executive Jar is not snug");
        h.assertTrue(!HeadSpeak.snug(EntityType.SLIME, new CompoundTag(), ModRegistry.EXECUTIVE_JAR.getId().toString()), "a size-1 slime is snug");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aCreatureWithNoLinesKeepsItsOldAnswer(GameTestHelper h) {
        h.assertValueEqual(HeadSpeak.bells(EntityType.ARMOR_STAND, new CompoundTag()), 0, "a creature with no row has bell lines");
        h.assertTrue(HeadSpeak.bells(EntityType.COW, new CompoundTag()) >= 2, "a cow has fewer than two bell lines");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void theGoatGivesTwoHornsEver(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        JarredHeadBlockEntity goat = fed(h, p, FLOOR, EntityType.GOAT);
        goat.rungAt = Long.MIN_VALUE / 2;
        useOn(h, p, goat, ItemStack.EMPTY);
        goat.rungAt = Long.MIN_VALUE / 2;
        useOn(h, p, goat, ItemStack.EMPTY);
        goat.rungAt = Long.MIN_VALUE / 2;
        useOn(h, p, goat, ItemStack.EMPTY);
        h.assertValueEqual(dropped(h, FLOOR, Items.GOAT_HORN), 2, "goat horns");
        h.assertTrue(!goat.record().getBoolean("HasLeftHorn") && !goat.record().getBoolean("HasRightHorn"), "horns left on the record");
        h.assertTrue(key(goat.said()).startsWith("bell.headinajar."), "the third ring was not a bell line: " + key(goat.said()));
        done(h, p);
    }

    // --- desk jobs on the pass and at dawn -----------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void theBlazeSmeltsAQuarterFasterOncePerFurnace(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        JarredHeadBlockEntity a = fed(h, p, FLOOR, EntityType.BLAZE);
        JarredHeadBlockEntity b = fed(h, p, FLOOR.east(2), EntityType.BLAZE);
        h.setBlock(FLOOR.east(), Blocks.FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT, true));
        FurnaceBlockEntity furnace = (FurnaceBlockEntity) h.getBlockEntity(FLOOR.east());
        furnace.cookingTotalTime = 200;
        furnace.cookingProgress = 50;
        DeskJobs.runPass(h.getLevel(), a);
        DeskJobs.runPass(h.getLevel(), b);
        h.assertValueEqual(furnace.cookingProgress, 50 + DeskJobs.BLAZE, "two blazes breathed on one furnace in one pass");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theGhastCriesATearAtDawn(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        JarredHeadBlockEntity ghast = fed(h, p, FLOOR, EntityType.GHAST);
        DeskJobs.dawn(h.getLevel(), ghast.getBlockPos(), ghast, ghast.staff());
        h.assertValueEqual(dropped(h, FLOOR, Items.GHAST_TEAR), 1, "tears at the first dawn");
        h.assertTrue(ghast.firstTear && "desk.headinajar.ghast.first_tear".equals(key(ghast.said())), "the first tear's line");
        ghast.staff().morale = 20;
        DeskJobs.dawn(h.getLevel(), ghast.getBlockPos(), ghast, ghast.staff());
        h.assertValueEqual(dropped(h, FLOOR, Items.GHAST_TEAR), 3, "tears after a Disengaged dawn");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theChickenLaysAFeatherAtDawn(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        JarredHeadBlockEntity chicken = fed(h, p, FLOOR, EntityType.CHICKEN);
        DeskJobs.dawn(h.getLevel(), chicken.getBlockPos(), chicken, chicken.staff());
        h.assertValueEqual(dropped(h, FLOOR, Items.FEATHER), 1, "feathers");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void thePiglinTurnsOnlyOffANetherFloor(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        h.setBlock(FLOOR.east(2).below(), Blocks.NETHERRACK);
        JarredHeadBlockEntity loose = fed(h, p, FLOOR, EntityType.PIGLIN);
        JarredHeadBlockEntity safe = fed(h, p, FLOOR.east(2), EntityType.PIGLIN);
        h.assertTrue(Warnings.delivered(p).contains("desk.piglin_turns"), "no warning feeding a piglin where it turns");
        for (int i = 0; i < DeskJobs.TURNS / DeskJobs.PASS + 1; i++) {
            DeskJobs.runPass(h.getLevel(), loose);
            DeskJobs.runPass(h.getLevel(), safe);
        }
        h.assertTrue(loose.entityType() == EntityType.ZOMBIFIED_PIGLIN, "the piglin off a Nether floor did not turn");
        h.assertTrue(safe.entityType() == EntityType.PIGLIN, "the piglin on netherrack turned");
        h.assertTrue(loose.staff() != null && loose.staff().staff(), "turning lost its file");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 300)
    public static void thePiglinBartersAGoldIngot(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        h.setBlock(FLOOR.below(), Blocks.NETHERRACK);
        JarredHeadBlockEntity piglin = fed(h, p, FLOOR, EntityType.PIGLIN);
        useOn(h, p, piglin, new ItemStack(Items.GOLD_INGOT, 2));
        h.assertTrue(piglin.inspectUntil > 0, "it is not inspecting");
        h.assertValueEqual(p.getMainHandItem().getCount(), 1, "ingots left");
        useOn(h, p, piglin, p.getMainHandItem());
        h.assertValueEqual(Refusals.lastDelivered(p), "desk.busy", "a second ingot while it looks");
        h.runAfterDelay(DeskJobs.INSPECT * 2 + 5, () -> {
            h.assertValueEqual(piglin.inspectUntil, 0L, "still inspecting");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void theAllayTakesAnItemAndCallsItIntoAChest(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        JarredHeadBlockEntity allay = fed(h, p, FLOOR, EntityType.ALLAY);
        useOn(h, p, allay, new ItemStack(Items.DIAMOND));
        h.assertTrue(DeskJobs.allayItem(h.getLevel(), allay).is(Items.DIAMOND), "the allay does not hold the diamond");
        h.setBlock(FLOOR.west(), Blocks.CHEST);
        h.spawnItem(Items.DIAMOND, FLOOR.east(4).above().getX(), FLOOR.getY() + 1, FLOOR.getZ());
        for (int i = 0; i < 15; i++) DeskJobs.runPass(h.getLevel(), allay);
        ChestBlockEntity chest = (ChestBlockEntity) h.getBlockEntity(FLOOR.west());
        h.assertValueEqual(chest.getItem(0).getCount(), 1, "the diamond in the chest");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theEvokerTurnsABlueSheepRed(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        JarredHeadBlockEntity evoker = fed(h, p, FLOOR, EntityType.EVOKER);
        Sheep sheep = h.spawn(EntityType.SHEEP, FLOOR.east(3));
        sheep.setNoAi(true);
        sheep.setColor(DyeColor.BLUE);
        for (int i = 0; i < 15; i++) DeskJobs.runPass(h.getLevel(), evoker);
        h.assertTrue(sheep.getColor() == DyeColor.RED, "the blue sheep is " + sheep.getColor());
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theCowMakesTheArraysBrothLast(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        fed(h, p, FLOOR.east(3), EntityType.COW);
        h.assertTrue(DeskJobs.cowNear(h.getLevel(), h.absolutePos(FLOOR)), "a fed cow head 3 blocks off is not near");
        h.assertTrue(!DeskJobs.cowNear(h.getLevel(), h.absolutePos(FLOOR).west(9)), "a fed cow head reaches 12 blocks");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theSnifferFindsSomethingOnDirt(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        h.setBlock(FLOOR.below(), Blocks.DIRT);
        JarredHeadBlockEntity sniffer = fed(h, p, FLOOR, EntityType.SNIFFER);
        DeskJobs.dawn(h.getLevel(), sniffer.getBlockPos(), sniffer, sniffer.staff());
        h.assertTrue(dropped(h, FLOOR, Items.TORCHFLOWER_SEEDS) + dropped(h, FLOOR, Items.PITCHER_POD) == 1, "the sniffer found nothing");
        done(h, p);
    }

    // --- morale's work ------------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void moraleMovesAtDawnForLunch(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        JarredHeadBlockEntity cow = fed(h, p, FLOOR, EntityType.COW);
        StaffFile f = cow.staff();
        f.morale = 58;
        DeskJobs.dawn(h.getLevel(), cow.getBlockPos(), cow, f);
        h.assertValueEqual(f.morale, 60, "fed: +5, never above 60 from feeding");
        f.morale = 70;
        DeskJobs.dawn(h.getLevel(), cow.getBlockPos(), cow, f);
        h.assertValueEqual(f.morale, 70, "fed above 60: unchanged");
        cow.setBowl(0);
        f.morale = 50;
        DeskJobs.dawn(h.getLevel(), cow.getBlockPos(), cow, f);
        h.assertValueEqual(f.morale, 45, "hungry: -5");
        f.morale = 32;
        DeskJobs.dawn(h.getLevel(), cow.getBlockPos(), cow, f);
        h.assertValueEqual(f.morale, 30, "hungry: never below 30");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aCakeOnceADay(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        JarredHeadBlockEntity cow = fed(h, p, FLOOR, EntityType.COW);
        cow.staff().morale = 30;
        useOn(h, p, cow, new ItemStack(Items.CAKE));
        h.assertValueEqual(cow.staff().morale, 70, "a cake: +40");
        h.assertTrue(h.getBlockState(FLOOR.above()).isAir(), "a cake was placed");
        useOn(h, p, cow, new ItemStack(Items.CAKE));
        h.assertValueEqual(Refusals.lastDelivered(p), "staff.no_cake", "a second cake");
        JarredHeadBlockEntity trophy = plinth(h, FLOOR.east(2), EntityType.PIG);
        useOn(h, p, trophy, new ItemStack(Items.CAKE));
        h.assertValueEqual(Refusals.lastDelivered(p), "staff.cake_trophy", "a cake on a trophy");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void rivalsAndColleaguesAtDawn(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        JarredHeadBlockEntity cow = fed(h, p, FLOOR, EntityType.COW);
        plinth(h, FLOOR.east(3), EntityType.PIG);
        cow.staff().morale = 70;
        DeskJobs.dawn(h.getLevel(), cow.getBlockPos(), cow, cow.staff());
        h.assertValueEqual(cow.staff().morale, 72, "a Livestock colleague within 8: +2");
        h.assertTrue(DeskJobs.rivals(EntityType.SKELETON, EntityType.WOLF) && !DeskJobs.rivals(EntityType.COW, EntityType.PIG), "the rival pairs");
        done(h, p);
    }

    // --- Lookouts and the comparator ------------------------------------------------------------------------------------

    private static TurretPedestalBlockEntity turret(GameTestHelper h, ServerPlayer p, EntityType<? extends Mob> head) {
        h.setBlock(FLOOR, ModRegistry.TURRET_PEDESTAL.get());
        var t = (TurretPedestalBlockEntity) h.getBlockEntity(FLOOR);
        t.energy().set(40_000);
        t.use(p, jarredHead(h, head, null));
        return t;
    }

    @GameTest(template = "platform")
    public static void aCowHeadFitsAsALookout(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        TurretPedestalBlockEntity t = turret(h, p, EntityType.COW);
        h.assertTrue(t.headInfo().type() == EntityType.COW && t.lookout(), "the cow head was not fitted as a Lookout");
        h.assertTrue(Warnings.delivered(p).contains("turret.lookout"), "no Lookout warning");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void theLookoutAlarmsGlowsAndDrivesAComparator(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(5));
        TurretPedestalBlockEntity t = turret(h, p, EntityType.COW);
        var husk = h.spawn(EntityType.HUSK, FLOOR.east(4));
        husk.setNoAi(true);
        float health = husk.getHealth();
        h.runAfterDelay(30, () -> {
            h.assertTrue(t.alarmed(), "the Lookout is not alarmed");
            h.assertTrue(husk.hasEffect(MobEffects.GLOWING), "the husk does not glow");
            h.assertValueEqual(h.getBlockState(FLOOR).getAnalogOutputSignal(h.getLevel(), h.absolutePos(FLOOR)), 15, "the comparator while alarmed");
            h.assertValueEqual(husk.getHealth(), health, "the Lookout hurt the husk");
            husk.discard();
        });
        h.runAfterDelay(30 + 20 + TurretPedestalBlockEntity.HOLD + 25, () -> {
            h.assertValueEqual(h.getBlockState(FLOOR).getAnalogOutputSignal(h.getLevel(), h.absolutePos(FLOOR)), 0, "the comparator after");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void anAttackingTurretDrivesTheComparatorToo(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(5));
        TurretPedestalBlockEntity t = turret(h, p, EntityType.ZOMBIE);
        var husk = h.spawn(EntityType.HUSK, FLOOR.east(1));
        husk.setNoAi(true);
        h.runAfterDelay(10, () -> {
            h.assertValueEqual(t.signal(), 15, "an engaged attacker's signal");
            done(h, p);
        });
    }

    // --- the Living Library ------------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aHeadGivesBloodOnceADay(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        JarredHeadBlockEntity cow = fed(h, p, FLOOR, EntityType.COW);
        int morale = cow.staff().morale;
        ItemStack syringe = new ItemStack(ModRegistry.PRECISION_SYRINGE.get());
        useOn(h, p, cow, syringe);
        ItemStack sample = p.getInventory().items.stream().filter(s -> s.is(ModRegistry.BLOOD_SAMPLE.get())).findFirst().orElse(ItemStack.EMPTY);
        int q = GeneticsItems.quality(sample);
        h.assertTrue(q >= HeadDonor.MIN && q <= HeadDonor.MAX, "a fed head's sample is " + q);
        h.assertValueEqual(syringe.getDamageValue(), 1, "the syringe's wear");
        h.assertValueEqual(cow.staff().morale, morale - 10, "morale after a draw");
        useOn(h, p, cow, syringe);
        h.assertValueEqual(Refusals.lastDelivered(p), "library.rested", "a second draw the same day");
        JarredHeadBlockEntity trophy = plinth(h, FLOOR.east(2), EntityType.PIG);
        int before = HeadDonor.drawn().size();
        useOn(h, p, trophy, new ItemStack(ModRegistry.PRECISION_SYRINGE.get()));
        String last = HeadDonor.drawn().get(HeadDonor.drawn().size() - 1);
        int tq = Integer.parseInt(last.substring(last.indexOf('|') + 1));
        h.assertTrue(HeadDonor.drawn().size() == before + 1 && tq >= HeadDonor.MIN - HeadDonor.HUNGRY && tq <= HeadDonor.MAX - HeadDonor.HUNGRY,
                "a trophy's sample is " + tq);
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theRightToolForEachHead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        JarredHeadBlockEntity warden = plinth(h, FLOOR, EntityType.WARDEN);
        int before = HeadDonor.drawn().size();
        int refused = HeadDonor.TOOLS_REFUSED;
        useOn(h, p, warden, new ItemStack(ModRegistry.SYRINGE.get()));
        h.assertValueEqual(HeadDonor.drawn().size(), before, "a Syringe through a warden's hide");
        // the wrong tool is a refusal like any other: its sound and the head's shake, not only a line
        h.assertValueEqual(HeadDonor.TOOLS_REFUSED, refused + 1, "the wrong tool refused with sound and shake");
        useOn(h, p, warden, new ItemStack(ModRegistry.LEVIATHAN_NEEDLE.get()));
        h.assertValueEqual(HeadDonor.drawn().size(), before + 1, "a Leviathan Needle on the warden head");
        JarredHeadBlockEntity skeleton = plinth(h, FLOOR.east(2), EntityType.SKELETON);
        useOn(h, p, skeleton, new ItemStack(ModRegistry.SYRINGE.get()));
        h.assertValueEqual(HeadDonor.drawn().size(), before + 1, "a Syringe on a skeleton head");
        useOn(h, p, skeleton, new ItemStack(ModRegistry.MARROW_DRILL.get()));
        h.assertValueEqual(HeadDonor.drawn().size(), before + 2, "a Marrow Drill on the skeleton head");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aStrangerCannotBleedOrTakeSomeonesStaff(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        ServerPlayer stranger = player(h, FLOOR.south(4));
        JarredHeadBlockEntity cow = fed(h, p, FLOOR, EntityType.COW);
        useOn(h, stranger, cow, new ItemStack(ModRegistry.PRECISION_SYRINGE.get()));
        h.assertValueEqual(Refusals.lastDelivered(stranger), "staff.not_yours", "a stranger's draw");
        stranger.gameMode.destroyBlock(cow.getBlockPos());
        h.assertTrue(h.getBlockState(FLOOR).is(ModRegistry.JARRED_HEAD_BLOCK.get()), "a stranger broke someone's staff head");
        done(h, p, stranger);
    }

    @GameTest(template = "platform")
    public static void theBoardGivesThroughTheArray(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        h.setBlock(FLOOR, ModRegistry.CEREBRAL_ARRAY.get().defaultBlockState());
        var array = (CerebralArrayBlockEntity) h.getBlockEntity(FLOOR);
        for (int i = 0; i < 3; i++) array.items().setStackInSlot(i, jarredHead(h, EntityType.ZOMBIE, null));
        int potential = array.potential();
        ItemStack syringe = new ItemStack(ModRegistry.PRECISION_SYRINGE.get(), 1);
        int before = HeadDonor.drawn().size();
        for (int i = 0; i < 3; i++) HeadDonor.array(p, syringe, InteractionHand.MAIN_HAND, array);
        h.assertValueEqual(HeadDonor.drawn().size(), before + 3, "three draws from three directors");
        HeadDonor.array(p, syringe, InteractionHand.MAIN_HAND, array);
        h.assertValueEqual(Refusals.lastDelivered(p), "library.board_rested", "a fourth draw");
        h.assertValueEqual(array.potential(), potential, "the Array's output after the draws");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aLeechJarBesideAPlinthDrawsItOnceADayAndStillFarms(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        JarredHeadBlockEntity cow = fed(h, p, FLOOR, EntityType.COW);
        h.setBlock(FLOOR.east(), ModRegistry.LEECH_JAR.get());
        var jar = (LeechJarBlockEntity) h.getBlockEntity(FLOOR.east());
        var pig = h.spawn(EntityType.PIG, FLOOR.east().south());
        pig.setNoAi(true);
        h.assertTrue(jar.feed(h.getLevel()), "the first feeding took nothing");
        h.assertTrue(GeneticsItems.species(jar.items().getStackInSlot(0)) == EntityType.COW, "the first feeding did not draw the head");
        h.assertTrue(jar.feed(h.getLevel()), "the second feeding took nothing");
        h.assertTrue(GeneticsItems.species(jar.items().getStackInSlot(1)) == EntityType.PIG, "the second feeding did not farm the pig");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void bloodBankAfterSevenDays(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        JarredHeadBlockEntity cow = fed(h, p, FLOOR, EntityType.COW);
        cow.libraryDays = HeadDonor.BANK - 1;
        useOn(h, p, cow, new ItemStack(ModRegistry.PRECISION_SYRINGE.get()));
        h.assertTrue(SawTableGameTests.earned(h, p, "blood_bank"), "Blood Bank was not earned on the seventh day");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void aPhantomAndACreeperMeetAFedCatHead(GameTestHelper h) {
        // loads both creatures with their head-at-work goals (the phantom's mixin, the creeper's flight) and ticks them
        ServerPlayer p = player(h, FLOOR.south(3));
        JarredHeadBlockEntity cat = fed(h, p, FLOOR, EntityType.CAT);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        h.assertTrue(DeskIndex.near(h.getLevel(), "cat", Vec3.atCenterOf(cat.getBlockPos()), DeskJobs.CAT_CREEPERS) != null, "the fed cat head is not indexed");
        var phantom = h.spawn(EntityType.PHANTOM, FLOOR.east(3).above(3));
        var creeper = h.spawn(EntityType.CREEPER, FLOOR.east(2));
        h.runAfterDelay(60, () -> {
            h.assertTrue(creeper.isAlive(), "the creeper went off near the cat head");
            h.assertTrue(creeper.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof com.coolfreeze23.headinajar.block.HeadEvents.AvoidCatHead),
                    "the creeper has no cat-head goal");
            done(h, p);
        });
    }
}
