package com.coolfreeze23.headinajar.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The Secondment Collar: a brass ring with a 500 mB broth canister. Used on a host (with hands) it clicks round the
 * neck; a Jarred Head used on the collared host seats on it and the head's mind drives the body. The uses themselves
 * are {@code staff.second.Secondment}'s (a host's instruments, and the refusal on anything else).
 */
public class SecondmentCollarItem extends Item {
    public SecondmentCollarItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.headinajar.secondment_collar.how").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.headinajar.secondment_collar.note").withStyle(ChatFormatting.DARK_GRAY));
    }
}
