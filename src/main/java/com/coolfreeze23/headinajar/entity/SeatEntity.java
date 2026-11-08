package com.coolfreeze23.headinajar.entity;

import com.coolfreeze23.headinajar.block.SeparationChairBlock;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The invisible seat a player rides while sitting in a chair. It goes away when empty or when the chair is gone. */
public class SeatEntity extends Entity {
    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    public static SeatEntity at(Level level, BlockPos chair, Direction facing) {
        SeatEntity seat = new SeatEntity(ModRegistry.SEAT.get(), level);
        seat.moveTo(chair.getX() + 0.5, chair.getY() + 0.35, chair.getZ() + 0.5, facing.toYRot(), 0);
        level.addFreshEntity(seat);
        return seat;
    }

    public BlockPos chairPos() {
        return BlockPos.containing(getX(), getY() - 0.3, getZ());
    }

    /** The chair under this seat, if it is still there. */
    public SeparationChairBlockEntity chair() {
        return level().getBlockEntity(chairPos()) instanceof SeparationChairBlockEntity chair ? chair : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (getPassengers().isEmpty() || !(level().getBlockState(chairPos()).getBlock() instanceof SeparationChairBlock))) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return Vec3.ZERO;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Direction facing = level().getBlockState(chairPos()).getOptionalValue(SeparationChairBlock.FACING).orElse(Direction.NORTH);
        return Vec3.atBottomCenterOf(chairPos().relative(facing));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
