package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Worn by the head: receives the Collar Cam's picture and sends the head's signals back to the body. */
public class RelayGogglesItem extends Item {
    private final int tier;

    public RelayGogglesItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    public double range() {
        return Config.goggleRange(tier);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.headinajar.relay_goggles.range", (int) range()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.headinajar.relay_goggles.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
