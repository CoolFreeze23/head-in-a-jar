package com.coolfreeze23.headinajar.staff;

/**
 * A block that wears the staff badge for the head (or heads) it holds: a plinth head on the payroll, the Turret
 * Pedestal, Employee Mount, Neural Socket and Cerebral Array. The colour is worked out on the server (the employer's
 * team colour, or company teal) and sent in the block's update tag as {@code "Badge"}; -1 is no badge.
 */
public interface Badged {
    /** Company teal, for an employer with no team or a team with no colour. */
    int TEAL = 0x5AA6A4;

    /** The badge's colour as this side knows it (0xRRGGBB), or -1 for none. */
    int badge();

    /** Server: the badge's colour, or -1; the block tells its watchers when it changes. */
    void setBadge(int colour);
}
