package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Lets a linked body carry its own head on its hip (a perfect link). Pick the head up by using it with a satchel in
 * your inventory; sneak-use the satchel to set the head down.
 */
public class HeadSatchelItem extends Item {
    public HeadSatchelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var state = FormActions.state(player);
        if (!player.isShiftKeyDown() || state.form() != Form.LINKED || !state.carryingHead()) {
            if (player instanceof ServerPlayer server) {
                String why = state.form() == Form.LINKED ? (state.carryingHead() ? "satchel.sneak" : "satchel.empty")
                        : state.form().isDecapitated() ? "satchel.jar" : "satchel.whole";
                Refusals.deny(server, why, net.minecraft.network.chat.Component.keybind("key.sneak"));
            }
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer server) FormActions.setDownHead(server);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.headinajar.head_satchel.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
