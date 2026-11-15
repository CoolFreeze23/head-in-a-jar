package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity;
import com.coolfreeze23.headinajar.block.MachineBlock;
import com.coolfreeze23.headinajar.block.PrintCatalogue;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.menu.BodyPrinterMenu;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/** Print-to-Order: the Body Printer's Template slot, its catalogue, its scaled costs, and what a printed host is. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class PrintGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);
    private static final Item[] MATERIALS = {Items.ROTTEN_FLESH, Items.BONE_BLOCK, Items.REDSTONE_BLOCK, Items.SLIME_BALL};

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

    private static BodyPrinterBlockEntity printer(GameTestHelper h, BlockPos at) {
        h.setBlock(at, ModRegistry.BODY_PRINTER.get());
        return (BodyPrinterBlockEntity) h.getBlockEntity(at);
    }

    /** A full own-body load: 16/8/4/8, {@code saline} mB, {@code fe} stored. */
    private static void load(BodyPrinterBlockEntity printer, int saline, int fe) {
        for (int i = 0; i < 4; i++) printer.items().setStackInSlot(i, new ItemStack(MATERIALS[i], BodyPrinterBlockEntity.NEED[i]));
        if (saline > 0) printer.saline().fill(new FluidStack(ModFluids.SALINE.source.get(), saline), IFluidHandler.FluidAction.EXECUTE);
        printer.energy().set(fe);
    }

    private static ItemStack fraction(EntityType<?> type, int quality) {
        return GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), type, quality);
    }

    private static void template(BodyPrinterBlockEntity printer, EntityType<?> type, int quality) {
        printer.items().setStackInSlot(BodyPrinterBlockEntity.TEMPLATE, fraction(type, quality));
    }

    /** The doorstep, in the level's own coordinates. */
    private static BlockPos door(BodyPrinterBlockEntity printer) {
        return printer.getBlockPos().relative(printer.getBlockState().getValue(MachineBlock.FACING));
    }

    /** Runs the print to its end at once, as the level would over a minute. */
    private static void finish(GameTestHelper h, BodyPrinterBlockEntity printer) {
        for (int i = 0; i <= BodyPrinterBlockEntity.TICKS && printer.printing(); i++) {
            BodyPrinterBlockEntity.kitTick(h.getLevel(), printer.getBlockPos(), printer.getBlockState(), printer);
        }
    }

    private static <T extends Mob> List<T> at(GameTestHelper h, BodyPrinterBlockEntity printer, Class<T> kind) {
        return h.getLevel().getEntitiesOfClass(kind, new AABB(door(printer)).inflate(0.5));
    }

    private static boolean earned(GameTestHelper h, ServerPlayer p, String id) {
        AdvancementHolder holder = h.getLevel().getServer().getAdvancements().get(HeadInAJar.id(id));
        if (holder == null) throw new net.minecraft.gametest.framework.GameTestAssertException("no advancement " + id);
        return p.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static int count(GameTestHelper h, BlockPos around, Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(around)).inflate(2)).stream()
                .filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    /** Nothing taken: the own-body load still in, the template still in its slot, the power still stored. */
    private static void untouched(GameTestHelper h, BodyPrinterBlockEntity printer, int saline, int fe, String what) {
        for (int i = 0; i < 4; i++) {
            h.assertValueEqual(printer.items().getStackInSlot(i).getCount(), BodyPrinterBlockEntity.NEED[i], what + ": material " + i);
        }
        h.assertTrue(printer.items().getStackInSlot(BodyPrinterBlockEntity.TEMPLATE).is(ModRegistry.DNA_FRACTION.get()), what + ": the template was taken");
        h.assertValueEqual(printer.saline().getFluidAmount(), saline, what + ": saline");
        h.assertValueEqual(printer.energy().getEnergyStored(), fe, what + ": power");
        h.assertFalse(printer.printing(), what + ": it started");
    }

    /** Pressing Print with this template is refused with {@code reason}, and nothing is taken. */
    private static void refused(GameTestHelper h, ServerPlayer p, EntityType<?> type, int quality, String reason) {
        BodyPrinterBlockEntity printer = printer(h, FLOOR);
        load(printer, 2000, 250_000);
        template(printer, type, quality);
        h.assertFalse(printer.createMenu(1, p.getInventory(), p).clickMenuButton(p, 0), "printed a " + type);
        h.assertValueEqual(Refusals.lastDelivered(p), reason, "the refusal for a " + type);
        untouched(h, printer, 2000, 250_000, String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
    }

    private static String verdict(EntityType<?> type) {
        PrintCatalogue.Verdict v = PrintCatalogue.model(fraction(type, 80), true);
        return v.ok() ? "size " + v.size() : v.reason();
    }

    // --- a print --------------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aCowTemplatePrintsACowHost(GameTestHelper h) {
        // a presser with their head on: a species print takes any form
        ServerPlayer p = player(h, FLOOR.east(3));
        h.assertTrue(FormActions.state(p).form() == Form.BODY, "setup: the presser is not in a body");
        BodyPrinterBlockEntity printer = printer(h, FLOOR);
        load(printer, 2000, 250_000);
        template(printer, EntityType.COW, 72);
        // the screen's numbers: the species, its class, the quality, licensed, no refusal
        BodyPrinterMenu menu = (BodyPrinterMenu) printer.createMenu(1, p.getInventory(), p);
        h.assertValueEqual(menu.data(BodyPrinterMenu.SPECIES), BuiltInRegistries.ENTITY_TYPE.getId(EntityType.COW), "synced species");
        h.assertValueEqual(menu.data(BodyPrinterMenu.SIZE), PrintCatalogue.Size.MEDIUM.code(), "synced size");
        h.assertValueEqual(menu.data(BodyPrinterMenu.QUALITY), 72, "synced quality");
        h.assertValueEqual(menu.data(BodyPrinterMenu.LOCKED), 0, "synced licence");
        h.assertValueEqual(menu.data(BodyPrinterMenu.REASON), 0, "synced refusal");
        h.assertTrue(menu.clickMenuButton(p, 0), "the printer refused a cow");
        // everything is taken at Print: 16/8/4/8, 2,000 mB and the fraction
        for (int i = 0; i < 4; i++) h.assertTrue(printer.items().getStackInSlot(i).isEmpty(), "material " + i + " not taken");
        h.assertTrue(printer.items().getStackInSlot(BodyPrinterBlockEntity.TEMPLATE).isEmpty(), "the fraction was not used");
        h.assertValueEqual(printer.saline().getFluidAmount(), 0, "saline taken");
        h.assertTrue(printer.printType() == EntityType.COW, "the job is not a cow");
        finish(h, printer);
        // 200 kFE over the minute
        h.assertValueEqual(250_000 - printer.energy().getEnergyStored(), BodyPrinterBlockEntity.TICKS * (BodyPrinterBlockEntity.ENERGY / BodyPrinterBlockEntity.TICKS), "power used");
        List<net.minecraft.world.entity.animal.Cow> cows = at(h, printer, net.minecraft.world.entity.animal.Cow.class);
        h.assertValueEqual(cows.size(), 1, "cows at the door");
        var cow = cows.get(0);
        HostData data = Hosts.data(cow);
        h.assertTrue(Hosts.isHost(cow) && data.printed, "not a printed host");
        h.assertTrue(Math.abs(data.freshness - 72) < 0.01f && data.sync == 0, "freshness " + data.freshness + ", sync " + data.sync);
        h.assertFalse(cow.isBaby(), "a calf was printed");
        h.assertTrue(cow.isPersistenceRequired(), "it may despawn");
        for (EquipmentSlot slot : EquipmentSlot.values()) h.assertTrue(cow.getItemBySlot(slot).isEmpty(), "it came with " + slot);
        StaffFile file = Staff.of(cow);
        h.assertTrue(file != null && p.getUUID().equals(file.employer), "the presser is not its employer");
        h.assertValueEqual(file.history.get(file.history.size() - 1).k(), "printed", "its file's last line");
        h.assertTrue(earned(h, p, "made_to_order"), "Made to Order not granted");
        h.assertTrue(Memos.sent(p, Memos.Memo.FIRST_SPECIES_PRINT), "the first-print memo was not sent");
        h.assertTrue(Fx.sent().stream().anyMatch(s -> s.fx().kind() == Fx.PRINTED && s.fx().entityB() == cow.getId()), "no shake for the new host");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aChickenTemplateCostsAQuarter(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        BodyPrinterBlockEntity printer = printer(h, FLOOR);
        // exactly a quarter of the power, more of everything else than it needs
        load(printer, 2000, BodyPrinterBlockEntity.ENERGY / 4);
        template(printer, EntityType.CHICKEN, 60);
        h.assertTrue(printer.start(p), "the printer refused a chicken");
        h.assertValueEqual(printer.items().getStackInSlot(0).getCount(), 12, "flesh left");
        h.assertValueEqual(printer.items().getStackInSlot(1).getCount(), 6, "bone blocks left");
        h.assertValueEqual(printer.items().getStackInSlot(2).getCount(), 3, "redstone blocks left");
        h.assertValueEqual(printer.items().getStackInSlot(3).getCount(), 6, "slime left");
        h.assertValueEqual(printer.saline().getFluidAmount(), 1500, "saline left");
        finish(h, printer);
        int perTick = BodyPrinterBlockEntity.ENERGY / 4 / BodyPrinterBlockEntity.TICKS;
        h.assertValueEqual(BodyPrinterBlockEntity.ENERGY / 4 - printer.energy().getEnergyStored(), BodyPrinterBlockEntity.TICKS * perTick, "power used");
        var chickens = at(h, printer, net.minecraft.world.entity.animal.Chicken.class);
        h.assertTrue(chickens.size() == 1 && Hosts.data(chickens.get(0)).printed, "no printed chicken");
        // a small model takes half
        h.assertValueEqual(PrintCatalogue.Size.SMALL.of(BodyPrinterBlockEntity.SALINE), 1000, "a small print's saline");
        h.assertValueEqual(verdict(EntityType.PIG), "size SMALL", "the pig");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aStriderPrintsAsMedium(GameTestHelper h) {
        // thin enough for the door and under two blocks tall
        for (EntityType<?> type : List.of(EntityType.STRIDER, EntityType.LLAMA, EntityType.TRADER_LLAMA, EntityType.BLAZE)) {
            h.assertValueEqual(verdict(type), "size MEDIUM", String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
        }
        h.succeed();
    }

    // --- refusals, in the printer's order --------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aSpiderWillNotFitThroughTheDoor(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        refused(h, p, EntityType.SPIDER, 80, "printer.too_wide");
        for (EntityType<?> type : List.of(EntityType.PANDA, EntityType.TURTLE, EntityType.HORSE, EntityType.POLAR_BEAR, EntityType.SNIFFER)) {
            h.assertValueEqual(verdict(type), "printer.too_wide", String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
        }
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void anEndermanIsTooTall(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        refused(h, p, EntityType.ENDERMAN, 80, "printer.too_tall");
        for (EntityType<?> type : List.of(EntityType.WITHER_SKELETON, EntityType.RAVAGER, EntityType.GHAST, EntityType.CAMEL)) {
            h.assertValueEqual(verdict(type), "printer.too_tall", String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
        }
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aSquidNeedsWaterAtTheDoor(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        refused(h, p, EntityType.SQUID, 80, "printer.needs_water");
        BodyPrinterBlockEntity printer = (BodyPrinterBlockEntity) h.getBlockEntity(FLOOR);
        // flooded, it prints, and the squid comes out into the water
        h.getLevel().setBlockAndUpdate(door(printer), Blocks.WATER.defaultBlockState());
        h.assertTrue(printer.start(p), "the printer refused a squid at a flooded door");
        finish(h, printer);
        var squids = at(h, printer, net.minecraft.world.entity.animal.Squid.class);
        h.assertTrue(squids.size() == 1 && h.getLevel().getFluidState(squids.get(0).blockPosition()).is(net.minecraft.tags.FluidTags.WATER) && Hosts.data(squids.get(0)).printed, "no printed squid in the water");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aBossIsNotPrinted(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        refused(h, p, EntityType.ENDER_DRAGON, 60, "printer.boss");
        h.assertValueEqual(verdict(EntityType.WITHER), "printer.boss", "the wither");
        // and a player's fraction (only by command)
        h.assertValueEqual(verdict(EntityType.PLAYER), "printer.player", "a player");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aWardenIsNotLicensed(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        refused(h, p, EntityType.WARDEN, 80, "printer.licence");
        h.assertValueEqual(verdict(EntityType.ELDER_GUARDIAN), "printer.licence", "the elder guardian");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aSlimeIsAllHead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        refused(h, p, EntityType.SLIME, 80, "printer.all_head");
        for (EntityType<?> type : List.of(EntityType.MAGMA_CUBE, EntityType.SHULKER, EntityType.BEE, EntityType.SILVERFISH, EntityType.ENDERMITE, EntityType.TADPOLE)) {
            h.assertValueEqual(verdict(type), "printer.all_head", String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
        }
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void anIronGolemIsFacilitiesBusiness(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        refused(h, p, EntityType.IRON_GOLEM, 80, "printer.facilities");
        for (EntityType<?> type : List.of(EntityType.SNOW_GOLEM, EntityType.ALLAY, EntityType.VEX, EntityType.BREEZE)) {
            h.assertValueEqual(verdict(type), "printer.facilities", String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
        }
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void qualityUnderFortyIsRefusedAndNothingIsTaken(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        refused(h, p, EntityType.COW, 39, "printer.poor");
        h.assertValueEqual(verdict(EntityType.ILLUSIONER), "printer.not_approved", "the illusioner");
        // something that is not a fraction at all (a Blood Sample put in by hand) is not a template
        h.assertValueEqual(PrintCatalogue.model(GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), EntityType.COW, 80), true).reason(),
                "printer.not_fraction", "a blood sample");
        done(h, p);
    }

    // --- what a printed host is --------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void aPrintedHostDropsNothingWhenItDies(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(4));
        var printed = h.spawn(EntityType.COW, FLOOR.north(3));
        var sawn = h.spawn(EntityType.COW, FLOOR.south(3));
        for (var cow : List.of(printed, sawn)) {
            HostData d = Hosts.data(cow);
            d.host = true;
            d.freshness = 70;
        }
        Hosts.data(printed).printed = true;
        printed.hurt(h.getLevel().damageSources().playerAttack(p), 1000);
        sawn.hurt(h.getLevel().damageSources().playerAttack(p), 1000);
        h.runAfterDelay(3, () -> {
            h.assertValueEqual(count(h, FLOOR.north(3), Items.BEEF), 0, "beef from the printed cow");
            h.assertValueEqual(count(h, FLOOR.north(3), Items.LEATHER), 0, "leather from the printed cow");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(h.absolutePos(FLOOR.north(3))).inflate(2)).isEmpty(), "experience from the printed cow");
            h.assertTrue(count(h, FLOOR.south(3), Items.BEEF) > 0, "the sawn host dropped no beef");
            // and the flag survives a save
            HostData read = HostData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,
                    HostData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, Hosts.data(printed)).getOrThrow()).getOrThrow();
            h.assertTrue(read.printed, "printed lost in a save");
            HostData old = HostData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
            h.assertFalse(old.printed, "an old host reads as printed");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void aVillagerPrintsUnemployedAndAZombieBare(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        BodyPrinterBlockEntity printer = printer(h, FLOOR);
        load(printer, 2000, 250_000);
        template(printer, EntityType.VILLAGER, 90);
        h.assertTrue(printer.start(p), "the printer refused a villager");
        finish(h, printer);
        var villagers = at(h, printer, Villager.class);
        h.assertTrue(villagers.size() == 1, "no villager at the door");
        h.assertTrue(villagers.get(0).getVillagerData().getProfession() == VillagerProfession.NONE && villagers.get(0).getVillagerData().getLevel() == 1,
                "a printed villager came with a trade");
        villagers.get(0).discard();
        load(printer, 2000, 250_000);
        template(printer, EntityType.ZOMBIE, 90);
        h.assertTrue(printer.start(p), "the printer refused a zombie");
        finish(h, printer);
        var zombies = at(h, printer, net.minecraft.world.entity.monster.Zombie.class);
        h.assertTrue(zombies.size() == 1, "no zombie at the door");
        var zombie = zombies.get(0);
        h.assertFalse(zombie.isBaby() || zombie.isPassenger() || zombie.isVehicle(), "a baby or a jockey was printed");
        for (EquipmentSlot slot : EquipmentSlot.values()) h.assertTrue(zombie.getItemBySlot(slot).isEmpty(), "the zombie came with " + slot);
        done(h, p);
    }

    // --- old printers, own bodies, refunds, the licence ---------------------------------------------------------------

    @GameTest(template = "platform")
    public static void anOldPrinterLoadsWithAnEmptyTemplateSlot(GameTestHelper h) {
        BodyPrinterBlockEntity printer = printer(h, FLOOR);
        var registries = h.getLevel().registryAccess();
        // a 0.6.1 printer's save: four slots of materials
        ItemStackHandler old = new ItemStackHandler(4);
        for (int i = 0; i < 4; i++) old.setStackInSlot(i, new ItemStack(MATERIALS[i], BodyPrinterBlockEntity.NEED[i]));
        CompoundTag tag = printer.saveWithoutMetadata(registries);
        tag.put("Items", old.serializeNBT(registries));
        printer.loadWithComponents(tag, registries);
        h.assertValueEqual(printer.items().getSlots(), 5, "slots after loading");
        for (int i = 0; i < 4; i++) h.assertValueEqual(printer.items().getStackInSlot(i).getCount(), BodyPrinterBlockEntity.NEED[i], "material " + i);
        h.assertTrue(printer.items().getStackInSlot(BodyPrinterBlockEntity.TEMPLATE).isEmpty(), "the Template slot is not empty");
        h.assertTrue(printer.printType() == null, "an old printer has a species job");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void anEmptyTemplateIsTodaysPrint(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        BodyPrinterBlockEntity printer = printer(h, FLOOR);
        load(printer, 2000, 250_000);
        // a body-form presser, nothing in the Template slot: today's refusal and line
        h.assertFalse(printer.start(p), "a presser with a head printed their own body");
        h.assertValueEqual(Refusals.lastDelivered(p), "printer.has_head", "the own-body refusal");
        untouchedMaterials(h, printer);
        // as a jar it prints the presser's body, as always
        FormActions.detach(p, 3);
        h.assertTrue(printer.start(p), "a jar could not print its body");
        h.assertTrue(printer.printing() && printer.printType() == null, "an own-body print is not an own-body job");
        // the Template slot takes a fraction, one at a time, and nothing else
        var items = printer.items();
        h.assertFalse(items.isItemValid(BodyPrinterBlockEntity.TEMPLATE, new ItemStack(Items.ROTTEN_FLESH)), "flesh fits the Template slot");
        h.assertFalse(items.isItemValid(BodyPrinterBlockEntity.TEMPLATE, GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), EntityType.COW, 80)), "a blood sample fits");
        ItemStack two = fraction(EntityType.COW, 80).copyWithCount(2);
        ItemStack left = items.insertItem(BodyPrinterBlockEntity.TEMPLATE, two, false);
        h.assertTrue(left.getCount() == 1 && items.getStackInSlot(BodyPrinterBlockEntity.TEMPLATE).getCount() == 1, "the slot took more than one");
        done(h, p);
    }

    private static void untouchedMaterials(GameTestHelper h, BodyPrinterBlockEntity printer) {
        for (int i = 0; i < 4; i++) {
            h.assertValueEqual(printer.items().getStackInSlot(i).getCount(), BodyPrinterBlockEntity.NEED[i], "material " + i);
        }
    }

    @GameTest(template = "platform")
    public static void breakingMidPrintRefundsTheScaledMaterials(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3)), other = player(h, FLOOR.west(3));
        BodyPrinterBlockEntity printer = printer(h, FLOOR);
        int[] quarter = {4, 2, 1, 2};
        for (int i = 0; i < 4; i++) printer.items().setStackInSlot(i, new ItemStack(MATERIALS[i], quarter[i]));
        printer.saline().fill(new FluidStack(ModFluids.SALINE.source.get(), 500), IFluidHandler.FluidAction.EXECUTE);
        printer.energy().set(BodyPrinterBlockEntity.ENERGY / 4);
        template(printer, EntityType.RABBIT, 75);
        h.assertTrue(printer.start(p), "the printer refused a rabbit");
        for (int i = 0; i < 100; i++) BodyPrinterBlockEntity.serverTick(h.getLevel(), printer.getBlockPos(), printer.getBlockState(), printer);
        // the job survives a save: its species and its class
        var registries = h.getLevel().registryAccess();
        printer.loadWithComponents(printer.saveWithoutMetadata(registries), registries);
        h.assertTrue(printer.printing() && printer.printType() == EntityType.RABBIT, "the job was lost in a save");
        other.gameMode.destroyBlock(h.absolutePos(FLOOR));
        for (int i = 0; i < 4; i++) {
            h.assertValueEqual(count(h, FLOOR, MATERIALS[i]), quarter[i], "refunded " + MATERIALS[i]);
        }
        h.assertValueEqual(count(h, FLOOR, ModRegistry.DNA_FRACTION.get()), 1, "the template was not handed back");
        done(h, p, other);
    }

    @GameTest(template = "platform")
    public static void specimenPrintingOffLocksTheSlot(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        BodyPrinterBlockEntity printer = printer(h, FLOOR);
        load(printer, 2000, 250_000);
        template(printer, EntityType.COW, 80);
        Config.SPECIMEN_PRINTING.set(false);
        try {
            BodyPrinterMenu menu = (BodyPrinterMenu) printer.createMenu(1, p.getInventory(), p);
            h.assertValueEqual(menu.data(BodyPrinterMenu.LOCKED), 1, "the screen is not told the slot is locked");
            ItemStack another = fraction(EntityType.PIG, 80);
            h.assertFalse(printer.items().isItemValid(BodyPrinterBlockEntity.TEMPLATE, another), "a locked slot takes a fraction");
            // a template already in (put there before the licence lapsed) does not print
            h.assertFalse(menu.clickMenuButton(p, 0), "printed without a licence");
            h.assertValueEqual(Refusals.lastDelivered(p), "printer.not_licensed", "the licence refusal");
            untouched(h, printer, 2000, 250_000, "unlicensed");
        } finally {
            Config.SPECIMEN_PRINTING.set(true);
        }
        h.assertTrue(printer.items().isItemValid(BodyPrinterBlockEntity.TEMPLATE, fraction(EntityType.PIG, 80)), "a licensed slot refuses a fraction");
        done(h, p);
    }
}
