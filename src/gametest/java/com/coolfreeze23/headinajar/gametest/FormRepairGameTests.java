package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity;
import com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Courier;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.item.QuantumItems;
import com.coolfreeze23.headinajar.menu.JarMenu;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The player's own forms and what carries them. Every test goes through what a real click, key press or tick does: the
 * interaction the client sends, a payload's handler, a menu button, the player tick event, the game's own damage.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class FormRepairGameTests {
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

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static JarState state(ServerPlayer p) {
        return FormActions.state(p);
    }

    private static void separate(GameTestHelper h, ServerPlayer p, BlockPos headAt, LifeSupportCradleBlockEntity cradle) {
        h.assertTrue(FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(headAt)), cradle), "separate failed");
    }

    private static void fit(ServerPlayer p, ModuleType... types) {
        for (int i = 0; i < types.length; i++) state(p).setModule(i, new ItemStack(ModRegistry.module(types[i])));
    }

    private static void key(ServerPlayer p, Network.Action action) {
        Network.action(p, new Network.JarAction(action));
    }

    /** One server tick of this player, as the game runs it (a test player is never ticked by its connection). */
    private static void tick(ServerPlayer p) {
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
    }

    private static InteractionResult useBlock(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        BlockPos abs = h.absolutePos(pos);
        return p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
    }

    private static void useItem(GameTestHelper h, ServerPlayer p, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        p.gameMode.useItem(p, h.getLevel(), stack, InteractionHand.MAIN_HAND);
    }

    private static HeadlessBody body(GameTestHelper h, ServerPlayer p) {
        return (HeadlessBody) h.getLevel().getEntity(state(p).body());
    }

    // --- Telekinesis: held things fall when let go -----------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void heldCowFallsWhenItsHolderLogsOut(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        fit(p, ModuleType.TELEKINESIS);
        place(h, p, FLOOR.east(3), 0, -15);
        var cow = h.spawn(EntityType.COW, FLOOR.east(3).south(3));
        key(p, Network.Action.ABILITY);
        h.assertTrue(state(p).heldEntity() == cow.getId(), "the cow was not grabbed: " + Refusals.delivered(p));
        h.assertFalse(cow.isNoGravity(), "the hold switched the saved gravity flag off");
        double floor = cow.getY();
        boolean[] online = {true};
        h.onEachTick(() -> {
            if (online[0]) tick(p);
        });
        h.runAfterDelay(15, () -> {
            h.assertTrue(cow.getY() > floor + 0.25, "the cow is not held up: " + (cow.getY() - floor));
            h.assertFalse(cow.isNoGravity(), "the saved gravity flag is off while held");
            online[0] = false;
            h.getLevel().getServer().getPlayerList().remove(p);
        });
        h.runAfterDelay(45, () -> {
            h.assertTrue(cow.getY() < floor + 0.05, "the cow kept floating after its holder left: " + (cow.getY() - floor));
            h.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void somethingLeftFloatingComesDown(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        fit(p, ModuleType.TELEKINESIS);
        place(h, p, FLOOR.east(3), 0, -15);
        var cow = h.spawn(EntityType.COW, FLOOR.east(3).south(3));
        cow.setNoGravity(true);
        key(p, Network.Action.ABILITY);
        h.assertTrue(state(p).heldEntity() == cow.getId(), "the cow was not grabbed");
        key(p, Network.Action.ABILITY);
        h.assertTrue(state(p).heldEntity() < 0, "still holding");
        h.assertFalse(cow.isNoGravity(), "a cow left floating was let go floating again");
        // a player left floating by the old hold lands when they log in
        ServerPlayer floated = player(h, FLOOR.west(2));
        floated.setNoGravity(true);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(floated));
        h.assertFalse(floated.isNoGravity(), "a floating player stayed floating after logging in");
        // a machine or a chest stays put, and says so
        h.setBlock(FLOOR.east(3).south(2), Blocks.CHEST);
        place(h, p, FLOOR.east(3), 0, 0);
        key(p, Network.Action.ABILITY);
        h.assertValueEqual(Refusals.lastDelivered(p), "tk.machine", "lifting a chest");
        done(h, p, floated);
    }

    // --- dying and the records ---------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theVoidTakesTheBodyNotTheHead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        separate(h, p, FLOOR.east(3), null);
        fit(p, ModuleType.MAGNET);
        p.setOnGround(true);
        tick(p);
        Vec3 ground = p.position();
        p.teleportTo(p.getX(), h.getLevel().getMinBuildHeight() - 70, p.getZ());
        p.hurt(p.damageSources().fellOutOfWorld(), 1000);
        h.assertTrue(p.isAlive(), "a body lost to the void was a real death");
        h.assertTrue(state(p).form() == Form.JAR, "form " + state(p).form());
        h.assertFalse(state(p).module(0).isEmpty(), "the jar shattered and lost its modules");
        h.assertTrue(p.getY() > h.getLevel().getMinBuildHeight(), "the head woke up in the void");
        HeadlessBody corpse = body(h, p);
        h.assertTrue(corpse != null && corpse.isCorpse() && corpse.position().distanceTo(ground) < 1, "no corpse where the body last stood");
        h.assertTrue(corpse.belongings().countItem(Items.DIAMOND) == 3, "the corpse lacks the diamonds");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void killedHeadAndBodyLeaveNoRecord(GameTestHelper h) {
        // a placed head removed by /kill is lost: a real death for its linked owner
        ServerPlayer p = player(h, FLOOR);
        separate(h, p, FLOOR.east(2), null);
        HeadJarEntity head = h.getEntities(ModRegistry.HEAD_JAR.get()).get(0);
        head.kill();
        h.assertTrue(p.isDeadOrDying(), "the owner lived on without a head");
        // an idle body removed by /kill: the jar is told there is no body, and goes nowhere
        ServerPlayer q = player(h, FLOOR.west(3));
        FormActions.detach(q, 3);
        state(q).setCollar(new ItemStack(ModRegistry.COLLAR_CAM.get()));
        Vec3 here = q.position();
        body(h, q).kill();
        h.assertTrue(HeadRegistry.get(q).body(q.getUUID()) == null, "the registry kept a killed body");
        key(q, Network.Action.SWAP);
        h.assertValueEqual(Refusals.lastDelivered(q), "swap.no_body", "swap into a killed body");
        h.assertTrue(q.position().distanceTo(here) < 0.01 && state(q).form() == Form.JAR, "the jar went to a body that is gone");
        done(h, p, q);
    }

    @GameTest(template = "platform")
    public static void dryingOutLeavesNoNextDeathFlag(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        separate(h, p, FLOOR.east(3), null);
        p.setGameMode(GameType.CREATIVE);
        state(p).setSaline(0);
        state(p).setIntegrity(1);
        tick(p);
        h.assertTrue(state(p).integrity() == 0 && p.isAlive(), "creative mode did not hold");
        h.assertFalse(FormActions.REAL_DEATH.contains(p.getUUID()), "a real-death flag was left behind");
        p.setGameMode(GameType.SURVIVAL);
        p.hurt(p.damageSources().generic(), 1000);
        h.assertTrue(p.isAlive() && state(p).form() == Form.JAR, "an ordinary body death was taken as a real one");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void bareJarSewsItselfBackFromItsEffects(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.getInventory().add(new ItemStack(Items.DIAMOND, 2));
        separate(h, p, FLOOR.east(3), null);
        p.hurt(p.damageSources().generic(), 1000);
        HeadlessBody corpse = body(h, p);
        h.assertTrue(corpse != null && corpse.isCorpse(), "no corpse");
        place(h, p, FLOOR.south(1), 180, 0);
        // no kit anywhere: the effects open, and the jar is told what it needs
        p.interactOn(corpse, InteractionHand.MAIN_HAND);
        h.assertTrue(p.containerMenu != p.inventoryMenu, "the corpse's effects did not open");
        h.assertValueEqual(Refusals.lastDelivered(p), "suture.no_kit", "no kit line");
        h.assertTrue(p.containerMenu.stillValid(p), "the view closed with the corpse still there");
        p.closeContainer();
        // a kit among the effects: the head goes back on, hands or no hands
        corpse.belongings().setItem(20, new ItemStack(ModRegistry.SUTURE_KIT.get()));
        p.interactOn(corpse, InteractionHand.MAIN_HAND);
        h.assertTrue(state(p).form() == Form.BODY, "not sewn back on: " + state(p).form());
        h.assertTrue(p.getInventory().contains(new ItemStack(Items.DIAMOND)), "the effects did not come back");
        h.assertFalse(p.getInventory().contains(new ItemStack(ModRegistry.SUTURE_KIT.get())), "the kit was not used");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void corpseViewClosesWhenTheCorpseGoes(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        separate(h, p, FLOOR.east(3), null);
        p.hurt(p.damageSources().generic(), 1000);
        HeadlessBody corpse = body(h, p);
        place(h, p, FLOOR.south(1), 180, 0);
        p.interactOn(corpse, InteractionHand.MAIN_HAND);
        h.assertTrue(p.containerMenu != p.inventoryMenu, "the corpse's effects did not open");
        corpse.discard();
        h.assertFalse(p.containerMenu.stillValid(p), "the view stayed usable after the corpse was gone");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void recalledBodyStopsAtLava(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        HeadlessBody body = body(h, p);
        fit(p, ModuleType.BODY_RECALL_BEACON);
        place(h, p, FLOOR.east(7), 0, 0);
        for (int i = 2; i <= 4; i++) h.setBlock(FLOOR.east(i).below(), Blocks.LAVA);
        h.runAfterDelay(80, () -> {
            h.assertTrue(body.isAlive() && !body.isCorpse(), "the body died on the way");
            h.assertFalse(body.isInLava() || body.isOnFire(), "the body walked into the lava");
            h.assertTrue(body.getX() < h.absoluteVec(Vec3.atLowerCornerOf(FLOOR.east(2))).x, "the body is over the lava at " + body.getX());
            h.assertTrue(body.getX() > h.absoluteVec(Vec3.atCenterOf(FLOOR)).x + 0.3, "the body did not walk toward its head at all");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void printedBodyIsAdoptedWhateverTheForm(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.north(3));
        FormActions.detach(p, 3);
        HeadlessBody old = body(h, p);
        old.hurt(h.getLevel().damageSources().generic(), 100);
        h.setBlock(FLOOR, ModRegistry.BODY_PRINTER.get());
        var printer = (BodyPrinterBlockEntity) h.getBlockEntity(FLOOR);
        net.minecraft.world.item.Item[] items = {Items.ROTTEN_FLESH, Items.BONE_BLOCK, Items.REDSTONE_BLOCK, Items.SLIME_BALL};
        for (int i = 0; i < 4; i++) printer.items().setStackInSlot(i, new ItemStack(items[i], BodyPrinterBlockEntity.NEED[i]));
        printer.saline().fill(new FluidStack(ModFluids.SALINE.source.get(), 2000), IFluidHandler.FluidAction.EXECUTE);
        printer.energy().set(250_000);
        // the Print button
        h.assertTrue(printer.createMenu(1, p.getInventory(), p).clickMenuButton(p, 0), "the printer refused a jar");
        // skip to the last seconds of the print
        var registries = h.getLevel().registryAccess();
        var tag = printer.saveWithoutMetadata(registries);
        tag.putInt("Progress", BodyPrinterBlockEntity.TICKS - 3);
        printer.loadWithComponents(tag, registries);
        // meanwhile the head plugged itself into something
        state(p).setForm(Form.SOCKET);
        h.runAfterDelay(10, () -> {
            HeadlessBody fresh = body(h, p);
            h.assertTrue(fresh != null && fresh != old && !fresh.isCorpse(), "the printed body was not taken on");
            // and a body printed while its owner was away is taken on when they are back
            state(p).setBody(null);
        });
        h.runAfterDelay(60, () -> {
            HeadlessBody fresh = body(h, p);
            h.assertTrue(fresh != null && fresh.isAlive() && !fresh.isCorpse(), "the printed body gave out");
            state(p).setForm(Form.JAR);
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void courierRefundsWhenTheBodyIsGone(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        JarMenu menu = new JarMenu(1, p.getInventory());
        menu.clickMenuButton(p, JarMenu.BUTTON_COURIER);
        h.assertValueEqual(Refusals.lastDelivered(p), "courier.already_here", "booking for a body right here");
        place(h, p, FLOOR.east(12), 0, 0);
        int before = state(p).charge();
        menu.clickMenuButton(p, JarMenu.BUTTON_COURIER);
        h.assertTrue(state(p).charge() < before, "no fee taken");
        body(h, p).kill();
        p.getPersistentData().putInt(Courier.KEY, 1);
        Courier.tick(p);
        h.assertTrue(state(p).charge() == before, "the fee was kept: " + state(p).charge() + " of " + before);
        h.assertTrue(p.getPersistentData().getInt(Courier.KEY) <= 0, "still on its way");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 1500)
    public static void courierFetchesABodyFromAnUnloadedChunk(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        ServerLevel level = h.getLevel();
        body(h, p).discard();
        // a body far away, in a chunk nobody keeps loaded
        int x = (int) p.getX() + 3000, z = (int) p.getZ();
        level.getChunk(new BlockPos(x, 0, z));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        boolean[] seenGone = {false};
        HeadlessBody far = new HeadlessBody(ModRegistry.HEADLESS_BODY.get(), level);
        far.moveTo(x + 0.5, y, z + 0.5, 0, 0);
        far.setOwner(p.getUUID(), p.getGameProfile().getName());
        far.setCollar(true);
        level.addFreshEntity(far);
        UUID id = far.getUUID();
        state(p).setBody(id);
        HeadRegistry.get(p).setBody(p.getUUID(), new HeadRegistry.Body(id, HeadRegistry.Spot.of(far), false));
        boolean[] booked = {false};
        // the player tick's countdown, once booked (registered up front: a test can't add tick tasks while they run). A
        // test server ticks as fast as it can, so a tick here is far shorter than the disk takes to load the body's
        // entities; the countdown waits for them, as thirty real seconds would
        h.onEachTick(() -> {
            if (booked[0] && level.getEntity(id) != null) Courier.tick(p);
        });
        h.runAfterDelay(100, () -> {
            new JarMenu(1, p.getInventory()).clickMenuButton(p, JarMenu.BUTTON_COURIER);
            h.assertTrue(p.getPersistentData().getInt(Courier.KEY) > 0, "not booked: " + Refusals.delivered(p));
            p.getPersistentData().putInt(Courier.KEY, 100);
            booked[0] = true;
        });
        h.succeedWhen(() -> {
            if (!booked[0] && level.getEntity(id) == null) seenGone[0] = true;
            var rec = HeadRegistry.get(p).body(p.getUUID());
            h.assertTrue(level.getEntity(id) instanceof HeadlessBody b && b.distanceTo(p) < Courier.NEARBY, "not delivered yet: unloaded " + seenGone[0]
                    + " key " + p.getPersistentData().getInt(Courier.KEY) + " record " + (rec == null ? null : rec.spot().pos()) + " loaded " + (level.getEntity(id) != null)
                    + " y " + y + " lines " + Refusals.delivered(p));
            org.slf4j.LoggerFactory.getLogger("FormRepairGameTests").info("courier test: the far body was unloaded before booking: {}", seenGone[0]);
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    // --- the Cradle and the Entangled Pair --------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void swappingStraightBackLeavesTheHeadDocked(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        h.setBlock(FLOOR.east(2), ModRegistry.CRADLE.get());
        var cradle = (LifeSupportCradleBlockEntity) h.getBlockEntity(FLOOR.east(2));
        separate(h, p, FLOOR, cradle);
        key(p, Network.Action.SWAP);
        h.assertTrue(state(p).form() == Form.JAR, "not in the head");
        key(p, Network.Action.SWAP);
        h.assertTrue(state(p).form() == Form.LINKED, "not back in the body: " + Refusals.delivered(p));
        h.assertTrue(p.getUUID().equals(cradle.owner()), "the head is not docked");
        HeadRegistry.Head head = HeadRegistry.get(p).head(p.getUUID());
        h.assertTrue(head != null && head.where() == HeadRegistry.Where.CRADLE, "the registry says " + (head == null ? null : head.where()));
        h.assertTrue(h.getEntities(ModRegistry.HEAD_JAR.get()).isEmpty(), "a loose head was left on the floor");
        // breaking the Cradle while its head is the player leaves no ghost behind
        key(p, Network.Action.SWAP);
        h.destroyBlock(FLOOR.east(2));
        h.assertTrue(h.getEntities(ModRegistry.HEAD_JAR.get()).isEmpty(), "a ghost head was set down");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void entangledPairSwapsAcrossDimensions(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        separate(h, p, FLOOR.east(2), null);
        UUID pair = UUID.randomUUID();
        ItemStack goggles = state(p).goggles().copy(), collar = state(p).collar().copy();
        QuantumItems.entangle(goggles, pair);
        QuantumItems.entangle(collar, pair);
        state(p).setGoggles(goggles);
        state(p).setCollar(collar);
        // an entangled body may take a portal
        h.assertFalse(NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(p, Level.NETHER)).isCanceled(), "an entangled body was stopped at a portal");
        key(p, Network.Action.SWAP);
        h.assertTrue(state(p).form() == Form.JAR, "not in the head");
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        p.teleportTo(nether, 0.5, 70, 0.5, 0, 0);
        h.assertTrue(p.level() == nether, "the jar did not reach the Nether");
        key(p, Network.Action.SWAP);
        h.assertTrue(state(p).form() == Form.LINKED, "no link across dimensions: " + Refusals.delivered(p));
        h.assertTrue(p.level() == h.getLevel(), "linked but still in the Nether");
        tick(p);
        h.assertTrue(state(p).form() == Form.LINKED, "the link broke across dimensions");
        key(p, Network.Action.SWAP);
        h.assertTrue(state(p).form() == Form.JAR && p.level() == nether, "did not wake in the head in the Nether");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void portalNamesTheSatchel(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        separate(h, p, FLOOR.east(1), null);
        h.assertTrue(NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(p, Level.NETHER)).isCanceled(), "portal not blocked");
        h.assertValueEqual(Refusals.lastDelivered(p), "portal.head_behind", "portal line");
        tick(p);
        h.assertTrue(state(p).headAt() == JarState.HEAD_PLACED, "the HUD is not told the head is placed");
        done(h, p);
    }

    // --- what a bare jar can reach -------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void bareJarReachesTheDockTheCradleAndABoat(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        h.setBlock(FLOOR.east(2), ModRegistry.CHARGING_DOCK.get());
        h.assertTrue(useBlock(h, p, FLOOR.east(2)).consumesAction(), "the Charging Dock did not answer a jar");
        ServerPlayer whole = player(h, FLOOR.west(3));
        h.assertTrue(useBlock(h, whole, FLOOR.east(2)).consumesAction(), "the Charging Dock did not answer a whole player");
        // an empty Cradle: the jar climbs in
        h.setBlock(FLOOR.north(2), ModRegistry.CRADLE.get());
        useBlock(h, p, FLOOR.north(2));
        // the jar stands on the armchair's seat cushion, in the Cradle's own cell
        h.assertTrue(p.blockPosition().equals(h.absolutePos(FLOOR.north(2))), "the jar did not climb into the Cradle: " + p.blockPosition());
        h.assertTrue(LifeSupportCradleBlockEntity.under(p) != null, "the jar is not in the Cradle");
        // a boat and a minecart need no hands
        place(h, p, FLOOR.south(2), 0, 0);
        var boat = h.spawn(EntityType.BOAT, FLOOR.south(4));
        p.interactOn(boat, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == boat, "a jar could not board a boat");
        p.stopRiding();
        var cart = h.spawn(EntityType.MINECART, FLOOR.west(5));
        place(h, p, FLOOR.west(4), 90, 0);
        p.interactOn(cart, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == cart, "a jar could not board a minecart");
        p.stopRiding();
        // other creatures stay out of reach
        var cow = h.spawn(EntityType.COW, FLOOR.south(5));
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == null, "a bare jar rode a cow");
        done(h, p, whole);
    }

    // --- every silent input answers -------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void othersHeadsAndBodiesSayWhose(GameTestHelper h) {
        ServerPlayer owner = player(h, FLOOR);
        separate(h, owner, FLOOR.east(2), null);
        ServerPlayer stranger = player(h, FLOOR.east(4));
        HeadJarEntity head = h.getEntities(ModRegistry.HEAD_JAR.get()).get(0);
        stranger.interactOn(head, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(stranger), "head.not_yours", "someone else's head");
        // a friend may top it up
        HeadRegistry.get(owner).setTrust(owner.getUUID(), stranger.getUUID(), true);
        state(owner).setSaline(100);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SALINE_BOTTLE.get()));
        stranger.interactOn(head, InteractionHand.MAIN_HAND);
        h.assertTrue(state(owner).saline() == 350, "a trusted friend could not refill the head: " + state(owner).saline());
        // the owner with a full jar keeps the bottle, and the head stays put
        state(owner).setSaline(state(owner).maxSaline());
        owner.getInventory().add(new ItemStack(ModRegistry.HEAD_SATCHEL.get()));
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SALINE_BOTTLE.get()));
        owner.interactOn(head, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(owner), "head.full", "refilling a full jar");
        h.assertFalse(state(owner).carryingHead(), "a full refill picked the head up");

        ServerPlayer other = player(h, FLOOR.west(3));
        FormActions.detach(other, 3);
        HeadlessBody body = body(h, other);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        stranger.interactOn(body, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(stranger), "body.not_yours", "someone else's body");
        done(h, owner, stranger, other);
    }

    @GameTest(template = "platform")
    public static void satchelAndRefillsAnswer(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.getInventory().add(new ItemStack(ModRegistry.HEAD_SATCHEL.get()));
        separate(h, p, FLOOR.east(1), null);
        useItem(h, p, new ItemStack(ModRegistry.HEAD_SATCHEL.get()));
        h.assertValueEqual(Refusals.lastDelivered(p), "satchel.empty", "satchel with no head in it");
        FormActions.pickUpHead(p, h.getEntities(ModRegistry.HEAD_JAR.get()).get(0));
        useItem(h, p, new ItemStack(ModRegistry.HEAD_SATCHEL.get()));
        h.assertValueEqual(Refusals.lastDelivered(p), "satchel.sneak", "satchel used without sneaking");

        // a bare jar can't pour: it is pointed to the feed slot; with an arm it pours
        ServerPlayer jar = player(h, FLOOR.west(3));
        FormActions.detach(jar, 3);
        state(jar).setSaline(100);
        useItem(h, jar, new ItemStack(ModRegistry.SALINE_BOTTLE.get()));
        h.assertValueEqual(Refusals.lastDelivered(jar), "jar.refill_feed", "a bare jar pouring saline");
        fit(jar, ModuleType.MANIPULATOR_ARM);
        useItem(h, jar, new ItemStack(ModRegistry.SALINE_BOTTLE.get()));
        h.assertTrue(state(jar).saline() == 350, "a jar with an arm could not pour saline: " + state(jar).saline());
        useItem(h, jar, new ItemStack(ModRegistry.GLASS_PATCH.get()));
        h.assertValueEqual(Refusals.lastDelivered(jar), "head.intact", "patching sound glass");
        done(h, p, jar);
    }

    @GameTest(template = "platform")
    public static void cradleSaysWhatItNeeds(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        h.setBlock(FLOOR.east(2), ModRegistry.CRADLE.get());
        var cradle = (LifeSupportCradleBlockEntity) h.getBlockEntity(FLOOR.east(2));
        ServerPlayer visitor = player(h, FLOOR.west(2));
        useBlock(h, visitor, FLOOR.east(2));
        h.assertValueEqual(Refusals.lastDelivered(visitor), "cradle.empty", "an empty Cradle");
        separate(h, p, FLOOR, cradle);
        useBlock(h, p, FLOOR.east(2));
        h.assertValueEqual(Refusals.lastDelivered(p), "cradle.need_satchel", "own head docked, no satchel");
        cradle.saline().fill(new FluidStack(ModFluids.SALINE.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
        visitor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SALINE_BOTTLE.get()));
        useBlock(h, visitor, FLOOR.east(2));
        h.assertValueEqual(Refusals.lastDelivered(visitor), "cradle.full", "a full Cradle");
        visitor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        useBlock(h, visitor, FLOOR.east(2));
        h.assertValueEqual(Refusals.lastDelivered(visitor), "cradle.wrong_fluid", "water in the Cradle");
        h.assertTrue(visitor.getMainHandItem().is(Items.WATER_BUCKET), "the water bucket was emptied");
        done(h, p, visitor);
    }

    @GameTest(template = "platform")
    public static void keysWithNothingBehindThemAnswer(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        separate(h, p, FLOOR.east(2), null);
        FormEvents.lockedOut(p);
        // the client asks for these: the payload's handler
        for (String reason : new String[]{"jar.menu_linked", "drive.linked", "swap.riding", "jar.refill_feed"}) {
            Refusals.fromClient(p, reason, -1);
            h.assertValueEqual(Refusals.lastDelivered(p), reason, "client reason " + reason);
        }
        done(h, p);
    }
}
