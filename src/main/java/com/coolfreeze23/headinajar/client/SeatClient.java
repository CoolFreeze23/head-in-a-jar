package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Sitting in a Separation Chair, the patient faces out into the room, so a use lands on the floor or on nothing: with an
 * empty hand nothing answers, and with something in hand it is placed or used instead. It is the chair they mean: while
 * seated, a use not aimed at someone goes to the chair they sit in by the ordinary use-on-a-block packet, and the chair
 * opens its panel again.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class SeatClient {
    private SeatClient() {
    }

    @SubscribeEvent
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || mc.gameMode == null || mc.player == null) return;
        if (!(mc.player.getVehicle() instanceof SeatEntity seat) || seat.chair() == null) return;
        // someone standing by can still be handed something; anything else the patient reaches for is the chair
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) return;
        var chair = seat.chairPos();
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(chair), Direction.UP, chair, true));
        event.setSwingHand(true);
        event.setCanceled(true);
    }
}
