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
public record HeadInfo(EntityType<?> type, String profession, CompoundTag villagerData, String jar, int size, boolean promoted) {
    public static final HeadInfo NONE = new HeadInfo(null, "", null, HeadInfo.LIFE_SUPPORT, 0, false);

    /** The jar a head went into, as recorded on it ({@code "Jar"}); none recorded is a Life Support Jar. */
    public static final String LIFE_SUPPORT = "headinajar:life_support_jar", COMPACT = "headinajar:compact_life_support_jar";

    /** Whether this head is in the Compact Jar (drawn in its own shape; the other three jars are drawn alike). */
    public boolean compact() {
        return COMPACT.equals(jar);
    }

    /** The recorded jar of a jarred head's data, or the Life Support Jar when none is recorded (heads cut before 0.6.0). */
    public static String jar(CompoundTag tag) {
        return tag != null && tag.contains("Jar") ? tag.getString("Jar") : LIFE_SUPPORT;
    }

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
        // a slime's or magma cube's size as drawn (its record saves one less), 0 for anything else
        CompoundTag stored = tag.getCompound("Entity");
        int size = (type == EntityType.SLIME || type == EntityType.MAGMA_CUBE) && stored.contains("Size") ? stored.getInt("Size") + 1 : 0;
        // promoted to the Board (0.7.2): read from its file once per stack; never sent to clients (the slots' sync tag is trimmed)
        com.coolfreeze23.headinajar.staff.StaffFile file = com.coolfreeze23.headinajar.staff.Staff.fromHeadTag(tag);
        boolean promoted = file != null && file.career.promotedDay >= 0;
        return new HeadInfo(type, profession, villager, jar(tag), size, promoted);
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
        if (tag.contains("Jar")) kept.putString("Jar", tag.getString("Jar"));
        CompoundTag entity = new CompoundTag();
        CompoundTag stored = tag.getCompound("Entity");
        if (stored.contains("VillagerData")) entity.put("VillagerData", stored.getCompound("VillagerData").copy());
        if (stored.contains("Size")) entity.putInt("Size", stored.getInt("Size"));
        kept.put("Entity", entity);
        CustomData.set(DataComponents.CUSTOM_DATA, out, kept);
        return out;
    }
}
