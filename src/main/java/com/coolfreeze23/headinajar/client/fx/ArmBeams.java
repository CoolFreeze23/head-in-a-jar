package com.coolfreeze23.headinajar.client.fx;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.mecha.ArmAim;
import com.coolfreeze23.headinajar.mecha.ArmSounds;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The arm tools' rays and streams, drawn by every client from what it already has (the synced arm buttons, battery,
 * vent and what a Tractor Beam holds) and, for the arcs, from one memo per jump. Each one leaves from the muzzle on the
 * tool's own model as it was drawn this frame ({@code ModuleLayer} records it; the rest pose's wrist when the machine
 * was not drawn) and lands where the pilot looks, which is where the crosshair is ({@link ArmAim#end}).
 * <ul>
 * <li>Plasma Lance: a thin pilot line, then the beam swells to full width (6 ticks), burns with a boiling texture and a
 * slow width wobble (never a flash), sparks and smoke where it lands; on release it collapses from the muzzle toward
 * the target over 4 ticks.</li>
 * <li>Arc Cannon and Tesla Coil: a jagged arc per jump, white-cored with an amber fringe, each jump 2 ticks after the
 * last, living 4 ticks with one re-roll (every 6 under the photosensitive option); sparks and a ring on each creature.</li>
 * <li>Tractor Beam: three thin teal ribbons twisting in a helix from the emitter to what it holds (one turn per 2 blocks,
 * turning once a second), rings running down it at the grab, unwinding outward and fading at the release.</li>
 * <li>Flamethrower: puffs launched from the nozzle along the aim, glowing amber, cooling through red to grey smoke,
 * slowing and rising so the flame bends up and curls at its end, about 7 blocks out (what it sets alight).</li>
 * <li>Welding Torch: a short blue-white arc from the torch to what it works on, with sparks.</li>
 * </ul>
 * Nothing is done for machines farther than {@link #RANGE}; particles stop at 32 blocks and follow the particle setting;
 * the camera moves only through {@link CameraDesk} (the screen-shake slider, off under the photosensitive option).
 */
public final class ArmBeams {
    /** Tools are drawn and sounded within this many blocks of the camera. */
    public static final double RANGE = 64;
    /** Ticks: the lance's ignition and collapse, an arc's life, the tractor's unwinding. */
    static final int IGNITE = 6, COLLAPSE = 4, ARC_LIFE = 4, UNWIND = 8;
    private static final int COPPER = 0xc26b4c, LANCE_CORE = 0xfae591, ARC_CORE = 0xfffaf0, WELD = 0x8fd4ff, WELD_CORE = 0xeaf8ff;
    private static final int FLAME = 0xffc24e, FLAME_PALE = 0xfff2c4, FLAME_RED = 0xd4581c, SMOKE = 0x5c616b;

    /** What this client remembers of one arm: whether its tool works, since when, until when, and where it last landed. */
    static final class Arm {
        MechaModule module;
        boolean on;
        double onAt = -1e9, offAt = -1e9;
        Vec3 end;
        int held = -1;
        double heldAt = -1e9, letGoAt = -1e9;
        Vec3 heldEnd;
    }

    private static final Map<MechaEntity, Arm[]> ARMS = new WeakHashMap<>();

    /** One jump of an arc, as the memo said. */
    private record Hop(int mecha, int slot, Vec3 from, Vec3 to, int fromEntity, int toEntity, double start, int seed, int n) {
    }

    private static final List<Hop> HOPS = new ArrayList<>();
    /** Creatures a Tractor Beam holds now (their shell is drawn). */
    private static final Map<Integer, Float> HELD = new HashMap<>();

    private ArmBeams() {
    }

    private static Arm[] arms(MechaEntity m) {
        return ARMS.computeIfAbsent(m, k -> new Arm[]{new Arm(), new Arm()});
    }

    /** Whether arm {@code side}'s tool is working now, by what every client has. */
    static boolean working(MechaEntity m, int side, MechaModule module) {
        if (module == null || !(m.getFirstPassenger() instanceof Player) || (m.armsShown() & (1 << side)) == 0) return false;
        if (m.vent() > 0 || m.isDeadOrDying()) return false;
        return switch (module) {
            case PLASMA_LANCE -> m.battery() >= 600;
            case FLAMETHROWER -> m.battery() >= 60;
            case WELDING_TORCH -> m.battery() >= 80;
            case TRACTOR_BEAM -> m.beamHeld(side) >= 0;
            default -> false;
        };
    }

    /**
     * Where flat slot {@code slot}'s ray leaves from: the muzzle on the tool's model as drawn this frame (or the last one),
     * else the arm's wrist on the rest pose turned with the body, else (a torso tool) high on the hull.
     */
    public static Vec3 muzzle(MechaEntity m, int slot, float pt, double time) {
        var v = m.view;
        if (slot >= 0 && slot < v.muzzle.length && v.muzzle[slot] != null && time - v.muzzleAt[slot] >= -0.01 && time - v.muzzleAt[slot] < 0.6) {
            return v.muzzle[slot];
        }
        int side = slot - m.frame().firstIndex(MechaModule.Slot.ARM);
        if (side >= 0 && side < m.frame().slots(MechaModule.Slot.ARM)) return ArmAim.origin(m, side, pt);
        return m.getPosition(pt).add(0, m.getBbHeight() * 0.8, 0);
    }

    // --- the memo ---------------------------------------------------------------------------------------------------

    public static void arc(Fx fx) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int arg = Math.round(fx.arg());
        int slot = arg % 64, n = arg / 64;
        int mecha = n == 0 ? fx.entityA() : -1;
        double now = mc.level.getGameTime();
        HOPS.add(new Hop(mecha, slot, fx.a(), fx.b(), fx.entityA(), fx.entityB(), now, fx.seed() + n * 7919, n));
        Vec3 at = fx.b();
        RandomSource r = mc.level.random;
        // where it lands: sparks, a ring, the crackle of the jump
        for (int i = 0; i < Comfort.particles(fx.entityB() >= 0 ? 8 : 5); i++) {
            Vec3 vel = new Vec3(r.nextGaussian() * 0.12, 0.08 + r.nextDouble() * 0.12, r.nextGaussian() * 0.12);
            FxParticle s = FxParticle.make(FxParticle.Look.SPARK, at, vel);
            if (s != null) s.color(FxColors.POWER_PALE).size(0.06f).gravity(0.6f).life(8 + r.nextInt(5)).add();
        }
        boolean calm = Comfort.photosensitive();
        FxParticle ring = FxParticle.make(FxParticle.Look.RING, at, Vec3.ZERO);
        if (ring != null) ring.color(FxColors.POWER_PALE).alpha(calm ? 0.5f : 0.85f).size(0.35f).grow(calm ? 1.6f : 2.4f).life(calm ? 10 : 6).add();
        if (n > 0 || !(mc.level.getEntity(fx.entityA()) instanceof MechaEntity m && m.moduleAt(slot) == MechaModule.ARC_CANNON)) {
            play(at, ArmSounds.ARC_HOP.get());
        }
        // the cannon kicks the pilot's view; whoever stands near feels a little of it
        if (n == 0 && mc.level.getEntity(fx.entityA()) instanceof MechaEntity m && m.moduleAt(slot) == MechaModule.ARC_CANNON) {
            if (m.getFirstPassenger() == mc.player) {
                CameraDesk.shake(0.12f);
                CameraDesk.kick(1.5f, 2, 6);
            } else {
                CameraDesk.nearby(fx.a(), 0.1f);
            }
        }
    }

    private static void play(Vec3 at, SoundEvent sound) {
        var level = Minecraft.getInstance().level;
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.NEUTRAL, 1, ArmSounds.pitch(level.random), false);
    }

    // --- every tick: what works, its sounds and particles ---------------------------------------------------------

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            ARMS.clear();
            HOPS.clear();
            HELD.clear();
            return;
        }
        double now = mc.level.getGameTime();
        HOPS.removeIf(h -> now - h.start > ARC_LIFE + 1);
        HELD.clear();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (MechaEntity m : mc.level.getEntitiesOfClass(MechaEntity.class, new AABB(cam, cam).inflate(RANGE))) {
            if (m.frame().slots(MechaModule.Slot.ARM) == 0) continue;
            Arm[] arms = null;
            for (int side = 0; side < Math.min(2, m.frame().slots(MechaModule.Slot.ARM)); side++) {
                MechaModule module = m.arm(side);
                if (ArmAim.muzzle(module) == null && (arms = ARMS.get(m)) == null) continue;
                if (arms == null) arms = arms(m);
                tickArm(m, side, module, arms[side], now, mc);
            }
        }
    }

    private static void tickArm(MechaEntity m, int side, MechaModule module, Arm a, double now, Minecraft mc) {
        boolean on = working(m, side, module);
        int slot = ArmAim.slot(m, side);
        Vec3 muzzle = muzzle(m, slot, 1, now + 1);
        if (on && (!a.on || a.module != module)) {
            a.onAt = now;
            if (module == MechaModule.PLASMA_LANCE) play(muzzle, ArmSounds.LANCE_IGNITE.get());
            if (module == MechaModule.FLAMETHROWER) play(muzzle, ArmSounds.FLAME_IGNITE.get());
        }
        if (!on && a.on) {
            a.offAt = now;
            if (a.module == MechaModule.PLASMA_LANCE) play(muzzle, ArmSounds.LANCE_OUT.get());
        }
        a.on = on;
        a.module = module;
        // the tractor's hold: grabbed, held, let go
        int held = module == MechaModule.TRACTOR_BEAM && m.getFirstPassenger() instanceof Player ? m.beamHeld(side) : -1;
        if (held >= 0 && a.held != held) {
            a.heldAt = now;
            grabRings(m, slot, held);
        }
        if (held < 0 && a.held >= 0) a.letGoAt = now;
        a.held = held;
        if (held >= 0) HELD.put(held, 1f);
        if (!on || !(m.getFirstPassenger() instanceof Player pilot)) return;
        Vec3 end = ArmAim.end(pilot, m, ArmAim.reach(module), 1);
        a.end = end;
        RandomSource r = m.getRandom();
        Object key = m.getId() * 8 + side;
        switch (module) {
            case PLASMA_LANCE -> {
                SoundLoops.want(key, ArmSounds.LANCE_HUM.get(), SoundSource.NEUTRAL, muzzle, 1, 1);
                for (int i = 0; i < Comfort.particles(2); i++) {
                    FxParticle s = FxParticle.make(FxParticle.Look.SPARK, end, new Vec3(r.nextGaussian() * 0.12, 0.1 + r.nextDouble() * 0.15, r.nextGaussian() * 0.12));
                    if (s != null) s.color(FLAME).size(0.07f).gravity(0.7f).life(8 + r.nextInt(6)).add();
                }
                if ((long) now % 4 == 0) puff(end, new Vec3(0, 0.04, 0), SMOKE, 0.3f, 22);
                // the pilot feels it as a steady rumble, never more than a knock
                if (m.getFirstPassenger() == mc.player && CameraDesk.trauma() < 0.1f) CameraDesk.shake(0.03f);
            }
            case FLAMETHROWER -> {
                SoundLoops.want(key, ArmSounds.FLAME_ROAR.get(), SoundSource.NEUTRAL, muzzle, 1, 1);
                flame(m, side, muzzle, end, r);
            }
            case WELDING_TORCH -> {
                SoundLoops.want(key, ArmSounds.WELD_HISS.get(), SoundSource.NEUTRAL, muzzle, 1, 1);
                Vec3 at = weldEnd(pilot, m, muzzle, 1);
                for (int i = 0; i < Comfort.particles(2); i++) {
                    FxParticle s = FxParticle.make(FxParticle.Look.SPARK, at, new Vec3(r.nextGaussian() * 0.1, 0.06 + r.nextDouble() * 0.12, r.nextGaussian() * 0.1));
                    if (s != null) s.color(WELD_CORE).size(0.05f).gravity(0.8f).life(6 + r.nextInt(6)).add();
                }
            }
            case TRACTOR_BEAM -> SoundLoops.want(key, ArmSounds.TRACTOR_HUM.get(), SoundSource.NEUTRAL, muzzle, 1, 1);
            default -> {
            }
        }
    }

    /**
     * The flame: a dense jet of glowing puffs out of the nozzle along the aim (spread by the cone slider), pale at the
     * nozzle and orange-red as they slow, rising a little so the jet bends up and curls at its end, about 7 blocks out;
     * a few grey smoke puffs leave its far end.
     */
    private static void flame(MechaEntity m, int side, Vec3 nozzle, Vec3 end, RandomSource r) {
        Vec3 dir = end.subtract(nozzle);
        if (dir.lengthSqr() < 1e-4) return;
        dir = dir.normalize();
        Vec3 p1 = dir.cross(new Vec3(0, 1, 0));
        if (p1.lengthSqr() < 1e-4) p1 = new Vec3(1, 0, 0);
        p1 = p1.normalize();
        Vec3 p2 = dir.cross(p1).normalize();
        // the cone slider: 0 a narrow jet, 100 a wide spray (the same cone the server burns within)
        double cone = 0.95 - 0.25 * m.armSlider(side) / 100.0, spread = Math.tan(Math.acos(cone)) * 0.45;
        for (int i = 0; i < Comfort.particles(6); i++) {
            double a = r.nextDouble() * Math.PI * 2, k = Math.sqrt(r.nextDouble()) * spread;
            Vec3 aim = dir.add(p1.scale(Math.cos(a) * k)).add(p2.scale(Math.sin(a) * k)).normalize();
            Vec3 vel = aim.scale(0.95 + r.nextDouble() * 0.15);
            // started a little way along their first tick, so the jet is continuous from the nozzle at any frame rate
            FxParticle f = FxParticle.make(FxParticle.Look.PUFF, nozzle.add(aim.scale(0.1 + r.nextDouble() * 0.5)), vel);
            if (f != null) f.fadeTo(i % 3 == 0 ? FLAME : FLAME_PALE, FLAME_RED).bright().size(0.12f).grow(3.2f).life(10 + r.nextInt(4)).drag(0.88f)
                    .gravity(-0.12f).add();
        }
        if (r.nextInt(2) == 0) {
            Vec3 tail = nozzle.add(dir.scale(4.5 + r.nextDouble() * 2));
            FxParticle s = FxParticle.make(FxParticle.Look.PUFF, tail, dir.scale(0.12).add(0, 0.06, 0));
            if (s != null) s.color(SMOKE).alpha(0.7f).size(0.3f).grow(2.5f).life(26).drag(0.94f).gravity(-0.08f).add();
        }
        if (r.nextInt(3) == 0) {
            FxParticle e = FxParticle.make(FxParticle.Look.MOTE, nozzle, dir.scale(0.8).add(r.nextGaussian() * 0.05, 0.05, r.nextGaussian() * 0.05));
            if (e != null) e.color(FLAME).size(0.04f).drag(0.9f).gravity(-0.1f).life(14).add();
        }
    }

    /** A puff of lit smoke. */
    private static void puff(Vec3 at, Vec3 vel, int rgb, float size, int life) {
        FxParticle p = FxParticle.make(FxParticle.Look.PUFF, at, vel);
        if (p != null) p.color(rgb).alpha(0.8f).size(size).grow(2.5f).life(life).drag(0.95f).gravity(-0.05f).add();
    }

    /** The rings that run down the helix when the beam takes hold. */
    private static void grabRings(MechaEntity m, int slot, int held) {
        var level = Minecraft.getInstance().level;
        for (int k = 0; k < 3; k++) {
            int step = k;
            FxManager.later(1 + k * 2, () -> {
                Entity e = level.getEntity(held);
                if (e == null) return;
                Vec3 a = muzzle(m, slot, 1, level.getGameTime() + 1), b = e.getBoundingBox().getCenter();
                Vec3 at = a.lerp(b, (step + 1) / 3.0);
                FxParticle ring = FxParticle.make(FxParticle.Look.RING, at, Vec3.ZERO);
                if (ring != null) ring.color(FxColors.SIGNAL_PALE).size(0.5f + step * 0.25f).grow(1.4f).frame(3).life(6).flat(b.subtract(a)).add();
            });
        }
    }

    /** Where the torch works: what it is aimed at within its reach, else a short stub out of the nozzle. */
    private static Vec3 weldEnd(Player pilot, MechaEntity m, Vec3 nozzle, float pt) {
        HitResult hit = ArmAim.aim(pilot, m, ArmAim.reach(MechaModule.WELDING_TORCH), pt);
        if (hit.getType() != HitResult.Type.MISS) return ArmAim.end(pilot, m, ArmAim.reach(MechaModule.WELDING_TORCH), pt);
        Vec3 d = hit.getLocation().subtract(nozzle);
        return nozzle.add(d.lengthSqr() < 1e-4 ? Vec3.ZERO : d.normalize().scale(0.6));
    }

    /** The custody shell's strength on something a Tractor Beam holds (0 when nothing holds it). */
    public static float shell(Entity e) {
        return HELD.getOrDefault(e.getId(), 0f);
    }

    // --- every frame: the beams --------------------------------------------------------------------------------------

    public static void render(Beams.Frame f) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime() + f.partialTick();
        for (Map.Entry<MechaEntity, Arm[]> e : ARMS.entrySet()) {
            MechaEntity m = e.getKey();
            if (m.isRemoved() || m.distanceToSqr(f.camera()) > RANGE * RANGE) continue;
            for (int side = 0; side < e.getValue().length; side++) {
                Arm a = e.getValue()[side];
                if (a.module == null) continue;
                int slot = ArmAim.slot(m, side);
                switch (a.module) {
                    case PLASMA_LANCE -> lance(f, m, slot, a, now);
                    case TRACTOR_BEAM -> tractor(f, m, slot, a, now);
                    case WELDING_TORCH -> weld(f, m, slot, a, now);
                    default -> {
                    }
                }
            }
        }
        for (Iterator<Hop> it = HOPS.iterator(); it.hasNext(); ) {
            Hop h = it.next();
            double age = now - h.start;
            if (age > ARC_LIFE) continue;
            Entity from = mc.level.getEntity(h.fromEntity), to = h.toEntity >= 0 ? mc.level.getEntity(h.toEntity) : null;
            Vec3 a = from instanceof MechaEntity mech && h.n == 0 ? muzzle(mech, h.slot, f.partialTick(), now)
                    : from != null ? from.getBoundingBox().move(from.getPosition(f.partialTick()).subtract(from.position())).getCenter() : h.from;
            Vec3 b = to != null ? to.getBoundingBox().move(to.getPosition(f.partialTick()).subtract(to.position())).getCenter() : h.to;
            int reroll = Comfort.rerollTicks(2);
            int n = Math.max(6, Math.min(12, (int) (a.distanceTo(b) * 1.5)));
            Vec3[] pts = Beams.arcPath(a, b, n, h.seed + (long) (age / reroll) * 31, 0.25f, null);
            // the brightness holds while the shape changes: it fades only at the end of its life
            float alpha = (float) Mth.clamp(1.2 - age / ARC_LIFE, 0, 1);
            Beams.arc(f, pts, FxColors.POWER, ARC_CORE, 0.045f, alpha);
        }
    }

    private static void lance(Beams.Frame f, MechaEntity m, int slot, Arm a, double now) {
        boolean on = a.on;
        double off = now - a.offAt;
        if (!on && off > COLLAPSE) return;
        Player pilot = m.getFirstPassenger() instanceof Player p ? p : null;
        if (pilot == null && on) return;
        Vec3 muzzle = muzzle(m, slot, f.partialTick(), now);
        Vec3 end = on ? ArmAim.end(pilot, m, ArmAim.reach(MechaModule.PLASMA_LANCE), f.partialTick()) : a.end;
        if (end == null) return;
        double age = now - a.onAt;
        // ignition: a thin pilot line for 2 ticks, then the swell to full width with a little overshoot
        float full = 3 / 16f, w;
        if (age < 2) w = 1 / 48f;
        else if (age < IGNITE) {
            float t = (float) ((age - 2) / (IGNITE - 2));
            float back = 1 + 2.7f * (float) Math.pow(t - 1, 3) + 1.7f * (float) Math.pow(t - 1, 2);
            w = Mth.lerp(back, 1 / 48f, full);
        } else w = full;
        // a slow wobble of the width (3 a second, smooth): it boils, it never flashes
        w *= 1 + 0.07f * (float) Math.sin(now * Math.PI * 2 * 3 / 20);
        Vec3 from = muzzle;
        float alpha = 1;
        if (!on) {
            // collapse: from the muzzle toward the target
            float u = (float) Mth.clamp(off / COLLAPSE, 0, 1);
            from = muzzle.lerp(end, u);
            w *= 1 - 0.5f * u;
            alpha = 1 - u * u;
        }
        Beams.Ribbon look = new Beams.Ribbon(COPPER, LANCE_CORE, w * 0.75f, w, alpha, (float) (now * 0.45));
        Beams.ribbon(f, new Vec3[]{from, from.lerp(end, 0.5), end}, look, null);
        if (on) {
            Beams.bead(f, muzzle, LANCE_CORE, 0.22f + 0.03f * (float) Math.sin(now * 1.3), 0.9f);
            Beams.bead(f, end, FxColors.HEAT, 0.28f, 0.8f);
        }
    }

    private static void tractor(Beams.Frame f, MechaEntity m, int slot, Arm a, double now) {
        var level = Minecraft.getInstance().level;
        Vec3 muzzle = muzzle(m, slot, f.partialTick(), now);
        Entity held = a.held >= 0 ? level.getEntity(a.held) : null;
        float unwind = 0;
        Vec3 end;
        if (held != null) {
            end = held.getPosition(f.partialTick()).add(0, held.getBbHeight() / 2, 0);
            a.heldEnd = end;
        } else {
            double t = now - a.letGoAt;
            if (t > UNWIND || a.heldEnd == null) return;
            unwind = (float) (t / UNWIND);
            end = a.heldEnd;
        }
        Vec3 d = end.subtract(muzzle);
        double length = d.length();
        if (length < 0.2) return;
        Vec3 dir = d.scale(1 / length);
        Vec3 p1 = dir.cross(new Vec3(0, 1, 0));
        if (p1.lengthSqr() < 1e-4) p1 = new Vec3(1, 0, 0);
        p1 = p1.normalize();
        Vec3 p2 = dir.cross(p1).normalize();
        double wide = held != null ? Math.min(1.2, held.getBbWidth()) : 0.6;
        double grow = Mth.clamp((now - a.heldAt) / 4, 0, 1);
        int n = Math.max(12, Math.min(32, (int) (length * 3)));
        float alpha = 1 - unwind;
        Beams.Ribbon look = new Beams.Ribbon(FxColors.SIGNAL_DARK, FxColors.SIGNAL_PALE, 1 / 40f, 1 / 24f, 0.85f * alpha, (float) (now * 0.1));
        for (int k = 0; k < 3; k++) {
            Vec3[] pts = new Vec3[n + 1];
            for (int i = 0; i <= n; i++) {
                double s = i / (double) n;
                double r = Mth.lerp(s, 0.08, 0.25 + 0.35 * wide) * (1 + 2.5 * unwind) * grow;
                // one turn per 2 blocks, turning once a second
                double ang = Math.PI * 2 * (s * length / 2 - now / 20.0) + k * Math.PI * 2 / 3;
                pts[i] = muzzle.add(d.scale(s)).add(p1.scale(Math.cos(ang) * r)).add(p2.scale(Math.sin(ang) * r));
            }
            Beams.ribbon(f, pts, look, null);
        }
        if (held != null) Beams.bead(f, muzzle, FxColors.SIGNAL_PALE, 0.16f, 0.8f);
    }

    private static void weld(Beams.Frame f, MechaEntity m, int slot, Arm a, double now) {
        if (!a.on || !(m.getFirstPassenger() instanceof Player pilot)) return;
        Vec3 muzzle = muzzle(m, slot, f.partialTick(), now);
        Vec3 end = weldEnd(pilot, m, muzzle, f.partialTick());
        int reroll = Comfort.rerollTicks(2);
        Vec3[] pts = Beams.arcPath(muzzle, end, 6, m.getId() * 131L + (long) (now / reroll), 0.08f, null);
        Beams.arc(f, pts, WELD, WELD_CORE, 0.03f, 0.95f);
        Beams.bead(f, end, WELD_CORE, 0.14f, 0.9f);
    }

    /** How dimmed the pilot's visor is now, 0..1 (eased in and out over 6 ticks). */
    private static float visor;

    /**
     * The auto-dimming visor: while the pilot's own Plasma Lance burns, the edges of the view darken by a fifth, eased
     * over 6 ticks in and out, so a bright beam a block from the camera never fills the screen. One set of strips.
     */
    public static void visor(net.minecraft.client.gui.GuiGraphics g, MechaEntity m, float dt) {
        boolean burning = false;
        Arm[] arms = ARMS.get(m);
        if (arms != null) for (Arm a : arms) burning |= a.on && a.module == MechaModule.PLASMA_LANCE;
        visor = Mth.clamp(visor + (burning ? 1 : -1) * dt / IGNITE, 0, 1);
        if (visor <= 0.001f) return;
        int w = g.guiWidth(), h = g.guiHeight(), band = Math.max(8, Math.min(w, h) / 6), steps = 12;
        for (int i = 0; i < steps; i++) {
            float a = 0.2f * visor * (1 - i / (float) steps);
            int c = (int) (a * 255) << 24;
            int x0 = band * i / steps, x1 = band * (i + 1) / steps;
            g.fill(x0, 0, x1, h, c);
            g.fill(w - x1, 0, w - x0, h, c);
            g.fill(x1, x0, w - x1, x1, c);
            g.fill(x1, h - x1, w - x1, h - x0, c);
        }
    }

    /** For the dev harness: tools working now and arcs in flight. */
    public static String state() {
        int working = 0;
        for (Arm[] arms : ARMS.values()) for (Arm a : arms) if (a.on) working++;
        return "arm tools working " + working + ", arcs " + HOPS.size() + ", tractor holds " + HELD.size();
    }
}
