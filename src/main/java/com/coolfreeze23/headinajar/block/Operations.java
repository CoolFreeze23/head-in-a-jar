package com.coolfreeze23.headinajar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The operation is the front door: nothing else that is a moment of its own (a Promotion to the Board) may run while a
 * Separation Chair within {@value #RADIUS} blocks operates. Where chairs are running is {@link ChairQuiet}'s (each
 * running chair marks itself every tick); this adds the moment a chair starts, for whatever has to give way to it.
 */
public final class Operations {
    public static final double RADIUS = 32;

    /** Told when a chair starts an operation: its level and position. */
    public interface Listener {
        void started(Level level, BlockPos chair);
    }

    private static final List<Listener> LISTENERS = new ArrayList<>();

    private Operations() {
    }

    public static void listen(Listener l) {
        LISTENERS.add(l);
    }

    /** Whether a chair is operating within {@code radius} blocks of {@code at}. */
    public static boolean near(Level level, Vec3 at, double radius) {
        return ChairQuiet.near(level, at, radius);
    }

    public static boolean near(Level level, BlockPos at, double radius) {
        return near(level, Vec3.atCenterOf(at), radius);
    }

    /** A chair has just started its operation (its first tick is marked here too, so it counts at once). */
    public static void started(Level level, BlockPos chair) {
        ChairQuiet.mark(level, chair, true);
        for (Listener l : List.copyOf(LISTENERS)) l.started(level, chair);
    }
}
