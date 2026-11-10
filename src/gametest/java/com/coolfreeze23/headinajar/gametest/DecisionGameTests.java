package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity;
import com.coolfreeze23.headinajar.block.ChargingDockBlock;
import com.coolfreeze23.headinajar.block.HeadJobs;
import com.coolfreeze23.headinajar.block.PowerBlockEntities;
import com.coolfreeze23.headinajar.block.SafetyPlacardBlock;
import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Nudge;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Courier;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.form.SharedKey;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.host.HostEvents;
import com.coolfreeze23.headinajar.item.Handbook;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.JarMenu;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * What a bare jar may work, the power numbers as one set, the handbook for new employees, the shared sprint key and the
 * small rulings. Each goes through the path a click, a key, a tick or a world load takes.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class DecisionGameTests {
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

    /**
     * A right-click on the top face of this block, as the client sends it. Returns whether the click got as far as
     * sending a menu to the client: a test player has no channel for the mod's menus, so that send is refused here.
     */
    private static boolean useBlock(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        BlockPos abs = h.absolutePos(pos);
        try {
            p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
        } catch (RuntimeException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (String.valueOf(t.getMessage()).contains("may not be sent")) return true;
            }
            throw e;
        }
        return p.containerMenu != p.inventoryMenu;
    }

    /** The event a right-click on this block fires: whether it was let through (not cancelled). */
    private static boolean clickAllowed(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        BlockPos abs = h.absolutePos(pos);
        var click = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, abs, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        return !NeoForge.EVENT_BUS.post(click).isCanceled();
    }

    private static ItemStack jarredHead(GameTestHelper h, EntityType<? extends Mob> type, VillagerProfession job) {
        Mob mob = h.spawn(type, FLOOR.above(3));
        if (mob instanceof Villager v && job != null) v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(1));
        if (mob instanceof Villager v) v.getOffers();
        ItemStack stack = JarredHeadItem.of(mob);
        mob.discard();
        return stack;
    }

    private static MechaEntity mecha(GameTestHelper h, MechaFrame frame, BlockPos at, int battery) {
        MechaEntity m = h.spawn(ModRegistry.mechaType(frame), at);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < frame.totalSlots(); i++) slots.add(ItemStack.EMPTY);
        m.setModules(slots);
        m.setOwner(UUID.randomUUID());
        m.setBattery(battery);
        return m;
    }

    // --- what a bare jar may work ---------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void bareJarWorksTheCompanysBlocksNotTheChest(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        // a jarred villager is placed by a whole player first
        p.setItemInHand(InteractionHand.MAIN_HAND, jarredHead(h, EntityType.VILLAGER, VillagerProfession.LIBRARIAN));
        useBlock(h, p, FLOOR.north(3).below());
        h.assertTrue(h.getBlockState(FLOOR.north(3)).is(ModRegistry.JARRED_HEAD_BLOCK.get()), "setup: the jarred villager was not placed");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.state(p).form() == Form.JAR && com.coolfreeze23.headinajar.form.FormEvents.lockedOut(p), "setup: not a bare jar");

        // every block of the company with a menu or a status lets the click through
        var blocks = List.of(ModRegistry.ASSEMBLY_GANTRY.get(), ModRegistry.BODY_LOCKER.get(), ModRegistry.TESLA_NODE.get(), ModRegistry.RELAY_MAST.get(),
                ModRegistry.RELAY_MAST_MK2.get(), ModRegistry.SIGNAL_JAMMER.get(), ModRegistry.LEECH_JAR.get(), ModRegistry.SAFETY_PLACARD.get(),
                ModRegistry.SEPARATION_CHAIR.get(), ModRegistry.JARRED_HEAD_BLOCK.get());
        for (var block : blocks) {
            h.assertTrue(block.defaultBlockState().is(com.coolfreeze23.headinajar.form.FormEvents.HEAD_OPERABLE), block + " is not head-operable");
        }
        // the Gantry's menu: the click reaches the menu being sent
        h.setBlock(FLOOR.east(2), ModRegistry.ASSEMBLY_GANTRY.get());
        h.assertTrue(clickAllowed(h, p, FLOOR.east(2)), "the Gantry was locked to a jar");
        h.assertTrue(useBlock(h, p, FLOOR.east(2)), "a bare jar's click did not open the Gantry");
        p.closeContainer();
        // status blocks answer and refuse nothing
        h.setBlock(FLOOR.west(2), ModRegistry.TESLA_NODE.get());
        h.setBlock(FLOOR.west(2).north(2), ModRegistry.SIGNAL_JAMMER.get());
        h.setBlock(FLOOR.east(2).south(2), ModRegistry.BODY_LOCKER.get());
        useBlock(h, p, FLOOR.west(2));
        useBlock(h, p, FLOOR.west(2).north(2));
        useBlock(h, p, FLOOR.east(2).south(2));
        h.assertTrue(Refusals.delivered(p).isEmpty(), "a status block refused a jar: " + Refusals.delivered(p));
        // the placard turns to its next notice
        h.setBlock(FLOOR.south(3), ModRegistry.SAFETY_PLACARD.get().defaultBlockState().setValue(SafetyPlacardBlock.FACING, Direction.NORTH));
        int before = h.getBlockState(FLOOR.south(3)).getValue(SafetyPlacardBlock.NOTICE);
        BlockPos placard = h.absolutePos(FLOOR.south(3));
        p.gameMode.useItemOn(p, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(placard), Direction.SOUTH, placard, false));
        h.assertTrue(h.getBlockState(FLOOR.south(3)).getValue(SafetyPlacardBlock.NOTICE) != before, "the placard did not answer a jar");
        // the jarred villager trades with a jar
        useBlock(h, p, FLOOR.north(3));
        h.assertTrue(p.containerMenu instanceof MerchantMenu, "a jarred villager would not trade with a bare jar");
        p.closeContainer();
        // the chair shows its panel; a jar is never sat down in it
        h.setBlock(FLOOR.south(2).east(3), ModRegistry.SEPARATION_CHAIR.get());
        h.assertTrue(useBlock(h, p, FLOOR.south(2).east(3)), "the chair's panel did not open for a jar");
        h.assertTrue(p.getVehicle() == null, "a jar was sat in the Separation Chair");
        p.closeContainer();

        // vanilla stays locked: a chest is refused with the Nudge
        h.setBlock(FLOOR.north(2).east(2), Blocks.CHEST);
        useBlock(h, p, FLOOR.north(2).east(2));
        h.assertTrue(p.containerMenu == p.inventoryMenu, "a bare jar opened a chest");
        h.assertValueEqual(Refusals.lastDelivered(p), Nudge.CONTAINER, "chest line");
        // a block in the mouth is not placed against a machine, and sneaking past the machine to use it is an item use
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
        useBlock(h, p, FLOOR.west(2));
        h.assertTrue(h.getBlockState(FLOOR.west(2).above()).isAir(), "a bare jar placed a block against a Tesla Node");
        p.setShiftKeyDown(true);
        h.runAfterDelay(Refusals.DEBOUNCE + 1, () -> {
            useBlock(h, p, FLOOR.west(2));
            h.assertTrue(h.getBlockState(FLOOR.west(2).above()).isAir(), "a sneaking bare jar placed a block");
            h.assertValueEqual(Refusals.lastDelivered(p), Nudge.USE, "sneak-use line");
            done(h, p);
        });
    }

    // --- the power numbers ------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void gantryChargesTheMechaOnItsPadTickByTick(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        gantry.energy().set(12_000);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.above(), 0);
        h.assertTrue(MechaFrame.HOPPER.battery == 100_000 && MechaFrame.COLOSSUS.battery == 1_000_000 && MechaFrame.EXECUTIVE.battery == 2_000_000,
                "frame batteries are not a quarter of the old ones");
        int[] last = {m.battery(), gantry.energy().getEnergyStored()};
        List<String> seen = new ArrayList<>();
        for (int t = 1; t <= 4; t++) {
            int tick = t;
            h.runAfterDelay(t, () -> {
                int battery = m.battery(), stored = gantry.energy().getEnergyStored();
                int gained = battery - last[0], given = last[1] - stored;
                seen.add(tick + ": +" + gained + " / -" + given);
                if (gained != given) h.fail("the mecha gained " + gained + " while the Gantry lost " + given + " (" + seen + ")");
                if (tick <= 2 && gained != AssemblyGantryBlockEntity.PAD_CHARGE) h.fail("no charge on tick " + tick + ": " + seen);
                last[0] = battery;
                last[1] = stored;
            });
        }
        h.runAfterDelay(6, () -> {
            h.assertTrue(m.battery() == 12_000 && gantry.energy().getEnergyStored() == 0, "charge made or lost: mecha " + m.battery() + ", gantry "
                    + gantry.energy().getEnergyStored() + " " + seen);
            h.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void oldMechaBatteryLoadsClamped(GameTestHelper h) {
        // a Colossus saved full under the old 4 MFE battery, and one saved with a broken negative value
        MechaEntity old = mecha(h, MechaFrame.COLOSSUS, FLOOR, 0);
        CompoundTag tag = new CompoundTag();
        old.saveWithoutId(tag);
        old.discard();
        tag.putInt("Battery", 4_000_000);
        MechaEntity loaded = ModRegistry.mechaType(MechaFrame.COLOSSUS).create(h.getLevel());
        loaded.load(tag);
        h.assertTrue(loaded.battery() == 1_000_000 && loaded.battery() == loaded.batteryCapacity(), "an old full Colossus loaded with " + loaded.battery());
        tag.putInt("Battery", -5);
        MechaEntity broken = ModRegistry.mechaType(MechaFrame.COLOSSUS).create(h.getLevel());
        broken.load(tag);
        h.assertTrue(broken.battery() == 0, "a negative battery loaded as " + broken.battery());
        h.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void nineDragonHeadsMakeWhatOneMakes(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CEREBRAL_ARRAY.get());
        h.setBlock(FLOOR.east(3), ModRegistry.CEREBRAL_ARRAY.get());
        var one = (CerebralArrayBlockEntity) h.getBlockEntity(FLOOR);
        var nine = (CerebralArrayBlockEntity) h.getBlockEntity(FLOOR.east(3));
        for (var a : List.of(one, nine)) {
            a.items().setStackInSlot(CerebralArrayBlockEntity.SLOT_CORE, new ItemStack(ModRegistry.BOARD_ROOM_CORE.get()));
            a.broth().fill(new FluidStack(ModFluids.BROTH.source.get(), 8000), IFluidHandler.FluidAction.EXECUTE);
        }
        one.items().setStackInSlot(0, new ItemStack(Items.DRAGON_HEAD));
        for (int i = 0; i < CerebralArrayBlockEntity.HEAD_SLOTS; i++) nine.items().setStackInSlot(i, new ItemStack(Items.DRAGON_HEAD));
        h.runAfterDelay(25, () -> {
            h.assertTrue(one.output() == HeadJobs.DRAGON_HEAD && HeadJobs.DRAGON_HEAD == 300, "one Dragon Head makes " + one.output());
            h.assertTrue(nine.output() == one.output(), "nine Dragon Heads make " + nine.output() + ", one makes " + one.output());
            h.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void jammerAndTurretDrainTheirNewRates(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.SIGNAL_JAMMER.get());
        var jammer = (PowerBlockEntities.Station) h.getBlockEntity(FLOOR);
        jammer.energy().set(10_000);
        h.setBlock(FLOOR.east(4), ModRegistry.TURRET_PEDESTAL.get());
        var turret = (TurretPedestalBlockEntity) h.getBlockEntity(FLOOR.east(4));
        turret.items().setStackInSlot(0, jarredHead(h, EntityType.SNOW_GOLEM, null));
        turret.energy().set(10_000);
        h.runAfterDelay(10, () -> {
            int jam = 10_000 - jammer.energy().getEnergyStored(), idle = 10_000 - turret.energy().getEnergyStored();
            h.assertTrue(jam >= 36 && jam <= 44, "the jammer drew " + jam + " FE in 10 ticks");
            h.assertTrue(idle >= 9 && idle <= 11, "an idle turret drew " + idle + " FE in 10 ticks");
            // a target: the firing rate
            h.spawn(EntityType.ZOMBIE, FLOOR.east(4).north(4));
            int start = turret.energy().getEnergyStored();
            h.runAfterDelay(10, () -> {
                int firing = start - turret.energy().getEnergyStored();
                h.assertTrue(firing >= 80 && firing <= 110, "a turret with a target drew " + firing + " FE in 10 ticks");
                h.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void shellAndPropellerDrainTheJarLess(GameTestHelper h) {
        h.assertTrue(HostEvents.SHELL_DRAIN == 3, "shell drain " + HostEvents.SHELL_DRAIN);
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        var shell = h.spawn(ModRegistry.ROVER.get(), FLOOR.east(2));
        FormActions.mountHost(p, shell);
        h.assertTrue(state(p).form() == Form.HOSTED, "setup: not riding the shell");
        int before = state(p).charge();
        h.runAfterDelay(20, () -> {
            // the shell ticks on its own (3 FE/t); the player isn't ticked by a test server
            int spent = before - state(p).charge();
            h.assertTrue(spent >= 3 * 18 && spent <= 3 * 22, "a shell took " + spent + " FE in 20 ticks");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void jarChargeGrowsWithTier(GameTestHelper h) {
        ServerPlayer basic = player(h, FLOOR), reinforced = player(h, FLOOR.east(3)), exec = player(h, FLOOR.west(3));
        FormActions.detach(basic, 3);
        FormActions.detach(reinforced, 5);
        FormActions.detach(exec, 7);
        h.assertTrue(state(basic).charge() == 100_000 && state(reinforced).charge() == 250_000 && state(exec).charge() == 500_000,
                "fresh jars hold " + state(basic).charge() + " / " + state(reinforced).charge() + " / " + state(exec).charge());
        // an existing Reinforced Jar at the old 100 kFE keeps it, with room to 250 kFE
        state(reinforced).setCharge(100_000);
        // the feed slot, clicked as the client clicks it: a dust is 5,000 FE
        JarMenu menu = new JarMenu(1, reinforced.getInventory());
        menu.setCarried(new ItemStack(Items.REDSTONE, 4));
        menu.clicked(JarState.MAX_SLOTS, 0, ClickType.PICKUP, reinforced);
        h.assertTrue(state(reinforced).charge() == 120_000, "four dusts in a Reinforced Jar at 100 kFE give " + state(reinforced).charge());
        // the Charging Dock fills past the old cap
        h.setBlock(FLOOR.east(3), ModRegistry.CHARGING_DOCK.get());
        var dock = (ChargingDockBlock.Entity) h.getBlockEntity(FLOOR.east(3));
        dock.energy().set(20_000);
        ChargingDockBlock.charge(reinforced, state(reinforced));
        h.assertTrue(state(reinforced).charge() > 120_000, "the dock did not charge a Reinforced Jar past 120 kFE: " + state(reinforced).charge());
        done(h, basic, reinforced, exec);
    }

    // --- the handbook ---------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void handbookOnFirstJoinOnceAndByRecipe(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Handbook.giveToTestPlayers = true;
        try {
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(p));
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(p));
        } finally {
            Handbook.giveToTestPlayers = false;
        }
        ItemStack want = Handbook.copy(h.getLevel().getServer());
        h.assertFalse(want.isEmpty(), "no handbook recipe loaded");
        long books = p.getInventory().items.stream().filter(s -> ItemStack.isSameItemSameComponents(s, want)).mapToInt(ItemStack::getCount).sum();
        h.assertTrue(books == 1, "a new employee got " + books + " handbooks for two logins");
        h.assertTrue(p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(Handbook.GIVEN), "the gift is not remembered past death");
        if (!Handbook.patchouli()) {
            var content = want.get(DataComponents.WRITTEN_BOOK_CONTENT);
            h.assertTrue(content != null && content.pages().size() >= 10 && content.pages().get(0).raw().getContents() instanceof TranslatableContents,
                    "the written edition has no translated pages");
        }
        // a lost copy: a book and a Surgical Steel Blend
        var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(new ItemStack(Items.BOOK), new ItemStack(ModRegistry.SURGICAL_STEEL_BLEND.get())));
        var found = h.getLevel().getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, h.getLevel());
        h.assertTrue(found.isPresent() && ItemStack.isSameItemSameComponents(found.get().value().assemble(input, h.getLevel().registryAccess()), want),
                "a book and a Surgical Steel Blend don't make the handbook");
        done(h, p);
    }

    // --- sprint and the ability on one key --------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void sharedKeyIsExplainedOnceWhereTheAbilityAnswers(GameTestHelper h) {
        ServerPlayer plain = player(h, FLOOR), gifted = player(h, FLOOR.east(3));
        // a body with no genes: the press was a sprint; nothing to explain
        Network.action(plain, new Network.JarAction(Network.Action.ABILITY_SHARED));
        h.assertFalse(plain.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(SharedKey.TOLD), "a press with nothing behind it was explained");
        // a body with an active gene: both fire, told once
        Gene active = Arrays.stream(Gene.values()).filter(g -> g.active).findFirst().orElseThrow();
        var genes = gifted.getData(ModRegistry.GENES);
        genes.serums.add(new Serum(List.of(new Serum.Entry(active, 1f)), 100));
        genes.changed();
        h.assertTrue(SharedKey.abilityAnswers(gifted), "setup: the gene's ability doesn't answer");
        h.assertTrue(SharedKey.pressed(gifted), "no notice at the first shared press");
        h.assertFalse(SharedKey.pressed(gifted), "the notice came twice");
        Network.action(gifted, new Network.JarAction(Network.Action.ABILITY_SHARED));
        h.assertTrue(gifted.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(SharedKey.TOLD), "the notice is not remembered");
        done(h, plain, gifted);
    }

    // --- the small rulings ------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void strangerWithSalineIsRefusedFriendIsNot(GameTestHelper h) {
        ServerPlayer owner = player(h, FLOOR), stranger = player(h, FLOOR.east(3));
        // the chair's result: a linked body, its head set down two blocks away
        h.assertTrue(FormActions.separate(owner, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.west(2))), null), "separate failed");
        HeadJarEntity head = h.getEntities(ModRegistry.HEAD_JAR.get()).get(0);
        state(owner).setSaline(100);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SALINE_BOTTLE.get()));
        stranger.interactOn(head, InteractionHand.MAIN_HAND);
        h.assertValueEqual(Refusals.lastDelivered(stranger), "head.not_yours", "a stranger's saline");
        h.assertTrue(state(owner).saline() == 100 && stranger.getMainHandItem().is(ModRegistry.SALINE_BOTTLE.get()), "a stranger topped up the head");
        HeadRegistry.get(owner).setTrust(owner.getUUID(), stranger.getUUID(), true);
        stranger.interactOn(head, InteractionHand.MAIN_HAND);
        h.assertTrue(state(owner).saline() == 350, "a trusted friend could not top up the head: " + state(owner).saline());
        done(h, owner, stranger);
    }

    @GameTest(template = "platform")
    public static void courierTakesWhatThereIsAndSaysSo(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        p.teleportTo(p.getX() + 12, p.getY(), p.getZ());
        state(p).setCharge(5_000);
        JarMenu menu = new JarMenu(1, p.getInventory());
        menu.clickMenuButton(p, JarMenu.BUTTON_COURIER);
        h.assertTrue(p.getPersistentData().getInt(Courier.KEY) > 0, "the Courier refused a jar with 5 kFE: " + Refusals.lastDelivered(p));
        h.assertTrue(state(p).charge() == 0 && p.getPersistentData().getInt(Courier.FEE) == 5_000, "fee " + p.getPersistentData().getInt(Courier.FEE));
        var line = Courier.booked(p);
        h.assertTrue(line.getContents() instanceof TranslatableContents t && t.getKey().equals("courier.headinajar.booked_fee") && t.getArgs()[0].equals("5,000"),
                "the booking does not say what it took: " + line);
        // an empty jar is rescued too
        Courier.cancel(p);
        state(p).setCharge(0);
        menu.clickMenuButton(p, JarMenu.BUTTON_COURIER);
        h.assertTrue(p.getPersistentData().getInt(Courier.KEY) > 0 && p.getPersistentData().getInt(Courier.FEE) == 0, "the Courier refused an empty jar");
        Courier.cancel(p);
        done(h, p);
    }
}
