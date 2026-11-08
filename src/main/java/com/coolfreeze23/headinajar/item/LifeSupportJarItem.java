package com.coolfreeze23.headinajar.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * An empty life-support jar. The Separation Chair puts your head in it. A compact jar is a head's size: the head inside
 * faces the front and the whole jar turns with it; the others are wide enough for the head to turn right round.
 */
public class LifeSupportJarItem extends Item {
    private final int slots;
    private final boolean compact;

    public LifeSupportJarItem(int slots, Properties properties) {
        this(slots, false, properties);
    }

    public LifeSupportJarItem(int slots, boolean compact, Properties properties) {
        super(properties);
        this.slots = slots;
        this.compact = compact;
    }

    public int slots() {
        return slots;
    }

    public boolean compact() {
        return compact;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.headinajar.life_support_jar.slots", slots).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(compact ? "item.headinajar.life_support_jar.compact" : "item.headinajar.life_support_jar.wide")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.headinajar.life_support_jar.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
