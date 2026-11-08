package com.coolfreeze23.headinajar.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/** Entry points for optional integrations. Each checks the mod is loaded before touching a class that needs it. */
public final class Compat {
    private Compat() {
    }

    public static boolean curios() {
        return ModList.get() != null && ModList.get().isLoaded("curios");
    }

    /** Whether the player wears this item in a Curios slot (false without Curios). */
    public static boolean wearsCurio(Player player, Item item) {
        return curios() && CuriosCompat.wears(player, item);
    }

    /** Client setup: hooks into Entity Model Features' player animations when it is installed. */
    public static void clientSetup() {
        if (ModList.get() != null && ModList.get().isLoaded("entity_model_features")) EmfCompat.register();
    }
}
