package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.host.HostAbilities;
import com.coolfreeze23.headinajar.host.HostControl;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.HostDefinition;
import com.coolfreeze23.headinajar.host.HostTricks;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** 0.6.3, Bodies at work: every vanilla host body's trick, fired on its own body, and the numbers behind them. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class TrickGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

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

    /** A host made the way the saw makes one, healed up and at {@code sync}. */
    private static <T extends Mob> T host(GameTestHelper h, EntityType<T> type, BlockPos at, ServerPlayer sawyer, float sync) {
        T mob = h.spawn(type, at);
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "setup: the saw failed on " + type);
        mob.setHealth(mob.getMaxHealth());
        Hosts.data(mob).sync = sync;
        return mob;
    }

    /** Turns the player to look at a point (world coordinates). */
    private static void look(ServerPlayer p, Vec3 at) {
        Vec3 d = at.subtract(p.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.setXRot(pitch);
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
    }

    // --- one per trick ---------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void mooHerdsWheatEaters(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 2, 2));
        Cow cow = host(h, EntityType.COW, new BlockPos(2, 2, 3), p, 60);
        Cow a = h.spawn(EntityType.COW, new BlockPos(12, 2, 12));
        Sheep b = h.spawn(EntityType.SHEEP, new BlockPos(12, 2, 8));
        h.spawn(EntityType.PIG, new BlockPos(8, 2, 12));
        double da = a.distanceTo(p), db = b.distanceTo(p);
        int paths = HostTricks.herdPaths;
        h.assertTrue(HostAbilities.use(p, cow, false), "Moo refused: " + Refusals.lastDelivered(p));
        // the pig eats carrots: two asked to follow, not three
        h.assertValueEqual(HostTricks.herdPaths - paths, 2, "paths asked for at the press");
        h.runAfterDelay(100, () -> {
            h.assertTrue(a.distanceTo(p) < da - 3 && b.distanceTo(p) < db - 3, "the herd did not come: cow " + da + " -> " + a.distanceTo(p) + ", sheep " + db + " -> " + b.distanceTo(p));
            // one path each 10 ticks at most, and none for an animal already beside the pilot
            int asked = HostTricks.herdPaths - paths;
            h.assertTrue(asked <= 2 * 11, "Moo asked for " + asked + " paths in 100 ticks for two animals");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void mooWithNobodyToHerdRefuses(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Cow cow = host(h, EntityType.COW, FLOOR.east(), p, 60);
        h.spawn(EntityType.PIG, FLOOR.west(3));
        h.assertFalse(HostAbilities.use(p, cow, false), "Moo fired with nobody to herd");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.no_herd", "the refusal");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void rootingCuesOnlyThePilot(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ServerPlayer other = player(h, FLOOR.west(4));
        var pig = host(h, EntityType.PIG, FLOOR.east(), p, 60);
        h.setBlock(new BlockPos(3, 1, 3), Blocks.FARMLAND);
        h.setBlock(new BlockPos(3, 2, 3), Blocks.CARROTS);
        h.setBlock(new BlockPos(11, 1, 11), Blocks.FARMLAND);
        h.setBlock(new BlockPos(11, 2, 11), Blocks.POTATOES);
        h.assertTrue(HostAbilities.use(p, pig, false), "Rooting refused: " + Refusals.lastDelivered(p));
        // a game without the cue (a test player) is shown each find by the server, to the pilot alone
        h.runAfterDelay(8, () -> {
            h.assertValueEqual(HostTricks.rootedFor(p.getUUID()), 2, "finds shown to the pilot");
            h.assertValueEqual(HostTricks.rootedFor(other.getUUID()), -1, "finds shown to a bystander");
            done(h, p, other);
        });
    }

    @GameTest(template = "platform")
    public static void shedDropsItsOwnColour(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Sheep sheep = host(h, EntityType.SHEEP, FLOOR.east(), p, 60);
        sheep.setColor(DyeColor.RED);
        h.assertTrue(HostAbilities.use(p, sheep, false), "Shed refused: " + Refusals.lastDelivered(p));
        List<ItemEntity> wool = h.getLevel().getEntitiesOfClass(ItemEntity.class, sheep.getBoundingBox().inflate(3));
        int red = wool.stream().filter(e -> e.getItem().is(Items.RED_WOOL)).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(red >= 1 && red <= 3 && red == wool.size(), "Shed dropped " + wool.size() + " stacks, " + red + " red wool");
        h.assertFalse(sheep.isSheared(), "Shed sheared the host");
        sheep.setSheared(true);
        Hosts.data(sheep).primaryCooldown = 0;
        h.assertFalse(HostAbilities.use(p, sheep, false), "a shorn host shed");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.shed_bare", "the refusal");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void sheepHalvesFallDamage(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Sheep sheep = host(h, EntityType.SHEEP, FLOOR.east(), p, 30);
        float before = sheep.getHealth();
        sheep.hurt(h.getLevel().damageSources().fall(), 4);
        h.assertTrue(Math.abs(before - sheep.getHealth() - 2) < 0.01, "a fall of 4 took " + (before - sheep.getHealth()));
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void llamaSpitHitsForOneCreditedToThePilot(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(1, 2, 7));
        var llama = host(h, EntityType.LLAMA, new BlockPos(1, 2, 7), p, 60);
        // no armour to soften the one point
        Cow target = h.spawn(EntityType.COW, new BlockPos(13, 2, 7));
        target.setNoAi(true);
        double start = target.getX();
        look(p, target.getBoundingBox().getCenter());
        float health = target.getHealth();
        h.assertTrue(HostAbilities.use(p, llama, false), "Spit refused: " + Refusals.lastDelivered(p));
        h.succeedWhen(() -> {
            h.assertTrue(target.getHealth() < health, "the spit has not hit at 12 blocks");
            h.assertTrue(Math.abs(health - target.getHealth() - 1) < 0.01, "the spit did " + (health - target.getHealth()));
            h.assertTrue(target.getLastHurtByMob() == p, "the hit was not the pilot's: " + target.getLastHurtByMob());
            h.assertTrue(target.getX() > start + 0.05 || target.getDeltaMovement().x > 0.05, "no knockback");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform")
    public static void bellLightsRaidersAndSendsVillagersIn(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Villager host = host(h, EntityType.VILLAGER, FLOOR.east(), p, 60);
        Pillager raider = h.spawn(EntityType.PILLAGER, new BlockPos(2, 2, 2));
        Villager villager = h.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
        h.assertTrue(HostAbilities.use(p, host, false), "the bell refused: " + Refusals.lastDelivered(p));
        h.assertTrue(raider.hasEffect(MobEffects.GLOWING) && raider.getEffect(MobEffects.GLOWING).getDuration() > 150, "the raider is not lit");
        h.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HEARD_BELL_TIME).isPresent(), "the villager did not hear the bell");
        h.assertFalse(villager.hasEffect(MobEffects.GLOWING), "a villager was lit");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void ironGolemsLeaveAVillagerRiderAlone(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Villager host = host(h, EntityType.VILLAGER, FLOOR.east(), p, 30);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, host), "setup: could not ride the villager");
        var golem = h.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
        golem.setTarget(p);
        h.assertTrue(golem.getTarget() == null, "a golem turned on the villager's rider");
        golem.setTarget(host);
        h.assertTrue(golem.getTarget() == null, "a golem turned on the villager");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void piglinsLeaveAPiglinRiderAlone(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Piglin host = host(h, EntityType.PIGLIN, FLOOR.east(), p, 30);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, host), "setup: could not ride the piglin");
        Piglin other = h.spawn(EntityType.PIGLIN, new BlockPos(2, 2, 2));
        other.setTarget(p);
        h.assertTrue(other.getTarget() == null, "a piglin turned on the piglin's rider");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void barterSpendsAnIngotAndPaysAfterSixSeconds(GameTestHelper h) {
        // the pilot stands off, so it does not pick the loot up itself
        ServerPlayer p = player(h, FLOOR.west(5));
        Piglin piglin = host(h, EntityType.PIGLIN, FLOOR.east(), p, 60);
        p.getInventory().add(new ItemStack(Items.GOLD_INGOT, 2));
        h.assertTrue(HostAbilities.use(p, piglin, false), "Barter refused: " + Refusals.lastDelivered(p));
        h.assertValueEqual(count(p, Items.GOLD_INGOT), 1, "ingots left");
        h.assertTrue(piglin.getOffhandItem().is(Items.GOLD_INGOT), "the piglin is not holding the ingot");
        Hosts.data(piglin).primaryCooldown = 0;
        h.assertFalse(HostAbilities.use(p, piglin, false), "a second barter while one is due");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.barter_busy", "the refusal");
        h.assertValueEqual(count(p, Items.GOLD_INGOT), 1, "an ingot spent on a refused barter");
        long due = Hosts.data(piglin).barterDue;
        h.runAfterDelay(100, () -> h.assertTrue(piglin.getOffhandItem().is(Items.GOLD_INGOT) && Hosts.data(piglin).barterDue == due, "paid early"));
        h.runAfterDelay(160, () -> {
            h.assertTrue(piglin.getOffhandItem().isEmpty() && Hosts.data(piglin).barterDue == 0, "not paid after six seconds");
            List<ItemEntity> loot = h.getLevel().getEntitiesOfClass(ItemEntity.class, piglin.getBoundingBox().inflate(3));
            h.assertFalse(loot.isEmpty(), "no loot at its feet");
            h.assertFalse(loot.stream().anyMatch(e -> e.getItem().is(Items.GOLD_INGOT)), "the ingot dropped as well");
            // the body picks none of it up
            h.assertTrue(piglin.getInventory().isEmpty(), "the piglin pocketed the loot");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void barterSurvivesAnUnload(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(5));
        Piglin piglin = host(h, EntityType.PIGLIN, FLOOR.east(), p, 60);
        p.getInventory().add(new ItemStack(Items.GOLD_INGOT));
        h.assertTrue(HostAbilities.use(p, piglin, false), "Barter refused: " + Refusals.lastDelivered(p));
        long due = Hosts.data(piglin).barterDue;
        CompoundTag tag = new CompoundTag();
        piglin.save(tag);
        piglin.discard();
        var loaded = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
        h.assertTrue(loaded instanceof Piglin, "the piglin did not load");
        Piglin back = (Piglin) loaded;
        h.getLevel().addFreshEntity(back);
        h.assertValueEqual(Hosts.data(back).barterDue, due, "the due time after a reload");
        h.assertTrue(back.getOffhandItem().is(Items.GOLD_INGOT), "the admired ingot after a reload");
        h.runAfterDelay(140, () -> {
            h.assertTrue(back.getOffhandItem().isEmpty() && Hosts.data(back).barterDue == 0, "a reloaded barter never paid");
            h.assertFalse(h.getLevel().getEntitiesOfClass(ItemEntity.class, back.getBoundingBox().inflate(3)).isEmpty(), "no loot after a reload");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void barterWithoutGoldRefuses(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Piglin piglin = host(h, EntityType.PIGLIN, FLOOR.east(), p, 60);
        h.assertFalse(HostAbilities.use(p, piglin, false), "Barter fired without gold");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.no_gold", "the refusal");
        h.assertTrue(piglin.getOffhandItem().isEmpty() && Hosts.data(piglin).barterDue == 0, "a refused barter started");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void warmUpHealsOnLavaOnly(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        var strider = host(h, EntityType.STRIDER, FLOOR, p, 60);
        h.assertFalse(HostAbilities.use(p, strider, false), "Warm Up turned on off lava");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.warm_up_lava", "the refusal");
        h.setBlock(new BlockPos(7, 0, 7), Blocks.STONE);
        h.setBlock(new BlockPos(7, 1, 7), Blocks.LAVA);
        h.runAfterDelay(5, () -> {
            Hosts.data(strider).primaryCooldown = 0;
            h.assertTrue(HostAbilities.use(p, strider, false), "Warm Up refused on lava: " + Refusals.lastDelivered(p));
            h.assertTrue(Hosts.data(strider).warming, "not warming");
            strider.setHealth(5);
        });
        h.runAfterDelay(70, () -> {
            h.assertTrue(strider.getHealth() >= 6, "standing on lava did not heal: " + strider.getHealth());
            Hosts.data(strider).primaryCooldown = 0;
            h.assertTrue(HostAbilities.use(p, strider, false), "Warm Up did not turn off");
            h.assertFalse(Hosts.data(strider).warming, "still warming");
            h.setBlock(new BlockPos(7, 1, 7), Blocks.STONE);
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 140)
    public static void withdrawCutsDamageAndHoldsStill(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var turtle = host(h, EntityType.TURTLE, FLOOR.east(), p, 60);
        h.assertTrue(HostAbilities.use(p, turtle, false), "Withdraw refused: " + Refusals.lastDelivered(p));
        float before = turtle.getHealth();
        turtle.hurt(h.getLevel().damageSources().generic(), 10);
        h.assertTrue(Math.abs(before - turtle.getHealth() - 2) < 0.01, "10 damage withdrawn took " + (before - turtle.getHealth()));
        h.assertTrue(HostTricks.heldStill(turtle), "not held still");
        p.zza = 1;
        h.assertTrue(HostControl.riddenInput(turtle, p).equals(Vec3.ZERO), "forward moved a withdrawn turtle");
        h.runAfterDelay(105, () -> {
            h.assertFalse(HostTricks.heldStill(turtle), "still withdrawn after five seconds");
            h.assertFalse(HostControl.riddenInput(turtle, p).equals(Vec3.ZERO), "forward still moves it nowhere");
            done(h, p);
        });
    }

    /** Fires a bound from the floor along +x and returns where it first lands, measured from where it left. */
    private static void measureBound(GameTestHelper h, ServerPlayer p, Mob body, boolean secondary, double want, String what, Runnable mid) {
        look(p, p.getEyePosition().add(10, 0, 0));
        Vec3 start = body.position();
        h.assertTrue(HostAbilities.use(p, body, secondary), what + " refused: " + Refusals.lastDelivered(p));
        long fired = h.getLevel().getGameTime();
        h.runAfterDelay(3, mid);
        h.succeedWhen(() -> {
            h.assertTrue(h.getLevel().getGameTime() - fired > 3 && body.onGround(), what + " still in the air");
            double d = flat(start, body.position());
            h.assertTrue(Math.abs(d - want) <= 0.5, what + " landed " + String.format("%.2f", d) + " blocks out, not " + want);
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void frogLongJumpsSix(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(1, 2, 7));
        var frog = host(h, EntityType.FROG, new BlockPos(1, 2, 7), p, 60);
        measureBound(h, p, frog, false, 6, "Long Jump",
                () -> h.assertTrue(frog.getPose() == Pose.LONG_JUMPING && Hosts.data(frog).leaping, "no jumping pose in the air"));
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void camelDashesEight(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 2, 7));
        Camel camel = host(h, EntityType.CAMEL, new BlockPos(2, 2, 7), p, 80);
        measureBound(h, p, camel, true, 8, "Dash", () -> h.assertTrue(camel.isDashing(), "no dash look"));
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void frogLandsOnItsFeet(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(1, 2, 7));
        var frog = host(h, EntityType.FROG, new BlockPos(1, 2, 7), p, 60);
        look(p, p.getEyePosition().add(10, 0, 0));
        h.assertTrue(HostAbilities.use(p, frog, false), "Long Jump refused");
        h.runAfterDelay(50, () -> {
            h.assertTrue(frog.getPose() == Pose.STANDING && !Hosts.data(frog).leaping, "the frog is still in its jump pose after landing");
            h.assertTrue(frog.getHealth() >= frog.getMaxHealth(), "the landing hurt");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void hissScattersCreepers(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var cat = host(h, EntityType.CAT, FLOOR, p, 60);
        h.assertFalse(HostAbilities.use(p, cat, false), "Hiss fired with no creepers");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.no_creepers", "the refusal");
        Creeper creeper = h.spawn(EntityType.CREEPER, FLOOR.south(3));
        creeper.setTarget(p);
        creeper.setSwellDir(1);
        double before = creeper.distanceTo(cat);
        Hosts.data(cat).primaryCooldown = 0;
        h.assertTrue(HostAbilities.use(p, cat, false), "Hiss refused: " + Refusals.lastDelivered(p));
        h.assertTrue(creeper.getTarget() == null && creeper.getSwellDir() < 0, "the creeper kept its target or its fuse");
        creeper.setTarget(p);
        h.assertTrue(creeper.getTarget() == null, "a hissed creeper turned on the pilot");
        h.runAfterDelay(60, () -> {
            h.assertTrue(creeper.distanceTo(cat) > before + 1.5, "the creeper did not flee: " + before + " -> " + creeper.distanceTo(cat));
            h.assertTrue(creeper.getTarget() != p, "a hissed creeper turned on the pilot");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void rearSparesPlayersWithoutPvp(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ServerPlayer other = player(h, FLOOR.north(2));
        Horse horse = host(h, EntityType.HORSE, FLOOR, p, 80);
        Husk husk = h.spawn(EntityType.HUSK, FLOOR.south(2));
        husk.setNoAi(true);
        var server = h.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(false);
            other.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(HostAbilities.use(p, horse, true), "Rear refused: " + Refusals.lastDelivered(p));
            h.assertTrue(other.getDeltaMovement().horizontalDistance() < 0.01, "a player was thrown with PvP off");
            h.assertTrue(husk.getDeltaMovement().z > 0.3, "the husk was not thrown: " + husk.getDeltaMovement());
            h.assertTrue(horse.isStanding(), "the horse did not rear");
            server.setPvpAllowed(true);
            Hosts.data(horse).secondaryCooldown = 0;
            h.assertTrue(HostAbilities.use(p, horse, true), "Rear refused: " + Refusals.lastDelivered(p));
            h.assertTrue(other.getDeltaMovement().z < -0.3, "a player was spared with PvP on: " + other.getDeltaMovement());
        } finally {
            server.setPvpAllowed(pvp);
        }
        done(h, p, other);
    }

    @GameTest(template = "platform")
    public static void mendSpendsIronForTwentyFive(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var golem = host(h, EntityType.IRON_GOLEM, FLOOR.east(2), p, 80);
        p.getInventory().add(new ItemStack(Items.IRON_INGOT));
        h.assertFalse(HostAbilities.use(p, golem, true), "Mend fired at full health");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.mend_full", "the refusal");
        h.assertValueEqual(count(p, Items.IRON_INGOT), 1, "iron spent at full health");
        golem.setHealth(50);
        h.assertTrue(HostAbilities.use(p, golem, true), "Mend refused: " + Refusals.lastDelivered(p));
        h.assertTrue(Math.abs(golem.getHealth() - 75) < 0.01, "Mend healed to " + golem.getHealth());
        h.assertValueEqual(count(p, Items.IRON_INGOT), 0, "iron left");
        Hosts.data(golem).secondaryCooldown = 0;
        h.assertFalse(HostAbilities.use(p, golem, true), "Mend fired without iron");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.no_iron", "the refusal");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 1300)
    public static void howlCallsOnlyYourWolves(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Wolf host = h.spawn(EntityType.WOLF, FLOOR.east());
        host.setTame(true, false);
        host.setOwnerUUID(p.getUUID());
        host.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), host, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME, "setup: saw");
        Hosts.data(host).sync = 80;
        Wolf stranger = h.spawn(EntityType.WOLF, new BlockPos(2, 2, 12));
        stranger.setTame(true, false);
        stranger.setOwnerUUID(UUID.randomUUID());
        stranger.setOrderedToSit(true);
        stranger.setInSittingPose(true);
        h.assertFalse(HostAbilities.use(p, host, true), "Howl fired with no wolves of yours");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.no_wolves", "the refusal");
        Wolf mine = h.spawn(EntityType.WOLF, new BlockPos(12, 2, 12));
        mine.setTame(true, false);
        mine.setOwnerUUID(p.getUUID());
        mine.setOrderedToSit(true);
        mine.setInSittingPose(true);
        Hosts.data(host).secondaryCooldown = 0;
        h.assertTrue(HostAbilities.use(p, host, true), "Howl refused: " + Refusals.lastDelivered(p));
        h.assertFalse(mine.isOrderedToSit() || mine.isInSittingPose(), "your wolf is still sitting");
        h.assertTrue(stranger.isOrderedToSit(), "someone else's wolf stood up");
        h.runAfterDelay(1220, () -> {
            h.assertTrue(mine.isOrderedToSit(), "your wolf did not sit down again after the minute");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void treasureSenseFindsNoneInTheNether(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "setup: no Nether");
        nether.getChunk(0, 0);
        var dolphin = EntityType.DOLPHIN.create(nether);
        dolphin.moveTo(0.5, 100, 0.5);
        HostData data = Hosts.data(dolphin);
        data.host = true;
        data.sync = 80;
        nether.addFreshEntity(dolphin);
        try {
            h.assertFalse(HostAbilities.use(p, dolphin, true), "Treasure Sense found something in the Nether");
            h.assertValueEqual(Refusals.lastDelivered(p), "host.no_treasure", "the refusal");
        } finally {
            dolphin.discard();
        }
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void treasureSenseReadsOnlyLoadedChunks(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var dolphin = host(h, EntityType.DOLPHIN, FLOOR.east(), p, 80);
        ServerLevel level = h.getLevel();
        // a buried treasure planted in the next chunk along (its start, as world generation leaves one)
        BlockPos chest = h.absolutePos(FLOOR).offset(16, -1, 0);
        var chunk = level.getChunkAt(chest);
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(BuiltinStructures.BURIED_TREASURE);
        var before = new HashMap<>(chunk.getAllStarts());
        int[] loads = {0};
        Consumer<ChunkEvent.Load> counter = e -> loads[0]++;
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.NORMAL, false, ChunkEvent.Load.class, counter);
        try {
            h.assertFalse(HostAbilities.use(p, dolphin, true), "Treasure Sense found treasure where there is none");
            h.assertValueEqual(Refusals.lastDelivered(p), "host.no_treasure", "the refusal");
            chunk.setStartForStructure(structure, new StructureStart(structure, chunk.getPos(), 0,
                    new PiecesContainer(List.of(new BuriedTreasurePieces.BuriedTreasurePiece(chest)))));
            Hosts.data(dolphin).secondaryCooldown = 0;
            h.assertTrue(HostAbilities.use(p, dolphin, true), "Treasure Sense missed the treasure: " + Refusals.lastDelivered(p));
            Vec3 found = HostTricks.treasure(level, dolphin.blockPosition(), 4);
            h.assertTrue(found != null && found.distanceTo(Vec3.atCenterOf(chest)) < 1, "it pointed at " + found + ", not " + chest);
            h.assertValueEqual(loads[0], 0, "chunks loaded by the presses");
        } finally {
            NeoForge.EVENT_BUS.unregister(counter);
            chunk.setAllStarts(before);
        }
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aModdedBlankStillHasNoTrick(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        // a body with no host file (as a modded creature has): no Ability and no Secondary
        var rabbit = host(h, EntityType.RABBIT, FLOOR.east(), p, 90);
        h.assertFalse(HostAbilities.use(p, rabbit, false), "a body with no file did a trick");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.no_ability", "the refusal");
        // a file naming a type nobody knows says the same, with the body's name
        var cow = host(h, EntityType.COW, FLOOR.west(), p, 90);
        String said = HostTricks.fire(p, cow, new HostDefinition.Ability("juggle", 60, Map.of()));
        h.assertValueEqual(said, "", "an unknown type's answer");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.no_ability", "the unknown type's refusal");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void anOldHostGainsItsTrickAndKeepsItsSync(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Cow old = host(h, EntityType.COW, FLOOR.east(), p, 63);
        Hosts.data(old).freshness = 71;
        h.spawn(EntityType.SHEEP, FLOOR.west(4));
        CompoundTag tag = new CompoundTag();
        old.save(tag);
        old.discard();
        // as 0.6.2 saved it: none of this release's fields
        CompoundTag attachments = tag.getCompound("neoforge:attachments");
        CompoundTag host = attachments.getCompound("headinajar:host");
        h.assertTrue(host.getBoolean("host"), "setup: the host data is not where it was looked for: " + attachments.getAllKeys());
        for (String key : List.of("run_anchor", "barter_due", "warming")) host.remove(key);
        tag.remove("UUID");
        Cow cow = (Cow) EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
        h.getLevel().addFreshEntity(cow);
        HostData data = Hosts.data(cow);
        h.assertTrue(data.host && Math.abs(data.sync - 63) < 0.01 && Math.abs(data.freshness - 71) < 0.01, "sync or freshness changed: " + data.sync + ", " + data.freshness);
        h.assertTrue(data.barterDue == 0 && !data.warming, "an old host loaded mid-trick");
        h.assertTrue(HostAbilities.use(p, cow, false), "the old cow has no Moo: " + Refusals.lastDelivered(p));
        done(h, p);
    }

    // --- every trick on its body -----------------------------------------------------------------------------

    /** The refusals a trick may answer with on a bare platform (nobody to herd, no creepers, no treasure ...). */
    private static final Set<String> BARE = Set.of("host.no_herd", "host.no_creepers", "host.no_treasure", "host.no_wolves", "host.warm_up_lava", "host.mend_full",
            "host.no_target");

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void everyTrickFiresOnItsBody(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.getInventory().add(new ItemStack(Items.GOLD_INGOT, 4));
        p.getInventory().add(new ItemStack(Items.IRON_INGOT, 4));
        List<EntityType<? extends Mob>> bodies = List.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.LLAMA, EntityType.VILLAGER,
                EntityType.PIGLIN, EntityType.STRIDER, EntityType.TURTLE, EntityType.FROG, EntityType.CAT, EntityType.CAMEL, EntityType.HORSE,
                EntityType.DONKEY, EntityType.MULE, EntityType.IRON_GOLEM, EntityType.WOLF, EntityType.DOLPHIN);
        StringBuilder bad = new StringBuilder();
        int i = 0;
        for (EntityType<? extends Mob> type : bodies) {
            BlockPos at = new BlockPos(1 + i % 6 * 2, 2, 2 + i / 6 * 5);
            i++;
            Mob body = type == EntityType.WOLF ? tameWolf(h, at, p) : host(h, type, at, p, 100);
            body.setHealth(body.getMaxHealth() - 1);
            HostDefinition def = Hosts.definition(body);
            if (def == null || def.primary().isEmpty() && def.secondary().isEmpty()) {
                bad.append(type).append(": no trick; ");
                continue;
            }
            for (boolean secondary : new boolean[]{false, true}) {
                var a = secondary ? def.secondary() : def.primary();
                if (a.isEmpty()) continue;
                try {
                    if (!HostAbilities.use(p, body, secondary) && !BARE.contains(Refusals.lastDelivered(p))) {
                        bad.append(type).append(' ').append(a.get().type()).append(": ").append(Refusals.lastDelivered(p)).append("; ");
                    }
                } catch (RuntimeException e) {
                    bad.append(type).append(' ').append(a.get().type()).append(" threw ").append(e).append("; ");
                }
            }
        }
        h.assertTrue(bad.isEmpty(), bad.toString());
        // and the trackers keep ticking without error for a second
        h.runAfterDelay(40, () -> done(h, p));
    }

    private static Wolf tameWolf(GameTestHelper h, BlockPos at, ServerPlayer p) {
        Wolf w = h.spawn(EntityType.WOLF, at);
        w.setTame(true, false);
        w.setOwnerUUID(p.getUUID());
        w.setHealth(1);
        SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), w, InteractionHand.MAIN_HAND);
        Hosts.data(w).sync = 100;
        return w;
    }
}
