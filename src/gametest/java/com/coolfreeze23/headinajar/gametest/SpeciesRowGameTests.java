package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.DeskJobs;
import com.coolfreeze23.headinajar.block.HeadJobs;
import com.coolfreeze23.headinajar.genetics.BloodType;
import com.coolfreeze23.headinajar.genetics.Directory;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffTable;
import com.coolfreeze23.headinajar.supplier.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The species rows (0.8.0) as they hold with or without a supplier installed: the three station fields of the staff
 * rows answer exactly what the old code tables answered for every creature that is not a new arrival, the desk pairs
 * keyed by id are the old pairs, the bloodless tools draw what they drew, and the census stays the 77. Each test reads
 * what the registry really holds, so it passes both ways. The tests that need the new arrivals themselves are
 * {@link SuppliedSpeciesGameTests}.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpeciesRowGameTests {
    static String id(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    /**
     * Every creature's head gets the same Array rate, turret attack and socket program from its staff row as from the
     * old tables (they are the fallback now), and every one of the 81 vanilla rows says all three, so the rows decide.
     */
    @GameTest(template = "platform")
    public static void theStationFieldsMoveNothing(GameTestHelper h) {
        int rows = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String id = id(type);
            if (Supplier.NEW_ARRIVALS.contains(id)) continue;
            h.assertValueEqual(HeadJobs.arrayOutput(type), HeadJobs.arrayTable(type), "the Array rate of " + id);
            h.assertValueEqual(HeadJobs.turretAttack(type), HeadJobs.turretTable(type), "the turret attack of " + id);
            h.assertValueEqual(HeadJobs.socketProgram(type), HeadJobs.socketTable(type), "the socket program of " + id);
            h.assertValueEqual(HeadJobs.turretJob(type), !HeadJobs.LOOKOUT.equals(HeadJobs.turretTable(type)), "the turret job of " + id);
            StaffTable.Row row = StaffTable.row(type);
            if (row != null && id.startsWith("minecraft:")) {
                rows++;
                h.assertTrue(row.array() != null && row.turret() != null && row.socket() != null, "the row of " + id + " says all three");
            }
        }
        h.assertValueEqual(rows, 81, "vanilla staff rows");
        // the old tables, word for word, at their corners
        h.assertValueEqual(HeadJobs.arrayOutput(EntityType.COW), 10, "a cow");
        h.assertValueEqual(HeadJobs.arrayOutput(EntityType.ZOMBIE), 20, "a zombie");
        h.assertValueEqual(HeadJobs.arrayOutput(EntityType.WANDERING_TRADER), 40, "a wandering trader");
        h.assertValueEqual(HeadJobs.arrayOutput(EntityType.PIGLIN_BRUTE), 60, "a piglin brute");
        h.assertValueEqual(HeadJobs.arrayOutput(EntityType.ENDERMAN), 80, "an enderman");
        h.assertValueEqual(HeadJobs.arrayOutput(EntityType.WITHER_SKELETON), 100, "a wither skeleton");
        h.assertValueEqual(HeadJobs.arrayOutput(EntityType.EVOKER), 120, "an evoker");
        h.assertValueEqual(HeadJobs.turretAttack(EntityType.STRAY), HeadJobs.BOW_SLOWNESS, "a stray");
        h.assertValueEqual(HeadJobs.turretAttack(EntityType.ELDER_GUARDIAN), HeadJobs.LASER, "an elder guardian");
        h.assertValueEqual(HeadJobs.turretAttack(EntityType.DROWNED), HeadJobs.BITE, "a drowned");
        h.assertValueEqual(HeadJobs.turretAttack(EntityType.COW), HeadJobs.LOOKOUT, "a cow keeps watch");
        h.assertValueEqual(HeadJobs.socketProgram(EntityType.HUSK), HeadJobs.BEAT, "a husk");
        h.assertValueEqual(HeadJobs.socketProgram(EntityType.VILLAGER), HeadJobs.COUNT, "a villager");
        h.assertValueEqual(HeadJobs.socketProgram(EntityType.ENDERMAN), HeadJobs.STARE, "an enderman");
        h.assertFalse(HeadJobs.socketJob(EntityType.PIG), "a pig runs no program");
        h.succeed();
    }

    /** The desk pairs, keyed by id now, are the old eight rivals with no line, and no vanilla pair is a colleague pair. */
    @GameTest(template = "platform")
    public static void theDeskPairsAreTheOldPairs(GameTestHelper h) {
        EntityType<?>[][] old = {{EntityType.VILLAGER, EntityType.ZOMBIE_VILLAGER}, {EntityType.VILLAGER, EntityType.PILLAGER},
                {EntityType.VILLAGER, EntityType.VINDICATOR}, {EntityType.SKELETON, EntityType.WOLF}, {EntityType.PIGLIN, EntityType.WITHER_SKELETON},
                {EntityType.CREEPER, EntityType.CAT}, {EntityType.CREEPER, EntityType.OCELOT}, {EntityType.SPIDER, EntityType.ARMADILLO}};
        for (EntityType<?>[] p : old) {
            h.assertTrue(DeskJobs.rivals(p[0], p[1]) && DeskJobs.rivals(p[1], p[0]), "rivals " + id(p[0]) + " and " + id(p[1]));
            h.assertTrue(DeskJobs.rivalPair(id(p[0]), id(p[1])).line() == null, "an old pair says nothing new");
        }
        int rivals = 0, colleagues = 0;
        for (EntityType<?> a : BuiltInRegistries.ENTITY_TYPE) {
            if (!id(a).startsWith("minecraft:") || Supplier.NEW_ARRIVALS.contains(id(a))) continue;
            for (EntityType<?> b : BuiltInRegistries.ENTITY_TYPE) {
                if (!id(b).startsWith("minecraft:") || Supplier.NEW_ARRIVALS.contains(id(b)) || id(a).compareTo(id(b)) >= 0) continue;
                if (DeskJobs.rivals(a, b)) rivals++;
                if (DeskJobs.colleagues(a, b)) colleagues++;
            }
        }
        // the creaking's rival is the ghast, which is vanilla; with the creaking absent that pair never meets
        h.assertValueEqual(rivals, 8, "rival pairs among vanilla's creatures");
        h.assertValueEqual(colleagues, 0, "cross-department colleagues among vanilla's creatures");
        h.assertFalse(DeskJobs.rivals(EntityType.COW, EntityType.PIG), "a cow and a pig");
        h.succeed();
    }

    /**
     * The collection tools draw what they always drew: a syringe any blood, each bloodless tool its own kind; sap is
     * bloodless and only the Oil Tap draws it. Untagged creatures are still warm.
     */
    @GameTest(template = "platform")
    public static void theToolsDrawWhatTheyDrew(GameTestHelper h) {
        for (BloodType tool : new BloodType[]{BloodType.WARM, BloodType.MARROW, BloodType.OIL, BloodType.ECTO}) {
            for (BloodType blood : BloodType.values()) {
                boolean was = blood == BloodType.SAP ? tool == BloodType.OIL : tool.bloodless ? blood == tool : !blood.bloodless;
                h.assertValueEqual(tool.draws(blood), was, tool + " on " + blood);
            }
        }
        h.assertTrue(BloodType.SAP.bloodless && !BloodType.SAP.flesh(), "sap is bloodless");
        h.assertTrue(GeneticsItems.SyringeItem.toolFor(BloodType.SAP) == ModRegistry.OIL_TAP.get(), "the Oil Tap draws sap");
        h.assertTrue(BloodType.of(EntityType.COW) == BloodType.WARM && BloodType.of(EntityType.IRON_GOLEM) == BloodType.OIL
                && BloodType.of(EntityType.SKELETON) == BloodType.MARROW && BloodType.of(EntityType.GHAST) == BloodType.BRIMSTONE, "old blood");
        h.succeed();
    }

    /**
     * The census stays the 77 and names no new arrival; The Competition is outside the fourteen departments and no
     * vanilla creature is in it; the gated files load exactly when their creature is registered.
     */
    @GameTest(template = "platform")
    public static void theCensusStaysTheSeventySeven(GameTestHelper h) {
        h.assertValueEqual(Directory.ON_THE_BOOKS.size(), 77, "the census");
        for (String id : Supplier.NEW_ARRIVALS) {
            h.assertFalse(Directory.VANILLA.contains(id) || Directory.ON_THE_BOOKS.contains(id), id + " is in the census");
            ResourceLocation rl = ResourceLocation.parse(id);
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(rl);
            h.assertValueEqual(type.isPresent(), Supplier.delivered().contains(id), id + " is on file exactly when registered");
            if (type.isPresent()) h.assertTrue(StaffTable.row(type.get()) != null, id + " has its staff row");
        }
        h.assertFalse(Staff.DEPARTMENTS.containsAll(Staff.OUTSIDE), "The Competition is not one of the fourteen");
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!Supplier.NEW_ARRIVALS.contains(id(type))) h.assertFalse(Staff.OUTSIDE.contains(Staff.department(type)), id(type) + " works for the competition");
        }
        boolean starters = h.getLevel().getServer().getAdvancements().get(HeadInAJar.id("new_starters")) != null;
        h.assertValueEqual(starters, !Supplier.delivered().isEmpty(), "New Starters is there exactly when a new arrival is");
        h.succeed();
    }
}
