package com.coolfreeze23.headinajar.feedback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The thing that said no: a machine, a mecha, a host. Its refusal sound comes from {@link #refusalOrigin()}, and
 * {@link #showRefusal} is where it shows the refusal on itself (a lamp turning red, a cockpit shaking its head).
 * Block entities and entities implement it to get their own visual; anything else is wrapped by {@link #at}.
 */
public interface Refuser {
    /** Where the refusal sound plays from. */
    Vec3 refusalOrigin();

    /** The sound category the refusal plays in: BLOCKS for a machine, NEUTRAL for a mecha or a creature. */
    default SoundSource refusalSource() {
        return SoundSource.BLOCKS;
    }

    /** Server side, once for every refusal that gets past the debounce. Nothing visual by default. */
    default void showRefusal(ServerPlayer player, String reason) {
    }

    /** A block entity as the refusing thing: itself if it implements this, else just its position. */
    static Refuser at(BlockEntity block) {
        if (block instanceof Refuser refuser) return refuser;
        Vec3 at = Vec3.atCenterOf(block.getBlockPos());
        return () -> at;
    }

    /** An entity as the refusing thing: itself if it implements this, else just its position. */
    static Refuser at(Entity entity) {
        if (entity instanceof Refuser refuser) return refuser;
        return new Refuser() {
            @Override
            public Vec3 refusalOrigin() {
                return entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            }

            @Override
            public SoundSource refusalSource() {
                return SoundSource.NEUTRAL;
            }
        };
    }
}
