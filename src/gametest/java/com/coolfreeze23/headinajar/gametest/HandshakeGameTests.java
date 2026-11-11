package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.host.HandshakeRule;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Neural Handshake on boarding a host starts even when the client learns it is riding a tick late. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class HandshakeGameTests {
    @GameTest(template = "platform")
    public static void aHandshakeWaitsForTheRideToShow(GameTestHelper h) {
        // not riding yet, inside the grace: the handshake waits
        h.assertFalse(HandshakeRule.over(false, false, 0), "dropped on the first tick, before the ride showed");
        h.assertFalse(HandshakeRule.over(false, false, HandshakeRule.GRACE_TICKS - 1), "dropped inside the grace");
        // riding: never over for being off the host
        h.assertFalse(HandshakeRule.over(true, false, 0), "over while riding");
        h.assertFalse(HandshakeRule.over(true, true, HandshakeRule.GRACE_TICKS + 5), "over while riding, after the grace");
        // never riding within the grace: over
        h.assertTrue(HandshakeRule.over(false, false, HandshakeRule.GRACE_TICKS), "kept waiting past the grace");
        // riding seen, then lost (got down, the host gone): over at once
        h.assertTrue(HandshakeRule.over(false, true, 0), "went on after getting down");
        h.succeed();
    }
}
