package com.coolfreeze23.headinajar.machine;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A machine drawn taller than a block. It takes the cell above as a hidden {@code machine_top}, which passes clicks,
 * collision, hoppers, pipes and cables through to it (the door's pattern: one item, nothing assembled). Shapes are
 * declared for the machine facing north and turned with it.
 */
public interface TallMachine {
    /** The machine's body above its own cell, in the top cell's coordinates (0-16), facing north. */
    VoxelShape upperShapeNorth();

    /** The facing a shape is turned to (north for a machine with no facing). */
    default Direction facingOf(BlockState state) {
        return state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    /** Which upper half the top cell draws. */
    MachineTopBlock.Kind topKind();

    /** The top cell's state for the machine state {@code lower}: kind, facing, and its saline level if it has one. */
    static BlockState topState(BlockState lower) {
        BlockState top = ModRegistry.MACHINE_TOP.get().defaultBlockState();
        if (!(lower.getBlock() instanceof TallMachine machine)) return top;
        top = top.setValue(MachineTopBlock.MACHINE, machine.topKind()).setValue(MachineTopBlock.FACING, machine.facingOf(lower));
        // what the upper half shows: the properties the top cell shares with the machine, by name
        for (var p : lower.getProperties()) {
            String name = p.getName();
            if ((name.equals("saline") || name.equals("broth") || name.equals("serum") || name.equals("level")) && p instanceof net.minecraft.world.level.block.state.properties.IntegerProperty level) {
                top = top.setValue(MachineTopBlock.SALINE, Math.min(7, lower.getValue(level)));
            } else if (p == MachineLamp.LAMP && machine.lampAbove()) {
                top = top.setValue(MachineTopBlock.LAMP, lower.getValue(MachineLamp.LAMP));
            } else if (name.equals("stab") && p instanceof net.minecraft.world.level.block.state.properties.IntegerProperty stab) {
                top = top.setValue(MachineTopBlock.STAB, Math.min(2, lower.getValue(stab)));
            } else if (name.equals("counter") && p instanceof net.minecraft.world.level.block.state.properties.IntegerProperty rolling) {
                top = top.setValue(MachineTopBlock.STAB, Math.min(2, lower.getValue(rolling)));
            }
        }
        return top;
    }

    /** The lamp's lens stands in the upper half (the top cell then follows the lamp; else it ignores it, no re-mesh). */
    default boolean lampAbove() {
        return false;
    }

    /** What of the upper body collides (defaults to all of it). */
    default VoxelShape upperCollisionNorth() {
        return upperShapeNorth();
    }

    default VoxelShape upperShape(BlockState state) {
        return rotate(upperShapeNorth(), facingOf(state));
    }

    /** Whether this machine has a top cell (a one-block member of a family of tall ones has none). */
    default boolean hasTop() {
        return true;
    }

    /** Puts the top cell above {@code pos} if the cell is free; false if something else stands there. */
    static boolean ensureTop(Level level, BlockPos pos) {
        BlockState lower = level.getBlockState(pos);
        if (!(lower.getBlock() instanceof TallMachine tall) || !tall.hasTop()) return false;
        BlockPos above = pos.above();
        BlockState there = level.getBlockState(above);
        BlockState want = topState(lower);
        if (there.is(ModRegistry.MACHINE_TOP.get())) {
            if (there != want) level.setBlock(above, want, Block.UPDATE_ALL);
            return true;
        }
        if (!level.isInWorldBounds(above) || !there.canBeReplaced() || !there.getFluidState().isEmpty()) return false;
        level.setBlock(above, want, Block.UPDATE_ALL);
        return true;
    }

    /** The machine below is gone: its top cell goes too (dropping nothing). Call from the machine's {@code onRemove}. */
    static void removeTop(LevelAccessor level, BlockPos pos) {
        BlockPos above = pos.above();
        if (level.getBlockState(above).is(ModRegistry.MACHINE_TOP.get())) {
            level.setBlock(above, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    /** Whether the machine at {@code pos} has room for its top cell (placement). */
    static boolean headroom(BlockGetter level, BlockPos pos, net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockPos above = pos.above();
        return context.getLevel().isInWorldBounds(above) && level.getBlockState(above).canBeReplaced(context);
    }

    /** A point of the machine's model (pixels about its bottom centre, facing north) in the world, turned with it. */
    static net.minecraft.world.phys.Vec3 local(BlockPos pos, BlockState state, double x, double y, double z) {
        Direction facing = state.getBlock() instanceof TallMachine tall ? tall.facingOf(state) : Direction.NORTH;
        float turn = switch (facing) {
            case SOUTH -> 180;
            case WEST -> 90;
            case EAST -> 270;
            default -> 0;
        };
        net.minecraft.world.phys.Vec3 v = new net.minecraft.world.phys.Vec3(x / 16, y / 16, z / 16).yRot((float) Math.toRadians(turn));
        return new net.minecraft.world.phys.Vec3(pos.getX() + 0.5 + v.x, pos.getY() + v.y, pos.getZ() + 0.5 + v.z);
    }

    /** {@code shape} (north) turned to {@code facing} about the cell's centre. */
    static VoxelShape rotate(VoxelShape shape, Direction facing) {
        if (facing == Direction.NORTH) return shape;
        VoxelShape[] out = {Shapes.empty()};
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double ax0 = x0, az0 = z0, ax1 = x1, az1 = z1;
            for (int i = 0; i < turns; i++) {
                // a quarter turn clockwise seen from above (north -> east): (x, z) -> (1 - z, x)
                double nx0 = 1 - az1, nx1 = 1 - az0, nz0 = ax0, nz1 = ax1;
                ax0 = nx0;
                ax1 = nx1;
                az0 = nz0;
                az1 = nz1;
            }
            out[0] = Shapes.or(out[0], Shapes.box(ax0, y0, az0, ax1, y1, az1));
        });
        return out[0].optimize();
    }
}
