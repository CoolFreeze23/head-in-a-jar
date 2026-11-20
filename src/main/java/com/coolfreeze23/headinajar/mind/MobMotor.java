package com.coolfreeze23.headinajar.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * The body half of a mob's own mind, for a mob whose vanilla mind the mod has switched off (a host: {@code
 * HostAiMixin} cancels {@code Mob.serverAiStep}, so no sensing, goals, brain, navigation or controls run).
 * <p>
 * Whatever decides where the mob goes (the headless chicken's run, later a worker's brain) calls {@link #tick} once a
 * tick from that cancelled AI step, and steers with {@link #walkTo}, {@link #halt} and the mob's own controls
 * ({@code getLookControl().setLookAt}, {@code getJumpControl().jump()}). {@link #tick} moves the body the way vanilla
 * would: navigation, then the move, look and jump controls, in vanilla's order (Mob.serverAiStep). It does nothing
 * else: no sensing, no goals, no brain; the caller is the mind.
 * <p>
 * Path penalties a mind wants while it runs (water, lava) are set with {@link #avoid} and put back with
 * {@link #release}; they are not saved, so a reload drops them with the mind. Server side only.
 */
public final class MobMotor {
    /** Each driven mob's path penalties as they were before {@link #avoid} changed them. */
    private static final Map<Mob, Map<PathType, Float>> SAVED_MALUS = new WeakHashMap<>();

    private MobMotor() {
    }

    /** One tick of the body: path following, then walking, looking and jumping, as vanilla's AI step does them. */
    public static void tick(Mob mob) {
        mob.getNavigation().tick();
        mob.getMoveControl().tick();
        mob.getLookControl().tick();
        mob.getJumpControl().tick();
    }

    /** Whether the mob has nowhere left to walk (no path, or the path is done). */
    public static boolean idle(Mob mob) {
        return mob.getNavigation().isDone();
    }

    /**
     * Plans a path to a spot and starts walking it at {@code speed} times the mob's walking speed. A path is refused
     * (and nothing changes) when there is none, when it does not reach, or when one of its nodes fails {@code keep}.
     * Returns whether the mob is now walking.
     */
    public static boolean walkTo(Mob mob, BlockPos to, double speed, Predicate<Node> keep) {
        Path path = mob.getNavigation().createPath(to, 0);
        if (path == null || !path.canReach()) return false;
        for (int i = 0; i < path.getNodeCount(); i++) {
            if (keep != null && !keep.test(path.getNode(i))) return false;
        }
        return mob.getNavigation().moveTo(path, speed);
    }

    /** Stops the mob where it is: the path dropped and no walking input left over from the move control. */
    public static void halt(Mob mob) {
        mob.getNavigation().stop();
        mob.setSpeed(0);
        mob.setZza(0);
        mob.setXxa(0);
        mob.setYya(0);
        mob.setJumping(false);
    }

    /** Sets path penalties while a mind drives the mob (-1 forbids a kind of block), remembering the old ones. */
    public static void avoid(Mob mob, Map<PathType, Float> malus) {
        Map<PathType, Float> saved = SAVED_MALUS.computeIfAbsent(mob, m -> new EnumMap<>(PathType.class));
        malus.forEach((type, value) -> {
            saved.putIfAbsent(type, mob.getPathfindingMalus(type));
            mob.setPathfindingMalus(type, value);
        });
    }

    /** Puts back the path penalties {@link #avoid} changed, and stops the mob. */
    public static void release(Mob mob) {
        Map<PathType, Float> saved = SAVED_MALUS.remove(mob);
        if (saved != null) saved.forEach(mob::setPathfindingMalus);
        halt(mob);
    }
}
