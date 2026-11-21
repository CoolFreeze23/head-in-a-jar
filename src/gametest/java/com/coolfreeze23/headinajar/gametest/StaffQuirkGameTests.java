package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.mixin.QuirkPiglinAccessor;
import com.coolfreeze23.headinajar.mixin.VillagerAccessor;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.work.Movers;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.QuirkState;
import com.coolfreeze23.headinajar.staff.work.Quirks;
import com.coolfreeze23.headinajar.staff.work.WorkRules;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The quirks (0.7.0): vanilla temperaments a worker keeps. {@code platform}: 15 x 6 x 15, the floor's top at helper y 1;
 * {@code platform32}: 32 x 10 x 32, the floor's top at helper y 5. A worker with no clock is awaiting assignment; where a
 * test needs a state the site would give it, it sets that state with the reason.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class StaffQuirkGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);
    private static final BlockPos CENTRE = new BlockPos(16, 6, 16);

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        return TestPlayers.at(h, "quirk", at);
    }

    private static void bye(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        bye(h, players);
        h.succeed();
    }

    private static <T extends Mob> T hire(GameTestHelper h, ServerPlayer p, EntityType<T> type, BlockPos at) {
        return hire(h, p, h.spawn(type, at));
    }

    /** Hired through the Lanyard, a hostile worn to half its health first. */
    private static <T extends Mob> T hire(GameTestHelper h, ServerPlayer p, T mob) {
        if (mob instanceof Enemy) mob.setHealth(mob.getMaxHealth() / 2);
        if (!Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), mob)) {
            // the Lanyard refuses a species whose movement family this build does not drive yet (the movers' part);
            // the quirk is what is tested here, so it is hired as the Lanyard would once that family is driven
            WorkTable.Spec spec = WorkTable.of(mob);
            h.assertTrue(spec != null && spec.refusal() == null && !Movers.canMove(mob, spec), "the Lanyard refused a " + mob.getType());
            Workforce.hire(p, mob);
        }
        h.assertTrue(Workforce.isWorker(mob), "not hired: " + mob.getType());
        return mob;
    }

    private static WorkerData data(Mob mob) {
        return Workforce.data(mob);
    }

    private static TimeClockBlockEntity clock(GameTestHelper h, BlockPos at, ServerPlayer owner) {
        h.setBlock(at, WorkRegistry.TIME_CLOCK.get());
        TimeClockBlockEntity c = (TimeClockBlockEntity) h.getBlockEntity(at);
        c.setOwner(owner);
        return c;
    }

    /** A Job Card for this site with a Pick-Up at each of {@code stops} (absolute positions). */
    private static ItemStack card(TimeClockBlockEntity c, BlockPos... stops) {
        ItemStack s = new ItemStack(WorkRegistry.JOB_CARD.get());
        List<Order.Stop> list = Arrays.stream(stops).map(p -> new Order.Stop(p, Direction.UP, false, ItemStack.EMPTY, false)).toList();
        s.set(WorkRegistry.JOB_CARD_DATA.get(), new Order.Card(new Order(Optional.of(c.site()), WorkTable.HAULING, list, List.of(), ""), "", 0));
        return s;
    }

    private static void look(ServerPlayer p, Vec3 at) {
        p.lookAt(EntityAnchorArgument.Anchor.EYES, at);
        p.setYHeadRot(p.getYRot());
    }

    // --- the enderman ------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void endermenStopWhenStaredAt(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(10, 6, 16));
        EnderMan e = hire(h, p, EntityType.ENDERMAN, CENTRE);
        WorkerData d = data(e);
        look(p, e.getEyePosition());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Quirks.holds(e, d), "a stared-at enderman went on"))
                .thenExecute(() -> {
                    h.assertValueEqual(d.status, "staff.headinajar.say.do_you_mind", "its status");
                    look(p, p.getEyePosition().add(-10, 0, 0));
                })
                .thenWaitUntil(() -> h.assertFalse(Quirks.holds(e, d), "the enderman stayed stopped after the look away"))
                .thenExecute(() -> bye(h, p))
                .thenSucceed();
    }

    @GameTest(template = "platform")
    public static void endermenNeverTeleportByThemselves(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        EnderMan e = hire(h, p, EntityType.ENDERMAN, FLOOR);
        EnderMan stranger = h.spawn(EntityType.ENDERMAN, FLOOR.east(4));
        Vec3 at = e.position();
        // hurt by something not alive, a vanilla enderman teleports nine times in ten
        for (int i = 0; i < 20; i++) {
            e.invulnerableTime = 0;
            e.hurt(h.getLevel().damageSources().generic(), 0.5F);
        }
        h.assertTrue(e.position().distanceTo(at) < 0.01, "a staff enderman teleported when hurt");
        h.assertTrue(EventHooks.onEnderTeleport(e, at.x + 10, at.y, at.z).isCanceled(), "a staff enderman's own teleport went ahead");
        h.assertFalse(EventHooks.onEnderTeleport(stranger, at.x + 10, at.y, at.z).isCanceled(), "a vanilla enderman's teleport was cancelled");
        stranger.discard();
        done(h, p);
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void anEndermanGetsOutOfTheRain(GameTestHelper h) {
        // weather is the whole server's: water stands in for the rain (the same vanilla test, isInWaterRainOrBubble)
        ServerPlayer p = player(h, new BlockPos(2, 6, 2));
        TimeClockBlockEntity c = clock(h, CENTRE, p);
        BlockPos puddle = new BlockPos(25, 5, 16);
        h.setBlock(puddle, Blocks.WATER);
        EnderMan e = hire(h, p, EntityType.ENDERMAN, puddle);
        h.assertTrue(c.join(e), "the clock had no room");
        WorkerData d = data(e);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(WorkRules.underAwning(e, d), "the wet enderman did not hop under its clock's awning"))
                .thenIdle(2) // its wetness is read again at its next tick
                .thenExecute(() -> {
                    h.assertFalse(e.isInWaterRainOrBubble(), "it hopped into the wet");
                    float before = e.getHealth();
                    e.invulnerableTime = 0;
                    h.assertFalse(e.hurt(h.getLevel().damageSources().drown(), 1), "water hurt it under its awning");
                    h.assertTrue(e.getHealth() == before, "it lost health under its awning");
                })
                .thenWaitUntil(() -> h.assertFalse(Quirks.holds(e, d), "dry, and no rain, it still waits"))
                .thenExecute(() -> bye(h, p))
                .thenSucceed();
    }

    // --- the cat -----------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 500)
    public static void aCatSitsOnAMarkedChest(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 6, 2));
        TimeClockBlockEntity c = clock(h, CENTRE, p);
        BlockPos chestAt = new BlockPos(21, 6, 16);
        h.setBlock(chestAt, Blocks.CHEST);
        BlockPos chest = h.absolutePos(chestAt);
        c.rack().setStackInSlot(0, card(c, chest));
        Cat cat = hire(h, p, EntityType.CAT, new BlockPos(11, 6, 16));
        h.assertTrue(c.join(cat), "the clock had no room");
        WorkerData d = data(cat);
        Quirks.state(cat).hurry();
        ServerLevel level = h.getLevel();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(cat.isInSittingPose() && ChestBlock.isChestBlockedAt(level, chest), "the idle cat is not sitting on the marked chest"))
                .thenExecute(() -> {
                    h.assertTrue(Quirks.holds(cat, d), "a sitting cat is not held, so its site would walk it off");
                    Quirks.state(cat).hurry(); // its while is up
                })
                .thenWaitUntil(() -> h.assertTrue(!cat.isInSittingPose() && !ChestBlock.isChestBlockedAt(level, chest) && !Quirks.holds(cat, d),
                        "the cat did not get up again"))
                .thenExecute(() -> bye(h, p))
                .thenSucceed();
    }

    @GameTest(template = "platform32", timeoutTicks = 120)
    public static void catsKeepCreepersOut(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 6, 2));
        TimeClockBlockEntity c = clock(h, CENTRE, p);
        Cat cat = hire(h, p, EntityType.CAT, new BlockPos(8, 6, 16));
        h.assertTrue(c.join(cat), "the clock had no room");
        Creeper creeper = h.spawn(EntityType.CREEPER, new BlockPos(25, 6, 16));
        BlockPos site = h.absolutePos(CENTRE);
        h.startSequence()
                .thenWaitUntil(() -> {
                    Path path = creeper.getNavigation().getPath();
                    h.assertTrue(path != null && !Workforce.inBox(site, path.getTarget()), "the creeper was not sent out of the site: "
                            + (path == null ? null : path.getTarget()));
                })
                .thenExecute(() -> {
                    creeper.discard();
                    bye(h, p);
                })
                .thenSucceed();
    }

    // --- the piglin --------------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aPiglinPocketsOneInSixtyFourAndGivesItBack(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        Piglin piglin = hire(h, p, EntityType.PIGLIN, FLOOR);
        piglin.setImmuneToZombification(true); // it stays a piglin for this test (the next one is about that)
        WorkerData d = data(piglin);
        ItemStack left = Quirks.taken(piglin, d, new ItemStack(Items.GOLD_NUGGET, 63));
        h.assertValueEqual(left.getCount(), 63, "skimmed before the 64th item");
        h.assertTrue(d.pocket.isEmpty(), "something in its pocket before the 64th item");
        left = Quirks.taken(piglin, d, new ItemStack(Items.GOLD_NUGGET, 1));
        h.assertTrue(left.isEmpty() && d.pocket.is(Items.GOLD_NUGGET) && d.pocket.getCount() == 1, "the 64th item did not go into its pocket");
        for (int i = 0; i < 2; i++) h.assertValueEqual(Quirks.taken(piglin, d, new ItemStack(Items.GOLD_NUGGET, 64)).getCount(), 63, "a stack of 64");
        h.assertValueEqual(d.pocket.getCount(), 3, "nuggets in the pocket");
        h.assertValueEqual(Quirks.taken(piglin, d, new ItemStack(Items.COBBLESTONE, 64)).getCount(), 64, "a pocket of nuggets took cobblestone");
        // anyone with an empty hand gets it back
        ServerPlayer passer = player(h, FLOOR.north(2));
        passer.interactOn(piglin, InteractionHand.MAIN_HAND);
        h.assertTrue(d.pocket.isEmpty(), "the pocket was not handed back");
        h.assertValueEqual(passer.getInventory().countItem(Items.GOLD_NUGGET), 3, "nuggets handed back");
        h.assertValueEqual(d.status, "staff.headinajar.say.pocket", "the hand-back line");
        passer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // the nuggets went to the hand that was empty
        passer.interactOn(piglin, InteractionHand.MAIN_HAND);
        h.assertValueEqual(d.status, "staff.headinajar.say.pocket_empty", "an empty pocket did not answer");
        done(h, p, passer);
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void aPiglinZombifiesAndStaysOnStaff(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        Piglin piglin = hire(h, p, EntityType.PIGLIN, FLOOR);
        int number = Staff.of(piglin).number;
        // vanilla's 300 ticks outside the Nether, most of them already served
        ((QuirkPiglinAccessor) piglin).headinajar$setTimeInOverworld(Quirks.CONVERSION_TIME - 20);
        AABB around = new AABB(h.absolutePos(FLOOR)).inflate(8);
        h.succeedWhen(() -> {
            List<ZombifiedPiglin> now = h.getLevel().getEntitiesOfClass(ZombifiedPiglin.class, around, Workforce::isWorker);
            h.assertTrue(now.size() == 1, "zombified piglins on staff: " + now.size());
            ZombifiedPiglin z = now.get(0);
            h.assertFalse(piglin.isAlive(), "the piglin is still there");
            h.assertTrue(p.getUUID().equals(Workforce.employer(z)), "the zombified piglin has another employer");
            StaffFile f = Staff.of(z);
            h.assertTrue(f != null && f.number == number, "the file number changed: " + (f == null ? null : f.number) + " for " + number);
            h.assertTrue(z.hasEffect(MobEffects.CONFUSION), "no nausea, as vanilla gives");
            bye(h, p);
        });
    }

    // --- the llama ---------------------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void anUnpaidLlamaSpitsHarmlessly(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        BlockPos clockAt = FLOOR.north(4);
        TimeClockBlockEntity c = clock(h, clockAt, p);
        Llama llama = hire(h, p, EntityType.LLAMA, FLOOR.east(1));
        h.assertTrue(c.join(llama), "the clock had no room");
        ServerLevel level = h.getLevel();
        StaffFile f = Staff.of(llama);
        f.paidThrough = Staff.day(level) - 1; // an unpaid dawn: its wage was not in the drawer
        float health = p.getHealth();
        AABB around = new AABB(h.absolutePos(FLOOR)).inflate(10);
        BlockPos at = h.absolutePos(clockAt);
        // the press as the game posts it before the block's own use (the clock's screen itself is not opened: a mock
        // player's connection may not be sent a menu)
        Runnable open = () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                p, InteractionHand.MAIN_HAND, at, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
        open.run();
        // its line, read at once (the site's next think writes its own status over it)
        h.assertValueEqual(data(llama).status, "staff.headinajar.say.spit", "its line");
        int spat = level.getEntitiesOfClass(LlamaSpit.class, around, s -> s.getOwner() == llama).size();
        h.assertValueEqual(spat, 1, "spits at whoever opened its clock");
        // one more straight at them, with no spread, so a hit is sure
        LlamaSpit aimed = new LlamaSpit(level, llama);
        aimed.shoot(p.getX() - aimed.getX(), p.getY(0.5) - aimed.getY(), p.getZ() - aimed.getZ(), 1.5F, 0);
        level.addFreshEntity(aimed);
        open.run();
        h.assertValueEqual(level.getEntitiesOfClass(LlamaSpit.class, around, s -> s.getOwner() == llama).size(), spat + 1, "it spat twice in a day");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(level.getEntitiesOfClass(LlamaSpit.class, around, s -> s.getOwner() == llama).isEmpty(), "the spit is still flying"))
                .thenExecute(() -> {
                    h.assertTrue(p.getHealth() == health, "the spit hurt: " + p.getHealth());
                    bye(h, p);
                })
                .thenSucceed();
    }

    // --- the panda ---------------------------------------------------------------------------------------------------

    private static Panda panda(GameTestHelper h, ServerPlayer p, TimeClockBlockEntity c, Panda.Gene gene, BlockPos at) {
        Panda panda = h.spawn(EntityType.PANDA, at);
        panda.setMainGene(gene);
        panda.setHiddenGene(gene);
        hire(h, p, panda);
        h.assertTrue(c.join(panda), "the clock had no room");
        Quirks.state(panda).hurry();
        return panda;
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void pandasWorkByPersonality(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 6, 2));
        TimeClockBlockEntity c = clock(h, CENTRE, p);
        c.timeOverride = 6000; // midday at this clock, whatever the world's time: the Day shift is on
        Panda lazy = panda(h, p, c, Panda.Gene.LAZY, new BlockPos(10, 6, 10));
        Panda worried = panda(h, p, c, Panda.Gene.WORRIED, new BlockPos(22, 6, 10));
        Panda playful = panda(h, p, c, Panda.Gene.PLAYFUL, new BlockPos(10, 6, 22));
        Panda aggressive = panda(h, p, c, Panda.Gene.AGGRESSIVE, new BlockPos(22, 6, 22));
        // on the job, which is when laziness and play show (the card is the jobs' part, not this test's)
        data(lazy).state = WorkerData.State.ON_JOB;
        data(playful).state = WorkerData.State.ON_JOB;
        // and kept there: with no card the site would send them back to the clock, and a panda walking there has no stop
        // to roll at (the site's day is not this test's)
        h.onEachTick(() -> {
            for (Panda pa : new Panda[]{lazy, playful}) {
                data(pa).state = WorkerData.State.ON_JOB;
                if (!pa.isRolling()) com.coolfreeze23.headinajar.mind.MobMotor.halt(pa);
            }
        });
        Spider intruder = h.spawn(EntityType.SPIDER, new BlockPos(23, 6, 22));
        intruder.setNoAi(true);
        float hp = intruder.getHealth();
        // thunder for this one think only, put back at once (the weather is the whole server's)
        ServerLevel level = h.getLevel();
        float rain = level.getRainLevel(1), thunder = level.getThunderLevel(1);
        level.setRainLevel(1);
        level.setThunderLevel(1);
        try {
            Quirks.think(worried, data(worried));
            h.assertTrue(Quirks.holds(worried, data(worried)), "a worried panda went on in thunder");
        } finally {
            level.setRainLevel(rain);
            level.setThunderLevel(thunder);
        }
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(playful.isRolling(), "the playful panda did not roll at its stop"))
                .thenWaitUntil(() -> h.assertTrue(Quirks.holds(lazy, data(lazy)) && lazy.isSitting(), "the lazy panda did not take its break"))
                .thenWaitUntil(() -> h.assertTrue(intruder.getHealth() < hp, "the aggressive panda let the intruder be"))
                .thenWaitUntil(() -> h.assertFalse(Quirks.holds(worried, data(worried)), "the worried panda still waits with no thunder"))
                .thenExecute(() -> {
                    intruder.discard();
                    bye(h, p);
                })
                .thenSucceed();
    }

    // --- the goat, the chicken, the dolphin --------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void goatsRamWithoutDamage(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        Goat goat = hire(h, p, EntityType.GOAT, FLOOR);
        Cow cow = hire(h, p, EntityType.COW, FLOOR.east(2));
        float hp = cow.getHealth();
        Vec3 at = cow.position();
        Quirks.state(goat).hurry();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(cow.position().distanceTo(at) > 0.3, "the goat's ram did not move its colleague"))
                .thenExecute(() -> {
                    h.assertTrue(cow.getHealth() == hp, "the ram hurt: " + cow.getHealth());
                    h.assertTrue(Quirks.state(goat).ramTarget == null, "the goat is still lining up");
                    bye(h, p);
                })
                .thenSucceed();
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void chickensLayIntoTheCarry(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        Chicken hauling = hire(h, p, EntityType.CHICKEN, FLOOR);
        Chicken idle = hire(h, p, EntityType.CHICKEN, FLOOR.east(5));
        WorkerData d = data(hauling);
        d.carried.add(new ItemStack(Items.WHEAT_SEEDS, 2)); // on a trip (the job that gave it the seeds is not this test's)
        // both an egg away, inside the window a think catches
        hauling.eggTime = Workforce.thinkTicks() * 2;
        idle.eggTime = Workforce.thinkTicks() * 2;
        AABB nearHauler = new AABB(hauling.blockPosition()).inflate(2), nearIdle = new AABB(idle.blockPosition()).inflate(2);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(d.carried.stream().anyMatch(s -> s.is(Items.EGG)), "no egg in the carry");
                    h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class, nearIdle, e -> e.getItem().is(Items.EGG)).isEmpty(),
                            "a chicken carrying nothing did not lay as vanilla does");
                })
                .thenExecute(() -> {
                    h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, nearHauler, e -> e.getItem().is(Items.EGG)).isEmpty(),
                            "the hauling chicken laid on the ground");
                    h.assertTrue(hauling.eggTime >= 6000 - 40, "its next egg is not on vanilla's timer: " + hauling.eggTime);
                    h.assertValueEqual(d.carried.size(), 2, "stacks carried");
                    bye(h, p);
                })
                .thenSucceed();
    }

    @GameTest(template = "platform32", timeoutTicks = 400)
    public static void dolphinsSurface(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 6, 2));
        // a pool four deep in the floor
        for (int x = 15; x <= 17; x++) for (int z = 15; z <= 17; z++) for (int y = 2; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
        Dolphin dolphin = hire(h, p, h.spawn(EntityType.DOLPHIN, new BlockPos(16, 2, 16)));
        WorkerData d = data(dolphin);
        dolphin.setAirSupply(1000);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Quirks.holds(dolphin, d), "a dolphin short of air did not stop to surface"))
                .thenWaitUntil(() -> h.assertTrue(dolphin.getAirSupply() >= dolphin.getMaxAirSupply() && !Quirks.holds(dolphin, d),
                        "the dolphin never got its air back: " + dolphin.getAirSupply()))
                .thenExecute(() -> bye(h, p))
                .thenSucceed();
    }

    // --- the villager and the wandering trader -----------------------------------------------------------------------

    private static Villager farmer(GameTestHelper h, ServerPlayer p, BlockPos at) {
        Villager v = h.spawn(EntityType.VILLAGER, at);
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER));
        return hire(h, p, v);
    }

    @GameTest(template = "platform")
    public static void villagersDoNotTradeOnShift(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        Villager v = farmer(h, p, FLOOR);
        WorkerData d = data(v);
        d.state = WorkerData.State.ON_JOB; // on the job, which is what the rule reads (its card and site are not this test's)
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD));
        p.interactOn(v, InteractionHand.MAIN_HAND);
        h.assertFalse(p.containerMenu instanceof MerchantMenu, "a villager on the job opened its trades");
        h.assertValueEqual(d.status, "staff.headinajar.say.on_the_clock", "its line");
        d.state = WorkerData.State.OFF_SHIFT; // off shift: standing in the line by its clock
        p.interactOn(v, InteractionHand.MAIN_HAND);
        h.assertTrue(p.containerMenu instanceof MerchantMenu, "a villager off shift would not trade");
        p.closeContainer();
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void villagersLevelAndRestockOffShift(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        Villager v = farmer(h, p, FLOOR);
        data(v).state = WorkerData.State.OFF_SHIFT; // off shift: standing in the line by its clock
        MerchantOffer first = v.getOffers().get(0);
        // a trade, as the trading screen makes it, the last experience it needs for its next level
        v.setVillagerXp(VillagerData.getMaxXpPerLevel(1) - 1);
        v.setTradingPlayer(p);
        v.notifyTrade(first);
        v.setTradingPlayer(null);
        h.assertTrue(((VillagerAccessor) v).headinajar$levelUp(), "the trade did not earn a level");
        h.assertValueEqual(first.getUses(), 1, "uses of the traded offer");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.getVillagerData().getLevel() == 2 && v.hasEffect(MobEffects.REGENERATION),
                        "no level up off shift: level " + v.getVillagerData().getLevel()))
                // a night passes: the day it last restocked is set back one
                .thenExecute(() -> Quirks.state(v).restockDay = Staff.day(h.getLevel()) - 1)
                .thenWaitUntil(() -> h.assertValueEqual(first.getUses(), 0, "its trades did not restock at dawn"))
                .thenExecute(() -> {
                    QuirkState q = Quirks.state(v);
                    h.assertValueEqual(q.restockDay, Staff.day(h.getLevel()), "the restock day");
                    bye(h, p);
                })
                .thenSucceed();
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void aStaffWanderingTraderNeverLeaves(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        WanderingTrader trader = h.spawn(EntityType.WANDERING_TRADER, FLOOR);
        trader.setDespawnDelay(3);
        hire(h, p, trader);
        h.assertValueEqual(trader.getDespawnDelay(), 0, "its despawn delay once hired");
        h.assertValueEqual(data(trader).oldDespawnDelay, 3, "the delay kept for Let go");
        h.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(Workforce.present(trader), "the staff wandering trader left");
                    h.assertValueEqual(trader.getDespawnDelay(), 0, "its despawn delay");
                    bye(h, p);
                })
                .thenSucceed();
    }

    // --- the iron golem and the witch --------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void anIronGolemOffersAPoppy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        IronGolem golem = h.spawn(EntityType.IRON_GOLEM, FLOOR);
        golem.setPlayerCreated(true); // built, not the village's
        hire(h, p, golem);
        farmer(h, p, FLOOR.east(2));
        Quirks.state(golem).hurry();
        h.succeedWhen(() -> {
            h.assertTrue(golem.getOfferFlowerTick() > 0, "the golem held out no poppy to its villager colleague");
            bye(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void aWitchSplashesRegeneration(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        Witch witch = hire(h, p, EntityType.WITCH, FLOOR);
        Cow cow = hire(h, p, EntityType.COW, FLOOR.east(3));
        cow.setHealth(4);
        Quirks.state(witch).hurry();
        int[] thrown = {0};
        h.onEachTick(() -> thrown[0] += h.getLevel().getEntitiesOfClass(ThrownPotion.class, new AABB(h.absolutePos(FLOOR)).inflate(8)).size());
        h.succeedWhen(() -> {
            h.assertTrue(cow.hasEffect(MobEffects.REGENERATION), "the hurt colleague got no Regeneration (potion ticks seen " + thrown[0]
                    + ", drinking " + witch.isDrinkingPotion() + ", next splash " + Quirks.state(witch).nextSplash + " now " + h.getLevel().getGameTime() + ")");
            bye(h, p);
        });
    }
}
