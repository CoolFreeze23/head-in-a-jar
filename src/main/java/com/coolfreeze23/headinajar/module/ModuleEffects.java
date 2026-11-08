package com.coolfreeze23.headinajar.module;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.JarState;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;

/** Attribute and effect side of the jar and its modules. Runs every tick on both sides so movement predicts correctly. */
public final class ModuleEffects {
    private static final ResourceLocation JAR_ARMOR = HeadInAJar.id("jar_glass");
    private static final ResourceLocation PLATING_ARMOR = HeadInAJar.id("armor_plating");
    private static final ResourceLocation PLATING_TOUGHNESS = HeadInAJar.id("armor_plating_toughness");
    private static final ResourceLocation SAFE_FALL = HeadInAJar.id("jar_safe_fall");
    private static final ResourceLocation HOP_SPEED = HeadInAJar.id("hop_springs");
    private static final ResourceLocation FLIGHT = HeadInAJar.id("propeller");
    private static final ResourceLocation SWIM = HeadInAJar.id("water_jet");
    private static final ResourceLocation ARM_DAMAGE = HeadInAJar.id("manipulator_arm");
    private static final ResourceLocation LEGS_SPEED = HeadInAJar.id("spider_legs");
    private static final ResourceLocation LEGS_STEP = HeadInAJar.id("spider_legs_step");
    private static final ResourceLocation GYRO = HeadInAJar.id("self_righting_gyro");

    public static void apply(Player player, JarState state) {
        boolean jar = state.form().isJarred();
        set(player, Attributes.ARMOR, JAR_ARMOR, jar ? 2 : 0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL, jar ? 3 : 0, AttributeModifier.Operation.ADD_VALUE);
        boolean plating = state.has(ModuleType.ARMOR_PLATING);
        set(player, Attributes.ARMOR, PLATING_ARMOR, plating ? 6 : 0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ARMOR_TOUGHNESS, PLATING_TOUGHNESS, plating ? 2 : 0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MOVEMENT_SPEED, HOP_SPEED, state.has(ModuleType.HOP_SPRINGS) ? -0.4 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(player, NeoForgeMod.CREATIVE_FLIGHT, FLIGHT, state.has(ModuleType.PROPELLER) ? 1 : 0, AttributeModifier.Operation.ADD_VALUE);
        set(player, NeoForgeMod.SWIM_SPEED, SWIM, state.has(ModuleType.WATER_JET) ? 1.5 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ATTACK_DAMAGE, ARM_DAMAGE, jar && state.has(ModuleType.MANIPULATOR_ARM) ? -0.5 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        boolean legs = state.has(ModuleType.SPIDER_LEGS);
        set(player, Attributes.MOVEMENT_SPEED, LEGS_SPEED, legs && !state.has(ModuleType.HOP_SPRINGS) ? -0.2 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(player, Attributes.STEP_HEIGHT, LEGS_STEP, legs ? 0.45 : 0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.KNOCKBACK_RESISTANCE, GYRO, state.has(ModuleType.SELF_RIGHTING_GYRO) ? 1 : 0, AttributeModifier.Operation.ADD_VALUE);
        if (state.has(ModuleType.SELF_RIGHTING_GYRO) && state.rollTicks() > 0) state.setRollTicks(0);
        // suction cups: walking into a wall climbs it, sneaking hangs on
        if (state.has(ModuleType.SUCTION_CUPS) && player.horizontalCollision) {
            var v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, player.isShiftKeyDown() ? Math.max(v.y, 0) : 0.2, v.z);
            player.fallDistance = 0;
        }

        if (player.getAbilities().flying && !player.mayFly()) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        if (jar) player.setSprinting(false);

        if (!player.level().isClientSide && state.has(ModuleType.NIGHT_LENS)) {
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current == null || current.getDuration() < 220) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
            }
        }
    }

    private static void set(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        if (amount == 0) {
            if (instance.hasModifier(id)) instance.removeModifier(id);
        } else {
            AttributeModifier existing = instance.getModifier(id);
            if (existing == null || existing.amount() != amount) {
                instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
            }
        }
    }

    private ModuleEffects() {
    }
}
