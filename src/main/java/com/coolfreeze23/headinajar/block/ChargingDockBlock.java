package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A low plate that charges a jar standing on it at 500 FE/t, from FE fed into it. */
public class ChargingDockBlock extends BaseEntityBlock {
    public static final MapCodec<ChargingDockBlock> CODEC = simpleCodec(ChargingDockBlock::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 4, 15);
    public static final int RATE = 500;

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

    /** The dock the player stands on, if any. */
    public static Entity under(Player player) {
        BlockPos pos = player.blockPosition();
        if (player.level().getBlockEntity(pos) instanceof Entity dock) return dock;
        if (player.onGround() && player.level().getBlockEntity(pos.below()) instanceof Entity dock) return dock;
        return null;
    }

    /** Moves charge from the dock under the player into their jar. */
    public static void charge(Player player, JarState state) {
        Entity dock = under(player);
        if (dock == null) return;
        int room = JarState.maxCharge() - state.charge();
        int moved = Math.min(Math.min(RATE, room), dock.energy.getEnergyStored());
        if (moved > 0) {
            dock.energy.set(dock.energy.getEnergyStored() - moved);
            state.setCharge(state.charge() + moved);
        }
    }

    public static class Entity extends BlockEntity {
        private final Buffer energy = new Buffer(20_000, 2_000, 0, this::setChanged);

        public Entity(BlockPos pos, BlockState state) {
            super(ModRegistry.DOCK_BE.get(), pos, state);
        }

        public Buffer energy() {
            return energy;
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
