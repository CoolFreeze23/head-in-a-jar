package com.coolfreeze23.headinajar.entity;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A Missile Pod micro-missile: climbs, then homes on its target; bursts without breaking blocks. */
public class MissileEntity extends Projectile implements ItemSupplier {
    private static final int LIFETIME = 100;
    private int targetId = -1;
    private float damage = 8;

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
    }

    public MissileEntity(Level level, MechaEntity mecha, Player pilot, LivingEntity target, float damage) {
        this(ModRegistry.MISSILE.get(), level);
        setOwner(pilot);
        this.damage = damage;
        this.targetId = target == null ? -1 : target.getId();
        Vec3 start = mecha.position().add(0, mecha.getBbHeight() * 0.9, 0);
        setPos(start.x + (random.nextDouble() - 0.5) * mecha.getBbWidth(), start.y, start.z + (random.nextDouble() - 0.5) * mecha.getBbWidth());
        Vec3 look = pilot.getLookAngle();
        setDeltaMovement(look.x * 0.4, 0.5, look.z * 0.4);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (!level().isClientSide) {
            if (tickCount > LIFETIME) {
                burst(null);
                return;
            }
            Entity target = targetId < 0 ? null : level().getEntity(targetId);
            if (tickCount > 6 && target != null && target.isAlive()) {
                Vec3 want = target.position().add(0, target.getBbHeight() / 2, 0).subtract(position()).normalize().scale(1.2);
                v = v.lerp(want, 0.25);
            } else if (tickCount > 6) {
                v = v.add(0, -0.03, 0);
            }
            setDeltaMovement(v);
            HitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                burst(hit);
                return;
            }
        } else {
            level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        updateRotation();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof MechaEntity m && m.getPassengers().contains(getOwner()));
    }

    private void burst(HitResult hit) {
        if (hit instanceof EntityHitResult e) e.getEntity().hurt(damageSources().explosion(this, getOwner()), damage);
        level().explode(this, getX(), getY(), getZ(), 1.2f, Level.ExplosionInteraction.NONE);
        discard();
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.FIREWORK_ROCKET);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
    }
}
