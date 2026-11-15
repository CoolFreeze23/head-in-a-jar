package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneEffects;
import com.coolfreeze23.headinajar.genetics.GeneNumbers;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.genetics.GentleGenes;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The gentler creatures' 0.6.2 genes (plan 4 and 5.1): Stew Glands' dandelion, Ruminant Gut's host on grass, Mountain
 * Hooves' full block and powder snow, Pollinator's number, Fleece against a sculk sensor and a warden, Puffer's room,
 * Roll Up, Arctic Coat, and every one of the fifteen on a cow host for 200 ticks.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class GentleGeneGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);
    private static final AtomicInteger NEXT = new AtomicInteger();

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        p.moveTo(v.x, v.y, v.z, 0, 0);
        return p;
    }

    /** A mock player who is not creative underneath (the game's mock players are), so a warden may take an interest. */
    private static ServerPlayer survivor(GameTestHelper h, BlockPos at) {
        String name = "Gentle" + NEXT.incrementAndGet();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(GameType.SURVIVAL);
        p.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        p.moveTo(v.x, v.y, v.z, 0, 0);
        return p;
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static LegacyRandomSource rolls(float roll) {
        return new LegacyRandomSource(1) {
            @Override
            public float nextFloat() {
                return roll;
            }
        };
    }

    private static Serum serum(Gene... genes) {
        return new Serum(java.util.Arrays.stream(genes).map(g -> new Serum.Entry(g, 1f)).toList(), 100);
    }

    private static void inject(GameTestHelper h, net.minecraft.world.entity.LivingEntity e, Gene gene, Genetics.Target target) {
        Genetics.Result r = Genetics.inject(e, serum(gene), target, rolls(0.5f));
        h.assertTrue(r == Genetics.Result.OK, "injecting " + gene + " into " + e.getType() + ": " + r);
    }

    /** A mock player is not ticked by the game: a test ticks its genes (and its clock) itself. */
    private static void tick(ServerPlayer p) {
        p.tickCount++;
        GeneEffects.tick(p);
    }

    private static <T extends Mob> T host(GameTestHelper h, EntityType<T> type, BlockPos at, ServerPlayer sawyer) {
        T mob = h.spawn(type, at);
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "saw failed on " + type);
        return mob;
    }

    // --- Stew Glands (G4) ------------------------------------------------------------------------------------------

    /** One dandelion: 1 hunger and vanilla's 7 ticks of Saturation, not 21; a poppy's Night Vision three times; one flower per 30 s. */
    @GameTest(template = "platform")
    public static void stewGlandsDandelionKeepsVanillasTime(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        inject(h, p, Gene.STEW_GLANDS, Genetics.Target.BODY);
        p.getFoodData().setFoodLevel(10);
        ItemStack dandelion = new ItemStack(Items.DANDELION, 2);
        p.setItemInHand(InteractionHand.MAIN_HAND, dandelion);
        p.gameMode.useItem(p, h.getLevel(), dandelion, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getFoodData().getFoodLevel() == 11, "a dandelion gave " + (p.getFoodData().getFoodLevel() - 10) + " hunger");
        MobEffectInstance sat = p.getEffect(MobEffects.SATURATION);
        h.assertTrue(sat != null && sat.getDuration() == 7, "Saturation for " + (sat == null ? 0 : sat.getDuration()) + " ticks, not vanilla's 7");
        h.assertTrue(dandelion.getCount() == 1, "the dandelion was not eaten");
        // a second flower inside 30 s is refused with its line, and nothing is eaten
        ItemStack poppy = new ItemStack(Items.POPPY);
        p.setItemInHand(InteractionHand.MAIN_HAND, poppy);
        p.gameMode.useItem(p, h.getLevel(), poppy, InteractionHand.MAIN_HAND);
        h.assertTrue(Refusals.delivered(p).contains("gene.stew.digesting"), "a second flower was not refused: " + Refusals.delivered(p));
        h.assertTrue(poppy.getCount() == 1 && !p.hasEffect(MobEffects.NIGHT_VISION), "the refused poppy was eaten");
        // another carrier's poppy: Night Vision for three times vanilla's 5 s
        ServerPlayer q = player(h, FLOOR.east(2));
        inject(h, q, Gene.STEW_GLANDS, Genetics.Target.BODY);
        q.setItemInHand(InteractionHand.MAIN_HAND, poppy);
        q.gameMode.useItem(q, h.getLevel(), poppy, InteractionHand.MAIN_HAND);
        MobEffectInstance night = q.getEffect(MobEffects.NIGHT_VISION);
        h.assertTrue(night != null && night.getDuration() == 300, "a poppy's Night Vision lasted " + (night == null ? 0 : night.getDuration()));
        done(h, p, q);
    }

    // --- Ruminant Gut (G5) -----------------------------------------------------------------------------------------

    /** A Ruminant host standing on grass for 6,000 ticks: its freshness holds where it was (it grazes back what rot takes). */
    @GameTest(template = "platform", timeoutTicks = 6400)
    public static void ruminantHostOnGrassHoldsOffRot(GameTestHelper h) {
        ServerPlayer sawyer = player(h, FLOOR.east(4));
        Cow cow = host(h, EntityType.COW, FLOOR, sawyer);
        // a pen, so it stays on its grass
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    h.setBlock(FLOOR.offset(dx, 0, dz), Blocks.BARRIER);
                    h.setBlock(FLOOR.offset(dx, 1, dz), Blocks.BARRIER);
                }
            }
        }
        h.assertTrue(h.getBlockState(FLOOR.below()).is(Blocks.GRASS_BLOCK), "the platform is not grass");
        inject(h, cow, Gene.RUMINANT_GUT, Genetics.Target.HOST);
        Hosts.data(cow).freshness = 80;
        h.getLevel().getServer().getPlayerList().remove(sawyer);
        h.runAtTickTime(6010, () -> {
            float f = Hosts.data(cow).freshness;
            h.assertTrue(cow.isAlive() && Math.abs(f - 80) < 0.6f, "after 6,000 ticks on grass the host's freshness is " + f + ", not 80");
            h.succeed();
        });
    }

    // --- Mountain Hooves (G3, G19) ---------------------------------------------------------------------------------

    /** The step is a whole block (unscaled) and climbs one; sneaking it is vanilla's; a non-carrier stays below. */
    @GameTest(template = "platform")
    public static void mountainHoovesStepUpAFullBlock(GameTestHelper h) {
        ServerPlayer goat = player(h, new BlockPos(3, 2, 4));
        ServerPlayer plain = player(h, new BlockPos(3, 2, 10));
        h.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
        h.setBlock(new BlockPos(4, 2, 10), Blocks.STONE);
        inject(h, goat, Gene.MOUNTAIN_HOOVES, Genetics.Target.BODY);
        tick(goat);
        h.assertTrue(Math.abs(goat.getAttributeValue(Attributes.STEP_HEIGHT) - 1.0) < 1.0e-6, "step height " + goat.getAttributeValue(Attributes.STEP_HEIGHT));
        double y0 = goat.getY();
        for (ServerPlayer p : List.of(goat, plain)) {
            for (int i = 0; i < 6; i++) {
                p.setOnGround(true);
                p.move(MoverType.SELF, new Vec3(0.25, -0.05, 0));
            }
        }
        h.assertTrue(goat.getY() >= y0 + 0.99, "the carrier did not step up the block: y " + (goat.getY() - y0));
        h.assertTrue(plain.getY() < y0 + 0.5, "a non-carrier stepped up a full block");
        goat.setShiftKeyDown(true);
        tick(goat);
        h.assertTrue(Math.abs(goat.getAttributeValue(Attributes.STEP_HEIGHT) - 0.6) < 1.0e-6, "sneaking, the step is " + goat.getAttributeValue(Attributes.STEP_HEIGHT));
        done(h, goat, plain);
    }

    /** The powder snow mixin answers for the carrier only; boots go into the pockets. */
    @GameTest(template = "platform")
    public static void mountainHoovesWalkOnPowderSnowForTheCarrierOnly(GameTestHelper h) {
        ServerPlayer goat = player(h, FLOOR);
        ServerPlayer plain = player(h, FLOOR.east(3));
        h.assertFalse(PowderSnowBlock.canEntityWalkOnPowderSnow(goat), "bare feet walked on powder snow before the gene");
        inject(h, goat, Gene.MOUNTAIN_HOOVES, Genetics.Target.BODY);
        h.assertTrue(PowderSnowBlock.canEntityWalkOnPowderSnow(goat), "the carrier sinks into powder snow");
        h.assertFalse(PowderSnowBlock.canEntityWalkOnPowderSnow(plain), "a non-carrier walks on powder snow");
        goat.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        tick(goat);
        h.assertTrue(goat.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "hooves kept their boots");
        h.assertTrue(goat.getInventory().contains(new ItemStack(Items.IRON_BOOTS)), "the boots did not go into the pockets");
        done(h, goat, plain);
    }

    // --- Pollinator (plan 4, flagged) -------------------------------------------------------------------------------

    /**
     * A carrier walked over 25 wheat for 2,400 ticks beside 25 left alone: each plant gets at most a growth tick a
     * minute (2 or 3 in the run, about doubling vanilla's ~1.8 random ticks), and the field grows far less than one bone
     * meal a plant (2 to 5 stages each).
     */
    @GameTest(template = "platform", timeoutTicks = 2600)
    public static void pollinatorGrowsLessThanBoneMeal(GameTestHelper h) {
        List<BlockPos> walked = new ArrayList<>(), alone = new ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                walked.add(new BlockPos(1 + x, 2, 5 + z));
                alone.add(new BlockPos(9 + x, 2, 5 + z));
            }
        }
        for (List<BlockPos> plot : List.of(walked, alone)) {
            for (BlockPos pos : plot) {
                h.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                h.setBlock(pos, Blocks.WHEAT.defaultBlockState());
            }
        }
        h.setBlock(new BlockPos(3, 4, 7), Blocks.LIGHT);
        h.setBlock(new BlockPos(11, 4, 7), Blocks.LIGHT);
        ServerPlayer bee = player(h, new BlockPos(1, 2, 5));
        inject(h, bee, Gene.POLLINATOR, Genetics.Target.BODY);
        int before = GentleGenes.pollinations;
        int[] step = {0};
        h.onEachTick(() -> {
            // a slow lap of the plot, a cell every 4 ticks
            int i = (step[0]++ / 4) % 50;
            int cell = i < 25 ? i : 49 - i;
            BlockPos at = walked.get((cell / 5) % 2 == 0 ? cell : (cell / 5) * 5 + 4 - cell % 5);
            Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
            bee.moveTo(v.x, v.y, v.z, 0, 0);
            tick(bee);
        });
        h.runAtTickTime(2400, () -> {
            int given = GentleGenes.pollinations - before;
            int grownWalked = 0, grownAlone = 0;
            for (BlockPos pos : walked) grownWalked += h.getBlockState(pos).getValue(CropBlock.AGE);
            for (BlockPos pos : alone) grownAlone += h.getBlockState(pos).getValue(CropBlock.AGE);
            HeadInAJar.LOGGER.info("Pollinator over 2,400 ticks: {} growth ticks to 25 plants; the walked field grew {} stages, the field left alone {}",
                    given, grownWalked, grownAlone);
            h.assertTrue(given >= 25 && given <= 75, given + " growth ticks to 25 plants in 2,400 ticks: not at most one a minute each");
            h.assertTrue(grownWalked < 50, "the walked field grew " + grownWalked + " stages: as much as a bone meal a plant");
            done(h, bee);
        });
    }

    // --- Fleece (plan 4, flagged; G12) -----------------------------------------------------------------------------

    private static void fleeced(ServerPlayer p, GameTestHelper h) {
        inject(h, p, Gene.FLEECE, Genetics.Target.BODY);
        Genetics.genes(p).fleece = GeneNumbers.FLEECE_REGROW;
    }

    /** Shears used on a fleeced cow host by a real click are the fleece's, not the host's status line: wool comes off. */
    @GameTest(template = "platform")
    public static void shearsOnAFleecedHostShearIt(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        net.minecraft.world.entity.animal.Cow cow = host(h, EntityType.COW, FLOOR.north(1), p);
        inject(h, cow, Gene.FLEECE, Genetics.Target.HOST);
        Genetics.genes(cow).fleece = GeneNumbers.FLEECE_REGROW;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.SHEARS));
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertTrue(Genetics.genes(cow).fleece < GeneNumbers.FLEECE_REGROW, "the click did not shear the host's fleece");
        done(h, p);
    }

    /** A fleeced carrier's steps and landings 6 blocks from a sculk sensor: it never wakes. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void fleeceKeepsASculkSensorAsleep(GameTestHelper h) {
        BlockPos sensor = FLOOR;
        h.setBlock(sensor, Blocks.SCULK_SENSOR);
        ServerPlayer sheep = player(h, new BlockPos(7, 2, 1));
        fleeced(sheep, h);
        int[] t = {0};
        h.onEachTick(() -> {
            tick(sheep);
            if (++t[0] > 3 && t[0] % 4 == 0) {
                sheep.gameEvent(GameEvent.STEP);
                sheep.gameEvent(GameEvent.HIT_GROUND);
            }
            if (SculkSensorBlock.getPhase(h.getBlockState(sensor)) != SculkSensorPhase.INACTIVE) {
                throw new GameTestAssertException("the sculk sensor heard a fleeced carrier");
            }
        });
        h.runAtTickTime(80, () -> done(h, sheep));
    }

    /** The same steps without a fleece wake the sensor (the check is the carrier's, not a broken sensor). */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void aSculkSensorHearsTheUnfleeced(GameTestHelper h) {
        BlockPos sensor = FLOOR;
        h.setBlock(sensor, Blocks.SCULK_SENSOR);
        ServerPlayer plain = player(h, new BlockPos(7, 2, 1));
        int[] t = {0};
        h.onEachTick(() -> {
            if (++t[0] > 3 && t[0] % 4 == 0) plain.gameEvent(GameEvent.STEP);
        });
        h.succeedWhen(() -> {
            if (SculkSensorBlock.getPhase(h.getBlockState(sensor)) == SculkSensorPhase.INACTIVE) throw new GameTestAssertException("the sensor slept");
            h.getLevel().getServer().getPlayerList().remove(plain);
        });
    }

    /** A warden's anger at an entity, from its saved state (the anger table is not public). */
    private static int anger(Warden warden, Entity at) {
        CompoundTag tag = new CompoundTag();
        warden.addAdditionalSaveData(tag);
        ListTag suspects = tag.getCompound("anger").getList("suspects", Tag.TAG_COMPOUND);
        for (int i = 0; i < suspects.size(); i++) {
            CompoundTag s = suspects.getCompound(i);
            if (s.contains("uuid") && NbtUtils.loadUUID(s.get("uuid")).equals(at.getUUID())) return s.getInt("anger");
        }
        return 0;
    }

    /** A listening warden, penned so it cannot come and sniff (sniffing at 6 blocks angers it whatever you wear). */
    private static Warden warden(GameTestHelper h, BlockPos at) {
        Warden warden = h.spawn(EntityType.WARDEN, at);
        warden.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 1200L);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    if ((dx != 0 || dz != 0) || dy == 3) h.setBlock(at.offset(dx, dy, dz), Blocks.BARRIER);
                }
            }
        }
        return warden;
    }

    /** A fleeced carrier walking 10 blocks from a listening warden for 200 ticks: the warden gains no anger at it. */
    @GameTest(template = "platform", timeoutTicks = 260)
    public static void fleeceWalksPastAListeningWarden(GameTestHelper h) {
        Warden warden = warden(h, new BlockPos(2, 2, 7));
        ServerPlayer sheep = survivor(h, new BlockPos(12, 2, 7));
        fleeced(sheep, h);
        int[] t = {0};
        h.onEachTick(() -> {
            tick(sheep);
            if (++t[0] > 3 && t[0] % 4 == 0) sheep.gameEvent(GameEvent.STEP);
        });
        h.runAtTickTime(200, () -> {
            int a = anger(warden, sheep);
            h.assertTrue(a == 0, "the warden is " + a + " angry at a fleeced carrier's steps");
            done(h, sheep);
        });
    }

    /** The same walk without a fleece angers the warden. */
    @GameTest(template = "platform", timeoutTicks = 260)
    public static void aListeningWardenHearsTheUnfleeced(GameTestHelper h) {
        Warden warden = warden(h, new BlockPos(2, 2, 7));
        ServerPlayer plain = survivor(h, new BlockPos(12, 2, 7));
        int[] t = {0};
        h.onEachTick(() -> {
            if (++t[0] > 3 && t[0] % 4 == 0) plain.gameEvent(GameEvent.STEP);
        });
        h.succeedWhen(() -> {
            if (anger(warden, plain) <= 0) throw new GameTestAssertException("the warden did not hear the steps");
            h.getLevel().getServer().getPlayerList().remove(plain);
        });
    }

    // --- Puffer (G22) ----------------------------------------------------------------------------------------------

    /** Under a low ceiling a zombie close by does not puff the carrier: "No room to puff.", and its size is unchanged. */
    @GameTest(template = "platform")
    public static void pufferWithNoRoomSaysSo(GameTestHelper h) {
        ServerPlayer fish = player(h, FLOOR);
        h.setBlock(FLOOR.above(2), Blocks.STONE);
        inject(h, fish, Gene.PUFFER, Genetics.Target.BODY);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(1));
        zombie.setNoAi(true);
        fish.tickCount = 4;
        tick(fish);
        h.assertTrue(Refusals.delivered(fish).contains("gene.puffer.no_room"), "no refusal: " + Refusals.delivered(fish));
        h.assertFalse(GentleGenes.puffed(fish), "puffed into the ceiling");
        tick(fish);
        h.assertTrue(Math.abs(fish.getAttributeValue(Attributes.SCALE) - 1) < 1.0e-6, "the size changed: " + fish.getAttributeValue(Attributes.SCALE));
        done(h, fish);
    }

    /** With room, the same zombie puffs the carrier 30% bigger and sends the memo. */
    @GameTest(template = "platform")
    public static void pufferPuffsWithRoom(GameTestHelper h) {
        ServerPlayer fish = player(h, FLOOR);
        inject(h, fish, Gene.PUFFER, Genetics.Target.BODY);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(1));
        zombie.setNoAi(true);
        fish.tickCount = 4;
        tick(fish);
        h.assertTrue(GentleGenes.puffed(fish), "a zombie a block away did not puff the carrier");
        tick(fish);
        h.assertTrue(Math.abs(fish.getAttributeValue(Attributes.SCALE) - (1 + GeneNumbers.PUFFER_SCALE)) < 1.0e-6, "puffed size " + fish.getAttributeValue(Attributes.SCALE));
        h.assertTrue(Fx.count(Fx.GENE_PUFFER, fish.getId()) >= 1, "no puff memo");
        done(h, fish);
    }

    // --- Roll Up (G20) and Arctic Coat ------------------------------------------------------------------------------

    /** Sneaking still for a second curls the carrier low (the swimming pose): 60% less damage; letting go uncurls. */
    @GameTest(template = "platform")
    public static void rollUpCurlsLowAndTakesLess(GameTestHelper h) {
        ServerPlayer dillo = player(h, FLOOR);
        inject(h, dillo, Gene.ROLL_UP, Genetics.Target.BODY);
        dillo.setShiftKeyDown(true);
        for (int i = 0; i < GeneNumbers.ROLL_UP_STILL + 3; i++) {
            dillo.setOnGround(true);
            tick(dillo);
        }
        h.assertTrue(GentleGenes.curled(dillo) && dillo.getForcedPose() == Pose.SWIMMING, "not curled after a still second of sneaking");
        float health = dillo.getHealth();
        dillo.hurt(h.getLevel().damageSources().generic(), 10);
        h.assertTrue(Math.abs(health - dillo.getHealth() - 4) < 0.01f, "curled, 10 damage took " + (health - dillo.getHealth()));
        dillo.setShiftKeyDown(false);
        tick(dillo);
        tick(dillo);
        h.assertTrue(!GentleGenes.curled(dillo) && dillo.getForcedPose() == null, "still curled after letting go of sneak");
        done(h, dillo);
    }

    /** Arctic Coat halves every Slowness put on the carrier. */
    @GameTest(template = "platform")
    public static void arcticCoatHalvesSlowness(GameTestHelper h) {
        ServerPlayer bear = player(h, FLOOR);
        inject(h, bear, Gene.ARCTIC_COAT, Genetics.Target.BODY);
        bear.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        MobEffectInstance slow = bear.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        h.assertTrue(slow != null && slow.getDuration() == 50 && slow.getAmplifier() == 1, "Slowness " + slow);
        done(h, bear);
    }

    // --- all fifteen on a host -------------------------------------------------------------------------------------

    private static final Gene[] GENTLE = {Gene.RUMINANT_GUT, Gene.STEW_GLANDS, Gene.SWINE_APPETITE, Gene.FLEECE, Gene.MOUNTAIN_HOOVES,
            Gene.BAMBOO_DIET, Gene.POLLINATOR, Gene.ARCTIC_COAT, Gene.ROLL_UP, Gene.KEEN_NOSE, Gene.MIMICRY, Gene.TADPOLE_TAIL,
            Gene.PUFFER, Gene.DUSK_DRAUGHT, Gene.FETCH};

    /** Each of the fifteen on its own cow host for 200 ticks: no error, the host alive and its gene working. */
    @GameTest(template = "platform", timeoutTicks = 300)
    public static void everyGentleGeneTicksOnACowHost(GameTestHelper h) {
        ServerPlayer sawyer = player(h, new BlockPos(7, 2, 13));
        List<Cow> cows = new ArrayList<>();
        for (int i = 0; i < GENTLE.length; i++) {
            Cow cow = host(h, EntityType.COW, new BlockPos(1 + (i % 5) * 3, 2, 1 + (i / 5) * 4), sawyer);
            inject(h, cow, GENTLE[i], Genetics.Target.HOST);
            if (GENTLE[i] == Gene.FLEECE) Genetics.genes(cow).fleece = GeneNumbers.FLEECE_REGROW;
            cows.add(cow);
        }
        h.getLevel().getServer().getPlayerList().remove(sawyer);
        h.runAtTickTime(200, () -> {
            for (int i = 0; i < GENTLE.length; i++) {
                Cow cow = cows.get(i);
                h.assertTrue(cow.isAlive(), GENTLE[i] + "'s host died");
                h.assertTrue(GeneEffects.has(cow, GENTLE[i]), GENTLE[i] + " is not working on its host");
            }
            h.succeed();
        });
    }
}
