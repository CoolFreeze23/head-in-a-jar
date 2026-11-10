package com.coolfreeze23.headinajar.feedback;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Nudge: what a bare jar (no Manipulator Arm) is told when it reaches for something. The line depends on what it
 * reached for. Shared by the client, which cancels most of these clicks before they are sent, and the server.
 */
public final class Nudge {
    public static final String USE = "jar.no_arm.use", CONTAINER = "jar.no_arm.container", CREATURE = "jar.no_arm.creature",
            ATTACK = "jar.no_arm.attack", VILLAGER = "jar.no_arm.villager", MINE = "jar.no_arm.mine", PICKUP = "jar.no_arm.pickup";
    public static final List<String> REASONS = List.of(USE, CONTAINER, CREATURE, ATTACK, VILLAGER, MINE, PICKUP);

    private Nudge() {
    }

    /** The reason for a bare jar using ({@code attack} false) or attacking what this hit result points at. */
    public static String reason(Level level, HitResult hit, boolean attack) {
        if (hit instanceof EntityHitResult e) return forEntity(e.getEntity(), attack);
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) return forBlock(level, b.getBlockPos(), b.getLocation(), attack);
        return USE;
    }

    public static String forEntity(Entity target, boolean attack) {
        if (target instanceof ItemEntity) return PICKUP;
        if (attack) return ATTACK;
        if (target instanceof AbstractVillager) return VILLAGER;
        return target instanceof LivingEntity ? CREATURE : USE;
    }

    /** A block, or an item lying where the jar is looking. */
    public static String forBlock(Level level, BlockPos pos, Vec3 at, boolean attack) {
        if (!level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(0.6), Entity::isAlive).isEmpty()) return PICKUP;
        if (attack) return MINE;
        var state = level.getBlockState(pos);
        if (level.getBlockEntity(pos) instanceof Container || state.getMenuProvider(level, pos) != null) return CONTAINER;
        return USE;
    }

    /** The arguments every Nudge line takes: the Manipulator Arm's name, then the Magnet's. */
    public static Object[] args() {
        return new Object[]{Component.translatable("item.headinajar.manipulator_arm"), Component.translatable("item.headinajar.magnet")};
    }
}
