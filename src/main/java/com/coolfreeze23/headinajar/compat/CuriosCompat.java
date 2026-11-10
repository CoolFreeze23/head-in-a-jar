package com.coolfreeze23.headinajar.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/** Curios: the Head Satchel counts when worn on the belt. Only loaded when Curios is present. */
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
}
