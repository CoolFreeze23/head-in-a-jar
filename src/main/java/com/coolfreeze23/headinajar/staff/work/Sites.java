package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Time Clocks as the rest of the workforce finds them: a clock is read only when its chunk is loaded (nothing here ever
 * loads one).
 */
public final class Sites {
    private Sites() {
    }

    /** The clock at a site, if its chunk is loaded; else null. */
    public static TimeClockBlockEntity clock(ServerLevel any, GlobalPos site) {
        if (site == null) return null;
        ServerLevel level = any.getServer().getLevel(site.dimension());
        if (level == null || !level.isLoaded(site.pos())) return null;
        return level.getBlockEntity(site.pos()) instanceof TimeClockBlockEntity c ? c : null;
    }

    /** A card back into a site's rack; false when that cannot be done now. */
    public static boolean returnCard(ServerLevel any, GlobalPos site, ItemStack card) {
        TimeClockBlockEntity c = clock(any, site);
        return c != null && c.returnCard(card);
    }

    /** A worker off a site's roster (a clock not loaded drops it from its roster the next time it looks). */
    public static void leave(ServerLevel any, GlobalPos site, UUID worker) {
        TimeClockBlockEntity c = clock(any, site);
        if (c != null) c.leave(worker);
    }
}
