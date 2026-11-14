package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.block.GalvanicPileBlockEntity;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * The facts a player needs at the moment they hold the block: what the Galvanic Pile burns and how much an operation
 * takes, that the Assembly Gantry is itself the pad a mecha stands on, how the Charging Dock and the Cradle are fed, and
 * how an Employee Mount is hung on a machine.
 */
public final class BlockTooltips {
    private BlockTooltips() {
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> tip = event.getToolTip();
        if (stack.is(ModRegistry.GALVANIC_PILE_ITEM.get())) {
            int flesh = GalvanicPileBlockEntity.fuelValue(new ItemStack(Items.ROTTEN_FLESH));
            int op = SeparationChairBlockEntity.energyCost();
            add(tip, Component.translatable("tooltip.headinajar.pile.what"));
            add(tip, Component.translatable("tooltip.headinajar.pile.fuels", Items.ROTTEN_FLESH.getDescription(), flesh,
                    Items.SPIDER_EYE.getDescription(), GalvanicPileBlockEntity.fuelValue(new ItemStack(Items.SPIDER_EYE)),
                    GalvanicPileBlockEntity.fuelValue(new ItemStack(Items.BEEF))));
            add(tip, Component.translatable("tooltip.headinajar.pile.operation", Component.translatable("block.headinajar.separation_chair"),
                    op, (op + flesh - 1) / flesh, Items.ROTTEN_FLESH.getDescription()));
        } else if (stack.is(ModRegistry.ASSEMBLY_GANTRY_ITEM.get())) {
            add(tip, Component.translatable("tooltip.headinajar.gantry.pad"));
            add(tip, Component.translatable("tooltip.headinajar.gantry.cost", AssemblyGantryBlockEntity.ASSEMBLE_COST));
        } else if (stack.is(ModRegistry.CHARGING_DOCK_ITEM.get())) {
            add(tip, Component.translatable("tooltip.headinajar.dock.how", Component.translatable("item.headinajar.head_satchel")));
            add(tip, Component.translatable("tooltip.headinajar.fed_by", Component.translatable("block.headinajar.galvanic_pile")));
        } else if (stack.is(ModRegistry.CRADLE_ITEM.get())) {
            add(tip, Component.translatable("tooltip.headinajar.cradle.how", Component.translatable("item.headinajar.saline_bottle")));
            add(tip, Component.translatable("tooltip.headinajar.fed_by", Component.translatable("block.headinajar.galvanic_pile")));
        } else if (stack.is(ModRegistry.EMPLOYEE_MOUNT_ITEM.get())) {
            // without sneak, a use on a machine with a screen opens the machine (as with any block placed against a chest)
            add(tip, Component.translatable("tooltip.headinajar.mount.how", Component.keybind("key.sneak")));
            add(tip, Component.translatable("tooltip.headinajar.mount.staff"));
        }
    }

    private static void add(List<Component> tip, Component line) {
        // after the name, before anything a mod or the advanced view appends
        tip.add(Math.min(1 + (int) tip.stream().skip(1).filter(BlockTooltips::ours).count(), tip.size()), line.copy().withStyle(ChatFormatting.GRAY));
    }

    private static boolean ours(Component line) {
        return line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().startsWith("tooltip.headinajar.");
    }
}
