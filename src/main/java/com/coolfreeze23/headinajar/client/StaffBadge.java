package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

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
    /**
     * The frame by grade ([u0, v0, u1, v1) px of 16, at the same places on this sheet and the worn lanyard's): an Intern's
     * is the plain frame, an Associate's has a brass stripe across the card's foot, a Senior Associate's a second across
     * its top, a Team Lead's the whole edge brass. Then the Team Lead's tie: its column (knot at v 0, blade below, one
     * texel a px) and its broad end. All untinted.
     */
    public static final float[][] FRAMES = {{0, 0, 5, 5}, {0, 10, 5, 15}, {5, 10, 10, 15}, {7, 5, 12, 10}};
    public static final float[] TIE = {13, 0, 14, 16}, TIE_TIP = {10, 13, 13, 15};
    /** Whether this sheet has the grades and the tie painted (checked once a resource load; without them every grade wears the plain frame). */
    public static boolean graded;
    /** The tie on a collar: its blade's half width and its broad end's, and how much nearer the broad end lies than the blade (px). */
    private static final float BLADE_HALF = 0.5f, TIP_HALF = 1.5f, TIP_H = 2, TIP_NEARER = 0.004f, TIE_BEHIND = 0.02f;

    private StaffBadge() {
    }

    /** Checks this sheet for the grade frames and the tie (a resource load). */
    public static void check(ResourceManager manager) {
        graded = painted(manager, TEXTURE, new float[][]{FRAMES[1], FRAMES[2], FRAMES[3], TIE, TIE_TIP})[0];
    }

    /**
     * For each group of regions ([u0, v0, u1, v1) px of 16, scaled to the sheet's size), whether every region in it has
     * an opaque pixel on the sheet as loaded now: a pack's older sheet without them answers false.
     */
    public static boolean[] painted(ResourceManager manager, ResourceLocation sheet, float[][]... groups) {
        boolean[] out = new boolean[groups.length];
        Optional<Resource> res = manager.getResource(sheet);
        if (res.isEmpty()) return out;
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            if (img.format() != NativeImage.Format.RGBA) return out;
            float k = img.getWidth() / 16f;
            for (int g = 0; g < groups.length; g++) {
                boolean all = true;
                for (float[] r : groups[g]) all &= opaque(img, r, k);
                out[g] = all;
            }
        } catch (IOException | RuntimeException e) {
            HeadInAJar.LOGGER.debug("Staff badge sheet {} could not be read: {}", sheet, e.toString());
        }
        return out;
    }

    private static boolean opaque(NativeImage img, float[] r, float k) {
        int x1 = Math.min(img.getWidth(), (int) Math.ceil(r[2] * k)), y1 = Math.min(img.getHeight(), (int) Math.ceil(r[3] * k));
        for (int x = (int) (r[0] * k); x < x1; x++) {
            for (int y = (int) (r[1] * k); y < y1; y++) if ((img.getPixelRGBA(x, y) >>> 24) != 0) return true;
        }
        return false;
    }

    /** Whether this player wants badges drawn (the client option {@code lanyards}). */
    public static boolean shown() {
        return !com.coolfreeze23.headinajar.Config.CLIENT_SPEC.isLoaded() || com.coolfreeze23.headinajar.Config.LANYARDS.get();
    }

    /** Draws the badge in {@code colour} (0xRRGGBB; nothing for -1, or with badges switched off) at {@code at}. */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int light, int colour, Anchor at) {
        draw(pose, buffers, light, colour, 0, at);
    }

    /** The same, the frame showing the wearer's grade (0 Intern .. 3 Team Lead) where the sheet has the grades. */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int light, int colour, int grade, Anchor at) {
        if (colour < 0 || !shown()) return;
        VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose m = pose.last();
        if (grade > 0 && graded) {
            float[] f = FRAMES[Math.min(grade, FRAMES.length - 1)];
            quad(v, m, at.x0(), at.y0(), at.x1(), at.y1(), at.z(), f[0], f[1], f[2], f[3], 0xFFFFFFFF, light);
        } else {
            quad(v, m, at.x0(), at.y0(), at.x1(), at.y1(), at.z(), FRAME_U0, FRAME_V0, FRAME_U1, FRAME_V1, 0xFFFFFFFF, light);
        }
        // the face a hair proud of the frame's plane, so the two never fight
        quad(v, m, at.fx0(), at.fy0(), at.fx1(), at.fy1(), at.z() - 0.01f, FACE_U0, FACE_V0, FACE_U1, FACE_V1, 0xFF000000 | colour, light);
    }

    /**
     * A Team Lead's tie under a badge clipped to a collar at {@code at}: the blade from the frame's top down behind the
     * card, its broad end just under the card (nothing on a sheet without the tie, or with badges switched off).
     */
    public static void tie(PoseStack pose, MultiBufferSource buffers, int light, Anchor at) {
        if (!graded || !shown()) return;
        VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose m = pose.last();
        float cx = (at.x0() + at.x1()) / 2, z = at.z() + TIE_BEHIND;
        quad(v, m, cx - BLADE_HALF, at.y0(), cx + BLADE_HALF, at.y1(), z, TIE[0], TIE[1], TIE[2], TIE[1] + (at.y1() - at.y0()), 0xFFFFFFFF, light);
        quad(v, m, cx - TIP_HALF, at.y0() - TIP_H, cx + TIP_HALF, at.y0(), z - TIP_NEARER, TIE_TIP[0], TIE_TIP[1], TIE_TIP[2], TIE_TIP[3], 0xFFFFFFFF, light);
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
