package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Carrying your own head. With an empty main hand you hold the jar in both arms in front of you: it lifts up from the
 * satchel when you pick it up, sways and bobs with your steps, and your head inside looks up at you. Holding anything
 * swings it round onto your back.
 */
public final class HeadCarry {
    /** Ticks for the jar to come up out of the satchel into your arms (and back down). */
    private static final float LIFT_TICKS = 8;
    private static final Map<UUID, float[]> LIFT = new WeakHashMap<>();

    private HeadCarry() {
    }

    public static boolean carrying(Player player) {
        JarState state = player.getData(ModRegistry.JAR_STATE);
        return state.form() == Form.LINKED && state.carryingHead();
    }

    /** Carrying with an empty main hand: the head is held in your arms rather than at your hip. */
    public static boolean inArms(Player player) {
        return carrying(player) && player.getMainHandItem().isEmpty();
    }

    /**
     * How far the jar is up in your arms, 0 (satchel) to 1 (held), eased. Moves toward the target a little each frame, so
     * picking it up and putting something in your hand both animate.
     */
    public static float lift(Player player, float age) {
        float[] s = LIFT.computeIfAbsent(player.getUUID(), k -> new float[]{0, age});
        float target = inArms(player) ? 1 : 0;
        float dt = Math.max(0, Math.min(5, age - s[1]));
        s[1] = age;
        float step = dt / LIFT_TICKS;
        s[0] = target > s[0] ? Math.min(target, s[0] + step) : Math.max(target, s[0] - step);
        float t = s[0];
        return t * t * (3 - 2 * t);
    }

    /** Body-space spot (pixels, y down from the shoulders) of the jar's base when held in front: low, so you can see over it. */
    private static final float FRONT_Y = 20, FRONT_Z = -9.7f;
    /** ...and when strapped to your back. */
    private static final float BACK_Y = 16, BACK_Z = 9.6f;

    /** Called at the end of HumanoidModel.setupAnim: both arms come down and forward to grip the jar's sides. */
    public static void poseArms(HumanoidModel<?> model, LivingEntity entity, float limbSwing, float limbSwingAmount, float age) {
        if (!(entity instanceof Player player) || !carrying(player)) return;
        float lift = lift(player, age);
        if (lift <= 0) return;
        // a gentle sway with each step, and a little breathing
        float sway = Mth.cos(limbSwing * 0.6662f) * 0.1f * limbSwingAmount;
        float breathe = Mth.sin(age * 0.067f) * 0.03f;
        float x = -0.78f + breathe;
        model.rightArm.xRot = Mth.lerp(lift, model.rightArm.xRot, x + sway);
        model.leftArm.xRot = Mth.lerp(lift, model.leftArm.xRot, x - sway);
        model.rightArm.yRot = Mth.lerp(lift, model.rightArm.yRot, 0);
        model.leftArm.yRot = Mth.lerp(lift, model.leftArm.yRot, 0);
        // the jar is wider than you are: the arms open out round it
        model.rightArm.zRot = Mth.lerp(lift, model.rightArm.zRot, 0.24f);
        model.leftArm.zRot = Mth.lerp(lift, model.leftArm.zRot, -0.24f);
    }

    /**
     * Third person, in body space (after body.translateAndRotate), life size: held in front against your stomach, or
     * strapped to your back while your hands are busy (your head looking out behind you). Picking it up swings it round your
     * side; walking bobs and tilts it.
     */
    public static void drawCarried(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, JarState state,
                                   float limbSwing, float limbSwingAmount, float age) {
        float lift = lift(player, age);
        float bob = Mth.sin(limbSwing * 0.6662f * 2) * 0.012f * limbSwingAmount;
        float tilt = Mth.sin(limbSwing * 0.6662f) * 4 * limbSwingAmount;
        float swing = Mth.sin(lift * Mth.PI) * 12;
        pose.translate(-swing / 16.0, Mth.lerp(lift, BACK_Y, FRONT_Y) / 16.0 + bob, Mth.lerp(lift, BACK_Z, FRONT_Z) / 16.0);
        pose.mulPose(Axis.ZP.rotationDegrees(tilt));
        pose.mulPose(Axis.XP.rotationDegrees(180));
        // on your back the jar faces backwards; in your arms forwards, with your head turned round to look up at you
        pose.mulPose(Axis.YP.rotationDegrees(180 * (1 - lift)));
        float headYaw = 180 * lift + Mth.sin(age * 0.05f) * 8 * lift;
        float pitch = -18 * lift;
        JarRenderer.draw(pose, buffers, light, OverlayTexture.NO_OVERLAY, player.getSkin().texture(), headYaw, pitch, age, true, !state.goggles().isEmpty());
    }

    /**
     * First person, life size: the jar sits low in front of you, its lid just under your line of sight, with your hands on
     * its sides. It rises into view as you lift it. Returns whether it drew (so the vanilla hands are skipped).
     */
    public static boolean drawFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, float partialTick, float equip) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !inArms(mc.player)) return false;
        AbstractClientPlayer player = mc.player;
        float age = player.tickCount + partialTick;
        float lift = lift(player, age);
        float walk = Mth.lerp(partialTick, player.walkDistO, player.walkDist);
        float amount = Mth.clamp(Mth.lerp(partialTick, player.oBob, player.bob) * 6, 0, 1);
        // camera (the Collar Cam) to the jar's base, in blocks: the same spot as in third person
        float eye = player.getEyeHeight();
        // held a little further out and lower than in third person, so you see down into the jar rather than its lid
        float baseY = 1.5f - FRONT_Y / 16f - eye - 0.04f;
        float baseZ = FRONT_Z / 16f - 0.55f;
        pose.pushPose();
        pose.translate(Mth.sin(walk * Mth.PI) * 0.02f * amount, -Math.abs(Mth.cos(walk * Mth.PI)) * 0.025f * amount - 0.9f * (1 - lift) - 0.2f * equip, 0);
        // the hands, on the jar's sides
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        for (int side = 0; side < 2; side++) {
            float f = side == 0 ? 1 : -1;
            pose.pushPose();
            pose.translate(f * 0.5f, baseY + 0.45f, baseZ + 0.05f);
            pose.mulPose(Axis.YP.rotationDegrees(f * -12));
            pose.mulPose(Axis.XP.rotationDegrees(-78));
            pose.mulPose(Axis.ZP.rotationDegrees(f * -6));
            pose.translate(-f * 0.31f, -0.7f, 0);
            if (side == 0) renderer.renderRightHand(pose, buffers, light, player);
            else renderer.renderLeftHand(pose, buffers, light, player);
            pose.popPose();
        }
        // the jar, life size, your head turned round to look up at you
        pose.translate(0, baseY, baseZ);
        // tipped toward you round its middle, as if held up to look at: the glass faces you, not the lid
        pose.translate(0, 0.5f, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-40));
        pose.translate(0, -0.5f, 0);
        var state = player.getData(ModRegistry.JAR_STATE);
        JarRenderer.draw(pose, buffers, light, OverlayTexture.NO_OVERLAY, player.getSkin().texture(), 180 + Mth.sin(age * 0.05f) * 6, -18, age, true,
                !state.goggles().isEmpty());
        pose.popPose();
        return true;
    }
}
