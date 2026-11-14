package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.block.BodyLockerBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The 0.5.0 release's own fixes, each through the path the game takes: a block broken through the player's own break
 * (the game mode, which raises the break event), a cable's offer through the block's energy capability, the key's packet
 * handler, the Gantry's menu buttons, the player's tick, and what the player's connection is really sent.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReleaseFiveGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);
    private static final BlockPos LOCKER = FLOOR.north(2);

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

    private static <T extends Mob> T host(GameTestHelper h, EntityType<T> type, BlockPos at, ServerPlayer sawyer) {
        T mob = h.spawn(type, at);
        mob.setNoAi(true);
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "saw failed on " + type);
        return mob;
    }

    /** The player breaks the block as a real break does: the game mode's destroy, which raises the break event first. */
    private static boolean breakBlock(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        return p.gameMode.destroyBlock(h.absolutePos(pos));
    }

    private static BodyLockerBlockEntity lockerWithAPig(GameTestHelper h, ServerPlayer owner) {
        h.setBlock(LOCKER, ModRegistry.BODY_LOCKER.get());
        var locker = (BodyLockerBlockEntity) h.getBlockEntity(LOCKER);
        host(h, EntityType.PIG, LOCKER.south(), owner);
        locker.use(owner);
        h.assertTrue(locker.full(), "the pig was not checked in");
        return locker;
    }

    private static boolean lockerStands(GameTestHelper h) {
        return h.getBlockState(LOCKER).is(ModRegistry.BODY_LOCKER.get());
    }

    /** Every chat and action-bar line the connection has been sent since the last call (the packets themselves). */
    private static List<Component> sent(ServerPlayer p, boolean overlay) {
        List<Component> lines = new ArrayList<>();
        if (!(p.connection.getConnection().channel() instanceof io.netty.channel.embedded.EmbeddedChannel wire)) return lines;
        for (Object o; (o = wire.readOutbound()) != null; ) {
            if (o instanceof ClientboundSystemChatPacket line && line.overlay() == overlay) lines.add(line.content());
        }
        return lines;
    }

    private static boolean hasKey(Component c, String key) {
        if (c.getContents() instanceof TranslatableContents t) {
            if (t.getKey().equals(key)) return true;
            for (Object a : t.getArgs()) if (a instanceof Component arg && hasKey(arg, key)) return true;
        }
        for (Component s : c.getSiblings()) if (hasKey(s, key)) return true;
        return false;
    }

    // --- the Body Locker ----------------------------------------------------------------------------------------

    /** A stranger breaking a locker that holds someone's body (either cell) is refused; the body and the locker stay. */
    @GameTest(template = "platform")
    public static void aStrangerCannotBreakALockerHoldingABody(GameTestHelper h) {
        ServerPlayer owner = player(h, FLOOR), stranger = player(h, FLOOR.east());
        var locker = lockerWithAPig(h, owner);
        h.assertFalse(breakBlock(h, stranger, LOCKER), "a stranger broke the locker");
        h.assertTrue(lockerStands(h) && locker.full(), "the locker or its body went");
        h.assertValueEqual(Refusals.lastDelivered(stranger), "locker.not_yours_break", "the stranger's answer");
        // the top cell comes on the locker's first tick
        h.runAfterDelay(3, () -> {
            h.assertTrue(h.getBlockState(LOCKER.above()).is(ModRegistry.MACHINE_TOP.get()), "the locker has no top cell");
            h.assertFalse(breakBlock(h, stranger, LOCKER.above()), "a stranger broke the locker by its top");
            h.assertTrue(lockerStands(h) && locker.full(), "the locker went by its top");
            h.assertTrue(h.getEntities(EntityType.PIG).stream().noneMatch(e -> !e.isRemoved()), "the body came out for a stranger");
            done(h, owner, stranger);
        });
    }

    /** The owner, and someone the owner trusts, break it as before: the body walks out. */
    @GameTest(template = "platform")
    public static void theOwnerOrATrustedColleagueBreaksALocker(GameTestHelper h) {
        ServerPlayer owner = player(h, FLOOR), colleague = player(h, FLOOR.east());
        lockerWithAPig(h, owner);
        var registry = HeadRegistry.get(h.getLevel().getServer());
        registry.setTrust(owner.getUUID(), colleague.getUUID(), true);
        try {
            h.assertTrue(breakBlock(h, colleague, LOCKER), "a trusted colleague could not break the locker");
        } finally {
            registry.setTrust(owner.getUUID(), colleague.getUUID(), false);
        }
        h.assertFalse(lockerStands(h), "the locker still stands");
        h.assertTrue(h.getEntities(EntityType.PIG).stream().anyMatch(e -> !e.isRemoved()), "the body did not walk out");
        lockerWithAPig(h, owner);
        h.runAfterDelay(3, () -> {
            h.assertTrue(breakBlock(h, owner, LOCKER.above()), "the owner could not break the locker by its top");
            h.assertFalse(lockerStands(h), "the owner's locker still stands");
            done(h, owner, colleague);
        });
    }

    /** An empty locker, and one stored before owners were saved, break for anyone. */
    @GameTest(template = "platform")
    public static void anEmptyOrOldLockerBreaksForAnyone(GameTestHelper h) {
        ServerPlayer owner = player(h, FLOOR), stranger = player(h, FLOOR.east());
        h.setBlock(LOCKER, ModRegistry.BODY_LOCKER.get());
        h.assertTrue(breakBlock(h, stranger, LOCKER), "an empty locker would not break");
        var locker = lockerWithAPig(h, owner);
        var saved = locker.saveWithFullMetadata(h.getLevel().registryAccess());
        saved.remove("StoredOwner");
        h.setBlock(LOCKER, Blocks.AIR);
        h.setBlock(LOCKER, ModRegistry.BODY_LOCKER.get());
        ((BodyLockerBlockEntity) h.getBlockEntity(LOCKER)).loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(breakBlock(h, stranger, LOCKER), "an old locker would not break for anyone");
        h.assertTrue(h.getEntities(EntityType.PIG).stream().anyMatch(e -> !e.isRemoved()), "the old locker's body did not walk out");
        done(h, owner, stranger);
    }

    /** One of the attack button's dig packets on a block, through the game mode's own handler. */
    private static void dig(GameTestHelper h, ServerPlayer p, BlockPos pos, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action action) {
        p.gameMode.handleBlockBreakAction(h.absolutePos(pos), action, net.minecraft.core.Direction.SOUTH, h.getLevel().getMaxBuildHeight(), 0);
    }

    /** A survival dig carried on long enough to finish: the game mode's ticks (the server runs one a tick), then the finish. */
    private static void finishDig(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        for (int i = 0; i < 2000; i++) p.gameMode.tick();
        dig(h, p, pos, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK);
    }

    private static long told(ServerPlayer p, String reason) {
        return Refusals.delivered(p).stream().filter(reason::equals).count();
    }

    /**
     * Holding attack on a stranger's full locker breaks it again and again: the client sends one start of digging,
     * then a finished dig each time the block comes back. The refusal is said once per hold, and again for a new press.
     */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void holdingAttackOnAStrangersLockerSaysItOnce(GameTestHelper h) {
        var start = net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK;
        ServerPlayer owner = player(h, FLOOR), stranger = player(h, FLOOR.east());
        var locker = lockerWithAPig(h, owner);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
        stranger.setOnGround(true);
        dig(h, stranger, LOCKER, start);
        finishDig(h, stranger, LOCKER);
        h.assertTrue(lockerStands(h) && locker.full(), "the locker went at the first dig");
        h.assertValueEqual(told(stranger, "locker.not_yours_break"), 1L, "lines after the first refused break (progress "
                + h.getBlockState(LOCKER).getDestroyProgress(stranger, h.getLevel(), h.absolutePos(LOCKER)) + ", reach "
                + stranger.canInteractWithBlock(h.absolutePos(LOCKER), 1.0) + ", delivered " + Refusals.delivered(stranger) + ")");
        // the button still held: the next finished dig comes with no new start (past the refusal rule's 10-tick guard)
        h.runAfterDelay(14, () -> {
            finishDig(h, stranger, LOCKER);
            h.assertTrue(lockerStands(h) && locker.full(), "the locker went at the held dig");
            h.assertValueEqual(told(stranger, "locker.not_yours_break"), 1L, "lines while the button is held");
            // let go and press again: a new start
            h.runAfterDelay(14, () -> {
                dig(h, stranger, LOCKER, start);
                finishDig(h, stranger, LOCKER);
                h.assertTrue(lockerStands(h) && locker.full(), "the locker went at the new press");
                h.assertValueEqual(told(stranger, "locker.not_yours_break"), 2L, "lines after a new press");
                done(h, owner, stranger);
            });
        });
    }

    // --- goggles from an old Curios head slot ---------------------------------------------------------------------

    /** What Curios does with an item from a slot it no longer has: gives it to the player, the rest dropped at their feet. */
    private static void curiosHandBack(ServerPlayer p, ItemStack stack) {
        net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(p, stack);
    }

    private static void fill(net.minecraft.world.Container c) {
        for (int i = 0; i < c.getContainerSize(); i++) c.setItem(i, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
    }

    private static int count(net.minecraft.world.Container c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    /**
     * Goggles handed back from an old Curios head slot are never lost: into the inventory; with the inventory full,
     * into the Ender Chest; with both full, at the player's feet, where they never despawn. The player is told where.
     */
    @GameTest(template = "platform")
    public static void gogglesFromAnOldCuriosSlotAreNeverLost(GameTestHelper h) {
        var goggles = ModRegistry.RELAY_GOGGLES_MK2.get();
        ServerPlayer roomy = player(h, FLOOR), full = player(h, FLOOR.east()), fuller = player(h, FLOOR.west());
        for (ServerPlayer p : List.of(roomy, full, fuller)) sent(p, false);

        com.coolfreeze23.headinajar.compat.OldHeadSlot.giveBack(roomy, () -> curiosHandBack(roomy, new ItemStack(goggles)));
        h.assertValueEqual(count(roomy.getInventory(), goggles), 1, "goggles in a roomy inventory");
        h.assertTrue(sent(roomy, false).stream().anyMatch(c -> hasKey(c, "message.headinajar.curios_head.inventory")), "the roomy player was not told");

        fill(full.getInventory());
        com.coolfreeze23.headinajar.compat.OldHeadSlot.giveBack(full, () -> curiosHandBack(full, new ItemStack(goggles)));
        h.assertValueEqual(count(full.getEnderChestInventory(), goggles), 1, "goggles in the Ender Chest of a full inventory");
        h.assertTrue(h.getEntities(EntityType.ITEM).stream().noneMatch(e -> e.getItem().is(goggles)), "goggles were dropped although the Ender Chest had room");
        h.assertTrue(sent(full, false).stream().anyMatch(c -> hasKey(c, "message.headinajar.curios_head.ender_chest")), "the full player was not told");

        fill(fuller.getInventory());
        fill(fuller.getEnderChestInventory());
        com.coolfreeze23.headinajar.compat.OldHeadSlot.giveBack(fuller, () -> curiosHandBack(fuller, new ItemStack(goggles)));
        var drop = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, fuller.getBoundingBox().inflate(2), e -> e.getItem().is(goggles));
        h.assertValueEqual(drop.size(), 1, "goggles at the feet of a player with no room anywhere");
        h.assertValueEqual(drop.get(0).getAge(), -32768, "the dropped goggles' age (they must never despawn)");
        h.assertTrue(sent(fuller, false).stream().anyMatch(c -> hasKey(c, "message.headinajar.curios_head.floor")), "the fullest player was not told");
        drop.get(0).discard();
        done(h, roomy, full, fuller);
    }

    /** A cable's offer is refused (the kiosk needs no power); a locker saved with a charge loads it unchanged. */
    @GameTest(template = "platform")
    public static void aLockerTakesNoPowerAndKeepsAnOldCharge(GameTestHelper h) {
        h.setBlock(LOCKER, ModRegistry.BODY_LOCKER.get());
        var cap = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(LOCKER), net.minecraft.core.Direction.SOUTH);
        h.assertTrue(cap != null, "the locker has no energy capability");
        h.assertValueEqual(cap.receiveEnergy(1000, false), 0, "power the locker took");
        h.assertFalse(cap.canReceive(), "the locker says it takes power");
        var locker = (BodyLockerBlockEntity) h.getBlockEntity(LOCKER);
        var saved = locker.saveWithFullMetadata(h.getLevel().registryAccess());
        saved.put("Energy", net.minecraft.nbt.IntTag.valueOf(5000));
        locker.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertValueEqual(locker.energy().getEnergyStored(), 5000, "an old locker's charge after loading");
        h.succeed();
    }

    // --- Telekinesis, memos -------------------------------------------------------------------------------------

    /** Lifting the parked mecha of an owner this server has never seen names them "another player", not "?". */
    @GameTest(template = "platform")
    public static void anUnknownOwnerIsAnotherPlayer(GameTestHelper h) {
        ServerPlayer lifter = player(h, FLOOR.west(3));
        MechaEntity m = h.spawn(ModRegistry.mechaType(MechaFrame.HOPPER), FLOOR.east(1));
        m.setNoAi(true);
        m.setOwner(java.util.UUID.randomUUID());
        FormActions.detach(lifter, 3);
        FormActions.state(lifter).setModule(0, new ItemStack(ModRegistry.module(ModuleType.TELEKINESIS)));
        FormActions.state(lifter).setCharge(100_000);
        lifter.setYRot(-90);
        lifter.setYHeadRot(-90);
        lifter.setXRot(0);
        sent(lifter, true);
        Network.action(lifter, new Network.JarAction(Network.Action.ABILITY));
        h.assertValueEqual(Refusals.lastDelivered(lifter), "tk.not_trusted", "lifting a stranger's mecha");
        List<Component> bar = sent(lifter, true);
        Component line = bar.stream().filter(c -> hasKey(c, "refusal.headinajar.tk.not_trusted")).findFirst().orElse(null);
        h.assertTrue(line != null, "the refusal never reached the connection: " + bar);
        h.assertTrue(hasKey(line, "message.headinajar.another_player"), "the owner is not named as another player: " + line.getString());
        m.discard();
        done(h, lifter);
    }

    /** A memo goes on the wire as a translation (its key and arguments), so each reader's own client words it. */
    @GameTest(template = "platform")
    public static void aMemoTravelsAsATranslationNotText(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        sent(p, false);
        for (int i = 0; i < 3; i++) {
            p.tickCount = (p.tickCount / 20 + 1) * 20;
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
        }
        List<Component> chat = sent(p, false);
        Component memo = chat.stream().filter(c -> hasKey(c, "memo.headinajar.from")).findFirst().orElse(null);
        h.assertTrue(memo != null, "no memo reached the connection: " + chat);
        h.assertTrue(hasKey(memo, com.coolfreeze23.headinajar.clarity.Memos.Memo.NO_HANDS.key()), "the memo's text is not its translation: " + memo);
        h.assertTrue(hasKey(memo, "item.headinajar.manipulator_arm"), "the memo's argument is not a translation: " + memo);
        done(h, p);
    }

    // --- the Gantry's Thrust slider -----------------------------------------------------------------------------

    /** The Thrust slider set with the Gantry's own buttons is the lift the Jump Jets give, through the jet key. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theThrustSliderSetsTheJetsLift(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(4));
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.HOPPER)));
        int back = MechaFrame.HOPPER.firstIndex(MechaModule.Slot.BACK);
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + back, new ItemStack(ModRegistry.mechaModule(MechaModule.JUMP_JETS)));
        var menu = gantry.createMenu(1, p.getInventory(), p);
        for (int i = 0; i < back; i++) menu.clickMenuButton(p, AssemblyGantryMenu.NEXT);
        for (int i = 0; i < 6; i++) menu.clickMenuButton(p, AssemblyGantryMenu.PLUS);
        ItemStack full = gantry.items().getStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + back).copy();
        h.assertValueEqual(MechaModuleItem.slider(full), 100, "the Thrust after six presses of +");
        for (int i = 0; i < 12; i++) menu.clickMenuButton(p, AssemblyGantryMenu.MINUS);
        ItemStack none = gantry.items().getStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + back).copy();
        h.assertValueEqual(MechaModuleItem.slider(none), 0, "the Thrust after twelve presses of -");
        ServerPlayer a = player(h, FLOOR.east(4)), b = player(h, FLOOR.east(4));
        double high = lift(h, a, full), low = lift(h, b, none);
        h.assertTrue(Math.abs(high - 0.85) < 1e-6, "full Thrust lifts " + high);
        h.assertTrue(Math.abs(low - 0.45) < 1e-6, "no Thrust lifts " + low);
        done(h, p, a, b);
    }

    /** A Hopper in the air with these jets, boarded by the player, and the jet key's handler: its upward speed after. */
    private static double lift(GameTestHelper h, ServerPlayer pilot, ItemStack jets) {
        MechaEntity m = h.spawn(ModRegistry.HOPPER.get(), FLOOR.east(4).above(3));
        m.setNoGravity(true);
        m.setOnGround(false);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < m.frame().totalSlots(); i++) slots.add(ItemStack.EMPTY);
        slots.set(m.frame().firstIndex(MechaModule.Slot.LEGS), new ItemStack(ModRegistry.mechaModule(MechaModule.HYDRAULIC_LEGS)));
        slots.set(m.frame().firstIndex(MechaModule.Slot.BACK), jets);
        m.setModules(slots);
        m.setOwner(pilot.getUUID());
        m.setBattery(100_000);
        FormActions.detach(pilot, 3);
        h.assertTrue(FormActions.mountHost(pilot, m), "could not board");
        m.setOnGround(false);
        Network.action(pilot, new Network.JarAction(Network.Action.JET));
        double y = m.getDeltaMovement().y;
        m.discard();
        return y;
    }
    // --- the README pass's findings ------------------------------------------------------------------------------

    /** A use on a block face with what the player holds, as a click does it (a placed block, a pile fed). */
    private static InteractionResult useOn(GameTestHelper h, ServerPlayer p, BlockPos pos, net.minecraft.core.Direction face) {
        BlockPos abs = h.absolutePos(pos);
        return p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(abs), face, abs, false));
    }

    private static boolean earned(ServerPlayer p, String id) {
        var adv = p.server.getAdvancements().get(HeadInAJar.id(id));
        return adv != null && p.getAdvancements().getOrStartProgress(adv).isDone();
    }

    /** "Take a Seat" asks for a chair touching a fed pile: a chair beside an empty pile is not it; feeding the pile is. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void takeASeatNeedsTheChairBesideAFedPile(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        p.setGameMode(GameType.CREATIVE);
        BlockPos pile = FLOOR.east(1);
        h.setBlock(pile, ModRegistry.GALVANIC_PILE.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SEPARATION_CHAIR_ITEM.get()));
        useOn(h, p, FLOOR.below(), net.minecraft.core.Direction.UP);
        h.assertTrue(h.getBlockState(FLOOR).is(ModRegistry.SEPARATION_CHAIR.get()), "the chair was not placed");
        h.assertFalse(earned(p, "chair"), "Take a Seat earned beside a pile with nothing in it");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 4));
        useOn(h, p, pile, net.minecraft.core.Direction.SOUTH);
        h.assertTrue(earned(p, "chair"), "feeding the pile beside the chair did not earn Take a Seat");
        // the other order: a second player places a chair against the pile, now fed
        ServerPlayer q = player(h, FLOOR.south(2).east(2));
        q.setGameMode(GameType.CREATIVE);
        q.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SEPARATION_CHAIR_ITEM.get()));
        useOn(h, q, pile.east().below(), net.minecraft.core.Direction.UP);
        h.assertTrue(h.getBlockState(pile.east()).is(ModRegistry.SEPARATION_CHAIR.get()), "the second chair was not placed");
        h.assertTrue(earned(q, "chair"), "a chair placed against a fed pile did not earn Take a Seat");
        done(h, p, q);
    }

    /** A linked body's death is told in one line: the wake-up line, not HR's card as well. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aLinkedBodysDeathIsOneLine(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        h.assertTrue(FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.east(3))), null), "separate failed");
        sent(p, false);
        p.hurt(p.damageSources().generic(), 1000);
        h.runAfterDelay(5, () -> {
            List<Component> chat = sent(p, false);
            h.assertTrue(chat.stream().anyMatch(c -> hasKey(c, "message.headinajar.body_died")), "no line for the body's death: " + chat);
            h.assertFalse(chat.stream().anyMatch(c -> hasKey(c, "message.headinajar.body.hr_card")), "HR's card said it again: " + chat);
            done(h, p);
        });
    }

    /** A creature that lives through a cut into an off-hand jar shows no cut at all: no head arcs into the jar. */
    @GameTest(template = "platform")
    public static void noHeadArcsIntoTheJarFromACreatureThatLives(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var lucky = h.spawn(EntityType.ZOMBIE, FLOOR.west(2));
        lucky.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
        lucky.setHealth(1);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        com.coolfreeze23.headinajar.fx.Fx.clearSent();
        p.interactOn(lucky, InteractionHand.MAIN_HAND);
        h.assertTrue(lucky.isAlive(), "the totem did not save it");
        h.assertTrue(com.coolfreeze23.headinajar.fx.Fx.sent().stream().noneMatch(s -> s.fx().kind() == com.coolfreeze23.headinajar.fx.Fx.HOST_CUT
                && s.fx().entityB() == lucky.getId()), "the cut was shown on a creature that lived");
        var doomed = h.spawn(EntityType.ZOMBIE, FLOOR.east(2));
        doomed.setHealth(1);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
        p.interactOn(doomed, InteractionHand.MAIN_HAND);
        h.assertTrue(com.coolfreeze23.headinajar.fx.Fx.sent().stream().anyMatch(s -> s.fx().kind() == com.coolfreeze23.headinajar.fx.Fx.HOST_CUT
                && s.fx().entityB() == doomed.getId() && s.fx().arg() == 1), "the head did not arc into the jar from a killing cut");
        done(h, p);
    }

    /** One Guideposts look (the memos), as the player's tick runs it every second. */
    private static void memoTick(ServerPlayer p) {
        p.tickCount = (p.tickCount / 20 + 1) * 20;
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
    }

    /**
     * Only a new employee is welcomed: a player who logs in with no history gets the book and the welcome; one who
     * already carries a handbook, or has earned the company's advancements before (an upgraded world), is not
     * welcomed as new, and the one carrying a copy is not handed a second.
     */
    @GameTest(template = "platform")
    public static void onlyANewEmployeeIsWelcomed(GameTestHelper h) {
        ServerPlayer fresh = player(h, FLOOR), carrying = player(h, FLOOR.east()), veteran = player(h, FLOOR.west());
        ItemStack book = com.coolfreeze23.headinajar.item.Handbook.copy(h.getLevel().getServer());
        carrying.getInventory().setItem(20, book.copy());
        var separated = h.getLevel().getServer().getAdvancements().get(HeadInAJar.id("separated"));
        for (String c : separated.value().criteria().keySet()) veteran.getAdvancements().award(separated, c);
        com.coolfreeze23.headinajar.item.Handbook.giveToTestPlayers = true;
        try {
            for (ServerPlayer p : List.of(fresh, carrying, veteran)) {
                NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));
            }
        } finally {
            com.coolfreeze23.headinajar.item.Handbook.giveToTestPlayers = false;
        }
        for (ServerPlayer p : List.of(fresh, carrying, veteran)) memoTick(p);
        long carried = carrying.getInventory().items.stream().filter(s -> ItemStack.isSameItemSameComponents(s, book)).count();
        h.assertValueEqual(carried, 1L, "handbooks carried by the player who already had one");
        var welcome = com.coolfreeze23.headinajar.clarity.Memos.Memo.WELCOME;
        h.assertTrue(com.coolfreeze23.headinajar.clarity.Memos.sent(fresh, welcome), "a new employee was not welcomed");
        h.assertFalse(com.coolfreeze23.headinajar.clarity.Memos.sent(carrying, welcome), "a player who had the book was welcomed as new");
        h.assertFalse(com.coolfreeze23.headinajar.clarity.Memos.sent(veteran, welcome), "a veteran was welcomed as new");
        done(h, fresh, carrying, veteran);
    }

    // --- the build's seat ticks ---------------------------------------------------------------------------------

    /**
     * Every frame's server seat ticks (when a fitted module clunks home and the Gantry welds it) are its build clip's own
     * seat keys ({@code build:seat:<slot>} in the clip's timeline), read from the clip the game ships.
     */
    @GameTest(template = "platform")
    public static void everyFramesSeatTicksAreItsClipsSeatKeys(GameTestHelper h) {
        List<String> wrong = new ArrayList<>();
        for (MechaFrame frame : MechaFrame.values()) {
            var rig = com.coolfreeze23.headinajar.mecha.MechaRig.of(frame);
            if (rig == null) continue;
            String path = "/assets/headinajar/animations/entity/mecha/" + rig.id + ".animation.json";
            java.util.TreeSet<Integer> keys = new java.util.TreeSet<>();
            try (var in = HeadInAJar.class.getResourceAsStream(path)) {
                h.assertTrue(in != null, "no clip file " + path);
                var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var timeline = root.getAsJsonObject("animations").getAsJsonObject("build").getAsJsonObject("timeline");
                for (var e : timeline.entrySet()) {
                    if (e.getValue().getAsString().contains("build:seat:")) keys.add(Math.round(Float.parseFloat(e.getKey()) * 20));
                }
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
            java.util.TreeSet<Integer> ticks = new java.util.TreeSet<>();
            for (int t : rig.seatTicks) ticks.add(t);
            if (!keys.equals(ticks)) wrong.add(rig.id + " server " + ticks + " clip " + keys);
        }
        h.assertTrue(wrong.isEmpty(), "seat ticks that are not the clip's: " + wrong);
        h.succeed();
    }

    // --- a flier ------------------------------------------------------------------------------------------------

    /**
     * A bat is made hanging asleep, which pins it to its spot every tick; ridden by a jar it wakes on its own ridden
     * tick (the one the rider's client runs to fly it), so jump and sneak move it.
     */
    @GameTest(template = "platform", timeoutTicks = 80)
    public static void aRiddenBatWakesSoItCanFly(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var bat = host(h, EntityType.BAT, FLOOR.above(4), p);
        bat.setResting(true);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, bat), "could not board the bat");
        h.runAfterDelay(5, () -> {
            h.assertFalse(bat.isResting(), "a ridden bat still hangs asleep (pinned in place)");
            done(h, p);
        });
    }

    /**
     * The Lunk's toss, fired by the ability key's handler at a player: refused with the handling line while that player
     * has not trusted the rider (the scoop comes up empty); taken once they share a team, and once they trust the rider.
     */
    @GameTest(template = "platform", timeoutTicks = 160)
    public static void theLunkTossesAPlayerOnlyWithTheirTrust(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        ServerPlayer q = player(h, FLOOR.south(2));
        com.coolfreeze23.headinajar.entity.ShellEntity l = h.spawn(ModRegistry.LUNK.get(), FLOOR);
        l.setYRot(0);
        l.setYBodyRot(0);
        FormActions.detach(p, 3);
        p.interactOn(l, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == l && FormActions.state(p).form() == com.coolfreeze23.headinajar.form.Form.HOSTED, "the click did not board");
        FormActions.state(p).setCharge(FormActions.state(p).chargeCapacity());
        var board = h.getLevel().getScoreboard();
        long[] start = new long[1];
        Runnable press = () -> {
            com.coolfreeze23.headinajar.host.Hosts.data(l).primaryCooldown = 0;
            Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.south(2)));
            q.teleportTo(at.x, at.y, at.z);
            q.setDeltaMovement(Vec3.ZERO);
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, q.position().add(0, 1, 0));
            start[0] = h.getLevel().getGameTime();
            Network.action(p, new Network.JarAction(Network.Action.ABILITY));
        };
        h.runAfterDelay(2, () -> {
            press.run();
            h.assertValueEqual(Refusals.lastDelivered(p), "mecha.lift.untrusted", "the answer for a player who has not trusted the rider");
            h.assertTrue((l.tossAt() & 3) == com.coolfreeze23.headinajar.entity.ShellEntity.TOSS_REFUSED && l.tossAt() / 4 == start[0],
                    "the scoop was not refused: " + l.tossAt());
        });
        h.runAfterDelay(14, () -> h.assertTrue(l.tossHeldId() != q.getId() && q.getDeltaMovement().y < 0.3,
                "an untrusting player was picked up"));
        // teammates count as trusted
        h.runAfterDelay(40, () -> {
            var team = board.addPlayerTeam("lunk_crew");
            board.addPlayerToTeam(p.getScoreboardName(), team);
            board.addPlayerToTeam(q.getScoreboardName(), team);
            press.run();
            h.assertTrue((l.tossAt() & 3) == com.coolfreeze23.headinajar.entity.ShellEntity.TOSS && l.tossAt() / 4 == start[0],
                    "a teammate was not tossed: " + Refusals.delivered(p));
        });
        h.runAfterDelay(52, () -> h.assertValueEqual(l.tossHeldId(), q.getId(), "the teammate in the fists"));
        // and so does a player who trusts the rider
        h.runAfterDelay(100, () -> {
            board.removePlayerTeam(board.getPlayerTeam("lunk_crew"));
            HeadRegistry.get(h.getLevel().getServer()).setTrust(q.getUUID(), p.getUUID(), true);
            press.run();
            h.assertTrue((l.tossAt() & 3) == com.coolfreeze23.headinajar.entity.ShellEntity.TOSS && l.tossAt() / 4 == start[0],
                    "a trusting player was not tossed: " + Refusals.delivered(p));
        });
        h.runAfterDelay(112, () -> {
            h.assertValueEqual(l.tossHeldId(), q.getId(), "the trusting player in the fists");
            HeadRegistry.get(h.getLevel().getServer()).setTrust(q.getUUID(), p.getUUID(), false);
            done(h, p, q);
        });
    }

    /**
     * A vent's steam scalds whoever stands in it, whatever tick the vent began on: four vents brought on by the
     * machine's own tick at its heat limit, each starting one tick later in the four-tick steam beat, each hurt a player
     * beside it within its first second and a half.
     */
    @GameTest(template = "platform", timeoutTicks = 440)
    public static void ventSteamScaldsABystanderInEveryVent(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(4));
        ServerPlayer near = player(h, FLOOR.east(2));
        MechaEntity m = MechaLineGameTests.mecha(h, MechaFrame.HOPPER, FLOOR, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        FormActions.detach(p, 3);
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == m, "the click did not board");
        for (int i = 0; i < 4; i++) {
            int n = i;
            int begin = 5 + i * 111; // 111 = 3 (mod 4): each vent starts on another tick of the beat
            h.runAfterDelay(begin, () -> {
                h.assertValueEqual(m.vent(), 0, "still venting before vent " + n);
                near.setHealth(near.getMaxHealth());
                Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.east(2)));
                near.teleportTo(at.x, at.y, at.z);
                m.setHeat(m.frame().heatCap * 1.2f);
            });
            h.runAfterDelay(begin + 30, () -> {
                h.assertTrue(m.vent() > 0, "vent " + n + " did not start");
                h.assertTrue(near.getHealth() < near.getMaxHealth(), "vent " + n + "'s steam did not hurt the player beside it");
                if (n == 3) done(h, p, near);
            });
        }
    }
}
