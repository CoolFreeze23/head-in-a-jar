package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * What a jarred head in a slot is: its creature, a villager's profession and the villager data a renderer needs. Read
 * once from the item, without copying the stored mob, and kept until the slot holds a different stack.
 */
public record HeadInfo(EntityType<?> type, String profession, CompoundTag villagerData) {
    public static final HeadInfo NONE = new HeadInfo(null, "", null);

    /** The stored tag, read-only (never copied: a traded villager's record is several KB). */
    static CompoundTag data(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.getUnsafe();
    }

    public static HeadInfo of(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ModRegistry.JARRED_HEAD.get())) return NONE;
        CompoundTag tag = data(stack);
        if (tag == null) return NONE;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Type"));
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) return NONE;
        CompoundTag villager = tag.getCompound("Entity").contains("VillagerData") ? tag.getCompound("Entity").getCompound("VillagerData") : null;
        String profession = "";
        if (type == EntityType.VILLAGER && villager != null) {
            String job = villager.getString("profession");
            int colon = job.indexOf(':');
            profession = colon < 0 ? job : job.substring(colon + 1);
        }
        return new HeadInfo(type, profession, villager);
    }

    /** A one-slot cache: worked out again only when the slot holds a different stack. */
    public static final class Cache {
        private ItemStack seen;
        private HeadInfo info = NONE;

        public HeadInfo get(ItemStack stack) {
            if (stack != seen) {
                seen = stack;
                info = of(stack);
            }
            return info;
        }
    }

    /** One cache per slot. */
    public static Cache[] caches(int slots) {
        Cache[] out = new Cache[slots];
        for (int i = 0; i < slots; i++) out[i] = new Cache();
        return out;
    }

    /**
     * The slots as clients see them: a jarred head is sent as its creature and villager data only, not the whole stored
     * mob (clients only draw it). Other items go as they are.
     */
    public static CompoundTag syncTag(ItemStackHandler items, HolderLookup.Provider registries) {
        ItemStackHandler shown = new ItemStackHandler(items.getSlots());
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            shown.setStackInSlot(i, stack.is(ModRegistry.JARRED_HEAD.get()) ? trimmed(stack) : stack.copy());
        }
        return shown.serializeNBT(registries);
    }

    private static ItemStack trimmed(ItemStack head) {
        CompoundTag tag = data(head);
        ItemStack out = new ItemStack(ModRegistry.JARRED_HEAD.get(), head.getCount());
        if (tag == null) return out;
        CompoundTag kept = new CompoundTag();
        kept.putString("Type", tag.getString("Type"));
        CompoundTag entity = new CompoundTag();
        CompoundTag stored = tag.getCompound("Entity");
        if (stored.contains("VillagerData")) entity.put("VillagerData", stored.getCompound("VillagerData").copy());
        kept.put("Entity", entity);
        CustomData.set(DataComponents.CUSTOM_DATA, out, kept);
        return out;
    }
}
