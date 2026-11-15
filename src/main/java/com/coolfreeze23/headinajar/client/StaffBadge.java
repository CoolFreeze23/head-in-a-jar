package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The staff badge a block wears for the head (or heads) working in it: a steel-clipped frame and, in its hole, a face in
 * the employer's colour. Two flat quads on the block's front, lit by the block's light. Drawn in the block's model space
 * (px from its bottom centre, y up, the front at -Z), turned with the block as its model is.
 */
public final class StaffBadge {
    public static final ResourceLocation TEXTURE = HeadInAJar.id("textures/block/staff_badge.png");

    /** Where a block wears it: the frame's rectangle and the face's (px: x0, y0, x1, y1) and the plane they lie in (z). */
    public record Anchor(float x0, float y0, float x1, float y1, float fx0, float fy0, float fx1, float fy1, float z) {
    }

    /** On the plinth: clipped over the plaque's right-hand end. */
    public static final Anchor PLINTH = new Anchor(-7, 1, -2, 6, -6, 2, -3, 4, -8.05f);
    /** On the Turret Pedestal's base drum, under the turntable's lip, right of the barrel. */
    public static final Anchor TURRET = new Anchor(-5, 3, 0, 8, -4, 4, -1, 6, -6.05f);
    /** On the Cerebral Array's lowest front rail, in front of the middle seat (one for the whole Board). */
    public static final Anchor ARRAY = new Anchor(-2, 1, 3, 6, -1, 2, 2, 4, -9.05f);
    /** On the Employee Mount's desk edge, right of the nameplate, under the pay tray. */
    public static final Anchor MOUNT = new Anchor(-8, 1, -3, 6, -7, 2, -4, 4, -8.05f);
    /** On the Neural Socket's foot, under the level slot. */
    public static final Anchor SOCKET = new Anchor(-5, 0, 0, 5, -4, 1, -1, 3, -7.05f);

    /** The sheet's frame (untinted) and face (tinted) regions, in sheet px of 16. */
    private static final float FRAME_U0 = 0, FRAME_V0 = 0, FRAME_U1 = 5, FRAME_V1 = 5, FACE_U0 = 6, FACE_V0 = 0, FACE_U1 = 9, FACE_V1 = 2;

    private StaffBadge() {
    }

    /** Whether this player wants badges drawn (the client option {@code lanyards}). */
    public static boolean shown() {
        return !com.coolfreeze23.headinajar.Config.CLIENT_SPEC.isLoaded() || com.coolfreeze23.headinajar.Config.LANYARDS.get();
    }

    /** Draws the badge in {@code colour} (0xRRGGBB; nothing for -1, or with badges switched off) at {@code at}. */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int light, int colour, Anchor at) {
        if (colour < 0 || !shown()) return;
        VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose m = pose.last();
        quad(v, m, at.x0(), at.y0(), at.x1(), at.y1(), at.z(), FRAME_U0, FRAME_V0, FRAME_U1, FRAME_V1, 0xFFFFFFFF, light);
        // the face a hair proud of the frame's plane, so the two never fight
        quad(v, m, at.fx0(), at.fy0(), at.fx1(), at.fy1(), at.z() - 0.01f, FACE_U0, FACE_V0, FACE_U1, FACE_V1, 0xFF000000 | colour, light);
    }

    /** One quad facing -Z; seen from the front, the sheet's left column at the larger x and its top row at y1. */
    private static void quad(VertexConsumer v, PoseStack.Pose m, float x0, float y0, float x1, float y1, float z,
                             float u0, float v0, float u1, float v1, int argb, int light) {
        float s = 1 / 16f;
        vertex(v, m, x1 * s, y1 * s, z * s, u0 * s, v0 * s, argb, light);
        vertex(v, m, x1 * s, y0 * s, z * s, u0 * s, v1 * s, argb, light);
        vertex(v, m, x0 * s, y0 * s, z * s, u1 * s, v1 * s, argb, light);
        vertex(v, m, x0 * s, y1 * s, z * s, u1 * s, v0 * s, argb, light);
    }

    private static void vertex(VertexConsumer v, PoseStack.Pose m, float x, float y, float z, float u, float vv, int argb, int light) {
        v.addVertex(m, x, y, z).setColor(argb).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(m, 0, 0, -1);
    }
}
