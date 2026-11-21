package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.client.ClientRefusals;
import com.coolfreeze23.headinajar.item.WhistleItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.work.Detail;
import com.coolfreeze23.headinajar.staff.work.WorkerNet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Whistle on the client: an attack with it in hand is a point (vanilla sends nothing for a click in the air), the
 * entity under the crosshair up to 32 blocks sent as {@code WorkerNet.Point}; and a jar's clicks with it, which the
 * jar's guards would otherwise cancel: let through with a Speaker Grille, refused without one.
 */
public final class WhistleClient {
    private WhistleClient() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, WhistleClient::key);
    }

    /** Attack with the Whistle: the point goes to the server instead of a blow. */
    private static void key(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !WhistleItem.holding(player, InteractionHand.MAIN_HAND) || !WhistleItem.mayBlow(player)) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        var connection = mc.getConnection();
        if (connection == null || !connection.hasChannel(WorkerNet.Point.TYPE)) return;
        Entity seen = pick(mc, player);
        PacketDistributor.sendToServer(new WorkerNet.Point(seen == null ? -1 : seen.getId()));
    }

    /** The entity under the crosshair up to {@code Detail.REACH} blocks, short of any block in the way; or null. */
    static Entity pick(Minecraft mc, Player player) {
        if (mc.hitResult instanceof EntityHitResult hit) return hit.getEntity();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1);
        Vec3 end = eye.add(look.scale(Detail.REACH));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double reach = block.getType() == HitResult.Type.MISS ? Detail.REACH : block.getLocation().distanceTo(eye);
        end = eye.add(look.scale(reach));
        Entity vehicle = player.getVehicle();
        AABB area = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, area,
                e -> !e.isSpectator() && e.isPickable() && e != vehicle && e != player, reach * reach);
        return hit == null ? null : hit.getEntity();
    }

    /**
     * A jar's click with the Whistle (the jar's input guard asks first): with a Speaker Grille it goes through, without
     * one it is cancelled and the jar is told it can't whistle. False: not the Whistle in a jar, the guard carries on.
     */
    public static boolean grille(InputEvent.InteractionKeyMappingTriggered event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !player.getData(ModRegistry.JAR_STATE).form().isJarred()) return false;
        if (!(event.isUseItem() || event.isAttack()) || !WhistleItem.holding(player, event.getHand())) return false;
        if (WhistleItem.mayBlow(player)) return true;
        event.setCanceled(true);
        event.setSwingHand(false);
        ClientRefusals.deny(WhistleItem.JAR, Minecraft.getInstance().hitResult instanceof EntityHitResult hit ? hit.getEntity() : null);
        return true;
    }

    /**
     * A pilot holding the Whistle: the mouse buttons are the Whistle's, never the arms'. With a Speaker Grille they blow
     * it; without one the jar is told it can't whistle (one press, one answer).
     */
    public static boolean blowing(Player player) {
        return player != null && WhistleItem.holding(player);
    }
}
