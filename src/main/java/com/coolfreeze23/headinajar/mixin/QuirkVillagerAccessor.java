package com.coolfreeze23.headinajar.mixin;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The player a villager last traded with, whose reputation vanilla raises in {@code customServerAiStep} (switched off for
 * staff): the staff quirk does it off shift.
 */
@Mixin(Villager.class)
public interface QuirkVillagerAccessor {
    @Accessor("lastTradedPlayer")
    Player headinajar$lastTradedPlayer();

    @Accessor("lastTradedPlayer")
    void headinajar$setLastTradedPlayer(Player player);
}
