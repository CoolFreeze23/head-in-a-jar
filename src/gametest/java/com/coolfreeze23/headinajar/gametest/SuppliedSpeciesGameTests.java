package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.DeskJobs;
import com.coolfreeze23.headinajar.block.HeadJobs;
import com.coolfreeze23.headinajar.block.HeadSpeak;
import com.coolfreeze23.headinajar.block.PrintCatalogue;
import com.coolfreeze23.headinajar.block.TreadmillRate;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.genetics.BloodType;
import com.coolfreeze23.headinajar.genetics.Directory;
import com.coolfreeze23.headinajar.genetics.GeneArchive;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.SawTable;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffTable;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import com.coolfreeze23.headinajar.supplier.Supplier;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * The new arrivals' species rows (0.8.0), with the creatures themselves: each one's blood, department, jar, title,
 * noise, wage, Lanyard verdict, host neck, printer verdict, Treadmill row and station fields, the desk pairs, the
 * Directory's New Arrivals row and New Starters. Registered only when every new arrival is (as {@link SuppliedGameTests}
 * is), so a game without them runs exactly the old suite. {@code platform}: 15 x 6 x 15, the floor's top at helper y 1.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
public class SuppliedSpeciesGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    @SubscribeEvent
    static void register(RegisterGameTestsEvent event) {
        if (Supplier.delivered().size() == Supplier.NEW_ARRIVALS.size()) event.register(SuppliedSpeciesGameTests.class);
    }

    static EntityType<?> type(String path) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.withDefaultNamespace(path))
                .orElseThrow(() -> new GameTestAssertException("not registered: " + path));
    }

    static Mob spawn(GameTestHelper h, String path, BlockPos at) {
        Entity e = h.spawn(type(path), at);
        if (!(e instanceof Mob mob)) throw new GameTestAssertException(path + " is not a mob");
        mob.setNoAi(true);
        return mob;
    }

    /** What each row says: department, blood, title, noise and wage item (a tag's id with '#'), Array, turret, socket. */
    private record Expect(String dept, BloodType blood, String title, String noise, String wage, int count, int array, String turret,
                          String socket) {
    }

    private static final Map<String, Expect> ROWS = Map.of(
            "creaking", new Expect("competition", BloodType.SAP, "Night Auditor", "Creak.", "minecraft:resin_clump", 1, 10, "lookout", "none"),
            "happy_ghast", new Expect("logistics", BloodType.WARM, "Chief Happiness Officer", "Oooh!", "minecraft:snowball", 1, 20, "lookout", "none"),
            "copper_golem", new Expect("logistics", BloodType.OIL, "Records Clerk", "Tink.", "minecraft:copper_nugget", 1, 30, "lookout", "none"),
            "nautilus", new Expect("marine", BloodType.COLD, "Diving Officer", "Glub.", "#minecraft:fishes", 1, 10, "lookout", "none"),
            "zombie_nautilus", new Expect("marine", BloodType.GRAVE, "Salvage", "Glurgh.", "minecraft:rotten_flesh", 2, 20, "bite", "none"),
            "camel_husk", new Expect("logistics", BloodType.GRAVE, "Desert Operations", "Hhhrrr.", "minecraft:rabbit_foot", 1, 20, "lookout", "none"),
            "parched", new Expect("night_shift", BloodType.MARROW, "Compliance (Desert)", "Rattle.", "minecraft:bone", 1, 10, "bow_weakness", "none"),
            "sulfur_cube", new Expect("facilities", BloodType.BRIMSTONE, "Containment", "Gloop.", "minecraft:slime_ball", 1, 10, "lookout", "none"));

    /** Every new arrival's row: department, blood, title and noise (in the language files), wage, and its head at the stations. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void everyNewArrivalHasItsRow(GameTestHelper h) {
        h.assertValueEqual(ROWS.size(), Supplier.NEW_ARRIVALS.size(), "a row for each new arrival");
        Language lang = Language.getInstance();
        ROWS.forEach((path, e) -> {
            EntityType<?> type = type(path);
            h.assertValueEqual(Staff.department(type), e.dept(), "the department of a " + path);
            h.assertTrue(lang.has("department.headinajar." + e.dept()), "the department's name for a " + path);
            h.assertValueEqual(BloodType.of(type), e.blood(), "the blood of a " + path);
            String title = HeadSpeak.titleKey(type, new CompoundTag());
            h.assertValueEqual(title, "title.headinajar.minecraft." + path, "the title key of a " + path);
            h.assertValueEqual(lang.getOrDefault(title), e.title(), "the title of a " + path);
            h.assertValueEqual(lang.getOrDefault(StaffTable.noise(type)), e.noise(), "the noise of a " + path);
            StaffTable.Wage wage = StaffTable.wage(type);
            h.assertFalse(wage.none(), "a " + path + " is paid (not filed as a trophy)");
            String named = wage.item() != null ? BuiltInRegistries.ITEM.getKey(wage.item()).toString() : "#" + wage.tag().location();
            h.assertValueEqual(named, e.wage(), "the wage of a " + path);
            h.assertValueEqual(wage.count(), e.count(), "how many " + e.wage() + " a " + path + " takes");
            h.assertValueEqual(HeadJobs.arrayOutput(type), e.array(), "the Array rate of a " + path);
            h.assertValueEqual(HeadJobs.turretAttack(type), e.turret(), "the turret attack of a " + path);
            h.assertValueEqual(HeadJobs.socketProgram(type), e.socket(), "the socket program of a " + path);
            h.assertTrue(lang.has("hint.headinajar.minecraft." + path + ".where") && lang.has("hint.headinajar.minecraft." + path + ".jar"),
                    "the Directory's hints for a " + path);
        });
        h.assertTrue(HeadJobs.turretJob(type("parched")) && HeadJobs.arrows("bow_weakness"), "a parched head shoots arrows");
        h.assertTrue(HeadJobs.turretJob(type("zombie_nautilus")), "a zombie nautilus head bites");
        h.assertFalse(HeadJobs.socketJob(type("creaking")), "the creaking's program is a later release's");
        h.succeed();
    }

    /** The saw's jars: the happy ghast's head needs an Executive jar, the sulfur cube is all head and Reinforced, the rest any jar. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void theJarsAreTheBooks(GameTestHelper h) {
        for (String path : Supplier.NEW_ARRIVALS.stream().map(id -> id.substring(10)).toList()) {
            EntityType<?> type = type(path);
            h.assertValueEqual(type.is(SawTable.EXECUTIVE), path.equals("happy_ghast"), "an Executive jar for a " + path);
            h.assertValueEqual(type.is(SawTable.HEAD_ONLY), path.equals("sulfur_cube"), "all head: " + path);
            h.assertValueEqual(type.is(SawTable.REINFORCED), path.equals("sulfur_cube"), "a Reinforced jar for a " + path);
            h.assertFalse(type.is(SawTable.NEVER), "the saw never takes a " + path);
        }
        h.succeed();
    }

    /** The Lanyard: the book's verdict for each, in its own line. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform", timeoutTicks = 200)
    public static void theLanyardTable(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "arrivals", FLOOR.south(4));
        for (String path : new String[]{"happy_ghast", "copper_golem", "nautilus", "zombie_nautilus", "camel_husk"}) {
            h.assertTrue(Workforce.refusal(p, spawn(h, path, FLOOR)) == null, "a " + path + " can be hired");
        }
        // the parched is hostile: worn down to half first
        Mob parched = spawn(h, "parched", FLOOR.east(2));
        refused(h, p, parched, "lanyard.not_ready");
        parched.setHealth(parched.getMaxHealth() / 2);
        h.assertTrue(Workforce.refusal(p, parched) == null, "a parched at half health can be hired");
        // babies: the ghastling and a small cube are refused by the child-labour line
        Mob ghastling = spawn(h, "happy_ghast", FLOOR.west(3));
        ((AgeableMob) ghastling).setBaby(true);
        refused(h, p, ghastling, "lanyard.baby");
        Mob cube = spawn(h, "sulfur_cube", FLOOR.north(3));
        refused(h, p, cube, "lanyard.unknown");
        ((AgeableMob) cube).setBaby(true);
        refused(h, p, cube, "lanyard.baby");
        // the creaking: no heart is furniture; a heart-bound one answers to the heart (its saved home says so)
        Mob creaking = spawn(h, "creaking", FLOOR.north(2).east(2));
        refused(h, p, creaking, "lanyard.creaking");
        h.assertFalse(Workforce.heartBound(creaking), "a spawned creaking has no heart");
        CompoundTag tag = creaking.saveWithoutId(new CompoundTag());
        tag.put("home_pos", NbtUtils.writeBlockPos(h.absolutePos(FLOOR.north(5))));
        creaking.load(tag);
        h.assertTrue(Workforce.heartBound(creaking), "a creaking with a home is heart-bound");
        refused(h, p, creaking, "lanyard.heart");
        // a jockey's mount already works for its rider
        Mob husk = spawn(h, "camel_husk", FLOOR.south(2).west(2));
        Mob rider = h.spawn(EntityType.HUSK, FLOOR.south(2).west(2));
        rider.setNoAi(true);
        h.assertTrue(rider.startRiding(husk, true), "setup: the husk rides the camel husk");
        Object[] no = Workforce.refusal(p, husk);
        h.assertTrue(no != null && "lanyard.poach".equals(no[0]), "a ridden camel husk is poaching");
        h.assertValueEqual(((Component) no[1]).getString(), rider.getDisplayName().getString(), "it works for its rider");
        Mob nautilus = spawn(h, "zombie_nautilus", FLOOR.south(2).east(3));
        Mob drowned = h.spawn(EntityType.DROWNED, FLOOR.south(2).east(3));
        drowned.setNoAi(true);
        h.assertTrue(drowned.startRiding(nautilus, true), "setup: the drowned rides the zombie nautilus");
        Object[] no2 = Workforce.refusal(p, nautilus);
        h.assertTrue(no2 != null && "lanyard.poach".equals(no2[0]), "a ridden zombie nautilus is poaching");
        // and a real hire goes through: a copper golem joins on its nugget
        Mob golem = spawn(h, "copper_golem", FLOOR.east(4));
        h.assertTrue(Workforce.useLanyard(p, new ItemStack(com.coolfreeze23.headinajar.registry.WorkRegistry.STAFF_LANYARD.get()), golem), "the copper golem was not hired");
        h.assertTrue(Workforce.isWorker(golem), "the copper golem is staff");
        done(h, p);
    }

    private static void refused(GameTestHelper h, ServerPlayer p, Mob mob, String key) {
        Object[] no = Workforce.refusal(p, mob);
        h.assertTrue(no != null, "a " + BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()) + " was not refused");
        h.assertValueEqual(no[0], key, "the Lanyard's answer for a " + BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
        h.assertTrue(Language.getInstance().has("refusal.headinajar." + key), "the line " + key);
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** Host files: where the jar sits on each body; the sulfur cube is all head and has none. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void theHostNecks(GameTestHelper h) {
        Map<String, Vec3> necks = Map.of("creaking", new Vec3(0, 1.85, 0), "happy_ghast", new Vec3(0, 4.0, 0), "copper_golem", new Vec3(0, 0.69, 0),
                "nautilus", new Vec3(0, 1.15, 0), "zombie_nautilus", new Vec3(0, 1.15, 0), "camel_husk", new Vec3(0, 2.15, 0.7),
                "parched", new Vec3(0, 1.5, 0));
        necks.forEach((path, neck) -> {
            Mob mob = spawn(h, path, FLOOR);
            h.assertTrue(Hosts.definition(mob) != null, "a host file for a " + path);
            h.assertTrue(Hosts.neck(mob).distanceTo(neck) < 1e-6, "the neck of a " + path + ": " + Hosts.neck(mob));
            mob.discard();
        });
        Mob cube = spawn(h, "sulfur_cube", FLOOR);
        h.assertTrue(Hosts.definition(cube) == null, "a sulfur cube is never a host");
        h.assertTrue(Hosts.definition(spawn(h, "camel_husk", FLOOR.east(3))).decayPerMinute() == 0, "a camel husk never rots");
        h.succeed();
    }

    /** The Body Printer: the sizes decide, and the booth's old rules say the rest. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void thePrinterCatalogue(GameTestHelper h) {
        Map<String, String> verdicts = Map.of("creaking", "printer.too_tall", "happy_ghast", "printer.too_tall", "camel_husk", "printer.too_tall",
                "copper_golem", "printer.facilities", "sulfur_cube", "printer.all_head", "nautilus", "SMALL", "zombie_nautilus", "SMALL",
                "parched", "MEDIUM");
        verdicts.forEach((path, want) -> {
            PrintCatalogue.Verdict v = PrintCatalogue.model(GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), type(path), 80), true);
            h.assertValueEqual(v.ok() ? v.size().name() : v.reason(), want, "the printer on a " + path);
        });
        h.assertTrue(PrintCatalogue.marine(type("nautilus")) && PrintCatalogue.marine(type("zombie_nautilus")), "the nautili are delivered wet");
        h.assertTrue(PrintCatalogue.burnsInDaylight(type("zombie_nautilus")) && !PrintCatalogue.burnsInDaylight(type("parched")), "who burns");
        h.succeed();
    }

    /** The Treadmill rows, each loaded by its own condition. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void theTreadmillRows(GameTestHelper h) {
        Map<String, String> rows = Map.of("creaking", "watched", "happy_ghast", "ghast", "copper_golem", "6", "nautilus", "swimmer",
                "zombie_nautilus", "swimmer", "camel_husk", "12", "parched", "4");
        rows.forEach((path, want) -> {
            TreadmillRate r = TreadmillRate.of(type(path));
            h.assertTrue(r != null, "a Treadmill row for a " + path);
            h.assertValueEqual(r.fe().map(String::valueOf).orElseGet(() -> r.refuse().orElse("?")), want, "the belt for a " + path);
        });
        h.assertTrue(TreadmillRate.of(type("sulfur_cube")) == null, "a sulfur cube is never on a belt");
        h.assertTrue(Language.getInstance().has("refusal.headinajar.treadmill.watched"), "the watched line");
        h.succeed();
    }

    /** The creaking runs on sap: the Oil Tap draws it, a syringe does not. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void theOilTapDrawsSap(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "sap", FLOOR.south(4));
        Mob creaking = spawn(h, "creaking", FLOOR);
        var syringe = (GeneticsItems.SyringeItem) ModRegistry.SYRINGE.get();
        h.assertValueEqual(syringe.draw(p, new ItemStack(syringe), creaking, InteractionHand.MAIN_HAND), InteractionResult.FAIL, "a syringe on a creaking");
        var tap = (GeneticsItems.SyringeItem) ModRegistry.OIL_TAP.get();
        h.assertValueEqual(tap.draw(p, new ItemStack(tap), creaking, InteractionHand.MAIN_HAND), InteractionResult.CONSUME, "the Oil Tap on a creaking");
        boolean drawn = false;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ModRegistry.BLOOD_SAMPLE.get()) && GeneticsItems.species(s) == creaking.getType()) drawn = true;
        }
        h.assertTrue(drawn, "a sample of the creaking");
        done(h, p);
    }

    /** The desk pairs: the Night Auditor and the Grief Counsellor, two copper golems, Wellbeing and Sleep. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void theNewPairs(GameTestHelper h) {
        h.assertTrue(DeskJobs.rivals(type("creaking"), EntityType.GHAST), "the Night Auditor and the Grief Counsellor");
        h.assertTrue(DeskJobs.rivalPair("friendsandfoes:copper_golem", "minecraft:copper_golem") != null, "the two copper golem departments");
        h.assertTrue(DeskJobs.colleagues(type("happy_ghast"), EntityType.PHANTOM), "Wellbeing and Sleep");
        h.assertFalse(DeskJobs.rivals(type("happy_ghast"), EntityType.GHAST), "the happy ghast does not mind the ghast");
        Language lang = Language.getInstance();
        for (String line : new String[]{DeskJobs.rivalPair("minecraft:creaking", "minecraft:ghast").line(),
                DeskJobs.rivalPair("minecraft:copper_golem", "friendsandfoes:copper_golem").line(),
                DeskJobs.colleaguePair("minecraft:phantom", "minecraft:happy_ghast").line()}) {
            h.assertTrue(line != null && lang.has("desk.headinajar." + line), "the pair's line " + line);
        }
        h.succeed();
    }

    /**
     * The Directory: the New Arrivals row is the eight, the census stays the 77; the first new arrival stamped brings the
     * memo, and every one sampled brings New Starters (and not the Complete Census).
     */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform", timeoutTicks = 200)
    public static void theNewArrivalsRowAndNewStarters(GameTestHelper h) {
        h.assertTrue(Supplier.delivered().equals(Supplier.NEW_ARRIVALS), "the row's cells");
        h.assertValueEqual(Directory.ON_THE_BOOKS.size(), 77, "the census");
        ServerPlayer p = TestPlayers.at(h, "starter", FLOOR.south(4));
        var server = h.getLevel().getServer();
        String key = GeneArchive.key(p);
        h.assertValueEqual(Memos.told(p, Memos.Memo.NEW_ARRIVAL), 0, "no memo before a stamp");
        h.assertTrue(Directory.file(server, key, type("creaking"), Directory.Stamp.JARRED, p.getGameProfile().getName()), "the creaking is filed");
        h.assertValueEqual(Memos.told(p, Memos.Memo.NEW_ARRIVAL), 1, "the memo on the first new arrival");
        h.assertFalse(earned(h, p, "new_starters"), "New Starters before every sample");
        for (String id : Supplier.NEW_ARRIVALS) Directory.file(server, key, type(id.substring(10)), Directory.Stamp.SAMPLED, p.getGameProfile().getName());
        h.assertTrue(earned(h, p, "new_starters"), "New Starters once every new arrival is sampled");
        h.assertFalse(earned(h, p, "complete_census"), "the new arrivals are not the census");
        h.assertValueEqual(Memos.told(p, Memos.Memo.NEW_ARRIVAL), 1, "the memo comes once");
        done(h, p);
    }

    private static boolean earned(GameTestHelper h, ServerPlayer p, String id) {
        AdvancementHolder holder = h.getLevel().getServer().getAdvancements().get(HeadInAJar.id(id));
        if (holder == null) throw new GameTestAssertException("no advancement " + id);
        return p.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
