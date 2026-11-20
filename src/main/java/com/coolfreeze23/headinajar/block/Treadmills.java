package com.coolfreeze23.headinajar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Who is walking a Corporate Wellness Treadmill, and where the running belts are, per level (server side). Kept by the
 * belts' block entities as they clock walkers in and out, so a host asks "am I on a belt?" without a block read (its
 * rot doubles; a headless chicken stops running), and the Cardio Department sums only belts it knows of. Nothing here
 * is saved: a belt finds its walker again on its next check after a load.
 */
public final class Treadmills {
    /** Walker ids and the foot of the belt each walks (null: a belt not named). */
    private static final Map<ResourceKey<Level>, Map<Integer, BlockPos>> WALKERS = new HashMap<>();
    /** Running belts' feet, with their output this tick (FE/t, before the push). */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Double>> BELTS = new HashMap<>();

    private Treadmills() {
    }

    /** The most any body steps up while on or beside a belt: the console end's wall is 1.5 blocks over the 6 px deck. */
    public static final float MAX_STEP = 1.0f;

    /**
     * A body's step-up beside a belt: a body that steps more than a block (a camel steps 1.5) steps one block while a
     * Treadmill lies under or beside its feet, so the console end's wall stops it. Others are left as they are (and are
     * never looked up: only bodies stepping over a block read the blocks round them).
     */
    public static float stepBesideBelt(Entity e, float step) {
        if (step <= MAX_STEP) return step;
        var box = e.getBoundingBox();
        int y0 = net.minecraft.util.Mth.floor(box.minY - 0.5), y1 = net.minecraft.util.Mth.floor(box.minY + 0.5);
        for (BlockPos pos : BlockPos.betweenClosed(net.minecraft.util.Mth.floor(box.minX - 0.5), y0, net.minecraft.util.Mth.floor(box.minZ - 0.5),
                net.minecraft.util.Mth.floor(box.maxX + 0.5), y1, net.minecraft.util.Mth.floor(box.maxZ + 0.5))) {
            if (e.level().getBlockState(pos).getBlock() instanceof TreadmillBlock) return MAX_STEP;
        }
        return step;
    }

    /** Whether this entity is clocked in on a belt. */
    public static boolean isWalker(Entity e) {
        if (e.level().isClientSide) return false;
        Map<Integer, BlockPos> ids = WALKERS.get(e.level().dimension());
        return ids != null && ids.containsKey(e.getId());
    }

    public static void clockIn(Level level, Entity walker) {
        clockIn(level, walker, null);
    }

    /** Clocks a walker in on the belt whose foot is {@code foot} (where its loot goes if it rots away on the belt). */
    public static void clockIn(Level level, Entity walker, BlockPos foot) {
        WALKERS.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(walker.getId(), foot == null ? null : foot.immutable());
    }

    public static void clockOut(Level level, int walkerId) {
        Map<Integer, BlockPos> ids = WALKERS.get(level.dimension());
        if (ids != null) ids.remove(walkerId);
    }

    /**
     * Whether a ridden host stands on a running belt, so its rider's forward input walks it in place. Read on the
     * rider's client from the block under its feet (no packet).
     */
    public static boolean holdsForward(LivingEntity host) {
        return onRunningBelt(host);
    }

    /**
     * Whether an entity's feet are on a running belt: the block it stands in is a Treadmill with {@code RUNNING} (the
     * deck is 6 px, so a walker's feet are inside the belt's own cell). Both sides; the client reads it to walk a host
     * in place and to hold its rider's forward input.
     */
    public static boolean onRunningBelt(Entity e) {
        BlockState state = e.level().getBlockState(BlockPos.containing(e.getX(), e.getY() + 0.01, e.getZ()));
        return state.getBlock() instanceof TreadmillBlock && state.getValue(TreadmillBlock.RUNNING)
                && e.getY() - Math.floor(e.getY()) <= TreadmillBlock.DECK + 0.1;
    }

    /** A belt's output this tick (0 or less: it stopped). */
    public static void output(Level level, BlockPos foot, double fePerTick) {
        Map<BlockPos, Double> belts = BELTS.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        if (fePerTick > 0) belts.put(foot.immutable(), fePerTick);
        else belts.remove(foot);
    }

    /** The running belts of a level and what each makes (read only). */
    public static Map<BlockPos, Double> belts(Level level) {
        return BELTS.getOrDefault(level.dimension(), Map.of());
    }

    /**
     * A body that rots away on the belt falls apart onto the foot cell, so a hopper under the foot takes it all (the
     * belt is not a full block, so a hopper below reaches through it).
     */
    @SubscribeEvent
    public static void walkerDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        Map<Integer, BlockPos> ids = WALKERS.get(e.level().dimension());
        BlockPos foot = ids == null ? null : ids.get(e.getId());
        if (foot == null) return;
        for (ItemEntity item : event.getDrops()) {
            item.setPos(foot.getX() + 0.5, foot.getY() + TreadmillBlock.DECK + 0.05, foot.getZ() + 0.5);
            item.setDeltaMovement(0, 0, 0);
        }
    }

    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            WALKERS.remove(level.dimension());
            BELTS.remove(level.dimension());
        }
    }
}
