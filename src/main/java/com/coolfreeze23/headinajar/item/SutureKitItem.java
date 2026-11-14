package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Sews the head back on. A linked body carrying its own head holds use for 3 seconds; a jar uses the kit on its
 * own body or corpse.
 */
public class SutureKitItem extends Item {
    public static final int USE_TICKS = 60;

    public SutureKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var state = FormActions.state(player);
        if (state.form() != Form.LINKED || !state.carryingHead()) {
            if (level.isClientSide) player.displayClientMessage(Component.translatable(state.form() == Form.BODY
                    ? "message.headinajar.clarity.suture_head_on" : "message.headinajar.suture_how"), true);
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        // one stitch per loop of the needle hand
        if (level.isClientSide && remaining % 10 == 0) entity.playSound(com.coolfreeze23.headinajar.fx.BodySounds.SNICK.get(), 1.0f, 0.94f + level.random.nextFloat() * 0.12f);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && FormActions.suture(player, null)) stack.consume(1, player);
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BRUSH;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.headinajar.suture_kit.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
