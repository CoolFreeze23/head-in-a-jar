package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * A mob's face at a specimen jar's own pixel size: the front of its head part, read from its texture and averaged
 * down to {@link #SIZE} x {@link #SIZE} texels, so a 3 px face is three real texels wide (nothing scaled below the
 * block's own pixel). Made once per texture, kept as a small texture of its own.
 */
public final class HeadFaces {
    /** Texels across (and pixels drawn): the jar's glass is 4 px, the face sits inside it. */
    public static final int SIZE = 3;
    /** The face as read ({@link #OPEN}), with its eye row shut ({@link #SHUT}), and with its eyes turned aside ({@link #ASIDE}). */
    public static final int OPEN = 0, SHUT = 1, ASIDE = 2;
    private static final Map<ResourceLocation, ResourceLocation[]> FACES = new HashMap<>();
    private static final ResourceLocation[] NONE = new ResourceLocation[3];
    private static int made;

    /** The small texture of {@code part}'s front face on {@code texture} (null if it can't be read). */
    public static ResourceLocation face(ModelPart part, ResourceLocation texture) {
        return face(part, texture, OPEN);
    }

    /** The face, or one of its two living variants: the middle (eye) row given the brow's colours, or shifted a texel. */
    public static ResourceLocation face(ModelPart part, ResourceLocation texture, int variant) {
        return FACES.computeIfAbsent(texture, t -> make(part, t))[variant];
    }

    private static ResourceLocation register(NativeImage image) {
        ResourceLocation id = HeadInAJar.id("dynamic/head_face_" + made++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        return id;
    }

    private static ResourceLocation[] make(ModelPart part, ResourceLocation texture) {
        float[] uv = frontUv(part);
        if (uv == null) return NONE;
        var mc = Minecraft.getInstance();
        try (var in = mc.getResourceManager().open(texture); NativeImage src = NativeImage.read(in)) {
            int w = src.getWidth(), h = src.getHeight();
            double x0 = uv[0] * w, y0 = uv[1] * h, x1 = uv[2] * w, y1 = uv[3] * h;
            NativeImage out = new NativeImage(SIZE, SIZE, true);
            for (int j = 0; j < SIZE; j++) {
                for (int i = 0; i < SIZE; i++) {
                    // the area this texel covers, averaged (texel weights by overlap)
                    double ax = x0 + (x1 - x0) * i / SIZE, bx = x0 + (x1 - x0) * (i + 1) / SIZE;
                    double ay = y0 + (y1 - y0) * j / SIZE, by = y0 + (y1 - y0) * (j + 1) / SIZE;
                    double r = 0, g = 0, b = 0, a = 0, total = 0;
                    for (int y = (int) Math.floor(ay); y < Math.ceil(by); y++) {
                        for (int x = (int) Math.floor(ax); x < Math.ceil(bx); x++) {
                            double wx = Math.min(bx, x + 1) - Math.max(ax, x), wy = Math.min(by, y + 1) - Math.max(ay, y);
                            double weight = wx * wy;
                            if (weight <= 0 || x < 0 || y < 0 || x >= w || y >= h) continue;
                            int abgr = src.getPixelRGBA(x, y);
                            double alpha = (abgr >>> 24) / 255.0;
                            r += (abgr & 0xFF) * alpha * weight;
                            g += (abgr >> 8 & 0xFF) * alpha * weight;
                            b += (abgr >> 16 & 0xFF) * alpha * weight;
                            a += alpha * weight;
                            total += weight;
                        }
                    }
                    int pixel = 0;
                    if (a > 0) {
                        pixel = 0xFF000000 | (int) Math.round(b / a) << 16 | (int) Math.round(g / a) << 8 | (int) Math.round(r / a);
                    }
                    out.setPixelRGBA(i, j, total > 0 && a / total >= 0.5 ? pixel : 0);
                }
            }
            NativeImage shut = new NativeImage(SIZE, SIZE, true), aside = new NativeImage(SIZE, SIZE, true);
            shut.copyFrom(out);
            aside.copyFrom(out);
            for (int i = 0; i < SIZE; i++) {
                // a blink: the eye row takes the brow's colours; a glance: the eye row moved a texel to the side
                if (out.getPixelRGBA(i, 0) != 0) shut.setPixelRGBA(i, 1, out.getPixelRGBA(i, 0));
                aside.setPixelRGBA(i, 1, out.getPixelRGBA((i + 1) % SIZE, 1));
            }
            return new ResourceLocation[]{register(out), register(shut), register(aside)};
        } catch (Exception e) {
            return NONE;
        }
    }

    /** The UV box (0-1) of the head's face: of the quads it draws, the one toward -Z (the face) with the largest area. */
    private static float[] frontUv(ModelPart part) {
        RestBake.Capture capture = new RestBake.Capture();
        var saved = part.storePose();
        part.setPos(0, 0, 0);
        part.setRotation(0, 0, 0);
        part.render(new com.mojang.blaze3d.vertex.PoseStack(), capture, 0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        part.loadPose(saved);
        float[] best = null;
        double bestArea = 0;
        for (float[] q : capture.quads) {
            if (q[8] > -0.9f) continue;
            float u0 = 1, v0 = 1, u1 = 0, v1 = 0;
            for (int k = 0; k < 4; k++) {
                u0 = Math.min(u0, q[k * 9 + 4]);
                u1 = Math.max(u1, q[k * 9 + 4]);
                v0 = Math.min(v0, q[k * 9 + 5]);
                v1 = Math.max(v1, q[k * 9 + 5]);
            }
            double area = (u1 - u0) * (v1 - v0);
            if (area > bestArea) {
                bestArea = area;
                best = new float[]{u0, v0, u1, v1};
            }
        }
        return best;
    }

    /** Leaving a world drops nothing: the faces belong to textures, not to the world. */
    private HeadFaces() {
    }
}
