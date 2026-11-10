package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A low plate that charges, at 500 FE/t from FE fed into it, a jar standing on it or its owner's head set down on it.
 * It says when it starts, when the jar is full and when it has nothing to give, and sparks while it charges.
 */
public class ChargingDockBlock extends BaseEntityBlock {
    public static final MapCodec<ChargingDockBlock> CODEC = simpleCodec(ChargingDockBlock::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 4, 15);
    public static final int RATE = 500;
    /** Height of the plate's top above the block's floor. */
    public static final double TOP = 4 / 16.0;

    /** What a dock last told each jar (or each owner, about their placed head). Not saved. */
    private enum Told { CHARGING, FULL, EMPTY }
    private static final Map<UUID, Told> JARS = new HashMap<>();
    private static final Map<UUID, Told> HEADS = new HashMap<>();

    public ChargingDockBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    /** A click, in any form: a body carrying its head sets it down on the plate; anyone else hears what it holds. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity dock)) return net.minecraft.world.InteractionResult.PASS;
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        ServerPlayer server = (ServerPlayer) player;
        JarState jar = FormActions.state(server);
        if (jar.form() == Form.LINKED && jar.carryingHead() && FormActions.setDownHeadAt(server, top(pos))) {
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        int stored = dock.energy.getEnergyStored();
        player.displayClientMessage(Component.translatable(stored > 0 ? "message.headinajar.dock_status" : "message.headinajar.dock_empty",
                stored, dock.energy.getMaxEnergyStored(), Component.translatable("block.headinajar.galvanic_pile")), true);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    /** Where something set down on this dock stands: the middle of the plate's top. */
    public static Vec3 top(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos).add(0, TOP, 0);
    }

    /** The dock a box rests on (a jar's feet, a placed head), if any: one whose plate it overlaps, the nearest first. */
    public static Entity under(Level level, AABB box) {
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        Vec3 centre = new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 0.3, box.minZ), BlockPos.containing(box.maxX, box.minY + 0.05, box.maxZ))) {
            if (!(level.getBlockEntity(p) instanceof Entity dock)) continue;
            // standing on the plate or sunk into it, not hovering a hop above it
            if (box.minY > p.getY() + TOP + 0.1 || box.maxY < p.getY()) continue;
            if (box.maxX <= p.getX() + 1 / 16.0 || box.minX >= p.getX() + 15 / 16.0 || box.maxZ <= p.getZ() + 1 / 16.0 || box.minZ >= p.getZ() + 15 / 16.0) continue;
            double d = Vec3.atBottomCenterOf(p).distanceToSqr(centre);
            if (d < bestDist) {
                bestDist = d;
                best = dock;
            }
        }
        return best;
    }

    /** The dock the player stands on, if any. */
    public static Entity under(Player player) {
        return under(player.level(), player.getBoundingBox());
    }

    /** The dock a player is looking at within reach, if any. */
    public static BlockPos aimed(ServerPlayer player) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && player.level().getBlockEntity(block.getBlockPos()) instanceof Entity) return block.getBlockPos();
        return null;
    }

    /** Moves charge from the dock under the player into their jar (every tick for a jarred player). */
    public static void charge(Player player, JarState state) {
        Entity dock = under(player);
        if (dock == null) {
            JARS.remove(player.getUUID());
            return;
        }
        Told told = settle(JARS.get(player.getUUID()), give(dock, state, RATE), state);
        if (told == JARS.put(player.getUUID(), told) || !(player instanceof ServerPlayer server)) return;
        int percent = Math.round(100f * state.charge() / JarState.maxCharge());
        server.displayClientMessage(switch (told) {
            case CHARGING -> Component.translatable("message.headinajar.dock_charging", percent);
            case FULL -> Component.translatable("message.headinajar.dock_full");
            case EMPTY -> Component.translatable("message.headinajar.dock_empty", 0, dock.energy.getMaxEnergyStored(), Component.translatable("block.headinajar.galvanic_pile"));
        }, true);
        if (told == Told.CHARGING) dock.chime();
    }

    /**
     * A placed head resting on a dock takes charge too (called every {@code ticks} ticks from the head). Its owner, out
     * walking their body, is told when it starts and when it is full.
     */
    public static void chargeHead(HeadJarEntity head, ServerPlayer owner, int ticks) {
        Entity dock = under(head.level(), head.getBoundingBox());
        if (dock == null) {
            HEADS.remove(owner.getUUID());
            return;
        }
        JarState state = FormActions.state(owner);
        if (!state.form().isDecapitated() || state.form().isJarred()) return;
        Told told = settle(HEADS.get(owner.getUUID()), give(dock, state, RATE * ticks), state);
        if (told == HEADS.put(owner.getUUID(), told)) return;
        int percent = Math.round(100f * state.charge() / JarState.maxCharge());
        owner.displayClientMessage(switch (told) {
            case CHARGING -> Component.translatable("message.headinajar.dock_head_charging", percent);
            case FULL -> Component.translatable("message.headinajar.dock_head_full");
            case EMPTY -> Component.translatable("message.headinajar.dock_head_empty", Component.translatable("block.headinajar.galvanic_pile"));
        }, true);
        if (told == Told.CHARGING) dock.chime();
    }

    /** A jar told it is full stays "full" while the dock tops up what a few ticks of upkeep took. */
    private static Told settle(Told before, Told now, JarState state) {
        return before == Told.FULL && now == Told.CHARGING && state.charge() >= JarState.maxCharge() * 0.95 ? Told.FULL : now;
    }

    /** Moves up to {@code most} FE into the jar; returns what the dock is doing. */
    private static Told give(Entity dock, JarState state, int most) {
        int room = JarState.maxCharge() - state.charge();
        if (room <= 0) return Told.FULL;
        int moved = Math.min(Math.min(most, room), dock.energy.getEnergyStored());
        if (moved <= 0) return Told.EMPTY;
        dock.energy.set(dock.energy.getEnergyStored() - moved);
        state.setCharge(state.charge() + moved);
        dock.sparks();
        return Told.CHARGING;
    }

    public static class Entity extends BlockEntity {
        private final Buffer energy = new Buffer(20_000, 2_000, 0, this::setChanged);
        private long lastSpark;

        public Entity(BlockPos pos, BlockState state) {
            super(ModRegistry.DOCK_BE.get(), pos, state);
        }

        public Buffer energy() {
            return energy;
        }

        /** A few sparks over the plate while it charges, every few ticks. */
        void sparks() {
            if (!(level instanceof ServerLevel server) || server.getGameTime() - lastSpark < 6) return;
            lastSpark = server.getGameTime();
            Vec3 at = top(worldPosition);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.05, at.z, 3, 0.3, 0.02, 0.3, 0.02);
        }

        /** The sound of charging starting. */
        void chime() {
            if (level != null) level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.5f, 1.8f);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("Energy", energy.serializeNBT(registries));
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        }
    }
}
