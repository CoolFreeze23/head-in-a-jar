package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * HR's card, dropped where a member of staff left the company. Never crafted; read by hovering. It carries the leaver's
 * number, name and species and the day it left, so each reader sees the name in their own language's pool.
 */
public class CondolenceCardItem extends Item {
    public CondolenceCardItem(Properties properties) {
        super(properties);
    }

    /** A card for the file of one who left on {@code day}. */
    public static ItemStack of(StaffFile file, long day) {
        ItemStack stack = new ItemStack(ModRegistry.CONDOLENCE_CARD.get());
        CompoundTag tag = new CompoundTag();
        tag.putInt("Number", file.number);
        tag.putString("Species", file.species);
        if (file.customName != null) tag.putString("CustomName", file.customName);
        else tag.putInt("NameIndex", file.nameIndex);
        tag.putLong("Day", day);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag tag = data.getUnsafe();
        StaffFile f = new StaffFile();
        f.number = tag.getInt("Number");
        f.species = tag.getString("Species");
        f.customName = tag.contains("CustomName") ? tag.getString("CustomName") : null;
        f.nameIndex = tag.contains("NameIndex") ? tag.getInt("NameIndex") : 0;
        tooltip.add(Component.translatable("item.headinajar.condolence_card.sorry", Staff.name(f)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.headinajar.condolence_card.record", Staff.number(f.number), Staff.species(f),
                Staff.shown(tag.getLong("Day"))).withStyle(ChatFormatting.DARK_GRAY));
    }
}
