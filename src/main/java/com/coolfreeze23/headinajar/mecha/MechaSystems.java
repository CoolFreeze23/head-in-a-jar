package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.entity.MissileEntity;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** The P6 modules that are not arms: passive systems each tick, utility actions on R, and the defensive torso modules. */
public final class MechaSystems {
    private static final String BIO_FUEL = "headinajar_bio_fuel", FUSION_FUEL = "headinajar_fusion_fuel", REACTIVE = "headinajar_reactive_spent",
            STEALTH_OFF = "headinajar_stealth_off", SCAN = "headinajar_scan", MED_CREDIT = "headinajar_med_credit";
    public static final int REACTIVE_CHARGES = 6, PLASMA_PELLET_FE = 1_000_000;
    /** Smoke Mortar clouds still hanging in the air. */
    private static final Map<AreaEffectCloud, Boolean> SMOKE = new WeakHashMap<>();

    private MechaSystems() {
    }

    // --- every server tick -----------------------------------------------------------------------------

    /** Power in and out, heat, and the passive modules. Called by {@link MechaLogic#serverTick}. */
    public static void serverTick(MechaEntity m, Player pilot) {
        ServerLevel level = m.serverLevel();
        var data = m.getPersistentData();
        boolean moving = m.getDeltaMovement().horizontalDistanceSqr() > 0.001;
        float heat = 0;
        int drain = 0, gain = 0;

        var step = m.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) {
            var id = HeadInAJar.id("stilts");
            if (m.has(MechaModule.STILTS) && !step.hasModifier(id)) step.addTransientModifier(new AttributeModifier(id, 1, AttributeModifier.Operation.ADD_VALUE));
            else if (!m.has(MechaModule.STILTS) && step.hasModifier(id)) step.removeModifier(id);
        }
        if (pilot != null && moving && m.has(MechaModule.SPRINT_SERVOS)) drain += 15;
        if (pilot != null && m.horizontalCollision && m.has(MechaModule.ARACHNID_LEGS)) drain += 12;
        if (m.has(MechaModule.LAVA_TREADS) && (m.isInLava() || level.getFluidState(m.blockPosition().below()).is(net.minecraft.tags.FluidTags.LAVA))) {
            drain += 20;
            heat += 3;
            m.clearFire();
        }
        // cores
        if (m.has(MechaModule.BIO_REACTOR_CORE)) {
            int fuel = data.getInt(BIO_FUEL);
            if (fuel <= 0) fuel = refuelBio(m);
            if (fuel > 0) {
                int out = Math.min(60, fuel);
                data.putInt(BIO_FUEL, fuel - out);
                gain += out;
                heat += 2;
            }
        }
        int fusion = m.indexOf(MechaModule.FUSION_BOTTLE);
        if (fusion >= 0) {
            float output = 0.25f + 0.75f * m.sliderAt(fusion) / 100f;
            int fuel = data.getInt(FUSION_FUEL);
            if (fuel <= 0 && takeFromCargo(m, pilot, ModRegistry.PLASMA_PELLET.get())) fuel = PLASMA_PELLET_FE;
            if (fuel > 0) {
                int out = Math.min(Math.round(600 * output), fuel);
                data.putInt(FUSION_FUEL, fuel - out);
                gain += out;
                heat += 15 * output;
            }
        }
        // torso
        int spent = data.getInt(REACTIVE);
        if (m.has(MechaModule.REACTIVE_ARMOUR) && spent > 0 && m.tickCount % 600 == 0 && m.battery() >= 5000) {
            m.spend(5000);
            data.putInt(REACTIVE, spent - 1);
        }
        int off = data.getInt(STEALTH_OFF);
        if (off > 0) data.putInt(STEALTH_OFF, off - 1);
        if (m.has(MechaModule.STEALTH_FIELD) && off == 0 && m.battery() > 0 && m.tickCount % 10 == 0) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(32), mob -> mob.getTarget() != null
                    && (mob.getTarget() == m || mob.getTarget() == pilot) && mob.distanceTo(m) > 4)) {
                mob.setTarget(null);
            }
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + m.getBbHeight() / 2, m.getZ(), 3, m.getBbWidth() / 2, m.getBbHeight() / 3,
                    m.getBbWidth() / 2, 0);
        }
        int tesla = m.indexOf(MechaModule.TESLA_COIL);
        if (tesla >= 0 && pilot != null && m.battery() > 0) {
            float v = m.sliderAt(tesla) / 100f;
            drain += Math.round(100 + 300 * v);
            if (m.tickCount % 20 == 0) teslaArc(m, pilot, 3 + 3 * v, 2 + 4 * v);
        }
        if (m.has(MechaModule.MEDICAL_INJECTOR) && m.tickCount % 20 == 0) medical(m, pilot);
        if (m.has(MechaModule.THERMAL_IMAGING) && pilot != null && m.battery() > 0 && m.tickCount % 30 == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(32), e -> e != m && e != pilot && e.getVehicle() != m)) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 45, 0, true, false));
            }
        }
        int scan = data.getInt(SCAN);
        if (scan > 0) {
            data.putInt(SCAN, scan - 1);
            if (scan % 20 == 0 && pilot instanceof ServerPlayer sp) showOres(m, sp);
        }
        // smoke clouds blind the mobs inside them
        if (m.tickCount % 10 == 0) {
            for (AreaEffectCloud cloud : new ArrayList<>(SMOKE.keySet())) {
                if (!cloud.isAlive()) {
                    SMOKE.remove(cloud);
                    continue;
                }
                if (cloud.level() != level || cloud.distanceTo(m) > 64) continue;
                for (Mob mob : level.getEntitiesOfClass(Mob.class, cloud.getBoundingBox().inflate(0, 2, 0))) mob.setTarget(null);
            }
        }
        if (drain > 0 || gain > 0) m.setBattery(m.battery() + gain - drain);
        if (heat > 0) m.setHeat(m.heat() + heat);
    }

    private static int refuelBio(MechaEntity m) {
        for (int i = 0; i < m.cargo().getContainerSize(); i++) {
            ItemStack stack = m.cargo().getItem(i);
            int fe = 0;
            if (stack.is(ModRegistry.BIO_GEL.get())) fe = 20_000;
            else if (stack.is(ModRegistry.PLASMA_VIAL.get())) fe = 10_000;
            else {
                var food = stack.getFoodProperties(null);
                if (food != null) fe = food.nutrition() * 2000;
            }
            if (fe > 0) {
                stack.shrink(1);
                return fe;
            }
        }
        return 0;
    }

    private static boolean takeFromCargo(MechaEntity m, Player pilot, net.minecraft.world.item.Item item) {
        for (var container : pilot == null ? new net.minecraft.world.Container[]{m.cargo()} : new net.minecraft.world.Container[]{m.cargo(), pilot.getInventory()}) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).is(item)) {
                    container.removeItem(i, 1);
                    return true;
                }
            }
        }
        return false;
    }

    private static void teslaArc(MechaEntity m, Player pilot, float radius, float damage) {
        ServerLevel level = m.serverLevel();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(radius),
                e -> e instanceof Enemy && e.isAlive());
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(m)));
        Vec3 from = m.position().add(0, m.getBbHeight() * 0.8, 0);
        for (int i = 0; i < Math.min(3, targets.size()); i++) {
            LivingEntity t = targets.get(i);
            t.hurt(MechaArms.by(m, pilot, net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT), damage);
            Vec3 to = t.getEyePosition();
            for (int k = 0; k <= 8; k++) {
                Vec3 p = from.lerp(to, k / 8.0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
            }
            from = to;
        }
        if (!targets.isEmpty()) {
            m.playSound(SoundEvents.BEE_LOOP_AGGRESSIVE, 0.6f, 2f);
            m.setHeat(m.heat() + 10);
        }
    }

    /** Medical Injector: patches up the pilot and passengers below the threshold, and a damaged Chimera, with Bio-Gel. */
    private static void medical(MechaEntity m, Player pilot) {
        float threshold = m.sliderAt(m.indexOf(MechaModule.MEDICAL_INJECTOR)) / 100f;
        for (Entity e : m.getPassengers()) {
            if (e instanceof Player p && p.getHealth() < p.getMaxHealth() * threshold && takeFromCargo(m, pilot, ModRegistry.BIO_GEL.get())) {
                p.heal(4);
                m.playSound(SoundEvents.BREWING_STAND_BREW, 0.6f, 1.5f);
            }
        }
        if (m.frame() == MechaFrame.CHIMERA && m.getHealth() < m.getMaxHealth()) {
            var data = m.getPersistentData();
            int credit = data.getInt(MED_CREDIT);
            if (credit <= 0 && takeFromCargo(m, pilot, ModRegistry.BIO_GEL.get())) credit = 20;
            if (credit > 0) {
                m.heal(1);
                data.putInt(MED_CREDIT, credit - 1);
            }
        }
    }

    private static void showOres(MechaEntity m, ServerPlayer pilot) {
        int index = m.indexOf(MechaModule.ORE_SCANNER);
        int radius = 8 + 16 * (index < 0 ? 50 : m.sliderAt(index)) / 100;
        BlockPos c = m.blockPosition();
        ServerLevel level = m.serverLevel();
        int shown = 0;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-radius, -radius, -radius), c.offset(radius, radius, radius))) {
            if (!level.getBlockState(p).is(Tags.Blocks.ORES)) continue;
            level.sendParticles(pilot, ParticleTypes.WAX_ON, true, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
            if (++shown > 400) break;
        }
    }

    // --- damage -------------------------------------------------------------------------------------------

    /** Reactive Armour and the Energy Shield. Returns the damage left over. */
    public static float absorb(MechaEntity m, DamageSource source, float amount) {
        var data = m.getPersistentData();
        if (m.has(MechaModule.REACTIVE_ARMOUR) && (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_PROJECTILE))) {
            int spent = data.getInt(REACTIVE);
            if (spent < REACTIVE_CHARGES) {
                data.putInt(REACTIVE, spent + 1);
                amount *= 0.6f;
                m.serverLevel().sendParticles(ParticleTypes.EXPLOSION, m.getX(), m.getY() + m.getBbHeight() / 2, m.getZ(), 1, 0, 0, 0, 0);
            }
        }
        int shield = m.indexOf(MechaModule.ENERGY_SHIELD);
        if (shield >= 0 && m.battery() > 0 && amount > 0) {
            float share = 0.25f + 0.75f * m.sliderAt(shield) / 100f;
            float taken = Math.min(amount * share, m.battery() / 1000f);
            m.spend(Math.round(taken * 1000));
            m.setHeat(m.heat() + taken);
            amount -= taken;
            if (taken > 0) m.playSound(SoundEvents.SHIELD_BLOCK, 0.8f, 1.6f);
        }
        return amount;
    }

    public static int reactiveCharges(MechaEntity m) {
        return REACTIVE_CHARGES - m.getPersistentData().getInt(REACTIVE);
    }

    /** Attacking drops the Stealth Field for 3 s. */
    public static void revealed(MechaEntity m) {
        m.getPersistentData().putInt(STEALTH_OFF, 60);
    }

    // --- utility key --------------------------------------------------------------------------------------

    /** The utility modules fitted, in slot order. */
    public static List<MechaModule> utilities(MechaEntity m) {
        List<MechaModule> out = new ArrayList<>();
        for (int i = 0; i < m.frame().totalSlots(); i++) {
            MechaModule mod = m.moduleAt(i);
            if (mod != null && MechaModule.UTILITIES.contains(mod) && !out.contains(mod)) out.add(mod);
        }
        return out;
    }

    public static MechaModule selectedUtility(MechaEntity m) {
        List<MechaModule> list = utilities(m);
        return list.isEmpty() ? null : list.get(Math.floorMod(m.utility(), list.size()));
    }

    public static void nextUtility(MechaEntity m) {
        m.setUtility(m.utility() + 1);
    }

    /** A utility that can't fire now, and why: venting, a flat battery, or not enough FE for this one. */
    private static boolean refuse(MechaEntity m, ServerPlayer pilot, MechaModule mod, int needFe) {
        var gantry = Component.translatable("block.headinajar.assembly_gantry");
        var source = com.coolfreeze23.headinajar.feedback.Refuser.at(m);
        if (m.vent() > 0) return com.coolfreeze23.headinajar.feedback.Refusals.deny(pilot, source, "mecha.venting");
        if (m.battery() <= 0) return com.coolfreeze23.headinajar.feedback.Refusals.deny(pilot, source, "mecha.no_power", gantry);
        return com.coolfreeze23.headinajar.feedback.Refusals.deny(pilot, source, "mecha.utility.low_power",
                Component.translatable("item.headinajar." + mod.id()), (needFe + 999) / 1000, gantry);
    }

    /** R in a mecha: fires the selected utility module, or says why it can't. */
    public static boolean useUtility(MechaEntity m, ServerPlayer pilot) {
        MechaModule mod = selectedUtility(m);
        if (mod == null) return false;
        if (MechaLogic.effectiveness(m) <= 0) return refuse(m, pilot, mod, 0);
        ServerLevel level = m.serverLevel();
        switch (mod) {
            case ENDER_SATCHEL -> {
                if (!m.spend(2000)) return refuse(m, pilot, mod, 2000);
                pilot.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, p.getEnderChestInventory()),
                        Component.translatable("container.enderchest")));
                m.playSound(SoundEvents.ENDER_CHEST_OPEN, 1, 1);
            }
            case WORKBENCH -> pilot.openMenu(new SimpleMenuProvider((id, inv, p) -> new CraftingMenu(id, inv, ContainerLevelAccess.NULL),
                    Component.translatable("container.crafting")));
            case SMOKE_MORTAR -> {
                if (m.battery() < 2000) return refuse(m, pilot, mod, 2000);
                m.spend(2000);
                m.setHeat(m.heat() + 10);
                BlockHitResult hit = MechaLogic.lookBlock(pilot, 24);
                Vec3 at = hit.getType() == HitResult.Type.MISS ? pilot.getEyePosition().add(pilot.getLookAngle().scale(16)) : hit.getLocation();
                AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
                cloud.setRadius(4);
                cloud.setDuration(200);
                cloud.setParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE);
                cloud.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60));
                cloud.setOwner(m);
                level.addFreshEntity(cloud);
                SMOKE.put(cloud, true);
                m.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1, 0.6f);
            }
            case MISSILE_POD -> {
                int warhead = m.sliderAt(m.indexOf(MechaModule.MISSILE_POD));
                Entity looked = MechaLogic.lookEntity(pilot, m, 48);
                List<LivingEntity> targets = new ArrayList<>();
                if (looked instanceof LivingEntity l) targets.add(l);
                level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(32), e -> e instanceof Enemy && e.isAlive() && !targets.contains(e))
                        .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(m))).limit(4).forEach(targets::add);
                int fired = 0;
                for (int i = 0; i < 4 && m.battery() >= 4000; i++) {
                    LivingEntity target = targets.isEmpty() ? null : targets.get(i % targets.size());
                    m.spend(4000);
                    m.setHeat(m.heat() + 10 + 20 * warhead / 100f);
                    MissileEntity missile = new MissileEntity(level, m, pilot, target, 4 + 8 * warhead / 100f);
                    level.addFreshEntity(missile);
                    fired++;
                }
                if (fired == 0) return refuse(m, pilot, mod, 4000);
                m.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 0.8f);
            }
            case ORE_SCANNER -> {
                int radius = 8 + 16 * m.sliderAt(m.indexOf(MechaModule.ORE_SCANNER)) / 100;
                int cost = 4000 * radius / 8;
                if (m.battery() < cost) return refuse(m, pilot, mod, cost);
                m.spend(cost);
                m.setHeat(m.heat() + 6);
                m.getPersistentData().putInt(SCAN, 100);
                showOres(m, pilot);
                m.playSound(SoundEvents.BEACON_ACTIVATE, 0.8f, 1.8f);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Moving on hover skirts and lava treads (both sides, while ridden). */
    public static void riddenMovement(MechaEntity m) {
        Vec3 v = m.getDeltaMovement();
        int hover = m.indexOf(MechaModule.HOVER_SKIRT);
        if (hover >= 0 && m.battery() > 0) {
            double altitude = 0.5 + 1.5 * m.sliderAt(hover) / 100.0;
            double ground = groundDistance(m, altitude + 1);
            if (ground < altitude) m.setDeltaMovement(v.x, Math.max(v.y, 0.12), v.z);
            else if (ground < altitude + 0.4 && v.y < 0) m.setDeltaMovement(v.x, v.y * 0.3, v.z);
            m.fallDistance = 0;
        }
        if (m.has(MechaModule.LAVA_TREADS) && m.isInLava()) m.setDeltaMovement(v.x, Math.max(v.y, 0.1), v.z);
    }

    /** Distance down to the first solid block or liquid surface, up to {@code max}. */
    private static double groundDistance(MechaEntity m, double max) {
        var level = m.level();
        for (double d = 0; d < max; d += 0.25) {
            BlockPos p = BlockPos.containing(m.getX(), m.getY() - d, m.getZ());
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty()) return d;
        }
        return max;
    }
}
