package com.coolfreeze23.headinajar.staff.second;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * What the company keeps on a host that wears a Secondment Collar (attachment {@code headinajar:secondee}): the collar,
 * the jarred head seated on it (the whole Jarred Head item, its file taken out while it is seated: the file lives on
 * the host as its {@code headinajar:staff} for as long as the head is there), the team sync the pair has earned, the
 * broth in the collar's canister, and the jar's glass. No attachment, or {@code collar} false: an ordinary host (old
 * saves need nothing).
 * <p>
 * The fields below the codec are not saved.
 */
public final class SecondeeData {
    /** The canister's size, mB. */
    public static final int CANISTER = 500;

    public static final Codec<SecondeeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("collar", false).forGetter(d -> d.collar),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("head", ItemStack.EMPTY).forGetter(d -> d.head),
            Codec.FLOAT.optionalFieldOf("team_sync", 0f).forGetter(d -> d.teamSync),
            Codec.INT.optionalFieldOf("team_sync_for", 0).forGetter(d -> d.teamSyncFor),
            Codec.INT.optionalFieldOf("canister", 0).forGetter(d -> d.canister),
            Codec.INT.optionalFieldOf("glass", 0).forGetter(d -> d.glass),
            Codec.INT.optionalFieldOf("glass_max", 10).forGetter(d -> d.glassMax),
            UUIDUtil.CODEC.optionalFieldOf("employer").forGetter(d -> Optional.ofNullable(d.employer)),
            CompoundTag.CODEC.optionalFieldOf("body_file").forGetter(d -> Optional.ofNullable(d.bodyFile)),
            Codec.INT.optionalFieldOf("flags", 0).forGetter(d -> d.flags)
    ).apply(i, SecondeeData::new));

    /** A collar is on the host's neck. */
    public boolean collar;
    /** The seated Jarred Head (one item), or empty: an empty collar. Its {@code Staff} file is on the host meanwhile. */
    public ItemStack head = ItemStack.EMPTY;
    /** Team sync, 0-100: earned at work on shift with a card, by this head on this body. */
    public float teamSync;
    /** The staff number of the head the team sync was earned with (another head starts again at 0). */
    public int teamSyncFor;
    /** Broth in the canister, mB (0-{@link #CANISTER}). */
    public int canister;
    /** The jar's glass, and its most (10 Life Support or Compact, 20 Reinforced, 30 Executive). */
    public int glass, glassMax = 10;
    /** Who fitted the collar (or the host's employer): who may seat and unseat, with those they trust. */
    public UUID employer;
    /** The body's own Personnel File while a head is seated (put back when it is unseated), or null if it had none. */
    public CompoundTag bodyFile;
    /** Small saved facts, one bit each. New bits go at the end. Reserved: brain 0-7, broth 8-11, package 12-15, look 16-19. */
    public int flags;

    // --- not saved ---------------------------------------------------------------------------------------------------

    /** Ticks it has stood dry (the broth helper's), and the last view step sent to clients (the look helper's). */
    public int dryTicks;
    public int sentStep = -1;

    public SecondeeData() {
    }

    private SecondeeData(boolean collar, ItemStack head, float teamSync, int teamSyncFor, int canister, int glass, int glassMax,
                         Optional<UUID> employer, Optional<CompoundTag> bodyFile, int flags) {
        this.collar = collar;
        this.head = head;
        this.teamSync = teamSync;
        this.teamSyncFor = teamSyncFor;
        this.canister = canister;
        this.glass = glass;
        this.glassMax = glassMax;
        this.employer = employer.orElse(null);
        this.bodyFile = bodyFile.orElse(null);
        this.flags = flags;
    }

    /** A head is seated on the collar. */
    public boolean seated() {
        return collar && !head.isEmpty();
    }
}
