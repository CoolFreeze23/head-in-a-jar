package com.coolfreeze23.headinajar.entity;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A block lifted out of the world by Telekinesis. Without a holder it falls as an ordinary falling block. */
public class HeldBlockEntity extends Entity {
    private static final EntityDataAccessor<BlockState> STATE = SynchedEntityData.defineId(HeldBlockEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final DustParticleOptions SHIMMER = new DustParticleOptions(new Vector3f(0x86 / 255f, 0xca / 255f, 0xc1 / 255f), 0.7f);
    private long lastHeld;

    public HeldBlockEntity(EntityType<? extends HeldBlockEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static HeldBlockEntity lift(Level level, BlockPos pos, BlockState state) {
        HeldBlockEntity held = new HeldBlockEntity(ModRegistry.HELD_BLOCK.get(), level);
        held.setBlockState(state);
        held.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        held.lastHeld = level.getGameTime();
        return held;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, Blocks.STONE.defaultBlockState());
    }

    public BlockState getBlockState() {
        return entityData.get(STATE);
    }

    public void setBlockState(BlockState state) {
        entityData.set(STATE, state);
    }

    /** The holder calls this every tick it still holds the block. */
    public void keepHeld() {
        lastHeld = level().getGameTime();
    }

    @Override
    public void tick() {
        super.tick();
        move(MoverType.SELF, getDeltaMovement());
        if (level().isClientSide && tickCount % 3 == 0) {
            level().addParticle(SHIMMER, getX() + random.nextGaussian() * 0.4, getY() + random.nextDouble(), getZ() + random.nextGaussian() * 0.4, 0, 0, 0);
        }
        if (!level().isClientSide && level().getGameTime() - lastHeld > 3) {
            drop(getDeltaMovement(), false);
        }
    }

    /** Lets go: the block becomes a falling block with this velocity, which can hurt what it lands on when thrown. */
    public FallingBlockEntity drop(Vec3 velocity, boolean thrown) {
        if (isRemoved()) return null;
        FallingBlockEntity falling = new FallingBlockEntity(level(), getX(), getY(), getZ(), getBlockState());
        falling.setDeltaMovement(velocity);
        falling.time = 1;
        if (thrown) falling.setHurtsEntities(2.0f, 40);
        level().addFreshEntity(falling);
        discard();
        return falling;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setBlockState(NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK), tag.getCompound("BlockState")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("BlockState", NbtUtils.writeBlockState(getBlockState()));
    }
}
