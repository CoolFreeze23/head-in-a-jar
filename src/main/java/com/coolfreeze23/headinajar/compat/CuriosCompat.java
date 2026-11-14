package com.coolfreeze23.headinajar.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/** Curios: the Head Satchel counts when worn on the belt; an old head slot's goggles come back. Only loaded when Curios is present. */
final class CuriosCompat {
    private CuriosCompat() {
    }

    /** Set when Curios turned out to be a version whose API this was not built against: then nothing is worn there. */
    private static boolean broken;

    static boolean wears(Player player, Item item) {
        if (broken) return false;
        try {
            return CuriosApi.getCuriosInventory(player).map(inv -> inv.findFirstCurio(item).isPresent()).orElse(false);
        } catch (LinkageError e) {
            broken = true;
            com.coolfreeze23.headinajar.HeadInAJar.LOGGER.warn("Curios API changed; the Head Satchel on the belt is ignored: {}", e.toString());
            return false;
        }
    }

    /**
     * Curios' own hand-back of what sat in a slot it no longer has (an older version's goggles head slot), run now
     * rather than on the player's next tick, so {@link OldHeadSlot} can see where it goes.
     */
    static void giveBackInvalid(Player player) {
        if (broken) return;
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(inv -> inv.handleInvalidStacks());
        } catch (LinkageError e) {
            broken = true;
            com.coolfreeze23.headinajar.HeadInAJar.LOGGER.warn("Curios API changed; items in an old Curios slot are left to Curios: {}", e.toString());
        }
    }
}
