package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.compat.OldHeadSlot;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.host.FlierSeat;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The 0.5.1 fixes, each through the path the game takes: goggles handed back from an old Curios head slot to a creative
 * player, touched through the item's own pickup; a jar getting off a flier by sneak through the flier's own tick, with
 * what its client is sent.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReleaseFiveOneGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h, BlockPos at, GameType mode) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(mode);
        player.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(v.x, v.y, v.z, 0, 0);
        return player;
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static void fill(net.minecraft.world.Container c) {
        for (int i = 0; i < c.getContainerSize(); i++) c.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
    }

    private static int count(net.minecraft.world.Container c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    // --- goggles from an old Curios head slot ------------------------------------------------------------------------

    /**
     * A creative player with a full inventory and a full Ender Chest gets the goggles back at their feet, as a survival
     * player does, and touching them does not destroy them (vanilla destroys what such a player touches and has no
     * room for: shown here on a plain pair). Once there is room, the same touch picks them up.
     */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void handedBackGogglesWaitAtACreativePlayersFeet(GameTestHelper h) {
        var goggles = ModRegistry.RELAY_GOGGLES_MK2.get();
        ServerPlayer p = player(h, FLOOR, GameType.CREATIVE);
        h.assertTrue(p.hasInfiniteMaterials(), "the player is not in creative");
        fill(p.getInventory());
        fill(p.getEnderChestInventory());
        // vanilla's rule, on a pair that was simply dropped: a creative player with no room destroys what they touch
        ItemEntity plain = new ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), new ItemStack(goggles));
        plain.setNoPickUpDelay();
        h.getLevel().addFreshEntity(plain);
        plain.playerTouch(p);
        h.assertTrue(plain.isRemoved() && count(p.getInventory(), goggles) == 0, "vanilla kept a pair a full creative player touched");

        OldHeadSlot.giveBack(p, () -> net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(goggles)));
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(2), e -> e.getItem().is(goggles) && !e.isRemoved());
        h.assertValueEqual(drops.size(), 1, "goggles at the creative player's feet");
        ItemEntity drop = drops.get(0);
        h.assertValueEqual(drop.getAge(), -32768, "the dropped goggles' age (they must never despawn)");
        drop.setNoPickUpDelay();
        for (int i = 0; i < 3; i++) drop.playerTouch(p);
        h.assertTrue(!drop.isRemoved() && drop.getItem().is(goggles) && drop.getItem().getCount() == 1,
                "the creative player's touch destroyed the handed-back goggles");
        h.assertValueEqual(count(p.getInventory(), goggles), 0, "goggles in a full inventory");
        // room again: the touch picks them up
        p.getInventory().setItem(9, ItemStack.EMPTY);
        drop.playerTouch(p);
        h.assertTrue(drop.isRemoved(), "the goggles were not picked up once there was room");
        h.assertValueEqual(count(p.getInventory(), goggles), 1, "goggles in the inventory once there was room");
        done(h, p);
    }

    // --- getting off a flier -----------------------------------------------------------------------------------------

    private static <T extends Mob> T host(GameTestHelper h, EntityType<T> type, BlockPos at, ServerPlayer sawyer) {
        T mob = h.spawn(type, at);
        mob.setNoAi(true);
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "saw failed on " + type);
        mob.setNoGravity(true);
        return mob;
    }

    /** The yaws of the positions {@link #toldToStand} last read, in the same order. */
    private static final List<Float> YAWS = new ArrayList<>();

    /** Where the client was told to stand, from the packets sent to the player's connection since the last call. */
    private static List<Vec3> toldToStand(ServerPlayer p) {
        List<Vec3> out = new ArrayList<>();
        YAWS.clear();
        if (!(p.connection.getConnection().channel() instanceof io.netty.channel.embedded.EmbeddedChannel wire)) return out;
        for (Object o; (o = wire.readOutbound()) != null; ) {
            List<Object> packets = new ArrayList<>();
            packets.add(o);
            if (o instanceof ClientboundBundlePacket bundle) bundle.subPackets().forEach(packets::add);
            for (Object packet : packets) {
                if (packet instanceof ClientboundPlayerPositionPacket at && at.getRelativeArguments().isEmpty()) {
                    // x, y, z, and the yaw in the fourth place (only absolute teleports are counted)
                    out.add(new Vec3(at.getX(), at.getY(), at.getZ()));
                    YAWS.add(at.getYRot());
                }
            }
        }
        return out;
    }

    /**
     * Sneak on a flier hovering two blocks up lets the jar off (the flier's own tick runs the rider's dismount), and the
     * jar is set down on the floor beside it, clear of its box, facing it, with no fall to take; its client is told
     * where it stands and where it faces. Vanilla
     * put it at the top of the flier's box: six blocks up for a ghast.
     */
    private static void getsOffOntoTheFloor(GameTestHelper h, EntityType<? extends Mob> type) {
        ServerPlayer p = player(h, FLOOR, GameType.SURVIVAL);
        Mob flier = host(h, type, FLOOR.above(2), p);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, flier), "could not board the " + type);
        flier.setOnGround(false);
        double[] floor = new double[1];
        int[] glass = new int[1];
        h.runAfterDelay(2, () -> {
            h.assertTrue(FlierSeat.drop(flier) > 1 && FlierSeat.drop(flier) <= FlierSeat.SAFE_DROP, "the " + type + " is not just above the floor: " + FlierSeat.drop(flier));
            floor[0] = flier.getY() - FlierSeat.drop(flier);
            toldToStand(p);
            glass[0] = FormActions.state(p).integrity();
            p.setShiftKeyDown(true);
        });
        h.runAfterDelay(4, () -> {
            h.assertTrue(p.getVehicle() == null, "sneak two blocks up did not let the jar off the " + type);
            h.assertTrue(Math.abs(p.getY() - floor[0]) < 0.01, "the jar was left " + (p.getY() - floor[0]) + " above the floor by the " + type);
            h.assertFalse(p.getBoundingBox().intersects(flier.getBoundingBox()), "the jar was set down inside the " + type);
            h.assertTrue(p.fallDistance == 0, "the jar is falling");
            h.assertValueEqual(FormActions.state(p).integrity(), glass[0], "glass after getting off the " + type);
            List<Vec3> told = toldToStand(p);
            h.assertTrue(told.stream().anyMatch(v -> v.distanceTo(p.position()) < 0.01), "the client was not told where the jar stands: " + told);
            // facing the flier's centre, so a camera behind the jar is clear of the flier
            float toward = (float) Math.toDegrees(Math.atan2(-(flier.getX() - p.getX()), flier.getZ() - p.getZ()));
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(p.getYRot() - toward)) < 1, "the jar faces " + p.getYRot() + ", not the " + type + " at " + toward);
            int i = told.size() - 1;
            h.assertTrue(i >= 0 && Math.abs(net.minecraft.util.Mth.wrapDegrees(YAWS.get(i) - toward)) < 1, "the client was not told to face the " + type + ": " + YAWS);
            flier.discard();
            done(h, p);
        });
    }

    @GameTest(template = "tall_platform", timeoutTicks = 40)
    public static void gettingOffAGhastSetsTheJarOnTheFloor(GameTestHelper h) {
        getsOffOntoTheFloor(h, EntityType.GHAST);
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void gettingOffABatSetsTheJarOnTheFloor(GameTestHelper h) {
        getsOffOntoTheFloor(h, EntityType.BAT);
    }
}
