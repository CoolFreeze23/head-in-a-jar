package com.coolfreeze23.headinajar.client.fx;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The two beam styles, drawn on the client between points resolved every frame (so both ends stay glued to things that
 * move), with vanilla render types only:
 * <ul>
 * <li><b>ribbon</b> ("carrier wave"): a camera-facing strip with a scrolling texture in two layers, an alpha-blended
 * outer sheath in the ramp's dark step so it reads against a bright sky ({@code beaconBeam(tex, true)}) and an additive
 * pale core so it glows at night ({@code eyes(tex)});</li>
 * <li><b>arc</b>: a jagged polyline re-rolled from a seed, a coloured fringe and a pale core ({@code lightning()}).</li>
 * </ul>
 * Every beam is at least {@link #MIN_WIDTH_PER_BLOCK} x its distance wide (about a pixel at 70 degrees on 1080p), so it
 * still reads at 30 blocks; anything within 1.3 blocks of the camera fades out, so a beam that starts at the doer's own
 * eyes never covers the screen; and each beam is culled by its own box, so a long beam is not lost when one end is
 * off screen.
 */
public final class Beams {
    public static final ResourceLocation RIBBON = HeadInAJar.id("textures/fx/beam_ribbon.png");
    public static final float MIN_WIDTH_PER_BLOCK = 0.0035f;
    /** Nothing is drawn nearer the camera than this; it fades in over the next 1.0 blocks. */
    private static final float NEAR = 0.3f, FADE = 1.0f;

    /** One frame's drawing context: camera-relative coordinates, the world's buffers and frustum. */
    public record Frame(PoseStack pose, MultiBufferSource buffers, Vec3 camera, Frustum frustum, float partialTick, float time) {
        public boolean visible(Vec3... points) {
            if (points.length == 0) return false;
            double x0 = points[0].x, y0 = points[0].y, z0 = points[0].z, x1 = x0, y1 = y0, z1 = z0;
            for (Vec3 p : points) {
                x0 = Math.min(x0, p.x);
                y0 = Math.min(y0, p.y);
                z0 = Math.min(z0, p.z);
                x1 = Math.max(x1, p.x);
                y1 = Math.max(y1, p.y);
                z1 = Math.max(z1, p.z);
            }
            if (camera.distanceToSqr((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2) > 160 * 160) return false;
            return frustum == null || frustum.isVisible(new AABB(x0, y0, z0, x1, y1, z1).inflate(0.5));
        }
    }

    /** A ribbon's look. Colours are 0xRRGGBB; widths in blocks at each end. */
    public record Ribbon(int sheath, int core, float width0, float width1, float alpha, float scroll) {
    }

    /**
     * Draws a ribbon through {@code points}. {@code fade} (0..1 along the beam, or null) multiplies the alpha per point:
     * how a tether is drawn while it shoots out, snaps or fades.
     */
    /** Beams asked for and beams drawn since the last call to {@link #counts()} (for the harness). */
    private static int asked, drawn;

    public static String counts() {
        String s = drawn + "/" + asked;
        asked = drawn = 0;
        return s;
    }

    public static void ribbon(Frame f, Vec3[] points, Ribbon look, java.util.function.DoubleUnaryOperator fade) {
        asked++;
        if (points.length < 2 || look.alpha <= 0.01f || !f.visible(points)) return;
        drawn++;
        float length = 0;
        float[] along = new float[points.length];
        for (int i = 1; i < points.length; i++) along[i] = length += (float) points[i].distanceTo(points[i - 1]);
        if (length < 1e-3f) return;
        Matrix4f mat = f.pose.last().pose();
        PoseStack.Pose normal = f.pose.last();
        VertexConsumer sheath = f.buffers.getBuffer(RenderType.beaconBeam(RIBBON, true));
        strip(f, sheath, mat, normal, points, along, length, look, fade, 2.2f, look.sheath, 0.42f * look.alpha, false);
        VertexConsumer core = f.buffers.getBuffer(RenderType.eyes(RIBBON));
        strip(f, core, mat, normal, points, along, length, look, fade, 1f, look.core, look.alpha, true);
    }

    private static void strip(Frame f, VertexConsumer b, Matrix4f mat, PoseStack.Pose normal, Vec3[] pts, float[] along, float length,
                              Ribbon look, java.util.function.DoubleUnaryOperator fade, float widen, int rgb, float alpha, boolean additive) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, bl = (rgb & 255) / 255f;
        Vector3f[] side = new Vector3f[pts.length];
        float[] a = new float[pts.length];
        for (int i = 0; i < pts.length; i++) {
            Vec3 tangent = pts[Math.min(i + 1, pts.length - 1)].subtract(pts[Math.max(i - 1, 0)]);
            Vec3 rel = pts[i].subtract(f.camera);
            double dist = rel.length();
            Vec3 s = tangent.cross(rel);
            if (s.lengthSqr() < 1e-9) s = new Vec3(0, 1, 0);
            float t = along[i] / length;
            float w = Math.max(Mth.lerp(t, look.width0, look.width1) * widen, (float) dist * MIN_WIDTH_PER_BLOCK * widen);
            s = s.normalize().scale(w / 2);
            side[i] = new Vector3f((float) s.x, (float) s.y, (float) s.z);
            float near = Mth.clamp(((float) dist - NEAR) / FADE, 0, 1);
            a[i] = alpha * near * (fade == null ? 1 : (float) Mth.clamp(fade.applyAsDouble(t), 0, 1));
        }
        float vScale = 0.5f; // the 16x32 strip spans 2 blocks
        for (int i = 0; i + 1 < pts.length; i++) {
            if (a[i] <= 0.004f && a[i + 1] <= 0.004f) continue;
            Vec3 p0 = pts[i].subtract(f.camera), p1 = pts[i + 1].subtract(f.camera);
            float v0 = along[i] * vScale - look.scroll, v1 = along[i + 1] * vScale - look.scroll;
            // both windings: these render types cull back faces, and which side faces the camera varies
            vertex(b, mat, normal, p0, side[i], -1, 0, v0, r, g, bl, a[i], additive);
            vertex(b, mat, normal, p0, side[i], 1, 1, v0, r, g, bl, a[i], additive);
            vertex(b, mat, normal, p1, side[i + 1], 1, 1, v1, r, g, bl, a[i + 1], additive);
            vertex(b, mat, normal, p1, side[i + 1], -1, 0, v1, r, g, bl, a[i + 1], additive);
            vertex(b, mat, normal, p1, side[i + 1], -1, 0, v1, r, g, bl, a[i + 1], additive);
            vertex(b, mat, normal, p1, side[i + 1], 1, 1, v1, r, g, bl, a[i + 1], additive);
            vertex(b, mat, normal, p0, side[i], 1, 1, v0, r, g, bl, a[i], additive);
            vertex(b, mat, normal, p0, side[i], -1, 0, v0, r, g, bl, a[i], additive);
        }
    }

    private static void vertex(VertexConsumer b, Matrix4f mat, PoseStack.Pose normal, Vec3 p, Vector3f side, float sign, float u, float v,
                               float r, float g, float bl, float a, boolean additive) {
        // the core is drawn additively, where alpha does nothing: the fade goes into the colour instead
        float k = additive ? a : 1;
        b.addVertex(mat, (float) p.x + side.x * sign, (float) p.y + side.y * sign, (float) p.z + side.z * sign)
                .setColor(r * k, g * k, bl * k, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(normal, 0, 1, 0);
    }

    /** A jagged path from {@code a} to {@code b}: {@code n} segments, offsets up to {@code jitter} blocks, the same for one seed. */
    public static Vec3[] arcPath(Vec3 a, Vec3 b, int n, long seed, float jitter, Vec3 pullToward) {
        RandomSource random = RandomSource.create(seed);
        Vec3 d = b.subtract(a);
        Vec3 p1 = d.cross(new Vec3(0, 1, 0));
        if (p1.lengthSqr() < 1e-6) p1 = d.cross(new Vec3(1, 0, 0));
        p1 = p1.normalize();
        Vec3 p2 = d.cross(p1).normalize();
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double env = Math.sin(Math.PI * t);
            Vec3 p = a.add(d.scale(t));
            if (i > 0 && i < n) {
                p = p.add(p1.scale((random.nextFloat() * 2 - 1) * jitter * env)).add(p2.scale((random.nextFloat() * 2 - 1) * jitter * env));
                if (pullToward != null) p = p.add(pullToward.subtract(p).scale(0.5 * env));
            }
            pts[i] = p;
        }
        return pts;
    }

    /** Draws an arc along {@code pts}: a fringe of {@code rgb} and a pale core; {@code width} in blocks. */
    public static void arc(Frame f, Vec3[] pts, int rgb, int coreRgb, float width, float alpha) {
        asked++;
        if (pts.length < 2 || alpha <= 0.01f || !f.visible(pts)) return;
        drawn++;
        VertexConsumer b = f.buffers.getBuffer(RenderType.lightning());
        Matrix4f mat = f.pose.last().pose();
        segments(f, b, mat, pts, rgb, width * 2.4f, alpha * 0.55f);
        segments(f, b, mat, pts, coreRgb, width, alpha);
    }

    private static void segments(Frame f, VertexConsumer b, Matrix4f mat, Vec3[] pts, int rgb, float width, float alpha) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, bl = (rgb & 255) / 255f;
        for (int i = 0; i + 1 < pts.length; i++) {
            Vec3 p0 = pts[i].subtract(f.camera), p1 = pts[i + 1].subtract(f.camera);
            Vec3 mid = p0.add(p1).scale(0.5);
            double dist = mid.length();
            float near = Mth.clamp(((float) dist - NEAR) / FADE, 0, 1);
            if (near <= 0) continue;
            float w = Math.max(width, (float) dist * MIN_WIDTH_PER_BLOCK * 1.5f);
            Vec3 s = p1.subtract(p0).cross(mid);
            if (s.lengthSqr() < 1e-9) continue;
            s = s.normalize().scale(w / 2);
            float a = alpha * near;
            // overlap the joints a little so the bends have no gaps
            Vec3 dir = p1.subtract(p0).normalize().scale(w * 0.3);
            Vec3 q0 = p0.subtract(dir), q1 = p1.add(dir);
            Vec3[] c = {q0.subtract(s), q0.add(s), q1.add(s), q1.subtract(s)};
            quad2(b, mat, c, r, g, bl, a);
        }
    }

    /** A small bright square facing the camera (an arc's travelling bead), at {@code size} blocks across, at least a few pixels. */
    public static void bead(Frame f, Vec3 at, int rgb, float size, float alpha) {
        Vec3 p = at.subtract(f.camera);
        double dist = p.length();
        if (dist < NEAR + FADE) return;
        float s = Math.max(size, (float) dist * MIN_WIDTH_PER_BLOCK * 3) / 2;
        Vec3 right = p.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize().scale(s);
        Vec3 up = right.cross(p).normalize().scale(s);
        VertexConsumer b = f.buffers.getBuffer(RenderType.lightning());
        Matrix4f mat = f.pose.last().pose();
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, bl = (rgb & 255) / 255f;
        Vec3[] c = {p.subtract(right).subtract(up), p.subtract(right).add(up), p.add(right).add(up), p.add(right).subtract(up)};
        quad2(b, mat, c, r, g, bl, alpha);
    }

    /** A position-colour quad drawn with both windings (lightning() culls back faces). */
    private static void quad2(VertexConsumer b, Matrix4f mat, Vec3[] c, float r, float g, float bl, float a) {
        for (int i = 0; i < 4; i++) b.addVertex(mat, (float) c[i].x, (float) c[i].y, (float) c[i].z).setColor(r, g, bl, a);
        for (int i = 3; i >= 0; i--) b.addVertex(mat, (float) c[i].x, (float) c[i].y, (float) c[i].z).setColor(r, g, bl, a);
    }

    /** The point at fraction {@code t} of the way along a polyline. */
    public static Vec3 along(Vec3[] pts, double t) {
        double total = 0;
        for (int i = 1; i < pts.length; i++) total += pts[i].distanceTo(pts[i - 1]);
        double want = Mth.clamp(t, 0, 1) * total;
        for (int i = 1; i < pts.length; i++) {
            double seg = pts[i].distanceTo(pts[i - 1]);
            if (want <= seg || i == pts.length - 1) return pts[i - 1].lerp(pts[i], seg < 1e-9 ? 0 : Math.min(1, want / seg));
            want -= seg;
        }
        return pts[pts.length - 1];
    }

    /** A quadratic Bezier through {@code mid} as control point, {@code n} segments. */
    public static Vec3[] curve(Vec3 a, Vec3 control, Vec3 b, int n) {
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n, u = 1 - t;
            pts[i] = a.scale(u * u).add(control.scale(2 * u * t)).add(b.scale(t * t));
        }
        return pts;
    }

    private Beams() {
    }
}
