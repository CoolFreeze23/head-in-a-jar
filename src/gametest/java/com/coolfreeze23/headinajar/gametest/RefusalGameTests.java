package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.client.SignHold;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Nudge;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Every refusal is an answer. These go through the path a real click or key press takes (the interaction the client
 * sends, a payload's handler, a menu button), never the function underneath.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class RefusalGameTests {
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

    /** A right-click on the top face of this block, as the client sends it. */
    private static void useBlock(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        BlockPos abs = h.absolutePos(pos);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
    }

    private static long count(ServerPlayer p, String reason) {
        return Refusals.delivered(p).stream().filter(reason::equals).count();
    }

    private static void key(ServerPlayer p, Network.Action action) {
        Network.action(p, new Network.JarAction(action));
    }

    // --- the Nudge ----------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void bareJarIsToldWhyByWhatItReachedFor(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        h.setBlock(FLOOR.east(2), Blocks.CHEST);
        useBlock(h, p, FLOOR.east(2));
        h.assertTrue(p.containerMenu == p.inventoryMenu, "a bare jar opened a chest");
        h.assertValueEqual(Refusals.lastDelivered(p), Nudge.CONTAINER, "chest line");
        h.setBlock(FLOOR.west(2), Blocks.LEVER);
        useBlock(h, p, FLOOR.west(2));
        h.assertValueEqual(Refusals.lastDelivered(p), Nudge.USE, "lever line");

        var cow = h.spawn(EntityType.COW, FLOOR.north(2));
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), Nudge.CREATURE, "cow line");
        float health = cow.getHealth();
        p.attack(cow);
        h.assertTrue(cow.getHealth() == health, "a bare jar hurt the cow");
        h.assertValueEqual(Refusals.lastDelivered(p), Nudge.ATTACK, "attack line");

        var villager = h.spawn(EntityType.VILLAGER, FLOOR.south(2));
        p.interactOn(villager, InteractionHand.MAIN_HAND);
        h.assertTrue(p.containerMenu == p.inventoryMenu, "a bare jar traded");
        h.assertValueEqual(Refusals.lastDelivered(p), Nudge.VILLAGER, "villager line");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void headMachinesStillAnswerABareJar(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        h.setBlock(FLOOR.east(2), ModRegistry.BODY_PRINTER.get());
        // the event a right-click fires (a test player can't be sent the menu itself)
        BlockPos abs = h.absolutePos(FLOOR.east(2));
        var click = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, abs, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        h.assertFalse(NeoForge.EVENT_BUS.post(click).isCanceled(), "the Body Printer was locked to a jar");
        h.assertTrue(Refusals.delivered(p).isEmpty(), "a head machine refused a jar: " + Refusals.delivered(p));
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void sameNudgeIsAnsweredOnceInTenTicks(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        h.setBlock(FLOOR.east(2), Blocks.CHEST);
        for (int i = 0; i < 5; i++) useBlock(h, p, FLOOR.east(2));
        h.assertValueEqual(count(p, Nudge.CONTAINER), 1L, "refusals for five clicks in one tick");
        h.runAfterDelay(Refusals.DEBOUNCE + 1, () -> {
            useBlock(h, p, FLOOR.east(2));
            h.assertValueEqual(count(p, Nudge.CONTAINER), 2L, "no answer after the debounce");
            done(h, p);
        });
    }

    /** The client decides most Nudges (it cancels the click), and asks the server for them with a payload. */
    @GameTest(template = "platform")
    public static void clientCancelledClicksAreRefusedOutLoud(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        // what the client works out from its crosshair
        BlockPos chest = FLOOR.east(2), floor = FLOOR.west(2);
        h.setBlock(chest, Blocks.CHEST);
        var level = h.getLevel();
        BlockPos absChest = h.absolutePos(chest);
        h.assertValueEqual(Nudge.reason(level, new BlockHitResult(Vec3.atCenterOf(absChest), Direction.UP, absChest, false), false), Nudge.CONTAINER, "chest");
        h.assertValueEqual(Nudge.reason(level, new BlockHitResult(Vec3.atCenterOf(absChest), Direction.UP, absChest, false), true), Nudge.MINE, "mining");
        BlockPos absFloor = h.absolutePos(floor.below());
        Vec3 top = Vec3.atCenterOf(absFloor).add(0, 0.5, 0);
        ItemEntity dropped = new ItemEntity(level, top.x, top.y, top.z, new ItemStack(Items.APPLE));
        dropped.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(dropped);
        h.assertValueEqual(Nudge.reason(level, new BlockHitResult(top, Direction.UP, absFloor, false), false), Nudge.PICKUP, "item on the ground");
        h.assertValueEqual(Nudge.reason(level, BlockHitResult.miss(top, Direction.UP, absFloor), false), Nudge.USE, "the air");

        // the payload's handler
        var villager = h.spawn(EntityType.VILLAGER, FLOOR.south(2));
        Refusals.fromClient(p, Nudge.VILLAGER, villager.getId());
        h.assertValueEqual(Refusals.lastDelivered(p), Nudge.VILLAGER, "client-cancelled trade");
        h.assertTrue(villager.getUnhappyCounter() > 0, "the villager did not shake its head");
        Refusals.fromClient(p, "made.up.reason", -1);
        h.assertValueEqual(Refusals.delivered(p).size(), 1, "a client got an unregistered refusal through");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void bareJarIsToldOncePerItemItRollsOnto(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        ItemEntity apple = new ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), new ItemStack(Items.APPLE));
        apple.setNoPickUpDelay();
        h.getLevel().addFreshEntity(apple);
        apple.playerTouch(p);
        apple.playerTouch(p);
        h.assertFalse(p.getInventory().contains(new ItemStack(Items.APPLE)), "a bare jar picked up an apple");
        h.assertValueEqual(count(p, Nudge.PICKUP), 1L, "pickup refusals");
        done(h, p);
    }

    // --- the Glug -------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void abilityKeyNamesTheMissingModule(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        key(p, Network.Action.ABILITY);
        h.assertValueEqual(Refusals.lastDelivered(p), "jar.no_module", "no Telekinesis");
        state(p).setModule(0, new ItemStack(ModRegistry.module(ModuleType.TELEKINESIS)));
        state(p).setCharge(0);
        key(p, Network.Action.ABILITY);
        h.assertValueEqual(Refusals.lastDelivered(p), "jar.no_charge", "Telekinesis without charge");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void secondaryKeyNamesTheLaser(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        key(p, Network.Action.SECONDARY);
        h.assertValueEqual(Refusals.lastDelivered(p), "jar.no_module", "no Lacrimal Laser");
        state(p).setModule(0, new ItemStack(ModRegistry.module(ModuleType.LACRIMAL_LASER)));
        int full = state(p).charge();
        state(p).setCharge(100);
        key(p, Network.Action.SECONDARY);
        h.assertValueEqual(Refusals.lastDelivered(p), "jar.laser_low", "laser without charge");
        state(p).setCharge(full);
        key(p, Network.Action.SECONDARY);
        h.assertTrue(state(p).charge() < full, "the laser did not fire");
        key(p, Network.Action.SECONDARY);
        h.assertValueEqual(Refusals.lastDelivered(p), "jar.laser_cooling", "laser on cooldown");
        done(h, p);
    }

    // --- the Swap key -----------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void swapSaysWhyNot(GameTestHelper h) {
        // a body that died: a corpse
        ServerPlayer dead = player(h, FLOOR);
        FormActions.separate(dead, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.east(3))), null);
        dead.hurt(dead.damageSources().generic(), 1000);
        h.assertTrue(state(dead).form() == Form.JAR, "the body's death did not wake the head");
        key(dead, Network.Action.SWAP);
        h.assertValueEqual(Refusals.lastDelivered(dead), "swap.corpse", "corpse");
        h.assertTrue(state(dead).form() == Form.JAR, "swapped into a corpse");

        // the admin detach leaves no Collar Cam on the jar
        ServerPlayer bare = player(h, FLOOR.west(2));
        FormActions.detach(bare, 3);
        key(bare, Network.Action.SWAP);
        h.assertValueEqual(Refusals.lastDelivered(bare), "swap.no_collar", "no collar");

        // no body on file at all
        HeadRegistry.get(bare).setBody(bare.getUUID(), null);
        key(bare, Network.Action.SWAP);
        h.assertValueEqual(Refusals.lastDelivered(bare), "swap.no_body", "no body");

        // out of range: goggles that reach nowhere
        ServerPlayer far = player(h, FLOOR.north(2));
        FormActions.separate(far, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.north(4))), null);
        key(far, Network.Action.SWAP);
        h.assertTrue(state(far).form() == Form.JAR, "could not swap into the head");
        state(far).setGoggles(ItemStack.EMPTY);
        key(far, Network.Action.SWAP);
        h.assertValueEqual(Refusals.lastDelivered(far), "swap.weak_signal", "weak signal");
        h.assertTrue(state(far).form() == Form.JAR, "linked on no signal");
        done(h, dead, bare, far);
    }

    @GameTest(template = "platform")
    public static void swapFromABodyWithNoHeadOnFile(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.east(3))), null);
        HeadRegistry.get(p).setHead(p.getUUID(), null);
        key(p, Network.Action.SWAP);
        h.assertTrue(state(p).form() == Form.LINKED, "left the body with no head to go to");
        h.assertValueEqual(Refusals.lastDelivered(p), "swap.no_head", "no head");
        done(h, p);
    }

    // --- mecha ----------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void gantryButtonRefusesAFrameThatCannotWalk(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        gantry.energy().set(20_000);
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.HOPPER)));
        // the menu the Gantry opens, and what the Assemble button's packet does with it on the server
        var menu = gantry.createMenu(1, p.getInventory(), p);
        h.assertFalse(menu.clickMenuButton(p, AssemblyGantryMenu.ASSEMBLE), "built a Hopper with no legs");
        h.assertValueEqual(Refusals.lastDelivered(p), "gantry.no_legs", "legs line");
        h.assertTrue(gantry.onPad() == null, "a mecha stands on the pad");
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + MechaFrame.HOPPER.firstIndex(MechaModule.Slot.LEGS),
                new ItemStack(ModRegistry.mechaModule(MechaModule.STILTS)));
        h.assertTrue(menu.clickMenuButton(p, AssemblyGantryMenu.ASSEMBLE), "refused a Hopper on Stilts");
        done(h, p);
    }

    private static MechaEntity piloted(GameTestHelper h, ServerPlayer pilot, MechaModule arm, int battery) {
        MechaEntity m = h.spawn(ModRegistry.HOPPER.get(), FLOOR.east(3));
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < m.frame().totalSlots(); i++) slots.add(ItemStack.EMPTY);
        slots.set(m.frame().firstIndex(MechaModule.Slot.LEGS), new ItemStack(ModRegistry.mechaModule(MechaModule.HYDRAULIC_LEGS)));
        slots.set(m.frame().firstIndex(MechaModule.Slot.ARM), new ItemStack(ModRegistry.mechaModule(arm)));
        m.setModules(slots);
        m.setOwner(pilot.getUUID());
        m.setBattery(battery);
        FormActions.detach(pilot, 3);
        h.assertTrue(FormActions.mountHost(pilot, m), "could not board");
        return m;
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void flatMechaArmRefusesOncePerPress(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = piloted(h, p, MechaModule.HYDRAULIC_FIST, 0);
        Network.arms(p, 1);
        h.runAfterDelay(15, () -> {
            h.assertValueEqual(count(p, "mecha.arm.no_power"), 1L, "refusals while the button was held for 15 ticks");
            Network.arms(p, 0);
            h.runAfterDelay(3, () -> {
                Network.arms(p, 1);
                h.runAfterDelay(3, () -> {
                    h.assertValueEqual(count(p, "mecha.arm.no_power"), 2L, "a second press got no answer");
                    Network.arms(p, 0);
                    done(h, p);
                });
            });
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void mechaArmSaysWhenNothingIsFitted(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = piloted(h, p, MechaModule.HYDRAULIC_FIST, 100_000);
        // the right button: the Hopper's second arm slot is empty
        Network.arms(p, 2);
        h.runAfterDelay(3, () -> {
            h.assertValueEqual(count(p, "mecha.arm.empty"), 1L, "empty arm refusals");
            Network.arms(p, 0);
            done(h, p);
        });
    }

    // --- the Sign button ----------------------------------------------------------------------------------

    /** The chair screen's hold logic, fed the presses and releases the screen receives. */
    @GameTest(template = "platform")
    public static void signHoldTakesTwoSecondsWithAnyButton(GameTestHelper h) {
        SignHold hold = new SignHold();
        int signed = 0;
        // the right mouse button (a player with attack and use swapped), held over the button
        hold.press(1);
        for (int t = 0; t < SignHold.HOLD_TICKS + 20; t++) if (hold.tick(true, true)) signed++;
        h.assertValueEqual(signed, 1, "signatures from one long hold");
        h.assertTrue(hold.release(1), "release not taken");
        // released early: nothing
        hold.press(0);
        for (int t = 0; t < SignHold.HOLD_TICKS - 5; t++) h.assertFalse(hold.tick(true, true), "signed before two seconds");
        hold.release(0);
        h.assertFalse(hold.tick(true, true), "signed after letting go");
        // slid off the button: the hold ends, and coming back does not resume it
        hold.press(0);
        for (int t = 0; t < 30; t++) hold.tick(true, true);
        hold.tick(false, true);
        h.assertFalse(hold.holding(), "still holding off the button");
        for (int t = 0; t < 20; t++) h.assertFalse(hold.tick(true, true), "resumed after sliding off");
        // another button's release doesn't end it; the screen closing does
        hold.press(0);
        h.assertFalse(hold.release(1), "the wrong button let go");
        hold.reset();
        h.assertFalse(hold.holding(), "closing the screen left it held");
        h.succeed();
    }
}
