package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.supplier.RegisteredCondition;
import com.coolfreeze23.headinajar.supplier.Supplier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The supplier contract (0.8.0), as it holds with or without a supplier installed: every test here passes both ways,
 * reading what the registry really holds. The tests that need the supplied creatures are {@link SuppliedGameTests}.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class SupplierContractGameTests {
    static boolean test(String registry, String id) {
        return new RegisteredCondition(ResourceLocation.parse(registry), ResourceLocation.parse(id)).test(ICondition.IContext.EMPTY);
    }

    /** The condition asks the registry: vanilla's own ids pass, a missing id or an unknown registry fails. */
    @GameTest(template = "platform")
    public static void theRegisteredConditionAsksTheRegistry(GameTestHelper h) {
        h.assertTrue(test("entity_type", "minecraft:pig"), "a pig is registered");
        h.assertTrue(test("minecraft:item", "minecraft:egg"), "an egg is registered");
        h.assertTrue(test("block", "headinajar:time_clock"), "our own blocks count too");
        h.assertFalse(test("entity_type", "minecraft:no_such_creature"), "a missing id is not registered");
        h.assertFalse(test("no_such_registry", "minecraft:pig"), "an unknown registry has nothing in it");
        boolean creaking = test("entity_type", "minecraft:creaking");
        h.assertTrue(creaking == Supplier.registered("minecraft:creaking"), "the condition and the helper agree");
        h.assertTrue(Supplier.delivered().contains("minecraft:creaking") == creaking, "the creaking is on file exactly when it is registered");
        h.succeed();
    }

    /** A new arrival's staff row and host file load exactly when it is registered: their conditions are honoured. */
    @GameTest(template = "platform")
    public static void aSuppliedCreaturesFilesLoadOnlyWhenItIsRegistered(GameTestHelper h) {
        for (String id : Supplier.NEW_ARRIVALS) {
            ResourceLocation rl = ResourceLocation.parse(id);
            boolean on = Supplier.registered(id);
            h.assertTrue(com.coolfreeze23.headinajar.staff.StaffTable.hasRow(rl) == on, "staff row of " + id + " loaded: " + !on);
            if (!id.equals("minecraft:sulfur_cube")) {
                h.assertTrue(com.coolfreeze23.headinajar.host.Hosts.definitions().containsKey(rl) == on, "host file of " + id + " loaded: " + !on);
            }
        }
        h.succeed();
    }

    /**
     * Lines the server sends carry their English beside the key, so an older game joined to this server (it has never
     * heard of 0.8.0's keys) shows the English rather than the key's name: the status line and a refusal.
     */
    @GameTest(template = "platform")
    public static void newLinesTravelWithTheirEnglish(GameTestHelper h) {
        for (net.minecraft.network.chat.Component c : java.util.List.of(
                com.coolfreeze23.headinajar.command.HeadOfficeCommand.arrivals(h.getLevel().getServer()),
                com.coolfreeze23.headinajar.feedback.Refusals.line("head.discontinued"))) {
            h.assertTrue(c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getFallback() != null && !t.getFallback().equals(t.getKey()) && !t.getFallback().isEmpty(), "no English beside " + c);
        }
        var t = (net.minecraft.network.chat.contents.TranslatableContents) com.coolfreeze23.headinajar.feedback.Refusals.line("head.discontinued").getContents();
        h.assertValueEqual(t.getFallback(), "Head Office cannot seat a creature this world has never heard of.", "the English beside the refusal");
        h.succeed();
    }
}
