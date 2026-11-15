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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Data: 0 energy/10, 1 saline mB, 2 progress, 3 stall reason; then what the Template slot would print: 4 the species'
 * raw id (-1 none), 5 its class (0 none, 1 tiny, 2 small, 3 medium), 6 the template's quality, 7 specimen printing not
 * licensed here (1), 8 the catalogue's refusal ({@code PrintCatalogue.REASONS}, 0 none). Button 0 prints.
 */
public class BodyPrinterMenu extends MachineMenu {
    public static final int[][] SLOTS = {{30, 20}, {48, 20}, {30, 40}, {48, 40}, {96, 42}};
    public static final int SPECIES = 4, SIZE = 5, QUALITY = 6, LOCKED = 7, REASON = 8, DATA = 9;

    public BodyPrinterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainerData(DATA), buf.readBlockPos());
    }

    private BodyPrinterMenu(int id, Inventory inventory, ContainerData data, BlockPos pos) {
        // the client's copy of the slots takes what the printer's do, so a refused click is not shown going in
        this(id, inventory, new ItemStackHandler(5) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return switch (slot) {
                    case 0 -> stack.is(net.minecraft.world.item.Items.ROTTEN_FLESH);
                    case 1 -> stack.is(net.minecraft.world.item.Items.BONE_BLOCK);
                    case 2 -> stack.is(net.minecraft.world.item.Items.REDSTONE_BLOCK);
                    case 3 -> stack.is(net.minecraft.world.item.Items.SLIME_BALL);
                    case BodyPrinterBlockEntity.TEMPLATE -> BodyPrinterBlockEntity.takesTemplate(stack, data.get(LOCKED) == 0);
                    default -> false;
                };
            }

            @Override
            public int getSlotLimit(int slot) {
                return slot == BodyPrinterBlockEntity.TEMPLATE ? 1 : super.getSlotLimit(slot);
            }
        }, data, pos);
    }

    public BodyPrinterMenu(int id, Inventory inventory, IItemHandler items, ContainerData data, BlockPos pos) {
        super(ModRegistry.PRINTER_MENU.get(), id, inventory, items, data, pos, SLOTS);
    }

    @Override
    public int statusIndex() {
        return 3;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return id == 0 && player instanceof ServerPlayer server && player.level().getBlockEntity(pos()) instanceof BodyPrinterBlockEntity printer
                && printer.start(server);
    }
}
