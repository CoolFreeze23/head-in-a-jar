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
    public static final int INV_Y = 102;
    public static final int[][] SLOTS = slots();

    /** Frame and plating, then the module slots: rows of six from (44, 18). */
    private static int[][] slots() {
        int n = com.coolfreeze23.headinajar.mecha.MechaFrame.maxSlots();
        int[][] out = new int[2 + n][];
        out[0] = new int[]{22, 18};
        out[1] = new int[]{22, 54};
        for (int i = 0; i < n; i++) out[2 + i] = new int[]{44 + (i % 6) * 18, 18 + (i / 6) * 18};
        return out;
    }

    public AssemblyGantryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(SLOTS.length), new SimpleContainerData(2), buf.readBlockPos());
    }

    public AssemblyGantryMenu(int id, Inventory inventory, IItemHandler items, ContainerData data, BlockPos pos) {
        super(ModRegistry.GANTRY_MENU.get(), id, inventory, items, data, pos, SLOTS, INV_Y);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return player instanceof ServerPlayer server && player.level().getBlockEntity(pos()) instanceof AssemblyGantryBlockEntity gantry
                && gantry.button(server, id);
    }
}
