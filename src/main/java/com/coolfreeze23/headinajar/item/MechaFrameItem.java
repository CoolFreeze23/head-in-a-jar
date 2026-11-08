package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A mecha chassis, assembled into a mecha at the Assembly Gantry. */
public class MechaFrameItem extends Item {
    private final MechaFrame frame;

    public MechaFrameItem(MechaFrame frame, Properties properties) {
        super(properties);
        this.frame = frame;
    }

    public MechaFrame frame() {
        return frame;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("mecha_frame.headinajar.stats", frame.health, frame.armor, frame.load, frame.battery / 1000).withStyle(ChatFormatting.GRAY));
        StringBuilder slots = new StringBuilder();
        for (MechaModule.Slot s : MechaModule.Slot.values()) {
            if (frame.slots(s) == 0) continue;
            if (!slots.isEmpty()) slots.append(", ");
            slots.append(frame.slots(s)).append(' ').append(Component.translatable("mecha_slot.headinajar." + s.name().toLowerCase(java.util.Locale.ROOT)).getString());
        }
        tooltip.add(Component.literal(slots.toString()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
