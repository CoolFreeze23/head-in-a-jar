package com.coolfreeze23.headinajar.staff.work;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The operators' mandatory break ({@code /headoffice staff pause}), kept in the world's data (the Overworld's storage,
 * {@code headinajar_staff_pause}) so a lag hunt's pause survives a restart until an operator lifts it; read into
 * {@link Workforce#paused} as the server starts. {@code /headoffice status} says when it is on.
 */
public final class WorkPause extends SavedData {
    private static final String NAME = "headinajar_staff_pause";
    private boolean paused;

    public static WorkPause get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(WorkPause::new, WorkPause::load, null), NAME);
    }

    public boolean paused() {
        return paused;
    }

    /** Sets the pause, saved, and in force at once. */
    public static void set(MinecraftServer server, boolean on) {
        WorkPause p = get(server);
        if (p.paused != on) {
            p.paused = on;
            p.setDirty();
        }
        Workforce.paused = on;
    }

    @SubscribeEvent
    public static void starting(ServerStartingEvent event) {
        Workforce.paused = get(event.getServer()).paused;
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        // the next world (a single-player game) reads its own
        Workforce.paused = false;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Paused", paused);
        return tag;
    }

    private static WorkPause load(CompoundTag tag, HolderLookup.Provider registries) {
        WorkPause p = new WorkPause();
        p.paused = tag.getBoolean("Paused");
        return p;
    }
}
