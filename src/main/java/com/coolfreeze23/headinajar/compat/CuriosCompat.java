package com.coolfreeze23.headinajar.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/** Curios: the Head Satchel counts when worn on the belt. Only loaded when Curios is present. */
final class CuriosCompat {
    private CuriosCompat() {
    }

    static boolean wears(Player player, Item item) {
        return CuriosApi.getCuriosInventory(player).map(inv -> inv.findFirstCurio(item).isPresent()).orElse(false);
    }
}
