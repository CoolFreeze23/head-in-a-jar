package com.coolfreeze23.headinajar.item;

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

/** Hold use for two seconds to unscrew your head into the jar. */
public class LifeSupportJarItem extends Item {
    public static final int USE_TICKS = 40;
    private final int slots;

    public LifeSupportJarItem(int slots, Properties properties) {
        super(properties);
        this.slots = slots;
    }

    public int slots() {
        return slots;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            if (level.isClientSide) player.displayClientMessage(Component.translatable("message.headinajar.sneak_to_detach"), true);
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide && remaining % 8 == 0) {
            entity.playSound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.5f, 0.6f + (USE_TICKS - remaining) * 0.02f);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && FormActions.detach(player, slots)) {
            stack.consume(1, player);
        }
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
        tooltip.add(Component.translatable("item.headinajar.life_support_jar.slots", slots).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.headinajar.life_support_jar.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
