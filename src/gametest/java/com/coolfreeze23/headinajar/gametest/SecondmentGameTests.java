package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.SecondRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.second.SecondeeData;
import com.coolfreeze23.headinajar.staff.second.Secondees;
import com.coolfreeze23.headinajar.staff.second.Secondment;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerIndex;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The Secondment Collar's interactions (0.7.1): fitting it only to a host, seating a jarred head (its file onto the
 * host, the body's kept), the heads that may not be seconded, unseating with file and grade kept, shears, a player's
 * jar on a seconded or an empty collared host, old hosts, the server switch, the pairing lines once per player, and a
 * secondee on the payroll at the head's wage with the body's habits. Every use goes through
 * {@code Secondment.instrument}, as a host's instruments are dispatched.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class SecondmentGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h) {
        return TestPlayers.at(h, "second", FLOOR.south(3));
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** A host made the saw's way (no jar in the off hand). */
    private static <T extends Mob> T host(GameTestHelper h, ServerPlayer p, EntityType<T> type, BlockPos at) {
        T mob = h.spawn(type, at);
        mob.setHealth(1);
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        h.assertTrue(SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "the saw makes a host of the " + type.getDescription().getString());
        return mob;
    }

    /** A jarred head made the saw's way (a Life Support Jar in the off hand), taken out of the player's inventory. */
    private static ItemStack head(GameTestHelper h, ServerPlayer p, EntityType<? extends Mob> type, BlockPos at) {
        Mob mob = h.spawn(type, at);
        mob.setHealth(1);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(ModRegistry.JARRED_HEAD.get()) && JarredHeadItem.type(s) == type) {
                p.getInventory().setItem(i, ItemStack.EMPTY);
                return s;
            }
        }
        throw new AssertionError("no jarred " + type.getDescription().getString() + " head");
    }

    /** Uses {@code stack} on the host through the collar's own path, as the player's main hand; returns the hand after. */
    private static ItemStack use(ServerPlayer p, ItemStack stack, Mob host) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Secondment.instrument(p, p, p.getMainHandItem(), host, InteractionHand.MAIN_HAND);
        return p.getMainHandItem();
    }

    private static ItemStack collar() {
        return new ItemStack(SecondRegistry.SECONDMENT_COLLAR.get());
    }

    private static void told(GameTestHelper h, ServerPlayer p, String key, String what) {
        h.assertValueEqual(Refusals.lastDelivered(p), key, what);
    }

    private static CompoundTag record(ItemStack head) {
        CustomData data = head.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    /** A collared cow host with a zombie head seated on it by {@code p}. */
    private static Mob secondee(GameTestHelper h, ServerPlayer p, BlockPos at) {
        Mob cow = host(h, p, EntityType.COW, at);
        use(p, collar(), cow);
        use(p, head(h, p, EntityType.ZOMBIE, at.west(3)), cow);
        h.assertTrue(Secondees.seated(cow), "the zombie head is seated");
        return cow;
    }

    // --- 1. the collar fits a host only -----------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void collarFitsAHostOnly(GameTestHelper h) {
        ServerPlayer p = player(h);
        // a living cow, through the game's own use event (before the cow's own use)
        Mob living = h.spawn(EntityType.COW, FLOOR.east(3));
        p.setItemInHand(InteractionHand.MAIN_HAND, collar());
        var event = new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, living);
        NeoForge.EVENT_BUS.post(event);
        h.assertTrue(event.isCanceled(), "the collar on a living cow is answered before the cow");
        told(h, p, "secondment.not_host", "a collar on a cow with its head");
        h.assertTrue(p.getMainHandItem().getCount() == 1 && !Secondees.collared(living), "the collar was kept");
        // a Utility Shell
        ShellEntity rover = h.spawn(ModRegistry.ROVER.get(), FLOOR.west(3));
        h.assertTrue(use(p, collar(), rover).getCount() == 1, "the shell took the collar");
        told(h, p, "secondment.machine", "a collar on a shell");
        // a host someone is riding
        Mob ridden = host(h, p, EntityType.PIG, FLOOR.north(3));
        Mob rider = h.spawn(EntityType.CHICKEN, FLOOR.north(3));
        rider.startRiding(ridden, true);
        h.assertTrue(use(p, collar(), ridden).getCount() == 1 && !Secondees.collared(ridden), "the ridden host took the collar");
        told(h, p, "secondment.ridden", "a collar on a ridden host");
        // a host: fitted, the collar used
        Mob cow = host(h, p, EntityType.COW, FLOOR.east(1));
        h.assertTrue(use(p, collar(), cow).isEmpty(), "the collar was not used up");
        SecondeeData d = Secondees.data(cow);
        h.assertTrue(d != null && d.collar && d.head.isEmpty() && p.getUUID().equals(d.employer), "the collar's record");
        // a collared host
        h.assertTrue(use(p, collar(), cow).getCount() == 1, "a second collar was used");
        told(h, p, "secondment.already", "a collar on a collared host");
        done(h, p);
    }

    /**
     * A head on a creature with its own head is answered (it was a press that did nothing), and so is a collar or a head
     * used on the jar of someone riding a host: that is the host, and it is taken.
     */
    @GameTest(template = "platform")
    public static void aHeadOnALivingCreatureOrARidersJarIsAnswered(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack zombie = head(h, p, EntityType.ZOMBIE, FLOOR.west(3));
        Mob living = h.spawn(EntityType.COW, FLOOR.east(3));
        p.setItemInHand(InteractionHand.MAIN_HAND, zombie);
        var onCow = new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, living);
        NeoForge.EVENT_BUS.post(onCow);
        h.assertTrue(onCow.isCanceled(), "a head on a living cow is answered before the cow");
        told(h, p, "host.not_a_host", "a head on a cow with its own");
        h.assertTrue(p.getMainHandItem().getCount() == 1 && JarredHeadItem.type(p.getMainHandItem()) == EntityType.ZOMBIE, "the head was kept");
        // a player's jar riding a host: the collar and the head are both refused as for the ridden host
        Mob ridden = host(h, p, EntityType.PIG, FLOOR.north(3));
        ServerPlayer rider = TestPlayers.at(h, "rider", FLOOR.north(3));
        rider.startRiding(ridden, true);
        for (ItemStack held : new ItemStack[]{collar(), zombie}) {
            p.setItemInHand(InteractionHand.MAIN_HAND, held);
            var onRider = new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, rider);
            NeoForge.EVENT_BUS.post(onRider);
            h.assertTrue(onRider.isCanceled(), "a collar or head on the rider's jar is answered");
            told(h, p, "secondment.ridden", "a collar or head on the rider's jar");
            h.assertTrue(p.getMainHandItem().getCount() == 1 && !Secondees.collared(ridden), "nothing was used on the ridden host");
        }
        rider.stopRiding();
        done(h, p, rider);
    }

    /** The saw on a secondee points to unseating (a secondee is never let go, so the staff line would send one round). */
    @GameTest(template = "platform")
    public static void theSawOnASecondeeSaysUnseat(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = secondee(h, p, FLOOR.east(2));
        cow.setHealth(1);
        var verdict = com.coolfreeze23.headinajar.staff.SawTable.verdict(cow, p, ItemStack.EMPTY, true);
        h.assertValueEqual(verdict.reason(), "secondment.saw", "the saw's answer on a secondee");
        done(h, p);
    }

    // --- 2. a head seats ---------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aJarredHeadSeatsOnACollaredHost(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = host(h, p, EntityType.COW, FLOOR.east(2));
        StaffFile body = Staff.of(cow);
        use(p, collar(), cow);
        ItemStack zombie = head(h, p, EntityType.ZOMBIE, FLOOR.west(2));
        CompoundTag stored = record(zombie).getCompound("Entity").copy();
        StaffFile file = Staff.of(zombie);
        h.assertTrue(file != null && body != null, "the saw put both on file");
        h.assertTrue(use(p, zombie, cow).isEmpty(), "the head was not used up");
        SecondeeData d = Secondees.data(cow);
        h.assertTrue(d.seated() && JarredHeadItem.type(d.head) == EntityType.ZOMBIE, "the zombie head is seated");
        h.assertTrue(record(d.head).getCompound("Entity").equals(stored), "the stored zombie came through whole");
        h.assertTrue(d.teamSync == 0 && d.canister == 0 && d.glass == 10 && d.glassMax == 10, "team sync 0, canister 0, glass 10/10");
        StaffFile now = Staff.of(cow);
        h.assertTrue(now != null && now.number == file.number, "the host carries the head's file while it is seated");
        h.assertTrue(d.bodyFile != null && StaffFile.load(d.bodyFile).number == body.number, "the body's own file is kept");
        WorkerData w = Workforce.data(cow);
        h.assertTrue(w != null && w.state == WorkerData.State.AWAITING && p.getUUID().equals(w.employer), "the secondee awaits assignment");
        h.assertTrue(Workforce.name(cow).getString().equals(Staff.name(file).getString()), "lines name the head");
        // a villager head keeps its villager data
        Mob pig = host(h, p, EntityType.PIG, FLOOR.north(2));
        use(p, collar(), pig);
        ItemStack villager = head(h, p, EntityType.VILLAGER, FLOOR.south(1));
        CompoundTag data = record(villager).getCompound("Entity").getCompound("VillagerData").copy();
        use(p, villager, pig);
        h.assertTrue(Secondees.seated(pig) && Secondees.entityTag(Secondees.data(pig).head).getCompound("VillagerData").equals(data),
                "the villager head kept its villager data");
        done(h, p);
    }

    // --- 3. heads that may not be seconded --------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void headsThatMayNotBeSeconded(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = host(h, p, EntityType.COW, FLOOR.east(2));
        use(p, collar(), cow);
        h.assertTrue(use(p, new ItemStack(Items.PLAYER_HEAD), cow).getCount() == 1, "a player head was used");
        told(h, p, "secondment.player_head", "a player head");
        h.assertTrue(use(p, new ItemStack(Items.DRAGON_HEAD), cow).getCount() == 1, "a dragon head was used");
        told(h, p, "secondment.dragon", "the Dragon Head");
        h.assertTrue(use(p, new ItemStack(Items.SKELETON_SKULL), cow).getCount() == 1, "a skull was used");
        told(h, p, "secondment.trophy", "a skeleton skull");
        ItemStack creeper = head(h, p, EntityType.CREEPER, FLOOR.west(2));
        h.assertTrue(use(p, creeper, cow).getCount() == 1 && !Secondees.seated(cow), "the creeper head was seated");
        told(h, p, "secondment.creeper", "a creeper head");
        // someone else's head, with no trust
        ServerPlayer other = TestPlayers.at(h, "second", FLOOR.north(3));
        ItemStack theirs = head(h, other, EntityType.SKELETON, FLOOR.north(1));
        h.assertTrue(use(p, theirs, cow).getCount() == 1 && !Secondees.seated(cow), "someone else's head was seated");
        told(h, p, "secondment.others_head", "someone else's head");
        // the headcount
        WorkerIndex index = WorkerIndex.get(h.getLevel().getServer());
        UUID[] fake = new UUID[Config.get(Config.MAX_PER_PLAYER, 24)];
        for (int i = 0; i < fake.length; i++) {
            fake[i] = UUID.randomUUID();
            index.seen(p.getUUID(), fake[i], 9100 + i, "minecraft:cow", WorkerIndex.at(p));
        }
        try {
            ItemStack zombie = head(h, p, EntityType.ZOMBIE, FLOOR.south(1));
            h.assertTrue(use(p, zombie, cow).getCount() == 1 && !Secondees.seated(cow), "a head was seated over the headcount");
            told(h, p, "secondment.cap", "over the headcount");
        } finally {
            for (UUID id : fake) index.remove(p.getUUID(), id);
        }
        // a second head on a seconded host
        use(p, head(h, p, EntityType.ZOMBIE, FLOOR.south(2)), cow);
        h.assertTrue(Secondees.seated(cow), "the first head seats");
        h.assertTrue(use(p, head(h, p, EntityType.HUSK, FLOOR.east(4)), cow).getCount() == 1, "a second head was used");
        told(h, p, "secondment.second_head", "a second head");
        // a head on a host with no collar
        Mob pig = host(h, p, EntityType.PIG, FLOOR.west(4));
        h.assertTrue(use(p, head(h, p, EntityType.ZOMBIE, FLOOR.north(4)), pig).getCount() == 1, "a head went onto a bare neck");
        told(h, p, "secondment.no_collar", "a head with no collar");
        done(h, p, other);
    }

    // --- 4. unseating -------------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void unseatKeepsFileAndGrade(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = host(h, p, EntityType.COW, FLOOR.east(2));
        int bodyNumber = Staff.of(cow).number;
        use(p, collar(), cow);
        ItemStack zombie = head(h, p, EntityType.ZOMBIE, FLOOR.west(2));
        CompoundTag stored = record(zombie).getCompound("Entity").copy();
        StaffFile before = Staff.of(zombie);
        use(p, zombie, cow);
        Secondees.data(cow).glass = 7;
        p.setShiftKeyDown(true);
        use(p, ItemStack.EMPTY, cow);
        p.setShiftKeyDown(false);
        h.assertFalse(Secondees.seated(cow), "the head is still seated");
        h.assertTrue(Secondees.collared(cow), "the collar came off with the head");
        h.assertTrue(Workforce.data(cow) == null, "the body is still on the workforce");
        h.assertTrue(Staff.of(cow) != null && Staff.of(cow).number == bodyNumber, "the body has its own file back");
        ItemStack back = ItemStack.EMPTY;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(ModRegistry.JARRED_HEAD.get())) back = p.getInventory().getItem(i);
        }
        h.assertTrue(!back.isEmpty() && JarredHeadItem.type(back) == EntityType.ZOMBIE, "the head came back to the player");
        h.assertTrue(record(back).getCompound("Entity").equals(stored), "the stored zombie is unchanged");
        StaffFile after = Staff.of(back);
        h.assertTrue(after != null && after.number == before.number && after.employer.equals(before.employer), "file and number kept");
        h.assertTrue(after.history.size() == Math.min(StaffFile.HISTORY, before.history.size() + 2)
                        && after.history.get(after.history.size() - 1).k().equals("unseconded"),
                "the file gained the seat and the unseat: " + after.history);
        h.assertTrue(record(back).getInt(Secondment.GLASS) == 7, "the jar kept its glass");
        // shears take the empty collar back
        h.assertTrue(use(p, new ItemStack(Items.SHEARS), cow).is(Items.SHEARS), "the shears were used up");
        h.assertFalse(Secondees.collared(cow), "the collar is still on");
        h.assertTrue(p.getInventory().countItem(SecondRegistry.SECONDMENT_COLLAR.get()) == 1, "the collar came back");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void shearsWaitForTheHeadToComeOff(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = secondee(h, p, FLOOR.east(2));
        use(p, new ItemStack(Items.SHEARS), cow);
        told(h, p, "secondment.seated_collar", "shears on a seconded host");
        h.assertTrue(Secondees.seated(cow), "the shears took the collar off a seated head");
        done(h, p);
    }

    // --- 5. a player's jar ----------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void playerJarCannotBoardASecondee(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = secondee(h, p, FLOOR.east(2));
        FormActions.detach(p, 3);
        use(p, ItemStack.EMPTY, cow);
        told(h, p, "secondment.boarding_seconded", "a jar on a seconded host");
        h.assertFalse(FormActions.mountHost(p, cow), "the jar climbed onto a seconded host");
        h.assertTrue(p.getVehicle() == null, "the jar is riding");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void playerJarRidesAnEmptyCollaredHost(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = host(h, p, EntityType.COW, FLOOR.east(2));
        use(p, collar(), cow);
        FormActions.detach(p, 3);
        h.assertTrue(use(p, ItemStack.EMPTY, cow).isEmpty() && Secondment.instrument(p, p, ItemStack.EMPTY, cow, InteractionHand.MAIN_HAND) == null,
                "the empty collar answers a jar's climb itself");
        h.assertTrue(FormActions.mountHost(p, cow) && p.getVehicle() == cow, "the jar did not ride the empty collared host");
        done(h, p);
    }

    // --- 18. old hosts ---------------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void oldHostsLoadUnchanged(GameTestHelper h) throws Exception {
        // a cow host as 0.7.0 saved it: host data, no collar
        CompoundTag saved = TagParser.parseTag("{id:\"minecraft:cow\",Health:4.0f,\"neoforge:attachments\":{\"headinajar:host\":"
                + "{host:1b,freshness:73.5f,sync:41.25f,handshaken:1b}}}");
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.east(2)));
        Entity e = EntityType.loadEntityRecursive(saved, h.getLevel(), x -> {
            x.moveTo(at.x, at.y, at.z, 0, 0);
            return x;
        });
        h.assertTrue(e instanceof Mob && h.getLevel().addFreshEntity(e), "the old host loads");
        h.assertTrue(Hosts.isHost(e) && Hosts.data(e).freshness == 73.5f && Hosts.data(e).sync == 41.25f && Hosts.data(e).handshaken,
                "the old host's freshness, sync and handshake");
        h.assertTrue(Secondees.data(e) == null, "an old host has a collar");
        h.assertTrue(Workforce.data(e) == null, "an old host is staff");
        CompoundTag out = e.saveWithoutId(new CompoundTag());
        h.assertFalse(out.getCompound("neoforge:attachments").contains("headinajar:secondee"), "an old host saves a collar record");
        h.succeed();
    }

    // --- 19. the switch -----------------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void staffSecondmentOffRefusesTheCollar(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob seated = secondee(h, p, FLOOR.east(2));
        Mob cow = host(h, p, EntityType.COW, FLOOR.west(3));
        Config.SECONDMENT.set(false);
        try {
            h.assertTrue(use(p, collar(), cow).getCount() == 1 && !Secondees.collared(cow), "the collar was fitted with secondments off");
            told(h, p, "secondment.disabled", "the collar with secondments off");
        } catch (RuntimeException | AssertionError e) {
            Config.SECONDMENT.set(true);
            throw e;
        }
        Vec3 was = seated.position();
        h.runAfterDelay(60, () -> {
            try {
                h.assertTrue(Secondees.seated(seated), "a seated head was unseated when secondments went off");
                h.assertTrue(seated.position().distanceTo(was) < 0.2, "the secondee kept walking with secondments off");
            } finally {
                Config.SECONDMENT.set(true);
            }
            done(h, p);
        });
    }

    // --- 20. the pairings ------------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void pairingLinesOncePerPlayer(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob spider = host(h, p, EntityType.SPIDER, FLOOR.east(2));
        use(p, collar(), spider);
        String mine = p.getUUID() + "|skeleton_spider";
        long before = Secondment.said().stream().filter(mine::equals).count();
        use(p, head(h, p, EntityType.SKELETON, FLOOR.west(2)), spider);
        h.assertTrue(Secondment.said().stream().filter(mine::equals).count() == before + 1, "DOLORES said nothing of Security on the stairs");
        p.setShiftKeyDown(true);
        use(p, ItemStack.EMPTY, spider);
        p.setShiftKeyDown(false);
        ItemStack back = ItemStack.EMPTY;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(ModRegistry.JARRED_HEAD.get())) back = p.getInventory().removeItemNoUpdate(i);
        }
        use(p, back, spider);
        h.assertTrue(Secondees.seated(spider), "the skeleton head seats again");
        h.assertTrue(Secondment.said().stream().filter(mine::equals).count() == before + 1, "the pairing was said twice to one player");
        done(h, p);
    }

    // --- the head's skills, the body's habits, the payroll --------------------------------------------------------

    @GameTest(template = "platform")
    public static void aSecondeeIsOnThePayrollWithTheBodysHabits(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob cow = secondee(h, p, FLOOR.east(2));
        StaffFile f = Staff.of(cow);
        long day = Staff.day(h.getLevel());
        h.assertTrue(f.staff() && f.paidThrough >= day && f.lastDawn >= day, "the seconded head is not on the payroll: " + f.morale + " " + f.paidThrough);
        h.assertTrue(Secondees.payType(cow) == EntityType.ZOMBIE, "the wage is the body's, not the head's");
        WorkTable.Spec spec = WorkTable.of(cow);
        h.assertTrue(spec != null && !spec.quirk("burns") && !spec.nightShift() && Workforce.data(cow).shift == WorkerData.Shift.DAY,
                "a zombie head on a cow body burns, or is held to Night");
        h.assertTrue(spec.move().equals(WorkTable.ofSpecies(cow).move()) && spec.hands() == WorkTable.ofSpecies(cow).hands(),
                "the body's movement and hands");
        Mob skeleton = host(h, p, EntityType.SKELETON, FLOOR.west(3));
        use(p, collar(), skeleton);
        use(p, head(h, p, EntityType.COW, FLOOR.north(3)), skeleton);
        WorkTable.Spec bony = WorkTable.of(skeleton);
        h.assertTrue(bony != null && bony.quirk("burns") && bony.nightShift(), "a skeleton body does not burn by day under a cow head");
        done(h, p);
    }
}
