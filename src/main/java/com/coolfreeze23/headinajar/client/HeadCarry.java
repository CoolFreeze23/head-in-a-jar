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
 * tucks it back into the satchel on your hip.
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

    /** Called at the end of HumanoidModel.setupAnim: both arms come forward to hold the jar. */
    public static void poseArms(HumanoidModel<?> model, LivingEntity entity, float limbSwing, float limbSwingAmount, float age) {
        if (!(entity instanceof Player player) || !carrying(player)) return;
        float lift = lift(player, age);
        if (lift <= 0) return;
        // a gentle sway with each step, and a little breathing
        float sway = Mth.cos(limbSwing * 0.6662f) * 0.12f * limbSwingAmount;
        float breathe = Mth.sin(age * 0.067f) * 0.03f;
        float x = -0.95f + breathe;
        model.rightArm.xRot = Mth.lerp(lift, model.rightArm.xRot, x + sway);
        model.leftArm.xRot = Mth.lerp(lift, model.leftArm.xRot, x - sway);
        model.rightArm.yRot = Mth.lerp(lift, model.rightArm.yRot, -0.38f);
        model.leftArm.yRot = Mth.lerp(lift, model.leftArm.yRot, 0.38f);
        model.rightArm.zRot = Mth.lerp(lift, model.rightArm.zRot, 0.06f);
        model.leftArm.zRot = Mth.lerp(lift, model.leftArm.zRot, -0.06f);
    }

    /**
     * Third person, in body space (after body.translateAndRotate): the jar between the hands, or at the hip when stowed,
     * with the bob and tilt of walking.
     */
    public static void drawCarried(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, JarState state,
                                   float limbSwing, float limbSwingAmount, float age) {
        float lift = lift(player, age);
        float bob = Mth.sin(limbSwing * 0.6662f * 2) * 0.012f * limbSwingAmount;
        float tilt = Mth.sin(limbSwing * 0.6662f) * 6 * limbSwingAmount * lift;
        // hip (satchel) to arms
        pose.translate(Mth.lerp(lift, -5.2f, 0) / 16.0, Mth.lerp(lift, 9.5f, 3.4f) / 16.0 + bob * lift, Mth.lerp(lift, 0, -8.6f) / 16.0);
        pose.mulPose(Axis.ZP.rotationDegrees(tilt));
        float scale = Mth.lerp(lift, 0.42f, 0.46f);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(180));
        pose.translate(0, -0.7, 0);
        // in your arms the head turns round to look up at you
        float headYaw = 180 * lift + Mth.sin(age * 0.05f) * 8 * lift;
        float pitch = -28 * lift;
        JarRenderer.draw(pose, buffers, light, OverlayTexture.NO_OVERLAY, player.getSkin().texture(), headYaw, pitch, age, true, !state.goggles().isEmpty());
    }

    /**
     * First person: both hands come up from below the way they hold a map, with the jar between them, bobbing as you walk.
     * Returns whether it drew (so the vanilla hands are skipped).
     */
    public static boolean drawFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, float partialTick, float equip) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !inArms(mc.player)) return false;
        AbstractClientPlayer player = mc.player;
        float age = player.tickCount + partialTick;
        float lift = lift(player, age);
        float walk = Mth.lerp(partialTick, player.walkDistO, player.walkDist);
        float amount = Mth.clamp(Mth.lerp(partialTick, player.oBob, player.bob) * 6, 0, 1);
        // looking down raises the hands into view, like a held map
        float pitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        float tilt = Mth.clamp(1 - pitch / 45 + 0.1f, 0, 1);
        tilt = -Mth.cos(tilt * Mth.PI) * 0.5f + 0.5f;
        // unlike a map, the hands stay in view: they only lean back a little as you look up
        tilt = 0.25f + 0.25f * tilt;
        pose.pushPose();
        pose.translate(Mth.sin(walk * Mth.PI) * 0.02f * amount, -Math.abs(Mth.cos(walk * Mth.PI)) * 0.025f * amount - 0.6f * (1 - lift), 0);
        // the hands
        pose.pushPose();
        pose.translate(0, 0.4f - 0.3f * equip - tilt * 0.5f, -0.72f);
        pose.mulPose(Axis.XP.rotationDegrees(tilt * -85));
        pose.mulPose(Axis.YP.rotationDegrees(90));
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        for (int side = 0; side < 2; side++) {
            float f = side == 0 ? 1 : -1;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(92));
            pose.mulPose(Axis.XP.rotationDegrees(45));
            pose.mulPose(Axis.ZP.rotationDegrees(f * -41));
            pose.translate(f * 0.3f, -1.1f, 0.45f);
            if (side == 0) renderer.renderRightHand(pose, buffers, light, player);
            else renderer.renderLeftHand(pose, buffers, light, player);
            pose.popPose();
        }
        pose.popPose();
        // the jar between them, your head turned round to look up at you
        pose.translate(0, -0.4f - tilt * 0.1f, -0.82f);
        pose.mulPose(Axis.XP.rotationDegrees(8));
        pose.scale(0.36f, 0.36f, 0.36f);
        var state = player.getData(ModRegistry.JAR_STATE);
        JarRenderer.draw(pose, buffers, light, OverlayTexture.NO_OVERLAY, player.getSkin().texture(), 180 + Mth.sin(age * 0.05f) * 6, -30, age, true,
                !state.goggles().isEmpty());
        pose.popPose();
        return true;
    }
}
