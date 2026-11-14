package com.coolfreeze23.headinajar.genetics;

import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Genes in combat and over time. */
public final class GeneEvents {
    private GeneEvents() {
    }

    public static final String REGROWTH_READY = "headinajar_regrowth_ready";
    public static final int REGROWTH_COOLDOWN = 12_000;

    /** Blazing Soul: landing from a fall of 3 or more sends a ring of flame strikes out to 4 blocks. */
    @SubscribeEvent
    public static void fall(net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || event.getDistance() < 3 || !carries(e) || !GeneEffects.has(e, Gene.BLAZING_SOUL)) return;
        burst(e);
        event.setDamageMultiplier(0);
    }

    public static void burst(LivingEntity e) {
        var level = (net.minecraft.server.level.ServerLevel) e.level();
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, e.getX() + Math.cos(a) * 3, e.getY() + 0.1, e.getZ() + Math.sin(a) * 3, 4, 0.2, 0.3, 0.2, 0.02);
        }
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(4), o -> o != e && o != e.getVehicle())) {
            other.hurt(level.damageSources().onFire(), 4 * GeneEffects.scale(e));
            other.igniteForSeconds(4);
        }
        level.playSound(null, e.blockPosition(), net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, e.getSoundSource(), 1.2f, 0.6f);
    }

    private static boolean carries(LivingEntity e) {
        return e.hasData(ModRegistry.GENES) && !e.getData(ModRegistry.GENES).isEmpty();
    }

    /** Carriers tick their genes; a body whose genes just went (washed, moved to the body, injected away) ticks once more to switch them off. */
    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity e && (carries(e) || GeneEffects.applied(e))) GeneEffects.tick(e);
    }

    /** Dying, unloading, logging out or changing dimension: a Glow Cells light never stays behind. */
    @SubscribeEvent
    public static void leave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) GeneEffects.left(event.getEntity(), level);
    }

    /** The client builds a new player on a dimension change, without its genes: send them again (movement genes are client-side). */
    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            GeneEffects.settle(p);
            Network.syncGenes(p);
        }
    }

    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        var source = event.getSource();
        if (carries(victim) && GeneEffects.active(victim)) {
            if (source.is(DamageTypeTags.IS_FIRE) && (GeneEffects.has(victim, Gene.BLAZE_CORE) || GeneEffects.has(victim, Gene.MULE_FIRE))) {
                event.setCanceled(true);
                return;
            }
            if (source.is(DamageTypeTags.IS_FALL)) {
                if (GeneEffects.has(victim, Gene.SLIME_BODY) || GeneEffects.has(victim, Gene.BREEZE_UPDRAFT)) {
                    event.setCanceled(true);
                    return;
                }
                if (GeneEffects.has(victim, Gene.BRITTLE_BONES)) event.setAmount(event.getAmount() * 1.5f);
            }
            if (source.is(DamageTypeTags.IS_PROJECTILE)) event.setAmount(event.getAmount() * (1 - 0.3f * GeneEffects.s(victim, Gene.TURTLE_SHELL)));
            if (GeneEffects.has(victim, Gene.GLASS_JAW)) event.setAmount(event.getAmount() * 1.1f);
            // Regrowth: once every 10 minutes a lethal hit leaves you at 30% instead
            if (GeneEffects.has(victim, Gene.REGROWTH) && event.getAmount() >= victim.getHealth()) {
                long now = victim.level().getGameTime();
                var data = victim.getPersistentData();
                if (data.getLong(REGROWTH_READY) <= now) {
                    data.putLong(REGROWTH_READY, now + REGROWTH_COOLDOWN);
                    event.setAmount(0);
                    victim.setHealth(victim.getMaxHealth() * 0.3f);
                    victim.level().playSound(null, victim.blockPosition(), com.coolfreeze23.headinajar.fx.HouseSounds.GENE_REGROWTH.get(), victim.getSoundSource(), 0.6f, 1.4f);
                    return;
                }
            }
            if (GeneEffects.has(victim, Gene.SLIME_BODY) && source.getDirectEntity() instanceof LivingEntity attacker && source.getDirectEntity() == source.getEntity()) {
                attacker.knockback(1.2, victim.getX() - attacker.getX(), victim.getZ() - attacker.getZ());
            }
        }
        if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && carries(attacker) && GeneEffects.active(attacker)) {
            if (GeneEffects.has(attacker, Gene.VENOM_SAC)) victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), attacker);
            if (GeneEffects.has(attacker, Gene.WITHER_WARD)) victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), attacker);
            if (GeneEffects.has(attacker, Gene.HOGLIN_TUSKS)) {
                victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.6, 0));
                victim.hurtMarked = true;
            }
            if (GeneEffects.has(attacker, Gene.PACK_INSTINCT)) {
                long allies = attacker.level().getEntitiesOfClass(LivingEntity.class, attacker.getBoundingBox().inflate(8),
                        o -> o != attacker && (o instanceof Player || o instanceof TamableAnimal t && t.isTame())).size();
                event.setAmount(event.getAmount() + Math.min(3, allies));
            }
        }
    }

    @SubscribeEvent
    public static void effect(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect() == MobEffects.WITHER && carries(event.getEntity()) && GeneEffects.has(event.getEntity(), Gene.WITHER_WARD)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!event.isCanceled() && !e.level().isClientSide && carries(e) && GeneEffects.has(e, Gene.CREEPER_GLAND)) {
            e.level().explode(e, e.getX(), e.getY(), e.getZ(), 3, Level.ExplosionInteraction.NONE);
        }
    }

    /** Syringes and collection tools draw from creatures; serums go into hosts and Chimera Gene Ports. */
    @SubscribeEvent
    public static void interact(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        var clicked = event.getTarget();
        // a click on one of the Ender Dragon's parts is a click on the dragon
        if (clicked instanceof net.neoforged.neoforge.entity.PartEntity<?> part) clicked = part.getParent();
        if (!(clicked instanceof LivingEntity target)) return;
        var stack = event.getItemStack();
        net.minecraft.world.InteractionResult result = null;
        if (stack.getItem() instanceof com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem syringe) {
            result = event.getEntity() instanceof ServerPlayer p ? syringe.draw(p, stack, target, event.getHand()) : net.minecraft.world.InteractionResult.SUCCESS;
        } else if (stack.getItem() instanceof com.coolfreeze23.headinajar.item.GeneticsItems.SerumItem serum) {
            result = event.getEntity() instanceof ServerPlayer p ? serum.inject(p, stack, target) : net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (result != null && result != net.minecraft.world.InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    /** Genes survive a respawn when the server keeps them (on by default). */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && !com.coolfreeze23.headinajar.Config.KEEP_GENES.get()) return;
        event.getEntity().getData(ModRegistry.GENES).copyFrom(event.getOriginal().getData(ModRegistry.GENES));
        GeneEffects.settle(event.getEntity());
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Network.syncGenes(p);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity e && carries(e) && event.getEntity() instanceof ServerPlayer viewer) Network.syncGenesTo(viewer, e);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Network.syncGenes(p);
    }
}
