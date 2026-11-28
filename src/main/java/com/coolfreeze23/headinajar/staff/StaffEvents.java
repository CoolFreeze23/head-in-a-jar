package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.CondolenceCardItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The register's events: the staff table loading, hosts from before 0.6.0 registered as they join a level, a creature
 * the saw takes whole leaving nothing behind, and staff leaving the company (the Exit Interview and the Condolence
 * Card), and the file card opened from a placed head.
 */
public final class StaffEvents {
    /** Exit Interviews sent or held, newest last ("employer uuid|reason|number"), for tests. */
    private static final List<String> EXITS = new ArrayList<>();

    private StaffEvents() {
    }

    public static List<String> exits() {
        return EXITS;
    }

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent event) {
        event.addListener(new StaffTable.Loader());
    }

    /** A host from before 0.6.0 is put on file the first time it is added to a level (a chunk loading counts). */
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || mob instanceof MechaEntity) return;
        // only hosts read from a save: a host or a shell made now is put on file by whatever made it
        if (!event.loadedFromDisk() || !Hosts.isHost(mob) || Staff.of(mob) != null) return;
        Staff.register(mob, null, "retro", false);
    }

    // --- the whole creature in the jar -------------------------------------------------------------------------------

    private static boolean swallowed(Entity e) {
        return SawTable.swallow != null && SawTable.swallow.equals(e.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void drops(LivingDropsEvent event) {
        if (swallowed(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void experience(LivingExperienceDropEvent event) {
        if (swallowed(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void split(MobSplitEvent event) {
        if (swallowed(event.getParent())) event.setCanceled(true);
    }

    // --- leaving -----------------------------------------------------------------------------------------------------

    /** Why a member of staff left: a reason key, or the killer's own name. */
    public static String reason(LivingEntity dead, DamageSource source, StaffFile f) {
        if (Hosts.isHost(dead) && Hosts.data(dead).freshness <= 0 && source.is(DamageTypes.GENERIC_KILL)) return "staff.headinajar.reason.wastage";
        // 0.8.0: a creaking bound to a heart takes no harm; it dies only when its heart is broken (by a hand or a blast),
        // so whoever broke it, its heart was broken
        if (dead instanceof net.minecraft.world.entity.Mob mob && !source.is(DamageTypes.GENERIC_KILL)
                && com.coolfreeze23.headinajar.staff.work.Workforce.heartBound(mob)) return "staff.headinajar.reason.heart_broken";
        Entity killer = source.getEntity();
        if (killer != null && f.employer != null && f.employer.equals(killer.getUUID())) return "staff.headinajar.reason.management";
        if (source.is(DamageTypes.FALL)) return "staff.headinajar.reason.fall";
        if (source.is(DamageTypes.LAVA)) return "staff.headinajar.reason.lava";
        // burning: in a fire, a campfire or on a magma block, or still alight (the sun on the undead, a fire left behind)
        if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.CAMPFIRE)
                || source.is(DamageTypes.HOT_FLOOR)) return "staff.headinajar.reason.burned";
        if (source.is(DamageTypes.DROWN)) return "staff.headinajar.reason.drowned";
        // 0.8.0: a nautilus left out of the water (by id: any other creature drying out is still a workplace incident)
        if (source.is(DamageTypes.DRY_OUT) && com.coolfreeze23.headinajar.supplier.SuppliedMachines.NAUTILUS.equals(
                com.coolfreeze23.headinajar.supplier.SuppliedMachines.id(dead))) return "staff.headinajar.reason.dried_out";
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return "staff.headinajar.reason.explosion";
        if (killer instanceof LivingEntity living && killer != dead) return living.getName().getString();
        return "staff.headinajar.reason.other";
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void died(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (event.isCanceled() || !(dead.level() instanceof ServerLevel level) || !(dead instanceof Mob)) return;
        StaffFile f = Staff.of(dead);
        // a creature cut into a jar goes on as a head: it has not left
        if (f == null || f.left != null || swallowed(dead) || f.employer == null) return;
        if (!Hosts.isHost(dead) && !com.coolfreeze23.headinajar.staff.work.Workforce.isWorker(dead)) return;
        close(level, dead, f, reason(dead, event.getSource(), f), true);
    }

    /**
     * A member of staff has left ({@code why}: a reason key or a killer's name): the file is closed and the Exit
     * Interview goes out, with HR's Condolence Card for a death ({@code card}). Also for staff who leave the world
     * without a death the game reports (0.8.0, {@code supplier.SuppliedMachines#left}).
     */
    public static void close(ServerLevel level, LivingEntity leaver, StaffFile f, String why, boolean card) {
        long day = Staff.day(level);
        f.left = new StaffFile.Left(day, why);
        f.note("left", day, why);
        leaver.setData(com.coolfreeze23.headinajar.registry.ModRegistry.STAFF, f);
        exitInterview(level, leaver, f, why, card);
    }

    /**
     * The Exit Interview for a member of staff who has left ({@code why}: a reason key or a killer's name), to its
     * employer now or when they next log in; and, for a death, HR's Condolence Card where it happened.
     */
    public static void exitInterview(ServerLevel level, LivingEntity leaver, StaffFile f, String why, boolean card) {
        long day = Staff.day(level);
        String dept = Hosts.isHost(leaver) && Hosts.data(leaver).shell ? Staff.TEMPS : Staff.department(leaver.getType());
        Component line = Component.translatable("staff.headinajar.exit", Staff.name(f), Staff.species(f), Staff.departmentName(dept),
                Math.max(0, day - f.joined), Component.translatable(StaffTable.noise(leaver.getType())), Staff.reason(why));
        Staff.refresh(f, level.getServer());
        ServerPlayer employer = level.getServer().getPlayerList().getPlayer(f.employer);
        if (employer != null) employer.sendSystemMessage(line);
        else StaffRegister.get(level.getServer()).hold(f.employer, Component.Serializer.toJson(line, level.registryAccess()));
        EXITS.add(f.employer + "|" + why + "|" + f.number);
        // HR's card, where it happened (lost with everything else in lava or the void)
        if (card) leaver.spawnAtLocation(CondolenceCardItem.of(f, day));
    }

    /** Exit Interviews that waited for an offline employer. */
    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (String json : StaffRegister.get(player.server).release(player.getUUID())) {
            Component line = Component.Serializer.fromJson(json, player.registryAccess());
            if (line != null) player.sendSystemMessage(line);
        }
    }

    // --- the file card -----------------------------------------------------------------------------------------------

    /**
     * Sneak and use a placed head with an empty main hand: its file card opens. Handled here, before the block, because
     * the game skips the block altogether when a sneaking player holds anything in the off hand.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void card(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().getMainHandItem().isEmpty() || !event.getEntity().isSecondaryUseActive()) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof com.coolfreeze23.headinajar.block.JarredHeadBlockEntity head)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) StaffCards.open(player, head);
    }
}
