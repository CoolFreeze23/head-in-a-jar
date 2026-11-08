package com.coolfreeze23.headinajar.menu;

import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Gantry: frame, plating, then 11 module slots (only those the frame has are used). Data: 0 energy/10, 1 selected slot. */
public class AssemblyGantryMenu extends MachineMenu {
    public static final int ASSEMBLE = 1, DISASSEMBLE = 2, REPAIR = 3, PREV = 4, NEXT = 5, MINUS = 6, PLUS = 7;
    public static final int[][] SLOTS = {
            {22, 18}, {22, 54},
            {44, 18}, {62, 18}, {80, 18}, {98, 18}, {116, 18}, {134, 18},
            {44, 36}, {62, 36}, {80, 36}, {98, 36}, {116, 36}};

    public AssemblyGantryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(SLOTS.length), new SimpleContainerData(2), buf.readBlockPos());
    }

    public AssemblyGantryMenu(int id, Inventory inventory, IItemHandler items, ContainerData data, BlockPos pos) {
        super(ModRegistry.GANTRY_MENU.get(), id, inventory, items, data, pos, SLOTS);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return player instanceof ServerPlayer server && player.level().getBlockEntity(pos()) instanceof AssemblyGantryBlockEntity gantry
                && gantry.button(server, id);
    }
}
