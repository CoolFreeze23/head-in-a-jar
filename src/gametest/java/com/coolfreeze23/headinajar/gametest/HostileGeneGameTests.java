package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneEffects;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The hostile creatures' genes (0.6.2): one test per gene that needs a mixin (vanilla changed for the carrier only),
 * the Rehabilitated discount, Undertow Stride's seabed speed, the balance fixes' numbers, and every gene ticking on a
 * cow host. Bodies and hosts take 70% of a gene's benefit, so a full-strength serum's +1 is +0.7 here.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class HostileGeneGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);
    private static final float K = 0.7f;
    /** The 17 genes of this stream. */
    private static final Gene[] HOSTILE = {Gene.UNDEATH, Gene.SUN_BAKED, Gene.REHABILITATED, Gene.GILDED_ROT, Gene.RAMPAGE,
            Gene.BOWSTRING_ARMS, Gene.RIME_ARROWS, Gene.SPORE_ARROWS, Gene.UNDERTOW_STRIDE, Gene.SNOW_TRAIL, Gene.GOLD_STANDARD,
            Gene.BRUTE_STRENGTH, Gene.BREAKING_AND_ENTERING, Gene.STEADY_AIM, Gene.POTION_TOLERANCE, Gene.PEARL_DIGESTION,
            Gene.STONE_LUNGS};

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer p = SawTableGameTests.player(h, at);
        p.spawnInvulnerableTime = 0;
        return p;
    }

    /** Puts a full-strength serum of {@code genes} into the body (no rejection roll). */
    private static <T extends LivingEntity> T give(T body, Gene... genes) {
        Genetics.genes(body).serums.add(new Serum(java.util.Arrays.stream(genes).map(g -> new Serum.Entry(g, 1f)).toList(), 100));
        Genetics.genes(body).changed();
        return body;
    }

    private static void near(GameTestHelper h, double actual, double expected, String what) {
        if (Math.abs(actual - expected) > 0.01) throw new GameTestAssertException(what + ": " + actual + ", expected " + expected);
    }

    private static Object call(Class<?> owner, String name, Object self, Class<?>[] types, Object... args) {
        try {
            Method m = owner.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(self, args);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("could not call " + name + ": " + e);
        }
    }

    /** A cow with plenty of health and no armour, for counting damage. */
    private static Cow dummy(GameTestHelper h, BlockPos at) {
        Cow cow = h.spawn(EntityType.COW, at);
        cow.setNoAi(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        cow.setHealth(200);
        return cow;
    }

    private static float hit(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
        victim.invulnerableTime = 0;
        float before = victim.getHealth();
        victim.hurt(source, amount);
        return before - victim.getHealth();
    }

    // --- one per mixin ---------------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void undeathHealsOnHarmingAndShrugsOffPoison(GameTestHelper h) {
        ServerPlayer dead = give(player(h, FLOOR), Gene.UNDEATH);
        ServerPlayer alive = player(h, FLOOR.east(3));
        dead.setHealth(10);
        alive.setHealth(10);
        h.assertTrue(dead.isInvertedHealAndHarm() && !alive.isInvertedHealAndHarm(), "only the carrier is inverted");
        MobEffects.HARM.value().applyInstantenousEffect(null, null, dead, 0, 1.0);
        MobEffects.HARM.value().applyInstantenousEffect(null, null, alive, 0, 1.0);
        h.assertTrue(dead.getHealth() > 10, "Instant Damage did not heal the carrier: " + dead.getHealth());
        h.assertTrue(alive.getHealth() < 10, "Instant Damage healed a living player: " + alive.getHealth());
        h.assertFalse(dead.addEffect(new MobEffectInstance(MobEffects.POISON, 100)), "Poison took on the carrier");
        h.assertFalse(dead.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100)), "Regeneration took on the carrier");
        h.assertTrue(alive.addEffect(new MobEffectInstance(MobEffects.POISON, 100)), "Poison did not take on a living player");
        // zombies leave the carrier alone until it strikes one
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.south(4));
        zombie.setNoAi(true);
        zombie.setTarget(dead);
        h.assertTrue(zombie.getTarget() == null, "a zombie took the carrier for a target");
        zombie.setTarget(alive);
        h.assertTrue(zombie.getTarget() == alive, "a zombie would not target a living player");
        zombie.setTarget(null);
        zombie.hurt(dead.damageSources().playerAttack(dead), 1);
        zombie.setTarget(dead);
        h.assertTrue(zombie.getTarget() == dead, "a struck zombie still ignores the carrier");
        SawTableGameTests.done(h, dead, alive);
    }

    @GameTest(template = "platform")
    public static void rehabilitatedGivesTheCuredDiscountAtSeventyPercent(GameTestHelper h) {
        ServerPlayer carrier = give(player(h, FLOOR), Gene.REHABILITATED);
        ServerPlayer curer = player(h, FLOOR.east(3));
        Villager villager = h.spawn(EntityType.VILLAGER, FLOOR.south(3));
        villager.setNoAi(true);
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 30), new ItemStack(Items.BOOK), 12, 1, 0.05f));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 30), new ItemStack(Items.BOOKSHELF), 12, 1, 0.2f));
        villager.setOffers(offers);
        // the player who really cured it: MAJOR_POSITIVE 20 (x5) + MINOR_POSITIVE 25 = 125
        villager.getGossips().add(curer.getUUID(), GossipType.MAJOR_POSITIVE, 20);
        villager.getGossips().add(curer.getUUID(), GossipType.MINOR_POSITIVE, 25);
        Class<?>[] sig = {Player.class};
        call(Villager.class, "updateSpecialPrices", villager, sig, curer);
        h.assertTrue(offers.get(0).getSpecialPriceDiff() == -6 && offers.get(1).getSpecialPriceDiff() == -25,
                "the curer's discount " + offers.get(0).getSpecialPriceDiff() + " / " + offers.get(1).getSpecialPriceDiff());
        offers.forEach(MerchantOffer::resetSpecialPriceDiff);
        call(Villager.class, "updateSpecialPrices", villager, sig, carrier);
        h.assertTrue(offers.get(0).getSpecialPriceDiff() == -4 && offers.get(1).getSpecialPriceDiff() == -17,
                "the carrier's discount (87 reputation) " + offers.get(0).getSpecialPriceDiff() + " / " + offers.get(1).getSpecialPriceDiff());
        offers.forEach(MerchantOffer::resetSpecialPriceDiff);
        // hit a villager: the discount is gone for a day
        villager.hurt(carrier.damageSources().playerAttack(carrier), 0.5f);
        call(Villager.class, "updateSpecialPrices", villager, sig, carrier);
        h.assertTrue(offers.get(0).getSpecialPriceDiff() >= 0 && offers.get(1).getSpecialPriceDiff() >= 0,
                "the discount survived hitting a villager: " + offers.get(1).getSpecialPriceDiff());
        SawTableGameTests.done(h, carrier, curer);
    }

    @GameTest(template = "platform")
    public static void gildedRotKeepsAGoldenEdgeOnTheUndeadOnly(GameTestHelper h) {
        ServerPlayer carrier = give(player(h, FLOOR), Gene.GILDED_ROT);
        ServerPlayer plain = player(h, FLOOR.east(3));
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.south(3));
        zombie.setNoAi(true);
        Cow cow = dummy(h, FLOOR.south(5));
        ItemStack carried = new ItemStack(Items.GOLDEN_SWORD), other = new ItemStack(Items.GOLDEN_SWORD), onCow = new ItemStack(Items.GOLDEN_SWORD);
        carried.postHurtEnemy(zombie, carrier);
        other.postHurtEnemy(zombie, plain);
        onCow.postHurtEnemy(cow, carrier);
        h.assertTrue(carried.getDamageValue() == 0, "the carrier's golden sword wore on a zombie: " + carried.getDamageValue());
        h.assertTrue(other.getDamageValue() == 1, "a plain player's golden sword did not wear: " + other.getDamageValue());
        h.assertTrue(onCow.getDamageValue() == 1, "the golden edge was kept on a cow: " + onCow.getDamageValue());
        // +3 (x0.7) on the undead with gold
        carrier.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_SWORD));
        float loss = hit(zombie, carrier.damageSources().playerAttack(carrier), 4);
        h.assertTrue(loss > 4, "no gold bonus on a zombie: " + loss);
        // a zombified piglin you hit fights you alone: the others ignore its call
        ZombifiedPiglin struck = h.spawn(EntityType.ZOMBIFIED_PIGLIN, FLOOR.west(3));
        ZombifiedPiglin friend = h.spawn(EntityType.ZOMBIFIED_PIGLIN, FLOOR.west(5));
        struck.setNoAi(true);
        friend.setNoAi(true);
        struck.setLastHurtByMob(carrier);
        struck.setTarget(carrier);
        friend.setTarget(carrier);
        h.assertTrue(struck.getTarget() == carrier, "the piglin that was hit would not fight");
        h.assertTrue(friend.getTarget() == null, "a piglin that was not hit joined in");
        friend.setTarget(plain);
        h.assertTrue(friend.getTarget() == plain, "a piglin would not join in against a plain player");
        SawTableGameTests.done(h, carrier, plain);
    }

    @GameTest(template = "platform")
    public static void goldStandardIsGoldToPiglins(GameTestHelper h) {
        ServerPlayer carrier = give(player(h, FLOOR), Gene.GOLD_STANDARD);
        ServerPlayer plain = player(h, FLOOR.east(3));
        h.assertTrue(PiglinAi.isWearingGold(carrier), "piglins do not see gold on a Gold Standard carrier");
        h.assertFalse(PiglinAi.isWearingGold(plain), "piglins see gold on a plain player wearing none");
        plain.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        h.assertTrue(PiglinAi.isWearingGold(plain), "vanilla's golden helmet no longer counts");
        SawTableGameTests.done(h, carrier, plain);
    }

    @GameTest(template = "platform")
    public static void bruteStrengthIsHatedInGoldAndNeverBarters(GameTestHelper h) {
        ServerPlayer brute = give(player(h, FLOOR), Gene.BRUTE_STRENGTH);
        ServerPlayer plain = player(h, FLOOR.east(3));
        brute.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        plain.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        h.assertFalse(PiglinAi.isWearingGold(brute), "piglins are neutral to a brute in gold");
        h.assertTrue(PiglinAi.isWearingGold(plain), "piglins are hostile to a plain player in gold");
        Piglin piglin = h.spawn(EntityType.PIGLIN, FLOOR.south(3));
        piglin.setNoAi(true);
        Class<?>[] sig = {Piglin.class, ItemStack.class};
        ItemEntity thrown = brute.drop(new ItemStack(Items.GOLD_INGOT), false);
        h.assertTrue(thrown != null, "the brute could not throw gold");
        h.assertFalse((Boolean) call(PiglinAi.class, "wantsToPickup", null, sig, piglin, thrown.getItem()), "a piglin wants a brute's gold");
        ItemEntity offered = plain.drop(new ItemStack(Items.GOLD_INGOT), false);
        h.assertTrue((Boolean) call(PiglinAi.class, "wantsToPickup", null, sig, piglin, offered.getItem()), "a piglin turns down a plain player's gold");
        // offering gold by hand is refused with its line
        brute.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT));
        h.assertTrue(brute.interactOn(piglin, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "a brute bartered by hand");
        h.assertValueEqual(Refusals.lastDelivered(brute), "gene.brute_strength.barter", "the barter refusal");
        h.assertTrue(piglin.getOffhandItem().isEmpty(), "the piglin took the brute's gold");
        h.assertFalse(brute.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100)), "Weakness took on a brute");
        SawTableGameTests.done(h, brute, plain);
    }

    @GameTest(template = "platform")
    public static void pearlDigestionProvokesEndermenOnSightAndThrowsFurther(GameTestHelper h) {
        ServerPlayer carrier = give(player(h, FLOOR), Gene.PEARL_DIGESTION);
        ServerPlayer plain = player(h, FLOOR.east(2));
        // both look south (+z); the enderman stands behind them, to the north
        EnderMan enderman = h.spawn(EntityType.ENDERMAN, FLOOR.north(5).east(1));
        enderman.setNoAi(true);
        Class<?>[] sig = {Player.class};
        h.assertTrue((Boolean) call(EnderMan.class, "isLookingAtMe", enderman, sig, carrier), "an enderman ignores a carrier it can see");
        h.assertFalse((Boolean) call(EnderMan.class, "isLookingAtMe", enderman, sig, plain), "an enderman is provoked by a player looking away");
        // the throw: x(1 + 0.22 x 0.7) speed
        ThrownEnderpearl mine = new ThrownEnderpearl(h.getLevel(), carrier), theirs = new ThrownEnderpearl(h.getLevel(), plain);
        mine.shootFromRotation(carrier, 0, 0, 0, 1.5f, 0);
        theirs.shootFromRotation(plain, 0, 0, 0, 1.5f, 0);
        double before = mine.getDeltaMovement().length(), plainBefore = theirs.getDeltaMovement().length();
        h.getLevel().addFreshEntity(mine);
        h.getLevel().addFreshEntity(theirs);
        near(h, mine.getDeltaMovement().length() / before, 1 + 0.22 * K, "the carrier's pearl speed");
        near(h, theirs.getDeltaMovement().length() / plainBefore, 1, "a plain player's pearl speed");
        mine.discard();
        theirs.discard();
        SawTableGameTests.done(h, carrier, plain);
    }

    // --- numbers ---------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void undertowWalksTheSeabedAtLandSpeed(GameTestHelper h) {
        // a pool 2 deep with glass walls: two lanes, a carrier cow and a plain one; a third cow on dry land
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 13; z++) {
                for (int y = 2; y <= 3; y++) {
                    boolean wall = x == 1 || x == 7 || z == 1 || z == 13 || x == 4;
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.GLASS : Blocks.WATER);
                }
            }
        }
        Cow carrier = give(h.spawn(EntityType.COW, new BlockPos(2, 2, 2)), Gene.UNDERTOW_STRIDE);
        Cow plain = h.spawn(EntityType.COW, new BlockPos(5, 2, 2));
        Cow land = h.spawn(EntityType.COW, new BlockPos(10, 2, 2));
        for (Cow c : List.of(carrier, plain, land)) c.setNoAi(true);
        h.runAfterDelay(20, () -> {
            near(h, carrier.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY), 1, "the carrier's water movement efficiency (unscaled)");
            double[] went = new double[3];
            List<Cow> cows = List.of(carrier, plain, land);
            for (int i = 0; i < 3; i++) {
                Cow c = cows.get(i);
                c.setDeltaMovement(Vec3.ZERO);
                c.setYRot(0);
                c.setSpeed(0.25f);
                double z0 = c.getZ();
                // a mob without AI does not move itself (travel skips it): AI on for these steps only
                c.setNoAi(false);
                for (int t = 0; t < 12; t++) c.travel(new Vec3(0, 0, 1));
                c.setNoAi(true);
                went[i] = c.getZ() - z0;
            }
            h.assertTrue(carrier.isInWater() && plain.isInWater() && !land.isInWater(), "the cows are not where they should be");
            h.assertTrue(went[0] >= 0.7 * went[2], "the carrier is slow on the seabed: " + went[0] + " against " + went[2] + " on land");
            h.assertTrue(went[0] > 2.5 * went[1], "the carrier is no faster than a plain cow in water: " + went[0] + " against " + went[1]);
            h.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void rampageTopsOutAtThree(GameTestHelper h) {
        ServerPlayer carrier = give(player(h, FLOOR), Gene.RAMPAGE);
        Cow cow = dummy(h, FLOOR.south(2));
        float[] expected = {1, 1 + K, 1 + 2 * K, 1 + 3 * K, 1 + 3 * K};
        for (int i = 0; i < expected.length; i++) near(h, hit(cow, carrier.damageSources().playerAttack(carrier), 1), expected[i], "hit " + (i + 1));
        SawTableGameTests.done(h, carrier);
    }

    @GameTest(template = "platform")
    public static void bowstringAddsOneAtImpactAndSteadyAimBoltsFlyAQuarterFaster(GameTestHelper h) {
        ServerPlayer archer = give(player(h, FLOOR), Gene.BOWSTRING_ARMS);
        ServerPlayer gunner = give(player(h, FLOOR.east(2)), Gene.STEADY_AIM);
        Cow cow = dummy(h, FLOOR.south(3));
        Arrow arrow = new Arrow(h.getLevel(), archer, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        near(h, hit(cow, archer.damageSources().arrow(arrow, archer), 2), 2 + K, "Bowstring Arms' arrow");
        Arrow bolt = new Arrow(h.getLevel(), gunner, new ItemStack(Items.ARROW), new ItemStack(Items.CROSSBOW));
        near(h, hit(cow, gunner.damageSources().arrow(bolt, gunner), 2), 2, "Steady Aim adds nothing flat");
        // the bolt flies x(1 + 0.25 x 0.7); a bow's arrow does not
        Arrow shot = new Arrow(h.getLevel(), gunner, new ItemStack(Items.ARROW), new ItemStack(Items.CROSSBOW));
        Arrow bowShot = new Arrow(h.getLevel(), gunner, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        shot.shoot(0, 0, 1, 3.15f, 0);
        bowShot.shoot(0, 0, 1, 3, 0);
        double before = shot.getDeltaMovement().length(), bowBefore = bowShot.getDeltaMovement().length();
        h.getLevel().addFreshEntity(shot);
        h.getLevel().addFreshEntity(bowShot);
        near(h, shot.getDeltaMovement().length() / before, 1 + 0.25 * K, "the bolt's speed");
        near(h, bowShot.getDeltaMovement().length() / bowBefore, 1, "a bow arrow's speed");
        shot.discard();
        bowShot.discard();
        // draws: a bow in 14 ticks (Bowstring), a bow in 24 and a crossbow load in 15 instead of 25 (Steady Aim)
        h.assertValueEqual(drawTicks(archer, new ItemStack(Items.BOW), 20), 14, "Bowstring Arms' bow draw");
        h.assertValueEqual(drawTicks(gunner, new ItemStack(Items.BOW), 20), 24, "Steady Aim's bow draw");
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        int charge = net.minecraft.world.item.CrossbowItem.getChargeDuration(crossbow, gunner);
        h.assertValueEqual(drawTicks(gunner, crossbow, charge), (int) Math.ceil(charge * 0.6 - 1e-6), "Steady Aim's crossbow load");
        h.assertValueEqual(drawTicks(player(h, FLOOR.west(2)), new ItemStack(Items.BOW), 20), 20, "a plain player's bow draw");
        SawTableGameTests.done(h, archer, gunner);
    }

    /** Real ticks until {@code stack} has been drawn {@code full} ticks' worth, through the use events as vanilla runs them. */
    private static int drawTicks(LivingEntity e, ItemStack stack, int full) {
        int duration = stack.getUseDuration(e);
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Start(e, stack, InteractionHand.MAIN_HAND, duration));
        int remaining = duration;
        for (int n = 1; n <= 100; n++) {
            remaining = NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Tick(e, stack, remaining)).getDuration();
            remaining--;
            if (duration - remaining >= full) return n;
        }
        return -1;
    }

    @GameTest(template = "platform")
    public static void sunBakedLeavesPlayersHungryAndCreaturesWeak(GameTestHelper h) {
        ServerPlayer carrier = give(player(h, FLOOR), Gene.SUN_BAKED);
        ServerPlayer other = player(h, FLOOR.east(2));
        Cow cow = dummy(h, FLOOR.south(2));
        hit(cow, carrier.damageSources().playerAttack(carrier), 1);
        // the test server forbids PvP; allowed for this one hit, then put back
        var server = h.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        server.setPvpAllowed(true);
        try {
            hit(other, carrier.damageSources().playerAttack(carrier), 1);
        } finally {
            server.setPvpAllowed(pvp);
        }
        h.assertTrue(cow.hasEffect(MobEffects.WEAKNESS) && cow.getEffect(MobEffects.WEAKNESS).getDuration() <= 60, "the cow is not weakened for 3 s");
        h.assertTrue(other.hasEffect(MobEffects.HUNGER) && !other.hasEffect(MobEffects.WEAKNESS), "the player is not made hungry");
        SawTableGameTests.done(h, carrier, other);
    }

    @GameTest(template = "platform")
    public static void potionToleranceLengthensDrinksAndMilkDoesNothing(GameTestHelper h) {
        ServerPlayer carrier = give(player(h, FLOOR), Gene.POTION_TOLERANCE);
        ServerPlayer plain = player(h, FLOOR.east(2));
        for (ServerPlayer p : List.of(carrier, plain)) {
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 400));
            ItemStack milk = new ItemStack(Items.MILK_BUCKET);
            milk.finishUsingItem(h.getLevel(), p);
        }
        h.assertTrue(carrier.hasEffect(MobEffects.DIG_SPEED), "milk cured the carrier");
        h.assertFalse(plain.hasEffect(MobEffects.DIG_SPEED), "milk no longer cures a plain player");
        ItemStack potion = PotionContents.createItemStack(Items.POTION, Potions.LONG_SWIFTNESS);
        ItemStack drunk = potion.copy();
        ItemStack left = potion.finishUsingItem(h.getLevel(), carrier);
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(carrier, drunk, 0, left));
        int vanilla = 9600;
        h.assertTrue(carrier.hasEffect(MobEffects.MOVEMENT_SPEED), "the potion did nothing");
        near(h, carrier.getEffect(MobEffects.MOVEMENT_SPEED).getDuration(), Math.round(vanilla * (1 + 0.5f * K)), "the drunk potion's length");
        SawTableGameTests.done(h, carrier, plain);
    }

    @GameTest(template = "platform")
    public static void breakingAndEnteringAndStoneLungs(GameTestHelper h) {
        ServerPlayer burglar = give(player(h, FLOOR), Gene.BREAKING_AND_ENTERING);
        ServerPlayer miner = give(player(h, FLOOR.east(2)), Gene.STONE_LUNGS);
        burglar.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        var door = Blocks.OAK_DOOR.defaultBlockState();
        float doorSpeed = NeoForge.EVENT_BUS.post(new PlayerEvent.BreakSpeed(burglar, door, 4, null)).getNewSpeed();
        h.assertTrue(doorSpeed >= 1000, "a wooden door is not one hit: " + doorSpeed);
        float stoneSpeed = NeoForge.EVENT_BUS.post(new PlayerEvent.BreakSpeed(miner, Blocks.DEEPSLATE.defaultBlockState(), 4, null)).getNewSpeed();
        near(h, stoneSpeed, 4 * (1 + 0.2f * K), "Stone Lungs on deepslate");
        Villager villager = h.spawn(EntityType.VILLAGER, FLOOR.south(3));
        villager.setNoAi(true);
        h.assertTrue(burglar.interactOn(villager, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "a villager traded with an axe-holder");
        h.assertValueEqual(Refusals.lastDelivered(burglar), "gene.breaking_and_entering.axe", "the axe refusal");
        float health = miner.getHealth();
        h.assertFalse(miner.hurt(miner.damageSources().inWall(), 2), "Stone Lungs suffocated");
        h.assertTrue(miner.getHealth() == health, "Stone Lungs lost health inside a wall");
        SawTableGameTests.done(h, burglar, miner);
    }

    // --- every gene on a host --------------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 300)
    public static void everyHostileGeneTicksOnACowHost(GameTestHelper h) {
        ServerPlayer sawyer = player(h, new BlockPos(1, 2, 1));
        List<Cow> cows = new ArrayList<>();
        for (int i = 0; i < HOSTILE.length; i++) {
            Cow cow = h.spawn(EntityType.COW, new BlockPos(2 + (i % 5) * 3, 2, 2 + (i / 5) * 3));
            cow.setHealth(1);
            h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), cow, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                    "the saw failed on cow " + i);
            h.assertTrue(Hosts.isHost(cow), "cow " + i + " is no host");
            cow.setHealth(cow.getMaxHealth());
            cow.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            give(cow, HOSTILE[i]);
            h.assertTrue(GeneEffects.has(cow, HOSTILE[i]), "cow " + i + " does not carry " + HOSTILE[i]);
            cows.add(cow);
        }
        int[] start = cows.stream().mapToInt(c -> c.tickCount).toArray();
        h.runAfterDelay(200, () -> {
            for (int i = 0; i < cows.size(); i++) {
                Cow cow = cows.get(i);
                if (!cow.isAlive()) continue;
                h.assertTrue(cow.tickCount - start[i] >= 190, HOSTILE[i] + "'s host stopped ticking");
                h.assertTrue(Genetics.genes(cow).has(HOSTILE[i]), HOSTILE[i] + " left its host");
            }
            SawTableGameTests.done(h, sawyer);
        });
    }
}
