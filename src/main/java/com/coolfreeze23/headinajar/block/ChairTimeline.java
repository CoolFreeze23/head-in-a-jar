package com.coolfreeze23.headinajar.block;

/**
 * The Separation Chair's operation, tick by tick: every tick the server acts on, the drawing changes at and the
 * patient's overlay reads. The animation file is keyed to the same ticks; when the model's clips are retimed, this is
 * the one place the code follows them.
 */
public final class ChairTimeline {
    /** Length of the operation clip; the server finishes here. */
    public static final int LENGTH = 240;
    /** The point of no return: the parts are spent and the botch is decided. */
    public static final int CUT_COMMIT = 100;

    // server sounds (each at the tick its hit must land on, less the file's pre-roll)
    public static final int MOTOR = 0;
    public static final int BARRIER_DROP = 3;
    /** The barrier tip lands on the latch; the latch file has 2 ticks of ratchet before its hit. */
    public static final int BARRIER_CONTACT = 13, BARRIER_SETTLE = 19, LATCH_PREROLL = 2;
    public static final int GAS = 30, GAS_END = 56, GAS_EVERY = 4;
    public static final int SCAN = 60;
    /** The blade passes the neck. */
    public static final int CUT = 120;
    public static final int GLUG_LID = 140;
    public static final int[] GLUG_POUR = {199, 205};
    public static final int ANTENNA = 214;
    public static final int[] RATCHET = {228, 230, 232};
    public static final int BARRIER_LIFT = 236;

    // what the renderer draws, and when
    /** From here the patient's own head is hidden and the chair draws it on the claw. */
    public static final int HEAD_OFF = CUT;
    /** Goggles appear under the closing jaw. */
    public static final int GOGGLES_ON = 148;
    /** The head drops the last few pixels into the jar and grows from the seated 0.9375 to the jar's 1.0. */
    public static final int DROP_START = 195, IN_JAR = 202;
    /** The saline rises in the jar between these ticks. */
    public static final int FILL_START = 199, FILL_END = 207;
    /** The Collar Cam is bolted on behind the placard. */
    public static final int COLLAR_ON = 228;
    /** The curtain stands in front of the neck (the overlay says PRIVACY, PLEASE meanwhile). */
    public static final int CURTAIN_UP = 106, CURTAIN_DOWN = 134;
    /** The tray slides out or in (its own clip, its own controller). */
    public static final int TRAY_LENGTH = 14;
    /** The scan ring passes over the eyes between these ticks (the overlay's line follows it). */
    public static final int SCAN_RING_START = 64, SCAN_RING_END = 95;
    /** The boot counter. */
    public static final int BOOT_START = 210;

    /** Clip lengths of the clips the chair seeks into. */
    public static final int IDLE_LENGTH = 80, READY_LENGTH = 40, ABORT_LENGTH = 16, AFTERCARE_LENGTH = 30;
    /** How long the abort blends from wherever the machine was before its clip runs. */
    public static final int ABORT_BLEND = 10;
    /** After the operation the chair keeps drawing its jar until the real jar is there, at most this long. */
    public static final int HANDOFF_MAX = 10;
    /** A patient who is gone this long after the cut ends the run. */
    public static final int PATIENT_GRACE = 200;

    private ChairTimeline() {
    }
}
