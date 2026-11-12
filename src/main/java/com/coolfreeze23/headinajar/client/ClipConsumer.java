package com.coolfreeze23.headinajar.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Draws only the part of a model below (or above) a horizontal plane, by cutting each quad as it is drawn: real
 * geometry, so it survives batching, Sodium and Iris; nothing scaled, no shader, no scissor. The plane is a height in
 * the space the vertices arrive in (a block entity renderer's camera-relative world, where horizontal stays
 * horizontal). Quads come as four vertices through the one-call {@code addVertex}; a quad wholly on the kept side goes
 * straight on, one wholly on the other side is dropped, and one across the plane keeps 3 to 5 corners, sent on as one
 * or two quads. Nothing is allocated per vertex: the corners are held in small arrays.
 */
public final class ClipConsumer implements VertexConsumer {
    private static final int F = 9; // x y z u v nx ny nz, then the height above the kept side's edge
    private final VertexConsumer out;
    private final float plane;
    private final boolean keepBelow;
    private final float[] in = new float[4 * F];
    private final int[] ints = new int[4 * 3]; // color overlay light
    private final float[] kept = new float[8 * F];
    private final int[] keptInts = new int[8 * 3];
    private int n;

    public ClipConsumer(VertexConsumer out, float plane, boolean keepBelow) {
        this.out = out;
        this.plane = plane;
        this.keepBelow = keepBelow;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        int o = n * F, p = n * 3;
        in[o] = x;
        in[o + 1] = y;
        in[o + 2] = z;
        in[o + 3] = u;
        in[o + 4] = v;
        in[o + 5] = nx;
        in[o + 6] = ny;
        in[o + 7] = nz;
        in[o + 8] = keepBelow ? plane - y : y - plane;
        ints[p] = color;
        ints[p + 1] = overlay;
        ints[p + 2] = light;
        if (++n == 4) {
            cut();
            n = 0;
        }
    }

    private void cut() {
        boolean all = true, none = true;
        for (int i = 0; i < 4; i++) {
            boolean keep = in[i * F + 8] >= 0;
            all &= keep;
            none &= !keep;
        }
        if (none) return;
        if (all) {
            for (int i = 0; i < 4; i++) emit(in, ints, i);
            return;
        }
        int k = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            float da = in[i * F + 8], db = in[j * F + 8];
            if (da >= 0) copy(i, k++);
            if (da >= 0 != db >= 0) lerp(i, j, da / (da - db), k++);
        }
        if (k < 3) return;
        emit(kept, keptInts, 0);
        emit(kept, keptInts, 1);
        emit(kept, keptInts, 2);
        emit(kept, keptInts, k > 3 ? 3 : 2);
        if (k == 5) {
            emit(kept, keptInts, 0);
            emit(kept, keptInts, 3);
            emit(kept, keptInts, 4);
            emit(kept, keptInts, 4);
        }
    }

    private void copy(int from, int to) {
        System.arraycopy(in, from * F, kept, to * F, F);
        System.arraycopy(ints, from * 3, keptInts, to * 3, 3);
    }

    private void lerp(int a, int b, float t, int to) {
        for (int c = 0; c < F; c++) kept[to * F + c] = in[a * F + c] + (in[b * F + c] - in[a * F + c]) * t;
        System.arraycopy(ints, a * 3, keptInts, to * 3, 3);
    }

    private void emit(float[] f, int[] i, int at) {
        int o = at * F, p = at * 3;
        out.addVertex(f[o], f[o + 1], f[o + 2], i[p], f[o + 3], f[o + 4], i[p + 1], i[p + 2], f[o + 5], f[o + 6], f[o + 7]);
    }

    // a model part draws through the one call above; the separate calls are passed on as they come
    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        return out.addVertex(x, y, z);
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return out.setColor(r, g, b, a);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return out.setUv(u, v);
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return out.setUv1(u, v);
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return out.setUv2(u, v);
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return out.setNormal(x, y, z);
    }
}
