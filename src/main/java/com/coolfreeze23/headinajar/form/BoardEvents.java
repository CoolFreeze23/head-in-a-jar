package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.item.QuantumItems;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.network.Network;
import net.minecraft.core.particles.ParticleTypes;
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

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        JarState state = FormActions.state(player);
        if (state.form().isJarred()) Courier.tick(player);
        if (state.form() == Form.JAR && state.wheels() && state.has(com.coolfreeze23.headinajar.module.ModuleType.WHEELS)
                && player.getDeltaMovement().horizontalDistanceSqr() > 1e-4) {
            state.setCharge(state.charge() - WHEELS_DRAIN);
            if (player.tickCount % 20 == 0) com.coolfreeze23.headinajar.network.Network.sync(player);
        }
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

    /** Lacrimal Laser: the head weeps a beam, 4 damage at 8 blocks, once a second, 1,500 FE. */
    public static boolean laser(ServerPlayer player) {
        JarState state = FormActions.state(player);
        if (!state.form().isJarred() || !state.fitted(ModuleType.LACRIMAL_LASER) || state.charge() < LASER_COST) return false;
        long now = player.level().getGameTime();
        if (player.getPersistentData().getLong(LASER_READY) > now) return false;
        player.getPersistentData().putLong(LASER_READY, now + LASER_COOLDOWN);
        state.setCharge(state.charge() - LASER_COST);
        Network.sync(player);
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(8));
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
                e -> e != player && e instanceof LivingEntity && e.isAlive(), 64);
        if (hit != null) {
            hit.getEntity().hurt(level.damageSources().indirectMagic(player, player), 4);
            end = hit.getLocation();
        }
        for (int i = 1; i <= 8; i++) {
            Vec3 p = eye.lerp(end, i / 8.0);
            level.sendParticles(ParticleTypes.FALLING_WATER, p.x, p.y, p.z, 2, 0.02, 0.02, 0.02, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 0.5f, 2f);
        return true;
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
        state.setCharge(state.charge() - RECALL_DRAIN);
        Vec3 step = new Vec3(to.x, 0, to.z).normalize().scale(0.15);
        Vec3 v = body.getDeltaMovement();
        double up = body.horizontalCollision && body.onGround() ? 0.42 : v.y;
        body.setDeltaMovement(step.x, up, step.z);
        body.setYRot((float) (Math.atan2(to.z, to.x) * 180 / Math.PI) - 90);
        body.setYBodyRot(body.getYRot());
        body.hurtMarked = true;
    }
}
