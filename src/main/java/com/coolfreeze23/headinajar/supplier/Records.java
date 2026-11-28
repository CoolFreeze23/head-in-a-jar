package com.coolfreeze23.headinajar.supplier;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Stored creature records: a whole creature kept as data (a jarred head, a Body Locker, the Gantry's build, the Staff
 * Shuttle). Each record is stamped with the data version of the game that wrote it, as an int beside it ({@code
 * <key>DataVersion} next to {@code <key>}), so a later game knows how old the record is and can bring it up to date.
 * The record itself is never changed by the stamp. A record with no stamp was written before stamps (a 1.21.1 game).
 */
public final class Records {
    public static final String SUFFIX = "DataVersion";
    /** The data version records written by 1.21.1 carry, and what a record with no stamp was written at. */
    public static final int UNSTAMPED = 3955;

    private Records() {
    }

    /** This game's data version. */
    public static int now() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    /** Stamps the record at {@code key} in {@code parent} as written now. */
    public static void stamp(CompoundTag parent, String key) {
        parent.putInt(key + SUFFIX, now());
    }

    /** The stamp on the record at {@code key}, or 0 when it has none. */
    public static int stampOf(CompoundTag parent, String key) {
        return parent != null && parent.contains(key + SUFFIX, Tag.TAG_INT) ? parent.getInt(key + SUFFIX) : 0;
    }

    /** The data version the record at {@code key} was written at ({@link #UNSTAMPED} when it has no stamp). */
    public static int savedAt(CompoundTag parent, String key) {
        int v = stampOf(parent, key);
        return v > 0 ? v : UNSTAMPED;
    }

    /** Writes a stamp kept from earlier (0: none, nothing written), so a record copied keeps the age it had. */
    public static void put(CompoundTag parent, String key, int stamp) {
        if (stamp > 0) parent.putInt(key + SUFFIX, stamp);
    }

    // --- discontinued lines -------------------------------------------------------------------------------------------

    /** Whether a creature id names a creature this game has never heard of (an id that is there but not registered). */
    public static boolean discontinued(String id) {
        if (id == null || id.isEmpty()) return false;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(rl);
    }

    /** A creature's name from its id alone, for one this game does not have: its own lang line, else its id's words. */
    public static Component speciesName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id == null ? "" : id);
        if (rl == null) return Component.literal(String.valueOf(id));
        String[] words = rl.getPath().split("_");
        StringBuilder b = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return Component.translatableWithFallback("entity." + rl.getNamespace() + "." + rl.getPath().replace('/', '.'), b.toString());
    }
}
