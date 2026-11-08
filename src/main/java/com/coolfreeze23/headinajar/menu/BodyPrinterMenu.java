package com.coolfreeze23.headinajar.menu;

import com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity;
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

/** Data: 0 energy/10, 1 saline mB, 2 progress. Button 0 prints. */
public class BodyPrinterMenu extends MachineMenu {
    public static final int[][] SLOTS = {{30, 20}, {48, 20}, {30, 40}, {48, 40}};

    public BodyPrinterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(4), new SimpleContainerData(3), buf.readBlockPos());
    }

    public BodyPrinterMenu(int id, Inventory inventory, IItemHandler items, ContainerData data, BlockPos pos) {
        super(ModRegistry.PRINTER_MENU.get(), id, inventory, items, data, pos, SLOTS);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return id == 0 && player instanceof ServerPlayer server && player.level().getBlockEntity(pos()) instanceof BodyPrinterBlockEntity printer
                && printer.start(server);
    }
}
