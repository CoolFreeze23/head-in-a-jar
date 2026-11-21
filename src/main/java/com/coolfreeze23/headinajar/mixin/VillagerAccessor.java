package com.coolfreeze23.headinajar.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * A villager's levelling after a trade, which vanilla runs in {@code customServerAiStep} (switched off for staff): the
 * staff brain runs the same lines off shift.
 */
@Mixin(Villager.class)
public interface VillagerAccessor {
    @Accessor("updateMerchantTimer")
    int headinajar$updateMerchantTimer();

    @Accessor("updateMerchantTimer")
    void headinajar$setUpdateMerchantTimer(int ticks);

    @Accessor("increaseProfessionLevelOnUpdate")
    boolean headinajar$levelUp();

    @Accessor("increaseProfessionLevelOnUpdate")
    void headinajar$setLevelUp(boolean value);

    @Invoker("increaseMerchantCareer")
    void headinajar$increaseMerchantCareer();
}
