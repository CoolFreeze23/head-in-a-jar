package com.coolfreeze23.headinajar.menu;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Data: 0 energy/10, 1 water mB, 2 saline mB, 3 progress. */
public class SalineMixerMenu extends MachineMenu {
    public static final int[][] SLOTS = {{30, 17}, {30, 37}, {30, 57}, {124, 17}, {124, 57}};

    public SalineMixerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(5), new SimpleContainerData(4), buf.readBlockPos());
    }

    public SalineMixerMenu(int id, Inventory inventory, IItemHandler items, ContainerData data, BlockPos pos) {
        super(ModRegistry.MIXER_MENU.get(), id, inventory, items, data, pos, SLOTS);
    }
}
