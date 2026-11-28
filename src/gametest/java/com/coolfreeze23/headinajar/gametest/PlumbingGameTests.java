package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.block.BodyLockerBlockEntity;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.block.SeparationChairBlock;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.host.Unseating;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.SeparationChairMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.work.Shuttle;
import com.coolfreeze23.headinajar.supplier.Discontinued;
import com.coolfreeze23.headinajar.supplier.Equipment;
import com.coolfreeze23.headinajar.supplier.Records;
import com.coolfreeze23.headinajar.supplier.SuppliedContainers;
import com.coolfreeze23.headinajar.supplier.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The plumbing (0.8.0) as it holds with or without a supplier: the slot adapter, the stamps on stored records (and that
 * a record written before stamps loads and saves exactly as it was), discontinued lines (a creature id this game has
 * never heard of), and every row of the Unseating Policy, reached by marking a rider as a lance's hit would. The tests
 * that need the supplier's copper chests, shelves and spears are {@link SuppliedPlumbingGameTests}.
 * {@code platform}: 15 x 6 x 15, the floor's top at helper y 1.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class PlumbingGameTests {
    static final BlockPos FLOOR = new BlockPos(7, 2, 7);
    /** A creature id no game registers. */
    static final String GONE = "headinajar:discontinued_test_creature";

    static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer player = TestPlayers.at(h, "Plumb", at);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** Makes a host the way the saw does. */
    static <T extends Mob> T host(GameTestHelper h, EntityType<T> type, BlockPos at, ServerPlayer sawyer) {
        T mob = h.spawn(type, at);
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "saw failed on " + type);
        return mob;
    }

    /** A jar riding a fresh host of this type. */
    static <T extends Mob> T hosted(GameTestHelper h, ServerPlayer p, EntityType<T> type, BlockPos at) {
        T host = host(h, type, at, p);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, host), "could not mount the " + type);
        return host;
    }

    static ItemStack module(MechaModule m) {
        ItemStack stack = new ItemStack(ModRegistry.mechaModule(m));
        MechaModuleItem.setSlider(stack, 50);
        return stack;
    }

    /** A mecha of this frame with these modules, owned and piloted by the player. */
    static MechaEntity mecha(GameTestHelper h, ServerPlayer pilot, MechaFrame frame, BlockPos at, ItemStack... mods) {
        MechaEntity m = h.spawn(ModRegistry.mechaType(frame), at);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < m.frame().totalSlots(); i++) slots.add(ItemStack.EMPTY);
        for (ItemStack stack : mods) {
            MechaModule type = ((MechaModuleItem) stack.getItem()).type();
            int first = m.frame().firstIndex(type.slot);
            for (int i = first; i < first + m.frame().slots(type.slot); i++) {
                if (slots.get(i).isEmpty()) {
                    slots.set(i, stack);
                    break;
                }
            }
        }
        m.setModules(slots);
        m.setOwner(pilot.getUUID());
        m.setBattery(m.batteryCapacity());
        if (FormActions.state(pilot).form() == Form.BODY) FormActions.detach(pilot, 3);
        h.assertTrue(FormActions.mountHost(pilot, m), "could not board");
        return m;
    }

    /** As a lance's hit marks its target: by a zombie, with whatever it holds. */
    static void lance(GameTestHelper h, Entity target) {
        Zombie by = h.spawn(EntityType.ZOMBIE, FLOOR.north(3));
        by.setNoAi(true);
        Unseating.mark(target, by, by.getMainHandItem());
    }

    // --- 1.4 the slot adapter ----------------------------------------------------------------------------------------

    /** What a creature wears is found by what it is, in whatever slot it is kept; hands are not worn. */
    @GameTest(template = "platform")
    public static void theSlotAdapterFindsWhatIsWornInAnySlot(GameTestHelper h) {
        Zombie z = h.spawn(EntityType.ZOMBIE, FLOOR);
        z.setNoAi(true);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.BODY}) {
            for (EquipmentSlot s : EquipmentSlot.values()) z.setItemSlot(s, ItemStack.EMPTY);
            z.setItemSlot(slot, new ItemStack(Items.POPPY));
            h.assertTrue(Equipment.poppy(z).is(Items.POPPY), "the poppy in " + slot + " was not found");
            h.assertTrue(Equipment.worn(z, ItemTags.SMALL_FLOWERS).is(Items.POPPY), "the poppy in " + slot + " was not found by its tag");
            h.assertValueEqual(Equipment.wornSlot(z, s -> s.is(Items.POPPY)), slot, "the slot it is in");
        }
        for (EquipmentSlot s : EquipmentSlot.values()) z.setItemSlot(s, ItemStack.EMPTY);
        z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.POPPY));
        h.assertTrue(Equipment.poppy(z).isEmpty(), "a poppy held in the hand was taken as worn");
        h.assertTrue(Equipment.anywhere(z, s -> s.is(Items.POPPY)).is(Items.POPPY), "anywhere() did not look in the hands");
        h.assertTrue(Equipment.harness(z).isEmpty() && Equipment.swallowed(z).isEmpty(), "a zombie wears a harness or has swallowed a block");
        h.succeed();
    }

    // --- 1.6 stored records ------------------------------------------------------------------------------------------

    /** A head cut now carries this game's data version beside its record, and the record has no stamp in it. */
    @GameTest(template = "platform")
    public static void aJarredHeadIsStampedBesideItsRecord(GameTestHelper h) {
        h.assertValueEqual(Records.now(), Records.UNSTAMPED, "this game's data version (1.21.1)");
        Cow cow = h.spawn(EntityType.COW, FLOOR);
        ItemStack head = JarredHeadItem.of(cow);
        CompoundTag tag = head.get(DataComponents.CUSTOM_DATA).copyTag();
        h.assertValueEqual(tag.getInt("EntityDataVersion"), Records.now(), "the stamp beside the record");
        h.assertFalse(tag.getCompound("Entity").contains("EntityDataVersion") || tag.getCompound("Entity").contains("DataVersion"), "the stamp went into the record");
        // placed and broken, the stamp goes with it
        h.setBlock(FLOOR, ModRegistry.JARRED_HEAD_BLOCK.get());
        JarredHeadBlockEntity be = (JarredHeadBlockEntity) h.getBlockEntity(FLOOR);
        be.load(tag);
        h.assertValueEqual(be.entityStamp(), Records.now(), "the placed head's stamp");
        CompoundTag back = be.toItem().get(DataComponents.CUSTOM_DATA).copyTag();
        h.assertValueEqual(back.getInt("EntityDataVersion"), Records.now(), "the stamp after breaking it");
        h.assertValueEqual(back.getCompound("Entity"), tag.getCompound("Entity"), "the record after breaking it");
        h.succeed();
    }

    /** A 0.7.2 jarred head (no stamp) places, saves and breaks with no number changed and no stamp added. */
    @GameTest(template = "platform")
    public static void anOldJarredHeadLoadsWithNoNumberChanged(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        Cow cow = h.spawn(EntityType.COW, FLOOR.east(3));
        CompoundTag entity = new CompoundTag();
        cow.saveWithoutId(entity);
        entity.remove("Passengers");
        CompoundTag old = new CompoundTag();
        old.putString("Type", "minecraft:cow");
        old.put("Entity", entity.copy());
        old.putString("Jar", "headinajar:life_support_jar");
        old.putLong("LastRestock", 1234L);
        h.assertValueEqual(Records.stampOf(old, "Entity"), 0, "an old record has no stamp");
        h.assertValueEqual(Records.savedAt(old, "Entity"), Records.UNSTAMPED, "an old record was written by 1.21.1");
        ItemStack item = new ItemStack(ModRegistry.JARRED_HEAD.get());
        CustomData.set(DataComponents.CUSTOM_DATA, item, old.copy());
        // placed by the real use path
        p.setItemInHand(InteractionHand.MAIN_HAND, item);
        BlockPos below = h.absolutePos(FLOOR.below());
        InteractionResult r = item.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false)));
        h.assertTrue(r.consumesAction(), "the old head was not placed: " + r);
        JarredHeadBlockEntity be = (JarredHeadBlockEntity) h.getBlockEntity(FLOOR);
        h.assertValueEqual(be.entityStamp(), 0, "the placed old head was given a stamp");
        CompoundTag saved = be.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertFalse(saved.contains("EntityDataVersion"), "saving the old head added a stamp");
        h.assertValueEqual(saved.getCompound("Entity"), entity, "the record changed on saving");
        JarredHeadBlockEntity again = new JarredHeadBlockEntity(be.getBlockPos(), be.getBlockState());
        again.loadWithComponents(saved, h.getLevel().registryAccess());
        CompoundTag back = again.toItem().get(DataComponents.CUSTOM_DATA).copyTag();
        h.assertFalse(back.contains("EntityDataVersion"), "breaking the old head added a stamp");
        h.assertValueEqual(back.getCompound("Entity"), entity, "the record changed on breaking");
        h.assertValueEqual(back.getLong("LastRestock"), 1234L, "the restock day changed");
        h.assertValueEqual(back.getString("Jar"), "headinajar:life_support_jar", "the jar changed");
        done(h, p);
    }

    /** The Body Locker, the Gantry, the Time Clock's arrivals and the shuttle: old records stay unstamped, new ones are. */
    @GameTest(template = "platform")
    public static void everyStoredRecordKeepsItsAge(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        CompoundTag pig = new CompoundTag();
        pig.putString("id", "minecraft:pig");
        pig.putFloat("Health", 7);
        BlockPos at = h.absolutePos(FLOOR);
        // the locker, as 0.7.2 saved it
        BodyLockerBlockEntity locker = new BodyLockerBlockEntity(at, ModRegistry.BODY_LOCKER.get().defaultBlockState());
        CompoundTag l = new CompoundTag();
        l.put("Stored", pig.copy());
        l.putString("StoredName", "Pig");
        locker.loadWithComponents(l, reg);
        CompoundTag ls = locker.saveWithoutMetadata(reg);
        h.assertTrue(ls.getCompound("Stored").equals(pig) && !ls.contains("StoredDataVersion"), "the locker's old body: " + ls);
        // the Gantry's build, as 0.7.2 saved it, and with a stamp
        AssemblyGantryBlockEntity gantry = new AssemblyGantryBlockEntity(at, ModRegistry.ASSEMBLY_GANTRY.get().defaultBlockState());
        CompoundTag g = new CompoundTag();
        g.put("PendingBuild", pig.copy());
        gantry.loadWithComponents(g, reg);
        CompoundTag gs = gantry.saveWithoutMetadata(reg);
        h.assertTrue(gs.getCompound("PendingBuild").equals(pig) && !gs.contains("PendingBuildDataVersion"), "the Gantry's old build: " + gs);
        g.putInt("PendingBuildDataVersion", 4000);
        gantry.loadWithComponents(g, reg);
        h.assertValueEqual(gantry.saveWithoutMetadata(reg).getInt("PendingBuildDataVersion"), 4000, "the Gantry kept its build's age");
        // the Time Clock's arrivals: an old one, then one arriving now
        TimeClockBlockEntity clock = new TimeClockBlockEntity(at, WorkRegistry.TIME_CLOCK.get().defaultBlockState());
        CompoundTag c = new CompoundTag();
        ListTag arrivals = new ListTag();
        arrivals.add(pig.copy());
        c.put("Arrivals", arrivals);
        clock.loadWithComponents(c, reg);
        CompoundTag cs = clock.saveWithoutMetadata(reg);
        h.assertTrue(cs.getList("Arrivals", 10).size() == 1 && cs.getList("Arrivals", 10).getCompound(0).equals(pig) && !cs.contains("ArrivalsDataVersion"),
                "the clock's old arrival: " + cs);
        clock.arrive(pig.copy());
        int[] stamps = clock.saveWithoutMetadata(reg).getIntArray("ArrivalsDataVersion");
        h.assertTrue(stamps.length == 2 && stamps[0] == 0 && stamps[1] == Records.now(), "the arrivals' stamps: " + java.util.Arrays.toString(stamps));
        // the shuttle's records
        Shuttle.Data shuttle = new Shuttle.Data();
        shuttle.hold(GlobalPos.of(h.getLevel().dimension(), at), pig.copy(), 0);
        shuttle.hold(GlobalPos.of(h.getLevel().dimension(), at), pig.copy(), 4000);
        ListTag w = shuttle.save(new CompoundTag(), reg).getList("Waiting", 10);
        h.assertTrue(w.size() == 2 && !w.getCompound(0).contains("EntityDataVersion") && w.getCompound(1).getInt("EntityDataVersion") == 4000,
                "the shuttle's records: " + w);
        h.succeed();
    }

    /** A body checked into the locker now is stamped. */
    @GameTest(template = "platform")
    public static void aLockedBodyIsStamped(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        h.setBlock(FLOOR, ModRegistry.BODY_LOCKER.get());
        BodyLockerBlockEntity locker = (BodyLockerBlockEntity) h.getBlockEntity(FLOOR);
        Pig pig = host(h, EntityType.PIG, FLOOR.south(2), p);
        locker.use(p);
        h.assertTrue(locker.full() && !pig.isAlive(), "the pig was not checked in: " + Refusals.lastDelivered(p));
        h.assertValueEqual(locker.saveWithoutMetadata(h.getLevel().registryAccess()).getInt("StoredDataVersion"), Records.now(), "the locked body's stamp");
        done(h, p);
    }

    // --- 1.6 discontinued lines --------------------------------------------------------------------------------------

    static ItemStack discontinuedHead() {
        ItemStack item = new ItemStack(ModRegistry.JARRED_HEAD.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", GONE);
        CompoundTag entity = new CompoundTag();
        entity.putFloat("Health", 10);
        entity.putString("CustomName", "\"Brenda\"");
        tag.put("Entity", entity);
        tag.putString("Jar", "headinajar:life_support_jar");
        CustomData.set(DataComponents.CUSTOM_DATA, item, tag);
        return item;
    }

    /** A head of a creature this game lacks keeps its record, says it is discontinued, and is not seated. */
    @GameTest(template = "platform")
    public static void aDiscontinuedHeadKeepsItsRecordAndIsNotSeated(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        ItemStack item = discontinuedHead();
        CompoundTag before = item.get(DataComponents.CUSTOM_DATA).copyTag();
        h.assertTrue(JarredHeadItem.discontinued(item) && JarredHeadItem.type(item) == null, "not taken as discontinued");
        h.assertFalse(JarredHeadItem.discontinued(JarredHeadItem.of(h.spawn(EntityType.COW, FLOOR.east(3)))), "a cow's head is discontinued");
        h.assertFalse(JarredHeadItem.discontinued(new ItemStack(ModRegistry.JARRED_HEAD.get())), "a head with no record is discontinued");
        List<Component> tooltip = new ArrayList<>();
        item.getItem().appendHoverText(item, Item.TooltipContext.of(h.getLevel()), tooltip, TooltipFlag.NORMAL);
        var line = tooltip.stream().map(Component::getContents)
                .filter(c -> c instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("item.headinajar.jarred_head.discontinued"))
                .map(c -> (net.minecraft.network.chat.contents.TranslatableContents) c).findFirst().orElse(null);
        h.assertTrue(line != null, "no discontinued line: " + tooltip);
        h.assertTrue(line.getArgs().length == 1 && line.getArgs()[0] instanceof Component species && species.getString().equals("Discontinued Test Creature"),
                "the species is not named: " + java.util.Arrays.toString(line.getArgs()));
        // it cannot be placed, so the tooltip does not say "Place it anywhere."
        h.assertFalse(tooltip.stream().anyMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals("item.headinajar.jarred_head.how")), "a discontinued head says to place it: " + tooltip);
        // not placed: a refusal, nothing on the floor, the item and its record as they were
        p.setItemInHand(InteractionHand.MAIN_HAND, item);
        BlockPos below = h.absolutePos(FLOOR.below());
        InteractionResult r = item.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false)));
        h.assertFalse(r.consumesAction(), "a discontinued head was seated");
        h.assertValueEqual(Refusals.lastDelivered(p), "head.discontinued", "the refusal");
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(FLOOR)).isAir(), "something was placed");
        h.assertTrue(item.getCount() == 1 && item.get(DataComponents.CUSTOM_DATA).copyTag().equals(before), "the item or its record changed");
        done(h, p);
    }

    /** A placed head of a creature this game lacks stands empty and is on leave; broken, it keeps everything. */
    @GameTest(template = "platform")
    public static void aPlacedDiscontinuedHeadIsOnLeave(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.JARRED_HEAD_BLOCK.get());
        JarredHeadBlockEntity be = (JarredHeadBlockEntity) h.getBlockEntity(FLOOR);
        CompoundTag tag = discontinuedHead().get(DataComponents.CUSTOM_DATA).copyTag();
        be.load(tag);
        h.assertTrue(be.entityType() == null && be.onLeave(), "not on leave");
        CompoundTag back = be.toItem().get(DataComponents.CUSTOM_DATA).copyTag();
        h.assertValueEqual(back.getString("Type"), GONE, "the species was lost");
        h.assertValueEqual(back.getCompound("Entity"), tag.getCompound("Entity"), "the record was lost");
        h.setBlock(FLOOR.east(), ModRegistry.JARRED_HEAD_BLOCK.get());
        JarredHeadBlockEntity cow = (JarredHeadBlockEntity) h.getBlockEntity(FLOOR.east());
        cow.load(JarredHeadItem.of(h.spawn(EntityType.COW, FLOOR.east(3))).get(DataComponents.CUSTOM_DATA).copyTag());
        h.assertFalse(cow.onLeave(), "a cow's head is on leave");
        h.succeed();
    }

    static CompoundTag staffRecord(String id, UUID employer, String name) {
        StaffFile f = new StaffFile();
        f.number = 900 + name.length();
        f.species = id;
        f.employer = employer;
        f.customName = name;
        CompoundTag attached = new CompoundTag();
        attached.put("headinajar:staff", f.save());
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.put("neoforge:attachments", attached);
        return tag;
    }

    /**
     * Staff the game drops on loading (their species is gone), and anyone riding them, are listed in one memo at their
     * employer's next login; staff of a species it has load as ever and are not listed.
     */
    @GameTest(template = "platform")
    public static void staffTheGameDropsAreListedOnce(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        CompoundTag brenda = staffRecord(GONE, p.getUUID(), "Brenda");
        UUID brendaId = UUID.randomUUID();
        brenda.putUUID("UUID", brendaId);
        // she was on her employer's books (the headcount the Lanyard counts)
        var index = com.coolfreeze23.headinajar.staff.work.WorkerIndex.get(h.getLevel().getServer());
        index.seen(p.getUUID(), brendaId, 905, GONE, net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(FLOOR)));
        int headcount = index.count(p.getUUID());
        ListTag riders = new ListTag();
        riders.add(staffRecord("minecraft:cow", p.getUUID(), "Colin"));
        brenda.put("Passengers", riders);
        CompoundTag dennis = staffRecord("minecraft:cow", p.getUUID(), "Dennis");
        dennis.putUUID("UUID", UUID.randomUUID());
        // through the game's own loading: vanilla drops Brenda (and Colin on her), and loads Dennis
        List<Entity> loaded = EntityType.loadEntitiesRecursive(List.of(brenda, dennis), h.getLevel()).toList();
        h.assertTrue(loaded.size() == 1 && loaded.get(0).getType() == EntityType.COW, "the game loaded " + loaded);
        loaded.forEach(Entity::discard);
        Discontinued.file(h.getLevel().getServer());
        h.assertValueEqual(Discontinued.Register.get(h.getLevel().getServer()).waiting(p.getUUID()), 2, "listed for the employer");
        h.assertTrue(index.of(p.getUUID()).stream().noneMatch(e -> e.id.equals(brendaId)), "Brenda is still on her employer's books");
        h.assertValueEqual(index.count(p.getUUID()), headcount - 1, "the headcount after she went");
        int before = Memos.told(p, Memos.Memo.DISCONTINUED);
        h.assertTrue(Discontinued.tell(p), "the memo did not go out");
        h.assertValueEqual(Memos.told(p, Memos.Memo.DISCONTINUED), before + 1, "memos sent");
        h.assertValueEqual(Discontinued.Register.get(h.getLevel().getServer()).waiting(p.getUUID()), 0, "the list was kept after the memo");
        h.assertFalse(Discontinued.tell(p), "a second memo for the same staff");
        h.assertFalse(Memos.sent(p, Memos.Memo.DISCONTINUED), "the memo was marked as had: it could never come again");
        done(h, p);
    }

    /** A host the game drops at its rider's login is the rider's to be told about, by species. */
    @GameTest(template = "platform")
    public static void aRiddenHostTheGameDropsIsTheRiders(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        CompoundTag host = new CompoundTag();
        host.putString("id", GONE);
        CompoundTag attached = new CompoundTag();
        CompoundTag data = new CompoundTag();
        data.putBoolean("host", true);
        attached.put("headinajar:host", data);
        host.put("neoforge:attachments", attached);
        Discontinued.noteForTest(host, p.getUUID());
        Discontinued.noteForTest(host, null);
        Discontinued.file(h.getLevel().getServer());
        h.assertValueEqual(Discontinued.Register.get(h.getLevel().getServer()).waiting(p.getUUID()), 1, "the rider's host was not listed (or listed twice)");
        Discontinued.Register.get(h.getLevel().getServer()).release(p.getUUID());
        done(h, p);
    }

    /** The memos' bits are saved: the new ones come after the species helper's, in order. */
    @GameTest(template = "platform")
    public static void theNewMemosKeepTheirBits(GameTestHelper h) {
        h.assertValueEqual(Memos.Memo.NEW_ARRIVAL.ordinal(), 28, "New Arrival");
        h.assertValueEqual(Memos.Memo.UNSEATED.ordinal(), 29, "Unseated");
        h.assertValueEqual(Memos.Memo.DISCONTINUED.ordinal(), 30, "Discontinued");
        h.assertTrue(Memos.Memo.values().length <= 32, "more memos than bits");
        h.succeed();
    }

    // --- 1.3 capability providers (the part that holds without a supplier) --------------------------------------------

    /** The copper chests and the shelf are reached by id: exactly when they are registered. */
    @GameTest(template = "platform")
    public static void theSuppliedContainersAreThereExactlyWhenRegistered(GameTestHelper h) {
        boolean copper = net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.parse("minecraft:copper_chest"));
        h.assertValueEqual(SuppliedContainers.copperChests().size(), copper ? 8 : 0, "copper chests");
        h.assertTrue((SuppliedContainers.shelf() != null) == Supplier.registered(net.minecraft.resources.ResourceLocation.parse("block_entity_type"),
                net.minecraft.resources.ResourceLocation.parse("minecraft:shelf")), "the shelf");
        h.succeed();
    }

    // --- 1.5 the Unseating Policy, row by row ------------------------------------------------------------------------

    /** Pilot of a mecha: the restraint holds against a lance; an ordinary dismount (a tick later, no lance) is as before. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aLancedPilotHolds(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, p, MechaFrame.HOPPER, FLOOR, module(MechaModule.HYDRAULIC_LEGS));
        lance(h, p);
        p.stopRiding();
        h.assertTrue(p.getVehicle() == m && FormActions.state(p).form() == Form.PILOT, "the lance took the pilot out");
        h.assertValueEqual(Unseating.lastSaid(p), "pilot", "the line");
        h.runAfterDelay(1, () -> {
            p.stopRiding();
            h.assertTrue(p.getVehicle() == null, "an ordinary dismount (no lance) was held");
            done(h, p);
        });
    }

    /** The Passenger Pod holds; the Wheelhouse's bench is the game's (off you come). */
    @GameTest(template = "platform")
    public static void thePodHoldsAndTheBenchDoesNot(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR), q = player(h, FLOOR.east(2));
        MechaEntity m = mecha(h, p, MechaFrame.HOPPER, FLOOR, module(MechaModule.HYDRAULIC_LEGS), module(MechaModule.PASSENGER_POD));
        h.assertTrue(q.startRiding(m, true) && m.getPassengers().indexOf(q) == 1, "no one in the pod");
        lance(h, q);
        q.stopRiding();
        h.assertTrue(q.getVehicle() == m, "the lance took the passenger out of the pod");
        h.assertValueEqual(Unseating.lastSaid(q), "pod", "the pod's line");
        ServerPlayer r = player(h, FLOOR.south(4)), s = player(h, FLOOR.south(5));
        MechaEntity w = mecha(h, r, MechaFrame.WHEELHOUSE, FLOOR.south(4).east(3));
        h.assertTrue(s.startRiding(w, true) && w.getPassengers().indexOf(s) == 1, "no one on the bench");
        lance(h, s);
        s.stopRiding();
        h.assertTrue(s.getVehicle() == null, "the Wheelhouse's bench held");
        done(h, p, q, r, s);
    }

    /** In a Lunk: holds. */
    @GameTest(template = "platform")
    public static void theLunkHolds(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ShellEntity lunk = h.spawn(ModRegistry.LUNK.get(), FLOOR);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, lunk), "could not board the Lunk");
        lance(h, p);
        p.stopRiding();
        h.assertTrue(p.getVehicle() == lunk, "the Lunk fell over");
        h.assertValueEqual(Unseating.lastSaid(p), "lunk", "the line");
        done(h, p);
    }

    /** A hosted jar is unseated beside the body, takes one knock, is told by what, gets the memo; the body stands. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aLancedHostedJarIsUnseated(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Cow cow = hosted(h, p, EntityType.COW, FLOOR.east(2));
        cow.setCustomName(Component.literal("Bessie"));
        Hosts.data(cow).sync = 60;
        FormActions.state(p).setIntegrity(5);
        int memos = Memos.told(p, Memos.Memo.UNSEATED);
        lance(h, p);
        p.stopRiding();
        h.assertTrue(p.getVehicle() == null, "the jar held on");
        h.assertValueEqual(Unseating.lastSaid(p), "unseated", "the line");
        h.assertValueEqual(FormActions.state(p).integrity(), 4, "the knock");
        h.assertValueEqual(Memos.told(p, Memos.Memo.UNSEATED), memos + 1, "the memo");
        h.assertTrue(p.position().distanceTo(cow.position()) < 3, "the jar is not beside the body");
        h.runAfterDelay(2, () -> {
            h.assertValueEqual(FormActions.state(p).form(), Form.JAR, "the jar's form");
            h.assertTrue(cow.isAlive() && Hosts.isHost(cow) && Hosts.data(cow).sync == 60, "the body did not stand as it was");
            done(h, p);
        });
    }

    /** The knock is never the last of the glass. */
    @GameTest(template = "platform")
    public static void theKnockIsNeverTheLast(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        hosted(h, p, EntityType.PIG, FLOOR.east(2));
        FormActions.state(p).setIntegrity(1);
        lance(h, p);
        p.stopRiding();
        h.assertTrue(p.getVehicle() == null && FormActions.state(p).integrity() == 1 && p.isAlive(), "the last of the glass went");
        done(h, p);
    }

    /** A Rover is unseated as a host, with its own line. */
    @GameTest(template = "platform")
    public static void aRoverUnseatsAsAHost(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ShellEntity rover = h.spawn(ModRegistry.ROVER.get(), FLOOR);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, rover), "could not board the Rover");
        lance(h, p);
        p.stopRiding();
        h.assertTrue(p.getVehicle() == null, "the Rover held");
        h.assertValueEqual(Unseating.lastSaid(p), "unseated_shell", "the line");
        done(h, p);
    }

    /** {@code combat.lanceUnseatsHosts} off: hosts and Rovers hold too. */
    @GameTest(template = "platform")
    public static void withTheSwitchOffHostsHold(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR), q = player(h, FLOOR.south(4));
        Cow cow = hosted(h, p, EntityType.COW, FLOOR.east(2));
        ShellEntity rover = h.spawn(ModRegistry.ROVER.get(), FLOOR.south(4));
        FormActions.detach(q, 3);
        h.assertTrue(FormActions.mountHost(q, rover), "could not board the Rover");
        Config.LANCE_UNSEATS_HOSTS.set(false);
        try {
            lance(h, p);
            p.stopRiding();
            lance(h, q);
            q.stopRiding();
        } finally {
            Config.LANCE_UNSEATS_HOSTS.set(true);
        }
        h.assertTrue(p.getVehicle() == cow && q.getVehicle() == rover, "a host let go with the switch off");
        h.assertTrue("held".equals(Unseating.lastSaid(p)) && "held".equals(Unseating.lastSaid(q)), "the lines: " + Unseating.lastSaid(p) + ", " + Unseating.lastSaid(q));
        done(h, p, q);
    }

    /** Without a lance (or a tick late) a hosted jar gets off exactly as before: no knock, no line. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void noLanceNoPolicy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR), q = player(h, FLOOR.south(4));
        hosted(h, p, EntityType.COW, FLOOR.east(2));
        Pig pig = hosted(h, q, EntityType.PIG, FLOOR.south(4).east(2));
        FormActions.state(p).setIntegrity(5);
        FormActions.state(q).setIntegrity(5);
        p.stopRiding();
        h.assertTrue(p.getVehicle() == null && FormActions.state(p).integrity() == 5 && Unseating.lastSaid(p) == null, "an ordinary dismount was a lance's");
        lance(h, q);
        h.runAfterDelay(1, () -> {
            q.stopRiding();
            h.assertTrue(q.getVehicle() == null && FormActions.state(q).integrity() == 5 && Unseating.lastSaid(q) == null, "last tick's lance still counted");
            h.assertTrue(pig.isAlive(), "the pig");
            done(h, p, q);
        });
    }

    /** A vanilla mount in body form: the game's own rules, nothing said. */
    @GameTest(template = "platform")
    public static void aVanillaMountIsTheGames(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Pig pig = h.spawn(EntityType.PIG, FLOOR.east(2));
        h.assertTrue(p.startRiding(pig, true), "could not ride the pig");
        lance(h, p);
        p.stopRiding();
        h.assertTrue(p.getVehicle() == null && Unseating.lastSaid(p) == null, "a body-form rider was held or told");
        done(h, p);
    }

    /** The Separation Chair before the cut: a lance takes the patient out and the procedure is called off at once. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aLanceBeforeTheCutCallsOffTheProcedure(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        h.setBlock(FLOOR, ModRegistry.SEPARATION_CHAIR.get().defaultBlockState().setValue(SeparationChairBlock.FACING, Direction.NORTH));
        SeparationChairBlockEntity chair = (SeparationChairBlockEntity) h.getBlockEntity(FLOOR);
        chair.items().setStackInSlot(0, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        chair.items().setStackInSlot(1, new ItemStack(ModRegistry.RELAY_GOGGLES.get()));
        chair.items().setStackInSlot(2, new ItemStack(ModRegistry.COLLAR_CAM.get()));
        chair.items().setStackInSlot(3, new ItemStack(ModRegistry.SALINE_BOTTLE.get(), 4));
        chair.energy().set(50_000);
        SeatEntity seat = SeatEntity.at(h.getLevel(), h.absolutePos(FLOOR), Direction.NORTH);
        h.assertTrue(p.startRiding(seat, true), "could not sit");
        h.assertTrue(((SeparationChairMenu) chair.createMenu(1, p.getInventory(), p)).clickMenuButton(p, SeparationChairMenu.BUTTON_SIGN), "not started");
        h.runAfterDelay(10, () -> {
            h.assertTrue(chair.running() && chair.opTick() < SeparationChairBlockEntity.CUT_TICK, "setup: not before the cut");
            lance(h, p);
            p.stopRiding();
            h.assertTrue(p.getVehicle() == null, "the patient stayed in the chair");
            h.assertFalse(chair.running(), "the procedure went on");
            h.assertValueEqual(FormActions.state(p).form(), Form.BODY, "the head came off");
            done(h, p);
        });
    }

    /** A secondee's head rides nothing: DOLORES says so once to each employer. */
    @GameTest(template = "platform")
    public static void theCollarLineIsSaidOnce(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        h.assertTrue(Unseating.collarOnce(p), "the line was not said");
        h.assertFalse(Unseating.collarOnce(p), "the line was said twice");
        done(h, p);
    }

    /** A sword is no lance. */
    @GameTest(template = "platform")
    public static void aSwordIsNoLance(GameTestHelper h) {
        Zombie z = h.spawn(EntityType.ZOMBIE, FLOOR);
        z.setNoAi(true);
        z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        h.assertTrue(Unseating.lanceOf(z.damageSources().mobAttack(z)).isEmpty(), "a sword was taken for a lance");
        h.succeed();
    }
}
