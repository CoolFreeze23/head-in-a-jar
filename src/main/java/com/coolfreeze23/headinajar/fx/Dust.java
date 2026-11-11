package com.coolfreeze23.headinajar.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Dust from the block underfoot: one call throws that block's own particles for a landing or a heavy step. */
public final class Dust {
    /** The block a thing standing at {@code at} stands on, or null when that is air or draws nothing. */
    public static BlockState under(Level level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at.x, at.y - 0.2, at.z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) return null;
        return state;
    }

    /**
     * Client: a ring of {@code count} block particles thrown outward along the ground at {@code at}, {@code spread} blocks
     * across (count already scaled by the caller for the particle setting, or use {@code Comfort.particles}).
     */
    public static void client(Level level, Vec3 at, int count, float spread) {
        BlockState state = under(level, at);
        if (state == null || count <= 0) return;
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, state);
        for (int i = 0; i < count; i++) {
            float angle = i * Mth.TWO_PI / count + level.random.nextFloat() * 0.4f;
            double r = spread * (0.3 + level.random.nextDouble() * 0.2);
            double dx = Mth.cos(angle), dz = Mth.sin(angle);
            level.addParticle(dust, at.x + dx * r, at.y + 0.05, at.z + dz * r, dx * 0.15, 0.12 + level.random.nextDouble() * 0.08, dz * 0.15);
        }
    }

    /** Server: the same as one packet (vanilla's block particle burst), for callers with no memo of their own. */
    public static void server(ServerLevel level, Vec3 at, int count, float spread) {
        BlockState state = under(level, at);
        if (state == null || count <= 0) return;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y + 0.05, at.z, count, spread * 0.3, 0.02, spread * 0.3, 0.15);
    }

    private Dust() {
    }
}
