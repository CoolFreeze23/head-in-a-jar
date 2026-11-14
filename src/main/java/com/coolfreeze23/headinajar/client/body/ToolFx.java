package com.coolfreeze23.headinajar.client.body;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.client.fx.Comfort;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Tools in the hand, first person: the saw trembles and pushes through on the cut; a syringe rises, jabs, holds while
 * it draws and comes back; the Suture Kit's needle hand loops small circles while it stitches; the wrench turns a
 * quarter in three ratchet steps. All of it is a transform laid over the vanilla hand (nothing the server waits on),
 * and in third person the saw is held in both hands. Under the reduced-motion setting the motions are smaller.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ToolFx {
    private static long sawAt = -99, syringeAt = -99, wrenchAt = -99, sutureAt = -99;

    private static float time(float partial) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    private static boolean me(int id) {
        var p = Minecraft.getInstance().player;
        return p != null && p.getId() == id;
    }

    static void saw(int cutter) {
        if (me(cutter)) sawAt = (long) time(0);
    }

    static void used(Fx fx) {
        if (!me(fx.entityA())) return;
        long now = (long) time(0);
        switch (Math.round(fx.arg())) {
            case Fx.TOOL_SYRINGE -> syringeAt = now;
            case Fx.TOOL_SUTURE -> sutureAt = now;
            case Fx.TOOL_WRENCH -> wrenchAt = now;
            default -> {
            }
        }
    }

    /** For the harness: the last start times seen, in game ticks. */
    public static String state() {
        return "saw " + sawAt + ", syringe " + syringeAt + ", wrench " + wrenchAt + ", suture " + sutureAt;
    }

    private static float smooth(float u) {
        u = Mth.clamp(u, 0, 1);
        return u * u * (3 - 2 * u);
    }

    @SubscribeEvent
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float pt, float equip, float swing) {
                float t = time(pt) - sawAt, k = Math.max(0.4f, Comfort.motion());
                if (t >= 0 && t < 16) {
                    // the blade spins up (a tremble), then a forward push on the third tick, and back
                    float tremble = t < 3 ? Mth.sin(t * 9) * 0.008f : 0;
                    float push = t < 3 ? 0 : t < 5 ? (float) Math.pow(2, 10 * ((t - 3) / 2 - 1)) : 1 - smooth((t - 5) / 11);
                    pose.translate(tremble * k, tremble * 0.5f * k, -0.14f * push * k);
                    pose.mulPose(Axis.XP.rotationDegrees(-8 * push * k));
                }
                return false;
            }

            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                // held like a small chainsaw: both hands on it
                return hand == InteractionHand.MAIN_HAND ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
            }
        }, ModRegistry.SURGICAL_SAW.get());

        event.registerItem(new IClientItemExtensions() {
            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float pt, float equip, float swing) {
                float t = time(pt) - syringeAt, k = Math.max(0.4f, Comfort.motion());
                if (t >= 0 && t < 34) {
                    // raise, a careful jab (4 ticks), hold while it draws (20), withdraw
                    float raise = t < 4 ? smooth(t / 4) : t < 28 ? 1 : 1 - smooth((t - 28) / 6);
                    float jab = t < 4 ? 0 : t < 8 ? (float) Math.pow((t - 4) / 4, 3) : t < 28 ? 1 : 1 - smooth((t - 28) / 6);
                    float shake = t >= 8 && t < 28 ? Mth.sin(t * 5) * 0.004f : 0;
                    pose.translate(shake * k, 0.06f * raise * k, -0.16f * jab * k);
                    pose.mulPose(Axis.XP.rotationDegrees(-12 * raise * k));
                }
                return false;
            }
        }, ModRegistry.SYRINGE.get(), ModRegistry.PRECISION_SYRINGE.get(), ModRegistry.LEVIATHAN_NEEDLE.get(),
                ModRegistry.MARROW_DRILL.get(), ModRegistry.OIL_TAP.get(), ModRegistry.ECTO_TRAP.get());

        event.registerItem(new IClientItemExtensions() {
            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float pt, float equip, float swing) {
                float k = Math.max(0.4f, Comfort.motion());
                if (player.isUsingItem() && player.getUseItem() == stack) {
                    // small circles, one every 10 ticks, while the other hand steadies
                    float used = player.getTicksUsingItem() + pt;
                    float a = used / 10f * Mth.TWO_PI, ramp = smooth(used / 6);
                    pose.translate(Mth.cos(a) * 0.05f * ramp * k, Mth.sin(a) * 0.04f * ramp * k, -0.04f * ramp * k);
                    pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(a) * 6 * ramp * k));
                }
                float t = time(pt) - sutureAt;
                if (t >= 0 && t < 12) pose.translate(0, -0.08f * Mth.sin(smooth(t / 12) * Mth.PI) * k, 0);
                return false;
            }
        }, ModRegistry.SUTURE_KIT.get());

        event.registerItem(new IClientItemExtensions() {
            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float pt, float equip, float swing) {
                float t = time(pt) - wrenchAt, k = Math.max(0.4f, Comfort.motion());
                if (t >= 0 && t < 16) {
                    // a quarter turn in three ratchet steps (3 ticks each), then back over 7
                    float turn = t < 9 ? (Mth.floor(t / 3) + smooth((t % 3) / 1.5f)) / 3f : 1 - smooth((t - 9) / 7);
                    pose.translate(0, 0, -0.05f * k);
                    pose.mulPose(Axis.ZP.rotationDegrees(-90 * Math.min(1, turn) * k));
                }
                return false;
            }
        }, ModRegistry.SURGICAL_WRENCH.get());
    }

    private ToolFx() {
    }
}
