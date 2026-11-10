package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.module.ModuleType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;


/** The things a jar can make its host do: the primary (R) at 50% sync and the secondary (G) at 75%. */
public final class HostAbilities {
    private static final String NO_TARGET = "host.no_target";

    private HostAbilities() {
    }

    /** Fires the host's primary or secondary ability if it is unlocked and off cooldown; says why not otherwise. */
    public static boolean use(ServerPlayer pilot, Mob host, boolean secondary) {
        HostDefinition def = Hosts.definition(host);
        HostData data = Hosts.data(host);
        var ability = def == null ? java.util.Optional.<HostDefinition.Ability>empty() : secondary ? def.secondary() : def.primary();
        if (ability.isEmpty()) return Refusals.deny(pilot, Refuser.at(host), "host.no_ability", host.getDisplayName());
        int need = secondary ? 75 : 50;
        if (data.effectiveSync() < need) {
            pilot.displayClientMessage(Component.translatable("message.headinajar.sync_locked", need), true);
            return false;
        }
        // a Utility Shell runs on the jar's charge: a flat jar can't drive it, nor make it do tricks
        if (data.shell && !(host instanceof com.coolfreeze23.headinajar.entity.MechaEntity) && FormActions.state(pilot).charge() <= 0) {
            return Refusals.deny(pilot, Refuser.at(host), "host.shell_flat", Component.translatable("block.headinajar.charging_dock"));
        }
        if ((secondary ? data.secondaryCooldown : data.primaryCooldown) > 0) return Refusals.deny(pilot, Refuser.at(host), "host.cooling");
        String refused = fire(pilot, host, ability.get());
        if (refused != null) return Refusals.deny(pilot, Refuser.at(host), refused);
        if (secondary) data.secondaryCooldown = ability.get().cooldown();
        else data.primaryCooldown = ability.get().cooldown();
        return true;
    }

    private static Entity target(ServerPlayer pilot, double range) {
        Vec3 eye = pilot.getEyePosition();
        Vec3 end = eye.add(pilot.getLookAngle().scale(range));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(pilot, eye, end, new AABB(eye, end).inflate(1),
                e -> e != pilot && e != pilot.getVehicle() && e instanceof LivingEntity && e.isAlive(), range * range);
        return hit == null ? null : hit.getEntity();
    }

    private static BlockHitResult block(ServerPlayer pilot, double range) {
        Vec3 eye = pilot.getEyePosition();
        return pilot.level().clip(new ClipContext(eye, eye.add(pilot.getLookAngle().scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, pilot));
    }

    /** Fires an ability. Returns null when it fired, else the refusal reason (nothing to aim at, no room for a web). */
    static String fire(ServerPlayer pilot, Mob host, HostDefinition.Ability a) {
        ServerLevel level = pilot.serverLevel();
        Vec3 look = pilot.getLookAngle();
        double range = a.param("range", 16);
        switch (a.type()) {
            case "teleport" -> {
                BlockHitResult hit = block(pilot, range);
                if (hit.getType() != HitResult.Type.BLOCK) return NO_TARGET;
                BlockPos to = hit.getBlockPos().relative(hit.getDirection());
                host.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
                level.playSound(null, host.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1, 1);
                return null;
            }
            case "explode" -> {
                JarState state = FormActions.state(pilot);
                pilot.stopRiding();
                level.explode(host, host.getX(), host.getY(), host.getZ(), (float) a.param("power", 3), Level.ExplosionInteraction.NONE);
                host.kill();
                if (!state.has(ModuleType.SHOCK_LID)) state.setIntegrity(state.integrity() / 2);
                return null;
            }
            case "fireball" -> {
                int count = (int) a.param("count", 3);
                for (int i = 0; i < count; i++) {
                    Vec3 dir = look.add(level.random.nextGaussian() * 0.05, level.random.nextGaussian() * 0.05, level.random.nextGaussian() * 0.05);
                    var ball = a.param("large", 0) > 0 ? new LargeFireball(level, host, dir, 1) : new SmallFireball(level, host, dir);
                    ball.setPos(pilot.getEyePosition().add(look));
                    level.addFreshEntity(ball);
                }
                level.playSound(null, host.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1, 1);
                return null;
            }
            case "arrows" -> {
                int count = (int) a.param("count", 3);
                for (int i = 0; i < count; i++) {
                    var arrow = new net.minecraft.world.entity.projectile.Arrow(level, host, new ItemStack(Items.ARROW), null);
                    arrow.setPos(pilot.getEyePosition().add(look));
                    arrow.shoot(look.x, look.y, look.z, 2.4f, 4f);
                    arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
                    level.addFreshEntity(arrow);
                }
                level.playSound(null, host.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1, 1);
                return null;
            }
            case "web" -> {
                Entity t = target(pilot, range);
                BlockHitResult hit = t == null ? block(pilot, range) : null;
                if (t == null && hit.getType() != HitResult.Type.BLOCK) return NO_TARGET;
                BlockPos at = t != null ? t.blockPosition() : hit.getBlockPos().relative(hit.getDirection());
                // a web only fills air: never water, lava or a plant, which would be gone when it melts
                if (!level.getBlockState(at).isAir() || !level.mayInteract(pilot, at)) return "host.web_blocked";
                level.setBlockAndUpdate(at, Blocks.COBWEB.defaultBlockState());
                WebShots.get(level).add(at, level.getGameTime() + 200);
                return null;
            }
            case "charge", "pounce" -> {
                Vec3 push = look.multiply(1, 0, 1).normalize().scale(a.param("speed", 1.4));
                host.setDeltaMovement(push.x, a.type().equals("pounce") ? 0.5 : host.getDeltaMovement().y, push.z);
                host.hurtMarked = true;
                float damage = (float) a.param("damage", 6);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().expandTowards(push.scale(2)).inflate(0.5),
                        e -> e != host && e != pilot)) {
                    e.hurt(HostEvents.byPilot(host, pilot, net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK), damage);
                    e.knockback(a.param("knockback", 1.5), -push.x, -push.z);
                }
                level.playSound(null, host.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1, 1.2f);
                return null;
            }
            case "toss" -> {
                Entity t = target(pilot, a.param("range", 4));
                if (t == null) return NO_TARGET;
                t.setDeltaMovement(t.getDeltaMovement().add(0, a.param("power", 1.2), 0));
                t.hurtMarked = true;
                level.playSound(null, host.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1, 1);
                return null;
            }
            case "roar" -> {
                double r = a.param("radius", 6);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(r), e -> e != host && e != pilot)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) a.param("duration", 40), 4));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) a.param("duration", 40), 0));
                    e.knockback(0.8, host.getX() - e.getX(), host.getZ() - e.getZ());
                }
                level.playSound(null, host.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 1);
                return null;
            }
            case "lay_egg" -> {
                level.addFreshEntity(new ItemEntity(level, host.getX(), host.getY(), host.getZ(), new ItemStack(Items.EGG)));
                level.playSound(null, host.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1, 1);
                return null;
            }
            case "ink" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(6), e -> e != host && e != pilot)) {
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80));
                }
                level.sendParticles(ParticleTypes.SQUID_INK, host.getX(), host.getY() + 0.5, host.getZ(), 40, 1, 0.5, 1, 0.05);
                level.playSound(null, host.blockPosition(), SoundEvents.SQUID_SQUIRT, SoundSource.NEUTRAL, 1, 1);
                return null;
            }
            case "wind_charge" -> {
                WindCharge charge = new WindCharge(level, pilot.getEyePosition().x + look.x, pilot.getEyePosition().y + look.y, pilot.getEyePosition().z + look.z, look);
                charge.setOwner(host);
                charge.shoot(look.x, look.y, look.z, 1.5f, 1f);
                level.addFreshEntity(charge);
                return null;
            }
            case "sonic_boom" -> {
                Entity t = target(pilot, a.param("range", 15));
                if (!(t instanceof LivingEntity victim)) return NO_TARGET;
                victim.hurt(level.damageSources().sonicBoom(host), (float) a.param("damage", 10));
                Vec3 from = host.position().add(0, 1.6, 0), dir = victim.getEyePosition().subtract(from);
                for (int i = 1; i < (int) dir.length(); i++) {
                    Vec3 p = from.add(dir.normalize().scale(i));
                    level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                level.playSound(null, host.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2, 1);
                return null;
            }
            case "potion" -> {
                var potion = new net.minecraft.world.entity.projectile.ThrownPotion(level, host);
                potion.setItem(net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.SPLASH_POTION, net.minecraft.world.item.alchemy.Potions.WEAKNESS));
                potion.setPos(pilot.getEyePosition().add(look));
                potion.shoot(look.x, look.y + 0.1, look.z, 0.9f, 2f);
                level.addFreshEntity(potion);
                return null;
            }
            case "groan" -> {
                Entity t = target(pilot, 16);
                if (!(t instanceof LivingEntity victim)) return NO_TARGET;
                for (Mob ally : level.getEntitiesOfClass(Mob.class, host.getBoundingBox().inflate(16), m -> m.getType() == host.getType() && m != host && !Hosts.isHost(m))) {
                    ally.setTarget(victim);
                }
                level.playSound(null, host.blockPosition(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.5f, 0.6f);
                return null;
            }
            case "echolocation" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(16), e -> e != host && e != pilot)) {
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200));
                }
                level.playSound(null, host.blockPosition(), SoundEvents.BAT_AMBIENT, SoundSource.NEUTRAL, 1.5f, 0.5f);
                return null;
            }
            case "hover" -> {
                host.addEffect(new MobEffectInstance(MobEffects.LEVITATION, (int) a.param("duration", 30), 0));
                host.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, (int) a.param("duration", 30) + 60, 0));
                return null;
            }
            case "sprint" -> {
                host.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, (int) a.param("duration", 100), (int) a.param("amplifier", 2)));
                level.playSound(null, host.blockPosition(), SoundEvents.HORSE_GALLOP, SoundSource.NEUTRAL, 1, 1);
                return null;
            }
            default -> {
                return "host.no_ability";
            }
        }
    }

    /** Melts Web Shot cobwebs after 10 seconds (checked once a second, in the dimension they were shot in). */
    public static void tickWebs(ServerLevel level) {
        WebShots.get(level).tick(level);
    }
}
