package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.SeparationChairBlock;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Sitting down in the Separation Chair by a click: a click that cannot seat the player says so, a seat left behind by
 * an earlier try never stands in the way, and someone already on one of the company's machines is told to get down.
 * Clicks go through the game's own use path.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ChairSeatGameTests {
    private static final BlockPos CHAIR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(CHAIR.south(2)));
        player.moveTo(v.x, v.y, v.z, 180, 30);
        return player;
    }

    private static void placeChair(GameTestHelper h) {
        h.setBlock(CHAIR, ModRegistry.SEPARATION_CHAIR.get().defaultBlockState().setValue(SeparationChairBlock.FACING, Direction.SOUTH));
    }

    /** An empty-hand right-click on the chair, as the client sends it (the menu send a test player cannot take is allowed). */
    private static void click(GameTestHelper h, ServerPlayer p) {
        BlockPos abs = h.absolutePos(CHAIR);
        try {
            p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
        } catch (RuntimeException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (String.valueOf(t.getMessage()).contains("may not be sent")) return;
            }
            throw e;
        }
    }

    private static List<SeatEntity> seats(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(SeatEntity.class, new AABB(h.absolutePos(CHAIR)).inflate(1));
    }

    /** Another mod refuses the ride (the mount event cancelled): the click cannot seat, says so, and leaves no seat. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aChairThatCannotSeatYouSaysSo(GameTestHelper h) {
        placeChair(h);
        ServerPlayer p = player(h);
        java.util.function.Consumer<EntityMountEvent> refuse = e -> {
            if (e.isMounting() && e.getEntityMounting() == p) e.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(refuse);
        try {
            click(h, p);
        } finally {
            NeoForge.EVENT_BUS.unregister(refuse);
        }
        String said = Refusals.lastDelivered(p);
        h.assertTrue(p.getVehicle() == null, "seated after all");
        h.assertValueEqual(said, "chair.no_seat", "what the click said");
        h.assertTrue(seats(h).isEmpty(), "a seat was left in the chair");
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** Ten players in turn, each with an empty seat left in the chair before the click: every one sits, one seat at a time. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aStaleSeatNeverStopsTheNextSitter(GameTestHelper h) {
        placeChair(h);
        for (int i = 0; i < 10; i++) {
            SeatEntity.at(h.getLevel(), h.absolutePos(CHAIR), Direction.SOUTH);
            ServerPlayer p = player(h);
            click(h, p);
            h.assertTrue(p.getVehicle() instanceof SeatEntity, "sitter " + (i + 1) + " was not seated (" + Refusals.lastDelivered(p) + ")");
            h.assertValueEqual(seats(h).size(), 1, "seats in the chair with sitter " + (i + 1));
            p.stopRiding();
            h.getLevel().getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }

    /** Someone riding one of the company's seats elsewhere is told to get down; the chair does not pull them off. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aRiderIsToldToGetDownFirst(GameTestHelper h) {
        placeChair(h);
        ServerPlayer p = player(h);
        SeatEntity elsewhere = SeatEntity.at(h.getLevel(), h.absolutePos(CHAIR.east(3)), Direction.SOUTH);
        h.assertTrue(p.startRiding(elsewhere, true), "setup: could not ride the other seat");
        click(h, p);
        h.assertValueEqual(Refusals.lastDelivered(p), "chair.riding", "what the click said");
        h.assertTrue(p.getVehicle() == elsewhere, "the click pulled the rider off");
        p.stopRiding();
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }
}
