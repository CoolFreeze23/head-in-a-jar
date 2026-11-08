package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.block.PowerBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/** Links Tesla Nodes: use the sending node, then the receiving one. Sneak-use a node to unlink it. */
public class SurgicalWrenchItem extends Item {
    public SurgicalWrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (!(level.getBlockEntity(pos) instanceof PowerBlockEntities.TeslaNode node)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (player != null && player.isShiftKeyDown()) {
            node.link(null);
            player.displayClientMessage(Component.translatable("message.headinajar.tesla_unlinked"), true);
            return InteractionResult.CONSUME;
        }
        if (data.contains("From")) {
            BlockPos from = BlockPos.of(data.copyTag().getLong("From"));
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove("From"));
            if (from.equals(pos) || !(level.getBlockEntity(from) instanceof PowerBlockEntities.TeslaNode sender)) return InteractionResult.FAIL;
            if (from.distSqr(pos) > PowerBlockEntities.TeslaNode.RANGE * PowerBlockEntities.TeslaNode.RANGE) {
                if (player != null) player.displayClientMessage(Component.translatable("message.headinajar.tesla_too_far"), true);
                return InteractionResult.FAIL;
            }
            sender.link(pos);
            if (player != null) player.displayClientMessage(Component.translatable("message.headinajar.tesla_linked"), true);
        } else {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong("From", pos.asLong()));
            if (player != null) player.displayClientMessage(Component.translatable("message.headinajar.tesla_from"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.headinajar.surgical_wrench.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
