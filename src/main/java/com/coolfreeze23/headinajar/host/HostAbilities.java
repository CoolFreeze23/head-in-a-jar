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
    /** A refusal {@link #fire} has already said itself (one that names someone). */
    static final String SAID = "";

    private HostAbilities() {
    }

    /**
     * Fires the host's primary or secondary ability if it is unlocked and off cooldown; says why not otherwise. A Lunk
     * answers a refused toss with its refused scoop, a Spider Walker a refused web with its dud (it rears, the nozzle
     * coughs and it taps it), as well as the line.
     */
    public static boolean use(ServerPlayer pilot, Mob host, boolean secondary) {
        boolean used = fireAbility(pilot, host, secondary);
        if (!used && !secondary && host instanceof com.coolfreeze23.headinajar.entity.ShellEntity shell) {
            if (shell.kind() == com.coolfreeze23.headinajar.entity.ShellEntity.Kind.LUNK) shell.tossRefused();
            else shell.webDud();
        }
        return used;
    }

    private static boolean fireAbility(ServerPlayer pilot, Mob host, boolean secondary) {
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
        if (refused != null) return refused.equals(SAID) ? false : Refusals.deny(pilot, Refuser.at(host), refused);
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
                if (host instanceof com.coolfreeze23.headinajar.entity.ShellEntity shell && shell.animated()) {
                    // the Spider Walker rears and spits: the web lands with the spit, on the clip's tick 4
                    shell.spit(at, pilot);
                    return null;
                }
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
                // a player is picked up only if they trust the rider (teams count), as for every other lift
                if (t instanceof net.minecraft.world.entity.player.Player other
                        && !com.coolfreeze23.headinajar.form.HeadRegistry.get(level.getServer()).trusts(other.getUUID(), pilot)) {
                    Refusals.deny(pilot, Refuser.at(host), "mecha.lift.untrusted", other.getDisplayName());
                    return SAID;
                }
                if (host instanceof com.coolfreeze23.headinajar.entity.ShellEntity shell && shell.kind() == com.coolfreeze23.headinajar.entity.ShellEntity.Kind.LUNK) {
                    // the Lunk scoops, heaves and lets go: the push comes at its clip's release, with its own sounds
                    shell.startToss(t, a.param("power", 1.2));
                    return null;
                }
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
                if (host instanceof com.coolfreeze23.headinajar.entity.ShellEntity shell) shell.markSprint();
                level.playSound(null, host.blockPosition(), SoundEvents.HORSE_GALLOP, SoundSource.NEUTRAL, 1, 1);
                return null;
            }
            default -> {
                // the 0.6.3 tricks (or "no trick for that" for a type nobody knows)
                return HostTricks.fire(pilot, host, a);
            }
        }
    }

    // --- the job brain's use of a seconded body (0.7.1) -------------------------------------------------------------

    /** {@link #fireFor}'s answer for a type the brain never uses: explode ("Staff do not explode."), groan, wind charge. */
    public static final String NEVER = "secondment.no_explode";
    /** {@link #fireFor}'s answer for a witch's potion with a friend within 4 of its foe: it is not thrown. */
    public static final String FRIENDS_NEAR = "secondment.friends_near";
    /** How far above a seated body's neck the jar's middle is: where its shots leave from. */
    public static final double JAR_MID = 0.45;

    /**
     * A seconded host's Ability or Secondary used by the job brain ({@code staff.second.BodyWork}), beside {@link #fire}
     * and never touching the player's path: aimed from the jar at {@code target}, every shot owned by the host, and every
     * area effect on foes only (the company's friend-or-foe rule for {@code employer}: never the employer, other staff,
     * villagers, golems, pets, allies or animals). Never explode, groan or wind charge ({@link #NEVER}); a teleport is
     * the movers' hop to a stop (the brain chooses the cell), so it is not fired here. Ranges, sight and cooldowns are the
     * brain's to check. Returns null when it fired, else why not.
     */
    public static String fireFor(Mob host, LivingEntity target, HostDefinition.Ability a, java.util.UUID employer) {
        if (!(host.level() instanceof ServerLevel level) || a == null) return NO_TARGET;
        Vec3 from = com.coolfreeze23.headinajar.body.BodyServer.neck(host).add(0, JAR_MID, 0);
        Vec3 aim = target == null ? Vec3.directionFromRotation(0, host.getYRot()) : target.getBoundingBox().getCenter().subtract(from);
        Vec3 dir = aim.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, host.getYRot()) : aim.normalize();
        double flat = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
        java.util.function.Predicate<LivingEntity> foe = e -> e != host && e.isAlive()
                && com.coolfreeze23.headinajar.staff.work.FriendOrFoe.mayTarget(employer, level, e);
        switch (a.type()) {
            case "explode", "groan", "wind_charge" -> {
                return NEVER;
            }
            case "arrows" -> {
                if (target == null) return NO_TARGET;
                int count = (int) a.param("count", 3);
                for (int i = 0; i < count; i++) {
                    var arrow = new net.minecraft.world.entity.projectile.Arrow(level, host, new ItemStack(Items.ARROW), null);
                    arrow.setPos(from.add(dir.scale(0.5)));
                    arrow.shoot(aim.x, aim.y + flat * 0.2, aim.z, 1.6f, 4f);
                    arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
                    level.addFreshEntity(arrow);
                }
                level.playSound(null, host.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1, 1);
                return null;
            }
            case "fireball" -> {
                if (target == null) return NO_TARGET;
                boolean large = a.param("large", 0) > 0;
                int count = large ? 1 : (int) a.param("count", 3);
                for (int i = 0; i < count; i++) {
                    // three small ones spread a little; a ghast's one goes where it is aimed, as a ghast's does
                    double spread = large ? 0 : 0.05;
                    Vec3 d = dir.add(level.random.nextGaussian() * spread, level.random.nextGaussian() * spread, level.random.nextGaussian() * spread);
                    var ball = large ? new LargeFireball(level, host, d, (int) a.param("power", 1)) : new SmallFireball(level, host, d);
                    ball.setPos(from.add(dir.scale(large ? 1.2 : 0.6)));
                    level.addFreshEntity(ball);
                }
                level.playSound(null, host.blockPosition(), large ? SoundEvents.GHAST_SHOOT : SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1, 1);
                return null;
            }
            case "web" -> {
                if (target == null) return NO_TARGET;
                BlockPos at = target.blockPosition();
                // a web only fills air, and only where the employer may build (when they are here to ask)
                if (!level.getBlockState(at).isAir()) return "host.web_blocked";
                ServerPlayer boss = employer == null ? null : level.getServer().getPlayerList().getPlayer(employer);
                if (boss != null && boss.level() == level && !level.mayInteract(boss, at)) return "host.web_blocked";
                level.setBlockAndUpdate(at, Blocks.COBWEB.defaultBlockState());
                WebShots.get(level).add(at, level.getGameTime() + 200);
                level.playSound(null, host.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1, 1.4f);
                return null;
            }
            case "charge", "pounce" -> {
                Vec3 along = new Vec3(dir.x, 0, dir.z);
                along = along.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, host.getYRot()) : along.normalize();
                Vec3 push = along.scale(a.param("speed", 1.4));
                host.setDeltaMovement(push.x, a.type().equals("pounce") ? 0.5 : host.getDeltaMovement().y, push.z);
                host.hurtMarked = true;
                float damage = (float) a.param("damage", 6);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().expandTowards(push.scale(2)).inflate(0.5), foe)) {
                    com.coolfreeze23.headinajar.staff.work.Combat.credit(host, e);
                    e.hurt(host.damageSources().mobAttack(host), damage);
                    e.knockback(a.param("knockback", 1.5), -push.x, -push.z);
                }
                level.playSound(null, host.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1, 1.2f);
                return null;
            }
            case "toss" -> {
                // a player only where the company's rule lets staff fight players at all (genetics.pvpTurrets)
                if (target == null || !foe.test(target)) return NO_TARGET;
                target.setDeltaMovement(target.getDeltaMovement().add(0, a.param("power", 1.2), 0));
                target.hurtMarked = true;
                level.playSound(null, host.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1, 1);
                return null;
            }
            case "roar" -> {
                double r = a.param("radius", 6);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(r), foe)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) a.param("duration", 40), 4));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) a.param("duration", 40), 0));
                    e.knockback(0.8, host.getX() - e.getX(), host.getZ() - e.getZ());
                }
                level.playSound(null, host.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 1);
                return null;
            }
            case "ink" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(a.param("radius", 6)), foe)) {
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80));
                }
                level.sendParticles(ParticleTypes.SQUID_INK, host.getX(), host.getY() + 0.5, host.getZ(), 40, 1, 0.5, 1, 0.05);
                level.playSound(null, host.blockPosition(), SoundEvents.SQUID_SQUIRT, SoundSource.NEUTRAL, 1, 1);
                return null;
            }
            case "echolocation" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(a.param("radius", 16)), foe)) {
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200));
                }
                level.playSound(null, host.blockPosition(), SoundEvents.BAT_AMBIENT, SoundSource.NEUTRAL, 1.5f, 0.5f);
                return null;
            }
            case "sonic_boom" -> {
                if (target == null || !foe.test(target)) return NO_TARGET;
                com.coolfreeze23.headinajar.staff.work.Combat.credit(host, target);
                target.hurt(level.damageSources().sonicBoom(host), (float) a.param("damage", 10));
                Vec3 d = target.getEyePosition().subtract(from);
                for (int i = 1; i < (int) d.length(); i++) {
                    Vec3 p = from.add(d.normalize().scale(i));
                    level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                level.playSound(null, host.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2, 1);
                return null;
            }
            case "potion" -> {
                if (target == null) return NO_TARGET;
                // thrown only when nobody the company spares is within 4 of the foe (the splash would reach them)
                boolean friends = !level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(4),
                        e -> e != host && e != target && e.isAlive() && e.distanceToSqr(target) <= 16 && !foe.test(e)).isEmpty();
                if (friends) return FRIENDS_NEAR;
                var potion = new net.minecraft.world.entity.projectile.ThrownPotion(level, host);
                potion.setItem(net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.SPLASH_POTION, net.minecraft.world.item.alchemy.Potions.WEAKNESS));
                potion.setPos(from.add(dir.scale(0.5)));
                potion.shoot(aim.x, aim.y + flat * 0.2, aim.z, 0.75f, 2f);
                level.addFreshEntity(potion);
                level.playSound(null, host.blockPosition(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1, 1);
                return null;
            }
            case "spit" -> {
                if (target == null) return NO_TARGET;
                var spit = new net.minecraft.world.entity.projectile.LlamaSpit(net.minecraft.world.entity.EntityType.LLAMA_SPIT, level);
                spit.setOwner(host);
                spit.setPos(from.x, from.y, from.z);
                spit.shoot(aim.x, aim.y + flat * 0.15, aim.z, 1.5f, 1f);
                level.addFreshEntity(spit);
                level.playSound(null, host.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.NEUTRAL, 1, 1);
                return null;
            }
            case "hiss" -> {
                double r = a.param("radius", 8);
                var creepers = level.getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class, host.getBoundingBox().inflate(r),
                        c -> foe.test(c) && c.distanceToSqr(host) <= r * r);
                if (creepers.isEmpty()) return "host.no_creepers";
                for (var c : creepers) {
                    // its fuse winds back and it walks away from the body
                    c.setTarget(null);
                    ((com.coolfreeze23.headinajar.mixin.CreeperSwellAccess) c).headinajar$setSwell(0);
                    c.setSwellDir(-1);
                    Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(c, 16, 7, host.position());
                    if (away != null) c.getNavigation().moveTo(away.x, away.y, away.z, 1.25);
                }
                level.playSound(null, host.blockPosition(), SoundEvents.CAT_HISS, SoundSource.NEUTRAL, 1.5f, 1);
                return null;
            }
            case "rear" -> {
                double r = a.param("radius", 3), reach = r + host.getBbWidth() / 2;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(r),
                        e -> foe.test(e) && e.distanceToSqr(host) <= reach * reach)) {
                    e.knockback(a.param("knockback", 1.2), host.getX() - e.getX(), host.getZ() - e.getZ());
                    e.hurtMarked = true;
                }
                if (host instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse) horse.standIfPossible();
                level.playSound(null, host.blockPosition(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1, 1);
                return null;
            }
            case "bell" -> {
                // the village bell at work: raiders on site show through walls; nobody else is told anything
                double r = a.param("radius", 32);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(r),
                        e -> foe.test(e) && e.getType().is(net.minecraft.tags.EntityTypeTags.RAIDERS) && e.distanceToSqr(host) <= r * r)) {
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) a.param("duration", 200)));
                }
                level.playSound(null, host.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 2, 1);
                return null;
            }
            case "sprint" -> {
                host.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, (int) a.param("duration", 100), (int) a.param("amplifier", 2)));
                level.playSound(null, host.blockPosition(), SoundEvents.HORSE_GALLOP, SoundSource.NEUTRAL, 1, 1);
                return null;
            }
            case "hover" -> {
                host.addEffect(new MobEffectInstance(MobEffects.LEVITATION, (int) a.param("duration", 30), 0));
                host.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, (int) a.param("duration", 30) + 60, 0));
                return null;
            }
            case "lay_egg" -> {
                level.addFreshEntity(new ItemEntity(level, host.getX(), host.getY(), host.getZ(), new ItemStack(Items.EGG)));
                level.playSound(null, host.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1, 1);
                return null;
            }
            default -> {
                // a teleport (the movers' hop), or one of the 0.6.3 tricks the brain does not use (staff.second.BodyWork)
                return HostTricks.UNKNOWN;
            }
        }
    }

    /** Melts Web Shot cobwebs after 10 seconds (checked once a second, in the dimension they were shot in). */
    public static void tickWebs(ServerLevel level) {
        WebShots.get(level).tick(level);
    }
}
