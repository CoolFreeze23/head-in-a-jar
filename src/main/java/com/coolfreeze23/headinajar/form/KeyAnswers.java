package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.feedback.Refusals;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/**
 * The mod's keys pressed where they have nothing to do: the client stops them (it knows the form) and the server says
 * why, in the refusal's voice. Swap with your head still on; Drive on your own two feet. Sneak on a flying host sinks it
 * and lets the jar off on the ground ({@link com.coolfreeze23.headinajar.host.FlierSeat}).
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class KeyAnswers {
    public static final String SWAP_BODY = "swap.body", DRIVE_BODY = "drive.body";

    static {
        Refusals.allowFromClient(SWAP_BODY, Refusals.Cue.PLAIN);
        Refusals.allowFromClient(DRIVE_BODY, Refusals.Cue.PLAIN);
    }

    private KeyAnswers() {
    }

    @SubscribeEvent
    public static void dismount(EntityMountEvent event) {
        com.coolfreeze23.headinajar.host.FlierSeat.dismount(event);
    }
}
