package com.coolfreeze23.headinajar.staff;

/**
 * The 0.7.2 moments' hooks on the game bus: the Promotion to the Board, grades and reviews, grievances and strikes. Each
 * part registers its own handlers here (the bus refuses a class with no {@code @SubscribeEvent} methods).
 */
public final class MomentsEvents {
    private MomentsEvents() {
    }

    /** One line in {@code HeadInAJar}. */
    public static void register() {
        Promotion.register();
        RosterPromotion.register();
        // a striker stands in the ring before anything else holds it (its hold is the first)
        Strikes.register();
        Grievances.register();
    }
}
