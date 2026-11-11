package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.ItemAnswers;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * A head plugged into a Neural Socket has its clicks stopped on this side (the socket has its attention); a use or an
 * attack stopped that way is answered out loud, once per press, instead of vanishing.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class SocketClicks {
    private SocketClicks() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isCanceled() || mc.player == null || !ItemAnswers.plugged(mc.player) || event.isPickBlock()) return;
        ClientRefusals.deny(ItemAnswers.SOCKET_HANDS, mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null);
    }
}
