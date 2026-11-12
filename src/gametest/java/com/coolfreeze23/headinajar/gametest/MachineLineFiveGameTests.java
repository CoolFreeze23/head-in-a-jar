package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.JarredHeadBlock;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.block.LeechJarBlock;
import com.coolfreeze23.headinajar.block.LeechJarBlockEntity;
import com.coolfreeze23.headinajar.block.LeydenJarBlock;
import com.coolfreeze23.headinajar.block.MachineBlock;
import com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity;
import com.coolfreeze23.headinajar.block.PowerBlockEntities;
import com.coolfreeze23.headinajar.block.SafetyPlacardBlock;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.machine.MachineTopBlock;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fifth batch on the machine kit: the Leyden Jar (two cells, charge cells and lamp from what it holds, an old
 * placement), the Neural Socket (a jar plugging in by a click shuts the clamps; the bars follow the keys' handler; a
 * refusal rattles it; a head's program is reckoned by the clients), the Leech Jar (it wakes for a creature beside it,
 * samples as before, shows its vials, sulks full and is emptied by a click), the Jarred Head (an old placement is the
 * same head, its shape is the jar's) and the Safety Placard (a click still cycles it). Clicks go through the game's
 * own use path.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class MachineLineFiveGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(v.x, v.y, v.z, 0, 0);
        return player;
    }

    private static void leave(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
    }

    /** A right-click on a face of this block with what is in the main hand, as the client sends it. */
    private static void use(GameTestHelper h, ServerPlayer p, BlockPos pos, Direction face) {
        BlockPos abs = h.absolutePos(pos);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)), face, abs, false));
    }

    private static boolean isTop(GameTestHelper h, BlockPos pos) {
        return h.getBlockState(pos).is(ModRegistry.MACHINE_TOP.get());
    }

    private static MachineLamp lamp(GameTestHelper h, BlockPos pos) {
        return h.getBlockState(pos).getValue(MachineLamp.LAMP);
    }

    private static ItemStack jarredHead(GameTestHelper h, EntityType<? extends Mob> type, VillagerProfession job) {
        Mob mob = h.spawn(type, FLOOR.above(4));
        if (mob instanceof Villager v && job != null) v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(1));
        if (mob instanceof Villager v) v.getOffers();
        ItemStack stack = JarredHeadItem.of(mob);
        mob.discard();
        return stack;
    }

    // --- Leyden Jar --------------------------------------------------------------------------------------------

    /** Placed by a click it takes its top cell; a cable filling it lights its lamp and steps its cells; full shows four. */
    @GameTest(template = "platform", batch = "machine_line5", timeoutTicks = 200)
    public static void leydenShowsItsChargeAndCharging(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.LEYDEN_JAR_ITEM.get()));
        use(h, p, FLOOR.below(), Direction.UP);
        h.assertTrue(h.getBlockState(FLOOR).is(ModRegistry.LEYDEN_JAR.get()), "the jar was not placed");
        h.assertTrue(isTop(h, FLOOR.above()), "the jar has no top cell");
        h.assertValueEqual(h.getBlockState(FLOOR.above()).getValue(MachineTopBlock.MACHINE), MachineTopBlock.Kind.LEYDEN_JAR, "the top cell's kind");
        // a cable pushing into the upper half reaches the battery below
        var cable = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(FLOOR.above()), Direction.UP);
        h.assertTrue(cable != null, "the top cell does not take power");
        var jar = (PowerBlockEntities.LeydenJar) h.getBlockEntity(FLOOR);
        // every step scheduled here, not from inside another (the test's own schedule can't take new entries while it runs)
        h.runAfterDelay(30, () -> {
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.OFF, "an empty jar's lamp");
            h.assertValueEqual(h.getBlockState(FLOOR).getValue(LeydenJarBlock.LEVEL), 0, "an empty jar's cells");
        });
        // charging: 2,000 FE a tick through the cable for 40 ticks (80 kFE, a third of it)
        for (int i = 31; i <= 70; i++) h.runAfterDelay(i, () -> cable.receiveEnergy(2_000, false));
        h.runAfterDelay(75, () -> {
            h.assertTrue(jar.energy().getEnergyStored() == 80_000, "the cable did not fill it: " + jar.energy().getEnergyStored());
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.WORK, "a charging jar's lamp");
            h.assertValueEqual(h.getBlockState(FLOOR).getValue(LeydenJarBlock.LEVEL), 2, "a third charged");
            h.assertValueEqual(h.getBlockState(FLOOR.above()).getValue(MachineTopBlock.LAMP), MachineLamp.WORK, "the lamp above the cell line");
            jar.energy().set(250_000);
        });
        h.runAfterDelay(105, () -> {
            h.assertValueEqual(h.getBlockState(FLOOR).getValue(LeydenJarBlock.LEVEL), 4, "a full jar");
            // the knob's spark is drawn by the top cell, which carries the level
            h.assertValueEqual(h.getBlockState(FLOOR.above()).getValue(MachineTopBlock.SALINE), 4, "the top cell's level");
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.READY, "a full, idle jar's lamp");
            leave(h, p);
            h.succeed();
        });
    }

    /** The cells step up only past their line and back only below it: a charge sitting on a line does not flicker. */
    @GameTest(template = "platform", batch = "machine_line5")
    public static void leydenCellsHoldOnTheLine(GameTestHelper h) {
        int cap = 250_000;
        h.assertValueEqual(PowerBlockEntities.LeydenJar.level(0, cap, 0), 0, "empty");
        h.assertValueEqual(PowerBlockEntities.LeydenJar.level(1, cap, 0), 1, "any charge lights the first cell");
        // the second cell's line is a quarter: just past it is not enough, 3 % past is
        h.assertValueEqual(PowerBlockEntities.LeydenJar.level(cap / 4 + 100, cap, 1), 1, "just past the line");
        h.assertValueEqual(PowerBlockEntities.LeydenJar.level(cap / 4 + cap * 4 / 100, cap, 1), 2, "3 % past the line");
        // and down: just under the line keeps two cells, 3 % under drops one
        h.assertValueEqual(PowerBlockEntities.LeydenJar.level(cap / 4 - 100, cap, 2), 2, "just under the line");
        h.assertValueEqual(PowerBlockEntities.LeydenJar.level(cap / 4 - cap * 4 / 100, cap, 2), 1, "3 % under the line");
        h.assertValueEqual(PowerBlockEntities.LeydenJar.level(0, cap, 3), 0, "drained");
        h.succeed();
    }

    /** A jar placed before (no facing, lamp or level; no top cell) keeps its charge, gets its top cell and feeds as before. */
    @GameTest(template = "platform", batch = "machine_line5", timeoutTicks = 60)
    public static void anOldLeydenJarLoadsAndFeeds(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.LEYDEN_JAR.get().defaultBlockState());
        var jar = (PowerBlockEntities.LeydenJar) h.getBlockEntity(FLOOR);
        CompoundTag old = new CompoundTag();
        old.putString("id", "headinajar:leyden_jar");
        old.putInt("Energy", 120_000);
        jar.loadWithComponents(old, h.getLevel().registryAccess());
        h.assertTrue(jar.energy().getEnergyStored() == 120_000, "the old charge was lost");
        // a neighbour that takes power: the old rule (2,000 FE a tick into any non-Leyden neighbour)
        h.setBlock(FLOOR.east(), ModRegistry.CHARGING_DOCK.get());
        h.runAfterDelay(20, () -> {
            h.assertTrue(isTop(h, FLOOR.above()), "an old jar did not get its top cell");
            h.assertValueEqual(h.getBlockState(FLOOR).getValue(LeydenJarBlock.LEVEL), 2, "the old charge's cells");
            h.assertTrue(jar.energy().getEnergyStored() < 120_000, "it no longer feeds its neighbour");
            h.succeed();
        });
    }

    // --- Neural Socket -------------------------------------------------------------------------------------------

    /**
     * A bare jar's click plugs it in: the clamps' bit and the lamp; the keys' handler sets a face and the bar follows
     * (in the update tag); another player's click is refused and the socket rattles with its lamp red.
     */
    @GameTest(template = "platform", batch = "machine_line5", timeoutTicks = 120)
    public static void aJarPlugsInAndTheBarsFollowItsKeys(GameTestHelper h) {
        ServerPlayer jar = player(h, FLOOR.east(1)), other = player(h, FLOOR.west(2));
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        var socket = (NeuralSocketBlockEntity) h.getBlockEntity(FLOOR);
        FormActions.detach(jar, 3);
        jar.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(h, jar, FLOOR, Direction.UP);
        h.assertTrue(FormActions.state(jar).form() == Form.SOCKET, "a bare jar's click did not plug it in");
        h.runAfterDelay(20, () -> {
            CompoundTag tag = socket.getUpdateTag(h.getLevel().registryAccess());
            h.assertTrue(tag.getBoolean("occ"), "the clients are not told the socket is occupied: " + tag);
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.READY, "a plugged-in socket with every face at 0");
            // the level key's payload handler: north face to 9
            NeuralSocketBlockEntity.input(jar, 0, Direction.NORTH.get3DDataValue(), 9);
            h.runAfterDelay(20, () -> {
                CompoundTag lit = socket.getUpdateTag(h.getLevel().registryAccess());
                h.assertValueEqual((lit.getInt("lv") >>> (4 * Direction.NORTH.get3DDataValue())) & 15, 9, "the north bar in the update tag");
                h.assertValueEqual(lamp(h, FLOOR), MachineLamp.WORK, "a socket giving a signal");
                // someone else's click: refused, and the socket shows it
                use(h, other, FLOOR, Direction.UP);
                h.assertValueEqual(Refusals.lastDelivered(other), "socket.occupied", "another player's click");
                h.assertValueEqual(lamp(h, FLOOR), MachineLamp.FAULT, "the refusal's red lamp");
                leave(h, jar, other);
                h.succeed();
            });
        });
    }

    /** A zombie's head runs its clock: the clients are told the program, not each flip, so the socket sends nothing more. */
    @GameTest(template = "platform", batch = "machine_line5", timeoutTicks = 120)
    public static void aHeadsProgramIsReckonedByTheClients(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        var socket = (NeuralSocketBlockEntity) h.getBlockEntity(FLOOR);
        p.setItemInHand(InteractionHand.MAIN_HAND, jarredHead(h, EntityType.ZOMBIE, null));
        use(h, p, FLOOR, Direction.UP);
        h.assertTrue(!socket.head().isEmpty(), "the head did not go in by a click");
        h.runAfterDelay(30, () -> {
            CompoundTag tag = socket.getUpdateTag(h.getLevel().registryAccess());
            h.assertValueEqual((int) tag.getByte("prog"), NeuralSocketBlockEntity.PROG_CLOCK, "the clock program");
            h.assertFalse(tag.contains("lv"), "the flips are sent as levels");
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.WORK, "a running program's lamp");
            int sends = socket.kit().sends();
            h.runAfterDelay(40, () -> {
                h.assertValueEqual(socket.kit().sends(), sends, "update tags sent while the clock ran");
                leave(h, p);
                h.succeed();
            });
        });
    }

    /** An old socket (no lamp; levels and a head in its save) loads with its head, its levels and its job. */
    @GameTest(template = "platform", batch = "machine_line5", timeoutTicks = 40)
    public static void anOldSocketLoadsWithItsHead(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        var socket = (NeuralSocketBlockEntity) h.getBlockEntity(FLOOR);
        ItemStack head = jarredHead(h, EntityType.VILLAGER, null);
        socket.items().setStackInSlot(0, head.copy());
        CompoundTag saved = socket.saveWithFullMetadata(h.getLevel().registryAccess());
        h.setBlock(FLOOR, Blocks.AIR);
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        var again = (NeuralSocketBlockEntity) h.getBlockEntity(FLOOR);
        again.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(ItemStack.isSameItemSameComponents(again.head(), head), "the head in an old socket changed");
        h.runAfterDelay(20, () -> {
            h.assertTrue(again.output(Direction.NORTH) > 0, "the villager's program stopped");
            h.succeed();
        });
    }

    // --- Leech Jar -----------------------------------------------------------------------------------------------

    /**
     * A cow beside it: the clients are told its side, the sampling takes its blood as before and shows a vial; full,
     * the lamp waits; a click empties it into the player's hands and the vials go.
     */
    @GameTest(template = "platform", batch = "machine_line5", timeoutTicks = 200)
    public static void theLeechWakesSamplesAndIsEmptied(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(3));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.LEECH_JAR_ITEM.get()));
        use(h, p, FLOOR.below(), Direction.UP);
        h.assertTrue(h.getBlockState(FLOOR).is(ModRegistry.LEECH_JAR.get()) && isTop(h, FLOOR.above()), "placed with its top cell");
        var jar = (LeechJarBlockEntity) h.getBlockEntity(FLOOR);
        Mob cow = h.spawn(EntityType.COW, FLOOR.east());
        cow.setNoAi(true);
        h.runAfterDelay(25, () -> {
            CompoundTag tag = jar.getUpdateTag(h.getLevel().registryAccess());
            h.assertValueEqual((int) tag.getByte("side"), Direction.EAST.get2DDataValue(), "the cow's side: " + tag);
            // the sampling tick, as it comes every 30 s
            h.assertTrue(jar.feed(h.getLevel()), "no sample from the cow");
            for (int i = 1; i < 9; i++) jar.items().setStackInSlot(i, jar.items().getStackInSlot(0).copy());
            h.runAfterDelay(20, () -> {
                h.assertValueEqual(h.getBlockState(FLOOR).getValue(LeechJarBlock.SAMPLES), 3, "nine samples' vials");
                h.assertValueEqual(lamp(h, FLOOR), MachineLamp.WAIT, "a full jar's lamp");
                h.assertTrue(jar.getUpdateTag(h.getLevel().registryAccess()).getBoolean("full"), "the clients are not told it is full");
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                // a click on the globe (the top cell) empties it
                use(h, p, FLOOR.above(), Direction.SOUTH);
                h.assertValueEqual(jar.count(), 0, "the click did not empty it");
                h.assertTrue(p.getInventory().countItem(ModRegistry.BLOOD_SAMPLE.get()) == 9, "the samples did not reach the player");
                h.runAfterDelay(20, () -> {
                    h.assertValueEqual(h.getBlockState(FLOOR).getValue(LeechJarBlock.SAMPLES), 0, "emptied, the window");
                    h.assertValueEqual(lamp(h, FLOOR), MachineLamp.READY, "emptied, the lamp");
                    cow.discard();
                    leave(h, p);
                    h.succeed();
                });
            });
        });
    }

    /** A leech jar placed before (no lamp or samples; no top cell) keeps its samples and gets its top cell. */
    @GameTest(template = "platform", batch = "machine_line5", timeoutTicks = 60)
    public static void anOldLeechJarKeepsItsSamples(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.LEECH_JAR.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.WEST));
        var jar = (LeechJarBlockEntity) h.getBlockEntity(FLOOR);
        ItemStack sample = new ItemStack(ModRegistry.BLOOD_SAMPLE.get());
        jar.items().setStackInSlot(0, sample.copy());
        jar.items().setStackInSlot(1, sample.copy());
        CompoundTag saved = jar.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(saved.contains("Items") && saved.contains("Timer"), "the save's keys changed: " + saved.getAllKeys());
        jar.items().setStackInSlot(0, ItemStack.EMPTY);
        jar.items().setStackInSlot(1, ItemStack.EMPTY);
        jar.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertValueEqual(jar.count(), 2, "the old samples");
        h.runAfterDelay(20, () -> {
            h.assertTrue(isTop(h, FLOOR.above()), "no top cell");
            h.assertValueEqual(h.getBlockState(FLOOR.above()).getValue(MachineTopBlock.FACING), Direction.WEST, "the top cell's facing");
            h.assertValueEqual(h.getBlockState(FLOOR).getValue(LeechJarBlock.SAMPLES), 1, "two samples, one vial");
            h.succeed();
        });
    }

    // --- Jarred Head block ---------------------------------------------------------------------------------------

    /** A jarred villager placed before loads as the same head: its kind, its whole record and its restock clock. */
    @GameTest(template = "platform", batch = "machine_line5", timeoutTicks = 40)
    public static void anOldJarredHeadIsTheSameHead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        ItemStack head = jarredHead(h, EntityType.VILLAGER, VillagerProfession.LIBRARIAN);
        // placed by a click, as a player does
        p.setItemInHand(InteractionHand.MAIN_HAND, head.copy());
        use(h, p, FLOOR.below(), Direction.UP);
        h.assertTrue(h.getBlockState(FLOOR).is(ModRegistry.JARRED_HEAD_BLOCK.get()), "the head was not placed");
        var placed = (JarredHeadBlockEntity) h.getBlockEntity(FLOOR);
        // the save an earlier version wrote (the same three keys), loaded into a fresh block
        CompoundTag saved = placed.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(saved.contains("Type") && saved.contains("Entity") && saved.contains("LastRestock"), "the save's keys changed: " + saved.getAllKeys());
        h.setBlock(FLOOR.east(2), ModRegistry.JARRED_HEAD_BLOCK.get().defaultBlockState().setValue(JarredHeadBlock.FACING, Direction.EAST));
        var loaded = (JarredHeadBlockEntity) h.getBlockEntity(FLOOR.east(2));
        loaded.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertValueEqual(loaded.entityType(), EntityType.VILLAGER, "the head's kind");
        h.assertTrue(ItemStack.isSameItemSameComponents(loaded.toItem(), placed.toItem()), "the loaded head is not the same head");
        // the shape is the plinth and the jar: solid across the cell up to 16 px, the plinth's front open below the jar
        var shape = h.getBlockState(FLOOR).getShape(h.getLevel(), h.absolutePos(FLOOR));
        h.assertTrue(shape.max(Direction.Axis.Y) == 1 && shape.min(Direction.Axis.X) < 0.06 && shape.max(Direction.Axis.X) > 0.94,
                "the shape is not the jar's bounds: " + shape.bounds());
        leave(h, p);
        h.succeed();
    }

    // --- Safety Placard ------------------------------------------------------------------------------------------

    /** A click still cycles the notice and reads it out; the board is the block's face, on the wall. */
    @GameTest(template = "platform", batch = "machine_line5")
    public static void thePlacardCyclesOnTheWall(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.north(2));
        h.setBlock(FLOOR.south(), Blocks.STONE);
        h.setBlock(FLOOR, ModRegistry.SAFETY_PLACARD.get().defaultBlockState().setValue(SafetyPlacardBlock.FACING, Direction.NORTH));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(h, p, FLOOR, Direction.NORTH);
        h.assertValueEqual(h.getBlockState(FLOOR).getValue(SafetyPlacardBlock.NOTICE), 1, "the click did not cycle it");
        var shape = h.getBlockState(FLOOR).getShape(h.getLevel(), h.absolutePos(FLOOR));
        h.assertTrue(shape.min(Direction.Axis.Z) > 0.8 && shape.max(Direction.Axis.Z) == 1 && shape.max(Direction.Axis.Y) == 1, "the board is not on the wall: " + shape.bounds());
        leave(h, p);
        h.succeed();
    }
}
