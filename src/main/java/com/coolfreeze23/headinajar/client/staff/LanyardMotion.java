package com.coolfreeze23.headinajar.client.staff;

import net.minecraft.util.Mth;

/**
 * How the worn lanyard, its badge and a biped's arms move, as the art's reference maths has them
 * ({@code art/entity/lanyard/lanyard_motion.py}, ported as it is). Every time is in ticks of client render time (tick
 * plus partial tick), so nothing steps at 20 a second: the sway is a damped spring per angle, integrated in steps of at
 * most half a tick; the hire drop, the off-pop and the arms' unpin are curves of the time since their stamp.
 */
public final class LanyardMotion {
    // the sway
    static final float K = 0.35f, C = 0.55f, SPEED_LEAN = 60, LEAN_MAX = 14, STEP_BOB = 5, TURN_SWING = 1.2f, ROLL_MAX = 12, MAX_STEP = 0.5f;
    // the hire drop
    static final float DROP_FROM = 10, DROP_TICKS = 8, KICK = 6;
    // the off-pop
    static final float UNPIN_X = -1.2f, UNPIN_Y = 0.3f, UNPIN_IN = 6, UNPIN_HOLD = 8, UNPIN_OUT = 14;
    static final float UNCLIP = 7, POP_UP = 4, POP_G = 0.5f, POP_FWD = 1.2f, POP_SPIN = -30, POP_TICKS = 8;
    // carrying
    static final float CARRY_X = -1.0f, CARRY_Z = 0.08f, CARRY_EASE_K = 12;
    // reading a Job Card (the paper items' proposal, style guide 2.3-2.4): biped card arm and head; animal head dip
    static final float READ_ARM_X = -1.35f, READ_ARM_Y = 0.3f, READ_HEAD_X = 0.3f, READ_IN = 4, READ_HOLD = 16, READ_OUT = 20;
    static final float SNIFF_DIP = 0.45f, SNIFF_BOB = 0.08f, SNIFF_PERIOD = 3;

    private LanyardMotion() {
    }

    static float easeInOutSine(float u) {
        return 0.5f - 0.5f * Mth.cos((float) Math.PI * Mth.clamp(u, 0, 1));
    }

    static float easeOutCubic(float u) {
        u = 1 - Mth.clamp(u, 0, 1);
        return 1 - u * u * u;
    }

    /** px the lanyard is lifted (model up) {@code t} ticks after the hire stamp; 0 from {@link #DROP_TICKS} on. */
    static float hireOffset(float t) {
        if (t >= DROP_TICKS || t < 0) return 0;
        float u = Mth.clamp(t, 0, DROP_TICKS) / DROP_TICKS;
        return DROP_FROM * (1 - u * u);
    }

    /** 0..1: how far a biped's arms are toward the unpin pose, {@code t} ticks after the let-go stamp. */
    static float unpinWeight(float t) {
        if (t < 0 || t >= UNPIN_OUT) return 0;
        if (t <= UNPIN_IN) return easeInOutSine(t / UNPIN_IN);
        if (t <= UNPIN_HOLD) return 1;
        return 1 - easeInOutSine((t - UNPIN_HOLD) / (UNPIN_OUT - UNPIN_HOLD));
    }

    /** The off-pop {@code t} ticks after the let-go stamp: null when it is not drawn any more, else {up px, forward px, spin degrees}. */
    static float[] pop(float t) {
        if (t < UNCLIP) return new float[]{0, 0, 0};
        float u = t - UNCLIP;
        if (u >= POP_TICKS) return null;
        return new float[]{POP_UP * u - 0.5f * POP_G * u * u, POP_FWD * u, POP_SPIN * u};
    }

    /** 0..1: the reading pose's weight {@code t} ticks after the card was taken. */
    static float readWeight(float t) {
        if (t < 0 || t >= READ_OUT) return 0;
        if (t <= READ_IN) return easeOutCubic(t / READ_IN);
        if (t <= READ_HOLD) return 1;
        return 1 - easeInOutSine((t - READ_HOLD) / (READ_OUT - READ_HOLD));
    }

    /** An animal's head pitch while sniffing a card (radians, nose down), {@code t} ticks after it was taken. */
    static float sniff(float t) {
        float w = readWeight(t);
        if (w <= 0) return 0;
        float bob = t > READ_IN && t < READ_HOLD ? SNIFF_BOB * Mth.sin((float) (2 * Math.PI) * (t - READ_IN) / SNIFF_PERIOD) : 0;
        return SNIFF_DIP * w + bob;
    }

    /**
     * Steps one worker's pendulum ({@code s}: pitch, roll, their speeds, degrees) by {@code dt} ticks toward the targets
     * its walk sets: it trails behind the walk and nods twice a stride, and swings out on turns.
     */
    static void step(float[] s, float dt, float forwardSpeed, float walkAmount, float walkPos, float yawRate) {
        float tp = Mth.clamp(-SPEED_LEAN * forwardSpeed, -LEAN_MAX, LEAN_MAX) + STEP_BOB * walkAmount * Mth.sin(2 * 0.6662f * walkPos);
        float tr = Mth.clamp(TURN_SWING * yawRate, -ROLL_MAX, ROLL_MAX);
        float left = dt;
        while (left > 1e-6f) {
            float h = Math.min(MAX_STEP, left);
            float ap = K * (tp - s[0]) - C * s[2];
            float ar = K * (tr - s[1]) - C * s[3];
            s[2] += ap * h;
            s[3] += ar * h;
            s[0] += s[2] * h;
            s[1] += s[3] * h;
            left -= h;
        }
    }
}
