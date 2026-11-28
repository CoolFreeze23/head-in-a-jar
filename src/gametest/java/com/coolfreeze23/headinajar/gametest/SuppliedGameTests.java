package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.supplier.Supplier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The tests of the supplied creatures (0.8.0). They are registered only when every new arrival is registered (a game
 * with the supplier, or a later game that has them itself), so a game without them runs exactly the old suite. On the
 * GameTest server, {@code -Psupplier} loads the supplier from {@code libs/}.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
public class SuppliedGameTests {
    @SubscribeEvent
    static void register(RegisterGameTestsEvent event) {
        if (Supplier.delivered().size() == Supplier.NEW_ARRIVALS.size()) {
            HeadInAJar.LOGGER.info("The supplier has delivered: registering the supplied creatures' tests");
            event.register(SuppliedGameTests.class);
        }
    }

    /** Every new arrival is on file, in the order Head Office lists them. */
    @GameTest(templateNamespace = HeadInAJar.MOD_ID, template = "platform")
    public static void everyNewArrivalIsDelivered(GameTestHelper h) {
        h.assertTrue(Supplier.delivered().equals(Supplier.NEW_ARRIVALS), "all eight new arrivals are registered: " + Supplier.delivered());
        h.succeed();
    }
}
