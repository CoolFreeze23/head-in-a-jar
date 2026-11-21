package com.coolfreeze23.headinajar.staff.work.jobs;

import com.coolfreeze23.headinajar.item.ClipboardItem;
import com.coolfreeze23.headinajar.item.JobCardItem;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * The jobs' events: the Clipboard and a Job Card used on a creature (before its own use, as the Lanyard is, so a
 * villager does not open its trades), staff never trampling farmland, and the jobs' trackers let go with their level.
 */
public final class JobEvents {
    private JobEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void useOnCreature(PlayerInteractEvent.EntityInteract event) {
        ClipboardItem.useOnCreature(event);
        if (!event.isCanceled()) JobCardItem.useOnCreature(event);
    }

    /**
     * A working allay's hand shows its carry: a player's Use would take it (or give it something) the vanilla way, so
     * it says it is on the clock instead (anything the company's own items do on it has already been answered).
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void allayOnTheClock(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof net.minecraft.world.entity.animal.allay.Allay allay) || !Workforce.isWorker(allay)) return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (!event.getLevel().isClientSide()) Workforce.say(allay, "on_the_clock");
    }

    /** Staff walk on farmland as if sneaking. */
    @SubscribeEvent
    public static void trample(BlockEvent.FarmlandTrampleEvent event) {
        if (Workforce.isWorker(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) Farming.unload(level.dimension());
    }
}
