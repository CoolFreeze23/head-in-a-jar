package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * A turret's shots keep its rule (never its owner's team, players unless the server allows it, pets, villagers or golems)
 * wherever they land: an arrow, a fireball, a splash, a snowball's chill and an evoker's fangs pass over whoever the
 * turret would never aim at. The shots carry their turret's position ({@link TurretPedestalBlockEntity#SHOT_TAG}).
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class TurretShots {
    private TurretShots() {
    }

    /** Whether this turret's shot may touch {@code e}: a turret that is gone is given the benefit of the doubt. */
    private static boolean spares(TurretPedestalBlockEntity turret, LivingEntity e) {
        return turret != null && !turret.mayTarget(e);
    }

    @SubscribeEvent
    public static void hurt(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        Entity direct = event.getSource().getDirectEntity();
        if (direct != null) {
            if (spares(TurretPedestalBlockEntity.shooter(victim.level(), direct), victim)) event.setCanceled(true);
            return;
        }
        // fangs with no owner bite with plain magic: whichever of this turret's fangs stands on the victim
        if (!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.MAGIC)) return;
        for (EvokerFangs fangs : victim.level().getEntitiesOfClass(EvokerFangs.class, victim.getBoundingBox().inflate(0.3))) {
            if (spares(TurretPedestalBlockEntity.shooter(victim.level(), fangs), victim)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /** A splash from a witch's head leaves those the turret spares untouched. */
    @SubscribeEvent
    public static void splash(MobEffectEvent.Applicable event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || event.getEffectSource() == null) return;
        if (spares(TurretPedestalBlockEntity.shooter(e.level(), event.getEffectSource()), e)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /** A snow golem head's snowball slows what it hits, and only that. */
    @SubscribeEvent
    public static void snowball(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Snowball ball) || ball.level().isClientSide
                || !(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)) return;
        TurretPedestalBlockEntity turret = TurretPedestalBlockEntity.shooter(ball.level(), ball);
        if (turret == null || !turret.mayTarget(target)) return;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
    }
}
