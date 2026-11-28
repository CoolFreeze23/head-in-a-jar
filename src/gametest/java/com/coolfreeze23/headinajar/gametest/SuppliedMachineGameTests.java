package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.BodyLockerBlockEntity;
import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.StaffEvents;
import com.coolfreeze23.headinajar.staff.StaffTable;
import com.coolfreeze23.headinajar.supplier.SuppliedMachines;
import com.coolfreeze23.headinajar.supplier.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * The new arrivals at the machines that are not species rows (0.8.0), with the creatures and the supplier's items
 * themselves: every refusal the book gives them, with its line, and what the code can see of the ways they leave.
 * Registered only when every new arrival is (as {@link SuppliedGameTests} is), so a game without them runs exactly the
 * old suite. {@code platform}: 15 x 6 x 15, the floor's top at helper y 1.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
public class SuppliedMachineGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    @SubscribeEvent
    static void register(RegisterGameTestsEvent event) {
        if (Supplier.delivered().size() == Supplier.NEW_ARRIVALS.size()) event.register(SuppliedMachineGameTests.class);
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.withDefaultNamespace(path))
                .orElseThrow(() -> new GameTestAssertException("not registered: item " + path));
    }

    private static ItemStack stack(String path) {
        return new ItemStack(item(path));
    }

    private static BlockState block(String path) {
        Block b = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.withDefaultNamespace(path))
                .orElseThrow(() -> new GameTestAssertException("not registered: block " + path));
        return b.defaultBlockState();
    }

    /** A new arrival worn down and sawn into a host by {@code p}, as the saw does it. */
    private static Mob host(GameTestHelper h, ServerPlayer p, String path, BlockPos at) {
        Mob mob = SuppliedSpeciesGameTests.spawn(h, path, at);
        mob.setHealth(mob.getMaxHealth() * 0.4f);
        h.assertTrue(SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "the saw did not make a host of a " + path);
        h.assertTrue(Hosts.isHost(mob), "a " + path + " host");
        return mob;
    }

    /** A real click on a creature with this in the main hand (the interact event); whether it was cancelled. */
    private static boolean use(ServerPlayer p, ItemStack held, Mob target) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        var event = new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, target);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    /** A right-click on the top face of this block with this in the main hand, as the client sends it. */
    private static void useOn(GameTestHelper h, ServerPlayer p, ItemStack held, BlockPos pos) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = h.absolutePos(pos);
        p.gameMode.useItemOn(p, h.getLevel(), held, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
    }

    private static void refused(GameTestHelper h, ServerPlayer p, String reason, String what) {
        h.assertTrue(Refusals.delivered(p).contains(reason), what + ": not refused with " + reason + ", got " + Refusals.delivered(p));
        h.assertTrue(Language.getInstance().has(Refusals.PREFIX + reason), "the line " + reason);
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static long weatherAge(Mob golem) {
        return golem.saveWithoutId(new CompoundTag()).getLong("next_weather_age");
    }

    private static void setWeatherAge(Mob golem, long at) {
        CompoundTag tag = golem.saveWithoutId(new CompoundTag());
        tag.putLong("next_weather_age", at);
        golem.load(tag);
    }

    /** A creaking's saved home set to {@code home} (relative): it is bound to whatever heart is there. */
    private static void home(GameTestHelper h, Mob creaking, BlockPos home) {
        CompoundTag tag = creaking.saveWithoutId(new CompoundTag());
        tag.put("home_pos", NbtUtils.writeBlockPos(h.absolutePos(home)));
        creaking.load(tag);
    }

    /** Embalming Fluid and Neural Lace: bodies that never rot keep them, in their own words; the rest as any host. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void embalmingAndLaceOnTheNewBodies(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "embalm", FLOOR.south(4));
        long now = h.getLevel().getGameTime();
        Mob creaking = host(h, p, "creaking", FLOOR.west(4));
        h.assertTrue(use(p, new ItemStack(ModRegistry.EMBALMING_FLUID.get()), creaking), "the fluid on a creaking was not stopped");
        refused(h, p, "host.embalm_creaking", "Embalming Fluid on a creaking");
        h.assertTrue(Hosts.data(creaking).embalmedUntil <= now, "a creaking was embalmed");
        use(p, new ItemStack(ModRegistry.NEURAL_LACE.get()), creaking);
        refused(h, p, "host.lace_creaking", "Neural Lace on a creaking");
        h.assertFalse(Hosts.data(creaking).laced, "a creaking was laced");
        Mob husk = host(h, p, "camel_husk", FLOOR.east(4));
        use(p, new ItemStack(ModRegistry.EMBALMING_FLUID.get()), husk);
        refused(h, p, "host.embalm_camel_husk", "Embalming Fluid on a camel husk");
        use(p, new ItemStack(ModRegistry.NEURAL_LACE.get()), husk);
        refused(h, p, "host.lace_camel_husk", "Neural Lace on a camel husk");
        h.assertFalse(Hosts.data(husk).laced, "a camel husk was laced");
        // the copper golem rots in 0.8.0 (its host file decays at 2): the fluid works on it as on any host
        Mob golem = host(h, p, "copper_golem", FLOOR.north(4));
        h.assertTrue(Hosts.definition(golem).decayPerMinute() > 0 && SuppliedMachines.embalmRefusal(golem) == null
                && SuppliedMachines.laceRefusal(golem) == null, "the copper golem's lines are for a body that never rots");
        Hosts.data(golem).freshness = 40;
        use(p, new ItemStack(ModRegistry.EMBALMING_FLUID.get()), golem);
        h.assertTrue(Hosts.data(golem).freshness == 100 && Hosts.data(golem).embalmedUntil > now, "a copper golem host was not embalmed");
        Mob parched = host(h, p, "parched", FLOOR.south(2));
        Hosts.data(parched).freshness = 40;
        use(p, new ItemStack(ModRegistry.EMBALMING_FLUID.get()), parched);
        h.assertTrue(Hosts.data(parched).freshness == 100, "a parched host was not embalmed");
        done(h, p);
    }

    /** Honeycomb: the game waxes a copper golem's body; a waxed one, wood and everything else are told. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void honeycombOnTheNewBodies(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "honey", FLOOR.south(4));
        Mob golem = host(h, p, "copper_golem", FLOOR.north(3));
        h.assertFalse(SuppliedMachines.waxed(golem), "a new golem is not waxed");
        // the real click: our handler lets it through and the golem itself takes the wax
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEYCOMB));
        p.interactOn(golem, InteractionHand.MAIN_HAND);
        h.assertTrue(SuppliedMachines.waxed(golem), "honeycomb did not wax the copper golem host");
        h.assertTrue(use(p, new ItemStack(Items.HONEYCOMB), golem), "honeycomb on a waxed golem was not stopped");
        refused(h, p, "host.honeycomb_again", "honeycomb on a waxed copper golem");
        Mob creaking = host(h, p, "creaking", FLOOR.west(4));
        use(p, new ItemStack(Items.HONEYCOMB), creaking);
        refused(h, p, "host.honeycomb_wood", "honeycomb on a creaking");
        Mob ghast = host(h, p, "happy_ghast", FLOOR.east(3).south(1));
        use(p, new ItemStack(Items.HONEYCOMB), ghast);
        refused(h, p, "host.honeycomb", "honeycomb on a happy ghast");
        // a vanilla creature's body is as it was (the status line, no refusal)
        Mob cow = h.spawn(EntityType.COW, FLOOR.south(2));
        cow.setHealth(2);
        SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), cow, InteractionHand.MAIN_HAND);
        h.assertTrue(SuppliedMachines.honeycombRefusal(new ItemStack(Items.HONEYCOMB), cow) == null, "a cow's body has no honeycomb line");
        done(h, p);
    }

    /** The Body Locker: the happy ghast does not fit, a heart-bound creaking is the heart's, a copper golem keeps. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform", timeoutTicks = 200)
    public static void theBodyLocker(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "locker", FLOOR.south(4));
        BlockPos at = FLOOR.north(2);
        h.setBlock(at, ModRegistry.BODY_LOCKER.get());
        var locker = (BodyLockerBlockEntity) h.getBlockEntity(at);
        Mob ghast = host(h, p, "happy_ghast", FLOOR.north(2).east(3));
        locker.use(p);
        refused(h, p, "locker.too_happy", "a happy ghast at the locker");
        h.assertFalse(locker.full() || ghast.isRemoved(), "a happy ghast was stored");
        ghast.discard();
        // a creaking bound to a heart (a heart there, so it stays standing a tick) is refused; a heartless one is a body
        h.setBlock(FLOOR.west(5), block("creaking_heart"));
        Mob bound = host(h, p, "creaking", FLOOR.north(1).west(1));
        home(h, bound, FLOOR.west(5));
        locker.use(p);
        refused(h, p, "locker.heart", "a heart-bound creaking at the locker");
        h.assertFalse(locker.full(), "a heart-bound creaking was stored");
        bound.discard();
        Mob loose = host(h, p, "creaking", FLOOR.north(1).west(1));
        locker.use(p);
        h.assertTrue(locker.full() && loose.isRemoved(), "a heartless creaking host was not stored");
        locker.use(p);
        h.assertFalse(locker.full(), "the creaking was not let out");
        h.getEntities(SuppliedSpeciesGameTests.type("creaking")).forEach(e -> e.discard());
        // a copper golem: stored with its own line, and its weathering clock stops while it is inside
        Mob golem = host(h, p, "copper_golem", FLOOR.north(1).east(1));
        h.assertValueEqual(SuppliedMachines.storedLine(golem), "message.headinajar.locker_stored_copper", "the golem's line");
        long next = h.getLevel().getGameTime() + 100_000;
        setWeatherAge(golem, next);
        locker.use(p);
        h.assertTrue(locker.full() && golem.isRemoved(), "the copper golem host was not stored");
        long in = h.getLevel().getGameTime();
        h.runAfterDelay(20, () -> {
            long out = h.getLevel().getGameTime();
            locker.use(p);
            h.assertFalse(locker.full(), "the golem was not let out");
            Mob back = (Mob) h.getEntities(SuppliedSpeciesGameTests.type("copper_golem")).stream().filter(e -> !e.isRemoved()).findFirst()
                    .orElseThrow(() -> new GameTestAssertException("no golem came back"));
            h.assertValueEqual(weatherAge(back), next + (out - in), "the golem's next weathering moved on by its time inside");
            h.assertFalse(back.saveWithoutId(new CompoundTag()).contains(SuppliedMachines.CHECKED_IN), "the check-in mark left on the golem");
            done(h, p);
        });
    }

    /** The clock's arithmetic: waxed, unset and fully weathered golems stay as they are; other records are untouched. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void theLockerStopsTheWeatheringClock(GameTestHelper h) {
        for (long[] c : new long[][]{{5_000, 6_000}, {-2, -2}, {-1, -1}, {0, 0}}) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", SuppliedMachines.COPPER_GOLEM);
            tag.putLong("next_weather_age", c[0]);
            tag.putLong(SuppliedMachines.CHECKED_IN, 100);
            CompoundTag out = SuppliedMachines.checkOut(tag, 1_100);
            h.assertValueEqual(out.getLong("next_weather_age"), c[1], "a clock at " + c[0] + " after 1000 ticks inside");
            h.assertFalse(out.contains(SuppliedMachines.CHECKED_IN), "the mark comes off");
            h.assertTrue(tag.contains(SuppliedMachines.CHECKED_IN), "the stored record is untouched until the body is out");
        }
        CompoundTag pig = new CompoundTag();
        pig.putString("id", "minecraft:pig");
        h.assertValueEqual(SuppliedMachines.checkOut(pig, 5_000), pig, "any other record comes out as stored");
        h.succeed();
    }

    /** The Galvanic Pile (resin), the Saline Mixer (a dried ghast), the Charging Dock (copper), the Array (a statue). */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void theMachinesSayNo(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "machines", FLOOR.south(4));
        Map<String, Object[]> cases = Map.of(
                "pile.resin", new Object[]{ModRegistry.GALVANIC_PILE.get(), "resin_clump", FLOOR.west(4)},
                "mixer.dried_ghast", new Object[]{ModRegistry.SALINE_MIXER.get(), "dried_ghast", FLOOR.west(1)},
                "dock.copper", new Object[]{ModRegistry.CHARGING_DOCK.get(), "copper_block", FLOOR.east(2)},
                "array.statue", new Object[]{ModRegistry.CEREBRAL_ARRAY.get(), "copper_golem_statue", FLOOR.east(5)});
        cases.forEach((reason, c) -> {
            BlockPos at = (BlockPos) c[2];
            h.setBlock(at, (Block) c[0]);
            ItemStack held = stack((String) c[1]);
            useOn(h, p, held, at);
            refused(h, p, reason, c[1] + " on " + BuiltInRegistries.BLOCK.getKey((Block) c[0]));
            h.assertTrue(p.getMainHandItem().getCount() == 1, c[1] + " was used up at " + reason);
            h.assertTrue(p.containerMenu == p.inventoryMenu, c[1] + " opened a screen at " + reason);
        });
        // resin's block too (another player: the same line twice within the debounce is said once)
        ServerPlayer q = TestPlayers.at(h, "machines", FLOOR.south(5));
        useOn(h, q, stack("resin_block"), FLOOR.west(4));
        refused(h, q, "pile.resin", "a resin block at the pile");
        done(h, p, q);
    }

    /** The Surgical Wrench on a copper golem, and the saw on its statue. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void copperIsNotATeslaJob(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "copper", FLOOR.south(4));
        Mob golem = SuppliedSpeciesGameTests.spawn(h, "copper_golem", FLOOR.east(3));
        h.assertTrue(use(p, new ItemStack(ModRegistry.SURGICAL_WRENCH.get()), golem), "the wrench on a golem was not stopped");
        refused(h, p, "wrench.copper_golem", "the wrench on a copper golem");
        Mob hosted = host(h, p, "copper_golem", FLOOR.west(3));
        h.assertTrue(use(p, new ItemStack(ModRegistry.SURGICAL_WRENCH.get()), hosted), "the wrench on a golem host was not stopped");
        BlockPos at = FLOOR.north(3);
        h.setBlock(at, block("copper_golem_statue"));
        BlockState before = h.getBlockState(at);
        useOn(h, p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), at);
        refused(h, p, "saw.statue", "the saw on a statue");
        h.assertValueEqual(h.getBlockState(at), before, "the saw changed the statue (its pose)");
        done(h, p);
    }

    /** A mecha takes no copper for plating, no harness and no nautilus armour. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void aMechaIsNotANautilus(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "mecha", FLOOR.south(4));
        MechaEntity m = h.spawn(ModRegistry.mechaType(MechaFrame.HOPPER), FLOOR.north(2));
        m.setOwner(p.getUUID());
        Map<String, String> cases = Map.of("copper_nugget", "mecha.copper", "copper_block", "mecha.copper", "copper_helmet", "mecha.copper",
                "white_harness", "mecha.harness", "iron_nautilus_armor", "mecha.nautilus_armor");
        cases.forEach((path, reason) -> {
            h.assertTrue(use(p, stack(path), m), path + " on a mecha was not stopped");
            h.assertValueEqual(SuppliedMachines.mechaRefusal(stack(path)), reason, path + " on a mecha");
            h.assertTrue(Language.getInstance().has(Refusals.PREFIX + reason), "the line " + reason);
        });
        h.assertTrue(SuppliedMachines.mechaRefusal(new ItemStack(Items.COPPER_INGOT)) == null, "a copper ingot (1.21.1's) is as it was");
        h.assertValueEqual(SuppliedMachines.frameName(m).getString(), Language.getInstance().getOrDefault("entity.headinajar.hopper"), "the frame named");
        done(h, p);
    }

    /** A jarred head does not go in a bundle, either way round; the bundle stays empty and the head stays in hand. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void aColleagueIsNotLuggage(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "bundle", FLOOR.south(4));
        var menu = p.inventoryMenu;
        // the head carried onto a bundle (inventory slot 9 is the menu's slot 9), with each button
        for (int button : new int[]{0, 1}) {
            p.getInventory().setItem(9, stack("bundle"));
            menu.setCarried(new ItemStack(ModRegistry.JARRED_HEAD.get()));
            menu.clicked(9, button, ClickType.PICKUP, p);
            BundleContents in = p.getInventory().getItem(9).get(DataComponents.BUNDLE_CONTENTS);
            h.assertTrue(in != null && in.isEmpty(), "a jarred head went into a bundle (button " + button + ")");
            h.assertTrue(menu.getCarried().is(ModRegistry.JARRED_HEAD.get()), "the head left the hand (button " + button + ")");
            refused(h, p, "bundle.colleague", "a head onto a bundle");
        }
        // a bundle carried onto a head
        p.getInventory().setItem(10, new ItemStack(ModRegistry.JARRED_HEAD.get()));
        ItemStack bundle = stack("red_bundle");
        menu.setCarried(bundle);
        menu.clicked(10, 0, ClickType.PICKUP, p);
        BundleContents in = menu.getCarried().get(DataComponents.BUNDLE_CONTENTS);
        h.assertTrue(in != null && in.isEmpty(), "a bundle took a jarred head");
        h.assertTrue(p.getInventory().getItem(10).is(ModRegistry.JARRED_HEAD.get()), "the head left its slot");
        // the rule is the bundle's only: the head still fits a shulker box (the check shulker boxes use)
        h.assertTrue(new ItemStack(ModRegistry.JARRED_HEAD.get()).canFitInsideContainerItems(), "a jarred head no longer fits a shulker box");
        menu.setCarried(ItemStack.EMPTY);
        done(h, p);
    }

    /** Leaving: a nautilus dried out, a creaking whose heart was broken or left behind, a copper golem retired as a statue. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform", timeoutTicks = 200)
    public static void theExitInterviews(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "leaving", FLOOR.south(4));
        String me = p.getUUID() + "|";
        // dried out: the nautilus's own damage on land
        Mob nautilus = host(h, p, "nautilus", FLOOR.east(4));
        nautilus.hurt(h.getLevel().damageSources().dryOut(), 1000);
        h.assertTrue(StaffEvents.exits().stream().anyMatch(s -> s.startsWith(me + "staff.headinajar.reason.dried_out|")), "no 'dried out': " + StaffEvents.exits());
        // retired: a statue stands where the golem stood and the golem is let go (as the game does it)
        Mob golem = host(h, p, "copper_golem", FLOOR.west(4));
        h.getLevel().setBlock(golem.blockPosition(), block("oxidized_copper_golem_statue"), 3);
        golem.discard();
        h.assertTrue(StaffEvents.exits().stream().anyMatch(s -> s.startsWith(me + "staff.headinajar.reason.retired|")), "no 'retired in place': " + StaffEvents.exits());
        // a copper golem let go anywhere else (a locker, a command) has not left
        Mob other = host(h, p, "copper_golem", FLOOR.north(4));
        long before = StaffEvents.exits().stream().filter(s -> s.startsWith(me)).count();
        other.discard();
        h.assertValueEqual(StaffEvents.exits().stream().filter(s -> s.startsWith(me)).count(), before, "a discarded golem had an Exit Interview");
        // too far: the heart is there, the creaking 40 blocks off
        h.setBlock(FLOOR.north(5).east(5), block("creaking_heart"));
        Mob far = SuppliedSpeciesGameTests.spawn(h, "creaking", FLOOR.north(5).east(4));
        home(h, far, FLOOR.north(5).east(5));
        h.assertTrue(SuppliedMachines.creakingReason(h.getLevel(), far) == null, "a creaking beside its heart has no reason to leave");
        Vec3 away = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.north(5).east(5))).add(40, 0, 0);
        far.moveTo(away.x, away.y, away.z);
        h.assertValueEqual(SuppliedMachines.creakingReason(h.getLevel(), far), "staff.headinajar.reason.heart_far", "a creaking 40 off its heart");
        far.discard();
        // heart broken by the employer's own hand: the heart takes its creaking down with the breaker's blow, a death the
        // game reports; seen in the game as "Reason for leaving: management". It is the heart that was broken.
        h.setBlock(FLOOR.south(5).east(5), block("creaking_heart"));
        Mob struck = host(h, p, "creaking", FLOOR.south(5).east(3));
        home(h, struck, FLOOR.south(5).east(5));
        h.assertValueEqual(StaffEvents.reason(struck, p.damageSources().playerAttack(p), com.coolfreeze23.headinajar.staff.Staff.of(struck)), "staff.headinajar.reason.heart_broken",
                "a heart-bound creaking struck down with its heart");
        Mob loose = host(h, p, "creaking", FLOOR.south(5).west(3));
        h.assertValueEqual(StaffEvents.reason(loose, p.damageSources().playerAttack(p), com.coolfreeze23.headinajar.staff.Staff.of(loose)), "staff.headinajar.reason.management",
                "a creaking with no heart, killed by its employer");
        struck.discard();
        loose.discard();
        // heart broken: a host whose home has no heart falls in its own tick, and the interview goes out when it is gone
        Mob creaking = host(h, p, "creaking", FLOOR);
        home(h, creaking, FLOOR.south(5).west(5));
        h.succeedWhen(() -> {
            h.assertTrue(creaking.isRemoved(), "the heartless creaking is still here");
            h.assertTrue(StaffEvents.exits().stream().anyMatch(s -> s.startsWith(me + "staff.headinajar.reason.heart_broken|")),
                    "no 'its heart was broken': " + StaffEvents.exits());
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    /**
     * A jar at a creaking's body that is bound to its heart: the game lets nothing ride it, and the jar is told whose it
     * is ("Already employed by its heart."), not "Someone is already in that body" (seen in the game).
     */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void aHeartBoundBodyTakesNoJar(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "boarder", FLOOR.south(2));
        h.setBlock(FLOOR.east(4), block("creaking_heart"));
        Mob creaking = host(h, p, "creaking", FLOOR);
        home(h, creaking, FLOOR.east(4));
        com.coolfreeze23.headinajar.form.FormActions.detach(p, 3);
        p.interactOn(creaking, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == null, "the jar boarded a heart-bound creaking");
        h.assertValueEqual(com.coolfreeze23.headinajar.feedback.Refusals.lastDelivered(p), "lanyard.heart", "the line");
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** Every new Lookout's alarm is a sound the game has, so none needs the fallback (MachineTableGameTests has that). */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void theAlarmsAreRealSounds(GameTestHelper h) {
        for (String path : List.of("creaking", "happy_ghast", "copper_golem", "nautilus", "camel_husk", "sulfur_cube")) {
            EntityType<?> type = SuppliedSpeciesGameTests.type(path);
            ResourceLocation id = StaffTable.alarm(type);
            h.assertTrue(id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id), "the " + path + "'s alarm " + id + " is not a sound");
            h.assertValueEqual(TurretPedestalBlockEntity.alarmSound(h.getLevel(), type), BuiltInRegistries.SOUND_EVENT.get(id), "the " + path + "'s alarm");
        }
        h.succeed();
    }
}
