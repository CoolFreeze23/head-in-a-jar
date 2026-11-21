package com.coolfreeze23.headinajar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Where a Separation Chair is operating right now, so nothing else talks over its twelve seconds: DOLORES's memos wait
 * while any chair within {@value #RADIUS} blocks of the reader is running. Each running chair marks itself every tick;
 * a mark older than a few ticks (a chair broken mid-operation, or unloaded) no longer counts.
 */
public final class ChairQuiet {
    public static final double RADIUS = 32;
    private static final int STALE = 5;
    private record Seat(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final Map<Seat, Long> RUNNING = new HashMap<>();

    private ChairQuiet() {
    }

    public static void mark(Level level, BlockPos pos, boolean running) {
        Seat seat = new Seat(level.dimension(), pos.immutable());
        if (running) RUNNING.put(seat, level.getGameTime());
        else RUNNING.remove(seat);
    }

    /** Whether a chair is operating within {@value #RADIUS} blocks of this player. */
    public static boolean near(Player player) {
        return near(player.level(), player.position(), RADIUS);
    }

    /** Whether a chair is operating within {@code radius} blocks of a point in this level. */
    public static boolean near(Level level, net.minecraft.world.phys.Vec3 at, double radius) {
        if (RUNNING.isEmpty()) return false;
        long now = level.getGameTime();
        RUNNING.values().removeIf(t -> now - t > STALE || t > now);
        for (Seat seat : RUNNING.keySet()) {
            if (seat.dimension() == level.dimension() && seat.pos().getCenter().distanceToSqr(at) <= radius * radius) return true;
        }
        return false;
    }
}
