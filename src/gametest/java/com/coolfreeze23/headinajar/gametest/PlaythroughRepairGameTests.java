package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.ChargingDockBlock;
import com.coolfreeze23.headinajar.block.GalvanicPileBlockEntity;
import com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity;
import com.coolfreeze23.headinajar.block.PowerBlockEntities;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Courier;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.FormGuards;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.host.HostControl;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.JarMenu;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * What a new player's playthrough ran into, repaired. Each test takes the path a real click, key or tick takes: the
 * interaction the client sends ({@code interactOn}, {@code useItemOn}, {@code useItem}), a payload's handler, a menu
 * button, the player and level ticks, the game's own damage.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class PlaythroughRepairGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        place(h, player, at, 0, 0);
        return player;
    }

    private static void place(GameTestHelper h, ServerPlayer p, BlockPos at, float yaw, float pitch) {
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        p.moveTo(v.x, v.y, v.z, yaw, pitch);
    }

    private static void placeAt(GameTestHelper h, ServerPlayer p, Vec3 relative, float yaw, float pitch) {
        Vec3 v = h.absoluteVec(relative);
        p.moveTo(v.x, v.y, v.z, yaw, pitch);
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static JarState state(ServerPlayer p) {
        return FormActions.state(p);
    }

    private static void key(ServerPlayer p, Network.Action action) {
        Network.action(p, new Network.JarAction(action));
    }

    /** One server tick of this player, as its connection runs it (a test player's connection never does). */
    private static void tick(ServerPlayer p) {
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
    }

    private static InteractionResult useBlock(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        BlockPos abs = h.absolutePos(pos);
        return p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.2, 0), Direction.UP, abs, false));
    }

    private static void separate(GameTestHelper h, ServerPlayer p, BlockPos headAt) {
        h.assertTrue(FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(headAt)), null), "separate failed");
    }

    /** Separated with a satchel, the head picked up: a linked body carrying its head. */
    private static void carrying(GameTestHelper h, ServerPlayer p) {
        p.getInventory().add(new ItemStack(ModRegistry.HEAD_SATCHEL.get()));
        separate(h, p, FLOOR.west(1));
        HeadJarEntity head = h.getEntities(ModRegistry.HEAD_JAR.get()).get(0);
        h.assertTrue(FormActions.pickUpHead(p, head), "could not pick up own head");
    }

    /** A sawn host, made the way the saw makes one. */
    private static <T extends Mob> T host(GameTestHelper h, EntityType<T> type, BlockPos at, ServerPlayer sawyer) {
        T mob = h.spawn(type, at);
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "saw failed on " + type);
        return mob;
    }

    private static void lookAt(ServerPlayer p, Vec3 at) {
        Vec3 to = at.subtract(p.getEyePosition());
        p.setYRot((float) (Math.atan2(to.z, to.x) * 180 / Math.PI) - 90);
        p.setXRot((float) -(Math.atan2(to.y, to.horizontalDistance()) * 180 / Math.PI));
    }

    // --- 1. a held sneak doesn't carry from one form into the next ---------------------------------------------

    @GameTest(template = "platform")
    public static void aSneakHeldToGetOnIsNotASneakToGetOff(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Pig pig = host(h, EntityType.PIG, FLOOR.east(2), p);
        carrying(h, p);
        // the satchel's set-down is a sneak-use: the sneak is still held when the head lands on the host
        p.setShiftKeyDown(true);
        p.interactOn(pig, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == pig, "the head did not climb onto the host");
        h.assertFalse(p.isShiftKeyDown(), "the sneak held to get on still reads as held");
        // what the next tick of a ridden player does with a held sneak: get off
        p.rideTick();
        h.assertTrue(p.getVehicle() == pig && state(p).form() == Form.HOSTED, "thrown straight off the host: " + state(p).form());
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aBurningBodyLeftByASwapBurnsOnAlone(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        separate(h, p, FLOOR.east(3));
        p.igniteForSeconds(8);
        p.fallDistance = 6;
        key(p, Network.Action.SWAP);
        h.assertValueEqual(state(p).form(), Form.JAR, "form after the swap");
        h.assertFalse(p.isOnFire(), "the jar woke up burning");
        h.assertTrue(p.fallDistance == 0, "the jar woke up mid-fall: " + p.fallDistance);
        HeadlessBody body = (HeadlessBody) h.getLevel().getEntity(state(p).body());
        h.assertTrue(body != null && body.isOnFire(), "the burning body stopped burning");
        done(h, p);
    }

    // --- 2. the Rover: fast, and a crash is one knock --------------------------------------------------------

    @GameTest(template = "platform")
    public static void aRoverIsFastNotFourTimesASprint(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        ShellEntity rover = h.spawn(ModRegistry.ROVER.get(), FLOOR.east(2));
        ShellEntity lunk = h.spawn(ModRegistry.LUNK.get(), FLOOR.west(3));
        ShellEntity spider = h.spawn(ModRegistry.SPIDER_WALKER.get(), FLOOR.north(3));
        // the speed the ridden-speed hook gives; on flat ground a ridden speed s settles at s / (1 - 0.6 * 0.91) a tick
        double rv = HostControl.riddenSpeed(rover, p) / (1 - 0.546) * 20;
        double lv = HostControl.riddenSpeed(lunk, p) / (1 - 0.546) * 20;
        double sv = HostControl.riddenSpeed(spider, p) / (1 - 0.546) * 20;
        h.assertTrue(rv > 5.6 && rv <= 10.5, "the Rover drives at " + rv + " blocks a second");
        h.assertTrue(rv > sv && sv > lv, "fast, brisk, slow: Rover " + rv + ", Spider Walker " + sv + ", Lunk " + lv);
        h.assertTrue(lv < 5.6, "the Lunk outruns a sprint: " + lv);
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void aRoverCrashIsOneKnockNotADeathByWall(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.north(5));
        FormActions.detach(p, 3);
        ShellEntity rover = h.spawn(ModRegistry.ROVER.get(), FLOOR.north(5));
        for (int x = 3; x <= 11; x++) for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(x, y, FLOOR.getZ() + 3), Blocks.STONE);
        p.interactOn(rover, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == rover, "the jar did not board the Rover");
        int full = state(p).integrity();
        // the rider's client drives a ridden shell: the moves arrive between its ticks, as a vehicle-move packet's do
        h.onEachTick(() -> {
            if (p.getVehicle() == rover) rover.move(MoverType.PLAYER, new Vec3(0, -0.08, 0.45));
        });
        h.runAfterDelay(30, () -> h.assertValueEqual(state(p).integrity(), full - 1, "integrity after driving into the wall"));
        h.runAfterDelay(90, () -> {
            // pushing against it for three seconds more: still the one knock, and the jar's eye in the wall costs nothing
            h.assertValueEqual(state(p).integrity(), full - 1, "integrity after pushing against the wall");
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().inWall(), 1);
            h.assertValueEqual(state(p).integrity(), full - 1, "integrity after the jar's eye was in the wall");
            h.assertTrue(p.isAlive() && state(p).form() == Form.HOSTED, "the jar didn't come through the crash");
            done(h, p);
        });
    }

    // --- 3. the Charging Dock charges; power is shared ---------------------------------------------------------

    @GameTest(template = "platform")
    public static void aJarStandingOnTheDockCharges(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        h.setBlock(FLOOR, ModRegistry.CHARGING_DOCK.get());
        var dock = (ChargingDockBlock.Entity) h.getBlockEntity(FLOOR);
        dock.energy().set(20_000);
        FormActions.detach(p, 3);
        state(p).setCharge(10_000);
        // half on the plate, as a jar that hopped up to its edge stands
        placeAt(h, p, Vec3.atBottomCenterOf(FLOOR).add(0, ChargingDockBlock.TOP, 0.55), 0, 0);
        for (int i = 0; i < 10; i++) tick(p);
        h.assertTrue(state(p).charge() >= 14_000, "a jar on the dock's edge charged to " + state(p).charge());
        h.assertTrue(dock.energy().getEnergyStored() <= 15_000, "the dock gave nothing: " + dock.energy().getEnergyStored());
        // a jar beside it, off the plate, takes nothing
        int before = state(p).charge();
        place(h, p, FLOOR.east(2), 0, 0);
        for (int i = 0; i < 10; i++) tick(p);
        h.assertTrue(state(p).charge() <= before, "a jar off the dock charged");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void aHeadSetDownOnTheDockStandsOnItAndCharges(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        BlockPos dockAt = FLOOR.south(3).east(1);
        h.setBlock(dockAt, ModRegistry.CHARGING_DOCK.get());
        var dock = (ChargingDockBlock.Entity) h.getBlockEntity(dockAt);
        dock.energy().set(20_000);
        carrying(h, p);
        state(p).setCharge(30_000);
        // standing by the dock, looking at it: the satchel's sneak-use puts the head on the plate
        place(h, p, FLOOR.south(2), 0, 0);
        lookAt(p, h.absoluteVec(Vec3.atCenterOf(dockAt).add(0, -0.4, 0)));
        BlockPos aimed = ChargingDockBlock.aimed(p);
        ItemStack satchel = new ItemStack(ModRegistry.HEAD_SATCHEL.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, satchel);
        p.setShiftKeyDown(true);
        p.gameMode.useItem(p, h.getLevel(), satchel, InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false);
        h.assertFalse(state(p).carryingHead(), "the head is still in the satchel");
        HeadJarEntity head = h.getEntities(ModRegistry.HEAD_JAR.get()).get(0);
        Vec3 top = h.absoluteVec(ChargingDockBlock.top(dockAt));
        h.assertTrue(head.position().distanceTo(top) < 0.05, "the head is not on the plate: " + head.position().subtract(top) + ", aimed at " + aimed);
        h.runAfterDelay(40, () -> {
            h.assertTrue(head.isAlive() && head.getY() >= top.y - 0.01, "the head sank into the dock: " + (head.getY() - top.y));
            h.assertTrue(state(p).charge() >= 40_000, "a head on the dock charged to " + state(p).charge());
            // the same head swapped into stands on the dock as a jar and keeps charging (the dock topped up again)
            dock.energy().set(20_000);
            key(p, Network.Action.SWAP);
            h.assertValueEqual(state(p).form(), Form.JAR, "form after the swap");
            int before = state(p).charge();
            for (int i = 0; i < 4; i++) tick(p);
            h.assertTrue(state(p).charge() > before, "the jar the head became doesn't charge on the dock");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void aCarriedHeadGoesOnTheDockByAClick(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos dockAt = FLOOR.south(1);
        h.setBlock(dockAt, ModRegistry.CHARGING_DOCK.get());
        carrying(h, p);
        place(h, p, FLOOR, 0, 0);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        useBlock(h, p, dockAt);
        h.assertFalse(state(p).carryingHead(), "the click left the head in the satchel");
        HeadJarEntity head = h.getEntities(ModRegistry.HEAD_JAR.get()).get(0);
        h.assertTrue(head.position().distanceTo(h.absoluteVec(ChargingDockBlock.top(dockAt))) < 0.05, "the head is not on the plate");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void thePileSharesWithEveryNeighbour(GameTestHelper h) {
        // a pile with a hungry Leyden Jar on one side and two Charging Docks on others
        h.setBlock(FLOOR, ModRegistry.GALVANIC_PILE.get());
        h.setBlock(FLOOR.west(), ModRegistry.LEYDEN_JAR.get());
        h.setBlock(FLOOR.east(), ModRegistry.CHARGING_DOCK.get());
        h.setBlock(FLOOR.north(), ModRegistry.CHARGING_DOCK.get());
        var pile = (GalvanicPileBlockEntity) h.getBlockEntity(FLOOR);
        var leyden = (PowerBlockEntities.LeydenJar) h.getBlockEntity(FLOOR.west());
        var east = (ChargingDockBlock.Entity) h.getBlockEntity(FLOOR.east());
        var north = (ChargingDockBlock.Entity) h.getBlockEntity(FLOOR.north());
        // 20 FE a tick, as burning flesh makes it
        h.onEachTick(() -> pile.energy().set(pile.energy().getEnergyStored() + 20));
        h.runAfterDelay(60, () -> {
            int a = leyden.energy().getEnergyStored(), b = east.energy().getEnergyStored(), c = north.energy().getEnergyStored();
            h.assertTrue(a > 0 && b > 0 && c > 0, "a neighbour got nothing: Leyden " + a + ", docks " + b + ", " + c);
            int most = Math.max(a, Math.max(b, c)), least = Math.min(a, Math.min(b, c));
            h.assertTrue(most - least <= most / 4 + 20, "the share is lopsided: " + a + ", " + b + ", " + c);
            h.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void aLeydenJarSharesToo(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.LEYDEN_JAR.get());
        h.setBlock(FLOOR.west(), ModRegistry.CHARGING_DOCK.get());
        h.setBlock(FLOOR.east(), ModRegistry.CHARGING_DOCK.get());
        var leyden = (PowerBlockEntities.LeydenJar) h.getBlockEntity(FLOOR);
        leyden.energy().set(1_500);
        h.runAfterDelay(3, () -> {
            int west = ((ChargingDockBlock.Entity) h.getBlockEntity(FLOOR.west())).energy().getEnergyStored();
            int east = ((ChargingDockBlock.Entity) h.getBlockEntity(FLOOR.east())).energy().getEnergyStored();
            h.assertTrue(west >= 700 && east >= 700, "1,500 FE went " + west + " / " + east);
            h.succeed();
        });
    }

    // --- 4. an unpowered Cradle says so ----------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void anUnpoweredCradleSaysWhatItLacks(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(1);
        h.setBlock(at, ModRegistry.CRADLE.get());
        var cradle = (LifeSupportCradleBlockEntity) h.getBlockEntity(at);
        carrying(h, p);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        useBlock(h, p, at);
        h.assertTrue(p.getUUID().equals(cradle.owner()), "the head did not dock");
        h.assertValueEqual(Refusals.lastDelivered(p), "cradle.starved", "line on docking in an empty Cradle");
        // saline in, still no power: someone else's click hears the status and what it lacks
        cradle.saline().fill(new FluidStack(ModFluids.SALINE.source.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        ServerPlayer other = player(h, FLOOR.west(2));
        other.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        useBlock(h, other, at);
        h.assertValueEqual(Refusals.lastDelivered(other), "cradle.unpowered", "line on clicking a Cradle with no power");
        // powered and wet: nothing to warn about
        cradle.energy().set(10_000);
        int told = Refusals.delivered(other).size();
        useBlock(h, other, at);
        h.assertValueEqual(Refusals.delivered(other).size(), told, "a stocked Cradle still warned");
        done(h, p, other);
    }

    // --- 5. the corpse: its owner gets their things back --------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theOwnerOpensTheirCorpseAfterLettingGo(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        separate(h, p, FLOOR.east(3));
        p.hurt(p.damageSources().generic(), 1000);
        HeadlessBody corpse = (HeadlessBody) h.getLevel().getEntity(state(p).body());
        h.assertTrue(corpse != null && corpse.isCorpse(), "no corpse");
        // Let go, from the jar menu's button, then respawn as a fresh body
        FormActions.openMenu(p);
        h.assertTrue(p.containerMenu instanceof JarMenu, "the jar menu did not open");
        p.containerMenu.clickMenuButton(p, JarMenu.BUTTON_LET_GO);
        h.assertTrue(p.isDeadOrDying(), "Let go did not let go");
        ServerPlayer fresh = h.getLevel().getServer().getPlayerList().respawn(p, false, Entity.RemovalReason.KILLED);
        h.assertValueEqual(state(fresh).form(), Form.BODY, "form after respawning");
        h.assertTrue(corpse.isAlive() && corpse.isCorpse(), "the corpse went with the jar");
        fresh.moveTo(corpse.getX(), corpse.getY(), corpse.getZ() - 1.5, 0, 20);
        fresh.interactOn(corpse, InteractionHand.MAIN_HAND);
        h.assertTrue(fresh.containerMenu instanceof ChestMenu, "the owner's click didn't open their corpse");
        h.assertTrue(fresh.containerMenu.slots.stream().anyMatch(s -> s.getItem().is(Items.DIAMOND)), "the diamonds aren't in it");
        // sneaking, the same
        fresh.closeContainer();
        fresh.setShiftKeyDown(true);
        fresh.interactOn(corpse, InteractionHand.MAIN_HAND);
        h.assertTrue(fresh.containerMenu instanceof ChestMenu, "a sneaking owner's click didn't open their corpse");
        done(h, fresh);
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void bodyDiesCourierBringsTheCorpseKitFromItsEffects(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.north(5));
        p.getInventory().add(new ItemStack(ModRegistry.SUTURE_KIT.get()));
        p.getInventory().add(new ItemStack(Items.IRON_INGOT, 59));
        separate(h, p, FLOOR.south(5));
        // the body dies with everything on it: the player wakes in the jar, 10 blocks off
        p.hurt(p.damageSources().generic(), 1000);
        h.assertValueEqual(state(p).form(), Form.JAR, "form after the body died");
        h.assertTrue(p.position().distanceTo(h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.south(5)))) < 1, "not woken in the head");
        HeadlessBody corpse = (HeadlessBody) h.getLevel().getEntity(state(p).body());
        h.assertTrue(corpse.belongings().countItem(ModRegistry.SUTURE_KIT.get()) == 1, "the kit is not among the corpse's effects");
        // the jar menu's Courier button: booked
        FormActions.openMenu(p);
        p.containerMenu.clickMenuButton(p, JarMenu.BUTTON_COURIER);
        h.assertTrue(p.getPersistentData().getInt(Courier.KEY) > 0, "no Courier booked: " + Refusals.delivered(p));
        p.closeContainer();
        // the delivery's last tick (its 30 seconds skipped): the corpse is set down beside the jar
        p.getPersistentData().putInt(Courier.KEY, 1);
        tick(p);
        h.assertTrue(corpse.isAlive() && corpse.distanceTo(p) < 3, "the corpse was not delivered: " + corpse.distanceTo(p));
        // swap into a corpse: refused, and told to use the Suture Kit on it
        key(p, Network.Action.SWAP);
        h.assertValueEqual(Refusals.lastDelivered(p), "swap.corpse", "line for swapping into a corpse");
        // the jar's click on its own corpse uses the kit from among its effects: whole again, with its things
        p.interactOn(corpse, InteractionHand.MAIN_HAND);
        h.assertValueEqual(state(p).form(), Form.BODY, "form after the click on the corpse");
        h.assertTrue(p.getInventory().countItem(Items.IRON_INGOT) == 59, "the effects did not come back");
        h.assertFalse(corpse.isAlive(), "the corpse is still there");
        done(h, p);
    }

    // --- 7. a jar that leaves a host by any route is a plain jar ------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aJarOffItsHostByAnyRouteIsAPlainJar(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Cow cow = host(h, EntityType.COW, FLOOR.east(2), p);
        Pig pig = host(h, EntityType.PIG, FLOOR.west(2), p);
        ShellEntity rover = h.spawn(ModRegistry.ROVER.get(), FLOOR.north(3));
        FormActions.detach(p, 3);
        // a teleport off the cow (as /tp, a pearl or a portal moves a rider)
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertValueEqual(state(p).form(), Form.HOSTED, "form on the cow");
        Vec3 to = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.south(4)));
        p.teleportTo(h.getLevel(), to.x, to.y, to.z, java.util.Set.of(), 0, 0);
        List<String> seen = new ArrayList<>();
        h.runAfterDelay(2, () -> {
            seen.add("tp " + state(p).form() + " " + (p.getVehicle() != null));
            // the pig, removed by a command
            p.moveTo(pig.getX(), pig.getY(), pig.getZ());
            p.interactOn(pig, InteractionHand.MAIN_HAND);
            seen.add("pig " + state(p).form());
            pig.discard();
        });
        h.runAfterDelay(4, () -> {
            seen.add("discard " + state(p).form() + " " + (p.getVehicle() != null));
            // the Rover, destroyed under the jar
            p.moveTo(rover.getX(), rover.getY(), rover.getZ());
            p.interactOn(rover, InteractionHand.MAIN_HAND);
            seen.add("rover " + state(p).form());
            rover.kill();
        });
        h.runAfterDelay(6, () -> {
            seen.add("killed " + state(p).form() + " " + (p.getVehicle() != null));
            h.assertTrue(seen.equals(List.of("tp JAR false", "pig HOSTED", "discard JAR false", "rover HOSTED", "killed JAR false")), "forms: " + seen);
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aJarGetsOutBesideTheBodyNotInsideIt(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        MechaEntity m = h.spawn(ModRegistry.mechaType(MechaFrame.HOPPER), FLOOR.east(3));
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < MechaFrame.HOPPER.totalSlots(); i++) slots.add(ItemStack.EMPTY);
        slots.set(MechaFrame.HOPPER.firstIndex(MechaModule.HYDRAULIC_LEGS.slot), new ItemStack(ModRegistry.mechaModule(MechaModule.HYDRAULIC_LEGS)));
        m.setModules(slots);
        m.setOwner(p.getUUID());
        m.setBattery(50_000);
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertValueEqual(state(p).form(), Form.PILOT, "form after boarding");
        // the eject handle (sneak held with the jar menu key), as its payload arrives
        key(p, Network.Action.EJECT);
        h.assertValueEqual(state(p).form(), Form.JAR, "form after the eject");
        h.assertFalse(p.getBoundingBox().intersects(m.getBoundingBox()), "the jar got out inside the mecha's box: jar " + p.getBoundingBox() + ", mecha " + m.getBoundingBox() + ", width " + m.getBbWidth()
                + ", clear spot " + ShellEntity.clearOf(m, p));
        // a Rover, left by a sneak
        ShellEntity rover = h.spawn(ModRegistry.ROVER.get(), FLOOR.west(3));
        p.moveTo(rover.getX(), rover.getY(), rover.getZ() - 1.2);
        p.interactOn(rover, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == rover, "the jar did not board the Rover");
        p.setShiftKeyDown(true);
        p.rideTick();
        p.setShiftKeyDown(false);
        h.assertFalse(p.isPassenger(), "the sneak did not get the jar off the Rover");
        h.assertFalse(p.getBoundingBox().intersects(rover.getBoundingBox()), "the jar got out inside the Rover's box");
        done(h, p);
    }

    // --- 9. Telekinesis is the bare jar's -----------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void telekinesisEndsInACockpitAndTheKeyIsTheMechas(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        state(p).setModule(0, new ItemStack(ModRegistry.module(ModuleType.TELEKINESIS)));
        var cow = h.spawn(EntityType.COW, FLOOR.south(3));
        lookAt(p, cow.getBoundingBox().getCenter());
        key(p, Network.Action.ABILITY);
        h.assertTrue(state(p).heldEntity() == cow.getId(), "the cow was not lifted: " + Refusals.delivered(p));
        MechaEntity m = h.spawn(ModRegistry.mechaType(MechaFrame.HOPPER), FLOOR.east(3));
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < MechaFrame.HOPPER.totalSlots(); i++) slots.add(ItemStack.EMPTY);
        slots.set(MechaFrame.HOPPER.firstIndex(MechaModule.HYDRAULIC_LEGS.slot), new ItemStack(ModRegistry.mechaModule(MechaModule.HYDRAULIC_LEGS)));
        m.setModules(slots);
        m.setOwner(p.getUUID());
        m.setBattery(50_000);
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertValueEqual(state(p).form(), Form.PILOT, "form after boarding");
        h.assertTrue(state(p).heldEntity() < 0, "the hold went on into the cockpit");
        // in the cockpit with no utility module, the ability key is the mecha's: refused, nothing lifted
        int charge = state(p).charge();
        p.setXRot(60);
        key(p, Network.Action.ABILITY);
        h.assertValueEqual(Refusals.lastDelivered(p), "mecha.no_utility", "ability in a cockpit with no utility");
        h.assertTrue(state(p).heldEntity() < 0, "the jar's Telekinesis answered from the cockpit");
        h.assertTrue(state(p).charge() >= charge - 100, "the jar spent charge on a lift from the cockpit");
        // the same key bound with sprint: still the mecha's refusal, and no "the key does both" notice for a key that does neither
        key(p, Network.Action.ABILITY_SHARED);
        h.assertValueEqual(Refusals.lastDelivered(p), "mecha.no_utility", "shared ability key in a cockpit with no utility");
        h.assertTrue(state(p).heldEntity() < 0, "the shared key lifted something from the cockpit");
        h.assertFalse(p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getBoolean(com.coolfreeze23.headinajar.form.SharedKey.TOLD),
                "the shared-key notice was sent for a Telekinesis the cockpit never uses");
        done(h, p);
    }

    // --- 11. the pile says what went in -------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void thePileCountsWhatItWasFed(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        h.setBlock(FLOOR.east(1), ModRegistry.GALVANIC_PILE.get());
        var pile = (GalvanicPileBlockEntity) h.getBlockEntity(FLOOR.east(1));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ROTTEN_FLESH, 12));
        useBlock(h, p, FLOOR.east(1));
        h.assertTrue(p.getMainHandItem().isEmpty(), "the flesh did not go in");
        h.assertValueEqual(pile.fuelLeft(), 48_000, "FE the pile will make from 12 rotten flesh");
        done(h, p);
    }

    // --- 12. the jar menu key pressed by a whole body says why ------------------------------------------------------

    @GameTest(template = "platform")
    public static void theJarMenuKeyAsAWholeBodySaysWhy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        // the client's cancel arrives as a refuse request with its reason
        Refusals.fromClient(p, FormGuards.BODY_JAR_KEY, -1);
        h.assertValueEqual(Refusals.lastDelivered(p), FormGuards.BODY_JAR_KEY, "line for the jar menu key as a body");
        done(h, p);
    }
}
