package com.coolfreeze23.headinajar.block;

import net.neoforged.neoforge.items.ItemStackHandler;

/** A block entity whose items drop when its block is broken. */
public interface HasItems {
    ItemStackHandler items();

    /** Items the machine has taken in but not yet turned into anything; they drop too when it is broken. */
    default java.util.List<net.minecraft.world.item.ItemStack> consumed() {
        return java.util.List.of();
    }
}
