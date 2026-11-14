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
    /** FE/t of a Dragon Head in a Cerebral Array with a Board Room Core. Only one Dragon Head counts: the chair at the head of the table. */
    public static final int DRAGON_HEAD = 300;
    private HeadJobs() {
    }

    public static EntityType<?> type(ItemStack stack) {
        return stack.is(ModRegistry.JARRED_HEAD.get()) ? JarredHeadItem.type(stack) : null;
    }

    /** The stored mob's record, read-only: it is the item's own tag, never copied (callers must not change it). */
    public static CompoundTag entityTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.getUnsafe().getCompound("Entity");
    }

    /** A jarred villager's profession ("librarian", "nitwit", ...), or "" for anything else. */
    public static String profession(ItemStack stack) {
        return HeadInfo.of(stack).profession();
    }

    /** What a head would do on a Turret Pedestal: false for heads with no attack (a pig, a cow). */
    public static boolean turretJob(EntityType<?> type) {
        return type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED || type == EntityType.BLAZE
                || type == EntityType.GUARDIAN || type == EntityType.ELDER_GUARDIAN || type == EntityType.CREEPER || type == EntityType.ZOMBIE
                || type == EntityType.HUSK || type == EntityType.DROWNED || type == EntityType.SNOW_GOLEM || type == EntityType.WITCH || type == EntityType.EVOKER;
    }

    /** Whether a head has a program to run in a Neural Socket on its own. */
    public static boolean socketJob(EntityType<?> type) {
        return type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED || type == EntityType.VILLAGER || type == EntityType.ENDERMAN;
    }

    /** FE/t a head makes in a Cerebral Array by thinking very hard about broth. The Dragon Head needs a Board Room Core. */
    public static int arrayOutput(ItemStack stack, boolean boardRoom) {
        if (stack.is(Items.DRAGON_HEAD)) return boardRoom ? DRAGON_HEAD : 0;
        return arrayOutput(type(stack));
    }

    /** FE/t a jarred head of this creature makes in a Cerebral Array (0 for none). */
    public static int arrayOutput(EntityType<?> type) {
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
        // a two-cell machine is staffed from either half: a mount on its upper half's sides, or on top of it, counts
        BlockPos top = machine.above();
        boolean tall = level.getBlockState(top).getBlock() instanceof com.coolfreeze23.headinajar.machine.MachineTopBlock;
        for (BlockPos cell : tall ? new BlockPos[]{machine, top} : new BlockPos[]{machine}) {
            for (Direction d : Direction.values()) {
                if (!(level.getBlockEntity(cell.relative(d)) instanceof EmployeeMountBlockEntity mount) || !mount.facesInto(cell)) continue;
                String job = mount.profession();
                for (String p : professions) if (p.equals(job)) best = Math.max(best, mount.paid() ? 1f : 0.5f);
            }
        }
        return best;
    }
}
