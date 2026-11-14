package com.coolfreeze23.headinajar.entity;

import com.coolfreeze23.headinajar.anim.SeekingController;
import com.coolfreeze23.headinajar.mecha.MechaRig;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;

import java.util.HashMap;
import java.util.Map;

/**
 * Which clip the Lunk (a Utility Shell on the heavy-biped skeleton: a filing cabinet with gorilla arms) plays, and at
 * which tick, from its synced moments and what this client measures of its motion. Three controllers, in the model's
 * order: {@code main} (parked, flat, the toss, the knuckle-walk, the tic, idle), {@code cockpit} (the jar cupped up onto
 * the lid, and lifted down to the floor in front when it gets off), {@code hit} (the flinch).
 */
public final class LunkAnimation {
    private static final Map<String, MechaRig.Clip> CLIPS = new HashMap<>();

    static {
        String table = "parked 160L, board 26, alight 30, idle 120L, idle_tic 30, walk 12L, walk_fast 12L, toss 30, toss_refused 22,"
                + " hurt 10, flat 40, destroyed 40";
        for (String entry : table.split("\\s*,\\s*")) {
            String[] p = entry.trim().split(" ");
            boolean loop = p[1].endsWith("L");
            CLIPS.put(p[0], MechaRig.Clip.of(p[0], Integer.parseInt(loop ? p[1].substring(0, p[1].length() - 1) : p[1]), loop));
        }
    }

    /** The jar's base on the lid, blocks above the Lunk's feet (its jar_anchor, 30.4 px). */
    public static final double JAR_BASE = 30.4 / 16;
    /** The collapse's length: the shell is removed when it ends. */
    public static final int DESTROYED = 40;
    /** The knuckle-walk was authored at the ridden speed (0.18 x {@link ShellEntity#RIDDEN_SCALE} x 2.16 blocks a tick). */
    public static final double WALK_DESIGN = 0.194;
    /** The toss: the fists close under the load at 8 and let go at 19 (the push comes then). */
    public static final int TOSS_GRAB = 8, TOSS_RELEASE = 19, TOSS_TICKS = 30, REFUSED_TICKS = 22;
    /** How fast a tossed load leaves forward at the release (blocks a tick), clearing the cabinet it was held over. */
    public static final double TOSS_FORWARD = 0.35;
    /**
     * The fists' grip point through the toss (the middle between the two grips, model pixels, facing north: -z is
     * ahead), sampled from the clip at ticks 8, 11, 14 and 18: under the load in front, up past the face, overhead.
     */
    private static final double[][] GRIP = {{8, 9.6, -23.5}, {11, 36.1, -24.7}, {14, 52.6, -3.5}, {18, 53.4, -1.9}, {19, 53.4, -1.9}};

    /** Where the load's feet are at toss tick t: on the fists (2 px above the grip). */
    public static net.minecraft.world.phys.Vec3 grip(double t) {
        for (int i = 0; i < GRIP.length - 1; i++) {
            double[] a = GRIP[i], b = GRIP[i + 1];
            if (t <= b[0] || i == GRIP.length - 2) {
                double k = Math.max(0, Math.min(1, (t - a[0]) / (b[0] - a[0])));
                return new net.minecraft.world.phys.Vec3(0, a[1] + (b[1] - a[1]) * k + 2, a[2] + (b[2] - a[2]) * k);
            }
        }
        return new net.minecraft.world.phys.Vec3(0, GRIP[0][1] + 2, GRIP[0][2]);
    }

    /** Getting off: the jar is drawn in the fists until it is let go on the floor in front (tick 17). */
    public static final int ALIGHT_RELEASE = 17, ALIGHT_TICKS = 30, BOARD_TICKS = 26;
    /** Bones whose world matrices the effects read, and the lamps drawn again through the glow mask. */
    public static final String[] TRACKED = {"jar_anchor", "torso", "hand_l", "hand_r", "foot_l", "foot_r", "sensor"};
    /** Effect locators: bone and place in the model (pixels, facing north). */
    public static final Map<String, Object[]> LOCATORS = Map.of(
            "fx_step_l", new Object[]{"foot_l", -5.0, -2.0, -1.0}, "fx_step_r", new Object[]{"foot_r", 5.0, -2.0, -1.0},
            "fx_knuckle_l", new Object[]{"hand_l", -12.5, 1.0, -1.5}, "fx_knuckle_r", new Object[]{"hand_r", 12.5, 1.0, -1.5},
            "fx_core", new Object[]{"sensor", 5.0, 31.5, -12.0}, "fx_hull", new Object[]{"torso", 0.0, 30.4, 0.0},
            "fx_lamp_tail", new Object[]{"torso", 6.0, 27.0, 9.0}, "fx_jar_exit", new Object[]{"torso", 0.0, 48.4, 0.0});

    private LunkAnimation() {
    }

    public static MechaRig.Clip clip(String name) {
        return CLIPS.get(name);
    }

    private static RoverAnimation.Choice at(String clip, double tick, int blend) {
        return new RoverAnimation.Choice(CLIPS.get(clip), tick, blend);
    }

    public static boolean piloted(ShellEntity s) {
        return s.getFirstPassenger() instanceof Player;
    }

    /** The jar aboard is out of charge: the Lunk winds down and slumps (it cannot move or toss). */
    public static boolean flat(ShellEntity s) {
        return s.getFirstPassenger() instanceof Player p && p.hasData(ModRegistry.JAR_STATE) && p.getData(ModRegistry.JAR_STATE).charge() <= 0;
    }

    /** Ticks since the last toss moment, or -1. */
    static double sinceToss(ShellEntity s, double now) {
        long at = s.tossAt();
        return at < 0 ? -1 : now - at / 4;
    }

    /** Ticks into getting off while the jar is still in the fists, else -1. */
    public static double alighting(ShellEntity s, double now) {
        long boarded = s.boardedAt();
        if (piloted(s) || boarded >= -1) return -1;
        double t = now + boarded;
        return t >= 0 && t < ALIGHT_RELEASE ? t : -1;
    }

    /** Nobody aboard and nothing happening (no getting off, toss, blow or collapse): drawn parked, settled, still. */
    public static boolean still(ShellEntity s, double now) {
        if (piloted(s) || s.isDeadOrDying() || s.hurtTime > 0) return false;
        double toss = sinceToss(s, now);
        if (toss >= 0 && toss < TOSS_TICKS) return false;
        long boarded = s.boardedAt();
        return !(boarded < -1 && now + boarded < ALIGHT_TICKS + 10);
    }

    /** What this client measures of a Lunk: its pace (blocks a tick, chased) and the walk's phase. */
    static void measure(ShellEntity s, RoverAnimation.View v, double now) {
        if (v.lunkLast < 0 || now < v.lunkLast || now - v.lunkLast > 10) {
            v.lunkLast = now;
            v.lunkX = s.getX();
            v.lunkZ = s.getZ();
            return;
        }
        double dt = now - v.lunkLast;
        if (dt <= 0) return;
        if (s.level().getGameTime() != v.lunkTick) {
            // once a game tick: the distance moved since the last
            v.lunkTick = s.level().getGameTime();
            double d = Math.hypot(s.getX() - v.lunkX, s.getZ() - v.lunkZ);
            v.lunkX = s.getX();
            v.lunkZ = s.getZ();
            v.lunkSpeed += (d - v.lunkSpeed) * 0.5;
        }
        v.lunkPhase = (v.lunkPhase + dt * Math.max(0.25, Math.min(2.5, v.lunkSpeed / WALK_DESIGN))) % 1440;
        v.lunkLast = now;
        if (flat(s)) {
            if (v.flatSince < 0) v.flatSince = now;
        } else v.flatSince = -1;
    }

    /** The whole shell's clip at {@code now}. */
    public static RoverAnimation.Choice main(ShellEntity s, RoverAnimation.View v, double now) {
        double frac = now - Math.floor(now);
        if (s.isDeadOrDying()) return at("destroyed", s.deathTime + frac, 0);
        if (still(s, now)) return at("parked", 0, 0);
        if (!piloted(s)) return at("parked", now, 5);
        double toss = sinceToss(s, now);
        if (toss >= 0) {
            boolean refused = (s.tossAt() & 3) == ShellEntity.TOSS_REFUSED;
            if (!refused && toss < TOSS_TICKS) return at("toss", toss, 2);
            if (refused && toss < REFUSED_TICKS) return at("toss_refused", toss, 2);
        }
        // a flat jar: it winds down and slumps into the parked pose, and stays there until charged
        if (v.flatSince >= 0) return at("flat", now - v.flatSince, 3);
        if (v.lunkSpeed > 0.02) return at("walk", v.lunkPhase, 4);
        int cycle = 200 + Math.floorMod(s.getId(), 100);
        double tic = ((now + s.getId() * 37L) % cycle + cycle) % cycle;
        if (tic < clip("idle_tic").length()) return at("idle_tic", tic, 3);
        return at("idle", now, 5);
    }

    /** The jar cupped up onto the lid, or lifted off and down to the floor in front. */
    public static RoverAnimation.Choice cockpit(ShellEntity s, double now) {
        long boarded = s.boardedAt();
        if (piloted(s) && boarded >= 0 && now - boarded < BOARD_TICKS) return at("board", now - boarded, 0);
        if (!piloted(s) && boarded < -1 && now + boarded >= 0 && now + boarded < ALIGHT_TICKS && !s.isDeadOrDying()) return at("alight", now + boarded, 0);
        return null;
    }

    public static RoverAnimation.Choice hit(ShellEntity s, double now) {
        if (s.isDeadOrDying() || s.hurtTime <= 0) return null;
        return at("hurt", 10 - s.hurtTime + (now - Math.floor(now)), 0);
    }

    public static void register(ShellEntity s, AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(controller(s, "main", LunkAnimation::mainState));
        controllers.add(controller(s, "cockpit", st -> pick(st, cockpit(st.getAnimatable(), now(st)))));
        controllers.add(controller(s, "hit", st -> pick(st, hit(st.getAnimatable(), now(st)))));
    }

    private static SeekingController<ShellEntity> controller(ShellEntity s, String name,
                                                             software.bernie.geckolib.animation.AnimationController.AnimationStateHandler<ShellEntity> handler) {
        SeekingController<ShellEntity> c = new SeekingController<>(s, name, handler);
        c.setSoundKeyframeHandler(event -> com.coolfreeze23.headinajar.client.RoverClient.sound(event));
        c.setParticleKeyframeHandler(event -> com.coolfreeze23.headinajar.client.RoverClient.particle(event));
        c.setCustomInstructionKeyframeHandler(event -> com.coolfreeze23.headinajar.client.RoverClient.instruction(event));
        return c;
    }

    private static double now(AnimationState<ShellEntity> state) {
        return state.getAnimatable().level().getGameTime() + state.getPartialTick();
    }

    private static PlayState mainState(AnimationState<ShellEntity> state) {
        ShellEntity s = state.getAnimatable();
        double now = now(state);
        if (s.getFirstPassenger() instanceof Player p) s.view.lastPilot = p.getId();
        measure(s, s.view, now);
        return pick(state, main(s, s.view, now));
    }

    @SuppressWarnings("unchecked")
    private static PlayState pick(AnimationState<ShellEntity> state, RoverAnimation.Choice choice) {
        SeekingController<ShellEntity> c = (SeekingController<ShellEntity>) state.getController();
        if (choice == null) return c.none();
        return c.show(choice.clip().name(), choice.clip().raw(), choice.clip().at(choice.tick()), choice.blend());
    }
}
