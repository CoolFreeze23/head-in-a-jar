package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Grades;
import com.coolfreeze23.headinajar.staff.Staff;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * More Sorting filters a Drop-Off (data component {@code headinajar:filters}, 0.7.2), kept beside the order on a
 * Clipboard and on a Job Card: the print version (0 on a Clipboard, {@link #PRINTED} on every card printed from 0.7.2)
 * and up to {@link #EXTRA} more filters for each Drop-Off, by its block. A card without it was printed before 0.7.2 and
 * is read as it always was: one filter a Drop-Off, whoever works it. On a 0.7.2 Sorting card a worker reads a Drop-Off's
 * own filter and then its extras, as many kinds in all as its grade allows ({@link Grades#FILTERS}); the rest it does
 * not read, and what they name goes where an unnamed item goes.
 */
public record CardFilters(int version, List<Entry> stops) {
    /** The version on every card printed from 0.7.2. */
    public static final int PRINTED = 1;
    /** Extra filters a Drop-Off may have (nine with its own). */
    public static final int EXTRA = 8;
    public static final CardFilters NONE = new CardFilters(0, List.of());

    /** One Drop-Off's extra filters, by slot (an empty stack is an empty slot). */
    public record Entry(BlockPos pos, List<ItemStack> filters) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("filters", List.of()).forGetter(Entry::filters)
        ).apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Entry::pos, ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(EXTRA)), Entry::filters, Entry::new);

        public Entry {
            pos = pos.immutable();
            List<ItemStack> l = new ArrayList<>();
            for (int k = 0; k < filters.size() && k < EXTRA; k++) {
                ItemStack s = filters.get(k);
                l.add(s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1));
            }
            while (!l.isEmpty() && l.get(l.size() - 1).isEmpty()) l.remove(l.size() - 1);
            filters = List.copyOf(l);
        }

        boolean blank() {
            return filters.isEmpty();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry e) || !e.pos.equals(pos) || e.filters.size() != filters.size()) return false;
            for (int k = 0; k < filters.size(); k++) if (!ItemStack.isSameItemSameComponents(e.filters.get(k), filters.get(k))) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return pos.hashCode() * 31 + filters.size();
        }
    }

    public static final Codec<CardFilters> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("version", 0).forGetter(CardFilters::version),
            Entry.CODEC.listOf().optionalFieldOf("stops", List.of()).forGetter(CardFilters::stops)
    ).apply(i, CardFilters::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CardFilters> STREAM = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CardFilters::version, Entry.STREAM.apply(ByteBufCodecs.list(Order.MAX_STOPS)), CardFilters::stops, CardFilters::new);

    public CardFilters {
        stops = List.copyOf(stops);
    }

    // --- reading ------------------------------------------------------------------------------------------------------

    /** The component on an item (a Clipboard or a Job Card), or null: a card printed before 0.7.2, a bare clipboard. */
    public static CardFilters of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(WorkRegistry.FILTERS.get());
    }

    /** The extras on a Clipboard (none if it has none). */
    public static CardFilters onBoard(ItemStack board) {
        CardFilters f = of(board);
        return f == null ? NONE : f;
    }

    /** Whether this reads by grade: printed from 0.7.2. */
    public boolean printed() {
        return version >= PRINTED;
    }

    /** A Drop-Off's extra filters, slot by slot ({@link #EXTRA} of them, empty ones included). */
    public List<ItemStack> extras(BlockPos pos) {
        List<ItemStack> l = new ArrayList<>();
        for (Entry e : stops) if (e.pos.equals(pos)) l.addAll(e.filters);
        while (l.size() < EXTRA) l.add(ItemStack.EMPTY);
        return l;
    }

    /**
     * The kinds a Drop-Off's filters name, its own first and then its extras, each kind once, at most {@code allowance}
     * of them. None: an open Drop-Off.
     */
    public List<ItemStack> read(Order.Stop s, int allowance) {
        List<ItemStack> kinds = new ArrayList<>();
        List<ItemStack> all = new ArrayList<>();
        all.add(s.filter());
        all.addAll(extras(s.pos()));
        for (ItemStack f : all) {
            if (kinds.size() >= allowance) break;
            if (f.isEmpty()) continue;
            boolean seen = false;
            for (ItemStack k : kinds) seen |= ItemStack.isSameItem(k, f);
            if (!seen) kinds.add(f);
        }
        return kinds;
    }

    /** How many kinds a Drop-Off filters for, its own filter and its extras. */
    public int count(Order.Stop s) {
        return read(s, Integer.MAX_VALUE).size();
    }

    /** The most filters on any Drop-Off of the order. */
    public int most(Order o) {
        int n = 0;
        for (Order.Stop s : o.dropOffs()) n = Math.max(n, count(s));
        return n;
    }

    /** The lowest grade that reads this many filters a stop. */
    public static int gradeFor(int filters) {
        for (int g = Grades.INTERN; g <= Grades.TEAM_LEAD; g++) if (Grades.FILTERS[g] >= filters) return g;
        return Grades.TEAM_LEAD;
    }

    /** How many filters a stop a worker reads on a 0.7.2 card: its grade's. */
    public static int allowance(Mob mob) {
        return Grades.FILTERS[Grades.of(Staff.of(mob))];
    }

    /**
     * Where a stack may go, as {@link Order#destinations} says, except on a Sorting card printed from 0.7.2, where a
     * Drop-Off names a stack by any of the filters the worker reads ({@link #read}). Null filters (a card printed before
     * 0.7.2), or not Sorting: exactly {@link Order#destinations}.
     */
    public static List<Order.Stop> destinations(Order o, ItemStack stack, boolean sorting, CardFilters f, int allowance) {
        if (!sorting || f == null || !f.printed()) return o.destinations(stack, sorting);
        List<Order.Stop> named = new ArrayList<>(), open = new ArrayList<>(), rest = new ArrayList<>();
        for (Order.Stop s : o.dropOffs()) {
            List<ItemStack> kinds = f.read(s, Math.max(1, allowance));
            boolean names = false;
            for (ItemStack k : kinds) names |= ItemStack.isSameItem(k, stack);
            if (names) named.add(s);
            else if (s.everythingElse()) rest.add(s);
            else if (kinds.isEmpty()) open.add(s);
        }
        if (!named.isEmpty()) return named;
        List<Order.Stop> out = new ArrayList<>(rest);
        out.addAll(open);
        return out;
    }

    // --- the Clipboard ------------------------------------------------------------------------------------------------

    /** One extra filter of a Drop-Off set (or cleared, for an empty stack). */
    public CardFilters withExtra(BlockPos pos, int slot, ItemStack kind) {
        if (slot < 0 || slot >= EXTRA) return this;
        List<ItemStack> l = extras(pos);
        l.set(slot, kind.isEmpty() ? ItemStack.EMPTY : kind.copyWithCount(1));
        List<Entry> out = new ArrayList<>();
        boolean put = false;
        for (Entry e : stops) {
            if (e.pos.equals(pos)) {
                if (!put) out.add(new Entry(pos, l));
                put = true;
            } else out.add(e);
        }
        if (!put) out.add(new Entry(pos, l));
        return new CardFilters(version, out);
    }

    /** Only the extras of the order's Drop-Offs, and none left blank. */
    public CardFilters kept(Order o) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : stops) {
            if (e.blank()) continue;
            Order.Stop s = o.stopAt(e.pos);
            if (s != null && s.dropOff()) out.add(e);
        }
        return out.size() == stops.size() ? this : new CardFilters(version, out);
    }

    /** The same extras on a card printed now. */
    public CardFilters stamped() {
        return new CardFilters(PRINTED, stops);
    }

    /** Puts extras on a Clipboard (the component goes when there are none). */
    public static void put(ItemStack board, CardFilters f) {
        if (f.stops.isEmpty() && !f.printed()) board.remove(WorkRegistry.FILTERS.get());
        else board.set(WorkRegistry.FILTERS.get(), f);
    }

    /** Drops a Clipboard's extras for a stop unmarked or no longer a Drop-Off. */
    public static void tidy(ItemStack board) {
        CardFilters f = of(board);
        if (f == null) return;
        CardFilters k = f.kept(Order.of(board));
        if (k != f) put(board, k);
    }

    // --- the site -----------------------------------------------------------------------------------------------------

    /** Whether anyone on a clock's roster (loaded) has this grade or higher. */
    public static boolean staffed(TimeClockBlockEntity clock, int grade) {
        if (!(clock.getLevel() instanceof ServerLevel level)) return false;
        for (UUID id : clock.roster()) {
            Entity e = level.getEntity(id);
            if (e instanceof Mob mob && Grades.of(Staff.of(mob)) >= grade) return true;
        }
        return false;
    }
}
