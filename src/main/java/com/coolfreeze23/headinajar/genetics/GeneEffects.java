package com.coolfreeze23.headinajar.genetics;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** What genes do to the body that carries them, every tick and on demand (R / G for active genes). */
public final class GeneEffects {
    private static final Map<Entity, int[]> COOLDOWNS = new WeakHashMap<>();
    private static final Map<Entity, BlockPos> LIGHTS = new WeakHashMap<>();

    private GeneEffects() {
    }

    /** Whether genes in this body are working now: a player only in BODY or LINKED form, never an idle Headless Body. */
    public static boolean active(LivingEntity e) {
        if (e instanceof HeadlessBody) return false;
        if (e instanceof Player p) {
            Form f = p.getData(ModRegistry.JAR_STATE).form();
            return f == Form.BODY || f == Form.LINKED;
        }
        return true;
    }

    /** Hosts and bodies get numeric effects at 70%; mecha at full strength. */
    public static float scale(LivingEntity e) {
        return e instanceof MechaEntity ? 1f : 0.7f;
    }

    public static float s(LivingEntity e, Gene g) {
        return active(e) ? Genetics.genes(e).strength(g) : 0;
    }

    public static boolean has(LivingEntity e, Gene g) {
        return s(e, g) >= 0.5f;
    }

    private static void set(LivingEntity e, Holder<Attribute> attr, String id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        ResourceLocation key = HeadInAJar.id("gene_" + id);
        if (amount == 0) {
            if (inst.hasModifier(key)) inst.removeModifier(key);
        } else {
            AttributeModifier existing = inst.getModifier(key);
            if (existing == null || existing.amount() != amount) inst.addOrUpdateTransientModifier(new AttributeModifier(key, amount, op));
        }
    }

    /** Every tick, both sides (movement effects must match on the client that moves the body). */
    public static void tick(LivingEntity e) {
        GeneSet set = Genetics.genes(e);
        boolean on = active(e) && !set.isEmpty();
        float k = scale(e);
        set(e, Attributes.JUMP_STRENGTH, "rabbit_spring", on ? 0.15 * s(e, Gene.RABBIT_SPRING) * k : 0, AttributeModifier.Operation.ADD_VALUE);
        set(e, NeoForgeMod.SWIM_SPEED, "dolphin_grace", on ? s(e, Gene.DOLPHIN_GRACE) * k : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(e, Attributes.MOVEMENT_SPEED, "equine_gait", on ? 0.15 * s(e, Gene.EQUINE_GAIT) * k : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(e, Attributes.ARMOR, "golem_frame", on ? 4 * s(e, Gene.GOLEM_FRAME) * k : 0, AttributeModifier.Operation.ADD_VALUE);
        set(e, Attributes.KNOCKBACK_RESISTANCE, "golem_frame", on ? 0.5 * s(e, Gene.GOLEM_FRAME) * k : 0, AttributeModifier.Operation.ADD_VALUE);
        set(e, Attributes.SCALE, "size", on ? (has(e, Gene.GIGANTISM) ? 0.1 : 0) - (has(e, Gene.SHRINKAGE) ? 0.1 : 0) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(e, Attributes.MAX_HEALTH, "hybrid_vigour", on && has(e, Gene.HYBRID_VIGOUR) ? 0.1 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        boolean daylight = e.level().isDay() && e.level().canSeeSky(e.blockPosition());
        set(e, Attributes.MOVEMENT_SPEED, "photophobia", on && has(e, Gene.PHOTOPHOBIA) && daylight ? -0.15 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (e instanceof Player p) set(e, NeoForgeMod.CREATIVE_FLIGHT, "draconic_wings", on && has(e, Gene.DRACONIC_WINGS) ? 1 : 0, AttributeModifier.Operation.ADD_VALUE);
        if (!on) return;
        movement(e);
        if (!e.level().isClientSide) serverTick(e, (ServerLevel) e.level());
    }

    /** Climbing, gliding, slow fall, lava floating. Also called from host and mecha steering. */
    public static void movement(LivingEntity e) {
        Vec3 v = e.getDeltaMovement();
        if (has(e, Gene.SPIDER_CLIMB) && e.horizontalCollision) {
            e.setDeltaMovement(v.x, 0.2, v.z);
            e.fallDistance = 0;
            v = e.getDeltaMovement();
        }
        if (has(e, Gene.FEATHER_BONES) && v.y < -0.08) {
            e.setDeltaMovement(v.x, -0.08, v.z);
            e.fallDistance = 0;
        } else if (has(e, Gene.PHANTOM_MEMBRANE) && v.y < -0.3 && e.isShiftKeyDown()) {
            Vec3 look = e.getLookAngle().multiply(1, 0, 1).normalize().scale(0.3);
            e.setDeltaMovement(look.x, -0.1, look.z);
            e.fallDistance = 0;
        }
        if (has(e, Gene.STRIDER_SOLES) && e.isInLava()) e.setDeltaMovement(v.x, Math.max(v.y, 0.12), v.z);
    }

    private static void serverTick(LivingEntity e, ServerLevel level) {
        int t = e.tickCount;
        if (has(e, Gene.GILLS) && e.isUnderWater()) e.setAirSupply(e.getMaxAirSupply());
        if (has(e, Gene.BLAZE_CORE)) e.clearFire();
        if (e instanceof Player p && has(e, Gene.CAT_EYES) && t % 100 == 0) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
        if (e instanceof Player p && has(e, Gene.HEROS_BLOOD) && t % 200 == 0) p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 400, 0, true, false, false));
        if (has(e, Gene.ECHOLOCATION) && t % 80 == 0) {
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(16), o -> o != e)) {
                other.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false));
            }
        }
        if (has(e, Gene.AXOLOTL_REGROWTH) && t % 40 == 0 && (e.isInWaterOrRain())) e.heal(1);
        if (has(e, Gene.HICCUPS) && e.onGround() && t % (400 + (e.getId() % 400)) == 0) {
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.35, 0));
            e.hurtMarked = true;
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.6f, 1.6f);
        }
        if (has(e, Gene.CLUCKING) && t % 300 == 0) level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.CHICKEN_AMBIENT, SoundSource.PLAYERS, 1, 1);
        if (has(e, Gene.MAGNETIC_PERSONALITY) && t % 4 == 0) {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, e.getBoundingBox().inflate(4),
                    i -> i.getItem().is(net.neoforged.neoforge.common.Tags.Items.INGOTS_IRON) || i.getItem().is(net.minecraft.world.item.Items.IRON_NUGGET))) {
                item.setDeltaMovement(e.position().subtract(item.position()).normalize().scale(0.2));
            }
        }
        light(e, level, has(e, Gene.GLOW_CELLS) || has(e, Gene.BIOLUMINESCENCE));
        int[] cd = COOLDOWNS.get(e);
        if (cd != null) {
            if (cd[0] > 0) cd[0]--;
            if (cd[1] > 0) cd[1]--;
        }
    }

    /** Glow Cells: an invisible light block follows the body. */
    private static void light(LivingEntity e, ServerLevel level, boolean glow) {
        BlockPos old = LIGHTS.get(e);
        BlockPos now = glow && e.isAlive() ? e.blockPosition().above() : null;
        if (old != null && !old.equals(now) && level.getBlockState(old).is(Blocks.LIGHT)) level.removeBlock(old, false);
        if (now != null && level.getBlockState(now).isAir()) {
            level.setBlock(now, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 10), 3);
            LIGHTS.put(e, now);
        } else if (now == null) {
            LIGHTS.remove(e);
        }
    }

    /** The active genes of a body, in gene order: the first answers R, the second G. */
    public static List<Gene> actives(LivingEntity e) {
        List<Gene> out = new ArrayList<>();
        for (Gene g : Gene.values()) if (g.active && has(e, g)) out.add(g);
        return out;
    }

    /** Fires an active gene (R = 0, G = 1). {@code aim} is whoever looks: the pilot of a host or mecha, or the body itself. */
    public static boolean activate(LivingEntity body, LivingEntity aim, int which) {
        List<Gene> actives = actives(body);
        if (which >= actives.size()) return false;
        int[] cd = COOLDOWNS.computeIfAbsent(body, k -> new int[2]);
        if (cd[which] > 0) return false;
        Gene gene = actives.get(which);
        ServerLevel level = (ServerLevel) body.level();
        Vec3 look = aim.getLookAngle();
        boolean done = switch (gene) {
            case ENDER_BLINK -> {
                Vec3 eye = aim.getEyePosition();
                BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(24)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, aim));
                if (hit.getType() != HitResult.Type.BLOCK) yield false;
                BlockPos to = hit.getBlockPos().relative(hit.getDirection());
                Entity mover = body.getVehicle() != null ? body.getVehicle() : body;
                mover.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
                level.playSound(null, to, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1);
                cd[which] = 60;
                yield true;
            }
            case GUARDIAN_EYE -> {
                LivingEntity target = target(aim, body, 16);
                if (target == null) yield false;
                target.hurt(level.damageSources().indirectMagic(body, body), 8 * scale(body));
                Vec3 from = aim.getEyePosition(), dir = target.getEyePosition().subtract(from);
                for (int i = 1; i < (int) dir.length(); i++) {
                    Vec3 p = from.add(dir.normalize().scale(i));
                    level.sendParticles(ParticleTypes.BUBBLE, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
                }
                level.playSound(null, body.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1, 1);
                cd[which] = 60;
                yield true;
            }
            case BREEZE_UPDRAFT -> {
                Vec3 eye = aim.getEyePosition().add(look);
                WindCharge charge = new WindCharge(level, eye.x, eye.y, eye.z, look);
                charge.setOwner(body);
                charge.shoot(look.x, look.y, look.z, 1.5f, 1f);
                level.addFreshEntity(charge);
                cd[which] = 30;
                yield true;
            }
            case SONIC_BOOM -> {
                LivingEntity target = target(aim, body, 15);
                if (target == null) yield false;
                target.hurt(level.damageSources().sonicBoom(body), 10);
                level.playSound(null, body.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2, 1);
                cd[which] = 300;
                yield true;
            }
            default -> false;
        };
        return done;
    }

    private static LivingEntity target(LivingEntity aim, LivingEntity body, double range) {
        Vec3 eye = aim.getEyePosition(), end = eye.add(aim.getLookAngle().scale(range));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(aim, eye, end, new AABB(eye, end).inflate(1),
                o -> o != aim && o != body && o instanceof LivingEntity && o.isAlive(), range * range);
        return hit == null ? null : (LivingEntity) hit.getEntity();
    }
}
