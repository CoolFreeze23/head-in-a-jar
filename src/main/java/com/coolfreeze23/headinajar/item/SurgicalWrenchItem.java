package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.PowerBlockEntities;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Links Tesla Nodes: use the sending node, then the receiving one. Sneak-use a node to unlink it. The wrench acts before
 * the node's own use (which shows its charge to an empty hand), so a plain click with it in hand always reaches it.
 */
public class SurgicalWrenchItem extends Item {
    private static final String FROM = "From", FROM_DIM = "FromDim";

    public SurgicalWrenchItem(Properties properties) {
        super(properties);
    }

    /** Runs before the block is used: a Tesla Node is linked or unlinked; a link waiting for its receiver is answered. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        // a node's upper half is the node
        BlockPos pos = com.coolfreeze23.headinajar.machine.MachineTopBlock.machineAt(level, context.getClickedPos());
        boolean node = level.getBlockEntity(pos) instanceof PowerBlockEntities.TeslaNode;
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        boolean pending = data.contains(FROM);
        if (!node && !(pending && ours(level, pos))) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        if (!node) {
            // a link was waiting for its receiver, and this is not a node
            forget(stack);
            Refusals.deny(player, at(level, pos), "wrench.not_a_node", Component.translatable("block.headinajar.tesla_node"));
            return InteractionResult.FAIL;
        }
        PowerBlockEntities.TeslaNode here = (PowerBlockEntities.TeslaNode) level.getBlockEntity(pos);
        if (player.isShiftKeyDown()) {
            forget(stack);
            if (here.target() == null) return deny(player, here, "wrench.not_linked");
            here.link(null);
            player.displayClientMessage(Component.translatable("message.headinajar.tesla_unlinked"), true);
            com.coolfreeze23.headinajar.body.BodyServer.tool(player, null, com.coolfreeze23.headinajar.fx.Fx.TOOL_WRENCH);
            return InteractionResult.CONSUME;
        }
        if (!pending) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putLong(FROM, pos.asLong());
                tag.putString(FROM_DIM, level.dimension().location().toString());
            });
            player.displayClientMessage(Component.translatable("message.headinajar.tesla_from"), true);
            here.perk();
            com.coolfreeze23.headinajar.body.BodyServer.tool(player, null, com.coolfreeze23.headinajar.fx.Fx.TOOL_WRENCH);
            return InteractionResult.CONSUME;
        }
        var tag = data.getUnsafe();
        BlockPos from = BlockPos.of(tag.getLong(FROM));
        String dim = tag.getString(FROM_DIM);
        forget(stack);
        if (!dim.isEmpty() && !dim.equals(level.dimension().location().toString())) return deny(player, here, "wrench.other_dimension");
        if (from.equals(pos)) return deny(player, here, "wrench.same_node");
        if (!(level.getBlockEntity(from) instanceof PowerBlockEntities.TeslaNode sender)) return deny(player, here, "wrench.sender_gone");
        if (from.distSqr(pos) > PowerBlockEntities.TeslaNode.RANGE * PowerBlockEntities.TeslaNode.RANGE) {
            return deny(player, here, "wrench.too_far", PowerBlockEntities.TeslaNode.RANGE);
        }
        sender.link(pos);
        player.displayClientMessage(Component.translatable("message.headinajar.tesla_linked"), true);
        com.coolfreeze23.headinajar.body.BodyServer.tool(player, null, com.coolfreeze23.headinajar.fx.Fx.TOOL_WRENCH);
        return InteractionResult.CONSUME;
    }

    /** Reached only when the block let the click through (sneaking, or a block with nothing to do): say what it is for. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!ours(level, pos)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (context.getPlayer() instanceof ServerPlayer player) {
            Refusals.deny(player, at(level, pos), "wrench.no_use", Component.translatable("block.headinajar.tesla_node"));
        }
        return InteractionResult.FAIL;
    }

    private static InteractionResult deny(ServerPlayer player, PowerBlockEntities.TeslaNode node, String reason, Object... args) {
        Refusals.deny(player, Refuser.at(node), reason, args);
        return InteractionResult.FAIL;
    }

    private static Refuser at(Level level, BlockPos pos) {
        var block = level.getBlockEntity(pos);
        if (block != null) return Refuser.at(block);
        Vec3 centre = Vec3.atCenterOf(pos);
        return () -> centre;
    }

    /** A block of this mod. */
    private static boolean ours(Level level, BlockPos pos) {
        return HeadInAJar.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getNamespace());
    }

    private static void forget(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(FROM);
            tag.remove(FROM_DIM);
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.headinajar.surgical_wrench.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
