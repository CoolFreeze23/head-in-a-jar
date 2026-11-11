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

    /** The saw used on an armour stand (a living thing that is not a creature) says what it is for. */
    @GameTest(template = "platform")
    public static void theSawOnAnArmourStandSaysWhatItIsFor(GameTestHelper h) {
        net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var at = h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(new net.minecraft.core.BlockPos(7, 2, 7)));
        p.moveTo(at.x, at.y, at.z, 0, 0);
        var stand = h.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, new net.minecraft.core.BlockPos(7, 2, 9));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(com.coolfreeze23.headinajar.registry.ModRegistry.SURGICAL_SAW.get()));
        p.interactOn(stand, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertValueEqual(com.coolfreeze23.headinajar.feedback.Refusals.lastDelivered(p), "item.air.saw", "the saw on an armour stand");
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }
}
