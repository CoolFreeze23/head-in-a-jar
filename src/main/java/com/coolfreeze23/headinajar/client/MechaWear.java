package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.client.fx.Comfort;
import com.coolfreeze23.headinajar.client.fx.FxColors;
import com.coolfreeze23.headinajar.client.fx.FxParticle;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.mecha.ArmSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Heat and wear a bystander can read without a gauge, from what every client has (heat, vent, health, the hurt time):
 * above 60% heat the hot metal ticks every one to four seconds; below half health a seam spits sparks every one to three
 * seconds with a fizz; below a quarter a column of dark smoke rises from the hull and every blow brings a spark crackle
 * (the limp is the walk's own clip, {@code HopperAnimation}). The vents' glow is drawn with the frame
 * ({@link #ventGlow}). Nothing is done for a machine farther than 32 blocks or behind the camera beyond 8.
 */
public final class MechaWear {
    /** Heat shares the vents glow at, steady, a step each (dark amber, amber); a vent is red, fading as it ends. */
    public static final float WARM = 0.6f, HOT = 0.85f;
    /** Health shares below which a machine sparks, and smokes and limps. */
    public static final float SPARKS = 0.5f, SMOKES = 0.25f;

    private static final class Seen {
        long nextTink = -1, nextSpark = -1;
        int hurtTime;
    }

    private static final Map<MechaEntity, Seen> SEEN = new WeakHashMap<>();
    private static final String[] SEAMS = {"fx_hull", "fx_core", "fx_vent_l", "fx_vent_r", "fx_wrist_l", "fx_wrist_r"};

    private MechaWear() {
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            SEEN.clear();
            return;
        }
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        Vec3 look = new Vec3(camera.getLookVector());
        long now = mc.level.getGameTime();
        for (MechaEntity m : mc.level.getEntitiesOfClass(MechaEntity.class, new AABB(cam, cam).inflate(32))) {
            if (!m.frame().animated() || m.isDeadOrDying()) continue;
            Vec3 to = m.position().add(0, m.getBbHeight() / 2, 0).subtract(cam);
            if (to.lengthSqr() > 64 && to.normalize().dot(look) < 0.2) continue;
            Seen s = SEEN.computeIfAbsent(m, k -> new Seen());
            RandomSource r = m.getRandom();
            float heat = m.heat() / Math.max(1, m.frame().heatCap), health = m.getHealth() / Math.max(1, m.getMaxHealth());
            // the hot metal ticks as it works (never while it vents: the vent has its own voice)
            if (heat >= WARM && m.vent() == 0 && to.lengthSqr() < 12 * 12) {
                if (s.nextTink < 0 || s.nextTink - now > 80) s.nextTink = now + 20 + r.nextInt(60);
                if (now >= s.nextTink) {
                    play(m, HopperKeyframes.locator(m, r.nextBoolean() ? "fx_vent_l" : "fx_vent_r"), ArmSounds.THERMAL_TINK.get());
                    s.nextTink = now + 20 + r.nextInt(60);
                }
            } else {
                s.nextTink = -1;
            }
            if (health < SPARKS) {
                if (s.nextSpark < 0) s.nextSpark = now + 10 + r.nextInt(40);
                if (now >= s.nextSpark) {
                    Vec3 at = outside(m, HopperKeyframes.locator(m, SEAMS[r.nextInt(SEAMS.length)]), r);
                    sparks(at, 12, r);
                    play(m, at, ArmSounds.SPARK_FIZZ.get());
                    s.nextSpark = now + 20 + r.nextInt(40);
                }
            } else {
                s.nextSpark = -1;
            }
            if (health < SMOKES && now % 2 == 0) {
                // dark smoke, lit by the world (dark at night), rising and spreading from the hull
                Vec3 at = HopperKeyframes.locator(m, "fx_hull").add(r.nextGaussian() * 0.12, 0.1, r.nextGaussian() * 0.12);
                FxParticle p = FxParticle.make(FxParticle.Look.PUFF, at, new Vec3(r.nextGaussian() * 0.01, 0.07 + r.nextDouble() * 0.03, r.nextGaussian() * 0.01));
                if (p != null && Comfort.particles(2) > (now % 4 == 0 ? 0 : 1)) p.color(0x333137).alpha(0.85f).size(0.28f).grow(3.5f).life(34 + r.nextInt(10)).drag(0.97f).add();
            }
            // a fresh blow on a badly damaged machine: a crackle and a spit of sparks where it was hit
            if (m.hurtTime > s.hurtTime && health < SMOKES) {
                Vec3 at = outside(m, HopperKeyframes.locator(m, "fx_hull"), r);
                sparks(at, 8, r);
                play(m, at, ArmSounds.SPARK_FIZZ.get());
            }
            s.hurtTime = m.hurtTime;
        }
    }

    /** A seam on the hull's skin at the height of {@code at} (the locators sit inside the model, where sparks would hide). */
    private static Vec3 outside(MechaEntity m, Vec3 at, RandomSource r) {
        Vec3 c = m.position();
        double dx = at.x - c.x, dz = at.z - c.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 0.1) {
            double a = r.nextDouble() * Math.PI * 2;
            dx = Math.cos(a);
            dz = Math.sin(a);
            d = 1;
        }
        double out = m.getBbWidth() / 2 + 0.15;
        return new Vec3(c.x + dx / d * out, at.y, c.z + dz / d * out);
    }

    private static void sparks(Vec3 at, int n, RandomSource r) {
        for (int i = 0; i < Comfort.particles(n); i++) {
            FxParticle s = FxParticle.make(FxParticle.Look.SPARK, at, new Vec3(r.nextGaussian() * 0.14, 0.08 + r.nextDouble() * 0.16, r.nextGaussian() * 0.14));
            if (s != null) s.color(FxColors.POWER_PALE).size(0.1f).gravity(0.7f).life(9 + r.nextInt(7)).add();
        }
    }

    private static void play(MechaEntity m, Vec3 at, SoundEvent sound) {
        m.level().playLocalSound(at.x, at.y, at.z, sound, SoundSource.NEUTRAL, 1, ArmSounds.pitch(m.getRandom()), false);
    }

    /**
     * The vents' glow as ARGB to draw their cubes again at full bright, or 0 for none: steady dark amber from 60% heat,
     * amber from 85%; while venting a red that fades out over the vent (it never blinks).
     */
    public static int ventGlow(MechaEntity m) {
        if (m.vent() > 0) {
            float a = 0.55f * m.vent() / com.coolfreeze23.headinajar.mecha.MechaLogic.VENT_TICKS;
            return (int) (a * 255) << 24 | FxColors.WARN;
        }
        float heat = m.heat() / Math.max(1, m.frame().heatCap);
        if (heat >= HOT) return 0x73000000 | 0xffc24e;
        if (heat >= WARM) return 0x4d000000 | 0xd47c16;
        return 0;
    }
}
