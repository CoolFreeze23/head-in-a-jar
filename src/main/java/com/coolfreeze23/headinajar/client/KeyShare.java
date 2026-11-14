package com.coolfreeze23.headinajar.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Whether one of the mod's keys sits on the same key as another mapping (another mod's, or vanilla's). Pressed where
 * the mod has nothing for it to do, such a press was meant for the other mapping and is left unanswered.
 */
public final class KeyShare {
    private KeyShare() {
    }

    public static boolean withAnother(KeyMapping mine) {
        for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
            if (other != mine && !other.isUnbound() && other.same(mine)) return true;
        }
        return false;
    }
}
