package com.coolfreeze23.headinajar.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A mob's persistence flag, read before a hire and put back on Let go (vanilla has no way to unset it). */
@Mixin(Mob.class)
public interface MobAccessor {
    @Accessor("persistenceRequired")
    boolean headinajar$persistent();

    @Accessor("persistenceRequired")
    void headinajar$setPersistent(boolean value);
}
