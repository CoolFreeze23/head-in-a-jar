package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.mecha.MechaModule;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/** A mecha module, fitted at the Assembly Gantry. Its slider setting (0-100%) is kept on the item. */
public class MechaModuleItem extends Item {
    private final MechaModule type;

    public MechaModuleItem(MechaModule type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public MechaModule type() {
        return type;
    }

    public static int slider(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || !data.contains("Slider") ? 50 : data.copyTag().getInt("Slider");
    }

    public static void setSlider(ItemStack stack, int value) {
        int v = Math.max(0, Math.min(100, value));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("Slider", v));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("mecha_module.headinajar." + type.name().toLowerCase(java.util.Locale.ROOT) + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("mecha_module.headinajar.stats", Component.translatable("mecha_slot.headinajar." + type.slot.name().toLowerCase(java.util.Locale.ROOT)),
                type.mass).withStyle(ChatFormatting.DARK_GRAY));
        if (type.slider != null) {
            tooltip.add(Component.translatable("mecha_module.headinajar.slider", Component.translatable("mecha_slider.headinajar." + type.slider), slider(stack))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
