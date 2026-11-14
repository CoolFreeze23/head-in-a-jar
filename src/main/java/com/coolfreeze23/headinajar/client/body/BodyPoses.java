package com.coolfreeze23.headinajar.client.body;

import com.coolfreeze23.headinajar.client.fx.Comfort;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * What a headless body does while its owner is away, and how it lies down when it dies. All of it is code on the
 * vanilla player model, on a client timer (the body's own age since this client saw it), with nothing synced but what
 * the body already has: its owner (whose jar, if near, it turns its stump toward, reaches out to or waves at), whether
 * it is a corpse, and whether its things are open.
 * <p>
 * By time away: at attention with its hands clasped behind its back, breathing, shifting its weight now and then;
 * after 30 s a fidget every 12 s (pats its pockets, adjusts its collar or pats the stump, checks a watch it does
 * not have, taps a foot); after 3 minutes it sits down cross-legged. A corpse falls over 24 ticks (knees, a hard sit,
 * onto its back) and folds its hands over HR's card; they unfold while its things are open.
 */
public final class BodyPoses {
    /** When the body sits down: 3 minutes. */
    public static final int SIT_AFTER = 3600;
    /** When the fidgets start: 30 seconds. */
    public static final int FIDGET_AFTER = 600;
    public static final int FALL = 24;

    // rightArm x y z, leftArm x y z, rightLeg x y z, leftLeg x y z, sit, roll, yaw, lean
    private static final int N = 16;
    private static final int SIT = 12, ROLL = 13, YAW = 14, LEAN = 15;

    private static final class State {
        final float[] now = new float[N];
        float lastAge = -1;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<Integer, Long> FALLS = new HashMap<>();

    /** A body just died where this client can see it: it falls instead of lying down at once. */
    static void corpse(int id) {
        var level = Minecraft.getInstance().level;
        if (level != null) FALLS.put(id, level.getGameTime());
    }

    /** 0 to 1 through the fall; 1 for a corpse that was already lying when it came into view. */
    public static float fall(HeadlessBody body, float pt) {
        Long at = FALLS.get(body.getId());
        var level = Minecraft.getInstance().level;
        if (at == null || level == null) return 1;
        float u = (level.getGameTime() - at + pt) / FALL;
        if (u >= 1.5f) FALLS.remove(body.getId());
        return Mth.clamp(u, 0, 1);
    }

    /** Which idle pose a body is in at this age (for the harness and tests): attention, fidget name, or sitting. */
    public static String phase(HeadlessBody body) {
        if (body.isCorpse()) return body.isOpened() ? "corpse-open" : "corpse";
        int age = body.tickCount;
        if (age >= SIT_AFTER) return "sitting";
        if (age >= FIDGET_AFTER) {
            int f = fidget(body, age);
            return f < 0 ? "attention" : FIDGETS[f];
        }
        return "attention";
    }

    private static final String[] FIDGETS = {"pockets", "collar", "watch", "tap"};

    /** Ticks between fidgets, and how long one lasts. */
    static final int FIDGET_EVERY = 240, FIDGET_LENGTH = 70;

    /** The fidget playing at this age, or -1: one every 240 ticks, each 70 long, chosen by the body and the slot. */
    private static int fidget(HeadlessBody body, int age) {
        int slot = (age - FIDGET_AFTER) / FIDGET_EVERY, in = (age - FIDGET_AFTER) % FIDGET_EVERY;
        if (in >= FIDGET_LENGTH) return -1;
        int h = (body.getId() * 31 + slot * 7919) ^ (slot >>> 2);
        return Math.floorMod(h, FIDGETS.length);
    }

    private static float smooth(float u) {
        u = Mth.clamp(u, 0, 1);
        return u * u * (3 - 2 * u);
    }

    /** The owner's player, if this client has them nearby and in a jar (their head is where they are). */
    private static Player ownerHead(HeadlessBody body) {
        var level = Minecraft.getInstance().level;
        if (level == null || body.ownerId() == null) return null;
        Player p = level.getPlayerByUUID(body.ownerId());
        if (p == null || p.distanceToSqr(body) > 16 * 16) return null;
        JarState s = p.getData(ModRegistry.JAR_STATE);
        return s.form().isJarred() ? p : null;
    }

    /** Sets the model's limbs for this frame (called after the vanilla pose, before sleeves are copied). */
    public static void pose(PlayerModel<HeadlessBody> m, HeadlessBody body, float age) {
        State st = STATES.computeIfAbsent(body.getId(), k -> new State());
        float[] want = new float[N];
        float k = Math.max(0.35f, Comfort.motion());
        if (body.isCorpse()) {
            corpsePose(want, body, age);
        } else {
            idlePose(want, body, age, k);
        }
        // chase the wanted angles, frame-rate independent (snaps on the first frame and through the fall)
        float dt = st.lastAge < 0 ? 99 : Mth.clamp(age - st.lastAge, 0, 10);
        st.lastAge = age;
        float c = body.isCorpse() && fall(body, 0) < 1 ? 1 : 1 - (float) Math.exp(-dt * 0.35f);
        for (int i = 0; i < N; i++) st.now[i] += (want[i] - st.now[i]) * (dt >= 99 ? 1 : c);
        float[] a = st.now;
        m.rightArm.xRot += a[0];
        m.rightArm.yRot += a[1];
        m.rightArm.zRot += a[2];
        m.leftArm.xRot += a[3];
        m.leftArm.yRot += a[4];
        m.leftArm.zRot += a[5];
        m.rightLeg.xRot += a[6];
        m.rightLeg.yRot += a[7];
        m.rightLeg.zRot += a[8];
        m.leftLeg.xRot += a[9];
        m.leftLeg.yRot += a[10];
        m.leftLeg.zRot += a[11];
        if (STATES.size() > 256) STATES.clear();
    }

    private static void idlePose(float[] w, HeadlessBody body, float age, float k) {
        int tick = body.tickCount;
        // at attention: hands clasped behind its back, breathing
        float breathe = Mth.sin(age * Mth.TWO_PI / 60f) * 0.025f;
        w[0] = 0.5f + breathe;
        w[2] = -0.16f;
        w[3] = 0.5f + breathe;
        w[5] = 0.16f;
        // the first moment empty: it powers down, hands to its sides, then clasps them
        if (tick < 14) {
            float u = smooth(tick / 14f);
            w[0] *= u;
            w[3] *= u;
            w[2] *= u;
            w[5] *= u;
        }
        // weight shifts every 100-160 ticks: the hips roll 2 degrees and the off leg relaxes
        int period = 100 + Math.floorMod(body.getId() * 37, 60);
        float side = ((tick / period) & 1) == 0 ? 1 : -1;
        w[ROLL] = 2 * side;
        if (side > 0) {
            w[9] = -0.08f;
            w[11] = -0.04f;
        } else {
            w[6] = -0.08f;
            w[8] = 0.04f;
        }
        Player owner = ownerHead(body);
        if (tick >= SIT_AFTER) {
            // sitting cross-legged on the ground, hands in its lap
            w[6] = -1.45f;
            w[7] = 0.3f;
            w[9] = -1.45f;
            w[10] = -0.3f;
            w[8] = w[11] = 0;
            w[0] = -0.65f;
            w[2] = 0.12f;
            w[3] = -0.65f;
            w[5] = -0.12f;
            w[SIT] = 1;
            w[ROLL] = 0;
        } else if (tick >= FIDGET_AFTER) {
            int f = fidget(body, tick);
            float t = (tick - FIDGET_AFTER) % FIDGET_EVERY + (age - tick);
            float in = smooth(t / 8) * (1 - smooth((t - (FIDGET_LENGTH - 8)) / 8));
            switch (f) {
                case 0 -> {
                    // pats its pockets: hands to its hips, two pats each side
                    float pat = t > 14 && t < 38 ? Math.abs(Mth.sin((t - 14) / 24 * Mth.TWO_PI * 2)) * 0.18f : 0;
                    blend(w, 0, in, -0.2f - pat, 0, 0.32f);
                    blend(w, 3, in, -0.2f - pat, 0, -0.32f);
                }
                case 1 -> {
                    // reaches up to where its head was: adjusts the Collar Cam (two twists) or pats the stump
                    float twist = t > 18 && t < 42 ? Mth.sin((t - 18) / 24 * Mth.TWO_PI * 2) * (body.hasCollar() ? 0.3f : 0.12f) : 0;
                    float pat = body.hasCollar() ? 0 : twist;
                    blend(w, 0, in, -2.55f + pat, -0.55f + (body.hasCollar() ? twist : 0), 0.1f);
                }
                case 2 -> {
                    // checks the watch it doesn't have: wrist up across the chest, the other hand pulls the sleeve
                    blend(w, 3, in, -1.35f, 0.85f, 0);
                    blend(w, 0, in * smooth((t - 6) / 6), -1.0f, -0.55f, 0);
                    w[LEAN] = 6 * in;
                }
                case 3 -> {
                    // taps a foot, four taps five ticks apart
                    float tap = t > 10 && t < 30 ? Math.abs(Mth.sin((t - 10) / 5 * Mth.PI)) * 0.22f : 0;
                    w[6] = -tap * in;
                }
                default -> {
                }
            }
        }
        if (owner != null) {
            Vec3 to = owner.position().subtract(body.position());
            double d = to.length();
            Vec3 look = owner.getLookAngle();
            boolean looking = d < 8 && look.dot(to.scale(-1 / Math.max(0.01, d))) > 0.93;
            if (d < 3) {
                // its head is here: it holds its arms out to take it
                blend(w, 0, 1, -1.35f, 0.1f, 0);
                blend(w, 3, 1, -1.35f, -0.1f, 0);
                w[LEAN] = 0;
            } else if (looking) {
                // its head is looking at it: it waves
                blend(w, 0, 1, -2.85f, 0, 0.35f + Mth.sin(age * 0.6f) * 0.35f * k);
            }
        }
        if (owner != null) {
            // it turns its stump toward its head
            Vec3 to = owner.position().subtract(body.position());
            float want = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
            w[YAW] = Mth.clamp(Mth.wrapDegrees(want - body.yBodyRot), -70, 70);
        }
    }

    private static void blend(float[] w, int at, float u, float x, float y, float z) {
        w[at] += (x - w[at]) * u;
        w[at + 1] += (y - w[at + 1]) * u;
        w[at + 2] += (z - w[at + 2]) * u;
    }

    private static void corpsePose(float[] w, HeadlessBody body, float age) {
        float f = fall(body, age - (int) age) * FALL;
        // knees go (legs forward as it sits back), then straighten as it tips onto its back
        float sit = smooth(f / 10) * (1 - smooth((f - 12) / 10));
        w[6] = w[9] = -1.4f * sit;
        // the arms flop out as it falls, then fold over its chest (over 20 ticks after it lands); open, they part
        float flop = f < FALL ? smooth(f / 12) : 0;
        float fold = f < FALL ? 0 : 1;
        w[2] = 0.6f * flop;
        w[5] = -0.6f * flop;
        if (fold > 0) {
            if (body.isOpened()) {
                w[2] = 0.95f;
                w[5] = -0.95f;
            } else {
                w[0] = -0.85f;
                w[1] = -0.45f;
                w[2] = -0.15f;
                w[3] = -0.85f;
                w[4] = 0.45f;
                w[5] = 0.15f;
            }
        }
    }

    /**
     * The whole-body part of the pose, in the renderer's own rotations (feet at the origin, facing its body yaw): the
     * hips' weight shift, the turn toward its head, sitting down, a lean to look at a wrist, and the corpse's fall.
     */
    public static void root(HeadlessBody body, PoseStack pose, float pt) {
        State st = STATES.get(body.getId());
        if (body.isCorpse()) {
            float f = fall(body, pt) * FALL;
            // it sits back as its knees go (the hips come down as far as the legs reach forward), then tips over
            float drop = smooth(f / 10) * (1 - smooth((f - 12) / 10));
            float tip = f < 12 ? 0 : Mth.clamp((f - 12) / 10, 0, 1);
            tip = tip * tip;
            // a small bump as its back lands
            float bump = f > 22 && f < FALL ? Mth.sin((f - 22) / 2 * Mth.PI) * 0.04f : 0;
            // lying, its back rests on the ground (half the torso's depth up), centred on where it stood
            pose.translate(0, -0.62 * drop + bump + 0.125 * tip, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-90 * tip));
            pose.translate(0, -0.9 * tip, 0);
            return;
        }
        if (st == null) return;
        float[] a = st.now;
        if (Math.abs(a[YAW]) > 0.05f) pose.mulPose(Axis.YP.rotationDegrees(-a[YAW]));
        pose.translate(0, -0.62 * a[SIT], 0);
        if (Math.abs(a[ROLL]) > 0.01f || Math.abs(a[LEAN]) > 0.01f) {
            pose.translate(0, 0.75, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(a[ROLL]));
            pose.mulPose(Axis.XP.rotationDegrees(-a[LEAN]));
            pose.translate(0, -0.75, 0);
        }
    }

    private BodyPoses() {
    }
}
