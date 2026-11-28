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

    // --- the stations: what the staff row says, and the old tables for a row that does not say -----------------------

    /**
     * The attacks a head can make on a Turret Pedestal (a staff row's {@code turret}), and {@link #LOOKOUT} for none:
     * arrows (plain, Slowness, Poison, Weakness), a fireball, the guardian's laser, the creeper's pulse, a bite, a
     * snowball, a splash of Weakness, a line of fangs.
     */
    public static final String LOOKOUT = "lookout", BOW = "bow", BOW_SLOWNESS = "bow_slowness", BOW_POISON = "bow_poison",
            BOW_WEAKNESS = "bow_weakness", FIREBALL = "fireball", LASER = "laser", PULSE = "pulse", BITE = "bite", SNOWBALL = "snowball",
            POTION = "potion", FANGS = "fangs";
    public static final java.util.Set<String> TURRET_ATTACKS = java.util.Set.of(LOOKOUT, BOW, BOW_SLOWNESS, BOW_POISON, BOW_WEAKNESS, FIREBALL,
            LASER, PULSE, BITE, SNOWBALL, POTION, FANGS);
    /** The Neural Socket programs (a staff row's {@code socket}): a beat, a count, a stare; {@link #NO_PROGRAM} for none. */
    public static final String NO_PROGRAM = "none", BEAT = "beat", COUNT = "count", STARE = "stare";
    public static final java.util.Set<String> SOCKET_PROGRAMS = java.util.Set.of(NO_PROGRAM, BEAT, COUNT, STARE);

    /** What a head would do on a Turret Pedestal: false for heads with no attack (a pig, a cow). */
    public static boolean turretJob(EntityType<?> type) {
        return !LOOKOUT.equals(turretAttack(type));
    }

    /** A head's attack on a Turret Pedestal ({@link #LOOKOUT} for none): its staff row's, else the old table's. */
    public static String turretAttack(EntityType<?> type) {
        var row = com.coolfreeze23.headinajar.staff.StaffTable.row(type);
        return row != null && row.turret() != null ? row.turret() : turretTable(type);
    }

    /** The turret table as it was before the staff rows said (a creature with no row, or a row that does not say). */
    public static String turretTable(EntityType<?> type) {
        if (type == EntityType.SKELETON) return BOW;
        if (type == EntityType.STRAY) return BOW_SLOWNESS;
        if (type == EntityType.BOGGED) return BOW_POISON;
        if (type == EntityType.BLAZE) return FIREBALL;
        if (type == EntityType.GUARDIAN || type == EntityType.ELDER_GUARDIAN) return LASER;
        if (type == EntityType.CREEPER) return PULSE;
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) return BITE;
        if (type == EntityType.SNOW_GOLEM) return SNOWBALL;
        if (type == EntityType.WITCH) return POTION;
        if (type == EntityType.EVOKER) return FANGS;
        return LOOKOUT;
    }

    /** Whether an attack shoots arrows from a chest beside the pedestal. */
    public static boolean arrows(String attack) {
        return attack.startsWith(BOW);
    }

    /** Whether a head has a program to run in a Neural Socket on its own. */
    public static boolean socketJob(EntityType<?> type) {
        return type != null && !NO_PROGRAM.equals(socketProgram(type));
    }

    /** A head's Neural Socket program ({@link #NO_PROGRAM} for none): its staff row's, else the old table's. */
    public static String socketProgram(EntityType<?> type) {
        var row = com.coolfreeze23.headinajar.staff.StaffTable.row(type);
        return row != null && row.socket() != null ? row.socket() : socketTable(type);
    }

    /** The socket table as it was before the staff rows said. */
    public static String socketTable(EntityType<?> type) {
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) return BEAT;
        if (type == EntityType.VILLAGER) return COUNT;
        if (type == EntityType.ENDERMAN) return STARE;
        return NO_PROGRAM;
    }

    /** FE/t a head makes in a Cerebral Array by thinking very hard about broth. The Dragon Head needs a Board Room Core. */
    public static int arrayOutput(ItemStack stack, boolean boardRoom) {
        if (stack.is(Items.DRAGON_HEAD)) return boardRoom ? DRAGON_HEAD : 0;
        return arrayOutput(type(stack));
    }

    /** FE/t a jarred head of this creature makes in a Cerebral Array (0 for none): its staff row's, else the old table's. */
    public static int arrayOutput(EntityType<?> type) {
        if (type == null) return 0;
        var row = com.coolfreeze23.headinajar.staff.StaffTable.row(type);
        return row != null && row.array() != null ? row.array() : arrayTable(type);
    }

    /** The Array table as it was before the staff rows said. */
    public static int arrayTable(EntityType<?> type) {
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
