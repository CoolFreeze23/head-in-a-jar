package com.coolfreeze23.headinajar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Where the fed heads are, per level and per desk job ("cow", "cat", "golem"...): kept as heads are fed, run dry, load
 * and unload, so nothing ever scans for them. A query walks one small set. An empty index costs one map read.
 */
public final class DeskIndex {
    private static final Map<LevelAccessor, Map<String, Set<BlockPos>>> INDEX = new HashMap<>();

    private DeskIndex() {
    }

    /** Puts a head in (fed, with a desk job, and desk jobs on) or takes it out. */
    public static void update(ServerLevel level, JarredHeadBlockEntity head) {
        remove(level, head);
        if (head.isRemoved() || !head.fed()) return;
        String job = com.coolfreeze23.headinajar.staff.StaffTable.desk(head.entityType());
        if (job == null) return;
        INDEX.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(job, j -> new HashSet<>()).add(head.getBlockPos().immutable());
    }

    public static void remove(ServerLevel level, JarredHeadBlockEntity head) {
        Map<String, Set<BlockPos>> jobs = INDEX.get(level);
        if (jobs == null) return;
        BlockPos pos = head.getBlockPos();
        jobs.values().removeIf(set -> set.remove(pos) && set.isEmpty());
        if (jobs.isEmpty()) INDEX.remove(level);
    }

    /** The level is going: forget it. */
    public static void unload(LevelAccessor level) {
        INDEX.remove(level);
    }

    /** The fed heads with this job (an empty set when none). Do not change it. */
    public static Set<BlockPos> all(LevelAccessor level, String job) {
        Map<String, Set<BlockPos>> jobs = INDEX.get(level);
        if (jobs == null) return Set.of();
        Set<BlockPos> set = jobs.get(job);
        return set == null ? Set.of() : set;
    }

    /** The nearest fed head with this job within {@code r} blocks of {@code at} (centre to centre), or null. */
    public static BlockPos near(LevelAccessor level, String job, net.minecraft.world.phys.Vec3 at, double r) {
        Set<BlockPos> set = all(level, job);
        if (set.isEmpty()) return null;
        BlockPos best = null;
        double bestD = r * r;
        for (BlockPos p : set) {
            double d = p.getCenter().distanceToSqr(at);
            if (d <= bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    public static boolean any(LevelAccessor level, String job, net.minecraft.world.phys.Vec3 at, double r) {
        return near(level, job, at, r) != null;
    }
}
