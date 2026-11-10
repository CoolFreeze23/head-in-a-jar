package com.coolfreeze23.headinajar.client;

/**
 * Hold-to-sign for Form 27-B, driven by the screen's own mouse press and release (any button, so it works whatever
 * the player has bound). Pressed on the Sign button and held there for {@link #HOLD_TICKS}, it signs once. Letting
 * go, sliding off the button, the form becoming unsignable or the screen closing all cancel the hold.
 * Plain state, no game classes, so it can be checked without a client.
 */
public final class SignHold {
    public static final int HOLD_TICKS = 40;
    private int button = -1;
    private int held;
    private boolean sent;

    /** A mouse button went down on the Sign button. */
    public void press(int button) {
        this.button = button;
        held = 0;
        sent = false;
    }

    /** A mouse button came up; true if it was the one holding the pen. */
    public boolean release(int button) {
        if (button != this.button) return false;
        reset();
        return true;
    }

    public void reset() {
        button = -1;
        held = 0;
        sent = false;
    }

    /** One client tick. Returns true on the one tick the form gets signed. */
    public boolean tick(boolean overButton, boolean canSign) {
        if (button < 0) return false;
        if (!overButton || !canSign) {
            reset();
            return false;
        }
        if (held < HOLD_TICKS) held++;
        if (held >= HOLD_TICKS && !sent) {
            sent = true;
            return true;
        }
        return false;
    }

    public boolean holding() {
        return button >= 0;
    }

    /** Ticks held so far, 0 to {@link #HOLD_TICKS}. */
    public int held() {
        return held;
    }
}
