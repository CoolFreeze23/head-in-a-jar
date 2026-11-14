package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.module.ModuleType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A module that fits in a life-support jar from the Jar menu. */
public class ModuleItem extends Item {
    private final ModuleType type;

    public ModuleItem(ModuleType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public ModuleType type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("module.headinajar." + type.id() + ".desc").withStyle(ChatFormatting.GRAY));
        if (type.powered) tooltip.add(Component.translatable(NEEDS_ONLY.contains(type) ? "module.headinajar.needs_charge" : "module.headinajar.powered")
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    /** Powered modules that stop when the jar is flat but spend nothing of their own while they work. */
    public static final java.util.Set<ModuleType> NEEDS_ONLY = java.util.EnumSet.of(ModuleType.SUCTION_CUPS, ModuleType.SPIDER_LEGS,
            ModuleType.HUB_ANTENNA, ModuleType.SALINE_RECYCLER, ModuleType.CORTEX_COPROCESSOR);
}
