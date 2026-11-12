package com.coolfreeze23.headinajar.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The kit machines' running particles: the few things that say "working" from ten blocks off, where a pixel of moving
 * part can't. A few a second per machine, none beyond 32 blocks, the flashes none under the photosensitive option.
 */
public final class MachineFx {
    private static final double RANGE = 32;

    private static boolean near(BlockEntity machine) {
        if ((MachineRenderers.debugOff & 16) != 0) return false;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return machine.getBlockPos().getCenter().distanceToSqr(camera) < RANGE * RANGE;
    }

    /** The Pile's spark at a crank: a bright jump across the terminals that lingers a moment. */
    public static void pileSpark(BlockEntity pile) {
        Level level = pile.getLevel();
        if (level == null || !near(pile) || LinkHud.photosensitive()) return;
        Vec3 a = MachineKeyframes.world(pile, new Vec3(-1, 27, -5)), b = MachineKeyframes.world(pile, new Vec3(1, 27, -5));
        Vec3 mid = a.lerp(b, 0.5);
        level.addParticle(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 0, 0.015, 0);
        level.addParticle(ParticleTypes.END_ROD, mid.x, mid.y + 0.04, mid.z, (level.random.nextDouble() - 0.5) * 0.02, 0.03, (level.random.nextDouble() - 0.5) * 0.02);
        for (int i = 0; i <= 3; i++) {
            Vec3 p = a.lerp(b, i / 3.0);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0, 0, 0);
        }
    }

    /** A wisp off the burning meat, out at the pan's front edge. */
    public static void pileWisp(BlockEntity pile) {
        Level level = pile.getLevel();
        if (level == null || !near(pile)) return;
        Vec3 p = MachineKeyframes.world(pile, new Vec3((level.random.nextDouble() - 0.5) * 6, 8.5, -6));
        level.addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.02, 0);
    }

    /** Bubbles rising off the saline's surface while a batch mixes. */
    public static void mixerBubble(BlockEntity mixer, int level) {
        Level world = mixer.getLevel();
        if (world == null || !near(mixer)) return;
        double y = 15 + (level <= 0 ? 0 : 1 + 2 * level);
        Vec3 p = MachineKeyframes.world(mixer, new Vec3((world.random.nextDouble() - 0.5) * 6, y, (world.random.nextDouble() - 0.5) * 4));
        // a teal fizz that rises through the glass and lasts a second: the pop of a real bubble is gone in four frames
        world.addParticle(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF5FC8C0), p.x, p.y, p.z, 0, 0.05, 0);
    }

    /** A drop at the tap's spout: a stream while bottling, the odd drip otherwise. */
    public static void mixerDrip(BlockEntity mixer, boolean stream) {
        Level level = mixer.getLevel();
        if (level == null || !near(mixer)) return;
        Vec3 p = MachineKeyframes.world(mixer, new Vec3(-2, 9, -3.5));
        ParticleOptions type = stream ? ParticleTypes.FALLING_WATER : ParticleTypes.DRIPPING_WATER;
        level.addParticle(type, p.x, p.y, p.z, 0, 0, 0);
    }

    private MachineFx() {
    }
}
