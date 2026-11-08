package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.module.ModuleType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Everything a mecha does: power, heat and weight each tick, movement, its arms, and taking damage. */
public final class MechaLogic {
    public static final float THROTTLE = 0.85f;
    public static final int VENT_TICKS = 100;

    private MechaLogic() {
    }

    /** Work in progress for one arm: what is being drilled and how far, what is held. */
    public static class ArmState {
        BlockPos target;
        float progress;
        int held = -1;
        int cooldown;
    }

    // --- weight ---------------------------------------------------------------------------------------

    /** Speed multiplier from load: full to 60%, then down to 60% at the rating, 30% overloaded, 0 locked. */
    public static double loadSpeed(float load) {
        if (load <= 0.6f) return 1;
        if (load <= 1.0f) return 1 - (load - 0.6f) / 0.4f * 0.4f;
        if (load <= 1.25f) return 0.3;
        return 0;
    }

    public static double loadJump(float load) {
        if (load <= 0.6f) return 1;
        if (load <= 1.0f) return 1 - (load - 0.6f) / 0.4f * 0.5f;
        return 0;
    }

    /** Throttled (85-99% heat) modules work at 70%; venting ones not at all. */
    public static float effectiveness(MechaEntity m) {
        if (m.vent() > 0 || m.battery() <= 0) return 0;
        return m.heat() >= m.frame().heatCap * THROTTLE ? 0.7f : 1f;
    }

    public static boolean overloaded(MechaEntity m) {
        return m.load() > 1.0f;
    }

    // --- movement (both sides) -------------------------------------------------------------------------

    public static Vec3 riddenInput(MechaEntity m, Player pilot) {
        boolean swimming = m.isInWater() && m.has(MechaModule.AQUA_FINS);
        if (!m.has(MechaModule.HYDRAULIC_LEGS) && !swimming) return Vec3.ZERO;
        if (m.load() > 1.25f || m.vent() > 0 && m.onGround()) return Vec3.ZERO;
        if (m.has(MechaModule.ANCHOR_CLAMPS) && pilot.isShiftKeyDown()) return Vec3.ZERO;
        float strafe = pilot.xxa * 0.5f, forward = pilot.zza;
        if (forward <= 0) forward *= 0.4f;
        return new Vec3(strafe, 0, forward);
    }

    public static float riddenSpeed(MechaEntity m) {
        int legs = m.indexOf(MechaModule.HYDRAULIC_LEGS);
        double stride = legs < 0 ? 1 : 0.6 + 0.8 * m.sliderAt(legs) / 100.0;
        double speed = m.frame().speed * stride * loadSpeed(m.load());
        if (m.battery() <= 0) speed *= 0.3;
        if (m.isInWater() && m.has(MechaModule.AQUA_FINS)) speed *= 2;
        return (float) speed;
    }

    /** Lift (kg) a Rocket Pack gives at its slider: 200 to 800. */
    public static int thrust(MechaEntity m) {
        int i = m.indexOf(MechaModule.ROCKET_PACK);
        return i < 0 ? 0 : 200 + 6 * m.sliderAt(i);
    }

    public static boolean canFly(MechaEntity m) {
        return thrust(m) > m.mass() && m.battery() > 0;
    }

    public static void tickRidden(MechaEntity m, Player pilot) {
        m.setYRot(pilot.getYRot());
        m.yRotO = m.getYRot();
        m.yBodyRot = m.getYRot();
        m.yHeadRot = m.getYRot();
        boolean jump = pilot.jumping;
        Vec3 v = m.getDeltaMovement();
        if (jump && canFly(m)) {
            m.setDeltaMovement(v.x, Math.min(0.45, v.y + 0.12), v.z);
            m.fallDistance = 0;
            return;
        }
        if (m.onGround()) {
            m.jetsLeft = 3;
            if (jump && m.load() <= 1.0f && m.vent() == 0) {
                int heels = m.indexOf(MechaModule.SPRING_HEELS);
                double power = heels < 0 ? 0.5 : 0.6 + 0.6 * m.sliderAt(heels) / 100.0;
                m.setDeltaMovement(v.x, power * loadJump(m.load()), v.z);
                m.hasImpulse = true;
            }
        } else {
            if (m.has(MechaModule.ROCKET_PACK) && jump && v.y < -0.1) m.setDeltaMovement(v.x, -0.1, v.z);
            if (m.has(MechaModule.GLIDER_WINGS) && jump && v.y < -0.08) {
                Vec3 look = pilot.getLookAngle().multiply(1, 0, 1).normalize().scale(0.35);
                m.setDeltaMovement(look.x, -0.08, look.z);
                m.fallDistance = 0;
            }
        }
        if (m.horizontalCollision && m.isInWater() && m.has(MechaModule.AQUA_FINS)) m.setDeltaMovement(v.x, 0.2, v.z);
    }

    /** Jump Jets: a mid-air burst (the client asks; the server spends). */
    public static boolean jet(MechaEntity m) {
        if (!m.has(MechaModule.JUMP_JETS) || m.onGround() || m.jetsLeft <= 0 || m.vent() > 0) return false;
        if (!m.spend(600)) return false;
        m.jetsLeft--;
        m.setHeat(m.heat() + 15);
        Vec3 v = m.getDeltaMovement();
        m.setDeltaMovement(v.x, 0.65, v.z);
        m.hurtMarked = true;
        m.fallDistance = 0;
        m.playSound(SoundEvents.FIRECHARGE_USE, 1, 0.8f);
        return true;
    }

    // --- every server tick ---------------------------------------------------------------------------------

    public static void serverTick(MechaEntity m) {
        ServerLevel level = m.serverLevel();
        Player pilot = Hosts.pilot(m);
        boolean moving = m.getDeltaMovement().horizontalDistanceSqr() > 0.001;
        float drain = m.frame() == MechaFrame.HOPPER ? 2 : 5;
        float heat = 0;
        for (int i = 0; i < m.frame().totalSlots(); i++) {
            MechaModule mod = m.moduleAt(i);
            if (mod == null) continue;
            drain += mod.idlePower;
            heat += mod.idleHeat;
        }
        if (moving && pilot != null) {
            drain += 4;
            heat += 0.2f;
        }
        if (overloaded(m)) drain *= 1.5f;
        if (pilot == null) drain = 0.5f;
        // power in
        int gain = 0;
        if (m.has(MechaModule.GALVANIC_STACK)) gain += 10;
        if (m.has(MechaModule.SOLAR_SKIN) && level.isDay() && level.canSeeSky(m.blockPosition().above((int) m.getBbHeight()))) gain += 15;
        if (m.has(MechaModule.COMBUSTION_KERNEL)) gain += burnFuel(m);
        if (m.has(MechaModule.ROCKET_PACK) && !m.onGround() && m.getDeltaMovement().y > 0 && pilot != null) {
            drain += 120 + 1.8f * m.sliderAt(m.indexOf(MechaModule.ROCKET_PACK));
            heat += 6;
        }
        m.setBattery(m.battery() + gain - Math.round(drain));

        // arms
        if (pilot instanceof ServerPlayer sp && m.vent() == 0 && m.battery() > 0) {
            for (int side = 0; side < 2; side++) {
                boolean held = (m.armsHeld & (1 << side)) != 0, before = (m.armsBefore & (1 << side)) != 0;
                MechaArms.tick(m, sp, side, held, held && !before, !held && before);
            }
        } else {
            for (int side = 0; side < 2; side++) MechaArms.release(m, side);
        }
        m.armsBefore = m.armsHeld;

        // heat out
        double env = 1;
        if (m.isUnderWater()) env = 3;
        else if (level.isRainingAt(m.blockPosition().above())) env = 1.5;
        if (level.dimensionType().ultraWarm()) env *= 0.5;
        float dissipation = m.frame().dissipation + (m.has(MechaModule.RADIATOR_FINS) ? 4 : 0) + (m.has(MechaModule.COOLANT_LOOP) ? 6 : 0);
        if (m.isInLava()) heat += 10;
        if (m.isOnFire() && !m.has(MechaModule.FIRE_SUPPRESSION)) heat += 3;
        m.setHeat((float) (m.heat() + heat - dissipation * env));

        if (m.vent() > 0) {
            m.setVent(m.vent() - 1);
            if (m.vent() % 4 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ(), 6, 0.8, 0.6, 0.8, 0.02);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(3), e -> e != m && e != pilot)) {
                    if (m.tickCount % 20 == 0) e.hurt(level.damageSources().hotFloor(), 2);
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40));
                }
            }
        } else if (m.heat() >= m.frame().heatCap) {
            vent(m, pilot);
        }

        // torso modules
        if (pilot != null) {
            JarState state = FormActions.state(pilot);
            if (m.has(MechaModule.LIFE_SUPPORT_RECYCLER) && m.battery() > 0) {
                state.setSaline(state.maxSaline());
                state.setCharge(JarState.maxCharge());
                if (m.tickCount % 600 == 0) state.setIntegrity(state.integrity() + 1);
            }
            if (m.has(MechaModule.NIGHT_OPTICS) && m.battery() > 0 && pilot.tickCount % 100 == 0) {
                pilot.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
            }
            if (m.has(MechaModule.FIRE_SUPPRESSION)) {
                m.clearFire();
                pilot.clearFire();
            }
        }
    }

    private static int burnFuel(MechaEntity m) {
        var data = m.getPersistentData();
        int fuel = data.getInt("headinajar_fuel");
        if (fuel <= 0) {
            for (int i = 0; i < m.cargo().getContainerSize(); i++) {
                ItemStack stack = m.cargo().getItem(i);
                int burn = stack.getBurnTime(RecipeType.SMELTING);
                if (burn > 0) {
                    stack.shrink(1);
                    fuel = burn * 10;
                    break;
                }
            }
        }
        if (fuel <= 0) return 0;
        int out = Math.min(80, fuel);
        data.putInt("headinajar_fuel", fuel - out);
        m.setHeat(m.heat() + 0.05f * out / 20f);
        return out;
    }

    /** Over its heat limit: everything shuts down for 5 s in a cloud of scalding steam. */
    public static void vent(MechaEntity m, Player pilot) {
        m.setVent(VENT_TICKS);
        m.setHeat(m.frame().heatCap * 0.5f);
        m.playSound(SoundEvents.LAVA_EXTINGUISH, 2, 0.6f);
        if (pilot != null && !FormActions.state(pilot).has(ModuleType.HEAT_SHIELD)) {
            pilot.hurt(m.serverLevel().damageSources().hotFloor(), 2);
        }
    }

    // --- damage -------------------------------------------------------------------------------------------

    /** Riot Shield, thorns: returns the damage the frame actually takes. */
    public static float incomingDamage(MechaEntity m, DamageSource source, float amount) {
        if (m.level().isClientSide) return amount;
        Entity attacker = source.getEntity();
        Player pilot = Hosts.pilot(m);
        if (attacker != null && attacker == pilot) return 0;
        if (attacker != null) {
            for (int side = 0; side < 2; side++) {
                if (m.arm(side) == MechaModule.RIOT_SHIELD && (m.armsHeld & (1 << side)) != 0) {
                    Vec3 toAttacker = attacker.position().subtract(m.position()).normalize();
                    if (toAttacker.dot(m.getLookAngle()) > 0.3) amount *= 0.2f;
                }
            }
            if (m.has(MechaModule.THORN_PLATING) && attacker instanceof LivingEntity living && source.getDirectEntity() == attacker) {
                living.hurt(m.serverLevel().damageSources().thorns(m), amount * 0.3f);
            }
        }
        int plating = m.indexOf(MechaModule.ARMOR_PLATING);
        if (plating >= 0) amount *= 1 - (0.1f + 0.002f * m.sliderAt(plating));
        return amount;
    }

    /** Golden Parachute: under 10% health the pilot is thrown clear with slow falling. */
    public static void afterDamage(MechaEntity m) {
        Player pilot = Hosts.pilot(m);
        if (pilot instanceof ServerPlayer sp && m.getHealth() < m.getMaxHealth() * 0.1f && m.isAlive()) {
            eject(m, sp);
            sp.setDeltaMovement(0, 1.2, 0);
            sp.hurtMarked = true;
            sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200));
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.headinajar.parachute"), true);
        }
    }

    public static void eject(MechaEntity m, ServerPlayer pilot) {
        m.ejecting = true;
        FormActions.leaveHost(pilot);
        m.ejecting = false;
    }

    /** Destroyed: a puff with no block damage, half the modules drop, the rest is scrap. */
    public static void destroyed(MechaEntity m) {
        ServerLevel level = m.serverLevel();
        level.explode(m, m.getX(), m.getY() + 1, m.getZ(), 2, Level.ExplosionInteraction.NONE);
        for (ItemStack stack : m.modules()) {
            if (stack.isEmpty()) continue;
            if (level.random.nextBoolean()) m.spawnAtLocation(stack.copy());
            else m.spawnAtLocation(new ItemStack(com.coolfreeze23.headinajar.registry.ModRegistry.MECHA_PLATING.get()));
        }
        for (int i = 0; i < m.cargo().getContainerSize(); i++) m.spawnAtLocation(m.cargo().removeItemNoUpdate(i));
    }

    // --- shared helpers for arms --------------------------------------------------------------------------

    static BlockHitResult lookBlock(Player pilot, double range) {
        Vec3 eye = pilot.getEyePosition();
        return pilot.level().clip(new ClipContext(eye, eye.add(pilot.getLookAngle().scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, pilot));
    }

    static Entity lookEntity(Player pilot, MechaEntity m, double range) {
        Vec3 eye = pilot.getEyePosition(), end = eye.add(pilot.getLookAngle().scale(range));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(pilot, eye, end, new AABB(eye, end).inflate(1),
                e -> e != pilot && e != m && !e.isSpectator() && e.isPickable(), range * range);
        return hit == null ? null : hit.getEntity();
    }

    /** Breaks a block as the pilot (protection mods can refuse), dropping into the Cargo Hold if there is one. */
    static boolean breakAs(MechaEntity m, ServerPlayer pilot, BlockPos pos) {
        ServerLevel level = m.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || !level.mayInteract(pilot, pos)) return false;
        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, pilot)).isCanceled()) return false;
        var be = level.getBlockEntity(pos);
        var drops = Block.getDrops(state, level, pos, be, pilot, new ItemStack(Items.IRON_PICKAXE));
        level.destroyBlock(pos, false, pilot);
        for (ItemStack drop : drops) {
            ItemStack left = m.has(MechaModule.CARGO_HOLD) ? m.cargo().addItem(drop) : drop;
            if (!left.isEmpty()) Block.popResource(level, pos, left);
        }
        return true;
    }

    /** A whole tree from one log: connected logs up to 128. */
    static Set<BlockPos> tree(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < 128) {
            BlockPos p = queue.poll();
            if (!seen.add(p) || !level.getBlockState(p).is(BlockTags.LOGS)) {
                seen.remove(p);
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (!seen.contains(n)) queue.add(n);
            }
        }
        return seen;
    }

    static Direction face(BlockHitResult hit) {
        return hit.getDirection();
    }

    static boolean miss(HitResult hit) {
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }
}
