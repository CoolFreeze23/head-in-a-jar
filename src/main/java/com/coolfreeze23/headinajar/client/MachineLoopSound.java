package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.client.fx.SoundLoops;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * A machine's running sound (the Centrifuge's whine, the Mixer's paddle, the Body Printer's carriage, the Jammer's
 * turbine, the Tesla Node's hum, the Array's urn): asked for every client tick of the machine with the volume and pitch
 * its moving part gives, and played through the shared loop mix ({@link SoundLoops}), which groups a room of identical
 * machines into one loop and keeps at most six loops heard. A volume under 0.02 lets the loop fade out.
 */
public final class MachineLoopSound {
    /** Client tick of the machine: keep its loop at {@code volume} (0 lets it go) and {@code pitch}. */
    public static void follow(BlockEntity machine, DeferredHolder<SoundEvent, SoundEvent> event, float volume, float pitch) {
        if (machine.getLevel() == null || !machine.getLevel().isClientSide || !event.isBound() || machine.isRemoved()) return;
        if (volume < 0.02f) return;
        var player = Minecraft.getInstance().player;
        Vec3 at = Vec3.atCenterOf(machine.getBlockPos()).add(0, 0.7, 0);
        if (player == null || player.distanceToSqr(at) > 24 * 24) return;
        SoundLoops.want(machine, event.get(), SoundSource.BLOCKS, at, volume, pitch <= 0 ? 1 : pitch);
    }

    private MachineLoopSound() {
    }
}
