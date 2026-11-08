package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.BodyLockerBlockEntity;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.host.HostAbilities;
import com.coolfreeze23.headinajar.host.HostControl;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Phase 3, Hostile Takeover: the saw, host bodies, piloting, decay, the locker, jarred heads and shells. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class HostGameTests {
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

    private static JarState state(ServerPlayer p) {
        return FormActions.state(p);
    }

    private static ItemStack saw() {
        return new ItemStack(ModRegistry.SURGICAL_SAW.get());
    }

    /** Makes a host the way the saw does. */
    private static <T extends Mob> T host(GameTestHelper h, EntityType<T> type, BlockPos at, ServerPlayer sawyer) {
        T mob = h.spawn(type, at);
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, saw(), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME, "saw failed on " + type);
        return mob;
    }

    @GameTest(template = "platform")
    public static void sawRefusesHealthyMobs(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(2));
        h.assertTrue(SurgicalSawItem.saw(p, saw(), zombie, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "sawed a healthy zombie");
        h.assertFalse(Hosts.isHost(zombie), "a healthy zombie became a host");
        zombie.setHealth(3);
        SurgicalSawItem.saw(p, saw(), zombie, InteractionHand.MAIN_HAND);
        h.assertTrue(Hosts.isHost(zombie) && Hosts.data(zombie).freshness == 100, "a weakened zombie did not become a host");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void sawRefusesBossesAndPets(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        h.assertTrue(EntityType.WITHER.is(Tags.EntityTypes.BOSSES) && EntityType.ENDER_DRAGON.is(Tags.EntityTypes.BOSSES), "boss tag missing");
        Zombie named = h.spawn(EntityType.ZOMBIE, FLOOR.east(2));
        named.setCustomName(Component.literal("Gerald"));
        named.setHealth(1);
        h.assertFalse(Hosts.canSaw(named, p), "sawed a named mob");
        Wolf wolf = h.spawn(EntityType.WOLF, FLOOR.west(2));
        wolf.setTame(true, false);
        wolf.setOwnerUUID(UUID.randomUUID());
        h.assertFalse(Hosts.canSaw(wolf, p), "sawed someone else's dog");
        wolf.setOwnerUUID(p.getUUID());
        h.assertTrue(Hosts.canSaw(wolf, p), "could not saw your own dog");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void mountMakesHostedAndHandsControl(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Zombie zombie = host(h, EntityType.ZOMBIE, FLOOR.east(2), p);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, zombie), "mount failed");
        h.assertTrue(state(p).form() == Form.HOSTED && p.getVehicle() == zombie, "not riding the host");
        h.assertTrue(zombie.getControllingPassenger() == p, "the host ignores its pilot: " + zombie.getControllingPassenger());
        Vec3 seat = zombie.getPassengerRidingPosition(p);
        h.assertTrue(Math.abs(seat.y - zombie.getY() - 1.45) < 0.01, "jar not at the neck: " + (seat.y - zombie.getY()));
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void hostStopsThinking(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Zombie zombie = host(h, EntityType.ZOMBIE, FLOOR.east(3), p);
        Vec3 start = zombie.position();
        h.runAfterDelay(40, () -> {
            h.assertTrue(zombie.position().distanceTo(start) < 0.2, "a host walked off on its own");
            h.assertTrue(zombie.getTarget() == null, "a host chose a target");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void hostDecaysAndCollapses(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Pig pig = host(h, EntityType.PIG, FLOOR.east(2), p);
        Hosts.data(pig).freshness = 0.0001f;
        h.runAfterDelay(5, () -> {
            h.assertFalse(pig.isAlive(), "a spent host kept standing");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void embalmingRestoresFreshness(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Pig pig = host(h, EntityType.PIG, FLOOR.east(2), p);
        Hosts.data(pig).freshness = 30;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.EMBALMING_FLUID.get()));
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, pig));
        h.assertTrue(Hosts.data(pig).freshness == 100 && Hosts.data(pig).embalmedUntil > h.getLevel().getGameTime(), "not embalmed");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void lockerStopsDecay(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        h.setBlock(FLOOR.north(2), ModRegistry.BODY_LOCKER.get());
        var locker = (BodyLockerBlockEntity) h.getBlockEntity(FLOOR.north(2));
        Pig pig = host(h, EntityType.PIG, FLOOR.north(1), p);
        Hosts.data(pig).freshness = 64;
        locker.use(p);
        h.assertTrue(locker.full() && pig.isRemoved(), "host not stored");
        locker.use(p);
        h.assertFalse(locker.full(), "host not released");
        Pig back = h.getEntities(EntityType.PIG).stream().filter(e -> !e.isRemoved()).findFirst().orElse(null);
        h.assertTrue(back != null && Hosts.isHost(back) && Math.abs(Hosts.data(back).freshness - 64) < 0.01, "host came back changed");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void syncUnlocksAtThresholds(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        EnderMan ender = host(h, EntityType.ENDERMAN, FLOOR, p);
        FormActions.detach(p, 3);
        FormActions.mountHost(p, ender);
        p.setXRot(60);
        Hosts.data(ender).sync = 40;
        h.assertFalse(HostAbilities.use(p, ender, false), "Blink worked at 40% sync");
        Hosts.data(ender).sync = 50;
        h.assertTrue(HostAbilities.use(p, ender, false), "Blink failed at 50% sync");
        h.assertFalse(HostAbilities.use(p, ender, false), "Blink ignored its cooldown");
        h.assertFalse(HostAbilities.use(p, ender, true), "the secondary worked below 75%");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void hostDeathEjectsJar(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Pig pig = host(h, EntityType.PIG, FLOOR.east(2), p);
        FormActions.detach(p, 3);
        FormActions.mountHost(p, pig);
        pig.kill();
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
        h.assertTrue(p.getVehicle() == null && state(p).form() == Form.JAR, "jar still hosted: " + state(p).form());
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void hostedAttackUsesHost(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ShellEntity lunk = h.spawn(ModRegistry.LUNK.get(), FLOOR);
        Pig victim = h.spawn(EntityType.PIG, FLOOR.east(2));
        FormActions.detach(p, 3);
        FormActions.mountHost(p, lunk);
        float before = victim.getHealth();
        NeoForge.EVENT_BUS.post(new AttackEntityEvent(p, victim));
        h.assertTrue(victim.getHealth() <= before - 9 || !victim.isAlive(), "the Lunk's punch did " + (before - victim.getHealth()));
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void shellRunsOnCharge(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ShellEntity rover = h.spawn(ModRegistry.ROVER.get(), FLOOR);
        h.assertTrue(Hosts.isHost(rover) && Hosts.data(rover).shell && Hosts.data(rover).effectiveSync() == 100, "rover is not a ready shell");
        FormActions.detach(p, 3);
        FormActions.mountHost(p, rover);
        p.zza = 1;
        h.assertTrue(HostControl.riddenInput(rover, p).z > 0, "a charged rover won't drive");
        state(p).setCharge(0);
        h.assertTrue(HostControl.riddenInput(rover, p).equals(Vec3.ZERO), "a flat jar drove the rover");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void villagerHeadTrades(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Villager villager = h.spawn(EntityType.VILLAGER, FLOOR.east(2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(1));
        int offers = villager.getOffers().size();
        h.assertTrue(offers > 0, "the librarian has no trades");
        villager.setHealth(1);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        SurgicalSawItem.saw(p, saw(), villager, InteractionHand.MAIN_HAND);
        ItemStack head = p.getInventory().items.stream().filter(s -> s.is(ModRegistry.JARRED_HEAD.get())).findFirst().orElse(ItemStack.EMPTY);
        h.assertTrue(JarredHeadItem.type(head) == EntityType.VILLAGER, "no jarred villager head");
        h.setBlock(FLOOR.south(2), ModRegistry.JARRED_HEAD_BLOCK.get());
        var block = (JarredHeadBlockEntity) h.getBlockEntity(FLOOR.south(2));
        block.load(head.get(DataComponents.CUSTOM_DATA).copyTag());
        h.assertTrue(block.trade(p), "the jarred villager would not trade");
        h.assertTrue(p.containerMenu instanceof MerchantMenu menu && menu.getOffers().size() == offers, "trades lost in the jar");
        p.closeContainer();
        done(h, p);
    }
}
