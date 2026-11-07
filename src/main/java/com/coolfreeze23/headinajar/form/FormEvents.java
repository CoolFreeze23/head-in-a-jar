package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.block.ChargingDockBlock;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.module.ModuleEffects;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.module.Telekinesis;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Common game rules of being a head in a jar. */
public final class FormEvents {
    public static final EntityDimensions JAR_SIZE = EntityDimensions.fixed(0.62f, 0.72f).withEyeHeight(0.42f);

    private static JarState state(Player player) {
        return player.getData(ModRegistry.JAR_STATE);
    }

    /** A jar with no Manipulator Arm can't touch the world. */
    public static boolean lockedOut(Player player) {
        JarState state = state(player);
        return state.form() == Form.JAR && !state.has(ModuleType.MANIPULATOR_ARM)
                || state.form() == Form.SOCKET;
    }

    @SubscribeEvent
    public static void size(EntityEvent.Size event) {
        if (event.getEntity() instanceof Player player && player.hasData(ModRegistry.JAR_STATE)) {
            Form form = state(player).form();
            if (form == Form.JAR || form == Form.SOCKET) event.setNewSize(JAR_SIZE);
        }
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        JarState state = state(player);
        ModuleEffects.apply(player, state);
        if (!(player instanceof ServerPlayer server)) return;
        if (state.form().isJarred()) serverTick(server, state);
        if (state.takeDirty(player.tickCount % 10 == 0)) Network.sync(server);
    }

    private static void serverTick(ServerPlayer player, JarState state) {
        long time = player.level().getGameTime();
        // a head doesn't get hungry: food is frozen while in a jar
        player.getFoodData().setExhaustion(0);
        int idle = Config.IDLE_DRAIN_INTERVAL.get();
        if (idle > 0 && time % idle == 0) state.spend(1);
        if (state.has(ModuleType.PROPELLER) && player.getAbilities().flying) state.spend(1);
        if (state.has(ModuleType.HEAT_SHIELD) && (player.isOnFire() || player.isInLava())) {
            player.clearFire();
            state.spend(1);
        }
        if (state.has(ModuleType.WATER_JET) && player.isInWater() && time % 2 == 0
                && player.getDeltaMovement().horizontalDistanceSqr() > 0.001) state.spend(1);
        if (ChargingDockBlock.isOn(player)) state.setCharge(state.charge() + 5);
        if (state.has(ModuleType.MAGNET) && time % 2 == 0) pullItems(player);
        Telekinesis.tick(player, state);
        if (state.form() == Form.SOCKET) player.setDeltaMovement(Vec3.ZERO);
    }

    private static void pullItems(ServerPlayer player) {
        Vec3 target = player.position().add(0, 0.3, 0);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(6))) {
            if (item.hasPickUpDelay()) continue;
            Vec3 pull = target.subtract(item.position());
            double d = pull.length();
            if (d < 0.5) continue;
            item.setDeltaMovement(item.getDeltaMovement().scale(0.6).add(pull.scale(0.12 / d)));
        }
    }

    @SubscribeEvent
    public static void jump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && state(player).has(ModuleType.HOP_SPRINGS)) {
            state(player).spend(1);
        }
    }

    // --- what a bare jar can't do -------------------------------------------------------

    private static void lockout(Player player, ICancellableEvent event) {
        if (lockedOut(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        lockout(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        lockout(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        lockout(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interactEntity(PlayerInteractEvent.EntityInteract event) {
        // your own body is the one thing a bare jar can reach for
        if (event.getTarget() instanceof HeadlessBody) return;
        lockout(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interactEntityAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof HeadlessBody) return;
        lockout(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (lockedOut(player)) {
            event.setCanceled(true);
        } else if (!player.level().isClientSide && state(player).form() == Form.JAR) {
            state(player).spend(2);
        }
    }

    @SubscribeEvent
    public static void breakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        JarState state = state(player);
        if (state.form() != Form.JAR) return;
        if (lockedOut(player)) {
            event.setCanceled(true);
            return;
        }
        float speed = event.getNewSpeed() * 0.5f;
        if (state.has(ModuleType.DRILL_BIT)) speed *= 6f;
        event.setNewSpeed(speed);
    }

    @SubscribeEvent
    public static void broke(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && state(player).form() == Form.JAR) state(player).spend(2);
    }

    @SubscribeEvent
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (lockedOut(player)) event.setCanceled(true);
            else if (player instanceof ServerPlayer && state(player).form() == Form.JAR) state(player).spend(2);
        }
    }

    @SubscribeEvent
    public static void pickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        JarState state = state(player);
        if (state.form().isJarred() && !state.has(ModuleType.MANIPULATOR_ARM) && !state.has(ModuleType.MAGNET)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    // --- what a jar is safe from ----------------------------------------------------------

    @SubscribeEvent
    public static void incomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        JarState state = state(player);
        if (!state.form().isJarred()) return;
        var source = event.getSource();
        if (source.is(DamageTypes.DROWN)) {
            event.setCanceled(true);
        } else if (source.is(DamageTypeTags.IS_FIRE) && state.has(ModuleType.HEAT_SHIELD)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) FormActions.shatter(player);
    }

    // --- keeping clients up to date ---------------------------------------------------------

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer viewer) {
            Network.syncTo(viewer, target);
        }
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        resync(event.getEntity());
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        resync(event.getEntity());
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        resync(event.getEntity());
    }

    private static void resync(Player player) {
        if (player instanceof ServerPlayer server) {
            server.refreshDimensions();
            Network.sync(server);
        }
    }

    private FormEvents() {
    }
}
