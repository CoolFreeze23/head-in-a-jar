package com.coolfreeze23.headinajar.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Puts a player's shoulder riders back in the world (a detail standing down takes its parrot off the shoulder). */
@Mixin(Player.class)
public interface PlayerShoulderInvoker {
    @Invoker("removeEntitiesOnShoulder")
    void headinajar$dropShoulders();
}
