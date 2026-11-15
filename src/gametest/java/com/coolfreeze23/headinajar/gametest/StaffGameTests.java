package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity;
import com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity;
import com.coolfreeze23.headinajar.block.HeadJobs;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.block.MachineBlock;
import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.coolfreeze23.headinajar.clarity.MemoQueue;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.Band;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffBlocks;
import com.coolfreeze23.headinajar.staff.StaffCards;
import com.coolfreeze23.headinajar.staff.StaffEvents;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.StaffNet;
import com.coolfreeze23.headinajar.staff.StaffRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Personnel File (0.6.0): numbers, the file travelling with a head, old worlds, stations unchanged, pay, leaving, memos. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class StaffGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        return SawTableGameTests.player(h, at);
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        SawTableGameTests.done(h, players);
    }

    private static ItemStack jarredHead(GameTestHelper h, EntityType<? extends Mob> type, VillagerProfession job) {
        Mob mob = h.spawn(type, FLOOR.above(3));
        if (mob instanceof Villager v && job != null) v.setVillagerData(v.getVillagerData().setProfession(job));
        ItemStack stack = JarredHeadItem.of(mob);
        mob.discard();
        return stack;
    }

    /** A placed head, from an item, as the item's own use would place it (but without a placer). */
    private static JarredHeadBlockEntity plinth(GameTestHelper h, BlockPos at, ItemStack head) {
        h.setBlock(at, ModRegistry.JARRED_HEAD_BLOCK.get());
        JarredHeadBlockEntity be = (JarredHeadBlockEntity) h.getBlockEntity(at);
        CustomData data = head.get(DataComponents.CUSTOM_DATA);
        be.load(data == null ? new CompoundTag() : data.copyTag());
        return be;
    }

    /** Uses {@code stack} on the head as a player's click would reach the block. */
    private static void useOn(GameTestHelper h, ServerPlayer p, JarredHeadBlockEntity head, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = head.getBlockPos();
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        p.gameMode.useItemOn(p, h.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
    }

    // --- numbers and files -------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void theFirstCutNumbersTheCreature(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        SawTableGameTests.arm(p, ItemStack.EMPTY);
        Cow cow = SawTableGameTests.weak(h, EntityType.COW, FLOOR.east());
        int next = StaffRegister.get(h.getLevel().getServer()).peek();
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        StaffFile f = Staff.of(cow);
        h.assertTrue(f != null && f.number >= next, "the host has no number");
        h.assertTrue(p.getUUID().equals(f.employer), "the cutter is not the employer");
        h.assertValueEqual(f.history.get(f.history.size() - 1).k(), "host", "the history line");
        h.assertTrue(SawTableGameTests.earned(h, p, "staff_number"), "Staff Number was not earned");
        h.assertTrue(Memos.told(p, Memos.Memo.STAFF_NUMBER) == 1, "the first-number memo, told " + Memos.told(p, Memos.Memo.STAFF_NUMBER));
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aSampledCreatureKeepsItsNumberWhenSawn(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Cow cow = SawTableGameTests.weak(h, EntityType.COW, FLOOR.east());
        cow.setHealth(cow.getMaxHealth());
        ItemStack syringe = new ItemStack(ModRegistry.SYRINGE.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, syringe);
        ModRegistry.SYRINGE.get().draw(p, syringe, cow, InteractionHand.MAIN_HAND);
        StaffFile sampled = Staff.of(cow);
        h.assertTrue(sampled != null && sampled.employer == null, "a sampled cow has no file, or an employer");
        cow.setHealth(1);
        p.getInventory().clearContent();
        SawTableGameTests.arm(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        CompoundTag tag = SawTableGameTests.data(SawTableGameTests.head(p));
        StaffFile onHead = StaffFile.load(tag.get("Staff"));
        h.assertTrue(onHead != null && onHead.number == sampled.number, "the head's number is not the sample's");
        h.assertTrue(!tag.getCompound("Entity").getCompound(Staff.ATTACHED).contains(Staff.KEY), "the stored mob keeps a second copy");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void numbersCountUpAndAreSaved(GameTestHelper h) {
        var server = h.getLevel().getServer();
        StaffRegister reg = StaffRegister.get(server);
        int a = reg.take(), b = reg.take(), c = reg.take();
        h.assertTrue(b == a + 1 && c == b + 1, "numbers " + a + ", " + b + ", " + c);
        StaffRegister back = StaffRegister.load(reg.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(back.peek() == reg.peek(), "the next number was not saved");
        h.assertValueEqual(Staff.number(12), "0012", "four digits");
        h.assertValueEqual(Staff.number(12345), "12345", "five after 9999");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void anOldPlacedHeadIsRegisteredOnItsFirstTick(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.JARRED_HEAD_BLOCK.get());
        JarredHeadBlockEntity head = (JarredHeadBlockEntity) h.getBlockEntity(FLOOR);
        // as 0.5.4 saved it: Type, Entity, LastRestock and nothing else
        CompoundTag old = new CompoundTag();
        old.putString("Type", "minecraft:cow");
        old.put("Entity", new CompoundTag());
        old.putLong("LastRestock", 0);
        head.loadWithComponents(old, h.getLevel().registryAccess());
        h.runAfterDelay(2, () -> {
            StaffFile f = head.staff();
            h.assertTrue(f != null, "not put on file");
            h.assertTrue(f.employer == null && !f.staff(), "a retroactive head has an employer or a wage");
            h.assertValueEqual(f.history.get(0).k(), "retro", "the history line");
            int n = f.number;
            CompoundTag saved = head.saveWithoutMetadata(h.getLevel().registryAccess());
            head.loadWithComponents(saved, h.getLevel().registryAccess());
            h.runAfterDelay(2, () -> {
                h.assertTrue(head.staff() != null && head.staff().number == n, "the number changed across save and load");
                h.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void anOldHostIsRegisteredWhenItJoins(GameTestHelper h) {
        Cow cow = h.spawn(EntityType.COW, FLOOR);
        Hosts.data(cow).host = true;
        h.assertTrue(Staff.of(cow) == null, "setup: already on file");
        // the event as a chunk loading from disk raises it
        StaffEvents.joined(new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(cow, h.getLevel(), true));
        StaffFile f = Staff.of(cow);
        h.assertTrue(f != null && f.employer == null && f.history.get(0).k().equals("retro"), "the old host was not put on file");
        // a host made fresh is not retroactive
        Cow fresh = h.spawn(EntityType.COW, FLOOR.east(2));
        Hosts.data(fresh).host = true;
        StaffEvents.joined(new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(fresh, h.getLevel(), false));
        h.assertTrue(Staff.of(fresh) == null, "a fresh host was registered retroactively");
        h.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void stationHeadsAreRegisteredWithNoNumberChanged(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(4));
        // a Cerebral Array with five legacy zombie heads and broth
        BlockPos at = FLOOR.east(2);
        h.setBlock(at, ModRegistry.CEREBRAL_ARRAY.get().defaultBlockState());
        var array = (CerebralArrayBlockEntity) h.getBlockEntity(at);
        for (int i = 0; i < 5; i++) array.items().setStackInSlot(i, jarredHead(h, EntityType.ZOMBIE, null));
        array.broth().fill(new FluidStack(ModFluids.BROTH.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
        int before = array.potential();
        // a Turret with a legacy skeleton head and an owner
        BlockPos tAt = FLOOR.west(2);
        h.setBlock(tAt, ModRegistry.TURRET_PEDESTAL.get().defaultBlockState());
        var turret = (TurretPedestalBlockEntity) h.getBlockEntity(tAt);
        turret.items().setStackInSlot(0, jarredHead(h, EntityType.SKELETON, null));
        turret.setOwner(p.getUUID());
        // a paid Mount on a machine
        h.setBlock(FLOOR.south(3), ModRegistry.DNA_SEQUENCER.get());
        BlockPos mAt = FLOOR.south(3).east();
        h.setBlock(mAt, ModRegistry.EMPLOYEE_MOUNT.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        var mount = (EmployeeMountBlockEntity) h.getBlockEntity(mAt);
        h.setBlock(mAt.south(), Blocks.CHEST);
        ((ChestBlockEntity) h.getBlockEntity(mAt.south())).setItem(0, new ItemStack(Items.EMERALD, 8));
        mount.use(p, jarredHead(h, EntityType.VILLAGER, VillagerProfession.LIBRARIAN));
        float staffBefore = HeadJobs.staff(h.getLevel(), h.absolutePos(FLOOR.south(3)), "librarian");
        boolean paidBefore = mount.paid();
        h.runAfterDelay(StaffBlocks.FILE_TICKS * 2 + 2, () -> {
            for (int i = 0; i < 5; i++) h.assertTrue(Staff.of(array.items().getStackInSlot(i)) != null, "an Array head is not on file");
            h.assertTrue(array.potential() == before && before == 110, "the Array's thinking changed: " + before + " to " + array.potential());
            StaffFile tf = Staff.of(turret.head());
            h.assertTrue(tf != null && p.getUUID().equals(tf.employer), "the turret head's employer is not the turret's owner");
            h.assertTrue(p.getUUID().equals(turret.owner()), "the turret's owner changed");
            h.assertTrue(Staff.of(mount.head()) != null, "the Mount's villager is not on file");
            h.assertTrue(HeadJobs.staff(h.getLevel(), h.absolutePos(FLOOR.south(3)), "librarian") == staffBefore && mount.paid() == paidBefore,
                    "the Mount's staffing changed");
            h.assertTrue(turret.badge() >= 0 && array.badge() >= 0 && mount.badge() >= 0, "a station wears no badge");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void theFileTravelsWithTheHead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var skeleton = SawTableGameTests.weak(h, EntityType.SKELETON, FLOOR.east());
        SawTableGameTests.arm(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        p.interactOn(skeleton, InteractionHand.MAIN_HAND);
        ItemStack head = SawTableGameTests.head(p);
        int n = Staff.of(head).number;
        JarredHeadBlockEntity placed = plinth(h, FLOOR.north(2), head);
        StaffBlocks.placed(placed, p);
        ItemStack again = placed.toItem();
        h.assertTrue(Staff.of(again).number == n, "breaking the head lost its number");
        h.setBlock(FLOOR.west(2), ModRegistry.TURRET_PEDESTAL.get().defaultBlockState());
        var turret = (TurretPedestalBlockEntity) h.getBlockEntity(FLOOR.west(2));
        turret.use(p, again);
        h.assertTrue(Staff.of(turret.head()).number == n, "fitting it changed the number");
        p.getInventory().clearContent();
        turret.use(p, ItemStack.EMPTY);
        ItemStack out = p.getInventory().items.stream().filter(s -> s.is(ModRegistry.JARRED_HEAD.get())).findFirst().orElse(ItemStack.EMPTY);
        StaffFile f = Staff.of(out);
        h.assertTrue(f != null && f.number == n, "taking it out changed the number");
        h.assertTrue(f.history.size() <= StaffFile.HISTORY && f.history.stream().anyMatch(e -> e.k().equals("fitted")), "the history: " + f.history);
        for (int i = 0; i < 12; i++) f.note("sampled", i, "");
        h.assertTrue(f.history.size() == StaffFile.HISTORY, "history is not capped at eight");
        done(h, p);
    }

    /**
     * A head set down by hand looks back at whoever set it down, its plinth's front (and its badge) to them. Before
     * 0.6.0 it was set facing away, turned as far as it could (75 degrees) and showing the placer its plinth's back.
     */
    @GameTest(template = "platform")
    public static void aPlacedHeadLooksAtWhoeverSetItDown(GameTestHelper h) {
        for (Direction look : new Direction[]{Direction.SOUTH, Direction.EAST}) {
            BlockPos at = FLOOR.relative(look.getOpposite(), 2);
            ServerPlayer p = player(h, at);
            p.setYRot(look.toYRot());
            p.setItemInHand(InteractionHand.MAIN_HAND, jarredHead(h, EntityType.COW, null));
            BlockPos abs = h.absolutePos(FLOOR.below());
            p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
            h.assertTrue(h.getBlockState(FLOOR).is(ModRegistry.JARRED_HEAD_BLOCK.get()), "the head was not placed");
            Direction looks = h.getBlockState(FLOOR).getValue(com.coolfreeze23.headinajar.block.JarredHeadBlock.FACING).getOpposite();
            h.assertTrue(looks == look.getOpposite(), "placed looking " + look + ", the head looks " + looks + ", not back at the placer");
            h.getLevel().getServer().getPlayerList().remove(p);
            h.setBlock(FLOOR, Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void renameIsForTheEmployerAndTheTrusted(GameTestHelper h) {
        ServerPlayer boss = player(h, FLOOR);
        ServerPlayer stranger = player(h, FLOOR.east(1));
        JarredHeadBlockEntity head = plinth(h, FLOOR.north(2), jarredHead(h, EntityType.COW, null));
        StaffBlocks.placed(head, boss);
        BlockPos pos = head.getBlockPos();
        StaffCards.rename(boss, pos, "Sir Moosalot The Third");
        h.assertValueEqual(head.staff().customName, "Sir Moosalot The", "a 20-character name kept to 16");
        StaffCards.rename(stranger, pos, "Mine Now");
        h.assertValueEqual(Refusals.lastDelivered(stranger), "staff.not_yours", "a stranger's rename");
        h.assertValueEqual(head.staff().customName, "Sir Moosalot The", "a stranger renamed it");
        com.coolfreeze23.headinajar.form.HeadRegistry.get(boss).setTrust(boss.getUUID(), stranger.getUUID(), true);
        StaffCards.rename(stranger, pos, "Clarabelle");
        h.assertValueEqual(head.staff().customName, "Clarabelle", "a trusted player could not rename it");
        StaffCards.rename(boss, pos, "   ");
        h.assertValueEqual(head.staff().customName, "Clarabelle", "a blank name did not keep the old one");
        h.assertValueEqual(StaffCards.clean("§cRed\u0007 Cow"), "Red Cow", "formatting and control characters");
        done(h, boss, stranger);
    }

    // --- leaving -----------------------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theExitInterviewGoesOnlyToTheEmployer(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        SawTableGameTests.arm(p, ItemStack.EMPTY);
        Cow cow = SawTableGameTests.weak(h, EntityType.COW, FLOOR.east(2));
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        int n = Staff.of(cow).number;
        cow.hurt(h.getLevel().damageSources().lava(), Float.MAX_VALUE);
        String want = p.getUUID() + "|staff.headinajar.reason.lava|" + n;
        h.assertTrue(StaffEvents.exits().stream().filter(want::equals).count() == 1, "no single Exit Interview: " + StaffEvents.exits());
        AABB around = new AABB(h.absolutePos(FLOOR)).inflate(6);
        h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(ModRegistry.CONDOLENCE_CARD.get())
                && SawTableGameTests.data(e.getItem()).getInt("Number") == n).isEmpty(), "no Condolence Card at the spot");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void reasonsForLeaving(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east());
        Hosts.data(cow).host = true;
        StaffFile f = new StaffFile();
        f.number = 1;
        f.employer = p.getUUID();
        var src = h.getLevel().damageSources();
        h.assertValueEqual(StaffEvents.reason(cow, src.fall(), f), "staff.headinajar.reason.fall", "a fall");
        h.assertValueEqual(StaffEvents.reason(cow, src.lava(), f), "staff.headinajar.reason.lava", "lava");
        h.assertValueEqual(StaffEvents.reason(cow, src.inFire(), f), "staff.headinajar.reason.lava", "fire counts as lava");
        h.assertValueEqual(StaffEvents.reason(cow, src.drown(), f), "staff.headinajar.reason.drowned", "drowning");
        h.assertValueEqual(StaffEvents.reason(cow, src.explosion(null, null), f), "staff.headinajar.reason.explosion", "an explosion");
        h.assertValueEqual(StaffEvents.reason(cow, src.playerAttack(p), f), "staff.headinajar.reason.management", "the employer");
        var zombie = h.spawn(EntityType.ZOMBIE, FLOOR.west(2));
        h.assertValueEqual(StaffEvents.reason(cow, src.mobAttack(zombie), f), zombie.getName().getString(), "a zombie");
        h.assertValueEqual(StaffEvents.reason(cow, src.generic(), f), "staff.headinajar.reason.other", "anything else");
        Hosts.data(cow).freshness = 0;
        h.assertValueEqual(StaffEvents.reason(cow, src.genericKill(), f), "staff.headinajar.reason.wastage", "rotting away");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aWildCreatureHasAFileButNoEmployer(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east(2));
        Staff.sampled(cow, p);
        int n = Staff.of(cow).number;
        cow.hurt(h.getLevel().damageSources().generic(), Float.MAX_VALUE);
        h.assertTrue(StaffEvents.exits().stream().noneMatch(s -> s.endsWith("|" + n)), "a wild cow sent an Exit Interview");
        AABB around = new AABB(h.absolutePos(FLOOR)).inflate(6);
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(ModRegistry.CONDOLENCE_CARD.get())).isEmpty(),
                "a wild cow left a card");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 20)
    @SuppressWarnings("unchecked")
    public static void theStaffPayloadsAreOptional(GameTestHelper h) throws ReflectiveOperationException {
        var field = net.neoforged.neoforge.network.registration.NetworkRegistry.class.getDeclaredField("PAYLOAD_REGISTRATIONS");
        field.setAccessible(true);
        var byProtocol = (java.util.Map<net.minecraft.network.ConnectionProtocol, java.util.Map<net.minecraft.resources.ResourceLocation,
                net.neoforged.neoforge.network.registration.PayloadRegistration<?>>>) field.get(null);
        int found = 0;
        for (var reg : byProtocol.get(net.minecraft.network.ConnectionProtocol.PLAY).values()) {
            if (!reg.id().equals(StaffNet.Card.TYPE.id()) && !reg.id().equals(StaffNet.Rename.TYPE.id())) continue;
            found++;
            h.assertTrue(new net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent(reg).optional(), reg.id() + " is not optional");
        }
        h.assertTrue(found == 2, "the staff payloads are not registered: " + found);
        h.assertValueEqual(com.coolfreeze23.headinajar.network.Network.VERSION, "6", "the network version");
        h.succeed();
    }

    // --- pay and morale ----------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aTrophyNeverTakesItsWage(GameTestHelper h) {
        JarredHeadBlockEntity head = plinth(h, FLOOR, jarredHead(h, EntityType.COW, null));
        h.setBlock(FLOOR.east(), Blocks.CHEST);
        var chest = (ChestBlockEntity) h.getBlockEntity(FLOOR.east());
        chest.setItem(0, new ItemStack(Items.WHEAT, 16));
        h.runAfterDelay(3, () -> {
            StaffFile f = head.staff();
            for (int i = 0; i < 3; i++) {
                f.lastDawn = Staff.day(h.getLevel()) - 1;
                StaffBlocks.tick(h.getLevel(), head.getBlockPos(), head);
            }
            h.assertTrue(chest.getItem(0).getCount() == 16, "a trophy took its wage");
            h.assertTrue(!f.staff() && head.badge() < 0 && Band.of(f) == Band.TROPHY, "a trophy has morale or a badge");
            h.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void payingByHandHires(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        JarredHeadBlockEntity head = plinth(h, FLOOR, jarredHead(h, EntityType.COW, null));
        useOn(h, p, head, new ItemStack(Items.WHEAT, 4));
        StaffFile f = head.staff();
        h.assertTrue(f != null && f.staff() && f.morale == 60, "paying by hand did not hire it at 60: " + (f == null ? null : f.morale));
        h.assertTrue(p.getUUID().equals(f.employer), "the payer is not the employer");
        h.assertTrue(head.badge() >= 0, "no badge on a hired head");
        h.assertTrue(SawTableGameTests.earned(h, p, "pay_grade"), "Pay Grade was not earned");
        useOn(h, p, head, new ItemStack(Items.WHEAT, 4));
        h.assertValueEqual(Refusals.lastDelivered(p), "staff.paid_today", "paying twice in a day");
        useOn(h, p, head, new ItemStack(Items.CARROT, 4));
        h.assertValueEqual(Refusals.lastDelivered(p), "staff.wrong_wage", "the wrong wage");
        h.assertTrue(f.morale == 60, "a refused payment moved morale");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aVillagerHeadStillTrades(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        JarredHeadBlockEntity head = plinth(h, FLOOR, jarredHead(h, EntityType.VILLAGER, VillagerProfession.FARMER));
        useOn(h, p, head, new ItemStack(Items.EMERALD, 4));
        h.assertTrue(p.containerMenu instanceof MerchantMenu, "an emerald on a villager head did not open its trades");
        h.assertTrue(head.staff() == null || !head.staff().staff(), "the villager head was paid");
        p.closeContainer();
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void dawnPayFromAChestBesideIt(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        JarredHeadBlockEntity head = plinth(h, FLOOR, jarredHead(h, EntityType.COW, null));
        h.setBlock(FLOOR.east(), Blocks.CHEST);
        var chest = (ChestBlockEntity) h.getBlockEntity(FLOOR.east());
        chest.setItem(0, new ItemStack(Items.WHEAT, 2));
        useOn(h, p, head, new ItemStack(Items.WHEAT));
        StaffFile f = head.staff();
        long day = Staff.day(h.getLevel());
        // the next dawn, as the head's tick finds it
        f.paidThrough = day - 1;
        StaffBlocks.dawn(h.getLevel(), head.getBlockPos(), head, f);
        h.assertTrue(chest.getItem(0).getCount() == 1 && f.morale == 70, "dawn pay: chest " + chest.getItem(0).getCount() + ", morale " + f.morale);
        // an empty chest
        chest.setItem(0, ItemStack.EMPTY);
        f.paidThrough = day - 1;
        StaffBlocks.dawn(h.getLevel(), head.getBlockPos(), head, f);
        h.assertTrue(f.morale == 55, "unpaid dawn: morale " + f.morale);
        h.assertTrue(Memos.told(p, Memos.Memo.UNPAID_DAWN) == 1, "the unpaid-dawn memo");
        f.paidThrough = day - 1;
        StaffBlocks.dawn(h.getLevel(), head.getBlockPos(), head, f);
        h.assertTrue(Memos.told(p, Memos.Memo.UNPAID_DAWN) == 1, "the unpaid-dawn memo came twice");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void oneDawnAfterManyDaysAway(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        JarredHeadBlockEntity head = plinth(h, FLOOR, jarredHead(h, EntityType.COW, null));
        h.setBlock(FLOOR.east(), Blocks.CHEST);
        var chest = (ChestBlockEntity) h.getBlockEntity(FLOOR.east());
        chest.setItem(0, new ItemStack(Items.WHEAT, 10));
        useOn(h, p, head, new ItemStack(Items.WHEAT));
        StaffFile f = head.staff();
        long day = Staff.day(h.getLevel());
        f.lastDawn = day - 5;
        f.paidThrough = day - 5;
        StaffBlocks.tick(h.getLevel(), head.getBlockPos(), head);
        StaffBlocks.tick(h.getLevel(), head.getBlockPos(), head);
        h.assertTrue(chest.getItem(0).getCount() == 9, "five days away took " + (10 - chest.getItem(0).getCount()) + " wages");
        h.assertTrue(f.lastDawn == day, "the dawn was not marked");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void bandsFollowMorale(GameTestHelper h) {
        h.assertTrue(Band.of(50, 0) == Band.CONTENT && Band.of(80, 0) == Band.ENGAGED && Band.of(30, 0) == Band.DISENGAGED, "the bands");
        h.assertTrue(Band.of(10, 1) == Band.DISENGAGED && Band.of(10, 2) == Band.GRIEVANCE, "a Grievance takes two low dawns");
        h.assertTrue(Band.of(-1, 0) == Band.TROPHY, "no morale is a trophy");
        // a paid head reaches Engaged within three dawns: 50, 60, 70, 80
        int m = StaffBlocks.START;
        for (int i = 0; i < 3; i++) m += StaffBlocks.PAID;
        h.assertTrue(m == 80 && Band.of(m, 0) == Band.ENGAGED, "three paid dawns give " + m);
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void theMountBandIsALabelOnly(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        h.setBlock(FLOOR, ModRegistry.DNA_SEQUENCER.get());
        BlockPos mAt = FLOOR.east();
        h.setBlock(mAt, ModRegistry.EMPLOYEE_MOUNT.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        var mount = (EmployeeMountBlockEntity) h.getBlockEntity(mAt);
        mount.use(p, jarredHead(h, EntityType.VILLAGER, VillagerProfession.LIBRARIAN));
        // unpaid since it was hung (no chest): Disengaged, then Grievance at three days, the days it faces the wall
        h.assertTrue(mount.band() == Band.DISENGAGED, "an unpaid mount's band: " + mount.band());
        float before = HeadJobs.staff(h.getLevel(), h.absolutePos(FLOOR), "librarian");
        h.assertTrue(before == 0.5f, "unpaid staff bonus " + before);
        h.assertTrue(!mount.paid() && mount.daysMissed() == 1, "setup: days missed " + mount.daysMissed());
        done(h, p);
    }

    // --- memos -------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void memosWaitAMinuteApart(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Memos.send(p, Memos.Memo.STAFF_NUMBER, "0001", "Cow");
        Memos.send(p, Memos.Memo.UNPAID_DAWN, "Bessie");
        h.assertTrue(Memos.history().contains(p.getUUID() + ":" + Memos.Memo.STAFF_NUMBER.name()), "the first memo did not go out");
        h.assertTrue(MemoQueue.waiting(p, Memos.Memo.UNPAID_DAWN) == 1, "the second memo did not wait");
        MemoQueue.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
        h.assertTrue(MemoQueue.waiting(p, Memos.Memo.UNPAID_DAWN) == 1, "the second memo went out inside the minute");
        MemoQueue.backdate(p, MemoQueue.SPACING);
        MemoQueue.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
        h.assertTrue(MemoQueue.waiting(p, Memos.Memo.UNPAID_DAWN) == 0
                && Memos.history().contains(p.getUUID() + ":" + Memos.Memo.UNPAID_DAWN.name()), "the second memo did not go out after a minute");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void noMemoWhileAChairOperates(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos chair = h.absolutePos(FLOOR.east(3));
        com.coolfreeze23.headinajar.block.ChairQuiet.mark(h.getLevel(), chair, true);
        Memos.send(p, Memos.Memo.STAFF_NUMBER, "0001", "Cow");
        h.assertTrue(MemoQueue.waiting(p, Memos.Memo.STAFF_NUMBER) == 1, "a memo went out while a chair operated");
        com.coolfreeze23.headinajar.block.ChairQuiet.mark(h.getLevel(), chair, false);
        MemoQueue.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
        h.assertTrue(MemoQueue.waiting(p, Memos.Memo.STAFF_NUMBER) == 0, "the memo did not go out once the chair stopped");
        done(h, p);
    }

    @GameTest(template = "platform", batch = "staff_config")
    public static void moraleOffKeepsEveryoneContent(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(2));
        JarredHeadBlockEntity head = plinth(h, FLOOR, jarredHead(h, EntityType.COW, null));
        h.setBlock(FLOOR.east(), Blocks.CHEST);
        var chest = (ChestBlockEntity) h.getBlockEntity(FLOOR.east());
        chest.setItem(0, new ItemStack(Items.WHEAT, 4));
        Config.MORALE.set(false);
        try {
            useOn(h, p, head, new ItemStack(Items.WHEAT));
            StaffFile f = head.staff();
            f.paidThrough = Staff.day(h.getLevel()) - 1;
            StaffBlocks.dawn(h.getLevel(), head.getBlockPos(), head, f);
            h.assertTrue(chest.getItem(0).getCount() == 3, "wages were not taken with morale off");
            h.assertTrue(f.morale == StaffBlocks.START && Band.of(f) == Band.CONTENT, "morale moved with morale off: " + f.morale);
        } finally {
            Config.MORALE.set(true);
        }
        done(h, p);
    }
}
