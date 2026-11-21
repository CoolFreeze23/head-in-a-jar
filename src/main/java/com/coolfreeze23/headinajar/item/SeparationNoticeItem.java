package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Form 45-S, the Notice of Separation: the paper a worker's employer gets back with the lanyard on Let go. A souvenir;
 * read by hovering (who it was, the day).
 */
public class SeparationNoticeItem extends Item {
    public SeparationNoticeItem(Properties properties) {
        super(properties);
    }

    /** A notice for a worker let go today. */
    public static ItemStack of(Mob mob) {
        ItemStack stack = new ItemStack(WorkRegistry.SEPARATION_NOTICE.get());
        StaffFile f = Staff.of(mob);
        CompoundTag tag = new CompoundTag();
        if (f != null) {
            tag.putInt("Number", f.number);
            tag.putString("Species", f.species);
            if (f.customName != null) tag.putString("CustomName", f.customName);
            else tag.putInt("NameIndex", f.nameIndex);
        }
        tag.putLong("Day", Staff.day(mob.level()));
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.headinajar.separation_notice.text").withStyle(ChatFormatting.GRAY));
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("Number")) return;
        CompoundTag tag = data.getUnsafe();
        StaffFile f = new StaffFile();
        f.number = tag.getInt("Number");
        f.species = tag.getString("Species");
        f.customName = tag.contains("CustomName") ? tag.getString("CustomName") : null;
        f.nameIndex = tag.contains("NameIndex") ? tag.getInt("NameIndex") : 0;
        tooltip.add(Component.translatable("item.headinajar.separation_notice.record", Staff.number(f.number), Staff.name(f), Staff.species(f),
                Staff.shown(tag.getLong("Day"))).withStyle(ChatFormatting.DARK_GRAY));
    }
}
