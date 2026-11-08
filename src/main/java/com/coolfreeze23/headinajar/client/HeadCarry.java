package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Carrying your own head. With an empty main hand you hold the jar in both arms in front of you, by its sides: it swings
 * round from your back when you pick it up, turns with your hands as they sway, and your head inside looks up at you.
 * Holding anything swings it round onto your back.
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

    /** The model and walk cycle each carrying player was last posed with, so the pose can be put back after other mods animate. */
    private static final Map<UUID, Posed> POSED = new HashMap<>();

    private record Posed(HumanoidModel<?> model, float limbSwing, float limbSwingAmount, float age) {
    }

    /** Called at the end of HumanoidModel.setupAnim: both arms come down and forward to grip the jar's sides. */
    public static void poseArms(HumanoidModel<?> model, LivingEntity entity, float limbSwing, float limbSwingAmount, float age) {
        if (!(entity instanceof Player player) || drawingHands) return;
        if (!carrying(player)) {
            POSED.remove(player.getUUID());
            return;
        }
        POSED.put(player.getUUID(), new Posed(model, limbSwing, limbSwingAmount, age));
        applyArms(model, player, limbSwing, limbSwingAmount, age);
    }

    /**
     * Puts the carrying pose back after an animation mod (Entity Model Features, under Fresh Animations' player pack) has
     * moved the arms, sleeves included.
     */
    public static void reposeArms(Player player) {
        Posed posed = POSED.get(player.getUUID());
        if (posed == null || !carrying(player)) return;
        HumanoidModel<?> model = posed.model();
        applyArms(model, player, posed.limbSwing(), posed.limbSwingAmount(), posed.age());
        if (model instanceof PlayerModel<?> playerModel) {
            playerModel.rightSleeve.copyFrom(model.rightArm);
            playerModel.leftSleeve.copyFrom(model.leftArm);
        }
    }

    /**
     * The carrying pose: both arms forward and opened wide round the jar, which is twice as wide as you are, so each hand
     * lands just outside the glass on its side (about 10.5 px out, 6 px down and 6 px in front of the shoulders).
     */
    private static final float ARMS_FORWARD = -0.75f, ARMS_OUT = 0.82f;

    private static void applyArms(HumanoidModel<?> model, Player player, float limbSwing, float limbSwingAmount, float age) {
        float lift = lift(player, age);
        if (lift <= 0) return;
        // a gentle sway with each step (the jar turns with it), and a little breathing
        float sway = Mth.cos(limbSwing * 0.6662f) * 0.1f * limbSwingAmount;
        float breathe = Mth.sin(age * 0.067f) * 0.03f;
        float x = ARMS_FORWARD + breathe;
        model.rightArm.xRot = Mth.lerp(lift, model.rightArm.xRot, x + sway);
        model.leftArm.xRot = Mth.lerp(lift, model.leftArm.xRot, x - sway);
        model.rightArm.yRot = Mth.lerp(lift, model.rightArm.yRot, 0);
        model.leftArm.yRot = Mth.lerp(lift, model.leftArm.yRot, 0);
        model.rightArm.zRot = Mth.lerp(lift, model.rightArm.zRot, ARMS_OUT);
        model.leftArm.zRot = Mth.lerp(lift, model.leftArm.zRot, -ARMS_OUT);
    }

    /** Where the hands hold the jar: this far up from its base, and this far behind its middle (pixels). */
    private static final float GRIP_UP = 9, GRIP_BACK = 4.5f;

    /**
     * Third person, in model space, life size. In your arms the jar is held by your hands: it is placed between them every
     * frame (their grip points on its sides), so it moves and turns with them, whatever is animating the arms. Strapped to
     * your back while your hands are busy, it rides on your body, your head looking out behind you. Picking it up swings it
     * round your side between the two.
     */
    public static void drawCarried(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, JarState state,
                                   HumanoidModel<?> model, float limbSwing, float limbSwingAmount, float age) {
        float lift = lift(player, age);
        // on your back: in body space, facing backwards, bobbing a little with your steps
        PoseStack back = new PoseStack();
        model.body.translateAndRotate(back);
        float bob = Mth.sin(limbSwing * 0.6662f * 2) * 0.012f * limbSwingAmount;
        back.translate(0, BACK_Y / 16.0 + bob, BACK_Z / 16.0);
        back.mulPose(Axis.ZP.rotationDegrees(Mth.sin(limbSwing * 0.6662f) * 4 * limbSwingAmount));
        back.mulPose(Axis.XP.rotationDegrees(180));
        back.mulPose(Axis.YP.rotationDegrees(180));
        // in your arms: between the hands, square to the line across them
        Vector3f right = grip(model.rightArm, -1), left = grip(model.leftArm, 1);
        Vector3f across = new Vector3f(left).sub(right);
        float flat = Mth.sqrt(across.x * across.x + across.z * across.z);
        PoseStack front = new PoseStack();
        front.translate((right.x + left.x) / 2, (right.y + left.y) / 2, (right.z + left.z) / 2);
        front.mulPose(Axis.YP.rotation((float) -Math.atan2(across.z, across.x)));
        front.mulPose(Axis.ZP.rotation((float) Math.atan2(across.y, flat)));
        front.translate(0, GRIP_UP / 16.0, -GRIP_BACK / 16.0);
        front.mulPose(Axis.XP.rotationDegrees(180));
        // between the two while it is being picked up or put away, swinging out round your side
        Matrix4f a = back.last().pose(), b = front.last().pose();
        Vector3f at = a.getTranslation(new Vector3f()).lerp(b.getTranslation(new Vector3f()), lift);
        at.x -= Mth.sin(lift * Mth.PI) * 12 / 16f;
        Quaternionf turn = a.getUnnormalizedRotation(new Quaternionf()).slerp(b.getUnnormalizedRotation(new Quaternionf()), lift);
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(turn);
        // your head turns round inside to look up at you while you hold it
        float headYaw = 180 * lift + Mth.sin(age * 0.05f) * 8 * lift;
        float pitch = -18 * lift;
        JarRenderer.draw(pose, buffers, light, OverlayTexture.NO_OVERLAY, player.getSkin().texture(), headYaw, pitch, age, true, !state.goggles().isEmpty());
    }

    /** The middle of a hand (2 px in from the end of the arm; side -1 right, 1 left), in model space (blocks). */
    private static Vector3f grip(ModelPart arm, float side) {
        PoseStack at = new PoseStack();
        arm.translateAndRotate(at);
        return at.last().pose().transformPosition(new Vector3f(side / 16f, 9 / 16f, 0));
    }

    /**
     * Replaces the whole first-person hand pass while the jar is in your arms (so no other mod adds hands of its own), with
     * the same view sway the vanilla pass applies. Returns false when not carrying, to let the normal pass run.
     */
    public static boolean drawFirstPersonPass(PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer player, int light, float partialTick) {
        if (player == null || !inArms(player)) return false;
        pose.pushPose();
        float xBob = Mth.lerp(partialTick, player.xBobO, player.xBob);
        float yBob = Mth.lerp(partialTick, player.yBobO, player.yBob);
        pose.mulPose(Axis.XP.rotationDegrees((player.getViewXRot(partialTick) - xBob) * 0.1f));
        pose.mulPose(Axis.YP.rotationDegrees((player.getViewYRot(partialTick) - yBob) * 0.1f));
        drawFirstPerson(pose, buffers, light, partialTick, 0);
        pose.popPose();
        buffers.endBatch();
        return true;
    }

    /** Set while the first-person hands render, whose setupAnim call must not pick up the third-person carry pose. */
    private static boolean drawingHands;

    /** Jar tipped toward you in first person (degrees about X, round its middle). */
    private static final float FP_TILT = -40;
    /** Where each hand grips the jar, in the jar's own frame (blocks from the middle of its base): the upper sides. */
    private static final float GRIP_X = 0.65f, GRIP_Y = 0.7f, GRIP_Z = 0.02f;
    /** Your shoulders, from the camera (blocks): the arms run from here to the grip. */
    private static final float SHOULDER_X = 0.42f, SHOULDER_Y = -0.45f, SHOULDER_Z = 0.3f;

    /**
     * First person, life size: the jar sits low in front of you, tipped toward you so you see your head inside, with a hand
     * on each side. The arms run from your shoulders to the jar's sides, so they stay in view at any field of view. It rises
     * into view as you lift it. Returns whether it drew (so the vanilla hands are skipped).
     */
    public static boolean drawFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, float partialTick, float equip) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !inArms(mc.player)) return false;
        AbstractClientPlayer player = mc.player;
        float age = player.tickCount + partialTick;
        float lift = lift(player, age);
        float walk = Mth.lerp(partialTick, player.walkDistO, player.walkDist);
        float amount = Mth.clamp(Mth.lerp(partialTick, player.oBob, player.bob) * 6, 0, 1);
        // camera (the Collar Cam) to the jar's base, in blocks: held a little further out and lower than in third person
        float eye = player.getEyeHeight();
        float baseY = 1.5f - FRONT_Y / 16f - eye - 0.04f;
        float baseZ = FRONT_Z / 16f - 0.55f;
        pose.pushPose();
        pose.translate(Mth.sin(walk * Mth.PI) * 0.02f * amount, -Math.abs(Mth.cos(walk * Mth.PI)) * 0.025f * amount - 0.9f * (1 - lift) - 0.2f * equip, 0);
        // the hands: each grip point on the tipped jar, reached from the shoulder on that side
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        float cos = Mth.cos(FP_TILT * Mth.DEG_TO_RAD), sin = Mth.sin(FP_TILT * Mth.DEG_TO_RAD);
        drawingHands = true;
        try {
            for (int side = 0; side < 2; side++) {
                float f = side == 0 ? 1 : -1;
                float ry = GRIP_Y - 0.5f, rz = GRIP_Z;
                Vector3f grip = new Vector3f(f * GRIP_X, baseY + 0.5f + ry * cos - rz * sin, baseZ + ry * sin + rz * cos);
                Vector3f along = new Vector3f(grip).sub(f * SHOULDER_X, SHOULDER_Y, SHOULDER_Z).normalize();
                pose.pushPose();
                pose.translate(grip.x, grip.y, grip.z);
                // the arm model runs down its +Y from the shoulder: point that along shoulder-to-grip, palm turned in to the glass
                pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, along.x, along.y, along.z));
                pose.mulPose(Axis.YP.rotationDegrees(f * 90));
                // the hand (10 px down the arm, 1 px out from its middle) lands on the grip
                pose.translate(f * 6 / 16f, -10 / 16f, 0);
                if (side == 0) renderer.renderRightHand(pose, buffers, light, player);
                else renderer.renderLeftHand(pose, buffers, light, player);
                pose.popPose();
            }
        } finally {
            drawingHands = false;
        }
        // the jar, life size, your head turned round to look up at you
        pose.translate(0, baseY, baseZ);
        pose.translate(0, 0.5f, 0);
        pose.mulPose(Axis.XP.rotationDegrees(FP_TILT));
        pose.translate(0, -0.5f, 0);
        var state = player.getData(ModRegistry.JAR_STATE);
        JarRenderer.draw(pose, buffers, light, OverlayTexture.NO_OVERLAY, player.getSkin().texture(), 180 + Mth.sin(age * 0.05f) * 6, -18, age, true,
                !state.goggles().isEmpty());
        pose.popPose();
        return true;
    }
}
