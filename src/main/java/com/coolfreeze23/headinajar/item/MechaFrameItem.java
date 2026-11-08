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

    /** Genes saved on a frame by the Gantry when a Chimera is taken apart. */
    public static java.util.Optional<com.coolfreeze23.headinajar.genetics.GeneSet> genes(ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("Genes")) return java.util.Optional.empty();
        return com.coolfreeze23.headinajar.genetics.GeneSet.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, data.copyTag().get("Genes")).result();
    }

    public static void setGenes(ItemStack stack, com.coolfreeze23.headinajar.genetics.GeneSet genes) {
        if (genes.isEmpty()) return;
        com.coolfreeze23.headinajar.genetics.GeneSet.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, genes).result().ifPresent(tag ->
                net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, t -> t.put("Genes", tag)));
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
        if (frame.genePorts() > 0) tooltip.add(Component.translatable("mecha_frame.headinajar.gene_ports", frame.genePorts()).withStyle(ChatFormatting.LIGHT_PURPLE));
        genes(stack).ifPresent(g -> tooltip.add(Component.translatable("mecha_frame.headinajar.genes_kept", g.serums.size()).withStyle(ChatFormatting.AQUA)));
    }
}
