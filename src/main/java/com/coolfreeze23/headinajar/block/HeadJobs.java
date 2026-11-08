package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** What jarred heads do for a living: which head is which, what a head earns in a Cerebral Array, and villager staff. */
public final class HeadJobs {
    private HeadJobs() {
    }

    public static EntityType<?> type(ItemStack stack) {
        return stack.is(ModRegistry.JARRED_HEAD.get()) ? JarredHeadItem.type(stack) : null;
    }

    public static CompoundTag entityTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag().getCompound("Entity");
    }

    /** A jarred villager's profession ("librarian", "nitwit", ...), or "" for anything else. */
    public static String profession(ItemStack stack) {
        if (type(stack) != EntityType.VILLAGER) return "";
        String id = entityTag(stack).getCompound("VillagerData").getString("profession");
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    /** FE/t a head makes in a Cerebral Array by thinking very hard about broth. The Dragon Head needs a Board Room Core. */
    public static int arrayOutput(ItemStack stack, boolean boardRoom) {
        if (stack.is(Items.DRAGON_HEAD)) return boardRoom ? 1000 : 0;
        EntityType<?> type = type(stack);
        if (type == null) return 0;
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) return 20;
        if (type == EntityType.VILLAGER || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.WANDERING_TRADER) return 40;
        if (type == EntityType.WITCH || type == EntityType.PIGLIN || type == EntityType.PIGLIN_BRUTE) return 60;
        if (type == EntityType.ENDERMAN) return 80;
        if (type == EntityType.WITHER_SKELETON) return 100;
        if (type == EntityType.EVOKER) return 120;
        return 10;
    }

    // --- villager staff -----------------------------------------------------------------------------------

    /**
     * How well staffed a machine is for a profession: 1 when an Employee Mount on one of its sides holds a paid villager of
     * that profession, 0.5 when unpaid, 0 when nobody (or a nitwit) is there.
     */
    public static float staff(Level level, BlockPos machine, String... professions) {
        float best = 0;
        for (Direction d : Direction.values()) {
            if (!(level.getBlockEntity(machine.relative(d)) instanceof EmployeeMountBlockEntity mount) || !mount.facesInto(machine)) continue;
            String job = profession(mount.head());
            for (String p : professions) if (p.equals(job)) best = Math.max(best, mount.paid() ? 1f : 0.5f);
        }
        return best;
    }
}
