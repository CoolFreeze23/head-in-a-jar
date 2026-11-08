package com.coolfreeze23.headinajar.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** An empty life-support jar. The Separation Chair puts your head in it. */
public class LifeSupportJarItem extends Item {
    private final int slots;

    public LifeSupportJarItem(int slots, Properties properties) {
        super(properties);
        this.slots = slots;
    }

    public int slots() {
        return slots;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.headinajar.life_support_jar.slots", slots).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.headinajar.life_support_jar.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
