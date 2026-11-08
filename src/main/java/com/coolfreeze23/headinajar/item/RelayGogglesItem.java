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

    /** Quantum Goggles (tier 4) reach anywhere in the dimension. */
    public double range() {
        if (tier >= 4) return 30_000_000;
        return Config.goggleRange(tier);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add((tier >= 4 ? Component.translatable("item.headinajar.quantum_goggles.range")
                : Component.translatable("item.headinajar.relay_goggles.range", (int) range())).withStyle(ChatFormatting.GRAY));
        com.coolfreeze23.headinajar.item.QuantumItems.entangled(stack).ifPresent(id ->
                tooltip.add(Component.translatable("item.headinajar.entangled_shard.id", id.toString().substring(0, 8)).withStyle(ChatFormatting.LIGHT_PURPLE)));
        tooltip.add(Component.translatable("item.headinajar.relay_goggles.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
