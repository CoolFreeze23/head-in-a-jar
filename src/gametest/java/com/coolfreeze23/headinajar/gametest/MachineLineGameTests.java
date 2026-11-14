package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.GalvanicPileBlockEntity;
import com.coolfreeze23.headinajar.block.LabMachines;
import com.coolfreeze23.headinajar.block.SalineMixerBlock;
import com.coolfreeze23.headinajar.block.SalineMixerBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.machine.KitMachine;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

/**
 * The machine kit and the first three machines on it: the hidden top cell (placing, the refusal under a ceiling, a
 * hopper through it, breaking either half, an old placement with none), the lamp and levels and their hold, the update
 * tag, the openers count with two players, the collection flag, the brown-out, the refusal, and each machine's job
 * producing what it produced before at the same cost and time. Clicks go through the game's own use and break paths.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class MachineLineGameTests {
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

    private static int count(GameTestHelper h, BlockPos around, net.minecraft.world.item.Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(around)).inflate(2)).stream()
                .filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static boolean isTop(GameTestHelper h, BlockPos pos) {
        return h.getBlockState(pos).is(ModRegistry.MACHINE_TOP.get());
    }

    private static MachineLamp lamp(GameTestHelper h, BlockPos pos) {
        return h.getBlockState(pos).getValue(MachineLamp.LAMP);
    }

    private static void stockMixer(SalineMixerBlockEntity m) {
        m.water().fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
        m.items().setStackInSlot(SalineMixerBlockEntity.SLOT_SUGAR, new ItemStack(Items.SUGAR));
        m.items().setStackInSlot(SalineMixerBlockEntity.SLOT_KELP, new ItemStack(Items.KELP));
        m.items().setStackInSlot(SalineMixerBlockEntity.SLOT_REDSTONE, new ItemStack(Items.REDSTONE));
    }

    // --- the top cell -----------------------------------------------------------------------------------------

    @GameTest(template = "platform", batch = "machine_line")
    public static void placingPutsTheTopAndACeilingRefuses(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        BlockPos base = FLOOR.below();
        // placed by a click on the floor's top face: the machine, then its hidden top above it
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SALINE_MIXER_ITEM.get()));
        use(h, p, base, Direction.UP);
        h.assertTrue(h.getBlockState(FLOOR).is(ModRegistry.SALINE_MIXER.get()), "the mixer was not placed");
        h.assertTrue(isTop(h, FLOOR.above()), "the mixer has no top cell");
        h.assertTrue(p.getMainHandItem().isEmpty(), "the item was not used up");
        // under a one-block ceiling: refused with a line, nothing placed, the item kept
        BlockPos spot = FLOOR.east(3);
        h.setBlock(spot.above(), Blocks.STONE);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.GALVANIC_PILE_ITEM.get()));
        use(h, p, spot.below(), Direction.UP);
        h.assertTrue(h.getBlockState(spot).isAir(), "a pile was placed under the ceiling");
        h.assertValueEqual(Refusals.lastDelivered(p), "machine.no_headroom", "the refusal under a ceiling");
        h.assertTrue(p.getMainHandItem().getCount() == 1, "the refused item was used up");
        // a click on the top cell is a click on the machine: it opens the mixer's screen (the test's mock connection
        // refuses the screen packet itself, after the machine has made the menu)
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var m = (SalineMixerBlockEntity) h.getBlockEntity(FLOOR);
        try {
            use(h, p, FLOOR.above(), Direction.NORTH);
        } catch (RuntimeException screenPacket) {
            // expected with a mock player
        }
        h.assertTrue(m.kit().openers() == 1, "a click on the upper half did not reach the mixer");
        // the top cell is solid where the machine is drawn
        h.assertFalse(h.getBlockState(FLOOR.above()).getCollisionShape(h.getLevel(), h.absolutePos(FLOOR.above())).isEmpty(), "the upper half has no collision");
        leave(h, p);
        h.succeed();
    }

    /** The top cell draws the machine's upper half, so it carries which machine, its facing and its shown saline. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 60)
    public static void theTopCellFollowsTheMachine(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.SALINE_MIXER.get().defaultBlockState().setValue(com.coolfreeze23.headinajar.block.MachineBlock.FACING, Direction.EAST));
        var m = (SalineMixerBlockEntity) h.getBlockEntity(FLOOR);
        m.saline().fill(new FluidStack(com.coolfreeze23.headinajar.registry.ModFluids.SALINE.source.get(), 2500), IFluidHandler.FluidAction.EXECUTE);
        h.runAfterDelay(3, () -> {
            var top = h.getBlockState(FLOOR.above());
            h.assertTrue(top.is(ModRegistry.MACHINE_TOP.get()), "no top cell");
            h.assertValueEqual(top.getValue(MachineTopBlock.MACHINE), MachineTopBlock.Kind.SALINE_MIXER, "the top's machine");
            h.assertValueEqual(top.getValue(MachineTopBlock.FACING), Direction.EAST, "the top's facing");
            h.assertValueEqual(top.getValue(MachineTopBlock.SALINE), 3, "the top's saline level");
            h.assertTrue(top.getLightBlock(h.getLevel(), h.absolutePos(FLOOR.above())) == 0 && !top.canOcclude(), "the top cell blocks light or occludes");
            h.succeed();
        });
    }

    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 100)
    public static void aHopperFeedsThroughTheTop(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.SALINE_MIXER.get());
        h.runAfterDelay(2, () -> {
            h.assertTrue(isTop(h, FLOOR.above()), "no top cell");
            h.setBlock(FLOOR.above(2), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
            var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) h.getBlockEntity(FLOOR.above(2));
            hopper.setItem(0, new ItemStack(Items.SUGAR, 4));
        });
        h.succeedWhen(() -> {
            var m = (SalineMixerBlockEntity) h.getBlockEntity(FLOOR);
            h.assertTrue(m.items().getStackInSlot(SalineMixerBlockEntity.SLOT_SUGAR).is(Items.SUGAR), "the hopper above did not feed the mixer");
        });
    }

    @GameTest(template = "platform", batch = "machine_line")
    public static void breakingEitherHalfBreaksBoth(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        // the top broken: the machine drops itself and what it held
        h.setBlock(FLOOR, ModRegistry.CENTRIFUGE.get());
        h.assertTrue(com.coolfreeze23.headinajar.machine.TallMachine.ensureTop(h.getLevel(), h.absolutePos(FLOOR)), "no top");
        ((LabMachines.Centrifuge) h.getBlockEntity(FLOOR)).items().setStackInSlot(1, new ItemStack(ModRegistry.DNA_FRACTION.get()));
        p.gameMode.destroyBlock(h.absolutePos(FLOOR.above()));
        h.assertTrue(h.getBlockState(FLOOR).isAir() && h.getBlockState(FLOOR.above()).isAir(), "breaking the top left a half");
        h.assertTrue(count(h, FLOOR, ModRegistry.CENTRIFUGE_ITEM.get()) == 1, "the machine did not drop");
        h.assertTrue(count(h, FLOOR, ModRegistry.DNA_FRACTION.get()) == 1, "its contents did not drop");
        // the machine broken: the top goes, dropping nothing more
        BlockPos other = FLOOR.east(3);
        h.setBlock(other, ModRegistry.GALVANIC_PILE.get());
        com.coolfreeze23.headinajar.machine.TallMachine.ensureTop(h.getLevel(), h.absolutePos(other));
        p.gameMode.destroyBlock(h.absolutePos(other));
        h.assertTrue(h.getBlockState(other).isAir() && h.getBlockState(other.above()).isAir(), "breaking the machine left its top");
        h.assertTrue(count(h, other, ModRegistry.GALVANIC_PILE_ITEM.get()) == 1, "the pile dropped " + count(h, other, ModRegistry.GALVANIC_PILE_ITEM.get()));
        leave(h, p);
        h.succeed();
    }

    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 260)
    public static void anOldPlacementWorksWithOrWithoutATop(GameTestHelper h) {
        // under a ceiling (placed before tall machines): no top, and it spins as before
        h.setBlock(FLOOR.above(), Blocks.STONE);
        h.setBlock(FLOOR, ModRegistry.CENTRIFUGE.get());
        var c = (LabMachines.Centrifuge) h.getBlockEntity(FLOOR);
        c.items().setStackInSlot(0, GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), EntityType.SPIDER, 70));
        c.energy().set(20_000);
        // room above but no top yet: it puts its top on its first tick
        BlockPos other = FLOOR.east(3);
        h.setBlock(other, ModRegistry.GALVANIC_PILE.get());
        h.assertFalse(isTop(h, other.above()), "a top before any tick");
        h.runAfterDelay(2, () -> h.assertTrue(isTop(h, other.above()), "an old pile with room did not get its top"));
        h.runAtTickTime(200, () -> {
            h.assertTrue(h.getBlockState(FLOOR.above()).is(Blocks.STONE), "the ceiling was replaced");
            h.assertTrue(c.items().getStackInSlot(1).is(ModRegistry.DNA_FRACTION.get()), "the centrifuge under a ceiling did not work");
            h.succeed();
        });
    }

    /**
     * Every machine that is two cells tall now, placed as 0.3.0 left it with a solid block on top (stone) and one with
     * grass on top: under the stone it keeps the stone and stands as one cell, its block entity alive; under grass it
     * takes its top cell (grass is replaceable, as a placement treats it); once the stone is broken it takes its top too.
     */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 80)
    public static void everyTallMachinePlacedBeforeKeepsWhatIsAboveIt(GameTestHelper h) {
        net.minecraft.world.level.block.Block[] tall = {ModRegistry.GALVANIC_PILE.get(), ModRegistry.SALINE_MIXER.get(), ModRegistry.CENTRIFUGE.get(),
                ModRegistry.CRADLE.get(), ModRegistry.BODY_PRINTER.get(), ModRegistry.GENE_SPLICER.get(), ModRegistry.CHARGING_DOCK.get(),
                ModRegistry.TESLA_NODE.get(), ModRegistry.INJECTION_RIG.get(), ModRegistry.DNA_SEQUENCER.get(), ModRegistry.CEREBRAL_ARRAY.get(),
                ModRegistry.RELAY_MAST.get(), ModRegistry.RELAY_MAST_MK2.get(), ModRegistry.TURRET_PEDESTAL.get(), ModRegistry.BODY_LOCKER.get(),
                ModRegistry.LEYDEN_JAR.get(), ModRegistry.LEECH_JAR.get()};
        for (int i = 0; i < tall.length; i++) {
            h.assertTrue(tall[i] instanceof com.coolfreeze23.headinajar.machine.TallMachine, tall[i] + " is not a tall machine");
            BlockPos under = new BlockPos(1 + i % 9, 2, i < 9 ? 3 : 5), grass = under.south(6);
            h.setBlock(under.above(), Blocks.STONE);
            h.setBlock(under, tall[i]);
            h.setBlock(grass.above(), Blocks.SHORT_GRASS);
            h.setBlock(grass, tall[i]);
        }
        h.runAfterDelay(5, () -> {
            for (int i = 0; i < tall.length; i++) {
                BlockPos under = new BlockPos(1 + i % 9, 2, i < 9 ? 3 : 5), grass = under.south(6);
                h.assertTrue(h.getBlockState(under.above()).is(Blocks.STONE), tall[i] + " replaced the stone above it");
                h.assertTrue(h.getBlockState(under).is(tall[i]) && h.getBlockEntity(under) != null, tall[i] + " under stone is gone or lost its block entity");
                h.assertTrue(isTop(h, grass.above()), tall[i] + " under grass did not take its top cell");
                // the player breaks the stone: the machine takes its top cell within two seconds
                h.setBlock(under.above(), Blocks.AIR);
            }
        });
        h.runAfterDelay(50, () -> {
            for (int i = 0; i < tall.length; i++) {
                BlockPos under = new BlockPos(1 + i % 9, 2, i < 9 ? 3 : 5);
                h.assertTrue(isTop(h, under.above()), tall[i] + " did not take its top cell once the stone above was gone");
            }
            h.succeed();
        });
    }

    /**
     * An old Charging Dock under glass, clicked: the player is told once that it stands cut off, and the click still does
     * its job (an empty dock refuses); a second click says nothing more; under stone nothing is said at all.
     */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 20)
    public static void anOldDockUnderGlassSaysItIsCutOffOnce(GameTestHelper h) {
        BlockPos glass = FLOOR, stone = FLOOR.east(3);
        h.setBlock(glass.above(), Blocks.GLASS);
        h.setBlock(glass, ModRegistry.CHARGING_DOCK.get());
        h.setBlock(stone.above(), Blocks.STONE);
        h.setBlock(stone, ModRegistry.CHARGING_DOCK.get());
        ServerPlayer p = player(h, FLOOR.south(2));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(h, p, glass, Direction.UP);
        h.assertTrue(com.coolfreeze23.headinajar.block.MachineEvents.toldCutOff(p).size() == 1, "the player was not told the dock under glass is cut off");
        h.assertValueEqual(Refusals.lastDelivered(p), "dock.empty", "the click's own answer");
        use(h, p, glass, Direction.UP);
        h.assertTrue(com.coolfreeze23.headinajar.block.MachineEvents.toldCutOff(p).size() == 1, "told twice");
        use(h, p, stone, Direction.UP);
        h.assertTrue(com.coolfreeze23.headinajar.block.MachineEvents.toldCutOff(p).size() == 1, "told about a machine under stone");
        leave(h, p);
        h.succeed();
    }

    @GameTest(template = "platform", batch = "machine_line")
    public static void anOldSaveLoadsWithEverythingItHeld(GameTestHelper h) {
        // a pile saved mid-burn before BurnTotal existed, and the blockstate it had (lit=true) is gone
        h.setBlock(FLOOR, ModRegistry.GALVANIC_PILE.get());
        var pile = (GalvanicPileBlockEntity) h.getBlockEntity(FLOOR);
        CompoundTag old = new CompoundTag();
        old.putInt("Burning", 1200);
        CompoundTag fuel = new CompoundTag();
        var stacks = new net.minecraft.nbt.ListTag();
        CompoundTag slot = (CompoundTag) new ItemStack(Items.ROTTEN_FLESH, 5).save(h.getLevel().registryAccess());
        slot.putInt("Slot", 0);
        stacks.add(slot);
        fuel.put("Items", stacks);
        fuel.putInt("Size", 1);
        old.put("Fuel", fuel);
        pile.loadWithComponents(old, h.getLevel().registryAccess());
        h.assertTrue(pile.burning() == 1200 && pile.fuel().getStackInSlot(0).getCount() == 5, "the old pile lost what it held");
        h.assertTrue(pile.fuelLeft() == 1200 + 5 * 4000, "the old pile's fuel is worth " + pile.fuelLeft());
        h.succeed();
    }

    // --- lamp, levels, update tag -------------------------------------------------------------------------------

    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 120)
    public static void levelsFollowTheTankAndHold(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.SALINE_MIXER.get());
        var m = (SalineMixerBlockEntity) h.getBlockEntity(FLOOR);
        m.water().fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
        h.runAfterDelay(2, () -> h.assertTrue(h.getBlockState(FLOOR).getValue(SalineMixerBlock.WATER) == 4, "full water is not level 4"));
        h.runAfterDelay(20, () -> m.water().drain(4000, IFluidHandler.FluidAction.EXECUTE));
        h.runAfterDelay(25, () -> {
            m.water().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(26, () -> {
            int shown = h.getBlockState(FLOOR).getValue(SalineMixerBlock.WATER);
            h.assertTrue(shown == 0, "a level changed again within 15 ticks of the last change: " + shown);
        });
        h.runAfterDelay(45, () -> {
            h.assertTrue(h.getBlockState(FLOOR).getValue(SalineMixerBlock.WATER) == 1, "the level did not follow after the hold");
            h.succeed();
        });
    }

    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 100)
    public static void theUpdateTagCarriesOnlyItsFieldsAndOnlyOnAChange(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CENTRIFUGE.get());
        var c = (LabMachines.Centrifuge) h.getBlockEntity(FLOOR);
        int[] sends = new int[1];
        h.runAfterDelay(5, () -> {
            CompoundTag tag = c.getUpdateTag(h.getLevel().registryAccess());
            Set<String> allowed = Set.of("rate", "open", "t0", "p0", "cr", "n", "next", "dna", "vials", "bottles");
            for (String key : tag.getAllKeys()) h.assertTrue(allowed.contains(key), "the update tag carries " + key);
            h.assertFalse(tag.contains("Items") || tag.contains("Energy"), "the update tag carries the save");
            sends[0] = c.kit().sends();
        });
        h.runAfterDelay(25, () -> {
            h.assertTrue(c.kit().sends() == sends[0], "the idle machine sent " + (c.kit().sends() - sends[0]) + " tags in 20 ticks");
            c.items().setStackInSlot(1, new ItemStack(ModRegistry.DNA_FRACTION.get()));
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(c.kit().sends() == sends[0] + 1, "one change sent " + (c.kit().sends() - sends[0]) + " tags");
            h.assertTrue(c.getUpdateTag(h.getLevel().registryAccess()).getByte("dna") == 1, "the change is not in the tag");
            // a client copy fed only the tag draws the same
            var copy = new LabMachines.Centrifuge(c.getBlockPos(), c.getBlockState());
            copy.handleUpdateTag(c.getUpdateTag(h.getLevel().registryAccess()), h.getLevel().registryAccess());
            h.assertTrue(copy.dna() == 1 && copy.hasOutput(), "the copy does not see the output");
            h.succeed();
        });
    }

    // --- the screen, the flag -----------------------------------------------------------------------------------

    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 60)
    public static void theHatchCountsTwoPlayers(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CENTRIFUGE.get());
        var c = (LabMachines.Centrifuge) h.getBlockEntity(FLOOR);
        ServerPlayer a = player(h, FLOOR.south(2)), b = player(h, FLOOR.west(2));
        // what a click opens, on the server (the mock connection can't take the screen packet itself)
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            p.containerMenu = c.createMenu(1, p.getInventory(), p);
        }
        h.assertTrue(c.kit().openers() == 2 && c.kit().open(), "two players: " + c.kit().openers());
        h.assertTrue(c.getUpdateTag(h.getLevel().registryAccess()).getBoolean("open"), "the open bit is not in the tag");
        a.closeContainer();
        h.assertTrue(c.kit().open(), "the drawer shut while a second player had the screen open");
        h.runAfterDelay(10, () -> {
            // the recount keeps the one still looking
            h.assertTrue(c.kit().openers() == 1 && c.kit().open(), "after the recount: " + c.kit().openers());
            b.closeContainer();
            h.assertFalse(c.kit().open(), "the drawer stayed out with nobody at the screen");
            leave(h, a, b);
            h.succeed();
        });
    }

    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 240)
    public static void theFlagStaysUntilTheOutputIsTaken(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CENTRIFUGE.get());
        var c = (LabMachines.Centrifuge) h.getBlockEntity(FLOOR);
        c.items().setStackInSlot(0, GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), EntityType.SPIDER, 70));
        c.energy().set(20_000);
        h.runAtTickTime(170, () -> h.assertTrue(c.getUpdateTag(h.getLevel().registryAccess()).getByte("dna") == 1, "no output flagged after the spin"));
        h.runAtTickTime(210, () -> {
            h.assertTrue(c.hasOutput(), "the flag dropped by itself");
            // taken out from below, as a hopper does
            var below = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, h.absolutePos(FLOOR), Direction.DOWN);
            below.extractItem(1, 64, false);
            below.extractItem(2, 64, false);
        });
        h.runAtTickTime(213, () -> {
            h.assertFalse(c.hasOutput(), "the flag stayed up with the output taken");
            h.succeed();
        });
    }

    // --- brown-out, refusal --------------------------------------------------------------------------------------

    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 200)
    public static void aBrownOutSlowsTheMixerAndTheLampSaysSo(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.SALINE_MIXER.get());
        var m = (SalineMixerBlockEntity) h.getBlockEntity(FLOOR);
        stockMixer(m);
        // power for every other tick only
        h.onEachTick(() -> {
            if (h.getTick() % 2 == 0) m.energy().set(SalineMixerBlockEntity.FE_PER_TICK);
        });
        h.runAtTickTime(100, () -> {
            h.assertTrue(m.progress() >= 40 && m.progress() <= 60, "half power gave progress " + m.progress() + " in 100 ticks");
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.LOW, "the lamp in a brown-out");
            h.assertTrue(m.kit().rate() == 2, "the rate in a brown-out is " + m.kit().rate());
            h.succeed();
        });
    }

    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 120)
    public static void aRefusalRattlesAndTheLampGoesRed(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.GALVANIC_PILE.get());
        var pile = (GalvanicPileBlockEntity) h.getBlockEntity(FLOOR);
        pile.fuel().setStackInSlot(0, new ItemStack(Items.ROTTEN_FLESH, 64));
        h.runAfterDelay(20, () -> {
            // the player is here only for the click: a mock player left standing for ticks upsets other tests' bodies
            ServerPlayer p = player(h, FLOOR.south(2));
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPIDER_EYE, 3));
            use(h, p, FLOOR, Direction.NORTH);
            String said = Refusals.lastDelivered(p);
            int kept = p.getMainHandItem().getCount();
            leave(h, p);
            h.assertValueEqual(said, "pile.full", "the refusal");
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.FAULT, "the lamp at the refusal");
            h.assertTrue(kept == 3, "the refused meat was taken");
        });
        h.runAfterDelay(70, () -> {
            h.assertTrue(lamp(h, FLOOR) != MachineLamp.FAULT, "the lamp stayed red");
            h.succeed();
        });
    }

    // --- the jobs, unchanged ---------------------------------------------------------------------------------------

    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 260)
    public static void theJobsMakeWhatTheyMadeAtTheSameCostAndTime(GameTestHelper h) {
        // the pile: one rotten flesh is 4,000 FE over 200 ticks
        h.setBlock(FLOOR, ModRegistry.GALVANIC_PILE.get());
        var pile = (GalvanicPileBlockEntity) h.getBlockEntity(FLOOR);
        pile.fuel().setStackInSlot(0, new ItemStack(Items.ROTTEN_FLESH));
        // the mixer: 200 ticks, 2,000 FE, sugar, kelp, redstone and 1,000 mB of water for 1,000 mB of saline
        BlockPos mp = FLOOR.east(3);
        h.setBlock(mp, ModRegistry.SALINE_MIXER.get());
        var m = (SalineMixerBlockEntity) h.getBlockEntity(mp);
        stockMixer(m);
        m.energy().set(20_000);
        // the centrifuge: 160 ticks at 19 FE/t, a DNA Fraction and a Plasma Vial
        BlockPos cp = FLOOR.west(3);
        h.setBlock(cp, ModRegistry.CENTRIFUGE.get());
        var c = (LabMachines.Centrifuge) h.getBlockEntity(cp);
        c.items().setStackInSlot(0, GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), EntityType.SPIDER, 70));
        c.energy().set(20_000);
        long start = h.getTick();
        h.runAtTickTime(start + 158, () -> h.assertTrue(c.items().getStackInSlot(1).isEmpty(), "the centrifuge finished early"));
        h.runAtTickTime(start + 162, () -> {
            h.assertTrue(c.items().getStackInSlot(1).is(ModRegistry.DNA_FRACTION.get()) && c.items().getStackInSlot(2).is(ModRegistry.PLASMA_VIAL.get()),
                    "the centrifuge made the wrong things");
            h.assertTrue(c.energy().getEnergyStored() == 20_000 - 160 * 19, "the spin cost " + (20_000 - c.energy().getEnergyStored()));
        });
        h.runAtTickTime(start + 198, () -> h.assertTrue(m.saline().getFluidAmount() == 0, "the mixer finished early"));
        h.runAtTickTime(start + 203, () -> {
            h.assertTrue(m.saline().getFluidAmount() == 1000 && m.water().getFluidAmount() == 1000, "the mixer made " + m.saline().getFluidAmount());
            h.assertTrue(m.energy().getEnergyStored() == 20_000 - 2000, "the batch cost " + (20_000 - m.energy().getEnergyStored()));
            h.assertTrue(m.items().getStackInSlot(SalineMixerBlockEntity.SLOT_SUGAR).isEmpty(), "the sugar was not used");
            h.assertTrue(pile.energy().getEnergyStored() == 4000 && pile.burning() == 0, "the pile made " + pile.energy().getEnergyStored());
            h.succeed();
        });
    }

    /** Nothing a client draws is missing from the kit machines' tags, and they implement the kit. */
    @GameTest(template = "platform", batch = "machine_line")
    public static void theThreeAreOnTheKit(GameTestHelper h) {
        for (var block : java.util.List.of(ModRegistry.GALVANIC_PILE.get(), ModRegistry.SALINE_MIXER.get(), ModRegistry.CENTRIFUGE.get())) {
            h.setBlock(FLOOR, block);
            BlockEntity be = h.getBlockEntity(FLOOR);
            h.assertTrue(be instanceof KitMachine, block + " is not a kit machine");
            h.assertTrue(block instanceof com.coolfreeze23.headinajar.machine.TallMachine, block + " is not tall");
            h.assertTrue(h.getBlockState(FLOOR).hasProperty(MachineLamp.LAMP), block + " has no lamp");
            h.setBlock(FLOOR, Blocks.AIR);
        }
        h.assertTrue(MachineTopBlock.machine(h.getLevel(), h.absolutePos(FLOOR.above())) == null, "a top with no machine");
        h.succeed();
    }

    // --- batch two: the Life Support Cradle, the Body Printer, the Gene Splicer ------------------------------------

    /** A Cradle saved before it was an armchair, with a head docked in a Compact jar: the head is still there, and shown. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 40)
    public static void aCradleSavedWithAHeadKeepsIt(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CRADLE.get());
        var cradle = (com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity) h.getBlockEntity(FLOOR);
        CompoundTag old = new CompoundTag();
        java.util.UUID id = java.util.UUID.randomUUID();
        old.putUUID("Owner", id);
        old.putString("OwnerName", "Dev");
        old.putBoolean("Compact", true);
        old.putInt("Energy", 5000);
        cradle.loadWithComponents(old, h.getLevel().registryAccess());
        // as a dock left it: the head registry says the head is here (else the Cradle lets it go, the game's own rule)
        com.coolfreeze23.headinajar.form.HeadRegistry.get(h.getLevel().getServer()).setHead(id, new com.coolfreeze23.headinajar.form.HeadRegistry.Head(
                com.coolfreeze23.headinajar.form.HeadRegistry.Where.CRADLE,
                new com.coolfreeze23.headinajar.form.HeadRegistry.Spot(h.getLevel().dimension(), net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(FLOOR))), null));
        h.assertTrue(id.equals(cradle.owner()) && cradle.compact(), "the old Cradle lost its head");
        h.runAfterDelay(3, () -> {
            CompoundTag tag = cradle.getUpdateTag(h.getLevel().registryAccess());
            h.assertTrue(tag.hasUUID("Owner") && tag.getBoolean("Compact") && tag.getBoolean("Powered"), "the docked head is not in the update tag: " + tag);
            h.assertFalse(tag.contains("Energy") || tag.contains("Saline"), "the update tag carries the save");
            // a client copy fed only the tag draws the head, Compact
            var copy = new com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity(cradle.getBlockPos(), cradle.getBlockState());
            copy.handleUpdateTag(tag, h.getLevel().registryAccess());
            h.assertTrue(id.equals(copy.owner()) && copy.compact() && copy.powered(), "the client copy does not see the head");
            h.assertTrue(isTop(h, FLOOR.above()), "the old Cradle got no top cell");
            h.succeed();
        });
    }

    /** A jar climbs into an empty Cradle by a click: it stands on the seat, in the chair's own cell. */
    @GameTest(template = "platform", batch = "machine_line")
    public static void aJarClimbsOntoTheCradlesSeat(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.south(2));
        com.coolfreeze23.headinajar.form.FormActions.detach(p, 3);
        h.setBlock(FLOOR, ModRegistry.CRADLE.get());
        com.coolfreeze23.headinajar.machine.TallMachine.ensureTop(h.getLevel(), h.absolutePos(FLOOR));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(h, p, FLOOR, Direction.NORTH);
        BlockPos at = p.blockPosition();
        boolean in = com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity.under(p) != null;
        leave(h, p);
        h.assertTrue(at.equals(h.absolutePos(FLOOR)), "the jar is not on the seat: " + at);
        h.assertTrue(in, "the jar on the seat is not in the Cradle");
        h.succeed();
    }

    /** The Cradle's lamp says no power while a head is docked, its bag shows the saline, its top follows both. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 60)
    public static void theCradleSaysItsPowerAndSaline(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CRADLE.get());
        var cradle = (com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity) h.getBlockEntity(FLOOR);
        java.util.UUID id = java.util.UUID.randomUUID();
        cradle.dock(id, "Dev", ItemStack.EMPTY);
        // as a dock leaves it: the head registry says the head is here, else the Cradle's check (every 20 ticks of game
        // time) lets the head go, and whether that check fell inside the wait below depended on the tick the test began
        var registry = com.coolfreeze23.headinajar.form.HeadRegistry.get(h.getLevel().getServer());
        registry.setHead(id, new com.coolfreeze23.headinajar.form.HeadRegistry.Head(com.coolfreeze23.headinajar.form.HeadRegistry.Where.CRADLE,
                new com.coolfreeze23.headinajar.form.HeadRegistry.Spot(h.getLevel().dimension(), net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(FLOOR))), null));
        cradle.saline().fill(new FluidStack(com.coolfreeze23.headinajar.registry.ModFluids.SALINE.source.get(), 2500), IFluidHandler.FluidAction.EXECUTE);
        h.runAfterDelay(5, () -> {
            boolean docked = id.equals(cradle.owner());
            registry.setHead(id, null);
            h.assertTrue(docked, "the Cradle let its docked head go");
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.LOW, "a docked head with no power");
            var state = h.getBlockState(FLOOR);
            h.assertValueEqual(state.getValue(com.coolfreeze23.headinajar.block.LifeSupportCradleBlock.SALINE), 3, "the bag's level");
            var top = h.getBlockState(FLOOR.above());
            h.assertValueEqual(top.getValue(MachineTopBlock.LAMP), MachineLamp.LOW, "the headrest's lamp (the top cell)");
            h.assertValueEqual(top.getValue(MachineTopBlock.SALINE), 3, "the bag (the top cell)");
            h.assertFalse(cradle.getUpdateTag(h.getLevel().registryAccess()).getBoolean("Powered"), "an unpowered Cradle says it breathes");
            h.succeed();
        });
    }

    /** The Print button refuses a player with a head on (the booth's bar kicks, the lamp is red); a jar's print runs on the clock. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 60)
    public static void thePrinterAnswersPrintAndShowsItsClock(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.BODY_PRINTER.get());
        var printer = (com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity) h.getBlockEntity(FLOOR);
        printer.items().setStackInSlot(0, new ItemStack(Items.ROTTEN_FLESH, 16));
        printer.items().setStackInSlot(1, new ItemStack(Items.BONE_BLOCK, 8));
        printer.items().setStackInSlot(2, new ItemStack(Items.REDSTONE_BLOCK, 4));
        printer.items().setStackInSlot(3, new ItemStack(Items.SLIME_BALL, 8));
        printer.saline().fill(new FluidStack(com.coolfreeze23.headinajar.registry.ModFluids.SALINE.source.get(), 2000), IFluidHandler.FluidAction.EXECUTE);
        printer.energy().set(200_000);
        h.runAfterDelay(3, () -> {
            h.assertValueEqual(printer.getUpdateTag(h.getLevel().registryAccess()).getByte("mats"), (byte) 15, "the four materials in the tag");
            ServerPlayer whole = player(h, FLOOR.south(2));
            printer.createMenu(1, whole.getInventory(), whole).clickMenuButton(whole, 0);
            String said = Refusals.lastDelivered(whole);
            leave(h, whole);
            h.assertValueEqual(said, "printer.has_head", "Print with a head on");
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.FAULT, "the lamp at the refusal");
            ServerPlayer jar = player(h, FLOOR.south(2));
            com.coolfreeze23.headinajar.form.FormActions.detach(jar, 3);
            printer.createMenu(2, jar.getInventory(), jar).clickMenuButton(jar, 0);
            leave(h, jar);
            h.assertTrue(printer.printing(), "the print did not start");
        });
        h.runAfterDelay(30, () -> {
            CompoundTag tag = printer.getUpdateTag(h.getLevel().registryAccess());
            h.assertTrue(tag.contains("t0") && tag.hasUUID("skin"), "the print's clock or skin is not in the tag: " + tag);
            h.assertValueEqual(h.getBlockState(FLOOR.above()).getValue(MachineTopBlock.MACHINE), MachineTopBlock.Kind.BODY_PRINTER, "the top cell");
            h.succeed();
        });
    }

    /** The Splicer's finish is seen: the event, the stitch at the strand's end, the cassette and the flag. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 40)
    public static void theSplicersFinishIsShown(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.GENE_SPLICER.get());
        var s = (LabMachines.Splicer) h.getBlockEntity(FLOOR);
        CompoundTag tag = new CompoundTag();
        tag.putString("Gene0", com.coolfreeze23.headinajar.genetics.Gene.values()[0].id());
        tag.putBoolean("Running", true);
        tag.putInt("Progress", LabMachines.Splicer.TICKS - 2);
        tag.putInt("Energy", 20_000);
        CompoundTag items = new CompoundTag();
        var list = new net.minecraft.nbt.ListTag();
        for (int[] slot : new int[][]{{1, 0}, {2, 1}}) {
            ItemStack stack = slot[0] == 1 ? new ItemStack(ModRegistry.BIO_GEL.get()) : new ItemStack(Items.GLASS_BOTTLE);
            CompoundTag one = (CompoundTag) stack.save(h.getLevel().registryAccess());
            one.putInt("Slot", slot[0]);
            list.add(one);
        }
        items.put("Items", list);
        items.putInt("Size", 4);
        tag.put("Items", items);
        s.loadWithComponents(tag, h.getLevel().registryAccess());
        h.runAfterDelay(10, () -> {
            h.assertTrue(s.hasOutput(), "no cassette after the run");
            h.assertValueEqual(h.getBlockState(FLOOR).getValue(com.coolfreeze23.headinajar.block.GeneSplicerBlock.STITCH), 3, "the strand's stitches at the end");
            CompoundTag sync = s.getUpdateTag(h.getLevel().registryAccess());
            h.assertTrue(sync.getBoolean("out") && sync.getByteArray("spools")[0] == 1, "the tag: " + sync);
            h.succeed();
        });
    }

    // --- the silent cases, and batch three ---------------------------------------------------------------------

    /** Cooked beef at the Pile is refused, naming what it burns; another meat in the slot is refused too, nothing fed. */
    @GameTest(template = "platform", batch = "machine_line")
    public static void thePileRefusesWhatItDoesNotBurn(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.GALVANIC_PILE.get());
        var pile = (com.coolfreeze23.headinajar.block.GalvanicPileBlockEntity) h.getBlockEntity(FLOOR);
        ServerPlayer p = player(h, FLOOR.south(2));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF));
        use(h, p, FLOOR, Direction.NORTH);
        String beef = Refusals.lastDelivered(p);
        pile.fuel().setStackInSlot(0, new ItemStack(Items.ROTTEN_FLESH, 3));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPIDER_EYE, 2));
        use(h, p, FLOOR, Direction.NORTH);
        String eye = Refusals.lastDelivered(p);
        int eyes = p.getMainHandItem().getCount();
        leave(h, p);
        h.assertValueEqual(beef, "pile.not_fuel", "cooked beef");
        h.assertValueEqual(eye, "pile.full", "a spider eye on rotten flesh");
        h.assertValueEqual(eyes, 2, "spider eyes kept");
        h.assertValueEqual(pile.fuel().getStackInSlot(0).getCount(), 3, "the slot");
        h.succeed();
    }

    /** An empty bucket at a Mixer with less than a bucket of saline: refused, no screen. */
    @GameTest(template = "platform", batch = "machine_line")
    public static void theMixerSaysItHasNothingToFillABucket(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.SALINE_MIXER.get());
        ServerPlayer p = player(h, FLOOR.south(2));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        use(h, p, FLOOR, Direction.NORTH);
        String said = Refusals.lastDelivered(p);
        boolean screen = p.containerMenu != p.inventoryMenu;
        leave(h, p);
        h.assertValueEqual(said, "mixer.nothing_to_fill", "the bucket");
        h.assertFalse(screen, "the screen opened");
        h.succeed();
    }

    /** A jar on the Charging Dock: the switch shuts and the dial reads its charge; empty, the dock browns out; a click on an empty dock is refused. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 100)
    public static void theDockShowsItsSwitchAndTheJarsCharge(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CHARGING_DOCK.get().defaultBlockState());
        var dock = (com.coolfreeze23.headinajar.block.ChargingDockBlock.Entity) h.getBlockEntity(FLOOR);
        dock.energy().set(20_000);
        ServerPlayer jar = player(h, FLOOR);
        com.coolfreeze23.headinajar.form.FormActions.detach(jar, 3);
        var state = com.coolfreeze23.headinajar.form.FormActions.state(jar);
        state.setCharge(state.chargeCapacity() / 4);
        Vec3 top = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR)).add(0, com.coolfreeze23.headinajar.block.ChargingDockBlock.TOP, 0);
        jar.moveTo(top.x, top.y, top.z, 0, 0);
        h.runAfterDelay(22, () -> {
            h.assertValueEqual(dock.switchState(), com.coolfreeze23.headinajar.block.ChargingDockBlock.Entity.SHUT, "the switch with a jar on");
            h.assertTrue(dock.charge() >= 20 && dock.charge() <= 100, "the dial's reading: " + dock.charge());
            h.assertTrue(isTop(h, FLOOR.above()), "no top cell");
            h.assertValueEqual(h.getBlockState(FLOOR.above()).getValue(MachineTopBlock.LAMP), MachineLamp.WORK, "the lamp (the top cell)");
            dock.energy().set(0);
        });
        h.runAfterDelay(40, () -> {
            h.assertValueEqual(dock.switchState(), com.coolfreeze23.headinajar.block.ChargingDockBlock.Entity.LOW, "an empty dock under a jar");
            CompoundTag tag = dock.getUpdateTag(h.getLevel().registryAccess());
            h.assertTrue(tag.getByte("sw") == 2 && tag.contains("charge"), "the update tag: " + tag);
            leave(h, jar);
            ServerPlayer body = player(h, FLOOR.south(2));
            body.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            use(h, body, FLOOR, Direction.UP);
            String said = Refusals.lastDelivered(body);
            leave(h, body);
            h.assertValueEqual(said, "dock.empty", "a click on an empty dock");
        });
        h.runAfterDelay(55, () -> {
            h.assertValueEqual(dock.switchState(), com.coolfreeze23.headinajar.block.ChargingDockBlock.Entity.OPEN, "the jar gone");
            h.succeed();
        });
    }

    /**
     * The film's order: a jar steps onto the dock, the dock runs dry under it, the jar steps off and goes back on its body,
     * and its next click on the empty dock is refused like anyone's.
     */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 120)
    public static void aHeadBackOnItsBodyIsRefusedByAnEmptyDock(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CHARGING_DOCK.get().defaultBlockState());
        var dock = (com.coolfreeze23.headinajar.block.ChargingDockBlock.Entity) h.getBlockEntity(FLOOR);
        dock.energy().set(20_000);
        ServerPlayer p = player(h, FLOOR.south(2).west(2));
        com.coolfreeze23.headinajar.form.FormActions.detach(p, 3);
        Vec3 top = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR)).add(0, com.coolfreeze23.headinajar.block.ChargingDockBlock.TOP, 0);
        p.teleportTo(top.x, top.y, top.z);
        h.runAfterDelay(20, () -> dock.energy().set(0));
        h.runAfterDelay(60, () -> {
            Vec3 off = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.south(3).west(2)));
            p.teleportTo(off.x, off.y, off.z);
            var bodies = h.getLevel().getEntitiesOfClass(com.coolfreeze23.headinajar.entity.HeadlessBody.class, new AABB(h.absolutePos(FLOOR)).inflate(8));
            h.assertTrue(!bodies.isEmpty() && com.coolfreeze23.headinajar.form.FormActions.reattach(p, bodies.get(0)), "the head did not go back on its body");
        });
        h.runAfterDelay(80, () -> {
            String held = p.getMainHandItem().toString();
            use(h, p, FLOOR, Direction.UP);
            String said = Refusals.lastDelivered(p);
            leave(h, p);
            h.assertValueEqual(said, "dock.empty", "a click on an empty dock, holding " + held + ", energy " + dock.energy().getEnergyStored());
            h.succeed();
        });
    }

    /**
     * A pair linked in 0.3.0 (the sender saved only its Target; the receiver nothing of its sender) loads, sends, and
     * the receiver learns its sender within a tick, so its ball turns to face it.
     */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 40)
    public static void anOldTeslaPairLoadsAndTheReceiverLearnsItsSender(GameTestHelper h) {
        BlockPos a = FLOOR.west(3), b = FLOOR.east(3);
        h.setBlock(a, ModRegistry.TESLA_NODE.get());
        h.setBlock(b, ModRegistry.TESLA_NODE.get());
        var sender = (com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode) h.getBlockEntity(a);
        var receiver = (com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode) h.getBlockEntity(b);
        // the old saves, as 0.3.0 wrote them
        CompoundTag oldSender = new CompoundTag();
        oldSender.putInt("Energy", 10_000);
        oldSender.putLong("Target", h.absolutePos(b).asLong());
        sender.loadWithComponents(oldSender, h.getLevel().registryAccess());
        CompoundTag oldReceiver = new CompoundTag();
        oldReceiver.putInt("Energy", 0);
        receiver.loadWithComponents(oldReceiver, h.getLevel().registryAccess());
        h.assertTrue(receiver.source() == null, "the old receiver knew its sender");
        h.runAfterDelay(5, () -> {
            h.assertTrue(h.absolutePos(a).equals(receiver.source()), "the receiver did not learn its sender");
            h.assertTrue(receiver.energy().getEnergyStored() > 0, "the old pair did not send");
            h.succeed();
        });
    }

    /** Two Tesla Nodes linked by the wrench (clicked on their upper halves): the sender sends and says so, the receiver knows its sender. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 60)
    public static void linkedTeslaNodesShowTheirLinkAndSending(GameTestHelper h) {
        BlockPos a = FLOOR.west(3), b = FLOOR.east(3);
        h.setBlock(a, ModRegistry.TESLA_NODE.get());
        h.setBlock(b, ModRegistry.TESLA_NODE.get());
        var sender = (com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode) h.getBlockEntity(a);
        var receiver = (com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode) h.getBlockEntity(b);
        sender.energy().set(10_000);
        receiver.energy().set(8_000);
        h.runAfterDelay(3, () -> {
            h.assertTrue(isTop(h, a.above()) && isTop(h, b.above()), "the nodes' upper halves");
            ServerPlayer p = player(h, FLOOR.south(2));
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_WRENCH.get()));
            for (BlockPos at : new BlockPos[]{a.above(), b.above()}) {
                BlockPos abs = h.absolutePos(at);
                var ctx = new net.minecraft.world.item.context.UseOnContext(p, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
                p.getMainHandItem().onItemUseFirst(ctx);
            }
            leave(h, p);
            h.assertValueEqual(sender.target(), h.absolutePos(b), "the link");
            h.assertValueEqual(receiver.source(), h.absolutePos(a), "the receiver's sender");
        });
        h.runAfterDelay(7, () -> {
            h.assertTrue(sender.sending(), "the sender is not sending");
            CompoundTag tag = sender.getUpdateTag(h.getLevel().registryAccess());
            h.assertTrue(tag.contains("to") && tag.getBoolean("send"), "the sender's tag: " + tag);
            h.assertTrue(receiver.getUpdateTag(h.getLevel().registryAccess()).contains("from"), "the receiver's tag");
        });
        // the receiver (8 of its 10 kFE stored, feeding nothing) fills in a few ticks: the sender waits, and says so
        h.runAfterDelay(40, () -> {
            h.assertTrue(sender.kit().since(com.coolfreeze23.headinajar.machine.MachineKit.EV_OWN, h.getLevel().getGameTime()) < 40, "the sender's perk");
            h.assertFalse(sender.sending(), "still sending into a full node");
            h.assertValueEqual(lamp(h, a), MachineLamp.WAIT, "the sender's lamp, its partner full");
            h.succeed();
        });
    }

    private static ItemStack rigCassette() {
        ItemStack stack = new ItemStack(ModRegistry.SERUM_CASSETTE.get());
        new com.coolfreeze23.headinajar.genetics.Serum(java.util.List.of(new com.coolfreeze23.headinajar.genetics.Serum.Entry(
                com.coolfreeze23.headinajar.genetics.Gene.values()[0], 1f)), 100).writeTo(stack);
        return stack;
    }

    /** Inject pressed: the shot lands thirty ticks later, not at once; the globe shows the serum's colour, the counter rolls. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 80)
    public static void theRigsShotLandsAtThirtyTicks(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.INJECTION_RIG.get());
        var rig = (LabMachines.InjectionRig) h.getBlockEntity(FLOOR);
        rig.items().setStackInSlot(0, rigCassette());
        rig.energy().set(20_000);
        ServerPlayer p = player(h, FLOOR.south(2));
        h.runAfterDelay(3, () -> {
            h.assertTrue(h.getBlockState(FLOOR.above()).getValue(MachineTopBlock.SALINE) > 0, "the globe is not lit for a loaded serum");
            h.assertTrue(rig.start(p), "Inject was refused");
        });
        h.runAfterDelay(15, () -> {
            h.assertValueEqual(rig.energy().getEnergyStored(), 20_000, "power spent before the jab");
            h.assertFalse(rig.items().getStackInSlot(0).isEmpty(), "the cassette used before the jab");
            h.assertValueEqual(h.getBlockState(FLOOR).getValue(com.coolfreeze23.headinajar.block.InjectionRigBlock.COUNTER), 1, "the counter during the shot");
        });
        h.runAfterDelay(40, () -> {
            boolean spent = rig.energy().getEnergyStored() < 20_000 && rig.items().getStackInSlot(0).isEmpty();
            leave(h, p);
            h.assertTrue(spent, "the shot did not land at thirty ticks");
            h.assertTrue(rig.kit().since(LabMachines.InjectionRig.EV_RESULT, h.getLevel().getGameTime()) < 15, "no result event");
            h.succeed();
        });
    }

    /** A patient who walks off before the jab: the shot is called off, nothing spent, the lamp red. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 60)
    public static void theRigsShotIsCalledOffIfThePatientMoves(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.INJECTION_RIG.get());
        var rig = (LabMachines.InjectionRig) h.getBlockEntity(FLOOR);
        rig.items().setStackInSlot(0, rigCassette());
        rig.energy().set(20_000);
        ServerPlayer p = player(h, FLOOR.south(2));
        h.runAfterDelay(3, () -> h.assertTrue(rig.start(p), "Inject was refused"));
        h.runAfterDelay(12, () -> {
            Vec3 away = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.south(5)));
            p.moveTo(away.x, away.y, away.z, 0, 0);
        });
        h.runAfterDelay(20, () -> {
            boolean kept = rig.energy().getEnergyStored() == 20_000 && !rig.items().getStackInSlot(0).isEmpty();
            boolean cancelled = rig.kit().since(LabMachines.InjectionRig.EV_CANCEL, h.getLevel().getGameTime()) < 10;
            leave(h, p);
            h.assertTrue(cancelled, "no cancel");
            h.assertTrue(kept, "something was spent");
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.FAULT, "the lamp after a cancel");
            h.succeed();
        });
    }

    /** A called read rings up its tally (every call right: all twelve bits); the auto-read runs on the kit's clock. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 60)
    public static void theSequencerRingsUpItsCalls(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.DNA_SEQUENCER.get());
        var seq = (LabMachines.Sequencer) h.getBlockEntity(FLOOR);
        seq.items().setStackInSlot(0, com.coolfreeze23.headinajar.item.GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), net.minecraft.world.entity.EntityType.ZOMBIE, 60));
        seq.energy().set(40_000);
        h.runAfterDelay(3, () -> {
            ServerPlayer p = player(h, FLOOR.south(2));
            int[] bases = seq.shownBases();
            // a key struck in the screen rings on the register for all to see, and reads nothing
            h.assertTrue(seq.button(p, LabMachines.Sequencer.KEY + 4 + 2), "a struck key was refused");
            h.assertValueEqual(seq.kit().eventParam(LabMachines.Sequencer.EV_BEAT), 6, "the struck key (call 1, base G)");
            h.assertTrue(seq.button(p, LabMachines.Sequencer.CALL + LabMachines.Sequencer.encode(bases)), "the call was refused");
            leave(h, p);
            h.assertValueEqual(seq.kit().eventParam(LabMachines.Sequencer.EV_TALLY), 0xFFF, "the tally of twelve right calls");
            h.assertTrue(isTop(h, FLOOR.above()), "no top cell");
        });
        h.runAfterDelay(6, () -> {
            seq.items().setStackInSlot(0, com.coolfreeze23.headinajar.item.GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), net.minecraft.world.entity.EntityType.ZOMBIE, 60));
            ServerPlayer p = player(h, FLOOR.south(2));
            h.assertTrue(seq.button(p, LabMachines.Sequencer.AUTO), "Auto was refused");
            leave(h, p);
        });
        h.runAfterDelay(30, () -> {
            CompoundTag tag = seq.getUpdateTag(h.getLevel().registryAccess());
            h.assertTrue(tag.contains("t0") && tag.getBoolean("auto") && tag.getBoolean("loaded"), "the auto-read's clock: " + tag);
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.WORK, "the lamp while reading");
            h.succeed();
        });
    }

    /** The Array: its urn shows the broth on the top cell, its lamp the heads' state, its update tag the seats. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 60)
    public static void theArrayShowsItsBrothAndSeats(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CEREBRAL_ARRAY.get());
        var array = (com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity) h.getBlockEntity(FLOOR);
        h.runAfterDelay(3, () -> h.assertValueEqual(lamp(h, FLOOR), MachineLamp.OFF, "an empty Array's lamp"));
        h.runAfterDelay(4, () -> {
            array.items().setStackInSlot(4, new ItemStack(Items.DRAGON_HEAD));
            array.broth().fill(new FluidStack(com.coolfreeze23.headinajar.registry.ModFluids.BROTH.source.get(), 6000), IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAfterDelay(45, () -> {
            h.assertValueEqual(h.getBlockState(FLOOR.above()).getValue(MachineTopBlock.SALINE), 3, "the urn's broth (the top cell)");
            h.assertTrue(lamp(h, FLOOR) != MachineLamp.OFF, "the lamp with a head seated");
            CompoundTag tag = array.getUpdateTag(h.getLevel().registryAccess());
            h.assertTrue(tag.contains("Items") && tag.getBoolean("fed"), "the seats and the feeding in the tag: " + tag);
            h.succeed();
        });
    }

    /** A villager hired with no wage at hand counts from the day it was hired, not from the world's first day; a stone is refused. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 70)
    public static void aMountedHeadCountsItsDaysFromItsHiring(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.EMPLOYEE_MOUNT.get());
        var mount = (com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity) h.getBlockEntity(FLOOR);
        h.getLevel().setDayTime(24000L * 40 + 1000);
        ServerPlayer p = player(h, FLOOR.south(2));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        use(h, p, FLOOR, Direction.NORTH);
        String said = Refusals.lastDelivered(p);
        var villager = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, FLOOR.above(3));
        villager.setVillagerData(villager.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN).setLevel(1));
        ItemStack head = com.coolfreeze23.headinajar.item.JarredHeadItem.of(villager);
        villager.discard();
        p.setItemInHand(InteractionHand.MAIN_HAND, head);
        use(h, p, FLOOR, Direction.NORTH);
        leave(h, p);
        h.assertValueEqual(said, "mount.needs_villager", "a stone at the mount");
        h.assertFalse(mount.head().isEmpty(), "the villager was not hired");
        h.assertTrue(mount.daysMissed() <= 1, "a head hired today counts " + mount.daysMissed() + " days unpaid");
        h.runAfterDelay(55, () -> {
            // after the refusal's red (40 ticks)
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.OFF, "an unpaid employee's lamp");
            h.assertTrue(mount.getUpdateTag(h.getLevel().registryAccess()).contains("Items"), "the head in the update tag");
            h.succeed();
        });
    }

    /** A mast routes, a jammer spins up: each tells its client what it does (the next hop; jamming), and its lamp says so. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 40)
    public static void theStationsShowRoutingAndJamming(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.RELAY_MAST.get());
        h.setBlock(FLOOR.east(3), ModRegistry.SIGNAL_JAMMER.get());
        var mast = (com.coolfreeze23.headinajar.block.PowerBlockEntities.Station) h.getBlockEntity(FLOOR);
        var jammer = (com.coolfreeze23.headinajar.block.PowerBlockEntities.Station) h.getBlockEntity(FLOOR.east(3));
        mast.energy().set(50_000);
        jammer.energy().set(50_000);
        h.runAfterDelay(3, () -> {
            h.assertTrue(isTop(h, FLOOR.above()), "the mast's upper half");
            h.assertFalse(isTop(h, FLOOR.east(3).above()), "the jammer is one block, with no top cell");
            long now = h.getLevel().getGameTime();
            mast.route(h.absolutePos(FLOOR.north(5)), now);
            jammer.jammed(now);
        });
        h.runAfterDelay(25, () -> {
            CompoundTag m = mast.getUpdateTag(h.getLevel().registryAccess());
            CompoundTag j = jammer.getUpdateTag(h.getLevel().registryAccess());
            h.assertTrue(m.contains("aim") && m.getBoolean("carry") && m.getBoolean("on"), "the mast's tag: " + m);
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.WORK, "a carrying mast's lamp");
            h.assertTrue(j.getBoolean("on"), "the jammer's tag: " + j);
            h.succeed();
        });
    }

    /** A turret with a blaze's head: its target is told to the clients, its shot is an event (the charge), its top cell stands. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 80)
    public static void theTurretTellsItsTargetAndShots(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.TURRET_PEDESTAL.get());
        var turret = (com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity) h.getBlockEntity(FLOOR);
        var blaze = h.spawn(net.minecraft.world.entity.EntityType.BLAZE, FLOOR.above(4));
        turret.items().setStackInSlot(0, com.coolfreeze23.headinajar.item.JarredHeadItem.of(blaze));
        blaze.discard();
        turret.energy().set(40_000);
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, FLOOR.north(5));
        zombie.setNoAi(true);
        h.runAfterDelay(50, () -> {
            CompoundTag tag = turret.getUpdateTag(h.getLevel().registryAccess());
            h.assertTrue(isTop(h, FLOOR.above()), "the turret's upper half");
            h.assertValueEqual(tag.getInt("target"), zombie.getId(), "the target in the tag");
            h.assertTrue(turret.kit().since(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity.EV_SHOT, h.getLevel().getGameTime()) < 60, "no shot told");
            h.assertValueEqual(turret.kit().eventParam(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity.EV_SHOT),
                    com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity.CHARGE, "the blaze's charge");
            zombie.discard();
            h.succeed();
        });
    }

    /** The Cloakroom with nothing in reach answers (no refusal); empty, its lamp is ready; its top cell stands. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 30)
    public static void theCloakroomAnswersWithNothingToCheckIn(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.BODY_LOCKER.get());
        h.runAfterDelay(3, () -> {
            ServerPlayer p = player(h, FLOOR.south(2));
            use(h, p, FLOOR, Direction.NORTH);
            boolean refused = !Refusals.delivered(p).isEmpty();
            leave(h, p);
            h.assertFalse(refused, "a click with no body in reach was refused");
            h.assertValueEqual(lamp(h, FLOOR), MachineLamp.READY, "an empty kiosk's lamp");
            h.assertTrue(isTop(h, FLOOR.above()), "the kiosk's upper half");
            h.succeed();
        });
    }

    /** Lightning in the rain near a Relay Mast: its dish flinches (an event its clients play). */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 30)
    public static void aMastFlinchesAtLightning(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.RELAY_MAST.get());
        var mast = (com.coolfreeze23.headinajar.block.PowerBlockEntities.Station) h.getBlockEntity(FLOOR);
        h.getLevel().setWeatherParameters(0, 6000, true, false);
        var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(h.getLevel());
        var at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.east(6)));
        bolt.moveTo(at.x, at.y, at.z);
        bolt.setVisualOnly(true);
        h.getLevel().addFreshEntity(bolt);
        h.runAfterDelay(3, () -> {
            h.getLevel().setWeatherParameters(6000, 0, false, false);
            h.assertTrue(mast.kit().since(com.coolfreeze23.headinajar.block.PowerBlockEntities.Station.EV_FLINCH, h.getLevel().getGameTime()) < 10, "the mast did not flinch");
            h.succeed();
        });
    }
}
