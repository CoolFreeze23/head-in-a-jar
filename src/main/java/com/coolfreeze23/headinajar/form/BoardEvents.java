package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.item.QuantumItems;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.network.Network;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Phase 6 behaviour on players and bodies: Collar Cam upgrades, the Lacrimal Laser's cooldown, the Body Recall Beacon. */
public final class BoardEvents {
    public static final int LASER_COST = 1500, LASER_COOLDOWN = 20, RECALL_DRAIN = 10, WHEELS_DRAIN = 10;
    private static final String LASER_READY = "headinajar_laser_ready";

    private BoardEvents() {
    }

    /** Whether each player's jar had charge last tick (not saved: only the moment it runs out is heard). */
    private static final java.util.Map<java.util.UUID, Boolean> CHARGED = new java.util.HashMap<>();

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        JarState state = FormActions.state(player);
        if (state.form().isJarred()) Courier.tick(player);
        else Courier.cancel(player);
        if (state.form() == Form.JAR && state.wheels() && state.has(com.coolfreeze23.headinajar.module.ModuleType.WHEELS)
                && player.getDeltaMovement().horizontalDistanceSqr() > 1e-4) {
            state.setCharge(state.charge() - WHEELS_DRAIN);
            if (player.tickCount % 20 == 0) com.coolfreeze23.headinajar.network.Network.sync(player);
        }
        if (state.form() == Form.JAR && state.wheels() && state.charge() <= 0) {
            // flat: the wheels fold away (they can't turn) and the jar is told how it still gets about
            state.setWheels(false);
            player.serverLevel().playSound(null, player.blockPosition(), com.coolfreeze23.headinajar.fx.HouseSounds.WHEELS_UP.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1, 0.9f);
            com.coolfreeze23.headinajar.network.Network.sync(player);
            com.coolfreeze23.headinajar.feedback.Warnings.warn(player, 0, "jar.wheels_flat", Component.translatable("block.headinajar.charging_dock"),
                    Component.keybind("key.headinajar.drive"));
        }
        // the jar's charge running out: the company signs off politely, to its owner only
        boolean charged = state.charge() > 0;
        if (state.form().isJarred() && !charged && Boolean.TRUE.equals(CHARGED.get(player.getUUID()))) {
            player.playNotifySound(com.coolfreeze23.headinajar.fx.HouseSounds.JINGLE_SHUTDOWN.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1, 1);
        }
        CHARGED.put(player.getUUID(), charged);
        if (state.form() != Form.LINKED || state.collar().isEmpty()) return;
        if (player.tickCount % 100 == 0 && QuantumItems.has(state.collar(), QuantumItems.Upgrade.NIGHT_OPTICS)) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
        }
        if (player.tickCount % 40 == 0 && QuantumItems.has(state.collar(), QuantumItems.Upgrade.THERMAL_OVERLAY)) {
            for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(24), e -> e != player)) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, true, false));
            }
        }
    }

    @SubscribeEvent
    public static void mount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof ServerPlayer p && FormActions.state(p).form() == Form.LINKED
                && event.getEntityBeingMounted() instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) {
            com.coolfreeze23.headinajar.Advance.grant(p, "headless_horseman");
        }
    }

    /**
     * A jar key with no module behind it: bubbles in the jar and a line naming the module and the jar menu key. A module
     * that is fitted but has no charge left gets the charging line instead. Returns false.
     */
    public static boolean noModule(ServerPlayer player, ModuleType module) {
        if (FormActions.state(player).fitted(module)) {
            return Refusals.glug(player, "jar.no_charge", Component.translatable("block.headinajar.charging_dock"));
        }
        return Refusals.glug(player, "jar.no_module", Component.translatable("item.headinajar." + module.id()),
                Component.keybind("key.headinajar.jar_menu"));
    }

    /** Lacrimal Laser: the head weeps a beam, 4 damage at 8 blocks, once a second, 1,500 FE. */
    public static boolean laser(ServerPlayer player) {
        JarState state = FormActions.state(player);
        if (!state.form().isJarred()) return false;
        if (!state.fitted(ModuleType.LACRIMAL_LASER)) return noModule(player, ModuleType.LACRIMAL_LASER);
        if (state.charge() < LASER_COST) return Refusals.glug(player, "jar.laser_low", Component.translatable("block.headinajar.charging_dock"));
        long now = player.level().getGameTime();
        if (player.getPersistentData().getLong(LASER_READY) > now) return Refusals.deny(player, "jar.laser_cooling");
        player.getPersistentData().putLong(LASER_READY, now + LASER_COOLDOWN);
        state.setCharge(state.charge() - LASER_COST);
        Network.sync(player);
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(8));
        // the beam stops at the first block in its way, and only what stands before that block is hurt
        var block = level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) end = block.getLocation();
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
                e -> e != player && e instanceof LivingEntity && e.isAlive() && !insideOwnBody(player, e), eye.distanceToSqr(end));
        if (hit != null) {
            hit.getEntity().hurt(level.damageSources().indirectMagic(player, player), 4);
            end = hit.getLocation();
        }
        // one memo: every client draws the twin weeping beams and the drips itself
        com.coolfreeze23.headinajar.fx.Fx.send(level, com.coolfreeze23.headinajar.fx.Fx.of(com.coolfreeze23.headinajar.fx.Fx.LASER, eye, end,
                player.getId(), hit == null ? -1 : hit.getEntity().getId(), hit == null ? 0 : 1));
        level.playSound(null, eye.x, eye.y, eye.z, com.coolfreeze23.headinajar.fx.FxSounds.LASER.get(), SoundSource.PLAYERS, 1.0f,
                com.coolfreeze23.headinajar.fx.FxSounds.pitch(player.getRandom()));
        return true;
    }

    /** The jar's own idle body, with the jar still standing inside it (just out of it): the beam goes out through it. */
    private static boolean insideOwnBody(ServerPlayer player, net.minecraft.world.entity.Entity e) {
        return e instanceof HeadlessBody body && player.getUUID().equals(body.ownerId()) && body.getBoundingBox().inflate(0.1).contains(player.getEyePosition());
    }

    /** Body Recall Beacon: an idle Headless Body walks toward its head while both are in the same loaded world. */
    @SubscribeEvent
    public static void bodyTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof HeadlessBody body) || body.level().isClientSide || body.isCorpse() || body.ownerId() == null) return;
        Player owner = body.level().getPlayerByUUID(body.ownerId());
        if (!(owner instanceof ServerPlayer sp)) return;
        JarState state = FormActions.state(sp);
        if (!state.form().isJarred() || !state.fitted(ModuleType.BODY_RECALL_BEACON) || state.charge() < RECALL_DRAIN) return;
        Vec3 to = owner.position().subtract(body.position());
        if (to.horizontalDistanceSqr() < 9) return;
        Vec3 step = new Vec3(to.x, 0, to.z).normalize().scale(0.15);
        // the body walks blind: it stops at a drop, lava or fire rather than go in
        if (hazardAhead(body, step)) {
            body.setDeltaMovement(0, body.getDeltaMovement().y, 0);
            return;
        }
        state.setCharge(state.charge() - RECALL_DRAIN);
        Vec3 v = body.getDeltaMovement();
        double up = body.horizontalCollision && body.onGround() ? 0.42 : v.y;
        body.setDeltaMovement(step.x, up, step.z);
        body.setYRot((float) (Math.atan2(to.z, to.x) * 180 / Math.PI) - 90);
        body.setYBodyRot(body.getYRot());
        body.hurtMarked = true;
    }

    /** Deepest drop the recalled body will walk off. */
    private static final int SAFE_DROP = 3;

    /** Whether the next stride (half a block ahead) puts the body in lava or fire, or over a drop deeper than it takes. */
    static boolean hazardAhead(HeadlessBody body, Vec3 step) {
        var level = body.level();
        Vec3 ahead = body.position().add(step.normalize().scale(0.6 + body.getBbWidth() / 2));
        net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(ahead.x, body.getY() + 0.01, ahead.z);
        for (int up = 0; up <= 1; up++) {
            var at = level.getBlockState(feet.above(up));
            if (at.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || at.is(net.minecraft.tags.BlockTags.FIRE)) return true;
        }
        // a wall or a step up is the jump's business, not a hazard
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) return false;
        for (int down = 1; down <= SAFE_DROP + 1; down++) {
            net.minecraft.core.BlockPos p = feet.below(down);
            if (p.getY() < level.getMinBuildHeight()) return true;
            var below = level.getBlockState(p);
            if (below.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || below.is(net.minecraft.tags.BlockTags.FIRE)) return true;
            // something to stand on (or water to land in) within reach: a step down, not a drop
            if (!below.getCollisionShape(level, p).isEmpty() || below.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) return false;
        }
        // nothing within reach below: a drop
        return true;
    }
}
