package com.coolfreeze23.headinajar.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/** Bolted onto the neck: the body's camera and motor receiver. A botched cut leaves it crooked (-10% range). */
public class CollarCamItem extends Item {
    public CollarCamItem(Properties properties) {
        super(properties);
    }

    public static boolean isCrooked(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("Crooked");
    }

    public static void setCrooked(ItemStack stack, boolean crooked) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (crooked) tag.putBoolean("Crooked", true);
            else tag.remove("Crooked");
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.headinajar.collar_cam.how").withStyle(ChatFormatting.DARK_GRAY));
        if (isCrooked(stack)) tooltip.add(Component.translatable("item.headinajar.collar_cam.crooked").withStyle(ChatFormatting.RED));
    }
}
