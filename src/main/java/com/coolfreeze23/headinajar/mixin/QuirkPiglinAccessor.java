package com.coolfreeze23.headinajar.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * A piglin's time outside the Nether and its turn into a zombified piglin, which vanilla runs in
 * {@code customServerAiStep} (switched off for staff): the staff quirk counts the same ticks and converts it the same way.
 */
@Mixin(AbstractPiglin.class)
public interface QuirkPiglinAccessor {
    @Accessor("timeInOverworld")
    int headinajar$timeInOverworld();

    @Accessor("timeInOverworld")
    void headinajar$setTimeInOverworld(int ticks);

    @Invoker("finishConversion")
    void headinajar$finishConversion(ServerLevel level);

    @Invoker("playConvertedSound")
    void headinajar$playConvertedSound();
}
