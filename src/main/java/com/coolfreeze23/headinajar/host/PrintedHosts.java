package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/**
 * Hosts printed to order drop no loot and no experience when they die or rot away: no printing for drops. What they
 * make while alive (a chicken's eggs, a sheep's wool) is theirs to give.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class PrintedHosts {
    private PrintedHosts() {
    }

    public static boolean printed(Entity e) {
        return Hosts.isHost(e) && Hosts.data(e).printed;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void drops(LivingDropsEvent event) {
        if (printed(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void experience(LivingExperienceDropEvent event) {
        if (printed(event.getEntity())) event.setCanceled(true);
    }
}
