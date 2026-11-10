package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.block.ChargingDockBlock;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.feedback.Nudge;
import com.coolfreeze23.headinajar.feedback.Refusals;
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
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraft.network.chat.Component;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;

/** Common game rules of being a head in a jar. */
public final class FormEvents {
    public static final EntityDimensions JAR_SIZE = EntityDimensions.fixed(0.86f, 0.95f).withEyeHeight(0.5f);
    /** A Compact Jar: head-sized. */
    public static final EntityDimensions COMPACT_SIZE = EntityDimensions.fixed(0.7f, 0.92f).withEyeHeight(0.5f);
    /** A headless body: fits under 1.5-block gaps; the Collar Cam sits on the neck. */
    public static final EntityDimensions LINKED_SIZE = EntityDimensions.fixed(0.6f, 1.45f).withEyeHeight(1.32f);

    private static JarState state(Player player) {
        return player.getData(ModRegistry.JAR_STATE);
    }

    static {
        // keys the client answers itself, where the form has nothing behind them
        Refusals.allowFromClient("swap.riding", Refusals.Cue.PLAIN);
        Refusals.allowFromClient("jar.menu_linked", Refusals.Cue.PLAIN);
        Refusals.allowFromClient("drive.linked", Refusals.Cue.PLAIN);
        Refusals.allowFromClient("jar.refill_feed", Refusals.Cue.NUDGE);
    }

    /** A jar with no Manipulator Arm can't touch the world. */
    public static boolean lockedOut(Player player) {
        // a mecha's Manipulator Hand acts for its pilot
        if (com.coolfreeze23.headinajar.mecha.MechaArms.acting(player)) return false;
        JarState state = state(player);
        return state.form() == Form.JAR && !state.has(ModuleType.MANIPULATOR_ARM)
                || state.form() == Form.SOCKET || state.form() == Form.HOSTED || state.form() == Form.PILOT;
    }

    @SubscribeEvent
    public static void size(EntityEvent.Size event) {
        if (event.getEntity() instanceof Player player && player.hasData(ModRegistry.JAR_STATE)) {
            Form form = state(player).form();
            // riding a host the jar keeps its own size: a full player size would seat it 0.6 below the neck, sunk into the body
            if (form == Form.JAR || form == Form.SOCKET || form == Form.HOSTED) event.setNewSize(state(player).compact() ? COMPACT_SIZE : JAR_SIZE);
            else if (form == Form.LINKED && event.getPose() != net.minecraft.world.entity.Pose.SLEEPING
                    && !com.coolfreeze23.headinajar.item.QuantumItems.has(state(player).collar(), com.coolfreeze23.headinajar.item.QuantumItems.Upgrade.PERISCOPE_STALK)) {
                event.setNewSize(LINKED_SIZE);
            }
        }
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        JarState state = state(player);
        ModuleEffects.apply(player, state);
        state.tickRoll();
        if (!(player instanceof ServerPlayer server)) return;
        if (state.form().isJarred()) serverTick(server, state);
        if (state.form().isDecapitated()) {
            NeuroLink.tick(server, state);
            LifeSupport.tick(server, state);
        }
        if (state.form() == Form.LINKED) linkedTick(server, state);
        // a hosted jar whose host is gone (died, collapsed, dismounted) is just a jar again
        if ((state.form() == Form.HOSTED || state.form() == Form.PILOT) && !(player.getVehicle() != null && player.getVehicle().isAlive() && com.coolfreeze23.headinajar.host.Hosts.isHost(player.getVehicle()))) {
            FormActions.leaveHost(server);
        }
        if (state.takeDirty(player.tickCount % 10 == 0)) Network.sync(server);
    }

    private static void serverTick(ServerPlayer player, JarState state) {
        long time = player.level().getGameTime();
        // a head doesn't get hungry: food is frozen while in a jar
        player.getFoodData().setExhaustion(0);
        int idle = Config.IDLE_DRAIN.get();
        if (idle > 0) state.spend(idle);
        if (state.has(ModuleType.PROPELLER) && player.getAbilities().flying) state.spend(100);
        if (state.has(ModuleType.HEAT_SHIELD) && (player.isOnFire() || player.isInLava())) {
            player.clearFire();
            state.spend(100);
        }
        if (state.has(ModuleType.WATER_JET) && player.isInWater() && time % 2 == 0
                && player.getDeltaMovement().horizontalDistanceSqr() > 0.001) state.spend(100);
        ChargingDockBlock.charge(player, state);
        if (state.has(ModuleType.MAGNET) && time % 2 == 0) pullItems(player);
        Telekinesis.tick(player, state);
        if (state.form() == Form.SOCKET) player.setDeltaMovement(Vec3.ZERO);
    }

    /** A headless body can't wear a helmet: it goes into the inventory. */
    private static void linkedTick(ServerPlayer player, JarState state) {
        FormActions.noteGround(player);
        var helmet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (!helmet.isEmpty()) {
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
            FormActions.give(player, helmet);
        }
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
            state(player).spend(100);
        }
    }

    // --- what a bare jar can't do -------------------------------------------------------

    /** Cancels what a locked-out jar tried; a bare jar is told why (the Nudge), with the line for what it reached for. */
    private static void lockout(Player player, ICancellableEvent event, java.util.function.Supplier<String> reason) {
        if (!lockedOut(player)) return;
        event.setCanceled(true);
        if (player instanceof ServerPlayer server && state(player).form() == Form.JAR) Refusals.nudge(server, reason.get());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // the mod's machines are built for heads: a jar can work them without an arm
        if (event.getLevel().getBlockState(event.getPos()).is(HEAD_OPERABLE)) return;
        lockout(event.getEntity(), event, () -> Nudge.forBlock(event.getLevel(), event.getPos(), event.getHitVec().getLocation(), false));
    }

    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> HEAD_OPERABLE =
            net.minecraft.tags.BlockTags.create(com.coolfreeze23.headinajar.HeadInAJar.id("head_operable"));

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (refill(event)) return;
        lockout(event.getEntity(), event, () -> Nudge.USE);
    }

    /**
     * A Saline Bottle or Glass Patch used in the air by a head: a jar with an arm (or a body carrying its head) pours it
     * in; a bare jar is pointed to the feed slot, and a body whose head is elsewhere to its head.
     */
    private static boolean refill(PlayerInteractEvent.RightClickItem event) {
        var stack = event.getItemStack();
        boolean saline = stack.is(ModRegistry.SALINE_BOTTLE.get()), patch = stack.is(ModRegistry.GLASS_PATCH.get());
        Player player = event.getEntity();
        JarState state = state(player);
        if (!saline && !patch || !state.form().isDecapitated()) return false;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(player.level().isClientSide));
        if (!(player instanceof ServerPlayer server)) return true;
        if (state.form() == Form.LINKED && !state.carryingHead()) {
            Refusals.deny(server, "jar.refill_linked", Component.translatable("block.headinajar.life_support_cradle"));
        } else if (lockedOut(player)) {
            Refusals.nudge(server, "jar.refill_feed");
        } else if (LifeSupport.refill(server, state, stack.getItem())) {
            stack.consume(1, player);
        } else {
            Refusals.deny(server, saline ? "head.full" : "head.intact");
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            if (lockedOut(event.getEntity())) event.setCanceled(true);
            return;
        }
        lockout(event.getEntity(), event, () -> Nudge.forBlock(event.getLevel(), event.getPos(), net.minecraft.world.phys.Vec3.atCenterOf(event.getPos()), true));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interactEntity(PlayerInteractEvent.EntityInteract event) {
        // your own body is the one thing a bare jar can reach for
        if (event.getTarget() instanceof HeadlessBody) return;
        // sitting needs no hands: a jar rides along in a boat or a minecart
        if (jarCanBoard(event.getEntity(), event.getTarget())) return;
        // a creature that never had a head (the Headless Mule) takes a jar without the saw
        if (!event.getLevel().isClientSide && event.getTarget() instanceof net.minecraft.world.entity.Mob mob && !com.coolfreeze23.headinajar.host.Hosts.isHost(mob)
                && state(event.getEntity()).form() == Form.JAR) {
            var def = com.coolfreeze23.headinajar.host.Hosts.definition(mob);
            if (def != null && def.has("headless")) {
                var data = com.coolfreeze23.headinajar.host.Hosts.data(mob);
                data.host = true;
                data.freshness = 100;
                com.coolfreeze23.headinajar.network.Network.syncHost(mob);
            }
        }
        if (com.coolfreeze23.headinajar.host.Hosts.isHost(event.getTarget()) && state(event.getEntity()).form() != Form.HOSTED) {
            com.coolfreeze23.headinajar.host.HostInteractions.interact(event);
            return;
        }
        lockout(event.getEntity(), event, () -> Nudge.forEntity(event.getTarget(), false));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interactEntityAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof HeadlessBody || jarCanBoard(event.getEntity(), event.getTarget())) return;
        // a host is handled by the plain interaction that follows; consuming this one here would stop the client sending it
        if (com.coolfreeze23.headinajar.host.Hosts.isHost(event.getTarget()) && state(event.getEntity()).form() != Form.HOSTED) return;
        lockout(event.getEntity(), event, () -> Nudge.forEntity(event.getTarget(), false));
    }

    /** A plain jar may climb into a vanilla boat or a rideable minecart (not a chest boat's chest). */
    public static boolean jarCanBoard(Player player, net.minecraft.world.entity.Entity target) {
        if (state(player).form() != Form.JAR) return false;
        if (target instanceof net.minecraft.world.entity.vehicle.Minecart) return true;
        return target instanceof net.minecraft.world.entity.vehicle.Boat && !(target instanceof net.minecraft.world.entity.vehicle.ChestBoat && player.isSecondaryUseActive());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (lockedOut(player)) {
            lockout(player, event, () -> Nudge.forEntity(event.getTarget(), true));
        } else if (!player.level().isClientSide && state(player).form() == Form.JAR) {
            state(player).spend(200);
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
        if (event.getPlayer() instanceof ServerPlayer player && state(player).form() == Form.JAR) state(player).spend(200);
    }

    @SubscribeEvent
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (lockedOut(player)) event.setCanceled(true);
            else if (player instanceof ServerPlayer && state(player).form() == Form.JAR) state(player).spend(200);
        }
    }

    @SubscribeEvent
    public static void pickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        JarState state = state(player);
        if (state.form().isJarred() && !state.has(ModuleType.MANIPULATOR_ARM) && !state.has(ModuleType.MAGNET)) {
            event.setCanPickup(TriState.FALSE);
            // told once per item it rolls onto, not every tick it sits there
            if (player instanceof ServerPlayer server && state.form() == Form.JAR
                    && !Integer.valueOf(event.getItemEntity().getId()).equals(TOUCHED.put(player.getUUID(), event.getItemEntity().getId()))) {
                Refusals.nudge(server, Nudge.PICKUP);
            }
        }
    }

    /** The last item each bare jar was told it can't pick up. */
    private static final java.util.Map<java.util.UUID, Integer> TOUCHED = new java.util.HashMap<>();

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
        } else if (state.form().isJarred() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            LifeSupport.absorb(player, state, event);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        JarState state = state(player);
        // the void takes a linked body, not its head (unless the head went down with it, carried)
        boolean fellOut = event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD);
        boolean bodyOnly = fellOut && state.form() == Form.LINKED && !state.carryingHead();
        boolean real = FormActions.REAL_DEATH.remove(player.getUUID()) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !bodyOnly;
        if (player.getVehicle() instanceof SeatEntity seat && seat.chair() != null) seat.chair().abort(player, "abort");
        if (!real && state.form() == Form.LINKED && FormActions.bodyDied(player, fellOut)) {
            // the body died, not the player: they wake up in their head
            event.setCanceled(true);
            return;
        }
        FormActions.shatter(player);
    }

    @SubscribeEvent
    public static void chat(net.neoforged.neoforge.event.ServerChatEvent event) {
        JarState state = state(event.getPlayer());
        if (state.form().isJarred() && state.has(ModuleType.SPEAKER_GRILLE)) state.say(event.getRawText());
    }

    @SubscribeEvent
    public static void travel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        JarState state = state(player);
        // an Entangled Pair carries the link between dimensions, so the body may go where the link goes
        if (state.form() == Form.LINKED && !state.carryingHead() && !com.coolfreeze23.headinajar.item.QuantumItems.linked(state)) {
            if ("break".equals(Config.get(Config.PORTAL_RULE, "block"))) return;
            event.setCanceled(true);
            // a portal tries again every tick it is stood in: say it once every few seconds
            Integer told = PORTAL_TOLD.get(player.getUUID());
            if (told == null || player.tickCount < told || player.tickCount - told >= 60) {
                PORTAL_TOLD.put(player.getUUID(), player.tickCount);
                Refusals.deny(player, "portal.head_behind", Component.translatable("item.headinajar.head_satchel"));
            }
        }
    }

    private static final java.util.Map<java.util.UUID, Integer> PORTAL_TOLD = new java.util.HashMap<>();

    /**
     * A mecha pilot doesn't leave by sneaking (sneaking is for the Anchor Clamps; ejecting is hold sneak + J). Anything
     * else that takes a pilot out (a teleport, spectator mode, a dimension change) is let through, and the pilot is a jar
     * again on the next tick.
     */
    @SubscribeEvent
    public static void mechaDismount(EntityMountEvent event) {
        if (event.isDismounting() && !event.getLevel().isClientSide() && event.getEntityBeingMounted() instanceof com.coolfreeze23.headinajar.entity.MechaEntity mecha
                && mecha.isAlive() && !mecha.ejecting && event.getEntityMounting() == mecha.getFirstPassenger()
                && event.getEntityMounting() instanceof Player p && state(p).form() == Form.PILOT && p.isAlive()
                && p.isShiftKeyDown() && !p.isSpectator()) {
            event.setCanceled(true);
        }
    }

    /** Hits on a pilot land on the mecha around them. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void pilotHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || state(player).form() != Form.PILOT) return;
        if (!(player.getVehicle() instanceof com.coolfreeze23.headinajar.entity.MechaEntity mecha) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (event.getSource().getEntity() == mecha) return;
        event.setCanceled(true);
        if (!player.level().isClientSide) mecha.hurt(event.getSource(), event.getAmount());
    }

    @SubscribeEvent
    public static void mount(EntityMountEvent event) {
        // the restraints hold once the cut has started
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof SeatEntity seat && seat.chair() != null
                && seat.chair().running() && seat.chair().opTick() >= SeparationChairBlockEntity.CUT_TICK && seat.isAlive()
                && event.getEntityMounting().isAlive()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TOUCHED.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player && player.getVehicle() instanceof SeatEntity seat && seat.chair() != null) {
            seat.chair().finishNow(player);
        }
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
        Telekinesis.landStranded(event.getEntity());
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
