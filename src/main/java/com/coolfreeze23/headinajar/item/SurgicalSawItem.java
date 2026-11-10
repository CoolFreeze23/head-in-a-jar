package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Use on a mob below 25% health: its head comes off and it becomes a host body a jar can pilot. With an empty Life
 * Support Jar in your off hand, the saw keeps the head instead (a jarred head) and the body falls.
 */
public class SurgicalSawItem extends Item {
    public static final float MAX_HEALTH_FRACTION = 0.25f;

    public SurgicalSawItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Mob mob)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        return saw((ServerPlayer) player, stack, mob, hand);
    }

    public static InteractionResult saw(ServerPlayer server, ItemStack stack, Mob mob, InteractionHand hand) {
        Player player = server;
        if (!Hosts.canSaw(mob, player)) {
            player.displayClientMessage(Component.translatable("message.headinajar.saw_refused"), true);
            return InteractionResult.FAIL;
        }
        if (mob.getHealth() > mob.getMaxHealth() * MAX_HEALTH_FRACTION) {
            player.displayClientMessage(Component.translatable("message.headinajar.saw_too_healthy"), true);
            return InteractionResult.FAIL;
        }
        ServerLevel level = server.serverLevel();
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.2f, 0.5f);
        com.coolfreeze23.headinajar.feedback.Sanitary.puff(level, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ());
        stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);

        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(ModRegistry.LIFE_SUPPORT_JAR.get())) {
            // keep the head: it goes into the jar, the body falls
            offhand.shrink(1);
            com.coolfreeze23.headinajar.form.FormActions.give(server, JarredHeadItem.of(mob));
            mob.hurt(mob.damageSources().playerAttack(player), Float.MAX_VALUE);
            return InteractionResult.CONSUME;
        }
        HostData data = Hosts.data(mob);
        data.host = true;
        data.freshness = 100;
        data.sync = 0;
        mob.setPersistenceRequired();
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.setHealth(Math.max(mob.getHealth(), mob.getMaxHealth() * MAX_HEALTH_FRACTION));
        Network.syncHost(mob);
        player.displayClientMessage(Component.translatable("message.headinajar.host_made", mob.getDisplayName()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.headinajar.surgical_saw.how").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.headinajar.surgical_saw.how2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
