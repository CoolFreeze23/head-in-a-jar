package com.coolfreeze23.headinajar.machine;

import com.coolfreeze23.headinajar.feedback.Refuser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A block entity built on the machine kit. It owns a {@link MachineKit} (lamp, levels, clock, rate, openers, the
 * update tag, block events, the top cell) and adds its own few synced fields. See the kit's notes for an example.
 */
public interface KitMachine extends Refuser {
    MachineKit kit();

    /** Server: the machine's own synced fields (only what a client draws), into the update tag. */
    void writeSync(CompoundTag tag);

    /** Client: the same fields back. */
    void readSync(CompoundTag tag);

    /** Client, once a tick: speed-driven and chased motion (kept on the block entity, drawn with the partial tick). */
    default void clientTick() {
    }

    /** Which part rattles for this refusal (the block event's parameter); 0 for the machine's lead part. */
    default int refusalPart(String reason) {
        return 0;
    }

    @Override
    default Vec3 refusalOrigin() {
        return Vec3.atCenterOf(kit().pos());
    }

    /** The house refusal on the machine itself: its lead part rattles and the lamp goes red for a moment. */
    @Override
    default void showRefusal(ServerPlayer player, String reason) {
        kit().refuse(refusalPart(reason));
    }
}
