package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A mob's head in a Life Support Jar, with everything the mob was (a villager keeps its trades). Place it on any
 * surface; a jarred villager still trades. Later machines use mob heads as turret brains and power.
 */
public class JarredHeadItem extends Item {
    public JarredHeadItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Mob mob) {
        ItemStack stack = new ItemStack(ModRegistry.JARRED_HEAD.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        CompoundTag entity = new CompoundTag();
        mob.saveWithoutId(entity);
        entity.remove("Passengers");
        tag.put("Entity", entity);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    public static EntityType<?> type(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(data.copyTag().getString("Type"));
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    @Override
    public Component getName(ItemStack stack) {
        EntityType<?> type = type(stack);
        return type == null ? super.getName(stack) : Component.translatable("item.headinajar.jarred_head.of", type.getDescription());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(pos).canBeReplaced()) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockState state = ModRegistry.JARRED_HEAD_BLOCK.get().defaultBlockState()
                .setValue(com.coolfreeze23.headinajar.block.JarredHeadBlock.FACING, context.getHorizontalDirection().getOpposite());
        level.setBlockAndUpdate(pos, state);
        if (level.getBlockEntity(pos) instanceof JarredHeadBlockEntity head) {
            CustomData data = context.getItemInHand().get(DataComponents.CUSTOM_DATA);
            if (data != null) head.load(data.copyTag());
        }
        context.getItemInHand().shrink(1);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.GLASS_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (type(stack) == EntityType.VILLAGER) tooltip.add(Component.translatable("item.headinajar.jarred_head.trades").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("item.headinajar.jarred_head.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
