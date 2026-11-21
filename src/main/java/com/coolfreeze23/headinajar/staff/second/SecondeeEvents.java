package com.coolfreeze23.headinajar.staff.second;

import com.coolfreeze23.headinajar.host.HostData;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The secondment's hooks into a host's life: its tick ({@code HostEvents.tick}: the canister and team sync), the
 * moment before rot kills it (the Redundancy Package fires first), and the game-bus handlers of each part.
 */
public final class SecondeeEvents {
    private SecondeeEvents() {
    }

    /** One line in {@code HeadInAJar}: every handler of the secondment on the game bus. */
    public static void register() {
        NeoForge.EVENT_BUS.register(Secondment.class);
        // (no handlers of its own here: the bus refuses a class with no @SubscribeEvent methods)
        RedundancyPackage.register();
        TeamSync.register();
        Broth.register();
        SecondeeSync.register();
    }

    /** Each server tick of a host (before its traits are applied): nothing for a host with no collar. */
    public static void tick(Mob host, HostData data, long time) {
        SecondeeData d = Secondees.data(host);
        if (d == null) return;
        Broth.tick(host, d, time);
        TeamSync.tick(host, d, data, time);
    }

    /** Rot is about to kill this host (natural wastage): a seated head leaves first. */
    public static void beforeWastage(Mob host) {
        if (Secondees.seated(host)) RedundancyPackage.wastage(host);
    }
}
