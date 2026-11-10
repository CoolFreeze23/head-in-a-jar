package com.coolfreeze23.headinajar.module;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.entity.HeldBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.JarState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The Telekinesis module: grab what you look at, carry it in front of the jar, drop it or throw it. Server side.
 * <p>
 * A held creature or item never has its saved NoGravity flag touched: the hold cancels gravity each tick it lasts, so
 * whatever ends the hold (a logout, a server stop, a portal, the holder's death) leaves something that simply falls.
 */
public final class Telekinesis {
    public static void toggle(ServerPlayer player, JarState state) {
        if (state.heldEntity() >= 0) {
            release(player, state);
        } else if (state.has(ModuleType.TELEKINESIS)) {
            grab(player, state);
        }
    }

    /** Grabs the nearest thing on the player's line of sight; returns the held entity, or null after saying why not. */
    public static Entity grab(ServerPlayer player, JarState state) {
        ServerLevel level = player.serverLevel();
        double range = Config.TELEKINESIS_RANGE.get();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));

        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double blockDist = blockHit.getType() == HitResult.Type.MISS ? range * range : blockHit.getLocation().distanceToSqr(eye);
        AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(player.getLookAngle().scale(Math.sqrt(blockDist))), box,
                e -> canLift(player, e), blockDist);

        Entity held = null;
        if (entityHit != null) {
            held = entityHit.getEntity();
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState block = level.getBlockState(pos);
            String why = whyNot(player, level, pos, block);
            if (why != null) {
                Refusals.deny(player, why);
                return null;
            }
            level.removeBlock(pos, false);
            held = HeldBlockEntity.lift(level, pos, block);
            level.addFreshEntity(held);
        }
        if (held == null) {
            Refusals.deny(player, "tk.nothing");
            return null;
        }
        state.setHeldEntity(held.getId());
        state.spend(500);
        level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 2.0f);
        return held;
    }

    private static boolean canLift(ServerPlayer player, Entity e) {
        if (e == player || e.isSpectator() || !e.isAlive() || e.isPassenger() && e.getVehicle() == player) return false;
        if (e.getType().is(Tags.EntityTypes.BOSSES)) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return e instanceof LivingEntity || e instanceof ItemEntity || e instanceof HeldBlockEntity || e instanceof FallingBlockEntity;
    }

    /** Why this block can't be lifted (a refusal reason), or null when it can. */
    private static String whyNot(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState block) {
        if (block.isAir() || !block.getFluidState().isEmpty() && block.getFluidState().isSource()) return "tk.nothing";
        if (block.hasBlockEntity()) return "tk.machine";
        float hardness = block.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > Config.TELEKINESIS_MAX_HARDNESS.get()) return "tk.too_hard";
        if (block.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || block.hasProperty(BlockStateProperties.BED_PART)) return "tk.too_big";
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return "tk.protected";
        return NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, block, player)).isCanceled() ? "tk.protected" : null;
    }

    public static void tick(ServerPlayer player, JarState state) {
        if (state.heldEntity() < 0) return;
        Entity held = player.level().getEntity(state.heldEntity());
        double range = Config.TELEKINESIS_RANGE.get() + 8;
        if (held == null || !held.isAlive() || !state.has(ModuleType.TELEKINESIS) || held.distanceToSqr(player) > range * range) {
            release(player, state);
            return;
        }
        double distance = 2.2 + held.getBbWidth() * 0.7;
        Vec3 target = player.getEyePosition().add(player.getLookAngle().scale(distance)).subtract(0, held.getBbHeight() / 2, 0);
        Vec3 pull = target.subtract(held.position()).scale(0.35);
        if (pull.length() > 1.2) pull = pull.normalize().scale(1.2);
        // items and falling blocks take their gravity before they move: give it back so they hang where the pull puts
        // them (a creature takes it after moving, and the next tick's pull replaces what is left)
        if (fallsBeforeMoving(held)) pull = pull.add(0, held.getGravity(), 0);
        held.setDeltaMovement(pull);
        held.fallDistance = 0;
        // a creature's client follows its position updates, and a lifted block's go out every tick anyway; a player
        // moves itself and an item flies itself, so those need the velocity every tick
        if (held instanceof Player || held instanceof ItemEntity || held instanceof FallingBlockEntity) held.hurtMarked = true;
        if (held instanceof HeldBlockEntity block) block.keepHeld();
        if (held instanceof Mob mob) mob.getNavigation().stop();
        state.spend(50);
    }

    private static boolean fallsBeforeMoving(Entity held) {
        return !(held instanceof LivingEntity) && !(held instanceof HeldBlockEntity);
    }

    /** Lets go of whatever is held. */
    public static void release(ServerPlayer player, JarState state) {
        Entity held = state.heldEntity() >= 0 ? player.level().getEntity(state.heldEntity()) : null;
        state.setHeldEntity(-1);
        if (held == null) return;
        if (held instanceof HeldBlockEntity block) {
            block.drop(block.getDeltaMovement().scale(0.3), false);
        } else {
            letFall(held);
        }
    }

    /**
     * What is let go falls. Earlier versions switched gravity off with the saved flag and could leave a creature hanging
     * for good; lifting it once more and letting go gives its gravity back.
     */
    private static void letFall(Entity held) {
        if (held.isNoGravity()) held.setNoGravity(false);
    }

    /** Throws what is held along the player's look. */
    public static void throwHeld(ServerPlayer player, JarState state) {
        Entity held = state.heldEntity() >= 0 ? player.level().getEntity(state.heldEntity()) : null;
        state.setHeldEntity(-1);
        if (held == null) return;
        Vec3 velocity = player.getLookAngle().scale(held instanceof ItemEntity ? 1.2 : 1.6);
        if (held instanceof HeldBlockEntity block) {
            block.drop(velocity, true);
        } else {
            letFall(held);
            held.setDeltaMovement(velocity);
            held.hurtMarked = true;
        }
        state.spend(2000);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.7f, 1.6f);
    }

    /** A player left floating by an earlier version's hold gets their gravity back when they log in. */
    public static void landStranded(Player player) {
        if (player.isNoGravity()) player.setNoGravity(false);
    }

    private Telekinesis() {
    }
}
