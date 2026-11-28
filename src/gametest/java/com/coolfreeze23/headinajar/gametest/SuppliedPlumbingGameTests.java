package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.host.Unseating;
import com.coolfreeze23.headinajar.item.ClipboardItem;
import com.coolfreeze23.headinajar.item.JobCardItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import com.coolfreeze23.headinajar.supplier.Equipment;
import com.coolfreeze23.headinajar.supplier.SuppliedContainers;
import com.coolfreeze23.headinajar.supplier.Supplier;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * The plumbing with the supplier's own blocks and weapons (0.8.0): the copper chests and the shelf as containers staff
 * reach (a double copper chest as one), the Clipboard on them and on a statue, Hauling between them; the slot adapter
 * on a real harness; and the Unseating Policy with a real spear, through the game's own damage, knockback and dismount
 * (and one real charge). Registered only when every new arrival is (as {@link SuppliedGameTests} is), so a game without
 * the supplier runs exactly the old suite. {@code platform}: 15 x 6 x 15; {@code platform32}: 32 wide.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
public class SuppliedPlumbingGameTests {
    private static final BlockPos FLOOR = PlumbingGameTests.FLOOR;
    private static final BlockPos CLOCK = new BlockPos(16, 6, 22);

    @SubscribeEvent
    static void register(RegisterGameTestsEvent event) {
        if (Supplier.delivered().size() == Supplier.NEW_ARRIVALS.size()) event.register(SuppliedPlumbingGameTests.class);
    }

    static Block block(String id) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow(() -> new GameTestAssertException("not registered: " + id));
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow(() -> new GameTestAssertException("not registered: " + id));
    }

    static IItemHandler handler(GameTestHelper h, BlockPos rel) {
        return h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(rel), Direction.UP);
    }

    /** A zombie holding this, standing still, a few blocks off. */
    static Zombie holding(GameTestHelper h, Item weapon, BlockPos at) {
        Zombie z = h.spawn(EntityType.ZOMBIE, at);
        z.setNoAi(true);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        return z;
    }

    // --- 1.3 the containers ------------------------------------------------------------------------------------------

    /** Each of the eight copper chests and the shelf is a container to staff: what goes in is in the block. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void everyCopperChestAndTheShelfIsAContainer(GameTestHelper h) {
        h.assertValueEqual(SuppliedContainers.copperChests().size(), 8, "copper chests registered");
        for (int i = 0; i < SuppliedContainers.COPPER_CHESTS.size(); i++) {
            BlockPos at = new BlockPos(2 + i, 2, 3);
            h.setBlock(at, block(SuppliedContainers.COPPER_CHESTS.get(i)));
            IItemHandler items = handler(h, at);
            h.assertTrue(items != null && items.getSlots() == 27, SuppliedContainers.COPPER_CHESTS.get(i) + ": " + (items == null ? "no handler" : items.getSlots() + " slots"));
            h.assertTrue(items.insertItem(0, new ItemStack(Items.COBBLESTONE, 5), false).isEmpty(), "it took nothing");
            h.assertTrue(((Container) h.getBlockEntity(at)).getItem(0).is(Items.COBBLESTONE), "what went in is not in the chest");
        }
        BlockPos shelf = new BlockPos(4, 2, 8);
        h.setBlock(shelf, block("minecraft:oak_shelf"));
        IItemHandler items = handler(h, shelf);
        h.assertTrue(items != null && items.getSlots() == 3, "the shelf: " + (items == null ? "no handler" : items.getSlots() + " slots"));
        h.assertTrue(items.insertItem(1, new ItemStack(Items.BREAD, 3), false).isEmpty(), "the shelf took nothing");
        h.assertTrue(((Container) h.getBlockEntity(shelf)).getItem(1).is(Items.BREAD), "what went in is not on the shelf");
        h.assertTrue(items.extractItem(1, 3, false).getCount() == 3, "the shelf gave nothing back");
        h.succeed();
    }

    /**
     * A double copper chest reads as one, from either half. Two of different weathering set side by side become one
     * weathering (the supplier's own rule for a joined pair), and read as one too.
     */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void aDoubleCopperChestReadsAsOne(GameTestHelper h) {
        Block copper = block("minecraft:copper_chest"), exposed = block("minecraft:exposed_copper_chest");
        BlockPos a = new BlockPos(4, 2, 4), b = a.east();
        h.setBlock(a, copper.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
        h.setBlock(b, copper.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        h.assertTrue(handler(h, a).getSlots() == 54 && handler(h, b).getSlots() == 54, "a double copper chest: " + handler(h, a).getSlots() + ", " + handler(h, b).getSlots());
        handler(h, a).insertItem(40, new ItemStack(Items.APPLE), false);
        h.assertTrue(handler(h, b).getStackInSlot(40).is(Items.APPLE), "the halves are two containers");
        BlockPos c = new BlockPos(4, 2, 9), d = c.east();
        h.setBlock(c, copper.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
        h.setBlock(d, exposed.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        h.assertTrue(h.getBlockState(c).getBlock() == h.getBlockState(d).getBlock(), "a joined pair kept two weatherings");
        h.assertTrue(handler(h, c).getSlots() == 54 && handler(h, d).getSlots() == 54, "a joined pair: " + handler(h, c).getSlots() + ", " + handler(h, d).getSlots());
        h.succeed();
    }

    private static ServerPlayer boss(GameTestHelper h) {
        return TestPlayers.at(h, "Boss", new BlockPos(1, 6, 1));
    }

    private static TimeClockBlockEntity clock(GameTestHelper h, ServerPlayer owner) {
        h.setBlock(CLOCK, WorkRegistry.TIME_CLOCK.get());
        h.setBlock(CLOCK.below(), Blocks.REDSTONE_BLOCK);
        TimeClockBlockEntity c = (TimeClockBlockEntity) h.getBlockEntity(CLOCK);
        c.setOwner(owner);
        return c;
    }

    private static boolean mark(GameTestHelper h, ServerPlayer p, ItemStack board, BlockPos rel, boolean sneak) {
        BlockPos pos = h.absolutePos(rel);
        p.setShiftKeyDown(sneak);
        try {
            return ClipboardItem.mark(p, h.getLevel(), board, pos, Direction.UP, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        } finally {
            p.setShiftKeyDown(false);
        }
    }

    /** The Clipboard marks a copper chest and a shelf as stops, and refuses a copper golem statue. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform32")
    public static void theClipboardFilesACopperChestAndRefusesAStatue(GameTestHelper h) {
        ServerPlayer p = boss(h);
        ItemStack board = new ItemStack(WorkRegistry.CLIPBOARD.get());
        clock(h, p);
        mark(h, p, board, CLOCK, false);
        h.setBlock(new BlockPos(12, 6, 16), block("minecraft:weathered_copper_chest"));
        h.setBlock(new BlockPos(20, 6, 16), block("minecraft:spruce_shelf"));
        h.setBlock(new BlockPos(16, 6, 16), block("minecraft:copper_golem_statue"));
        h.assertTrue(mark(h, p, board, new BlockPos(12, 6, 16), false), "the copper chest was not marked: " + Refusals.lastDelivered(p));
        h.assertTrue(mark(h, p, board, new BlockPos(20, 6, 16), true), "the shelf was not marked: " + Refusals.lastDelivered(p));
        Order o = Order.of(board);
        h.assertTrue(o.pickUps().size() == 1 && o.dropOffs().size() == 1, "stops: " + o.stops());
        h.assertFalse(mark(h, p, board, new BlockPos(16, 6, 16), false), "the statue was marked");
        h.assertValueEqual(Refusals.lastDelivered(p), "clipboard.statue", "the statue's refusal");
        h.assertTrue(Order.of(board).corner().isEmpty() && Order.of(board).stops().size() == 2, "the statue was taken as a corner or a stop");
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** Hauling takes from a copper chest and stocks a shelf. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform32", timeoutTicks = 800)
    public static void haulingMovesFromACopperChestToAShelf(GameTestHelper h) {
        ServerPlayer p = boss(h);
        TimeClockBlockEntity clock = clock(h, p);
        BlockPos from = new BlockPos(11, 6, 16), to = new BlockPos(21, 6, 16);
        h.setBlock(from, block("minecraft:copper_chest"));
        h.setBlock(to, block("minecraft:birch_shelf"));
        Container chest = (Container) h.getBlockEntity(from), shelf = (Container) h.getBlockEntity(to);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        Villager v = h.spawn(EntityType.VILLAGER, new BlockPos(16, 6, 13));
        h.assertTrue(Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), v), "setup: villager not hired: " + Refusals.lastDelivered(p));
        clock.join(v);
        ItemStack card = new ItemStack(WorkRegistry.JOB_CARD.get());
        Order order = Order.EMPTY.withClock(clock.site()).withJob(WorkTable.HAULING).withStops(List.of(
                new Order.Stop(h.absolutePos(from), Direction.UP, false, ItemStack.EMPTY, false),
                new Order.Stop(h.absolutePos(to), Direction.UP, true, ItemStack.EMPTY, false)));
        card.set(WorkRegistry.JOB_CARD_DATA.get(), new Order.Card(order, "Boss", 0));
        h.assertTrue(JobCardItem.give(p, card, v), "setup: the card was refused: " + Refusals.lastDelivered(p));
        WorkerData d = Workforce.data(v);
        d.shift = WorkerData.Shift.REDSTONE;
        d.state = WorkerData.State.ON_JOB;
        h.succeedWhen(() -> {
            int on = 0;
            for (int i = 0; i < shelf.getContainerSize(); i++) if (shelf.getItem(i).is(Items.COBBLESTONE)) on += shelf.getItem(i).getCount();
            h.assertTrue(on == 32, "on the shelf: " + on);
            h.assertTrue(chest.getItem(0).isEmpty(), "left in the copper chest: " + chest.getItem(0));
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    // --- 1.4 the slot adapter on the supplier's creatures --------------------------------------------------------------

    /** A harness put on a happy ghast the game's way, and a swallowed block, are found wherever they are kept. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void theSlotAdapterFindsAHarnessAndASwallowedBlock(GameTestHelper h) {
        ServerPlayer p = PlumbingGameTests.player(h, FLOOR.west(4));
        Mob ghast = (Mob) h.spawn(SuppliedSpeciesGameTests.type("happy_ghast"), FLOOR);
        ghast.setNoAi(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("minecraft:white_harness")));
        p.interactOn(ghast, InteractionHand.MAIN_HAND);
        h.assertTrue(Equipment.harness(ghast).is(Equipment.HARNESSES), "the harness put on is not found");
        Mob cube = (Mob) h.spawn(SuppliedSpeciesGameTests.type("sulfur_cube"), FLOOR.east(4));
        cube.setNoAi(true);
        Item swallowable = BuiltInRegistries.ITEM.getTag(Equipment.SULFUR_CUBE_SWALLOWABLE).flatMap(t -> t.stream().findFirst())
                .orElseThrow(() -> new GameTestAssertException("nothing is swallowable")).value();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.BODY}) {
            cube.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            cube.setItemSlot(EquipmentSlot.BODY, ItemStack.EMPTY);
            cube.setItemSlot(slot, new ItemStack(swallowable));
            h.assertTrue(Equipment.swallowed(cube).is(swallowable), "the block in " + slot + " was not found");
        }
        PlumbingGameTests.done(h, p);
    }

    // --- 1.5 the Unseating Policy with a real spear --------------------------------------------------------------------

    /** Every spear is a lance; a spear in the hand that hit, or one being charged in the other hand, is the lance. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void theSpearsAreLances(GameTestHelper h) {
        for (String s : List.of("wooden", "stone", "copper", "iron", "golden", "diamond", "netherite")) {
            h.assertTrue(new ItemStack(item("minecraft:" + s + "_spear")).is(Equipment.SPEARS), s + " spear is not in the tag");
        }
        Zombie z = holding(h, item("minecraft:iron_spear"), FLOOR);
        h.assertTrue(Unseating.lanceOf(z.damageSources().mobAttack(z)).is(item("minecraft:iron_spear")), "the spear in the hand was not the lance");
        Zombie off = holding(h, Items.IRON_SWORD, FLOOR.east(3));
        off.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(item("minecraft:stone_spear")));
        off.startUsingItem(InteractionHand.OFF_HAND);
        h.assertTrue(off.isUsingItem(), "setup: the off-hand spear is not being charged");
        h.assertTrue(Unseating.lanceOf(off.damageSources().mobAttack(off)).is(item("minecraft:stone_spear")), "the spear being charged was not the lance");
        h.succeed();
    }

    /** A spear's hit, then the dismount in the same tick (as the spear does it): the jar is unseated, the pilot holds. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void aSpearHitUnseatsAHostedJarAndThePilotHolds(GameTestHelper h) {
        ServerPlayer p = PlumbingGameTests.player(h, FLOOR), q = PlumbingGameTests.player(h, FLOOR.south(4));
        Cow cow = PlumbingGameTests.hosted(h, p, EntityType.COW, FLOOR.east(2));
        MechaEntity m = PlumbingGameTests.mecha(h, q, MechaFrame.HOPPER, FLOOR.south(4), PlumbingGameTests.module(MechaModule.HYDRAULIC_LEGS));
        Zombie z = holding(h, item("minecraft:iron_spear"), FLOOR.west(3));
        FormActions.state(p).setIntegrity(5);
        p.hurt(z.damageSources().mobAttack(z), 1);
        p.stopRiding();
        h.assertTrue(p.getVehicle() == null, "the jar held on");
        h.assertValueEqual(Unseating.lastSaid(p), "unseated", "the jar's line");
        h.assertTrue(FormActions.state(p).integrity() < 5, "no knock");
        AdvancementHolder adv = h.getLevel().getServer().getAdvancements().get(HeadInAJar.id("relieved_of_your_post"));
        h.assertTrue(adv != null && p.getAdvancements().getOrStartProgress(adv).isDone(), "Relieved of Your Post: " + adv);
        q.hurt(z.damageSources().mobAttack(z), 1);
        q.stopRiding();
        h.assertTrue(q.getVehicle() == m && FormActions.state(q).form() == Form.PILOT, "the pilot was taken out");
        h.assertValueEqual(Unseating.lastSaid(q), "pilot", "the pilot's line");
        h.assertTrue(cow.isAlive(), "the cow");
        PlumbingGameTests.done(h, p, q);
    }

    /** A sword's hit is no lance: a dismount in the same tick is an ordinary one, as in 0.7.2. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void aSwordHitIsNoLance(GameTestHelper h) {
        ServerPlayer p = PlumbingGameTests.player(h, FLOOR);
        PlumbingGameTests.hosted(h, p, EntityType.COW, FLOOR.east(2));
        Zombie z = holding(h, Items.IRON_SWORD, FLOOR.west(3));
        p.hurt(z.damageSources().mobAttack(z), 1);
        p.stopRiding();
        h.assertTrue(p.getVehicle() == null && Unseating.lastSaid(p) == null, "a sword's hit was a lance's: " + Unseating.lastSaid(p));
        PlumbingGameTests.done(h, p);
    }

    /** An unseating hit that does no harm still knocks back first: with a spear being charged near, that is the lance. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void aKnockFromAChargingSpearIsTheLance(GameTestHelper h) {
        ServerPlayer p = PlumbingGameTests.player(h, FLOOR);
        PlumbingGameTests.hosted(h, p, EntityType.COW, FLOOR.east(2));
        Zombie z = holding(h, item("minecraft:iron_spear"), FLOOR.west(3));
        z.startUsingItem(InteractionHand.MAIN_HAND);
        h.assertTrue(z.isUsingItem(), "setup: the spear is not being charged");
        p.knockback(0.4, 0, 1);
        p.stopRiding();
        h.assertTrue(p.getVehicle() == null, "the jar held on");
        h.assertValueEqual(Unseating.lastSaid(p), "unseated", "the line");
        PlumbingGameTests.done(h, p);
    }

    /** A Socket's jar rides nothing: a charge at it gets the clamps' line. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void theClampsHoldAgainstACharge(GameTestHelper h) {
        ServerPlayer p = PlumbingGameTests.player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        var socket = (NeuralSocketBlockEntity) h.getBlockEntity(FLOOR);
        FormActions.detach(p, 3);
        h.assertTrue(socket.plugIn(p) && FormActions.state(p).form() == Form.SOCKET, "could not plug in");
        Zombie z = holding(h, item("minecraft:iron_spear"), FLOOR.west(3));
        z.startUsingItem(InteractionHand.MAIN_HAND);
        p.hurt(z.damageSources().mobAttack(z), 1);
        h.assertValueEqual(Unseating.lastSaid(p), "socket", "the clamps' line");
        h.assertValueEqual(FormActions.state(p).form(), Form.SOCKET, "the jar came off the socket");
        PlumbingGameTests.done(h, p);
    }

    /**
     * The charge does not carry a Socket's jar off the socket either (seen in the game: the clamps' line, then the jar
     * shoved off by the horse riding through it, and the socket let go): its knock is taken by the clamps, and a shove
     * while lanced puts the jar back on its seat. An ordinary knock, with no lance about, still moves it.
     */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void theClampsTakeTheChargesKnock(GameTestHelper h) {
        ServerPlayer p = PlumbingGameTests.player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        var socket = (NeuralSocketBlockEntity) h.getBlockEntity(FLOOR);
        FormActions.detach(p, 3);
        h.assertTrue(socket.plugIn(p) && FormActions.state(p).form() == Form.SOCKET, "could not plug in");
        Zombie z = holding(h, item("minecraft:iron_spear"), FLOOR.west(3));
        z.startUsingItem(InteractionHand.MAIN_HAND);
        p.setDeltaMovement(Vec3.ZERO);
        p.hurt(z.damageSources().mobAttack(z), 1);
        p.knockback(0.8, -1, 0);
        h.assertTrue(p.getDeltaMovement().lengthSqr() < 1.0E-6, "the charge's knock moved the jar: " + p.getDeltaMovement());
        Vec3 seat = Vec3.atBottomCenterOf(h.absolutePos(FLOOR).above());
        h.runAfterDelay(5, () -> {
            h.assertValueEqual(FormActions.state(p).form(), Form.SOCKET, "the jar came off the socket");
            // the horse rides through it: shoved most of a block off its seat
            p.setPos(seat.x + 0.8, seat.y, seat.z);
        });
        h.runAfterDelay(8, () -> {
            h.assertValueEqual(FormActions.state(p).form(), Form.SOCKET, "the shove took the jar off the socket");
            h.assertTrue(p.position().distanceTo(seat) < 0.05, "the jar was not put back on its seat: " + p.position());
            z.stopUsingItem();
            z.discard();
            p.setDeltaMovement(Vec3.ZERO);
            p.knockback(0.8, -1, 0);
            h.assertTrue(p.getDeltaMovement().lengthSqr() > 0.01, "an ordinary knock no longer moves a socket's jar");
            PlumbingGameTests.done(h, p);
        });
    }

    /**
     * The real thing: a zombie charging with an iron spear runs at a hosted jar. The spear's own code hits the jar and
     * calls the dismount; the policy unseats it beside the cow.
     */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform", timeoutTicks = 120)
    public static void aRealChargeUnseatsAJar(GameTestHelper h) {
        ServerPlayer p = PlumbingGameTests.player(h, new BlockPos(10, 2, 7));
        Cow cow = PlumbingGameTests.hosted(h, p, EntityType.COW, new BlockPos(10, 2, 7));
        Zombie z = holding(h, item("minecraft:iron_spear"), new BlockPos(2, 2, 7));
        z.setNoGravity(true);
        z.startUsingItem(InteractionHand.MAIN_HAND);
        h.onEachTick(() -> {
            if (!z.isAlive() || p.getVehicle() == null) return;
            Vec3 jar = p.getBoundingBox().getCenter(), eye = z.getEyePosition();
            double dx = jar.x - eye.x, dz = jar.z - eye.z, flat = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90), pitch = (float) -Math.toDegrees(Math.atan2(jar.y - eye.y, flat));
            z.setYRot(yaw);
            z.setYHeadRot(yaw);
            z.setXRot(pitch);
            // a steady run at it, never through it (a position change, not a teleport, so its speed is known)
            if (flat > 1.2) z.setPos(z.getX() + 0.3 * dx / flat, z.getY(), z.getZ() + 0.3 * dz / flat);
            if (!z.isUsingItem()) z.startUsingItem(InteractionHand.MAIN_HAND);
        });
        h.succeedWhen(() -> {
            h.assertTrue(p.getVehicle() == null, "still riding (zombie at " + z.position() + ", jar at " + p.position() + ")");
            h.assertValueEqual(Unseating.lastSaid(p), "unseated", "the line");
            h.assertTrue(cow.isAlive(), "the cow");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    /**
     * The charge that takes a jar off its body passes over the body. VB's charge hits everything on its line, the jar
     * and the cow under it, in either order: the cow while the jar still rides it, or after the same charge took the
     * jar off (a mounted charge reaches the body a tick or two later). Either way the cow, at the health the saw leaves a
     * host (a quarter), keeps every point; another creature's charge, or this one's after the pass, hurts it as any.
     * Before 0.8.0's client pass the same charge killed it.
     */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void theChargeThatUnseatsAJarSparesItsBody(GameTestHelper h) {
        ServerPlayer p = PlumbingGameTests.player(h, new BlockPos(10, 2, 7));
        Cow cow = PlumbingGameTests.hosted(h, p, EntityType.COW, new BlockPos(10, 2, 7));
        float health = cow.getHealth();
        h.assertTrue(health < cow.getMaxHealth() / 2, "the host was not at a sawn body's health: " + health);
        Zombie z = holding(h, item("minecraft:iron_spear"), new BlockPos(8, 2, 7));
        z.startUsingItem(InteractionHand.MAIN_HAND);
        h.assertTrue(Unseating.charging(z), "the zombie is not charging");
        // the cow first, its jar still on
        cow.hurt(z.damageSources().mobAttack(z), 5);
        h.assertValueEqual(cow.getHealth(), health, "the ridden cow's health after the charge");
        h.assertTrue(p.getVehicle() == cow, "the jar came off without a hit on it");
        // then the jar, and the cow again in the same tick
        p.hurt(z.damageSources().mobAttack(z), 1);
        p.stopRiding();
        h.assertTrue(p.getVehicle() == null, "the jar held");
        h.assertValueEqual(Unseating.lastSaid(p), "unseated", "the line");
        cow.hurt(z.damageSources().mobAttack(z), 5);
        h.assertTrue(cow.isAlive(), "the charge killed the cow its jar had just left");
        h.assertValueEqual(cow.getHealth(), health, "the cow's health in the tick its jar was taken");
        h.assertValueEqual(Unseating.spared(cow), h.getLevel().getGameTime(), "spared this tick");
        Zombie other = holding(h, item("minecraft:iron_spear"), new BlockPos(8, 2, 9));
        other.startUsingItem(InteractionHand.MAIN_HAND);
        h.runAfterDelay(2, () -> {
            // the same charge two ticks on: still its pass
            cow.invulnerableTime = 0;
            cow.hurt(z.damageSources().mobAttack(z), 5);
            h.assertValueEqual(cow.getHealth(), health, "the cow's health two ticks after its jar was taken");
            // another creature's charge: the cow has no jar on it, so it is hurt as any
            cow.invulnerableTime = 0;
            cow.hurt(other.damageSources().mobAttack(other), 1);
            h.assertTrue(cow.getHealth() < health, "another charge at a body with no jar did not hurt it");
        });
        h.runAfterDelay(2 + Unseating.PASS_TICKS + 1, () -> {
            float now = cow.getHealth();
            cow.invulnerableTime = 0;
            cow.hurt(z.damageSources().mobAttack(z), 0.5f);
            h.assertTrue(cow.getHealth() < now, "the first charge still passed over the body after its pass");
            PlumbingGameTests.done(h, p);
        });
    }
}
