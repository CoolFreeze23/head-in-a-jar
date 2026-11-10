package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.JarState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/** Draws a life-support jar: frame, a head (any player's skin) floating in fluid, optional Relay Goggles, then the glass. */
public final class JarRenderer {
    public static final ResourceLocation TEXTURE = HeadInAJar.id("textures/entity/jar.png");
    public static final ResourceLocation GOGGLES_TEXTURE = HeadInAJar.id("textures/entity/relay_goggles.png");
    private static JarModel.Parts jar;
    private static ModelPart head;
    private static ModelPart hat;
    private static ModelPart goggles;
    /** The head inside is life size. */
    public static final float HEAD_SCALE = 1.0f;
    /**
     * The two jars. Sizes scale the 11 px model around its base. Turn and nod are how far the head may move inside the glass;
     * any more turn turns the whole jar, the way a head turns on a neck. The last three place the jar tipped on its side.
     */
    public record Shape(float wide, float tall, float neck, float turn, float nod, float rollLift, float rollSpin, float rollDrop) {
    }

    /**
     * The wide jars (Life Support, Reinforced, Executive): 1.3x wide and 1.1x tall, so the life-size head (8.8 px with the hat
     * layer) turns right round inside: its hat corner is 6.2 px from the axis and the goggle lenses 5.8, inside 6.5 px of glass.
     */
    public static final Shape WIDE = new Shape(1.3f, 1.1f, 22.5f, 180, 20, 0.45f, 127, 0.48f);
    /**
     * The Compact Jar, at the model's own size (10 px of glass): the head fits facing the front, turns 6 degrees and nods 3
     * inside it, and the jar turns with the rest.
     */
    public static final Shape COMPACT = new Shape(1.0f, 1.0f, 22.8f, 6, 3, 0.34f, 168, 0.41f);

    public static Shape shape(boolean compact) {
        return compact ? COMPACT : WIDE;
    }

    private static void bake() {
        var models = Minecraft.getInstance().getEntityModels();
        jar = JarModel.Parts.of(models.bakeLayer(JarModel.JAR));
        ModelPart root = models.bakeLayer(JarModel.HEAD);
        head = root.getChild("head");
        hat = root.getChild("hat");
        goggles = models.bakeLayer(JarModel.GOGGLES).getChild("goggles");
    }

    /** Clears the baked parts after a resource reload. */
    public static void reset() {
        jar = null;
        wheelParts = null;
    }

    /** A player's skin by id: their live skin if they are online, else the default for that id. */
    public static ResourceLocation skin(UUID owner) {
        if (owner == null) return DefaultPlayerSkin.getDefaultTexture();
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkin().texture() : DefaultPlayerSkin.get(owner).texture();
    }

    /** A player in jar form. */
    public static void render(AbstractClientPlayer player, JarState state, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        // on a host the jar sits square on its neck, facing the way its body faces; the head inside looks where you look
        if (state.form() == com.coolfreeze23.headinajar.form.Form.HOSTED && player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity host) {
            bodyYaw = Mth.rotLerp(partialTick, host.yBodyRotO, host.yBodyRot);
        }
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float pitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
        float walked = Mth.lerp(partialTick, player.walkDistO, player.walkDist) / 0.6f;
        boolean wheels = state.wheels() && state.fitted(com.coolfreeze23.headinajar.module.ModuleType.WHEELS);
        Shape shape = shape(state.compact());
        if (state.tipped()) {
            // on its side, rolling along: the jar's axis lies across the direction of travel
            pose.translate(0, shape.rollLift(), 0);
            pose.mulPose(Axis.XP.rotationDegrees(walked * shape.rollSpin()));
            pose.mulPose(Axis.ZP.rotationDegrees(90));
            pose.translate(0, -shape.rollDrop(), 0);
        } else if (wheels) {
            pose.translate(0, 3.5f / 16f, 0);
            drawWheels(pose, buffers, light, walked, shape);
        }
        draw(pose, buffers, light, LivingEntityRenderer.getOverlayCoords(player, 0), player.getSkin().texture(),
                headYaw - bodyYaw, pitch, player.tickCount + partialTick, state.charge() > 0, !state.goggles().isEmpty(), shape);
        var helmet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (state.fitted(com.coolfreeze23.headinajar.module.ModuleType.HAT_MOUNT) && !helmet.isEmpty()) {
            pose.pushPose();
            pose.translate(0, 0.98, 0);
            pose.scale(0.62f, 0.62f, 0.62f);
            Minecraft.getInstance().getItemRenderer().renderStatic(helmet, net.minecraft.world.item.ItemDisplayContext.HEAD, light,
                    OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
            pose.popPose();
        }
        pose.popPose();
        String speech = state.speech();
        if (!speech.isEmpty()) bubble(pose, buffers, light, speech);
    }

    /**
     * Draws a jar facing the pose's -Z with its base at the origin.
     *
     * @param headYaw head turn relative to the jar, in degrees
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ResourceLocation skin,
                            float headYaw, float pitch, float age, boolean charged, boolean withGoggles) {
        draw(pose, buffers, light, overlay, skin, headYaw, pitch, age, charged, withGoggles, WIDE);
    }

    /** As above, in the given jar. */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ResourceLocation skin,
                            float headYaw, float pitch, float age, boolean charged, boolean withGoggles, Shape shape) {
        draw(pose, buffers, light, overlay, skin, headYaw, pitch, age, charged, withGoggles, shape, false);
    }

    /** As above; {@code upsideDown} turns the head over about its own centre (a botched cut), nothing else changes. */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ResourceLocation skin,
                            float headYaw, float pitch, float age, boolean charged, boolean withGoggles, Shape shape, boolean upsideDown) {
        if (jar == null) bake();
        // the head turns as far as the glass lets it; the rest of the turn turns the jar
        float turn = Mth.wrapDegrees(headYaw);
        float inside = Mth.clamp(turn, -shape.turn(), shape.turn());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-(turn - inside)));
        pose.pushPose();
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);
        scaled(pose, shape);
        jar.frame().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay);
        pose.popPose();

        // the head bobs a little in the fluid and looks where it is told
        float bob = Mth.sin(age * 0.08f) * 0.35f;
        float yaw = inside * Mth.DEG_TO_RAD;
        float xRot = Mth.clamp(pitch, -shape.nod(), shape.nod()) * Mth.DEG_TO_RAD;
        for (ModelPart part : new ModelPart[]{head, hat, goggles}) {
            // upside down: turned half over about x through the head's centre, so it fills the same space
            part.y = shape.neck() + bob - (upsideDown ? 8 * HEAD_SCALE : 0);
            part.yRot = yaw;
            part.xRot = xRot + (upsideDown ? Mth.PI : 0);
            part.xScale = part.yScale = part.zScale = HEAD_SCALE;
        }
        head.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, overlay);
        hat.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, overlay);
        if (withGoggles) goggles.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(GOGGLES_TEXTURE)), light, overlay);

        int fluid = charged ? 0xFFFFFFFF : 0xFF707070;
        scaled(pose, shape);
        jar.fluid().render(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, overlay, fluid);
        jar.glass().render(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, overlay);
        pose.popPose();
        pose.popPose();
        pose.popPose();
    }

    /** Pushes the jar's size onto the pose, around the base underside (y = 24 px in jar model space). Pop it after. */
    private static void scaled(PoseStack pose, Shape shape) {
        pose.pushPose();
        pose.translate(0, 1.5f, 0);
        pose.scale(shape.wide(), shape.tall(), shape.wide());
        pose.translate(0, -1.5f, 0);
    }

    private static ModelPart wheelParts;
    private static final ResourceLocation WHEELS_TEXTURE = HeadInAJar.id("textures/entity/jar_wheels.png");

    /** The Wheels module under the base plate, turning with the distance driven. */
    private static void drawWheels(PoseStack pose, MultiBufferSource buffers, int light, float walked, Shape shape) {
        if (wheelParts == null) wheelParts = Minecraft.getInstance().getEntityModels().bakeLayer(JarModel.WHEELS);
        pose.pushPose();
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);
        pose.translate(0, 1.5f, 0);
        pose.scale(shape.wide(), 1, shape.wide());
        pose.translate(0, -1.5f, 0);
        for (int i = 0; i < 4; i++) wheelParts.getChild("wheel_" + i).xRot = walked * 3.8f;
        wheelParts.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(WHEELS_TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** The Speaker Grille's words, floating over the jar like a name tag. */
    private static void bubble(PoseStack pose, MultiBufferSource buffers, int light, String text) {
        var mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(0, 1.2, 0);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.022f, -0.022f, 0.022f);
        var font = mc.font;
        float x = -font.width(text) / 2f;
        font.drawInBatch(text, x, 0, 0xFFFFFFFF, false, pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0x80000000, light);
        pose.popPose();
    }

    /** A jar with something else inside: {@code inside} draws in jar model space and gets the bob offset. */
    public static void drawWith(PoseStack pose, MultiBufferSource buffers, int light, float age, java.util.function.BiConsumer<PoseStack, Float> inside) {
        if (jar == null) bake();
        int overlay = OverlayTexture.NO_OVERLAY;
        pose.pushPose();
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);
        scaled(pose, WIDE);
        jar.frame().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay);
        pose.popPose();
        inside.accept(pose, Mth.sin(age * 0.08f) * 0.35f);
        scaled(pose, WIDE);
        jar.fluid().render(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, overlay);
        jar.glass().render(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, overlay);
        pose.popPose();
        pose.popPose();
        pose.popPose();
    }

    public static void drawStill(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation skin, float age, boolean withGoggles) {
        draw(pose, buffers, light, OverlayTexture.NO_OVERLAY, skin, 0, 0, age, true, withGoggles);
    }

    /** True when this entity should be drawn as a jar. */
    public static boolean isJar(LivingEntity entity, JarState state) {
        return state.form().isJarred();
    }

    private JarRenderer() {
    }
}
