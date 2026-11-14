package com.coolfreeze23.headinajar.client.fx;

import com.coolfreeze23.headinajar.fx.Fx;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The client side of the memos: turns each {@link Fx} into sprites, beams, camera and sounds from the kit, and keeps
 * the short-lived things (delayed beats, the laser's beams) until they are done.
 */
public final class FxManager {
    private record Later(int at, Runnable run) {
    }

    private record Laser(int shooter, Vec3 eye, Vec3 hit, long start) {
    }

    private static final List<Later> LATER = new ArrayList<>();
    private static final List<Laser> LASERS = new ArrayList<>();
    private static int ticks;
    /** Memos handled, by kind (for the harness). */
    public static final int[] HANDLED = new int[16];

    public static void handle(Fx fx) {
        if (Minecraft.getInstance().level == null) return;
        if (fx.kind() >= 0 && fx.kind() < HANDLED.length) HANDLED[fx.kind()]++;
        switch (fx.kind()) {
            case Fx.TK_GRAB -> TetherFx.grab(fx);
            case Fx.TK_THROW, Fx.TK_SET_DOWN, Fx.TK_DROP -> TetherFx.release(fx);
            case Fx.TK_FIZZLE -> TetherFx.fizzle(fx);
            case Fx.SWAP -> SwapFx.handle(fx);
            case Fx.LASER -> laser(fx);
            case Fx.ARM_ARC -> ArmBeams.arc(fx);
            case Fx.TURRET_SHOT -> {
                FxParticle r = FxParticle.make(FxParticle.Look.RING, fx.a(), Vec3.ZERO);
                if (r != null) r.color(FxColors.WARN).size(0.45f).grow(2f).life(7).add();
            }
            default -> com.coolfreeze23.headinajar.client.body.BodyFx.handle(fx);
        }
    }

    /** Runs {@code run} on the client tick {@code ticks} from now. */
    public static void later(int delay, Runnable run) {
        LATER.add(new Later(ticks + delay, run));
    }

    public static void tick() {
        ticks++;
        if (Minecraft.getInstance().level == null) {
            LATER.clear();
            LASERS.clear();
            return;
        }
        List<Later> due = new ArrayList<>();
        for (Iterator<Later> it = LATER.iterator(); it.hasNext(); ) {
            Later l = it.next();
            if (l.at <= ticks) {
                due.add(l);
                it.remove();
            }
        }
        for (Later l : due) l.run.run();
        long now = Minecraft.getInstance().level.getGameTime();
        for (Laser l : LASERS) {
            long age = now - l.start;
            // pressurised tears: droplets fall off the beams with gravity, lit like water, not glowing
            if (age < 6) {
                for (int i = 0; i < Comfort.particles(2); i++) {
                    Vec3 p = l.eye.lerp(l.hit, Math.random() * 0.9 + 0.05);
                    FxParticle d = FxParticle.make(FxParticle.Look.DROPLET, p, new Vec3(0, -0.02, 0));
                    if (d != null) d.color(FxColors.WET).size(0.05f).gravity(0.6f).life(14).drag(0.98f).add();
                }
            }
        }
        LASERS.removeIf(l -> now - l.start > 8);
    }

    private static void laser(Fx fx) {
        var level = Minecraft.getInstance().level;
        LASERS.add(new Laser(fx.entityA(), fx.a(), fx.b(), level.getGameTime()));
        // at the eyes: two droplets well up; at the hit: a saline ring and a splash
        FxParticle ring = FxParticle.make(FxParticle.Look.RING, fx.b(), Vec3.ZERO);
        if (ring != null) ring.color(FxColors.WET_PALE).size(0.35f).grow(1.8f).life(8).flat(fx.b().subtract(fx.a())).add();
        for (int i = 0; i < Comfort.particles(4); i++) {
            double a = i * Math.PI / 2;
            FxParticle s = FxParticle.make(FxParticle.Look.DROPLET, fx.b(), new Vec3(Math.cos(a) * 0.08, 0.12, Math.sin(a) * 0.08));
            if (s != null) s.color(FxColors.WET).size(0.05f).gravity(0.7f).life(12).add();
        }
    }

    /** The laser's two beams, one per eye, converging on the hit and narrowing to nothing over 6 ticks. */
    public static void render(Beams.Frame f) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Laser l : LASERS) {
            float age = level.getGameTime() - l.start + f.partialTick();
            if (age > 7) continue;
            float u = Mth.clamp(age / 7f, 0, 1);
            float w = 1 / 32f * (1 - u * u) + 0.004f;
            Entity shooter = level.getEntity(l.shooter);
            Vec3 eye = shooter == null ? l.eye : shooter.getEyePosition(f.partialTick());
            Vec3 look = l.hit.subtract(eye).normalize();
            Vec3 side = look.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
            var mc = Minecraft.getInstance();
            boolean me = shooter == mc.player && mc.options.getCameraType().isFirstPerson();
            Beams.Ribbon look2 = new Beams.Ribbon(FxColors.WET_DARK, FxColors.WET_PALE, w, w, 1 - u * 0.5f, age * 0.2f);
            for (int s = -1; s <= 1; s += 2) {
                // from the goggle lenses; for the doer, from the lower corners of the view
                Vec3 from = me ? eye.add(look.scale(0.4)).add(side.scale(0.22 * s)).subtract(0, 0.25, 0) : eye.add(side.scale(0.11 * s)).add(look.scale(0.18));
                Beams.ribbon(f, new Vec3[]{from, from.lerp(l.hit, 0.5), l.hit}, look2, null);
            }
        }
    }

    private FxManager() {
    }
}
