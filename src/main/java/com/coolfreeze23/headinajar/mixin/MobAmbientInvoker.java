package com.coolfreeze23.headinajar.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A creature's own ambient sound (protected in vanilla), for a Lookout of a species the staff table gives no alarm. */
@Mixin(Mob.class)
public interface MobAmbientInvoker {
    @Invoker("getAmbientSound")
    SoundEvent headinajar$ambient();
}
