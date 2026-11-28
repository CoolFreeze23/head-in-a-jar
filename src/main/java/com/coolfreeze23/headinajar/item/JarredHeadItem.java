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

    /** A head with no jar recorded (as heads cut before 0.6.0 are: it shows as a Life Support Jar). */
    public static ItemStack of(Mob mob) {
        return of(mob, ItemStack.EMPTY);
    }

    /**
     * The creature's head in {@code jar} (recorded as {@code "Jar"}). Any file the creature has moves to the head's own
     * {@code "Staff"}, out of the stored mob, so there is one copy.
     */
    public static ItemStack of(Mob mob, ItemStack jar) {
        ItemStack stack = new ItemStack(ModRegistry.JARRED_HEAD.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        CompoundTag entity = new CompoundTag();
        mob.saveWithoutId(entity);
        entity.remove("Passengers");
        com.coolfreeze23.headinajar.staff.Staff.stripEntity(entity);
        tag.put("Entity", entity);
        com.coolfreeze23.headinajar.supplier.Records.stamp(tag, "Entity");
        if (!jar.isEmpty()) tag.putString("Jar", BuiltInRegistries.ITEM.getKey(jar.getItem()).toString());
        var file = com.coolfreeze23.headinajar.staff.Staff.of(mob);
        if (file != null) tag.put(com.coolfreeze23.headinajar.staff.Staff.ON_HEAD, file.save());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    /** The jar recorded on a jarred head item (a Life Support Jar when none is). */
    public static String jar(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return com.coolfreeze23.headinajar.block.HeadInfo.jar(data == null ? null : data.getUnsafe());
    }

    /** The creature id the head was recorded as ("" for none), registered or not. */
    public static String typeId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.getUnsafe().getString("Type");
    }

    /** A head of a creature this game has never heard of: its record is kept, untouched, until the creature is back. */
    public static boolean discontinued(ItemStack stack) {
        return com.coolfreeze23.headinajar.supplier.Records.discontinued(typeId(stack));
    }

    public static EntityType<?> type(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        // read in place: the stored mob is never copied just to learn what it was
        ResourceLocation id = ResourceLocation.tryParse(data.getUnsafe().getString("Type"));
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    @Override
    public Component getName(ItemStack stack) {
        EntityType<?> type = type(stack);
        if (type == null && discontinued(stack)) {
            return Component.translatable("item.headinajar.jarred_head.of", com.coolfreeze23.headinajar.supplier.Records.speciesName(typeId(stack)));
        }
        return type == null ? super.getName(stack) : Component.translatable("item.headinajar.jarred_head.of", type.getDescription());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(pos).canBeReplaced()) return InteractionResult.FAIL;
        // a discontinued model is not seated: the item keeps its record until its creature is registered again
        if (discontinued(context.getItemInHand())) {
            if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer placer) {
                com.coolfreeze23.headinajar.feedback.Refusals.deny(placer, "head.discontinued");
            }
            return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        // the head looks the opposite way to the block's facing (the plinth's front and the badge with it): facing the
        // way the placer looks, it looks back at them
        BlockState state = ModRegistry.JARRED_HEAD_BLOCK.get().defaultBlockState()
                .setValue(com.coolfreeze23.headinajar.block.JarredHeadBlock.FACING, context.getHorizontalDirection());
        // placed as any block is, so claim and protection mods can say no
        var snapshot = net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, pos);
        level.setBlockAndUpdate(pos, state);
        if (net.neoforged.neoforge.event.EventHooks.onBlockPlace(context.getPlayer(), snapshot, context.getClickedFace())) {
            snapshot.restore(3);
            return InteractionResult.FAIL;
        }
        if (level.getBlockEntity(pos) instanceof JarredHeadBlockEntity head) {
            CustomData data = context.getItemInHand().get(DataComponents.CUSTOM_DATA);
            if (data != null) head.load(data.copyTag());
            // on file from here: numbered now if it never was, and the placer is its employer if nobody is
            if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer placer) com.coolfreeze23.headinajar.staff.StaffBlocks.placed(head, placer);
        }
        context.getItemInHand().shrink(1);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.GLASS_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // its title, in gold under the name, where the reader's language has one for it
        EntityType<?> type = type(stack);
        if (type != null) {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            String title = com.coolfreeze23.headinajar.block.HeadSpeak.titleKey(type, data == null ? new CompoundTag() : data.getUnsafe().getCompound("Entity"));
            if (net.minecraft.locale.Language.getInstance().has(title)) {
                tooltip.add(Component.translatable("item.headinajar.jarred_head.title", Component.translatable(title)).withStyle(ChatFormatting.GOLD));
            }
        }
        if (type == null && discontinued(stack)) {
            tooltip.add(Component.translatable("item.headinajar.jarred_head.discontinued",
                    com.coolfreeze23.headinajar.supplier.Records.speciesName(typeId(stack))).withStyle(ChatFormatting.RED));
        }
        if (type == EntityType.VILLAGER) tooltip.add(Component.translatable("item.headinajar.jarred_head.trades").withStyle(ChatFormatting.GREEN));
        var jar = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(jar(stack)));
        jar.ifPresent(item -> tooltip.add(Component.translatable("item.headinajar.jarred_head.jar", item.getDescription()).withStyle(ChatFormatting.GRAY)));
        var file = com.coolfreeze23.headinajar.staff.Staff.of(stack);
        if (file != null) tooltip.add(Component.translatable("item.headinajar.jarred_head.staff", com.coolfreeze23.headinajar.staff.Staff.number(file.number),
                com.coolfreeze23.headinajar.staff.Staff.departmentName(com.coolfreeze23.headinajar.staff.Staff.department(type(stack)))).withStyle(ChatFormatting.GRAY));
        // a discontinued model is not seated, so it is not told to be
        if (!(type == null && discontinued(stack))) tooltip.add(Component.translatable("item.headinajar.jarred_head.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
