package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.host.HostEvents;
import com.coolfreeze23.headinajar.item.WhistleItem;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Whistle on creatures (before their own use, as the Lanyard), its attack, and what happens to a detail when its
 * player is hurt, dies, logs in or out or goes through a portal; the shuttle's tick.
 */
public final class DetailEvents {
    private DetailEvents() {
    }

    /** Use with the Whistle on a creature: on or off your detail; with Sneak, the detail stands down. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void use(PlayerInteractEvent.EntityInteract event) {
        if (!event.getItemStack().is(WorkRegistry.SUPERVISORS_WHISTLE.get())) return;
        Player player = event.getEntity();
        // a jar without a Speaker Grille: the jar's own guard answers ("A jar can't whistle.")
        if (!WhistleItem.mayBlow(player)) {
            WhistleItem.grille(player, event);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(player instanceof ServerPlayer sp) || sp.getCooldowns().isOnCooldown(event.getItemStack().getItem())) return;
        sp.getCooldowns().addCooldown(event.getItemStack().getItem(), WhistleItem.BLOW_TICKS);
        if (player.isSecondaryUseActive()) Detail.blow(sp, true);
        else Detail.whistleAt(sp, HostEvents.whole(event.getTarget()));
        sp.swing(event.getHand(), true);
    }

    /** Attack with the Whistle in hand: the fighters go for it (the Whistle itself strikes nobody). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!WhistleItem.holding(player, InteractionHand.MAIN_HAND)) return;
        if (!WhistleItem.mayBlow(player)) {
            WhistleItem.grille(player, event);
            return;
        }
        event.setCanceled(true);
        if (player instanceof ServerPlayer sp) Detail.point(sp, HostEvents.whole(event.getTarget()).getId());
    }

    /** Fighters answer whatever hurts their player. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void hurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Detail.hurt(p, event.getSource());
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Detail.supervisorDied(p);
        } else if (event.getEntity() instanceof Mob mob) {
            WorkerData d = Workforce.data(mob);
            if (d != null && d.state == WorkerData.State.DETAIL) Detail.memberDied(mob, d);
        }
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Detail.loggedOut(p);
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Detail.loggedIn(p);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Detail.changedDimension(p, event.getFrom());
    }

    /** A detail never goes through a portal: it waits on this side. */
    @SubscribeEvent
    public static void travel(EntityTravelToDimensionEvent event) {
        WorkerData d = Workforce.data(event.getEntity());
        if (d != null && d.state == WorkerData.State.DETAIL) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        Shuttle.tick(event.getServer());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        Detail.clear();
        Pets.clear();
    }
}
