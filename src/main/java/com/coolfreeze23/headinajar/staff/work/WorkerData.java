package com.coolfreeze23.headinajar.staff.work;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the company keeps on a living creature it has hired with a Staff Lanyard (attachment {@code headinajar:worker}):
 * who employs it, which Time Clock it belongs to, where it is in its day, its shift, the card it holds, what it is
 * carrying, and what to give back when it leaves. No attachment: not a worker (old saves need nothing). The number,
 * name, morale and history are on its Personnel File ({@code headinajar:staff}), as for any creature on file.
 * <p>
 * The fields below the codec are not saved: they belong to the brain's current task and are rebuilt after a load.
 */
public final class WorkerData {
    /** Where a worker is in its day. Saved by name, so the order may change. */
    public enum State implements StringRepresentable {
        /** Hired, with no clock yet: stands where it is until a clock of its employer's with room is within 16. */
        AWAITING,
        /** Walking to its clock to punch in (or back to it after a job). */
        TO_CLOCK,
        /** Off shift: a spot in the line by its clock, facing it, brain off. */
        OFF_SHIFT,
        /** On shift with a card: doing the card's job. */
        ON_JOB,
        /** Outside its site (shoved, led away, its clock broken or in another dimension). */
        OFF_SITE,
        /** Let go: 200 ticks of notice, then a vanilla mob again. */
        NOTICE,
        /** On a player's detail (the Supervisor's Whistle). */
        DETAIL,
        /** Taken out of the world by the company shuttle (only ever seen on a record in a clock's arrivals list). */
        SHUTTLE;

        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** A worker's shift (the Time Clock's Shifts tab; the species' shift by default). */
    public enum Shift implements StringRepresentable {
        DAY, NIGHT, REDSTONE;

        public static final Codec<Shift> CODEC = StringRepresentable.fromEnum(Shift::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Stacks a worker can carry at most (the ghast's six). */
    public static final int MAX_CARRIED = 6;

    public static final Codec<WorkerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("employer").forGetter(d -> d.employer),
            GlobalPos.CODEC.optionalFieldOf("clock").forGetter(d -> Optional.ofNullable(d.clock)),
            State.CODEC.optionalFieldOf("state", State.AWAITING).forGetter(d -> d.state),
            Shift.CODEC.optionalFieldOf("shift", Shift.DAY).forGetter(d -> d.shift),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("card", ItemStack.EMPTY).forGetter(d -> d.card),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("carried", List.of()).forGetter(d -> d.carried),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("tool", ItemStack.EMPTY).forGetter(d -> d.tool),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("pocket", ItemStack.EMPTY).forGetter(d -> d.pocket),
            Codec.LONG.optionalFieldOf("notice_until", 0L).forGetter(d -> d.noticeUntil),
            Codec.BOOL.optionalFieldOf("was_persistent", false).forGetter(d -> d.wasPersistent),
            Codec.INT.optionalFieldOf("old_despawn_delay", 0).forGetter(d -> d.oldDespawnDelay),
            UUIDUtil.CODEC.optionalFieldOf("detail").forGetter(d -> Optional.ofNullable(d.detail)),
            Codec.LONG.optionalFieldOf("hired_day", 0L).forGetter(d -> d.hiredDay),
            Codec.INT.optionalFieldOf("flags", 0).forGetter(d -> d.flags)
    ).apply(i, WorkerData::new));

    public UUID employer;
    /** Its Time Clock (dimension and position), or null while awaiting assignment. */
    public GlobalPos clock;
    public State state = State.AWAITING;
    public Shift shift = Shift.DAY;
    /** A personal Job Card (given by hand), or empty: a rack card stays in the rack and is only pointed at. */
    public ItemStack card = ItemStack.EMPTY;
    /** What it is carrying (at most {@link #MAX_CARRIED} stacks); set down on Let go, dropped on death. */
    public final List<ItemStack> carried = new ArrayList<>();
    /** A tool taken from a Pick-Up (shears), worn as it is used. */
    public ItemStack tool = ItemStack.EMPTY;
    /** A piglin's pocket (its skim). */
    public ItemStack pocket = ItemStack.EMPTY;
    /** The game time a Let go's notice ends (state NOTICE). */
    public long noticeUntil;
    /** Whether the creature was persistent before it was hired (restored on Let go). */
    public boolean wasPersistent;
    /** A wandering trader's despawn delay before it was hired (restored on Let go). */
    public int oldDespawnDelay;
    /** The player whose detail it is on (state DETAIL), or null. */
    public UUID detail;
    /** The day it was hired. */
    public long hiredDay;
    /** Small saved facts, one bit each ({@link #FLAG_HAT_THANKED} ...). New bits go at the end. */
    public int flags;

    /** It has been given a Hard Hat once (the "Health and Safety thanks you." line is said once). */
    public static final int FLAG_HAT_THANKED = 1;
    /** It has punched in at least once (the Clocking In advancement fires once per worker). */
    public static final int FLAG_PUNCHED = 1 << 1;

    // --- not saved: the brain's working state ------------------------------------------------------------------------

    /** The current job's own state (a trip, a field's reservation...), owned by whichever job set it. */
    public Object task;
    /** The rack slot (0-7) of the posting it is working, or -1 (a personal card, or none). */
    public int rackSlot = -1;
    /** The game time of its last path request (rationed: one every {@code WorkerPaths.PATH_TICKS}). */
    public long lastPath = Long.MIN_VALUE / 2;
    /** Failed path requests to the current stop, in a row. */
    public int pathFails;
    /** The game time its last floating line was said (one each {@code Workforce.SAY_TICKS}). */
    public long lastSaid = Long.MIN_VALUE / 2;
    /** Its status as the Roster shows it: a language key ({@code staff.headinajar.status.*} or a stop line), or null. */
    public String status;
    /** It is held up (a full Drop-Off, a missing tool, no arrows...): its clock's lamp shows amber. */
    public boolean heldUp;
    /** The game time until which it looks at whoever hired it (the hire). */
    public long lookUntil;
    /** Who hired it, for the look (not saved). */
    public UUID lookAt;

    public WorkerData() {
    }

    private WorkerData(UUID employer, Optional<GlobalPos> clock, State state, Shift shift, ItemStack card, List<ItemStack> carried, ItemStack tool,
                       ItemStack pocket, long noticeUntil, boolean wasPersistent, int oldDespawnDelay, Optional<UUID> detail, long hiredDay, int flags) {
        this.employer = employer;
        this.clock = clock.orElse(null);
        this.state = state;
        this.shift = shift;
        this.card = card;
        for (ItemStack s : carried) if (!s.isEmpty() && this.carried.size() < MAX_CARRIED) this.carried.add(s);
        this.tool = tool;
        this.pocket = pocket;
        this.noticeUntil = noticeUntil;
        this.wasPersistent = wasPersistent;
        this.oldDespawnDelay = oldDespawnDelay;
        this.detail = detail.orElse(null);
        this.hiredDay = hiredDay;
        this.flags = flags;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public void set(int flag) {
        flags |= flag;
    }

    /** Whether it is carrying anything. */
    public boolean carrying() {
        for (ItemStack s : carried) if (!s.isEmpty()) return true;
        return false;
    }

    /** Forgets the current task (a new card, a recall, a shift's end). */
    public void dropTask() {
        task = null;
        rackSlot = -1;
        pathFails = 0;
        heldUp = false;
    }
}
