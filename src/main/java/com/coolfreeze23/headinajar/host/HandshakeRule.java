package com.coolfreeze23.headinajar.host;

/**
 * When the Neural Handshake on boarding a host is over for being off the host. The server sends the handshake as the
 * jar climbs on; the client may learn that it is riding a tick later (the passengers update goes out at the end of the
 * server's tick), so "not riding it yet" is not "got off": the handshake waits up to {@link #GRACE_TICKS} for the
 * client to see itself on the host, and only once it has, getting off ends it.
 */
public final class HandshakeRule {
    /** How long the client waits to see itself riding the host before the handshake is dropped. */
    public static final int GRACE_TICKS = 40;

    private HandshakeRule() {
    }

    /**
     * @param ridingIt   the client sees itself riding the handshake's host now
     * @param seenRiding it has seen itself riding that host at least once since the handshake began
     * @param waited     ticks waited so far without ever riding it
     */
    public static boolean over(boolean ridingIt, boolean seenRiding, int waited) {
        if (ridingIt) return false;
        return seenRiding || waited >= GRACE_TICKS;
    }
}
