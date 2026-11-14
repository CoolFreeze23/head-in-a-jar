package com.coolfreeze23.headinajar.entity;

import com.coolfreeze23.headinajar.anim.SeekingController;
import com.coolfreeze23.headinajar.mecha.MechaRig;
import com.coolfreeze23.headinajar.mecha.Wheels;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;

import java.util.HashMap;
import java.util.Map;

/**
 * Which clip the Rover (a Utility Shell on the wheeled skeleton) plays, and at which tick, from its synced moments
 * and what this client measures of its motion; the wheels, steering and lean are code's ({@link Wheels}, the same as
 * the Wheelhouse's). Four controllers, in the model's order: {@code main}, {@code steer} (turning on the spot),
 * {@code cockpit} (boarding and getting off), {@code hit} (the crash and the flinch).
 */
public final class RoverAnimation {
    /** A clip at a tick, entered with a blend of so many ticks. */
    public record Choice(MechaRig.Clip clip, double tick, int blend) {
    }

    private static final Map<String, MechaRig.Clip> CLIPS = new HashMap<>();

    static {
        String table = "parked 80L, board 22, get_off 20, get_off_r 20, get_off_l 20, get_off_front 20, idle 40L, idle_tic 30, drive_start 8,"
                + " drive 8L, reverse 10L, brake 14, steer_l 6, steer_r 6, turn_l 10L, turn_r 10L, crash 22, sprint_start 14, hurt 10, limp 16L,"
                + " destroyed 30";
        for (String entry : table.split("\\s*,\\s*")) {
            String[] p = entry.trim().split(" ");
            boolean loop = p[1].endsWith("L");
            CLIPS.put(p[0], MechaRig.Clip.of(p[0], Integer.parseInt(loop ? p[1].substring(0, p[1].length() - 1) : p[1]), loop));
        }
    }

    /** The collapse's length: the shell is removed when it ends. */
    public static final int DESTROYED = 30;
    /** The get-off clips by the place the jar was put: behind, right, left, in front. */
    public static final String[] GET_OFF = {"get_off", "get_off_r", "get_off_l", "get_off_front"};
    /** The clip hides its stand-in jar from this tick: the real jar is drawn hopping off until then. */
    public static final int GET_OFF_HOP = 12;
    /** Below this share of the jar's charge the Rover limps. */
    public static final float LIMP_CHARGE = 0.05f;

    /**
     * The castors (3 px: 305.6 degrees a block rolled, capped at 150 a tick so the one bolt never seems to run
     * backwards), steering to 35 with the rear 12 the other way, the trolley rolling out of the turn up to 5 degrees
     * and its aerial swinging with it. Drive authored at 0.46 blocks a tick, reverse 0.18, limp 0.14.
     */
    public static final Wheels.Spec SPEC = new Wheels.Spec(new double[]{-6, 6, -6, 6}, new double[]{-7, -7, 7, 7}, 305.6, 150,
            0.46, 0.18, 0.14, 0.46, 35, 12 / 35f, 0, 1.5f, 5, -1, 1, "flag", "drive_start", 8, 14, 0);

    /** What a client measures of one Rover. */
    public static final class View {
        public final Wheels.State wheels = new Wheels.State();
        /** The player last seen at the wheel (the get-off clip draws their jar hopping off), by entity id. */
        public int lastPilot = -1;
        /** The bones' world matrices as last drawn (the effects' locators). */
        public final Map<String, org.joml.Matrix4f> bones = new HashMap<>();
        /** The Lunk: its measured pace, the walk's phase, when this client last measured, and since when its jar is flat. */
        public double lunkSpeed, lunkPhase, lunkLast = -1, lunkX, lunkZ, flatSince = -1;
        public long lunkTick;
    }

    private RoverAnimation() {
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

    /** The pilot's jar is nearly out of charge (or out): the Rover limps. */
    public static boolean flat(ShellEntity s) {
        if (!(s.getFirstPassenger() instanceof Player p) || !p.hasData(ModRegistry.JAR_STATE)) return false;
        var state = p.getData(ModRegistry.JAR_STATE);
        return state.charge() <= state.chargeCapacity() * LIMP_CHARGE;
    }

    /** The get-off clip and its tick, or null: the place the server chose, from the moment the pilot left. */
    public static Choice getOff(ShellEntity s, double now) {
        long boarded = s.boardedAt(), off = s.gotOffAt();
        if (piloted(s) || boarded >= -1) return null;
        double t = now + boarded;
        if (t < 0 || t >= 20) return null;
        int place = off >= 0 && off / 4 == -boarded ? (int) (off & 3) : ShellEntity.BEHIND;
        return at(GET_OFF[place], t, 0);
    }

    /** Nobody aboard and nothing happening (no getting off, crash, blow or collapse): drawn parked, settled, still. */
    public static boolean still(ShellEntity s, double now) {
        if (piloted(s) || s.isDeadOrDying() || s.hurtTime > 0) return false;
        if (s.crashedAt() >= 0 && now - s.crashedAt() < clip("crash").length()) return false;
        long boarded = s.boardedAt();
        return !(boarded < -1 && now + boarded < 30);
    }

    /** The whole shell's clip at {@code now}. */
    public static Choice main(ShellEntity s, View v, double now) {
        double frac = now - Math.floor(now);
        if (s.isDeadOrDying()) return at("destroyed", s.deathTime + frac, 0);
        if (still(s, now)) return at("parked", 0, 0);
        if (!piloted(s)) return at("parked", now, 5);
        long sprint = s.sprintedAt();
        if (sprint >= 0 && now - sprint < clip("sprint_start").length()) return at("sprint_start", now - sprint, 1);
        Wheels.Drive drive = Wheels.drive(SPEC, v.wheels, now, flat(s), false, CLIPS::containsKey);
        if (drive != null) return at(drive.clip(), drive.tick(), drive.blend());
        int cycle = 200 + Math.floorMod(s.getId(), 100);
        double tic = ((now + s.getId() * 37L) % cycle + cycle) % cycle;
        if (tic < clip("idle_tic").length()) return at("idle_tic", tic, 3);
        return at("idle", now, 5);
    }

    /** Turning on the spot; otherwise the code steers the castors. */
    public static Choice steer(ShellEntity s, View v, double now) {
        if (!piloted(s) || s.isDeadOrDying()) return null;
        int spot = Wheels.onTheSpot(v.wheels);
        return spot == 0 ? null : at(spot > 0 ? "turn_r" : "turn_l", now, 3);
    }

    /** Hopping on, or the get-off clip for where the jar was put. */
    public static Choice cockpit(ShellEntity s, double now) {
        long boarded = s.boardedAt();
        if (piloted(s) && boarded >= 0 && now - boarded < clip("board").length()) return at("board", now - boarded, 0);
        return getOff(s, now);
    }

    /** Into a wall (the server's crash), else the flinch. */
    public static Choice hit(ShellEntity s, double now) {
        if (s.isDeadOrDying()) return null;
        long crash = s.crashedAt();
        if (crash >= 0 && now - crash < clip("crash").length()) return at("crash", now - crash, 0);
        if (s.hurtTime > 0) return at("hurt", 10 - s.hurtTime + (now - Math.floor(now)), 0);
        return null;
    }

    public static void register(ShellEntity s, AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(controller(s, "main", RoverAnimation::mainState));
        controllers.add(controller(s, "steer", st -> pick(st, steer(st.getAnimatable(), st.getAnimatable().view, now(st)))));
        controllers.add(controller(s, "cockpit", st -> pick(st, cockpit(st.getAnimatable(), now(st)))));
        controllers.add(controller(s, "hit", st -> pick(st, hit(st.getAnimatable(), now(st)))));
    }

    private static SeekingController<ShellEntity> controller(ShellEntity s, String name,
                                                             software.bernie.geckolib.animation.AnimationController.AnimationStateHandler<ShellEntity> handler) {
        SeekingController<ShellEntity> c = new SeekingController<>(s, name, handler);
        c.setSoundKeyframeHandler(event -> com.coolfreeze23.headinajar.client.RoverClient.sound(event));
        c.setParticleKeyframeHandler(event -> com.coolfreeze23.headinajar.client.RoverClient.particle(event));
        return c;
    }

    private static double now(AnimationState<ShellEntity> state) {
        return state.getAnimatable().level().getGameTime() + state.getPartialTick();
    }

    private static PlayState mainState(AnimationState<ShellEntity> state) {
        ShellEntity s = state.getAnimatable();
        double now = now(state);
        if (s.getFirstPassenger() instanceof Player p) s.view.lastPilot = p.getId();
        s.view.wheels.update(s, SPEC, now, Wheels.onTheSpot(s.view.wheels) == 0, false);
        return pick(state, main(s, s.view, now));
    }

    @SuppressWarnings("unchecked")
    private static PlayState pick(AnimationState<ShellEntity> state, Choice choice) {
        SeekingController<ShellEntity> c = (SeekingController<ShellEntity>) state.getController();
        if (choice == null) return c.none();
        return c.show(choice.clip().name(), choice.clip().raw(), choice.clip().at(choice.tick()), choice.blend());
    }
}
