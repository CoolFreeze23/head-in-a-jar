package com.coolfreeze23.headinajar.client.fx;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.fx.Dust;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.fx.FxSounds;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Telekinesis, drawn from what every watcher already has: the holder's synced {@code heldEntity}. A teal tether runs
 * from the head in the jar to the held thing through a control point on a damped spring (it bows behind as the jar
 * turns and overshoots when it stops); the held thing wears a faint shell, hangs with some weight and hums. The memos
 * (grab, throw, set down, drop, fizzle) only say how a hold began or ended.
 */
public final class TetherFx {
    enum Mode { HOLD, THROWN, SET_DOWN, DROPPED, FIZZLE }

    static final class Tether {
        final int holder;
        int held;
        Mode mode = Mode.HOLD;
        /** client ticks since the grab, and since the mode began */
        int age, modeAge;
        int why;
        Vec3 ctrl, ctrlO, vel = Vec3.ZERO;
        Vec3 heldEnd, heldEndO, releaseFrom, fizzleTo, throwVel = Vec3.ZERO;
        int trail;
        boolean landed, sawHeld;
        float shell, shellO;

        Tether(int holder, int held) {
            this.holder = holder;
            this.held = held;
        }

        void mode(Mode m) {
            mode = m;
            modeAge = 0;
        }

        boolean done() {
            return switch (mode) {
                case HOLD -> false;
                case THROWN -> modeAge > 8 && (trail <= 0 || landed);
                case SET_DOWN -> modeAge > 14;
                case DROPPED -> modeAge > (why == Fx.DROP_CHARGE ? 26 : 14);
                case FIZZLE -> modeAge > 9;
            };
        }
    }

    private static final Map<Integer, Tether> TETHERS = new HashMap<>();
    /** What the preview outline would grab, refreshed every 4 ticks for the local jar only. */
    private static AABB preview;
    private static boolean previewOk;

    static Tether tether(int holder) {
        return TETHERS.get(holder);
    }

    /** How strongly {@code e} wears the custody shell now (0 when it isn't held). */
    public static float shell(Entity e, float pt) {
        for (Tether t : TETHERS.values()) {
            if (t.held == e.getId()) return Mth.lerp(pt, t.shellO, t.shell);
        }
        return 0;
    }

    /** True while {@code e} is held by someone's Telekinesis. */
    public static boolean held(Entity e) {
        for (Tether t : TETHERS.values()) if (t.held == e.getId() && t.mode == Mode.HOLD) return true;
        return false;
    }

    /** The holder of {@code e}, or null. */
    public static Entity holderOf(Entity e) {
        var level = Minecraft.getInstance().level;
        for (Tether t : TETHERS.values()) if (t.held == e.getId() && t.mode == Mode.HOLD && level != null) return level.getEntity(t.holder);
        return null;
    }

    // --- memos ---------------------------------------------------------------------------------------------------

    static void grab(Fx fx) {
        Tether t = new Tether(fx.entityA(), fx.entityB());
        t.heldEnd = t.heldEndO = fx.a();
        TETHERS.put(fx.entityA(), t);
        Entity holder = level().getEntity(fx.entityA());
        Vec3 head = holder == null ? fx.a().add(0, 1, 0) : holder.getEyePosition();
        Entity held = level().getEntity(fx.entityB());
        float width = held == null ? 0.8f : Math.max(0.5f, held.getBbWidth());
        Vec3 toHead = head.subtract(fx.a());
        // a ring closes on the target, then motes stream from it to the head
        FxParticle ring = FxParticle.make(FxParticle.Look.RING, fx.a(), Vec3.ZERO);
        if (ring != null) ring.color(FxColors.SIGNAL_PALE).size(width * 1.1f).grow(0.55f).frame(3).life(6).flat(toHead).add();
        int motes = Comfort.particles(8);
        for (int i = 0; i < motes; i++) {
            double a = i * Math.PI * 2 / Math.max(1, motes);
            Vec3 from = fx.a().add(Math.cos(a) * width * 0.6, (i % 3 - 1) * 0.3, Math.sin(a) * width * 0.6);
            FxParticle m = FxParticle.make(FxParticle.Look.MOTE, from, Vec3.ZERO);
            if (m != null) m.color(i % 2 == 0 ? FxColors.SIGNAL_PALE : FxColors.SIGNAL).size(0.06f).life(16).home(head).add();
        }
        FxManager.later(6, () -> {
            Entity h = level().getEntity(fx.entityB());
            Vec3 at = h == null ? fx.a() : h.position().add(0, h.getBbHeight() / 2, 0);
            FxParticle click = FxParticle.make(FxParticle.Look.RING, at, Vec3.ZERO);
            if (click != null) click.color(FxColors.SIGNAL_PALE).size(width * 0.5f).grow(1.4f).life(6).flat(toHead).add();
        });
        if (isMe(fx.entityA())) CameraDesk.fov(-2, 4, 8);
    }

    static void release(Fx fx) {
        Tether t = TETHERS.get(fx.entityA());
        if (t == null) {
            t = new Tether(fx.entityA(), fx.entityB());
            TETHERS.put(fx.entityA(), t);
        }
        if (t.held != fx.entityB()) t.sawHeld = false;
        t.held = fx.entityB();
        t.releaseFrom = t.heldEnd != null ? t.heldEnd : fx.a();
        switch (fx.kind()) {
            case Fx.TK_THROW -> {
                t.mode(Mode.THROWN);
                t.throwVel = fx.b();
                t.trail = 20;
                t.landed = false;
                t.vel = t.vel.add(fx.b().scale(0.6));
                FxParticle ring = FxParticle.make(FxParticle.Look.RING, fx.a(), Vec3.ZERO);
                if (ring != null) ring.color(FxColors.SIGNAL_PALE).size(0.8f).grow(2.6f).life(8).flat(fx.b()).add();
                for (int i = 0; i < Comfort.particles(10); i++) {
                    double ang = i * Math.PI / 5;
                    Vec3 out = fx.b().normalize().scale(0.25).add(Math.cos(ang) * 0.12, Math.sin(ang) * 0.12, Math.sin(ang + 1) * 0.12);
                    FxParticle m = FxParticle.make(FxParticle.Look.MOTE, fx.a(), out);
                    if (m != null) m.color(FxColors.SIGNAL_PALE).size(0.08f).life(10).add();
                }
                if (isMe(fx.entityA())) {
                    CameraDesk.fov(3, 2, 8);
                    CameraDesk.kick(1.2f, 2, 6);
                    CameraDesk.shake(0.08f);
                } else {
                    CameraDesk.nearby(fx.a(), 0.12f);
                }
            }
            case Fx.TK_SET_DOWN -> {
                t.mode(Mode.SET_DOWN);
                FxParticle ring = FxParticle.make(FxParticle.Look.RING, fx.a(), Vec3.ZERO);
                if (ring != null) ring.color(FxColors.SIGNAL).size(0.4f).grow(1.6f).life(8).flat(new Vec3(0, 1, 0)).add();
                for (int i = 0; i < Comfort.particles(3); i++) {
                    FxParticle m = FxParticle.make(FxParticle.Look.MOTE, fx.a().add((i - 1) * 0.25, 0.2, 0), new Vec3(0, 0.02, 0));
                    if (m != null) m.color(FxColors.SIGNAL).size(0.05f).gravity(0.04f).life(14).add();
                }
            }
            default -> {
                t.mode(Mode.DROPPED);
                t.why = (int) fx.arg();
            }
        }
    }

    static void fizzle(Fx fx) {
        Tether t = new Tether(fx.entityA(), -1);
        t.mode(Mode.FIZZLE);
        t.fizzleTo = fx.a();
        t.why = (int) fx.arg();
        TETHERS.put(fx.entityA(), t);
        FxManager.later(3, () -> {
            FxParticle ring = FxParticle.make(FxParticle.Look.RING, fx.a(), Vec3.ZERO);
            boolean machine = t.why == Fx.WHY_MACHINE;
            // company property: a small steady red ring; anything else a grey one
            if (ring != null) ring.color(machine ? FxColors.WARN : FxColors.GREY).size(machine ? 0.25f : 0.45f).grow(machine ? 1 : 1.5f).life(10).steady().add();
            for (int i = 0; i < Comfort.particles(3); i++) {
                FxParticle s = FxParticle.make(FxParticle.Look.STATIC, fx.a().add((i - 1) * 0.22, (i % 2) * 0.2, 0.1 * i), Vec3.ZERO);
                if (s != null) s.color(FxColors.GREY).size(0.09f).life(8).add();
            }
            if (t.why == Fx.WHY_HARD) {
                var state = level().getBlockState(net.minecraft.core.BlockPos.containing(fx.a()));
                if (!state.isAir()) {
                    for (int i = 0; i < Comfort.particles(4); i++) {
                        level().addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                                fx.a().x, fx.a().y, fx.a().z, (i - 1.5) * 0.06, 0.1, (i % 2 - 0.5) * 0.1);
                    }
                }
            }
        });
    }

    // --- every tick ----------------------------------------------------------------------------------------------

    public static void tick() {
        var level = level();
        if (level == null) {
            TETHERS.clear();
            return;
        }
        for (Player p : level.players()) {
            JarState s = p.getData(ModRegistry.JAR_STATE);
            int held = s.heldEntity();
            Tether t = TETHERS.get(p.getId());
            if (held >= 0) {
                if (t == null || t.mode != Mode.HOLD || t.held != held) {
                    // a hold this client saw begin without a memo (a late joiner, or the memo went to someone nearer)
                    if (t == null || t.mode != Mode.HOLD) {
                        t = new Tether(p.getId(), held);
                        t.age = 8;
                        TETHERS.put(p.getId(), t);
                    }
                    t.held = held;
                }
            } else if (t != null && t.mode == Mode.HOLD && t.age > 2) {
                // the hold ended and no memo said how: let it go slack
                t.releaseFrom = t.heldEnd;
                t.mode(Mode.SET_DOWN);
            }
        }
        for (Iterator<Tether> it = TETHERS.values().iterator(); it.hasNext(); ) {
            Tether t = it.next();
            Entity holder = level.getEntity(t.holder);
            if (holder == null || t.done()) {
                it.remove();
                continue;
            }
            t.age++;
            t.modeAge++;
            Entity held = t.held >= 0 ? level.getEntity(t.held) : null;
            t.heldEndO = t.heldEnd;
            if (held != null) {
                t.sawHeld = true;
                t.heldEnd = centre(held, 1);
            }
            if (t.heldEnd == null) t.heldEnd = t.fizzleTo != null ? t.fizzleTo : holder.getEyePosition();
            if (t.heldEndO == null) t.heldEndO = t.heldEnd;
            spring(t, start(holder, 1), t.mode == Mode.THROWN && t.modeAge > 2 ? start(holder, 1) : t.heldEnd);
            t.shellO = t.shell;
            float want = t.mode == Mode.HOLD && t.age > 5 ? 1 : 0;
            t.shell += Mth.clamp(want - t.shell, -1 / 8f, 1 / 6f);
            if (t.mode == Mode.HOLD && held != null) holdTick(t, held);
            if (t.mode == Mode.THROWN && held != null && t.trail > 0 && !t.landed) {
                t.trail--;
                FxParticle m = FxParticle.make(FxParticle.Look.MOTE, centre(held, 0), Vec3.ZERO);
                if (m != null) m.color(FxColors.SIGNAL).size(0.07f).life(10).add();
                if (held.onGround() && t.modeAge > 2 || held.isInWater()) land(t, held);
            } else if (t.mode == Mode.THROWN && held == null && t.sawHeld && !t.landed && t.modeAge > 2) {
                // a thrown block becomes a falling block, then a block: the dust is where it was last seen
                t.landed = true;
                Dust.client(level, t.heldEnd, Comfort.particles(10), 1.2f);
            }
        }
        previewTick();
    }

    private static void land(Tether t, Entity held) {
        t.landed = true;
        Vec3 at = held.position();
        Dust.client(level(), at, Comfort.particles(12), held.getBbWidth() + 0.8f);
        if (held instanceof LivingEntity) {
            for (int i = 0; i < 2; i++) {
                FxParticle ring = FxParticle.make(FxParticle.Look.RING, at.add(0, 0.05 + i * 0.25, 0), Vec3.ZERO);
                if (ring != null) ring.color(FxColors.SIGNAL).size(held.getBbWidth() * 0.6f + i * 0.2f).grow(1.8f).life(8 + i * 2).flat(new Vec3(0, 1, 0)).add();
            }
        }
        CameraDesk.nearby(at, 0.1f);
    }

    private static void holdTick(Tether t, Entity held) {
        // the held thing hangs: its legs stop walking
        if (held instanceof LivingEntity living) living.walkAnimation.setSpeed(0);
        float size = held.getBbWidth() * held.getBbWidth() * held.getBbHeight();
        float heavy = Mth.clamp(size, 0, 1.5f) / 1.5f;
        // a hum that labours with the size of what is held: lower and wobblier for a cow than for a pebble
        float time = t.age;
        float wobble = (0.015f + 0.07f * heavy) * Mth.sin(time * (0.5f - 0.3f * heavy));
        float pitch = 1.18f - 0.38f * heavy + wobble;
        SoundLoops.want("tk:" + t.holder, FxSounds.TK_HOLD.get(), SoundSource.PLAYERS, centre(held, 1), 0.55f + 0.45f * heavy, pitch);
        if (held instanceof ItemEntity && t.age % 2 == 0) {
            // a held item: four motes orbit it like a tiny atom
            for (int i = 0; i < 4; i++) {
                double a = t.age * 0.35 + i * Math.PI / 2;
                FxParticle m = FxParticle.make(FxParticle.Look.MOTE, centre(held, 1).add(Math.cos(a) * 0.3, Math.sin(a * 0.7) * 0.15, Math.sin(a) * 0.3), Vec3.ZERO);
                if (m != null) m.color(FxColors.SIGNAL_PALE).size(0.04f).life(3).steady().add();
            }
        }
        if (t.age % 40 == 0 && Comfort.particles(1) > 0) {
            FxParticle m = FxParticle.make(FxParticle.Look.MOTE, centre(held, 1).add(0, held.getBbHeight() * 0.4, 0), new Vec3(0, 0.01, 0));
            if (m != null) m.color(FxColors.SIGNAL_PALE).size(0.06f).life(12).add();
        }
    }

    /** The spring that carries the tether's control point: stiffness 40/s, damping 0.7, chasing the midpoint. */
    private static void spring(Tether t, Vec3 a, Vec3 b) {
        Vec3 mid = a.add(b).scale(0.5);
        double len = a.distanceTo(b);
        float slack = t.mode == Mode.SET_DOWN || t.mode == Mode.DROPPED && t.modeAge > (t.why == Fx.DROP_CHARGE ? 12 : 4) ? 1 : 0;
        Vec3 rest = mid.subtract(0, len * (0.06 + 0.35 * slack), 0);
        if (t.ctrl == null) t.ctrl = t.ctrlO = rest;
        t.ctrlO = t.ctrl;
        double k = slack > 0 ? 6 : 40, c = 2 * 0.7 * Math.sqrt(k), dt = 0.05 / 4;
        for (int i = 0; i < 4; i++) {
            Vec3 acc = rest.subtract(t.ctrl).scale(k).subtract(t.vel.scale(c));
            t.vel = t.vel.add(acc.scale(dt));
            t.ctrl = t.ctrl.add(t.vel.scale(dt));
        }
    }

    // --- drawing -------------------------------------------------------------------------------------------------

    private static final Beams.Ribbon HOLD_LOOK = new Beams.Ribbon(0x5aa6a4, FxColors.SIGNAL_PALE, 1.5f / 16, 2.5f / 16, 1f, 0);
    private static final Beams.Ribbon FIZZLE_LOOK = new Beams.Ribbon(FxColors.GREY_DARK, FxColors.GREY, 1 / 16f, 1 / 16f, 0.8f, 0);

    public static void render(Beams.Frame f) {
        var level = level();
        if (level == null) return;
        for (Tether t : TETHERS.values()) {
            Entity holder = level.getEntity(t.holder);
            if (holder == null || t.ctrl == null) continue;
            float pt = f.partialTick();
            Vec3 a = start(holder, pt);
            Vec3 ctrl = t.ctrlO.lerp(t.ctrl, pt);
            Entity held = t.held >= 0 ? level.getEntity(t.held) : null;
            Vec3 b = held != null ? centre(held, pt) : t.heldEndO.lerp(t.heldEnd, pt);
            float m = t.modeAge + pt;
            float scroll = (t.age + pt) * 0.09f;
            switch (t.mode) {
                case HOLD -> {
                    // the tether shoots out from the head in 3 ticks, its tip leading
                    float reach = Mth.clamp((t.age + pt - 2) / 3f, 0, 1);
                    if (reach <= 0) break;
                    Vec3[] pts = Beams.curve(a, ctrl, b, 12);
                    // seen from the holder's own eyes the line starts thin, so it never fills the bottom of the view
                    Beams.Ribbon look = mine(holder) ? new Beams.Ribbon(HOLD_LOOK.sheath(), HOLD_LOOK.core(), 1 / 96f, HOLD_LOOK.width1(), HOLD_LOOK.alpha(), 0) : HOLD_LOOK;
                    Beams.ribbon(f, pts, withScroll(look, scroll, 1), s -> s <= reach ? 1 : 0);
                    if (reach < 1) {
                        // the tip leads with a bright pulse while it shoots out
                        Beams.bead(f, Beams.along(pts, reach), FxColors.SIGNAL_PALE, 0.3f, 1);
                    } else {
                        // power travelling down the line, two pulses a second from the head to the held thing
                        for (int k = 0; k < 2; k++) {
                            double along = ((t0(t) + pt) % 20 + k * 10) % 20 / 20.0;
                            Beams.bead(f, Beams.along(pts, along), FxColors.SIGNAL_PALE, 0.16f, 0.9f);
                        }
                    }
                    if (held != null) boxShell(f, held, t, pt);
                }
                case THROWN -> {
                    // the line whips: the control point flies ahead for 2 ticks, then the line snaps back into the
                    // goggles over 4 ticks, thinning
                    float back = Mth.clamp((m - 2) / 4f, 0, 1);
                    Vec3 end = t.releaseFrom.add(t.throwVel.scale(Math.min(m, 2) * 0.5)).lerp(a, back * back);
                    Vec3[] pts = Beams.curve(a, ctrl, end, 12);
                    float thin = 1 - back;
                    Beams.ribbon(f, pts, new Beams.Ribbon(FxColors.SIGNAL_DARK, FxColors.SIGNAL_PALE, HOLD_LOOK.width0() * thin, HOLD_LOOK.width1() * thin,
                            0.95f * (0.3f + 0.7f * thin), scroll), null);
                }
                case SET_DOWN -> {
                    // slack: it sags toward the ground for 6 ticks, then fades from the held end back to the head
                    Vec3 end = held != null ? b : t.releaseFrom;
                    float gone = Mth.clamp((m - 6) / 6f, 0, 1);
                    Vec3[] pts = Beams.curve(a, ctrl, end, 12);
                    Beams.ribbon(f, pts, withScroll(HOLD_LOOK, scroll, 0.8f), s -> s < 1 - gone ? 1 : Math.max(0, 1 - (s - (1 - gone)) * 8));
                    if (held != null) boxShell(f, held, t, pt);
                }
                case DROPPED -> dropped(f, t, a, ctrl, held != null ? b : t.releaseFrom, m, scroll);
                case FIZZLE -> {
                    // out to the target in 3 ticks, fails to latch, curls back in 4 like a tape measure
                    float reach = m < 3 ? m / 3 : Math.max(0, 1 - (m - 3) / 4f);
                    Vec3 to = t.why == Fx.WHY_NOTHING ? t.fizzleTo.subtract(0, (m > 3 ? m - 3 : 0) * 0.15, 0) : t.fizzleTo;
                    Vec3 side = to.subtract(a).cross(new Vec3(0, 1, 0)).normalize().scale(m > 3 ? (m - 3) * 0.15 : 0);
                    Vec3 c2 = a.add(to).scale(0.5).add(side).subtract(0, 0.1, 0);
                    Vec3[] pts = Beams.curve(a, c2, to, 10);
                    Beams.ribbon(f, pts, FIZZLE_LOOK, s -> s <= reach ? 1 : 0);
                }
            }
        }
        renderPreview(f);
    }

    private static void dropped(Beams.Frame f, Tether t, Vec3 a, Vec3 ctrl, Vec3 b, float m, float scroll) {
        Vec3[] pts = Beams.curve(a, ctrl, b, 12);
        if (t.why == Fx.DROP_CHARGE) {
            // out of charge: the line dims twice, 7 ticks apart, then goes slack and fades
            float dim = (m < 2 || m >= 7 && m < 9) ? 0.3f : 1;
            float gone = Mth.clamp((m - 14) / 10f, 0, 1);
            Beams.ribbon(f, pts, withScroll(HOLD_LOOK, scroll, dim * (1 - gone)), null);
        } else if (t.why == Fx.DROP_RANGE) {
            // too far: the line stretches thin, then snaps in the middle and both halves recoil
            if (m < 3) {
                float thin = 1 - m / 4;
                Beams.ribbon(f, pts, new Beams.Ribbon(FxColors.SIGNAL_DARK, FxColors.SIGNAL_PALE, HOLD_LOOK.width0() * thin, HOLD_LOOK.width1() * thin, 0.95f, scroll), null);
            } else {
                float r = Mth.clamp((m - 3) / 8f, 0, 1);
                float half = 0.5f * (1 - r);
                float alpha = 1 - r;
                Beams.ribbon(f, pts, withScroll(HOLD_LOOK, scroll, alpha), s -> s < half ? 1 : 0);
                Beams.ribbon(f, pts, withScroll(HOLD_LOOK, scroll, alpha), s -> s > 1 - half ? 1 : 0);
            }
        } else {
            Beams.ribbon(f, pts, withScroll(HOLD_LOOK, scroll, Math.max(0, 1 - m / 8)), null);
        }
    }

    private static Beams.Ribbon withScroll(Beams.Ribbon r, float scroll, float alpha) {
        return new Beams.Ribbon(r.sheath(), r.core(), r.width0(), r.width1(), r.alpha() * alpha, scroll);
    }

    /**
     * A shell drawn around the bounding box, for held things that have no living model to wear one (blocks, items,
     * a placed head, a player in a jar).
     */
    private static void boxShell(Beams.Frame f, Entity held, Tether t, float pt) {
        if (held instanceof LivingEntity && !(held instanceof Player p && p.getData(ModRegistry.JAR_STATE).form().isJarred())) return;
        float alpha = Mth.lerp(pt, t.shellO, t.shell);
        if (alpha <= 0.01f) return;
        AABB box = held.getBoundingBox().move(held.getPosition(pt).subtract(held.position())).inflate(held instanceof ItemEntity ? 0.12 : 0.04);
        if (f.frustum() != null && !f.frustum().isVisible(box)) return;
        float time = held.tickCount + pt;
        var vc = f.buffers().getBuffer(ShellLayer.type((time * 0.01f) % 1, (time * 0.01f) % 1));
        int c = ShellLayer.color(alpha);
        float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f;
        var mat = f.pose().last().pose();
        var pose = f.pose().last();
        Vec3 o = f.camera();
        float x0 = (float) (box.minX - o.x), y0 = (float) (box.minY - o.y), z0 = (float) (box.minZ - o.z);
        float x1 = (float) (box.maxX - o.x), y1 = (float) (box.maxY - o.y), z1 = (float) (box.maxZ - o.z);
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0}, {x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1},
                {x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1}, {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0},
                {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}, {x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0}};
        for (float[] q : faces) {
            float[] uv = {0, 0, 1, 0, 1, 1, 0, 1};
            for (int i = 0; i < 4; i++) {
                vc.addVertex(mat, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).setColor(r, g, b, 1).setUv(uv[i * 2], uv[i * 2 + 1])
                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
            }
        }
    }

    // --- the preview outline -------------------------------------------------------------------------------------

    private static void previewTick() {
        var mc = Minecraft.getInstance();
        Player me = mc.player;
        if (me == null || mc.level.getGameTime() % 4 != 0) return;
        JarState s = me.getData(ModRegistry.JAR_STATE);
        preview = null;
        if (s.form() != Form.JAR || s.heldEntity() >= 0 || !s.fitted(ModuleType.TELEKINESIS)) return;
        double range = 16;
        Vec3 eye = me.getEyePosition(), look = me.getLookAngle(), end = eye.add(look.scale(range));
        var blockHit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, me));
        double reach = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? range : blockHit.getLocation().distanceTo(eye);
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(me, eye, eye.add(look.scale(reach)),
                me.getBoundingBox().expandTowards(look.scale(reach)).inflate(1), e -> e != me && !e.isSpectator() && e.isAlive()
                        && (e instanceof LivingEntity || e instanceof ItemEntity || e instanceof com.coolfreeze23.headinajar.entity.HeadJarEntity), reach * reach);
        if (hit != null) {
            Entity e = hit.getEntity();
            preview = e.getBoundingBox();
            // other players and their heads need their trust, which this client can't see: shown grey-teal
            previewOk = !e.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES) && !(e instanceof Player p && p.isCreative());
        } else if (blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            var pos = blockHit.getBlockPos();
            var state = mc.level.getBlockState(pos);
            if (state.isAir()) return;
            float hard = state.getDestroySpeed(mc.level, pos);
            previewOk = !state.hasBlockEntity() && hard >= 0 && hard <= 50
                    && !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART);
            var shape = state.getShape(mc.level, pos);
            preview = (shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds()).move(pos);
        }
    }

    /** Corner brackets on what the ability would grab: teal if it can, grey if not; breathing slowly, never flashing. */
    private static void renderPreview(Beams.Frame f) {
        if (preview == null || Minecraft.getInstance().options.hideGui) return;
        AABB box = preview.inflate(0.03);
        if (f.frustum() != null && !f.frustum().isVisible(box)) return;
        float breathe = 0.4f + 0.2f * Mth.sin(f.time() * Mth.TWO_PI / 40f);
        int rgb = previewOk ? FxColors.SIGNAL : FxColors.GREY;
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        var vc = f.buffers().getBuffer(RenderType.lines());
        var pose = f.pose().last();
        Vec3 o = f.camera();
        double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        double lx = Math.min(0.3, box.getXsize() / 3), ly = Math.min(0.3, box.getYsize() / 3), lz = Math.min(0.3, box.getZsize() / 3);
        for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) for (int k = 0; k < 2; k++) {
            double x = xs[i], y = ys[j], z = zs[k];
            double sx = i == 0 ? lx : -lx, sy = j == 0 ? ly : -ly, sz = k == 0 ? lz : -lz;
            line(vc, pose, o, x, y, z, x + sx, y, z, r, g, b, breathe, 1, 0, 0);
            line(vc, pose, o, x, y, z, x, y + sy, z, r, g, b, breathe, 0, 1, 0);
            line(vc, pose, o, x, y, z, x, y, z + sz, r, g, b, breathe, 0, 0, 1);
        }
    }

    private static void line(com.mojang.blaze3d.vertex.VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose pose, Vec3 o,
                             double x0, double y0, double z0, double x1, double y1, double z1, float r, float g, float b, float a, float nx, float ny, float nz) {
        vc.addVertex(pose, (float) (x0 - o.x), (float) (y0 - o.y), (float) (z0 - o.z)).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, (float) (x1 - o.x), (float) (y1 - o.y), (float) (z1 - o.z)).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }

    // --- points --------------------------------------------------------------------------------------------------

    /**
     * Where the tether leaves the holder: the head's eyes in the jar; for the local player in first person, from just
     * below the crosshair (the near-camera fade does the rest).
     */
    static Vec3 start(Entity holder, float pt) {
        Vec3 eye = holder.getEyePosition(pt);
        var mc = Minecraft.getInstance();
        if (holder == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return eye.add(holder.getViewVector(pt).scale(0.35)).subtract(0, 0.22, 0);
        }
        return eye;
    }

    static Vec3 centre(Entity e, float pt) {
        Vec3 p = e.getPosition(pt);
        double h = e instanceof ItemEntity ? 0.25 : e.getBbHeight() / 2;
        return p.add(0, h, 0);
    }

    private static int t0(Tether t) {
        return t.age;
    }

    private static boolean mine(Entity holder) {
        var mc = Minecraft.getInstance();
        return holder == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    private static boolean isMe(int id) {
        var p = Minecraft.getInstance().player;
        return p != null && p.getId() == id;
    }

    private static net.minecraft.client.multiplayer.ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    /** For the harness: how many tethers are live. */
    public static int live() {
        return TETHERS.size();
    }

    private TetherFx() {
    }
}
