package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.ChargingDockBlock;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The 0.5.2 fixes, each through the path the game takes: the Surgical Saw's window measured with the player's own
 * attack, weapon by weapon, and the cut made by a use on the creature; a head set down on a Charging Dock by a click on
 * the dock, and the jar woken there by the swap key.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReleaseFiveTwoGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h, BlockPos at, float yaw, float pitch) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(v.x, v.y, v.z, yaw, pitch);
        return player;
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    // --- the Surgical Saw's window ------------------------------------------------------------------------------

    private static final java.lang.reflect.Field STRENGTH;

    static {
        try {
            STRENGTH = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            STRENGTH.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A fully charged hit by the player's own attack (no crit: not falling), with the weapon's damage on the player as
     * holding it puts it there (a test player is never ticked, so its equipment is never read again by itself).
     */
    private static void hit(ServerPlayer p, ItemStack weapon, Mob mob) {
        p.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            var instance = p.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(modifier.id());
                instance.addTransientModifier(modifier);
            }
        });
        try {
            STRENGTH.setInt(p, 200);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        mob.invulnerableTime = 0;
        p.attack(mob);
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            var instance = p.getAttribute(attribute);
            if (instance != null) instance.removeModifier(modifier.id());
        });
    }

    /** The common hosts and the plain weapons a new player has: the bare hand, then wood to diamond, swords and axes. */
    private static final List<EntityType<? extends Mob>> HOSTS = List.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP,
            EntityType.VILLAGER, EntityType.SKELETON, EntityType.ZOMBIE);
    private static final List<Item> WEAPONS = List.of(Items.AIR, Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
            Items.DIAMOND_SWORD, Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE);

    /**
     * Every plain weapon leaves every common host, for at least one hit before the hit that kills, at half its health
     * or less, and the saw takes it there by a use on the creature (the event a right-click fires). The one exception
     * is a weapon whose first full hit kills (a stone, iron or diamond axe on a sheep: 9 against 8): no window can hold
     * a hit there. The health after each hit is written to the log, one line a pair. In a batch of its own: its 50-odd
     * cuts in one tick would push other tests' memos out of the record they read.
     */
    @GameTest(template = "platform", timeoutTicks = 40, batch = "saw_window")
    public static void everyPlainWeaponLeavesEveryCommonHostInTheSawsWindow(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR, -90, 0);
        List<String> missed = new ArrayList<>();
        List<String> oneHit = new ArrayList<>();
        for (EntityType<? extends Mob> type : HOSTS) {
            for (Item item : WEAPONS) {
                String name = type.toShortString() + " / " + (item == Items.AIR ? "fist" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath());
                // first the whole sequence, to the death
                Mob mob = h.spawn(type, FLOOR.east(2));
                mob.setNoAi(true);
                float max = mob.getMaxHealth();
                StringBuilder seq = new StringBuilder().append(max);
                int window = -1;
                for (int n = 1; n <= 40 && !mob.isDeadOrDying(); n++) {
                    hit(p, new ItemStack(item), mob);
                    seq.append(" -> ").append(mob.isDeadOrDying() ? "dead" : String.valueOf(mob.getHealth()));
                    if (window < 0 && !mob.isDeadOrDying() && mob.getHealth() <= max * SurgicalSawItem.MAX_HEALTH_FRACTION) window = n;
                }
                mob.discard();
                HeadInAJar.LOGGER.info("saw window: {}: {} (first hit in the window: {})", name, seq, window < 0 ? "none" : window);
                if (window < 0) {
                    if (seq.toString().equals(max + " -> dead")) oneHit.add(name);
                    else missed.add(name + " " + seq);
                    continue;
                }
                // then a fresh one, worn down to that hit, and sawn
                Mob host = h.spawn(type, FLOOR.east(2));
                host.setNoAi(true);
                for (int n = 0; n < window; n++) hit(p, new ItemStack(item), host);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
                p.interactOn(host, InteractionHand.MAIN_HAND);
                if (!Hosts.isHost(host)) missed.add(name + ": not sawn at " + host.getHealth());
                host.discard();
            }
        }
        h.assertTrue(missed.isEmpty(), "no hit in the saw's window: " + missed);
        h.assertValueEqual(oneHit.toString(), "[sheep / stone_axe, sheep / iron_axe, sheep / diamond_axe]", "the pairs whose first hit kills");
        // above the window it still refuses
        Mob cow = h.spawn(EntityType.COW, FLOOR.east(2));
        cow.setNoAi(true);
        cow.setHealth(cow.getMaxHealth() * SurgicalSawItem.MAX_HEALTH_FRACTION + 0.5f);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertFalse(Hosts.isHost(cow), "the saw took a cow above half its health");
        done(h, p);
    }

    // --- a jar on the Charging Dock --------------------------------------------------------------------------------

    /** The yaws of the absolute positions the player's client was sent since the last call, in order. */
    private static List<Float> toldToStand(ServerPlayer p) {
        List<Float> yaws = new ArrayList<>();
        if (!(p.connection.getConnection().channel() instanceof io.netty.channel.embedded.EmbeddedChannel wire)) return yaws;
        for (Object o; (o = wire.readOutbound()) != null; ) {
            List<Object> packets = new ArrayList<>();
            packets.add(o);
            if (o instanceof ClientboundBundlePacket bundle) bundle.subPackets().forEach(packets::add);
            for (Object packet : packets) {
                if (packet instanceof ClientboundPlayerPositionPacket at && at.getRelativeArguments().isEmpty()) yaws.add(at.getYRot());
            }
        }
        return yaws;
    }

    /**
     * A head set down on a Charging Dock from its front (a click on the dock, carrying the head) rests side-on to the
     * dock's column, and the swap key wakes the jar there facing the same way: its crosshair is off the dock, and its
     * client is told that yaw. Before, the jar took the body's yaw and looked straight into the column.
     */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aJarWokenOnTheChargingDockFacesSideOnToIt(GameTestHelper h) {
        // the player faces south (+Z), at the dock's front: the dock faces north, its column at its south edge
        ServerPlayer p = player(h, FLOOR, 0, 35);
        BlockPos dockAt = FLOOR.south(2);
        h.setBlock(dockAt, ModRegistry.CHARGING_DOCK.get().defaultBlockState().setValue(ChargingDockBlock.FACING, Direction.NORTH));
        h.assertTrue(FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.west(2))), null), "separate failed");
        // the satchel in the bag, not the hand: the hand stays empty for the clicks
        p.getInventory().setItem(9, new ItemStack(ModRegistry.HEAD_SATCHEL.get()));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        HeadJarEntity floor = h.getLevel().getEntitiesOfClass(HeadJarEntity.class, p.getBoundingBox().inflate(8)).get(0);
        p.interactOn(floor, InteractionHand.MAIN_HAND);
        h.assertTrue(FormActions.state(p).carryingHead(), "the head was not picked up");
        // a click on the dock, carrying the head, sets it on the plate
        BlockPos abs = h.absolutePos(dockAt);
        p.gameMode.useItemOn(p, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
        var heads = h.getLevel().getEntitiesOfClass(HeadJarEntity.class, new net.minecraft.world.phys.AABB(abs).inflate(1));
        h.assertTrue(heads.size() == 1 && ChargingDockBlock.under(h.getLevel(), heads.get(0).getBoundingBox()) != null, "the head is not on the dock");
        // side-on: west (90) or east (270), never facing the column (south, 0) or straight out (north, 180)
        float headYaw = heads.get(0).getYRot();
        h.assertTrue(Mth.degreesDifferenceAbs(headYaw, 90) < 1 || Mth.degreesDifferenceAbs(headYaw, 270) < 1, "the head on the dock faces " + headYaw);
        toldToStand(p);
        Network.action(p, new Network.JarAction(Network.Action.SWAP));
        h.assertTrue(FormActions.state(p).form() == Form.JAR, "the swap did not wake the jar: " + FormActions.state(p).form());
        h.assertTrue(ChargingDockBlock.under(p) != null, "the jar is not on the dock");
        h.assertTrue(Mth.degreesDifferenceAbs(p.getYRot(), headYaw) < 1, "the jar faces " + p.getYRot() + ", its head faced " + headYaw);
        List<Float> told = toldToStand(p);
        h.assertTrue(!told.isEmpty() && Mth.degreesDifferenceAbs(told.get(told.size() - 1), headYaw) < 1, "the client was told the yaws " + told);
        // its crosshair (at the same downward look the body had) is off the dock
        h.assertTrue(ChargingDockBlock.aimed(p) == null, "the jar on the dock still aims at the dock");
        done(h, p);
    }
}
