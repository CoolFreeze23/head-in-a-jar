package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity;
import com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity;
import com.coolfreeze23.headinajar.block.LabMachines;
import com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity;
import com.coolfreeze23.headinajar.block.MachineBlock;
import com.coolfreeze23.headinajar.block.SeparationChairBlock;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Warnings;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.host.HostControl;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.LabMenu;
import com.coolfreeze23.headinajar.menu.SeparationChairMenu;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Nothing lethal or costly without a word first, and no input left unanswered. Each test takes the path the game takes:
 * a mob's own attack, a block the head stands in, the interaction a click sends ({@code interactOn}, {@code attack},
 * {@code useItemOn}), a menu button, the block and level ticks, a capability as a pipe asks for it.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ThirdRepairGameTests {
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

    /** Separated by the chair's own code, the head set down at {@code headAt}: a linked body walking about. */
    private static HeadJarEntity separate(GameTestHelper h, ServerPlayer p, BlockPos headAt) {
        h.assertTrue(FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(headAt)), null), "separate failed");
        return h.getEntities(ModRegistry.HEAD_JAR.get()).get(0);
    }

    /**
     * Listens for this player's death the way the game reads its line: last, once the death stands, before the combat
     * record is cleared. Holder [0] gets the line's key; unregister holder [1] when done.
     */
    private static Object[] deathLine(ServerPlayer p) {
        Object[] out = new Object[2];
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> listener = e -> {
            if (e.getEntity() != p) return;
            Component message = p.getCombatTracker().getDeathMessage();
            out[0] = message.getContents() instanceof TranslatableContents t ? t.getKey() : message.getString();
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class, listener);
        out[1] = listener;
        return out;
    }

    private static void stopListening(Object[] line) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(line[1]);
    }

    private static <T extends Mob> T host(GameTestHelper h, EntityType<T> type, BlockPos at, ServerPlayer sawyer) {
        T mob = h.spawn(type, at);
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "saw failed on " + type);
        return mob;
    }

    private static InteractionResult useBlock(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        BlockPos abs = h.absolutePos(pos);
        return p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.2, 0), Direction.UP, abs, false));
    }

    // --- 1. a jar knocked about is told, and its end is announced ------------------------------------------------

    @GameTest(template = "platform")
    public static void aJarKnockedAboutIsToldWhatGlassIsLeft(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east());
        zombie.setNoAi(true);
        int before = state(p).integrity();
        // the zombie's own swing
        zombie.doHurtTarget(p);
        h.assertTrue(state(p).integrity() < before, "the glass soaked nothing");
        h.assertTrue(Warnings.delivered(p).contains("jar.glass_hit"), "a knock was not said: " + Warnings.delivered(p));
        state(p).setIntegrity(3);
        p.invulnerableTime = 0;
        zombie.doHurtTarget(p);
        h.assertTrue(Warnings.delivered(p).contains("jar.glass_low"), "low glass was not said: " + Warnings.delivered(p));
        state(p).setIntegrity(0);
        p.invulnerableTime = 0;
        zombie.doHurtTarget(p);
        h.assertTrue(Warnings.delivered(p).contains("jar.glass_gone"), "no glass left was not said: " + Warnings.delivered(p));
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aJarBrokenByAZombieIsAnnouncedAsBroken(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east());
        zombie.setNoAi(true);
        state(p).setIntegrity(0);
        p.setHealth(1);
        Object[] line = deathLine(p);
        try {
            zombie.doHurtTarget(p);
        } finally {
            stopListening(line);
        }
        h.assertTrue(p.isDeadOrDying(), "the jar lived");
        h.assertValueEqual(line[0], "death.attack.headinajar.jar_smashed", "the death line");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void aHeadInTheFireCracksAPointASecondAndItsOwnerIsWarned(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        BlockPos at = FLOOR.east(2);
        h.setBlock(at.below(), Blocks.SOUL_SOIL);
        separate(h, p, at);
        // a fire that never burns out, where the head stands
        h.setBlock(at, Blocks.SOUL_FIRE);
        int[] shielded = {-1};
        h.runAfterDelay(61, () -> {
            int glass = state(p).integrity();
            h.assertTrue(glass >= 6 && glass <= 8, "three seconds in the fire left " + glass + " of 10");
            h.assertTrue(Warnings.delivered(p).contains("head.burning"), "its owner was not warned: " + Warnings.delivered(p));
            h.assertFalse(p.isDeadOrDying(), "the owner died in three seconds");
            // with the owner's Heat Shield fitted, the jar stands in the fire unharmed, as the jar they are would
            state(p).setModule(0, new ItemStack(ModRegistry.module(ModuleType.HEAT_SHIELD)));
            shielded[0] = state(p).integrity();
        });
        h.runAfterDelay(110, () -> {
            h.assertValueEqual(state(p).integrity(), shielded[0], "glass behind a Heat Shield");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void aHeadLostToTheFireIsAnnouncedAsBurned(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        BlockPos at = FLOOR.east(2);
        h.setBlock(at.below(), Blocks.SOUL_SOIL);
        HeadJarEntity head = separate(h, p, at);
        state(p).setIntegrity(1);
        Object[] line = deathLine(p);
        h.setBlock(at, Blocks.SOUL_FIRE);
        h.runAfterDelay(30, () -> {
            stopListening(line);
            h.assertTrue(head.isRemoved(), "the head did not break");
            h.assertTrue(p.isDeadOrDying(), "its owner lived on");
            h.assertValueEqual(line[0], "death.attack.headinajar.head_burned", "the death line");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void aWreckedMechaThrowsItsPilotClearWithGlassToSpare(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        MechaEntity m = h.spawn(ModRegistry.mechaType(MechaFrame.HOPPER), FLOOR.east(2));
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < MechaFrame.HOPPER.totalSlots(); i++) slots.add(ItemStack.EMPTY);
        slots.set(MechaFrame.HOPPER.firstIndex(MechaModule.Slot.LEGS), new ItemStack(ModRegistry.mechaModule(MechaModule.HYDRAULIC_LEGS)));
        m.setModules(slots);
        m.setOwner(p.getUUID());
        m.setBattery(100_000);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, m), "could not board");
        int max = state(p).maxIntegrity();
        state(p).setIntegrity(max);
        m.hurt(h.getLevel().damageSources().generic(), 10_000);
        h.assertTrue(m.isDeadOrDying() || m.isRemoved(), "the mecha survived");
        h.assertTrue(state(p).form() == Form.JAR, "the pilot is " + state(p).form());
        h.assertValueEqual(state(p).integrity(), max - 1, "glass after the wreck");
        h.assertTrue(Warnings.delivered(p).contains("mecha.wrecked"), "the pilot was not told: " + Warnings.delivered(p));
        done(h, p);
    }

    // --- 2. a sample never costs the specimen ------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aSyringeNeverTakesTheLastPoint(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east());
        cow.setNoAi(true);
        cow.setHealth(0.5f);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SYRINGE.get()));
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertTrue(cow.isAlive() && !cow.isDeadOrDying(), "the needle killed the cow");
        h.assertTrue(p.getInventory().hasAnyMatching(s -> s.is(ModRegistry.BLOOD_SAMPLE.get())), "no sample drawn");
        Cow healthy = h.spawn(EntityType.COW, FLOOR.west());
        healthy.setNoAi(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SYRINGE.get()));
        p.interactOn(healthy, InteractionHand.MAIN_HAND);
        h.assertTrue(healthy.getHealth() == healthy.getMaxHealth() - 1, "a healthy cow did not feel the needle: " + healthy.getHealth());
        done(h, p);
    }

    // --- 3. strangers ------------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aStrangerKnockingOnAPlacedHeadIsToldNo(GameTestHelper h) {
        ServerPlayer owner = player(h, FLOOR.west(3)), stranger = player(h, FLOOR.east(1));
        HeadJarEntity head = separate(h, owner, FLOOR.east(2));
        int glass = state(owner).integrity();
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        stranger.attack(head);
        h.assertValueEqual(state(owner).integrity(), glass, "the stranger cracked the glass");
        h.assertFalse(head.isRemoved(), "the head broke");
        h.assertValueEqual(Refusals.lastDelivered(stranger), "head.hit_not_yours", "the stranger's answer");
        done(h, owner, stranger);
    }

    @GameTest(template = "platform")
    public static void anUntrustedPlayerCannotOpenACorpse(GameTestHelper h) {
        ServerPlayer owner = player(h, FLOOR.west(3)), stranger = player(h, FLOOR.east(1));
        owner.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        FormActions.detach(owner, 3);
        HeadlessBody body = h.getEntities(ModRegistry.HEADLESS_BODY.get()).get(0);
        body.takeInventory(owner);
        body.hurt(h.getLevel().damageSources().generic(), 1000);
        h.assertTrue(body.isCorpse(), "no corpse");
        // the click the stranger's client sends: the interact event, then the corpse's own answer
        stranger.interactOn(body, InteractionHand.MAIN_HAND);
        h.assertTrue(stranger.containerMenu == stranger.inventoryMenu, "an untrusted player opened the corpse: " + stranger.containerMenu);
        h.assertValueEqual(Refusals.lastDelivered(stranger), "body.corpse_untrusted", "the stranger's answer");
        // trusted, the same click opens it
        HeadRegistry.get(owner).setTrust(owner.getUUID(), stranger.getUUID(), true);
        stranger.interactOn(body, InteractionHand.MAIN_HAND);
        h.assertTrue(stranger.containerMenu instanceof ChestMenu, "a trusted player could not open the corpse");
        HeadRegistry.get(owner).setTrust(owner.getUUID(), stranger.getUUID(), false);
        stranger.closeContainer();
        done(h, owner, stranger);
    }

    // --- 4. your own placed head takes saline and a patch ------------------------------------------------------------

    @GameTest(template = "platform")
    public static void salineAndAPatchMendYourOwnPlacedHead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(1));
        HeadJarEntity head = separate(h, p, FLOOR.east(2));
        state(p).setSaline(400);
        state(p).setIntegrity(4);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SALINE_BOTTLE.get()));
        p.interactOn(head, InteractionHand.MAIN_HAND);
        h.assertValueEqual(state(p).saline(), 650, "saline after the bottle");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.GLASS_PATCH.get()));
        p.interactOn(head, InteractionHand.MAIN_HAND);
        h.assertValueEqual(state(p).integrity(), 9, "glass after the patch");
        h.assertFalse(head.isRemoved(), "the head was picked up instead");
        done(h, p);
    }

    // --- 5. what the mecha takes by hand -----------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void platingMendsAMechaAndFuelIsPointedToTheHold(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = h.spawn(ModRegistry.mechaType(MechaFrame.HOPPER), FLOOR.east(2));
        m.setOwner(p.getUUID());
        m.setHealth(m.getMaxHealth() - 30);
        float hurt = m.getHealth();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.MECHA_PLATING.get(), 2));
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(m.getHealth() > hurt, "plating did not mend the mecha");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.PLASMA_PELLET.get()));
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "mecha.no_cargo", "a pellet with no Cargo Hold");
        MechaEntity chimera = h.spawn(ModRegistry.mechaType(MechaFrame.CHIMERA), FLOOR.west(3));
        chimera.setOwner(p.getUUID());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.BIO_GEL.get()));
        p.interactOn(chimera, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(p), "mecha.no_cargo", "bio gel on a Chimera with no Cargo Hold");
        done(h, p);
    }

    // --- 6. the lab --------------------------------------------------------------------------------------------------

    private static BodyPrinterBlockEntity stockedPrinter(GameTestHelper h, int energy) {
        h.setBlock(FLOOR, ModRegistry.BODY_PRINTER.get());
        var printer = (BodyPrinterBlockEntity) h.getBlockEntity(FLOOR);
        net.minecraft.world.item.Item[] items = {Items.ROTTEN_FLESH, Items.BONE_BLOCK, Items.REDSTONE_BLOCK, Items.SLIME_BALL};
        for (int i = 0; i < 4; i++) printer.items().setStackInSlot(i, new ItemStack(items[i], BodyPrinterBlockEntity.NEED[i]));
        printer.saline().fill(new FluidStack(ModFluids.SALINE.source.get(), 2000), IFluidHandler.FluidAction.EXECUTE);
        printer.energy().set(energy);
        return printer;
    }

    @GameTest(template = "platform", timeoutTicks = 140)
    public static void thePrinterWaitsForItsPowerAndForAJar(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.north(3));
        FormActions.detach(p, 3);
        UUID old = state(p).body();
        int bodies = h.getEntities(ModRegistry.HEADLESS_BODY.get()).size();
        var printer = stockedPrinter(h, 100_000);
        // the Print button, short of a whole print's power: refused, nothing taken
        h.assertFalse(printer.createMenu(1, p.getInventory(), p).clickMenuButton(p, 0), "printed on 100 kFE");
        h.assertValueEqual(Refusals.lastDelivered(p), "printer.no_power", "the refusal");
        h.assertTrue(!printer.printing() && printer.items().getStackInSlot(0).getCount() == 16, "the materials were taken");
        printer.energy().set(BodyPrinterBlockEntity.ENERGY);
        h.assertTrue(printer.createMenu(1, p.getInventory(), p).clickMenuButton(p, 0), "the printer refused a jar with everything");
        var registries = h.getLevel().registryAccess();
        var tag = printer.saveWithoutMetadata(registries);
        tag.putInt("Progress", BodyPrinterBlockEntity.TICKS - 3);
        printer.loadWithComponents(tag, registries);
        // meanwhile the head went back on
        state(p).setForm(Form.BODY);
        h.runAfterDelay(30, () -> {
            h.assertValueEqual(h.getEntities(ModRegistry.HEADLESS_BODY.get()).size(), bodies, "a body came out for someone with a head on");
            h.assertTrue(Warnings.delivered(p).contains("printer.body_waits"), "the owner was not told the body waits");
            h.assertTrue(printer.printing(), "the finished body was thrown away");
            state(p).setForm(Form.JAR);
            h.assertFalse(printer.createMenu(2, p.getInventory(), p).clickMenuButton(p, 0), "printed a second body over the waiting one");
            h.assertValueEqual(Refusals.lastDelivered(p), "printer.waiting", "Print while a body waits");
        });
        h.runAfterDelay(70, () -> {
            h.assertFalse(printer.printing(), "the body did not come out for the jar");
            HeadlessBody fresh = state(p).body() == null ? null : (HeadlessBody) h.getLevel().getEntity(state(p).body());
            h.assertTrue(fresh != null && !fresh.getUUID().equals(old) && fresh.isAlive() && !fresh.isCorpse(), "the jar's body record is not the new body");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void sequencerAutoTurnsOffWithTheSlotEmpty(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(2));
        h.setBlock(FLOOR, ModRegistry.DNA_SEQUENCER.get());
        var s = (LabMachines.Sequencer) h.getBlockEntity(FLOOR);
        s.energy().set(10_000);
        s.items().setStackInSlot(0, GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), EntityType.SPIDER, 80));
        var menu = (LabMenu) s.createMenu(1, p.getInventory(), p);
        h.assertTrue(menu.clickMenuButton(p, LabMachines.Sequencer.AUTO), "Auto did not start");
        s.items().setStackInSlot(0, ItemStack.EMPTY);
        h.assertTrue(menu.clickMenuButton(p, LabMachines.Sequencer.AUTO), "Auto could not be switched off with the slot empty");
        CompoundTag saved = s.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertFalse(saved.getBoolean("Auto"), "Auto stayed on");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theSplicerSaysWhatALoadFits(GameTestHelper h) {
        h.assertValueEqual(Genetics.Target.lightestFor(3), Genetics.Target.BODY, "load 3");
        h.assertValueEqual(Genetics.Target.lightestFor(4), Genetics.Target.HOST, "load 4");
        h.assertValueEqual(Genetics.Target.lightestFor(7), Genetics.Target.MECHA, "load 7");
        h.assertTrue(Genetics.Target.lightestFor(9) == null, "load 9 fits something");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void aFractionNamesItsGenesAndTheReadsToADraft(GameTestHelper h) {
        ItemStack spider = GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), EntityType.SPIDER, 80);
        List<Component> tip = new ArrayList<>();
        spider.getItem().appendHoverText(spider, net.minecraft.world.item.Item.TooltipContext.EMPTY, tip, TooltipFlag.NORMAL);
        List<String> keys = new ArrayList<>();
        for (Component c : tip) if (c.getContents() instanceof TranslatableContents t) keys.add(t.getKey());
        h.assertTrue(keys.contains("item.headinajar.sample.carries") && keys.contains("item.headinajar.sample.reads"), "tooltip: " + keys);
        // quality 80, one gene, read at 60%: 12% a read, three reads to 30%
        h.assertValueEqual(GeneticsItems.SampleItem.readsToDraft(80, 1), 3, "reads to a Draft");
        // and the library agrees: three auto-reads take Spider Climb to a Draft, two don't
        float gain = Genetics.readGain(80, LabMachines.Sequencer.AUTO_ACCURACY, 1);
        h.assertTrue(gain * 2 < 30 && gain * 3 >= 30, "gain " + gain);
        ItemStack cow = GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), EntityType.COW, 80);
        tip.clear();
        cow.getItem().appendHoverText(cow, net.minecraft.world.item.Item.TooltipContext.EMPTY, tip, TooltipFlag.NORMAL);
        h.assertTrue(tip.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("item.headinajar.sample.no_genes")),
                "a cow's fraction does not say it carries nothing");
        h.succeed();
    }

    // --- 7. what the machine verifier read in the code ---------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aNitwitIsNeverChargedAnEmerald(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        BlockPos mountAt = FLOOR.east();
        h.setBlock(mountAt, ModRegistry.EMPLOYEE_MOUNT.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        var mount = (EmployeeMountBlockEntity) h.getBlockEntity(mountAt);
        Villager v = h.spawn(EntityType.VILLAGER, FLOOR.above(3));
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NITWIT));
        ItemStack head = JarredHeadItem.of(v);
        v.discard();
        mount.use(p, head);
        h.setBlock(mountAt.east(), Blocks.CHEST);
        ((ChestBlockEntity) h.getBlockEntity(mountAt.east())).setItem(0, new ItemStack(Items.EMERALD, 3));
        // payroll, as its tick runs it
        h.assertFalse(mount.payroll(), "a nitwit was paid");
        h.assertValueEqual(((ChestBlockEntity) h.getBlockEntity(mountAt.east())).getItem(0).getCount(), 3, "emeralds after a nitwit's payday");
        // and its slot in the complaint cycle is a real one wherever it stands
        long slot = Math.floorMod(new BlockPos(-37, 64, 5).asLong(), 2400L);
        h.assertTrue(slot >= 0 && slot < 2400, "slot " + slot);
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theGantryTakesOnlyPlatingFromAPipe(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.HOPPER)));
        BlockPos at = h.absolutePos(FLOOR);
        for (Direction side : new Direction[]{Direction.UP, Direction.NORTH, Direction.DOWN}) {
            var pipe = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, at, side);
            h.assertTrue(pipe != null, "no item handler on " + side);
            h.assertTrue(pipe.extractItem(AssemblyGantryBlockEntity.SLOT_FRAME, 1, false).isEmpty(), "a pipe took the frame from " + side);
            ItemStack legs = new ItemStack(ModRegistry.mechaModule(MechaModule.HYDRAULIC_LEGS));
            h.assertValueEqual(pipe.insertItem(AssemblyGantryBlockEntity.FIRST_MODULE, legs, false).getCount(), 1, "a pipe fitted a module from " + side);
        }
        var top = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, at, Direction.UP);
        h.assertTrue(top.insertItem(AssemblyGantryBlockEntity.SLOT_PLATING, new ItemStack(ModRegistry.MECHA_PLATING.get(), 4), false).isEmpty(),
                "plating was not taken from the top");
        h.assertTrue(gantry.items().getStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME).is(ModRegistry.frameItem(MechaFrame.HOPPER)), "the frame left");
        h.succeed();
    }

    // --- 8. the chair ------------------------------------------------------------------------------------------------

    private static final BlockPos CHAIR = new BlockPos(7, 2, 7);

    private static SeparationChairBlockEntity chair(GameTestHelper h) {
        h.setBlock(CHAIR, ModRegistry.SEPARATION_CHAIR.get().defaultBlockState().setValue(SeparationChairBlock.FACING, Direction.NORTH));
        SeparationChairBlockEntity chair = (SeparationChairBlockEntity) h.getBlockEntity(CHAIR);
        chair.items().setStackInSlot(0, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        chair.items().setStackInSlot(1, new ItemStack(ModRegistry.RELAY_GOGGLES.get()));
        chair.items().setStackInSlot(2, new ItemStack(ModRegistry.COLLAR_CAM.get()));
        chair.items().setStackInSlot(3, new ItemStack(ModRegistry.SALINE_BOTTLE.get(), 4));
        chair.energy().set(50_000);
        return chair;
    }

    @GameTest(template = "platform")
    public static void aSeatedPatientOpensThePanelAgainWithUse(GameTestHelper h) {
        ServerPlayer p = player(h, CHAIR);
        chair(h);
        // sitting down: the empty-handed use on the chair seats them and opens the panel. A test player can't be sent
        // the panel itself (an extended menu): the attempt to send it is what shows the chair answered.
        h.assertTrue(panelOpened(h, p), "sitting down did not open the panel");
        h.assertTrue(p.getVehicle() != null, "not seated");
        p.closeContainer();
        // the use the seated client sends for the chair it sits in, the panel closed
        h.assertTrue(panelOpened(h, p), "use from the seat did not open the panel again");
        h.assertTrue(p.getVehicle() != null, "the use got them up");
        p.stopRiding();
        done(h, p);
    }

    /** Uses the chair as a click does; whether the chair asked to open its panel for this player. */
    private static boolean panelOpened(GameTestHelper h, ServerPlayer p) {
        try {
            useBlock(h, p, CHAIR);
            return p.containerMenu instanceof SeparationChairMenu;
        } catch (RuntimeException sent) {
            return String.valueOf(sent.getMessage()).contains("open_screen");
        }
    }

    private static void sit(GameTestHelper h, ServerPlayer p) {
        com.coolfreeze23.headinajar.entity.SeatEntity seat = com.coolfreeze23.headinajar.entity.SeatEntity.at(h.getLevel(), h.absolutePos(CHAIR), Direction.NORTH);
        h.assertTrue(p.startRiding(seat, true), "could not sit");
    }

    @GameTest(template = "platform", timeoutTicks = 300)
    public static void aBotchedHeadPipedToACradleFloatsUpsideDownThere(GameTestHelper h) {
        ServerPlayer p = player(h, CHAIR);
        SeparationChairBlockEntity chair = chair(h);
        h.setBlock(CHAIR.east(2), ModRegistry.CRADLE.get());
        var cradle = (LifeSupportCradleBlockEntity) h.getBlockEntity(CHAIR.east(2));
        sit(h, p);
        h.assertTrue(((SeparationChairMenu) chair.createMenu(1, p.getInventory(), p)).clickMenuButton(p, SeparationChairMenu.BUTTON_SIGN), "not started");
        h.runAfterDelay(105, () -> {
            // what /data merge does: the botch decided at the point of no return
            CompoundTag tag = chair.saveWithFullMetadata(h.getLevel().registryAccess());
            tag.putBoolean("Botched", true);
            chair.loadWithComponents(tag, h.getLevel().registryAccess());
        });
        h.runAfterDelay(250, () -> {
            h.assertTrue(p.getUUID().equals(cradle.owner()), "the head did not go to the Cradle");
            h.assertTrue(cradle.upsideDown(), "the botched head sits upright in the Cradle");
            // what a client sees: the update tag carries it
            var copy = new LifeSupportCradleBlockEntity(cradle.getBlockPos(), cradle.getBlockState());
            copy.setLevel(h.getLevel());
            copy.handleUpdateTag(cradle.getUpdateTag(h.getLevel().registryAccess()), h.getLevel().registryAccess());
            h.assertTrue(copy.upsideDown(), "the client copy has it upright");
            cradle.setUpsideDown(1);
        });
        h.runAfterDelay(255, () -> {
            h.assertFalse(cradle.upsideDown(), "still upside down after its time");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 320)
    public static void doloresDoesNotSendTheHeadToTheOutletWhenItWentToTheCradle(GameTestHelper h) {
        ServerPlayer p = player(h, CHAIR);
        SeparationChairBlockEntity chair = chair(h);
        h.setBlock(CHAIR.east(2), ModRegistry.CRADLE.get());
        sit(h, p);
        h.assertTrue(((SeparationChairMenu) chair.createMenu(1, p.getInventory(), p)).clickMenuButton(p, SeparationChairMenu.BUTTON_SIGN), "not started");
        long start = h.getLevel().getGameTime();
        h.runAfterDelay(300, () -> {
            String pos = h.absolutePos(CHAIR).toShortString();
            boolean outletLine = false;
            for (String e : com.coolfreeze23.headinajar.block.DoloresVoice.HISTORY) {
                if (!e.contains(" " + pos + " ")) continue;
                String[] parts = e.split(" ");
                if (Long.parseLong(parts[0]) >= start && e.contains(" done")) outletLine = true;
            }
            h.assertFalse(outletLine, "her recorded line about the outlet played for a head piped to the Cradle");
            h.assertTrue(state(p).form() == Form.LINKED, "not separated");
            done(h, p);
        });
    }

    // --- 9. ridden hosts at a creature's pace -----------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aRiddenCowTopsOutNearASprint(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        Cow cow = host(h, EntityType.COW, FLOOR, p);
        // steady speed on flat ground for a pace: pace / (1 - 0.546) blocks a tick
        double perPace = 20 / (1 - 0.546);
        Hosts.data(cow).sync = 0;
        double low = HostControl.riddenSpeed(cow, p) * perPace;
        Hosts.data(cow).sync = 100;
        double full = HostControl.riddenSpeed(cow, p) * perPace;
        h.assertTrue(full <= 5.8 && full >= 5.3, "a cow at full sync does " + full + " blocks a second");
        h.assertTrue(low < full, "low sync is not slower: " + low + " against " + full);
        // a real mount keeps the pace it has under vanilla's saddle
        Horse horse = h.spawn(EntityType.HORSE, FLOOR.east(3));
        Hosts.data(horse).sync = 0;
        h.assertTrue(Math.abs(HostControl.riddenSpeed(horse, p) - horse.getAttributeValue(Attributes.MOVEMENT_SPEED)) < 1e-4, "a horse is paced");
        // the saved attribute is left alone
        h.assertTrue(Math.abs(cow.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() - 0.2) < 1e-4, "the cow's attribute changed");
        done(h, p);
    }
}
