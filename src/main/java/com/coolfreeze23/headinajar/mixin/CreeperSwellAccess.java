package com.coolfreeze23.headinajar.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A creeper's fuse (private in vanilla): a cat host's Hiss winds it back to nothing. */
@Mixin(Creeper.class)
public interface CreeperSwellAccess {
    @Accessor("swell")
    void headinajar$setSwell(int swell);
}
