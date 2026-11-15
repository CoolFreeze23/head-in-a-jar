package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The Surgical Saw's table (0.6.0): who refuses and why, which jar a head needs, the whole slime, the snow golem. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class SawTableGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        p.moveTo(v.x, v.y, v.z, 0, 0);
        return p;
    }

    static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    static <T extends Mob> T weak(GameTestHelper h, EntityType<T> type, BlockPos at) {
        T mob = h.spawn(type, at);
        mob.setHealth(1);
        mob.setNoAi(true);
        return mob;
    }

    /** A saw in the main hand and {@code jar} (or nothing) in the off hand. */
    static ItemStack arm(ServerPlayer p, ItemStack jar) {
        ItemStack saw = new ItemStack(ModRegistry.SURGICAL_SAW.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, saw);
        p.setItemInHand(InteractionHand.OFF_HAND, jar);
        return saw;
    }

    static boolean earned(GameTestHelper h, ServerPlayer p, String id) {
        AdvancementHolder holder = h.getLevel().getServer().getAdvancements().get(HeadInAJar.id(id));
        if (holder == null) throw new net.minecraft.gametest.framework.GameTestAssertException("no advancement " + id);
        return p.getAdvancements().getOrStartProgress(holder).isDone();
    }

    static ItemStack head(ServerPlayer p) {
        return p.getInventory().items.stream().filter(s -> s.is(ModRegistry.JARRED_HEAD.get())).findFirst().orElse(ItemStack.EMPTY);
    }

    static CompoundTag data(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    /** A refusal: the reason delivered, the creature untouched, the saw and the jar unspent. */
    static void refused(GameTestHelper h, ServerPlayer p, Mob mob, String reason, ItemStack saw, ItemStack jar, int jarCount) {
        p.interactOn(mob, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), reason, "the reason for " + mob.getType().getDescriptionId());
        h.assertTrue(mob.isAlive() && !Hosts.isHost(mob), "a refused cut changed the creature");
        h.assertTrue(saw.getDamageValue() == 0, "a refused cut cost the saw " + saw.getDamageValue());
        h.assertTrue(jar.getCount() == jarCount, "a refused cut cost the jar");
    }

    @GameTest(template = "platform", batch = "saw_config")
    public static void aBabyIsRefusedAndNothingIsSpent(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ItemStack jar = new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get());
        ItemStack saw = arm(p, jar);
        Cow calf = weak(h, EntityType.COW, FLOOR.east());
        calf.setAge(-24000);
        refused(h, p, calf, "saw.baby", saw, jar, 1);
        // the server may allow it (the behaviour before 0.6.0)
        Config.ALLOW_BABIES.set(true);
        try {
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            Cow other = weak(h, EntityType.COW, FLOOR.east(2));
            other.setAge(-24000);
            p.interactOn(other, InteractionHand.MAIN_HAND);
            h.assertTrue(Hosts.isHost(other), "with babies allowed the calf did not become a host");
        } finally {
            Config.ALLOW_BABIES.set(false);
        }
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aSlimeNeedsAJar(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ItemStack saw = arm(p, ItemStack.EMPTY);
        Slime slime = weak(h, EntityType.SLIME, FLOOR.east());
        slime.setSize(1, false);
        slime.setHealth(0.5f);
        refused(h, p, slime, "saw.jar_only.slime", saw, ItemStack.EMPTY, 0);
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aSlimeGoesWholeIntoTheJar(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ItemStack jar = new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get());
        arm(p, jar);
        Slime slime = weak(h, EntityType.SLIME, FLOOR.east());
        slime.setSize(1, false);
        slime.setHealth(0.5f);
        p.interactOn(slime, InteractionHand.MAIN_HAND);
        ItemStack head = head(p);
        CompoundTag tag = data(head);
        h.assertValueEqual(tag.getString("Type"), "minecraft:slime", "the head's creature");
        h.assertValueEqual(tag.getString("Jar"), "headinajar:life_support_jar", "the head's jar");
        h.assertTrue(slime.isRemoved(), "the slime is still out");
        h.assertTrue(earned(h, p, "entirely_head"), "Entirely Head was not earned");
        AABB around = new AABB(h.absolutePos(FLOOR)).inflate(4);
        h.runAfterDelay(25, () -> {
            h.assertTrue(h.getLevel().getEntitiesOfClass(Slime.class, around).isEmpty(), "a slime was left behind");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(Items.SLIME_BALL)).isEmpty(), "the slime dropped slime balls");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aBigSlimeNeedsAnExecutiveJarAndDoesNotSplit(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ItemStack reinforced = new ItemStack(ModRegistry.REINFORCED_JAR.get());
        ItemStack saw = arm(p, reinforced);
        Slime slime = weak(h, EntityType.SLIME, FLOOR.east());
        slime.setSize(4, false);
        slime.setHealth(1);
        refused(h, p, slime, "saw.jar_small.executive", saw, reinforced, 1);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.EXECUTIVE_JAR.get()));
        p.interactOn(slime, InteractionHand.MAIN_HAND);
        CompoundTag tag = data(head(p));
        h.assertTrue(tag.getCompound("Entity").getInt("Size") == 3, "the record's size: " + tag.getCompound("Entity").getInt("Size"));
        AABB around = new AABB(h.absolutePos(FLOOR)).inflate(6);
        h.runAfterDelay(25, () -> {
            h.assertTrue(h.getLevel().getEntitiesOfClass(Slime.class, around).isEmpty(), "the big slime split");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void aMagmaCubeAsASlime(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ItemStack small = new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get());
        ItemStack saw = arm(p, small);
        var cube = weak(h, EntityType.MAGMA_CUBE, FLOOR.east());
        cube.setSize(2, false);
        cube.setHealth(1);
        refused(h, p, cube, "saw.jar_small.reinforced", saw, small, 1);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.REINFORCED_JAR.get()));
        p.interactOn(cube, InteractionHand.MAIN_HAND);
        h.assertValueEqual(data(head(p)).getString("Jar"), "headinajar:reinforced_life_support_jar", "the magma cube's jar");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aWardenNeedsAReinforcedJar(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ItemStack small = new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get());
        ItemStack saw = arm(p, small);
        var warden = weak(h, EntityType.WARDEN, FLOOR.east(2));
        refused(h, p, warden, "saw.jar_small.reinforced", saw, small, 1);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.REINFORCED_JAR.get()));
        p.interactOn(warden, InteractionHand.MAIN_HAND);
        h.assertValueEqual(data(head(p)).getString("Type"), "minecraft:warden", "the warden's head");
        // a bigger jar always takes a smaller head
        var second = weak(h, EntityType.WARDEN, FLOOR.west(2));
        p.getInventory().clearContent();
        arm(p, new ItemStack(ModRegistry.EXECUTIVE_JAR.get()));
        p.interactOn(second, InteractionHand.MAIN_HAND);
        h.assertValueEqual(data(head(p)).getString("Jar"), "headinajar:executive_life_support_jar", "a warden in an Executive jar");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aGhastNeedsAnExecutiveJarButGivesABodyWithout(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ItemStack reinforced = new ItemStack(ModRegistry.REINFORCED_JAR.get());
        ItemStack saw = arm(p, reinforced);
        var ghast = weak(h, EntityType.GHAST, FLOOR.east(3).above(2));
        refused(h, p, ghast, "saw.jar_small.executive", saw, reinforced, 1);
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        p.interactOn(ghast, InteractionHand.MAIN_HAND);
        h.assertTrue(Hosts.isHost(ghast), "with no jar the ghast did not become a host");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void everyJarIsRecordedOnTheHead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var jars = List.of(ModRegistry.LIFE_SUPPORT_JAR.get(), ModRegistry.COMPACT_JAR.get(), ModRegistry.REINFORCED_JAR.get(), ModRegistry.EXECUTIVE_JAR.get());
        int i = 0;
        for (var jar : jars) {
            p.getInventory().clearContent();
            arm(p, new ItemStack(jar));
            Cow cow = weak(h, EntityType.COW, FLOOR.east(1 + i).south(i % 2));
            p.interactOn(cow, InteractionHand.MAIN_HAND);
            ItemStack head = head(p);
            String want = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(jar).toString();
            h.assertValueEqual(JarredHeadItem.jar(head), want, "the recorded jar");
            // placed and broken again, the jar is kept
            BlockPos at = FLOOR.north(2).east(i);
            h.setBlock(at, ModRegistry.JARRED_HEAD_BLOCK.get());
            JarredHeadBlockEntity placed = (JarredHeadBlockEntity) h.getBlockEntity(at);
            placed.load(data(head));
            h.assertValueEqual(placed.jar(), want, "the placed head's jar");
            h.assertValueEqual(JarredHeadItem.jar(placed.toItem()), want, "the jar after breaking it");
            h.assertTrue(placed.compact() == (jar == ModRegistry.COMPACT_JAR.get()), "the compact shape");
            i++;
        }
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void anOldHeadWithNoJarIsALifeSupportJar(GameTestHelper h) {
        Cow cow = h.spawn(EntityType.COW, FLOOR.east());
        ItemStack old = JarredHeadItem.of(cow);
        cow.discard();
        CompoundTag tag = data(old);
        h.assertTrue(!tag.contains("Jar") && !tag.contains("Staff"), "an old-shaped head has new keys: " + tag.getAllKeys());
        h.assertValueEqual(JarredHeadItem.jar(old), "headinajar:life_support_jar", "an old head's jar");
        h.setBlock(FLOOR, ModRegistry.JARRED_HEAD_BLOCK.get());
        JarredHeadBlockEntity placed = (JarredHeadBlockEntity) h.getBlockEntity(FLOOR);
        placed.load(tag);
        h.assertTrue(!placed.compact() && placed.jar().equals("headinajar:life_support_jar"), "drawn as " + placed.jar());
        h.runAfterDelay(3, () -> {
            h.assertTrue(placed.staff() != null, "the old head was not put on file");
            h.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void theSnowGolemLosesItsPumpkinFirst(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ItemStack saw = arm(p, ItemStack.EMPTY);
        SnowGolem golem = h.spawn(EntityType.SNOW_GOLEM, FLOOR.east());
        golem.setNoAi(true);
        h.assertTrue(golem.hasPumpkin(), "setup: no pumpkin");
        int before = Refusals.delivered(p).size();
        p.interactOn(golem, InteractionHand.MAIN_HAND);
        h.assertTrue(!golem.hasPumpkin() && golem.isAlive() && !Hosts.isHost(golem), "the pumpkin did not come off first");
        h.assertTrue(saw.getDamageValue() == 1, "the pumpkin cost " + saw.getDamageValue());
        h.assertTrue(Refusals.delivered(p).size() == before, "taking the pumpkin was refused: " + Refusals.delivered(p));
        AABB around = new AABB(h.absolutePos(FLOOR)).inflate(4);
        h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(Items.CARVED_PUMPKIN)).isEmpty(), "no pumpkin dropped");
        golem.setHealth(1);
        p.interactOn(golem, InteractionHand.MAIN_HAND);
        h.assertTrue(Hosts.isHost(golem), "the second cut was not a normal cut");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void everyRefusalHasItsLineAndTheRefusalSound(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ServerPlayer owner = player(h, FLOOR.west(3));
        ItemStack jar = new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get());
        ItemStack saw = arm(p, jar);
        // the Ender Dragon, through one of its parts
        var dragon = h.spawn(EntityType.ENDER_DRAGON, FLOOR.above(6));
        dragon.setNoAi(true);
        var part = dragon.getSubEntities()[0];
        p.interactOn(part, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "saw.dragon", "the dragon");
        dragon.discard();
        var wither = weak(h, EntityType.WITHER, FLOOR.east(3).above(2));
        refused(h, p, wither, "saw.wither", saw, jar, 1);
        wither.discard();
        Cow named = weak(h, EntityType.COW, FLOOR.east(1));
        named.setCustomName(Component.literal("Daisy"));
        refused(h, p, named, "saw.named", saw, jar, 1);
        Wolf wolf = weak(h, EntityType.WOLF, FLOOR.south(2));
        wolf.tame(owner);
        refused(h, p, wolf, "saw.pet", saw, jar, 1);
        Cow host = weak(h, EntityType.COW, FLOOR.north(2));
        Hosts.data(host).host = true;
        p.interactOn(host, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "saw.host", "a host");
        var shell = h.spawn(ModRegistry.LUNK.get(), FLOOR.west(1).south(3));
        // the same reason again inside the refusal's debounce: read the table's answer itself
        h.assertValueEqual(com.coolfreeze23.headinajar.staff.SawTable.verdict(shell, p, jar, true).reason(), "saw.machine", "a Utility Shell");
        var stand = h.spawn(EntityType.ARMOR_STAND, FLOOR.north(3).east(2));
        p.interactOn(stand, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "saw.furniture", "an armour stand");
        var body = h.spawn(ModRegistry.HEADLESS_BODY.get(), FLOOR.south(3).east(2));
        p.interactOn(body, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "item.on_colleague", "a headless body");
        h.assertTrue(saw.getDamageValue() == 0 && jar.getCount() == 1, "a refusal spent something");
        done(h, p, owner);
    }

    @GameTest(template = "platform")
    public static void aRefusalShakesTheHead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        arm(p, ItemStack.EMPTY);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east());
        cow.setNoAi(true);
        // at full health: too lively
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "saw.too_lively", "a lively cow");
        h.assertTrue(Fx.count(Fx.STAFF_SHAKE, cow.getId()) == 1, "the cow did not shake its head");
        h.assertTrue(Fx.count(Fx.HOST_CUT, cow.getId()) == 0, "a refusal was told as a cut");
        Villager villager = h.spawn(EntityType.VILLAGER, FLOOR.west(2));
        villager.setNoAi(true);
        // past the refusal's debounce, so the same reason is answered again
        h.runAfterDelay(Refusals.DEBOUNCE + 2, () -> {
            p.interactOn(villager, InteractionHand.MAIN_HAND);
            h.assertTrue(villager.getUnhappyCounter() > 0, "the villager did not shake its own head");
            h.assertTrue(Fx.count(Fx.STAFF_SHAKE, villager.getId()) == 0, "the villager was told to shake as well as shaking");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void aTadpoleIsNotABaby(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        arm(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        var tadpole = weak(h, EntityType.TADPOLE, FLOOR.east());
        tadpole.setHealth(0.5f);
        p.interactOn(tadpole, InteractionHand.MAIN_HAND);
        h.assertValueEqual(data(head(p)).getString("Type"), "minecraft:tadpole", "the tadpole's head");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aNameTagStillStopsTheSawAndAStaffNameDoesNot(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        arm(p, ItemStack.EMPTY);
        Cow tagged = weak(h, EntityType.COW, FLOOR.east());
        tagged.setCustomName(Component.literal("Bessie"));
        p.interactOn(tagged, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "saw.named", "a name-tagged cow");
        Cow sampled = weak(h, EntityType.COW, FLOOR.west(2));
        Staff.sampled(sampled, p);
        StaffFile f = Staff.of(sampled);
        h.assertTrue(f != null && !sampled.hasCustomName(), "the sampled cow has no file, or a vanilla name");
        p.interactOn(sampled, InteractionHand.MAIN_HAND);
        h.assertTrue(Hosts.isHost(sampled) && !sampled.hasCustomName(), "a staff name stopped the saw");
        h.assertTrue(Staff.of(sampled).number == f.number, "the number changed when it was cut");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void oneCutMemoPerCutAndNoneOnARefusal(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        arm(p, ItemStack.EMPTY);
        Cow cow = weak(h, EntityType.COW, FLOOR.east());
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertTrue(Fx.count(Fx.HOST_CUT, cow.getId()) == 1, "one cut, told " + Fx.count(Fx.HOST_CUT, cow.getId()));
        Cow calf = weak(h, EntityType.COW, FLOOR.west(2));
        calf.setAge(-24000);
        p.interactOn(calf, InteractionHand.MAIN_HAND);
        h.assertTrue(Fx.count(Fx.HOST_CUT, calf.getId()) == 0, "a refusal was told as a cut");
        done(h, p);
    }
}
