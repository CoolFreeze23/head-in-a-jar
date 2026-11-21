package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

/** The fifth batch's client setup: its renderers, the placard notices' sprite source and the picket signs' (0.7.2). */
public final class MachineClient5 {
    private MachineClient5() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> MachineRenderers5.register(event));
        modBus.addListener((RegisterSpriteSourceTypesEvent event) -> event.register(HeadInAJar.id("placard_notices"), PlacardNotices.TYPE));
        modBus.addListener((RegisterSpriteSourceTypesEvent event) -> event.register(HeadInAJar.id("picket_signs"), PicketSigns.TYPE));
    }
}
