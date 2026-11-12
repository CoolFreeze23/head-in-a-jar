package com.coolfreeze23.headinajar.smoke.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Dev-only: the smoke bystander has no client to confirm a teleport, so the harness confirms it. */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGameListenerAccess {
    @Accessor("awaitingPositionFromClient")
    void hij$setAwaitingPosition(Vec3 position);

    @Accessor("awaitingPositionFromClient")
    Vec3 hij$awaitingPosition();
}
