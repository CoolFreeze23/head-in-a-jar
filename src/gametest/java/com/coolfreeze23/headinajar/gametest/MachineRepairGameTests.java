package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity;
import com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity;
import com.coolfreeze23.headinajar.block.GalvanicPileBlockEntity;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.block.LabMachines;
import com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity;
import com.coolfreeze23.headinajar.block.PowerBlockEntities;
import com.coolfreeze23.headinajar.block.SalineMixerBlockEntity;
import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneArchive;
import com.coolfreeze23.headinajar.genetics.GeneSet;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.menu.LabMenu;
import com.coolfreeze23.headinajar.menu.MachineMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Machines, genetics and heads at work, through the paths real input takes: the click the client sends
 * ({@code gameMode.useItemOn} / {@code useItem}), the game's own block breaking, a hopper ticking in the world, the
 * menu's button and shift-click handlers, the socket payload's handler and the tick event.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class MachineRepairGameTests {
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

    /** A right-click on the top face of this block with whatever is in the main hand, as the client sends it. */
    private static void useBlock(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        BlockPos abs = h.absolutePos(pos);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
    }

    /** Breaking the block as a survival player does (the game's own break path, drops and all). */
    private static void breakBlock(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        p.gameMode.destroyBlock(h.absolutePos(pos));
    }

    /** One tick of this creature as the game posts it. */
    private static void tick(net.minecraft.world.entity.LivingEntity e) {
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(e));
    }

    private static Serum serum(Gene... genes) {
        return new Serum(java.util.Arrays.stream(genes).map(g -> new Serum.Entry(g, 1f)).toList(), 100);
    }

    /** Setup: a body that carries this serum (no injection roll). */
    private static void give(net.minecraft.world.entity.LivingEntity body, Serum serum) {
        Genetics.genes(body).serums.add(serum);
        Genetics.genes(body).changed();
    }

    private static int count(GameTestHelper h, BlockPos around, net.minecraft.world.item.Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(around)).inflate(2)).stream()
                .filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static ItemStack jarredHead(GameTestHelper h, EntityType<? extends Mob> type, VillagerProfession job) {
        Mob mob = h.spawn(type, FLOOR.above(3));
        if (mob instanceof Villager v && job != null) v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(1));
        if (mob instanceof Villager v) v.getOffers();
        ItemStack stack = JarredHeadItem.of(mob);
        mob.discard();
        return stack;
    }

    // --- 1. the wrench reaches the Tesla Node ---------------------------------------------------------------

    @GameTest(template = "platform")
    public static void wrenchLinksNodesByClicking(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos a = FLOOR.east(2), b = FLOOR.west(3);
        h.setBlock(a, ModRegistry.TESLA_NODE.get());
        h.setBlock(b, ModRegistry.TESLA_NODE.get());
        h.setBlock(FLOOR.north(2), ModRegistry.LEYDEN_JAR.get());
        var sender = (PowerBlockEntities.TeslaNode) h.getBlockEntity(a);
        // an empty hand still shows the charge, and says no more
        useBlock(h, p, a);
        h.assertTrue(Refusals.delivered(p).isEmpty() && sender.target() == null, "an empty hand did more than show the charge");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_WRENCH.get()));
        useBlock(h, p, a);
        useBlock(h, p, b);
        h.assertValueEqual(sender.target(), h.absolutePos(b), "plain clicks with the wrench did not link sender to receiver");
        // the same node twice; a node, then something that isn't one
        useBlock(h, p, b);
        useBlock(h, p, b);
        h.assertValueEqual(Refusals.lastDelivered(p), "wrench.same_node", "same node twice");
        useBlock(h, p, b);
        useBlock(h, p, FLOOR.north(2));
        h.assertValueEqual(Refusals.lastDelivered(p), "wrench.not_a_node", "a node, then a Leyden Jar");
        // sneak: unlink; again: nothing to unlink
        p.setShiftKeyDown(true);
        useBlock(h, p, a);
        h.assertTrue(sender.target() == null, "sneak-use did not unlink");
        useBlock(h, p, a);
        h.assertValueEqual(Refusals.lastDelivered(p), "wrench.not_linked", "unlinking an unlinked node");
        // sneak on a machine the wrench has no use for
        h.setBlock(FLOOR.south(2), ModRegistry.CENTRIFUGE.get());
        useBlock(h, p, FLOOR.south(2));
        h.assertValueEqual(Refusals.lastDelivered(p), "wrench.no_use", "wrench on a centrifuge");
        done(h, p);
    }

    // --- 2. gene effects end with their genes --------------------------------------------------------------

    @GameTest(template = "platform")
    public static void geneWashTakesTheEffectsAndTheLight(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        give(p, serum(Gene.GOLEM_FRAME, Gene.GLOW_CELLS));
        tick(p);
        BlockPos light = p.blockPosition().above();
        h.assertTrue(p.getAttribute(Attributes.ARMOR).getModifier(HeadInAJar.id("gene_golem_frame")) != null, "Golem Frame gave no armour");
        h.assertTrue(h.getLevel().getBlockState(light).is(Blocks.LIGHT), "Glow Cells made no light");
        ItemStack wash = new ItemStack(ModRegistry.GENE_WASH.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, wash);
        p.gameMode.useItem(p, h.getLevel(), wash, InteractionHand.MAIN_HAND);
        h.assertTrue(p.isUsingItem(), "a body could not drink the wash");
        p.getUseItem().finishUsingItem(h.getLevel(), p);
        tick(p);
        h.assertTrue(Genetics.genes(p).isEmpty(), "genes left after the wash");
        h.assertTrue(p.getAttribute(Attributes.ARMOR).getModifier(HeadInAJar.id("gene_golem_frame")) == null, "armour left after the wash");
        h.assertFalse(h.getLevel().getBlockState(light).is(Blocks.LIGHT), "the light stayed after the wash");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void detachingTakesTheEffectsAndAJarCantWash(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        give(p, serum(Gene.EQUINE_GAIT, Gene.GLOW_CELLS));
        var gait = HeadInAJar.id("gene_equine_gait");
        tick(p);
        BlockPos light = p.blockPosition().above();
        h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(gait) != null, "Equine Gait gave no speed");
        FormActions.detach(p, 3);
        tick(p);
        h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(gait) == null, "the jar kept the body's speed");
        h.assertFalse(h.getLevel().getBlockState(light).is(Blocks.LIGHT), "the light stayed with the jar");
        // a Gene Wash drunk as a jar (one with an arm, so it can hold the bottle): refused, and the drink doesn't start
        FormActions.state(p).setModule(0, new ItemStack(ModRegistry.module(com.coolfreeze23.headinajar.module.ModuleType.MANIPULATOR_ARM)));
        ItemStack wash = new ItemStack(ModRegistry.GENE_WASH.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, wash);
        p.gameMode.useItem(p, h.getLevel(), wash, InteractionHand.MAIN_HAND);
        h.assertFalse(p.isUsingItem(), "a jar started drinking");
        h.assertValueEqual(Refusals.lastDelivered(p), "gene.wash_jar", "jar wash line");
        h.assertTrue(wash.getCount() == 1, "the wash was used up");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void glowLeavesWithItsBody(GameTestHelper h) {
        var pig = h.spawn(EntityType.PIG, FLOOR);
        give(pig, serum(Gene.GLOW_CELLS));
        tick(pig);
        BlockPos light = pig.blockPosition().above();
        h.assertTrue(h.getLevel().getBlockState(light).is(Blocks.LIGHT), "no light over the glowing pig");
        h.assertTrue(pig.getPersistentData().contains(com.coolfreeze23.headinajar.genetics.GeneEffects.GLOW_AT), "light not recorded on the body");
        // the body leaves the level (killed, unloaded): the light goes with it
        pig.discard();
        h.assertFalse(h.getLevel().getBlockState(light).is(Blocks.LIGHT), "the light outlived its body");
        // a light left by an earlier unload is found again from the body's record and taken away
        var cow = h.spawn(EntityType.COW, FLOOR.east(3));
        BlockPos stray = h.absolutePos(FLOOR.east(3).above(3));
        h.getLevel().setBlock(stray, Blocks.LIGHT.defaultBlockState(), 3);
        cow.getPersistentData().putString(com.coolfreeze23.headinajar.genetics.GeneEffects.GLOW_AT, h.getLevel().dimension().location() + "|" + stray.asLong());
        give(cow, serum(Gene.GLOW_CELLS));
        tick(cow);
        h.assertFalse(h.getLevel().getBlockState(stray).is(Blocks.LIGHT), "the stray light was not cleaned when its body came back");
        h.succeed();
    }

    // --- 3. pipes and hoppers --------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void hoppersTakeOutputsAndLeaveInputs(GameTestHelper h) {
        BlockPos machine = FLOOR.above();
        h.setBlock(FLOOR, Blocks.HOPPER);
        h.setBlock(machine, ModRegistry.CENTRIFUGE.get());
        var c = (LabMachines.Centrifuge) h.getBlockEntity(machine);
        c.items().setStackInSlot(0, GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), EntityType.SPIDER, 70));
        c.items().setStackInSlot(1, GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), EntityType.PIG, 50));
        // a Cerebral Array's heads stay where they are
        BlockPos array = FLOOR.east(3).above();
        h.setBlock(FLOOR.east(3), Blocks.HOPPER);
        h.setBlock(array, ModRegistry.CEREBRAL_ARRAY.get());
        var rack = (CerebralArrayBlockEntity) h.getBlockEntity(array);
        rack.items().setStackInSlot(0, jarredHead(h, EntityType.ZOMBIE, null));
        // a hopper above the Leech Jar can't fill it with cobblestone
        h.setBlock(FLOOR.west(3), ModRegistry.LEECH_JAR.get());
        h.setBlock(FLOOR.west(3).above(), Blocks.HOPPER);
        var top = (net.minecraft.world.level.block.entity.HopperBlockEntity) h.getBlockEntity(FLOOR.west(3).above());
        top.setItem(0, new ItemStack(Items.COBBLESTONE));
        h.runAfterDelay(40, () -> {
            var below = (net.minecraft.world.level.block.entity.HopperBlockEntity) h.getBlockEntity(FLOOR);
            h.assertTrue(c.items().getStackInSlot(0).is(ModRegistry.BLOOD_SAMPLE.get()), "the hopper pulled the input sample out");
            h.assertTrue(c.items().getStackInSlot(1).isEmpty() && below.getItem(0).is(ModRegistry.DNA_FRACTION.get()), "the hopper did not take the output");
            h.assertFalse(rack.items().getStackInSlot(0).isEmpty(), "a hopper pulled a head out of the Array");
            h.assertTrue(top.getItem(0).is(Items.COBBLESTONE), "cobblestone went into the Leech Jar");
            // the rule for every side: inputs from the top and sides, never from below; outputs never put in
            var fromBelow = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(machine), Direction.DOWN);
            var fromSide = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(machine), Direction.NORTH);
            ItemStack sample = GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), EntityType.SPIDER, 70);
            h.assertTrue(fromBelow.insertItem(0, sample, true).getCount() == 1, "an input was filled from below");
            h.assertTrue(fromSide.insertItem(0, sample, true).isEmpty(), "an input was not filled from the side");
            h.assertTrue(fromSide.insertItem(1, GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), EntityType.PIG, 50), true).getCount() == 1, "an output was filled");
            h.assertTrue(fromSide.extractItem(0, 1, true).isEmpty(), "an input was emptied");
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void socketGoesQuietWhenItsHeadIsGone(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        var socket = (NeuralSocketBlockEntity) h.getBlockEntity(FLOOR);
        socket.items().setStackInSlot(0, jarredHead(h, EntityType.VILLAGER, null));
        h.runAfterDelay(5, () -> {
            h.assertTrue(socket.output(Direction.NORTH) > 0, "the villager program gave no signal");
            socket.items().setStackInSlot(0, ItemStack.EMPTY);
            h.runAfterDelay(3, () -> {
                for (Direction d : Direction.values()) h.assertTrue(socket.output(d) == 0, d + " still lit with no head");
                h.succeed();
            });
        });
    }

    // --- 4. shift-click --------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void shiftClickSavesAndSkipsOutputs(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(2));
        h.setBlock(FLOOR, ModRegistry.SALINE_MIXER.get());
        var mixer = (SalineMixerBlockEntity) h.getBlockEntity(FLOOR);
        mixer.items().setStackInSlot(SalineMixerBlockEntity.SLOT_SUGAR, new ItemStack(Items.SUGAR, 1));
        mixer.items().setStackInSlot(SalineMixerBlockEntity.SLOT_BOTTLE_OUT, new ItemStack(ModRegistry.SALINE_BOTTLE.get(), 2));
        var menu = (MachineMenu) mixer.createMenu(1, p.getInventory(), p);
        int hotbar = menu.slots.size() - 9;
        p.getInventory().setItem(0, new ItemStack(Items.SUGAR, 5));
        p.getInventory().setItem(1, new ItemStack(ModRegistry.SALINE_BOTTLE.get(), 3));
        var chunk = h.getLevel().getChunkAt(h.absolutePos(FLOOR));
        chunk.setUnsaved(false);
        menu.quickMoveStack(p, hotbar);
        h.assertTrue(mixer.items().getStackInSlot(SalineMixerBlockEntity.SLOT_SUGAR).getCount() == 6, "sugar did not merge in");
        h.assertTrue(chunk.isUnsaved(), "a merged shift-click did not mark the mixer to be saved");
        menu.quickMoveStack(p, hotbar + 1);
        h.assertTrue(mixer.items().getStackInSlot(SalineMixerBlockEntity.SLOT_BOTTLE_OUT).getCount() == 2, "shift-click topped up the output");
        h.assertTrue(p.getInventory().getItem(1).getCount() == 3, "the bottles left the inventory");
        done(h, p);
    }

    // --- 5. two players, one machine --------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void splicerBuildsFromThePressersLibrary(GameTestHelper h) {
        ServerPlayer a = player(h, FLOOR.east(2)), b = player(h, FLOOR.west(2));
        GeneArchive.get(h.getLevel().getServer()).add(GeneArchive.key(a), Gene.SPIDER_CLIMB, 100);
        h.setBlock(FLOOR, ModRegistry.GENE_SPLICER.get());
        var s = (LabMachines.Splicer) h.getBlockEntity(FLOOR);
        s.energy().set(40_000);
        s.items().setStackInSlot(LabMachines.Splicer.SLOT_GEL, new ItemStack(ModRegistry.BIO_GEL.get()));
        s.items().setStackInSlot(LabMachines.Splicer.SLOT_BOTTLE, new ItemStack(Items.GLASS_BOTTLE));
        var menuA = (LabMenu) s.createMenu(1, a.getInventory(), a);
        var menuB = (LabMenu) s.createMenu(2, b.getInventory(), b);
        menuA.clickMenuButton(a, LabMachines.Splicer.PICK + Gene.SPIDER_CLIMB.ordinal());
        h.assertTrue(s.strand().equals(List.of(Gene.SPIDER_CLIMB)), "A could not pick a read gene");
        h.assertFalse(menuB.clickMenuButton(b, LabMachines.Splicer.SPLICE), "B spliced a gene B never read");
        h.assertValueEqual(Refusals.lastDelivered(b), "lab.splicer.unread", "B's line");
        h.assertFalse(s.items().getStackInSlot(LabMachines.Splicer.SLOT_GEL).isEmpty(), "B's refused splice ate the gel");
        h.assertTrue(menuA.clickMenuButton(a, LabMachines.Splicer.SPLICE), "A could not splice A's own gene");
        h.assertFalse(menuB.clickMenuButton(b, LabMachines.Splicer.PICK + 100 + Gene.GILLS.ordinal()), "B changed a running strand");
        h.assertValueEqual(Refusals.lastDelivered(b), "lab.splicer.running", "running line");
        done(h, a, b);
    }

    @GameTest(template = "platform")
    public static void labButtonsSayWhy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(2));
        h.setBlock(FLOOR, ModRegistry.DNA_SEQUENCER.get());
        var seq = (LabMachines.Sequencer) h.getBlockEntity(FLOOR);
        var menu = (LabMenu) seq.createMenu(1, p.getInventory(), p);
        h.assertFalse(menu.clickMenuButton(p, LabMachines.Sequencer.AUTO), "Auto ran with nothing in the slot");
        h.assertValueEqual(Refusals.lastDelivered(p), "lab.sequencer.empty", "empty sequencer");
        h.setBlock(FLOOR.west(2), ModRegistry.INJECTION_RIG.get());
        var rig = (LabMachines.InjectionRig) h.getBlockEntity(FLOOR.west(2));
        var rigMenu = (LabMenu) rig.createMenu(2, p.getInventory(), p);
        rigMenu.clickMenuButton(p, LabMachines.InjectionRig.INJECT);
        h.assertValueEqual(Refusals.lastDelivered(p), "lab.rig.empty", "empty rig");
        ItemStack cassette = new ItemStack(ModRegistry.SERUM_CASSETTE.get());
        serum(Gene.CAT_EYES).writeTo(cassette);
        rig.items().setStackInSlot(0, cassette);
        rigMenu.clickMenuButton(p, LabMachines.InjectionRig.INJECT);
        h.assertValueEqual(Refusals.lastDelivered(p), "lab.no_power", "unpowered rig (it used to name the Gantry's 10 kFE)");
        // the Body Printer's Print button
        h.setBlock(FLOOR.north(2), ModRegistry.BODY_PRINTER.get());
        var printer = (BodyPrinterBlockEntity) h.getBlockEntity(FLOOR.north(2));
        var printMenu = printer.createMenu(3, p.getInventory(), p);
        printMenu.clickMenuButton(p, 0);
        h.assertValueEqual(Refusals.lastDelivered(p), "printer.has_head", "a whole player pressed Print");
        done(h, p);
    }

    // --- 6. the Neural Socket never strands a jar --------------------------------------------------------------

    @GameTest(template = "platform")
    public static void brokenSocketLetsTheJarGo(GameTestHelper h) {
        ServerPlayer jar = player(h, FLOOR.east(1)), other = player(h, FLOOR.west(3));
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        FormActions.detach(jar, 3);
        useBlock(h, jar, FLOOR);
        h.assertTrue(FormActions.state(jar).form() == Form.SOCKET, "a bare jar's click did not plug it in");
        breakBlock(h, other, FLOOR);
        h.assertTrue(FormActions.state(jar).form() == Form.JAR, "the jar is still in socket form after the socket broke");
        done(h, jar, other);
    }

    @GameTest(template = "platform")
    public static void socketKeysWithNoSocketUnplugAndRelogUnplugs(GameTestHelper h) {
        ServerPlayer jar = player(h, FLOOR.east(1));
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        FormActions.detach(jar, 3);
        useBlock(h, jar, FLOOR);
        // the socket carried off as a carry mod does it: block entity first, then the block, no break
        h.getLevel().removeBlockEntity(h.absolutePos(FLOOR));
        h.getLevel().setBlock(h.absolutePos(FLOOR), Blocks.AIR.defaultBlockState(), 2 | 16);
        h.assertTrue(FormActions.state(jar).form() == Form.SOCKET, "setup: still plugged in");
        NeuralSocketBlockEntity.input(jar, 0, 0, 7);
        h.assertTrue(FormActions.state(jar).form() == Form.JAR, "a level key with no socket left the jar stuck");
        // relogging in socket form
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        jar.moveTo(Vec3.atBottomCenterOf(h.absolutePos(FLOOR.east(1))));
        useBlock(h, jar, FLOOR);
        h.assertTrue(FormActions.state(jar).form() == Form.SOCKET, "setup: plugged in again");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(jar));
        h.assertTrue(FormActions.state(jar).form() == Form.JAR, "logging in left the jar in socket form");
        done(h, jar);
    }

    // --- 7. progress and contents ----------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void brokenPrinterGivesBackTheRecipe(GameTestHelper h) {
        ServerPlayer jar = player(h, FLOOR.east(2)), other = player(h, FLOOR.west(3));
        h.setBlock(FLOOR, ModRegistry.BODY_PRINTER.get());
        var printer = (BodyPrinterBlockEntity) h.getBlockEntity(FLOOR);
        printer.items().setStackInSlot(0, new ItemStack(Items.ROTTEN_FLESH, 16));
        printer.items().setStackInSlot(1, new ItemStack(Items.BONE_BLOCK, 8));
        printer.items().setStackInSlot(2, new ItemStack(Items.REDSTONE_BLOCK, 4));
        printer.items().setStackInSlot(3, new ItemStack(Items.SLIME_BALL, 8));
        printer.saline().fill(new net.neoforged.neoforge.fluids.FluidStack(com.coolfreeze23.headinajar.registry.ModFluids.SALINE.source.get(), 2000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        FormActions.detach(jar, 3);
        printer.createMenu(1, jar.getInventory(), jar).clickMenuButton(jar, 0);
        h.assertTrue(printer.printing() && printer.items().getStackInSlot(0).isEmpty(), "setup: the print did not start");
        printer.createMenu(2, jar.getInventory(), jar).clickMenuButton(jar, 0);
        h.assertValueEqual(Refusals.lastDelivered(jar), "printer.busy", "Print while printing");
        breakBlock(h, other, FLOOR);
        h.assertTrue(count(h, FLOOR, Items.ROTTEN_FLESH) == 16 && count(h, FLOOR, Items.BONE_BLOCK) == 8
                && count(h, FLOOR, Items.REDSTONE_BLOCK) == 4 && count(h, FLOOR, Items.SLIME_BALL) == 8, "the consumed recipe was lost");
        done(h, jar, other);
    }

    @GameTest(template = "platform")
    public static void machinesPauseInsteadOfStartingOver(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CENTRIFUGE.get());
        var c = (LabMachines.Centrifuge) h.getBlockEntity(FLOOR);
        c.items().setStackInSlot(0, GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), EntityType.SPIDER, 70));
        c.energy().set(LabMachines.Centrifuge.FE_PER_TICK * 50);
        for (int i = 0; i < 60; i++) LabMachines.Centrifuge.serverTick(h.getLevel(), c.getBlockPos(), c.getBlockState(), c);
        h.assertTrue(c.progress() == 50 && c.stall() == MachineMenu.NO_POWER, "the centrifuge did not pause on a flat battery: " + c.progress());
        c.energy().set(20_000);
        for (int i = 0; i < LabMachines.Centrifuge.TICKS - 50; i++) LabMachines.Centrifuge.serverTick(h.getLevel(), c.getBlockPos(), c.getBlockState(), c);
        h.assertTrue(c.items().getStackInSlot(1).is(ModRegistry.DNA_FRACTION.get()), "the spin did not finish from where it paused");
        // the mixer: an ingredient taken mid-batch, then put back
        h.setBlock(FLOOR.east(2), ModRegistry.SALINE_MIXER.get());
        var m = (SalineMixerBlockEntity) h.getBlockEntity(FLOOR.east(2));
        m.energy().set(20_000);
        m.water().fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 2000), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        m.items().setStackInSlot(SalineMixerBlockEntity.SLOT_SUGAR, new ItemStack(Items.SUGAR));
        m.items().setStackInSlot(SalineMixerBlockEntity.SLOT_KELP, new ItemStack(Items.KELP));
        m.items().setStackInSlot(SalineMixerBlockEntity.SLOT_REDSTONE, new ItemStack(Items.REDSTONE));
        for (int i = 0; i < 30; i++) SalineMixerBlockEntity.serverTick(h.getLevel(), m.getBlockPos(), m.getBlockState(), m);
        ItemStack sugar = m.items().extractItem(SalineMixerBlockEntity.SLOT_SUGAR, 1, false);
        for (int i = 0; i < 10; i++) SalineMixerBlockEntity.serverTick(h.getLevel(), m.getBlockPos(), m.getBlockState(), m);
        h.assertTrue(m.progress() == 30, "the mixer started over when the sugar went: " + m.progress());
        m.items().setStackInSlot(SalineMixerBlockEntity.SLOT_SUGAR, sugar);
        for (int i = 0; i < SalineMixerBlockEntity.BATCH_TICKS - 30; i++) SalineMixerBlockEntity.serverTick(h.getLevel(), m.getBlockPos(), m.getBlockState(), m);
        h.assertTrue(m.saline().getFluidAmount() == 1000, "the batch did not finish from where it paused");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void sequencerSpendsNothingOnAnUnreadableFraction(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(2));
        h.setBlock(FLOOR, ModRegistry.DNA_SEQUENCER.get());
        var s = (LabMachines.Sequencer) h.getBlockEntity(FLOOR);
        s.energy().set(10_000);
        s.items().setStackInSlot(0, GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), EntityType.CAVE_SPIDER, 80));
        var menu = (LabMenu) s.createMenu(1, p.getInventory(), p);
        h.assertTrue(menu.clickMenuButton(p, LabMachines.Sequencer.AUTO), "Auto did not start");
        // a fraction from a creature this world doesn't have (a mod removed since)
        ItemStack lost = new ItemStack(ModRegistry.DNA_FRACTION.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("Species", "gone_mod:gone_creature");
        tag.putInt("Quality", 80);
        CustomData.set(DataComponents.CUSTOM_DATA, lost, tag);
        s.items().setStackInSlot(0, lost);
        for (int i = 0; i < 40; i++) LabMachines.Sequencer.serverTick(h.getLevel(), s.getBlockPos(), s.getBlockState(), s);
        h.assertTrue(s.energy().getEnergyStored() == 10_000, "an unreadable fraction burned power");
        h.assertTrue(s.stall() == MachineMenu.UNREADABLE, "the screen isn't told why");
        h.assertFalse(menu.clickMenuButton(p, LabMachines.Sequencer.CALL), "bases were called on an unreadable fraction");
        h.assertValueEqual(Refusals.lastDelivered(p), "lab.sequencer.unreadable", "unreadable line");
        h.assertTrue(s.energy().getEnergyStored() == 10_000, "calling bases on it cost power");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void jarredVillagerKeepsRestockAndTrades(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR), other = player(h, FLOOR.west(3));
        ItemStack head = jarredHead(h, EntityType.VILLAGER, VillagerProfession.LIBRARIAN);
        // a head that restocked a moment ago, somewhere else
        long restocked = Math.max(1, h.getLevel().getGameTime() - 5);
        CustomData.update(DataComponents.CUSTOM_DATA, head, t -> t.putLong("LastRestock", restocked));
        p.setItemInHand(InteractionHand.MAIN_HAND, head);
        useBlock(h, p, FLOOR.east(2).below());
        BlockPos at = FLOOR.east(2);
        h.assertTrue(h.getBlockEntity(at) instanceof JarredHeadBlockEntity, "the head was not placed by a click");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        useBlock(h, p, at);
        h.assertTrue(p.containerMenu instanceof MerchantMenu, "the jarred villager did not trade");
        var offers = ((MerchantMenu) p.containerMenu).getOffers();
        h.assertFalse(offers.isEmpty(), "setup: the librarian has no trades");
        offers.get(0).increaseUses();
        breakBlock(h, other, at);
        h.assertFalse(p.containerMenu instanceof MerchantMenu, "the trade screen stayed open on a broken head");
        ItemEntity drop = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(at)).inflate(2)).stream()
                .filter(e -> e.getItem().is(ModRegistry.JARRED_HEAD.get())).findFirst().orElse(null);
        h.assertTrue(drop != null, "no head dropped");
        CompoundTag data = drop.getItem().get(DataComponents.CUSTOM_DATA).copyTag();
        h.assertTrue(data.getLong("LastRestock") == restocked, "the restock time was not kept: " + data.getLong("LastRestock") + " vs " + restocked);
        int uses = data.getCompound("Entity").getCompound("Offers").getList("Recipes", 10).getCompound(0).getInt("uses");
        h.assertTrue(uses == 1, "the trade made before the break was lost (uses " + uses + ")");
        done(h, p, other);
    }

    // --- 8. old and odd worlds ------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void unknownGenesAndSmallSaves(GameTestHelper h) {
        CompoundTag set = new CompoundTag();
        ListTag mutations = new ListTag();
        mutations.add(StringTag.valueOf("no_such_gene"));
        mutations.add(StringTag.valueOf("gills"));
        set.put("mutations", mutations);
        ListTag serums = new ListTag();
        CompoundTag s = new CompoundTag();
        ListTag genes = new ListTag();
        for (String id : new String[]{"renamed_gene", "cat_eyes"}) {
            CompoundTag e = new CompoundTag();
            e.putString("gene", id);
            e.putFloat("potency", 1);
            genes.add(e);
        }
        s.put("genes", genes);
        s.putInt("stability", 90);
        serums.add(s);
        set.put("serums", serums);
        var parsed = GeneSet.CODEC.parse(NbtOps.INSTANCE, set).result();
        h.assertTrue(parsed.isPresent(), "a body with an unknown gene failed to load");
        h.assertTrue(parsed.get().mutations.equals(List.of(Gene.GILLS)) && parsed.get().serums.get(0).genes().size() == 1
                && parsed.get().serums.get(0).genes().get(0).gene() == Gene.CAT_EYES, "unknown genes were not skipped");
        // a splicer saved with a gene that no longer exists
        h.setBlock(FLOOR, ModRegistry.GENE_SPLICER.get());
        var splicer = (LabMachines.Splicer) h.getBlockEntity(FLOOR);
        CompoundTag save = splicer.saveWithoutMetadata(h.getLevel().registryAccess());
        save.putString("Gene0", "no_such_gene");
        save.putString("Gene1", "gills");
        splicer.loadWithComponents(save, h.getLevel().registryAccess());
        h.assertTrue(splicer.strand().equals(List.of(Gene.GILLS)), "the splicer's strand did not load: " + splicer.strand());
        // a mixer saved by a version with fewer slots keeps its own slot count
        h.setBlock(FLOOR.east(2), ModRegistry.SALINE_MIXER.get());
        var mixer = (SalineMixerBlockEntity) h.getBlockEntity(FLOOR.east(2));
        CompoundTag old = mixer.saveWithoutMetadata(h.getLevel().registryAccess());
        var small = new net.neoforged.neoforge.items.ItemStackHandler(3);
        small.setStackInSlot(0, new ItemStack(Items.SUGAR, 4));
        old.put("Items", small.serializeNBT(h.getLevel().registryAccess()));
        mixer.loadWithComponents(old, h.getLevel().registryAccess());
        h.assertTrue(mixer.items().getSlots() == 5 && mixer.items().getStackInSlot(0).getCount() == 4, "slots after a small save: " + mixer.items().getSlots());
        SalineMixerBlockEntity.serverTick(h.getLevel(), mixer.getBlockPos(), mixer.getBlockState(), mixer);
        h.succeed();
    }

    // --- 9. the owner's pack ---------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void carryModsAreKeptOff(GameTestHelper h) {
        var blocks = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "relocation_not_supported"));
        var entities = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "capturing_not_supported"));
        for (var block : List.of(ModRegistry.NEURAL_SOCKET.get(), ModRegistry.BODY_PRINTER.get(), ModRegistry.JARRED_HEAD_BLOCK.get(), ModRegistry.TESLA_NODE.get())) {
            h.assertTrue(block.defaultBlockState().is(blocks), block + " can be carried off");
        }
        for (var type : List.of(ModRegistry.HEADLESS_BODY.get(), ModRegistry.HEAD_JAR.get(), ModRegistry.HOPPER.get(), ModRegistry.ROVER.get())) {
            h.assertTrue(type.is(entities), type + " can be picked up");
        }
        h.succeed();
    }

    // --- 10. heads read in place, synced lean ----------------------------------------------------------------

    @GameTest(template = "platform")
    public static void turretTakesWorkingHeadsAndSyncsOnlyTheirLooks(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(2));
        h.setBlock(FLOOR, ModRegistry.TURRET_PEDESTAL.get());
        var turret = (TurretPedestalBlockEntity) h.getBlockEntity(FLOOR);
        p.setItemInHand(InteractionHand.MAIN_HAND, jarredHead(h, EntityType.PIG, null));
        useBlock(h, p, FLOOR);
        h.assertTrue(turret.head().isEmpty(), "a pig head went on a turret");
        h.assertValueEqual(Refusals.lastDelivered(p), "turret.no_job", "pig head line");
        p.setItemInHand(InteractionHand.MAIN_HAND, jarredHead(h, EntityType.SKELETON, null));
        useBlock(h, p, FLOOR);
        h.assertTrue(turret.headInfo().type() == EntityType.SKELETON, "the skeleton head was not fitted");
        p.setItemInHand(InteractionHand.MAIN_HAND, jarredHead(h, EntityType.ZOMBIE, null));
        useBlock(h, p, FLOOR);
        h.assertValueEqual(Refusals.lastDelivered(p), "turret.occupied", "second head line");
        CompoundTag sync = turret.getUpdateTag(h.getLevel().registryAccess());
        CompoundTag item = sync.getCompound("Items").getList("Items", 10).getCompound(0);
        CompoundTag entity = item.getCompound("components").getCompound("minecraft:custom_data").getCompound("Entity");
        h.assertTrue(entity.isEmpty() || entity.getAllKeys().equals(java.util.Set.of("VillagerData")), "the whole stored mob went to clients: " + entity.getAllKeys());
        done(h, p);
    }

    // --- 11. the recipe pairs ---------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void recipePairsAreDistinct(GameTestHelper h) {
        var level = h.getLevel();
        ItemStack lens = new ItemStack(ModRegistry.LENS.get());
        String[][] pairs = {
                {"golden_carrot", "collar_night_optics", "headinajar:servo", "mecha_night_optics"},
                {"glow_ink_sac", "collar_thermal_overlay", "headinajar:servo", "mecha_thermal_imaging"},
                {"headinajar:synapse_chip", "mecha_bio_scanner", "redstone", "mecha_target_lock"}};
        for (String[] pair : pairs) {
            ItemStack middle = new ItemStack(item(pair[0])), under = new ItemStack(item(pair[2]));
            var two = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(1, 2, List.of(lens, middle)), level);
            var three = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(1, 3, List.of(lens, middle, under)), level);
            h.assertTrue(two.isPresent() && two.get().id().getPath().equals(pair[1]), "lens over " + pair[0] + " gave " + two.map(r -> r.id().toString()).orElse("nothing"));
            h.assertTrue(three.isPresent() && three.get().id().getPath().equals(pair[3]), "lens, " + pair[0] + ", " + pair[2] + " gave " + three.map(r -> r.id().toString()).orElse("nothing"));
        }
        h.succeed();
    }

    private static net.minecraft.world.item.Item item(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id.contains(":") ? ResourceLocation.parse(id) : ResourceLocation.withDefaultNamespace(id));
    }

    // --- 12. inputs that used to say nothing --------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void handsOnMachinesAreAnswered(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        // a water bucket on the Body Printer: refused, the menu stays shut, the bucket stays full
        h.setBlock(FLOOR.east(2), ModRegistry.BODY_PRINTER.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        useBlock(h, p, FLOOR.east(2));
        h.assertValueEqual(Refusals.lastDelivered(p), "machine.wrong_fluid", "water in the printer");
        h.assertTrue(p.getMainHandItem().is(Items.WATER_BUCKET) && p.containerMenu == p.inventoryMenu, "the bucket was taken or the menu opened");
        // a Saline Bottle pours in
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SALINE_BOTTLE.get()));
        useBlock(h, p, FLOOR.east(2));
        h.assertTrue(((BodyPrinterBlockEntity) h.getBlockEntity(FLOOR.east(2))).saline().getFluidAmount() == 250, "the bottle did not pour in");
        // a full Galvanic Pile
        h.setBlock(FLOOR.west(2), ModRegistry.GALVANIC_PILE.get());
        ((GalvanicPileBlockEntity) h.getBlockEntity(FLOOR.west(2))).fuel().setStackInSlot(0, new ItemStack(Items.ROTTEN_FLESH, 64));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ROTTEN_FLESH, 3));
        useBlock(h, p, FLOOR.west(2));
        h.assertValueEqual(Refusals.lastDelivered(p), "pile.full", "full pile");
        // a syringe on a colleague, a serum in the air
        ServerPlayer other = player(h, FLOOR.north(1));
        ItemStack syringe = new ItemStack(ModRegistry.SYRINGE.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, syringe);
        p.interactOn(other, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "gene.syringe_player", "syringe on a player");
        ItemStack cassette = new ItemStack(ModRegistry.SERUM_CASSETTE.get());
        serum(Gene.CAT_EYES).writeTo(cassette);
        p.setItemInHand(InteractionHand.MAIN_HAND, cassette);
        p.gameMode.useItem(p, h.getLevel(), cassette, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "gene.serum_self", "serum in the air");
        p.setItemInHand(InteractionHand.MAIN_HAND, syringe);
        p.gameMode.useItem(p, h.getLevel(), syringe, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "gene.syringe_air", "syringe in the air");
        // an empty socket clicked by a whole player; a head with no program
        h.setBlock(FLOOR.south(2), ModRegistry.NEURAL_SOCKET.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        useBlock(h, p, FLOOR.south(2));
        h.assertValueEqual(Refusals.lastDelivered(p), "socket.needs_head", "empty socket");
        p.setItemInHand(InteractionHand.MAIN_HAND, jarredHead(h, EntityType.SKELETON, null));
        useBlock(h, p, FLOOR.south(2));
        h.assertValueEqual(Refusals.lastDelivered(p), "socket.no_program", "skeleton in a socket");
        done(h, p, other);
    }
}
