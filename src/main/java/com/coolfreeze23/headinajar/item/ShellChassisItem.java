package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.entity.ShellEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;
import java.util.function.Supplier;

/** A folded Utility Shell: use it on the ground to set it up. A jar climbs in by using it. */
public class ShellChassisItem extends Item {
    private final Supplier<EntityType<ShellEntity>> type;

    public ShellChassisItem(Supplier<EntityType<ShellEntity>> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        ShellEntity shell = type.get().spawn(level, context.getItemInHand(), context.getPlayer(), pos, MobSpawnType.SPAWN_EGG, true, false);
        if (shell == null) return InteractionResult.FAIL;
        if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer placer) com.coolfreeze23.headinajar.staff.Staff.register(shell, placer, "shell", true);
        if (context.getPlayer() != null) shell.setYRot(context.getPlayer().getYRot() + 180);
        // the folded chassis stands up: its servos settle (heard by everyone near, the one who placed it too)
        var settle = com.coolfreeze23.headinajar.mecha.HopperSounds.get("servo_settle");
        if (settle != null) {
            level.playSound(null, shell.getX(), shell.getY(), shell.getZ(), settle, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f,
                    com.coolfreeze23.headinajar.mecha.HopperSounds.pitch("servo_settle", 1, level.random));
        }
        context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.headinajar.shell.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
