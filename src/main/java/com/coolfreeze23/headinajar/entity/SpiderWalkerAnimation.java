package com.coolfreeze23.headinajar.entity;

import com.coolfreeze23.headinajar.anim.SeekingController;
import com.coolfreeze23.headinajar.mecha.Legs;
import com.coolfreeze23.headinajar.mecha.MechaRig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;

import java.util.HashMap;
import java.util.Map;

/**
 * Which clip the Spider Walker (a Utility Shell on the multi-leg skeleton, eight legs) plays, and at which tick, from
 * its synced moments and what this client measures of its motion. The legs are solved in its clips for flat ground and
 * again in code on uneven ground ({@link Legs}). It climbs upright: the body stays level and the legs walk up the
 * wall's face. Four controllers, in the model's order: {@code main}, {@code cockpit} (hopping on and off), {@code web}
 * (the ability and its dud, over everything), {@code hit} (the flinch).
 */
public final class SpiderWalkerAnimation {
    /** A clip at a tick, entered with a blend of so many ticks. */
    public record Choice(MechaRig.Clip clip, double tick, int blend) {
    }

    private static final Map<String, MechaRig.Clip> CLIPS = new HashMap<>();

    static {
        String table = "parked 80L, board 22, get_off 20, idle 40L, idle_tic 36, walk 6L, walk_back 12L, turn_l 8L, turn_r 8L, climb 8L,"
                + " climb_top 14, jump 10, fall 12L, land 12, web 18, web_dud 22, hurt 10, limp 16L, destroyed 40";
        for (String entry : table.split("\\s*,\\s*")) {
            String[] p = entry.trim().split(" ");
            boolean loop = p[1].endsWith("L");
            CLIPS.put(p[0], MechaRig.Clip.of(p[0], Integer.parseInt(loop ? p[1].substring(0, p[1].length() - 1) : p[1]), loop));
        }
    }

    /** Eight legs at the tin's sides: thigh 10, shin 16, hips 8 px up; the body tilts on a slope to 8 degrees. */
    public static final Legs.Spec LEGS = new Legs.Spec(new String[]{"fl", "l2", "l3", "rl", "fr", "r2", "r3", "rr"},
            new double[]{-7.5, -9.5, -9.5, -7.5, 7.5, 9.5, 9.5, 7.5}, new double[]{-7.5, -2.5, 2.5, 7.5, -7.5, -2.5, 2.5, 7.5}, 8, 10, 16, 8, 15.5);
    /** The two sets of four that step together (the walk's {@code step:a} and {@code step:b}). */
    public static final String[] SET_A = {"fl", "r2", "l3", "rr"}, SET_B = {"fr", "l2", "r3", "rl"};
    /** Blocks a tick the walk, the backing gait, the limp and the climb were authored at. */
    public static final double WALK = 0.34, BACK = 0.085, LIMP = 0.085, CLIMB = 0.2;
    /** The collapse's length: the shell is removed when it ends. */
    public static final int DESTROYED = 40;
    /** The get-off clip hides its stand-in jar from this tick: the real jar is drawn hopping off until then. */
    public static final int GET_OFF_HOP = 10;
    /** The web's spit lands on the web clip's tick 4: the cobweb is placed then. */
    public static final int SPIT = 4;
    /** Its effects' locators: the bone each rides and its place in the model (px, facing north). */
    public static final Map<String, MechaRig.Locator> LOCATORS = new HashMap<>();

    static {
        Object[][] table = {{"fx_nozzle", "launcher", 0.0, 5.5, -12.0}, {"fx_eye", "lamp_core", 0.0, 8.5, -11.0}, {"fx_hull", "body", 0.0, 14.0, 0.0},
                {"fx_belly", "body", 0.0, 2.75, 0.0}, {"fx_key", "reel", 0.0, 8.0, 14.0}, {"fx_tail_l", "lamp_tail", -3.5, 9.25, 10.0},
                {"fx_tail_r", "lamp_tail", 3.5, 9.25, 10.0}, {"fx_jar_exit", "body", 0.0, 0.0, 20.0},
                {"fx_foot_fl", "shin_fl", -7.5, 8.0, -33.5}, {"fx_foot_l2", "shin_l2", -9.5, 8.0, -28.5}, {"fx_foot_l3", "shin_l3", -9.5, 8.0, -23.5},
                {"fx_foot_rl", "shin_rl", -7.5, 8.0, -18.5}, {"fx_foot_fr", "shin_fr", 7.5, 8.0, -33.5}, {"fx_foot_r2", "shin_r2", 9.5, 8.0, -28.5},
                {"fx_foot_r3", "shin_r3", 9.5, 8.0, -23.5}, {"fx_foot_rr", "shin_rr", 7.5, 8.0, -18.5}};
        for (Object[] l : table) LOCATORS.put((String) l[0], new MechaRig.Locator((String) l[1], new net.minecraft.world.phys.Vec3((double) l[2], (double) l[3], (double) l[4])));
    }

    /** What a client measures of one Spider Walker. */
    public static final class View {
        double lastNow = Double.NaN;
        long lastTick = Long.MIN_VALUE;
        /** Measured horizontal speed and the speed along its facing (blocks a tick, chased), the body's turn rate. */
        public double speed, forward;
        public float turn;
        float lastYaw = Float.NaN;
        public double walkPhase, backPhase, limpPhase;
        boolean wasOnGround = true;
        public int airTicks;
        public double jumpAt = -1, landAt = -1;
        /** The wall it climbs, and the pads on the ground under them. */
        public final Legs.Climb climb = new Legs.Climb();
        public final Legs.Pose legs = new Legs.Pose();
        /** The nozzle's chased aim (degrees). */
        public float aimYaw, aimPitch;
        /** The player last seen riding (the get-off clip draws their jar hopping off), by entity id. */
        public int lastPilot = -1;
        /** The bones' world matrices as last drawn (the effects' locators). */
        public final Map<String, org.joml.Matrix4f> bones = new HashMap<>();
        double dt;

        public double frameDelta() {
            return dt;
        }

        /** Brings the view up to {@code now} (game time plus partial tick). */
        public void update(ShellEntity s, double now) {
            dt = Double.isNaN(lastNow) ? 0 : Mth.clamp(now - lastNow, 0, 5);
            lastNow = now;
            double k = 1 - Math.exp(-dt * 10 / 20.0);
            double dx = s.getX() - s.xo, dz = s.getZ() - s.zo;
            speed += (Math.sqrt(dx * dx + dz * dz) - speed) * k;
            if (speed < 1e-4) speed = 0;
            float yaw = s.yBodyRot * Mth.DEG_TO_RAD;
            forward += (dx * -Mth.sin(yaw) + dz * Mth.cos(yaw) - forward) * k;
            walkPhase = (walkPhase + dt * Mth.clamp(speed / WALK, 0.25, 2.5)) % MechaRig.PHASE_WRAP;
            backPhase = (backPhase + dt * Mth.clamp(speed / BACK, 0.25, 2.5)) % MechaRig.PHASE_WRAP;
            limpPhase = (limpPhase + dt * Mth.clamp(speed / LIMP, 0.25, 2.5)) % MechaRig.PHASE_WRAP;
            climb.advance(dt, CLIMB);
            long tick = (long) Math.floor(now);
            if (tick == lastTick) return;
            lastTick = tick;
            float rate = Float.isNaN(lastYaw) ? 0 : Mth.wrapDegrees(s.yBodyRot - lastYaw);
            turn += (rate - turn) * 0.5f;
            lastYaw = s.yBodyRot;
            boolean ground = s.onGround();
            climb.tick(s, tick, ground);
            if (ground) {
                if (!wasOnGround && airTicks > 1) landAt = tick;
                airTicks = 0;
            } else {
                if (wasOnGround && (s.getDeltaMovement().y > 0.05 || s.getY() > s.yo) && climb.face == null) jumpAt = tick;
                airTicks++;
            }
            wasOnGround = ground;
        }
    }

    private SpiderWalkerAnimation() {
    }

    public static MechaRig.Clip clip(String name) {
        return CLIPS.get(name);
    }

    private static Choice at(String clip, double tick, int blend) {
        return new Choice(CLIPS.get(clip), tick, blend);
    }

    public static boolean piloted(ShellEntity s) {
        return s.getFirstPassenger() instanceof Player;
    }

    /** Nobody aboard and nothing happening (no getting off, web, blow or collapse): drawn parked, settled, still. */
    public static boolean still(ShellEntity s, double now) {
        if (piloted(s) || s.isDeadOrDying() || s.hurtTime > 0) return false;
        if (s.webAt() >= 0 && now - s.webAt() / 2 < clip("web_dud").length()) return false;
        long boarded = s.boardedAt();
        return !(boarded < -1 && now + boarded < 30);
    }

    /** The whole shell's clip at {@code now}. */
    public static Choice main(ShellEntity s, View v, double now) {
        double frac = now - Math.floor(now);
        if (s.isDeadOrDying()) return at("destroyed", s.deathTime + frac, 0);
        if (still(s, now)) return at("parked", 0, 0);
        if (!piloted(s)) return at("parked", now, 5);
        Legs.Climb c = v.climb;
        // up a wall: upright, the legs walk up its face; over the top, it hauls itself onto the new ground
        if (c.face != null) return at("climb", c.phase, 3);
        if (c.leftAt >= 0 && s.onGround() && now - c.leftAt < clip("climb_top").length()) return at("climb_top", now - c.leftAt, 1);
        if (v.airTicks > 4) return at("fall", now, 3);
        if (v.jumpAt >= 0 && now - v.jumpAt < clip("jump").length() && !s.onGround()) return at("jump", now - v.jumpAt, 1);
        // it takes no fall damage: every landing is the light one, with its boing
        if (v.landAt >= 0 && now - v.landAt < clip("land").length()) return at("land", now - v.landAt, 1);
        if (v.speed > 0.02) {
            if (RoverAnimation.flat(s)) return at("limp", v.limpPhase, 4);
            if (v.forward < -0.02) return at("walk_back", v.backPhase, 4);
            return at("walk", v.walkPhase, 4);
        }
        if (Math.abs(v.turn) > 1.5f) return at(v.turn > 0 ? "turn_r" : "turn_l", now, 3);
        int cycle = 200 + Math.floorMod(s.getId(), 100);
        double tic = ((now + s.getId() * 37L) % cycle + cycle) % cycle;
        if (tic < clip("idle_tic").length()) return at("idle_tic", tic, 3);
        return at("idle", now, 5);
    }

    /** Hopping on, or off. */
    public static Choice cockpit(ShellEntity s, double now) {
        long boarded = s.boardedAt();
        if (piloted(s) && boarded >= 0 && now - boarded < clip("board").length()) return at("board", now - boarded, 0);
        if (piloted(s) || boarded >= -1) return null;
        double t = now + boarded;
        return t >= 0 && t < clip("get_off").length() ? at("get_off", t, 0) : null;
    }

    /** The web, or its dud, from the synced moment (over the walk: it rears on its back legs wherever it is). */
    public static Choice web(ShellEntity s, double now) {
        long web = s.webAt();
        if (web < 0 || s.isDeadOrDying()) return null;
        double t = now - web / 2;
        String name = (web & 1) == 1 ? "web_dud" : "web";
        return t >= 0 && t < clip(name).length() ? at(name, t, 1) : null;
    }

    /** The flinch, from the hurt time every client is sent. */
    public static Choice hit(ShellEntity s, double now) {
        if (s.isDeadOrDying() || s.hurtTime <= 0) return null;
        return at("hurt", 10 - s.hurtTime + (now - Math.floor(now)), 0);
    }

    public static void register(ShellEntity s, AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(controller(s, "main", SpiderWalkerAnimation::mainState));
        controllers.add(controller(s, "cockpit", st -> pick(st, cockpit(st.getAnimatable(), now(st)))));
        controllers.add(controller(s, "web", st -> pick(st, web(st.getAnimatable(), now(st)))));
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
        if (s.getFirstPassenger() instanceof Player p) s.walker.lastPilot = p.getId();
        s.walker.update(s, now);
        return pick(state, main(s, s.walker, now));
    }

    @SuppressWarnings("unchecked")
    private static PlayState pick(AnimationState<ShellEntity> state, Choice choice) {
        SeekingController<ShellEntity> c = (SeekingController<ShellEntity>) state.getController();
        if (choice == null) return c.none();
        return c.show(choice.clip().name(), choice.clip().raw(), choice.clip().at(choice.tick()), choice.blend());
    }
}
