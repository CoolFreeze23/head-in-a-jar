package com.coolfreeze23.headinajar.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A machine's running sound (the Centrifuge's whine, the Mixer's paddle): one looped sound per machine whose volume
 * and pitch follow a part's speed, started when the part moves and let go when it rests or the machine is gone. The
 * smallest thing that works; a shared loop helper is meant to replace it.
 */
public final class MachineLoopSound extends AbstractTickableSoundInstance {
    private static final Map<BlockEntity, MachineLoopSound> PLAYING = new WeakHashMap<>();
    private final BlockEntity machine;
    private float wantVolume, wantPitch;

    private MachineLoopSound(BlockEntity machine, SoundEvent event) {
        super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.machine = machine;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.x = machine.getBlockPos().getX() + 0.5;
        this.y = machine.getBlockPos().getY() + 1.2;
        this.z = machine.getBlockPos().getZ() + 0.5;
    }

    /** Client tick of the machine: keep its loop at {@code volume} (0 lets it go) and {@code pitch}. */
    public static void follow(BlockEntity machine, DeferredHolder<SoundEvent, SoundEvent> event, float volume, float pitch) {
        if (machine.getLevel() == null || !machine.getLevel().isClientSide || !event.isBound()) return;
        MachineLoopSound sound = PLAYING.get(machine);
        if (sound != null && sound.isStopped()) {
            PLAYING.remove(machine);
            sound = null;
        }
        if (volume < 0.02f) {
            if (sound != null) sound.wantVolume = 0;
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null || player.distanceToSqr(machine.getBlockPos().getCenter()) > 24 * 24) return;
        if (sound == null) {
            sound = new MachineLoopSound(machine, event.get());
            PLAYING.put(machine, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
        sound.wantVolume = volume;
        sound.wantPitch = pitch;
    }

    @Override
    public void tick() {
        if (machine.isRemoved() || machine.getLevel() == null || wantVolume <= 0.01f && volume <= 0.02f) {
            stop();
            return;
        }
        volume += (wantVolume - volume) * 0.5f;
        pitch = wantPitch <= 0 ? 1 : wantPitch;
        // released by its machine: fade out
        wantVolume *= 0.97f;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
