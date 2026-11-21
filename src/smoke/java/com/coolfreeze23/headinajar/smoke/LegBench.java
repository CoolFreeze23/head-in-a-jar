package com.coolfreeze23.headinajar.smoke;

import com.coolfreeze23.headinajar.entity.SpiderWalkerAnimation;
import com.coolfreeze23.headinajar.mecha.Legs;
import com.coolfreeze23.headinajar.mecha.ProceduralLegs;
import net.minecraft.client.Minecraft;

/**
 * {@code legbench N FRAMES}: times the Spider Walker's legs that find their own footing on this client, against the
 * world round the player: N walkers in a ring 4 blocks out, each walking a circle at the flat-out pace and turning,
 * stepped FRAMES times at three frames a tick, with each leg's two-bone solve as the renderer does it. Reports the mean
 * and the 99th percentile per walker per frame, in microseconds. Nothing in the world changes.
 */
final class LegBench {
    private LegBench() {
    }

    static String run(Minecraft mc, String[] a) {
        int n = a.length > 0 ? Integer.parseInt(a[0]) : 8, frames = a.length > 1 ? Integer.parseInt(a[1]) : 600;
        var rig = SpiderWalkerAnimation.RIG;
        var spec = rig.spec();
        var level = mc.level;
        double cx = mc.player.getX(), cy = Math.floor(mc.player.getY() + 0.01), cz = mc.player.getZ();
        ProceduralLegs.State[] st = new ProceduralLegs.State[n];
        double[][] clip = new double[rig.count()][];
        for (int i = 0; i < rig.count(); i++) clip[i] = new double[]{rig.restX()[i], 0, rig.restZ()[i]};
        for (int k = 0; k < n; k++) st[k] = new ProceduralLegs.State();
        long[] times = new long[frames * n];
        double sink = 0;
        int c = 0;
        for (int f = 0; f < frames; f++) {
            double now = 1000 + f / 3.0;
            for (int k = 0; k < n; k++) {
                double ring = k * Math.PI * 2 / n, ang = ring + now * 0.34 / 1.5;
                double x = cx + Math.cos(ring) * 4 + Math.cos(ang) * 1.5, z = cz + Math.sin(ring) * 4 + Math.sin(ang) * 1.5;
                float yaw = (float) Math.toDegrees(-ang);
                long t0 = System.nanoTime();
                ProceduralLegs.update(rig, st[k], level, x, cy, z, yaw, now, null, null, 0, true, false, clip);
                for (int i = 0; i < rig.count(); i++) {
                    double[] m = st[k].model[i];
                    double dx = m[0] - spec.hipX()[i], dz = m[2] - spec.hipZ()[i];
                    double[] s = Legs.solve(Math.hypot(dx, dz), m[1] - spec.bodyY(), spec.femur(), spec.tibia());
                    sink += s[0] + Math.atan2(-dx, -dz);
                }
                times[c++] = System.nanoTime() - t0;
            }
        }
        // the first tenth is warm-up
        long[] kept = java.util.Arrays.copyOfRange(times, times.length / 10, times.length);
        java.util.Arrays.sort(kept);
        double mean = java.util.Arrays.stream(kept).average().orElse(0) / 1000.0;
        double p99 = kept[(int) (kept.length * 0.99)] / 1000.0, p50 = kept[kept.length / 2] / 1000.0;
        return String.format(java.util.Locale.ROOT, "legbench %d walkers x %d frames: mean %.1f us, median %.1f us, p99 %.1f us a walker a frame (%s)",
                n, frames, mean, p50, p99, sink != 0 ? "ok" : "ok0");
    }
}
