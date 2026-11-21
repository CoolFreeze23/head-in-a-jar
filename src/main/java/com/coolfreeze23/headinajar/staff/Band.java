package com.coolfreeze23.headinajar.staff;

/**
 * A member of staff's morale, as a word: Engaged 75 to 100, Content 40 to 74, Disengaged 15 to 39, and Grievance for
 * under 15 at two dawns running (0.7.2: Form 66-G is filed, and the band holds until morale is back to 40). A trophy has none. Sent to clients as its ordinal in a
 * byte ({@code "Band"}), so the order is never changed.
 */
public enum Band {
    NONE, ENGAGED, CONTENT, DISENGAGED, GRIEVANCE, TROPHY;

    public static final int ENGAGED_AT = 75, CONTENT_AT = 40, DISENGAGED_AT = 15;

    public String key() {
        return "staff.headinajar.band." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The band of a morale number; {@code lowDawns} counts dawns running under 15. */
    public static Band of(int morale, int lowDawns) {
        if (morale < 0) return TROPHY;
        if (morale >= ENGAGED_AT) return ENGAGED;
        if (morale >= CONTENT_AT) return CONTENT;
        if (morale < DISENGAGED_AT && lowDawns >= 2) return GRIEVANCE;
        return DISENGAGED;
    }

    public static Band of(StaffFile file) {
        if (file == null) return NONE;
        if (!file.staff()) return TROPHY;
        if (!com.coolfreeze23.headinajar.Config.get(com.coolfreeze23.headinajar.Config.MORALE, true)) return CONTENT;
        // 0.7.2: Form 66-G is open until morale is back to 40, whatever the number says meanwhile
        if (file.career != null && file.career.grievance) return GRIEVANCE;
        return of(file.morale, file.lowDawns);
    }

    public static Band byId(int id) {
        Band[] all = values();
        return id >= 0 && id < all.length ? all[id] : NONE;
    }

    /** Heads that sag in their jar. */
    public boolean low() {
        return this == DISENGAGED || this == GRIEVANCE;
    }
}
