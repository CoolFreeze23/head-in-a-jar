package com.coolfreeze23.headinajar.block;

import net.neoforged.neoforge.items.ItemStackHandler;

/** A block entity whose items drop when its block is broken. */
public interface HasItems {
    ItemStackHandler items();
}
