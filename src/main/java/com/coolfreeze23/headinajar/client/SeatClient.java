package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Sitting in a Separation Chair, the patient faces out into the room, so a use with an empty hand finds nothing under
 * the crosshair and the game would send nothing. It is the chair they mean: the use goes to the chair they sit in, by
 * the ordinary use-on-a-block packet, and the chair opens its panel again.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class SeatClient {
    private SeatClient() {
    }

    @SubscribeEvent
    public static void emptyClick(PlayerInteractEvent.RightClickEmpty event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getHand() != InteractionHand.MAIN_HAND || mc.gameMode == null || mc.player == null || event.getEntity() != mc.player) return;
        if (!(mc.player.getVehicle() instanceof SeatEntity seat) || seat.chair() == null) return;
        var chair = seat.chairPos();
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(chair), Direction.UP, chair, true));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }
}
